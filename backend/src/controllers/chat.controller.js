const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

// Same definition as chatScheduler.js::LOCAL_NOW - see that file's comment for why naive
// Philippine-local wall-clock time (not an absolute UTC instant) is the right frame here. Needed
// here too for the single-vs-recurring priority read-back in getChatStatus().
const LOCAL_NOW = `(NOW() AT TIME ZONE 'Asia/Manila')`;
const DAY_LABELS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

// Strips control characters (keeps newline/tab) before a message is stored - defense in depth
// per the security spec, even though the Android client only ever renders messages as plain
// Compose Text (no HTML/WebView involved, so there's no injection surface on the client itself).
const CONTROL_CHARS = new RegExp('[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]', 'g');

// "Device information (if available)" per the audit-trail spec - there's no dedicated column on
// audit_logs for this (would mean altering a table every other controller already writes to), so
// it's folded into the existing JSON `details` blob instead, only for chat's user-initiated actions.
function deviceInfo(req) {
  return req.headers['user-agent'] || null;
}

const MESSAGE_ROW = `
  SELECT m.id, m.session_id, m.user_id, m.message, m.sent_at, m.edited_at, m.is_pinned,
         u.name AS user_name, u.username AS user_username, u.photo_url AS user_photo_url
  FROM chat_messages m
  JOIN users u ON u.id = m.user_id
`;

// ---- Sessions -------------------------------------------------------------

const listSessions = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT cs.*, u.name AS created_by_name,
            (SELECT COUNT(*) FROM chat_participants p WHERE p.session_id = cs.id AND p.left_at IS NULL) AS participant_count
     FROM chat_sessions cs
     LEFT JOIN users u ON u.id = cs.created_by
     ORDER BY cs.start_datetime DESC`
  );
  res.json(rows);
});

// The single "current" session the Chat menu should show - prefers an open room, falls back to a
// recently-closed one (so users still see "This chat session has ended" instead of a blank
// screen), then the next upcoming scheduled one, else null (nothing to show - "reset the room").
const getActiveSession = asyncHandler(async (req, res) => {
  const queries = [
    `SELECT * FROM chat_sessions WHERE status = 'open' ORDER BY start_datetime DESC LIMIT 1`,
    `SELECT * FROM chat_sessions WHERE status = 'closed' ORDER BY end_datetime DESC LIMIT 1`,
    `SELECT * FROM chat_sessions WHERE status = 'scheduled' ORDER BY start_datetime ASC LIMIT 1`
  ];
  for (const query of queries) {
    const { rows } = await pool.query(query);
    if (rows.length) {
      const countRes = await pool.query(
        `SELECT COUNT(*) FROM chat_participants WHERE session_id = $1 AND left_at IS NULL`,
        [rows[0].id]
      );
      return res.json({ ...rows[0], participant_count: Number(countRes.rows[0].count) });
    }
  }
  res.json(null);
});

const getSession = asyncHandler(async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM chat_sessions WHERE id = $1', [req.params.id]);
  if (!rows.length) return res.status(404).json({ message: 'Chat session not found' });
  res.json(rows[0]);
});

const openSession = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query(
    `UPDATE chat_sessions SET status = 'open' WHERE id = $1 AND status IN ('scheduled', 'closed') RETURNING *`,
    [id]
  );
  if (!result.rowCount) return res.status(400).json({ message: 'Session cannot be opened from its current state' });
  await logAudit(req.user.id, 'CHAT_SESSION_OPENED', 'chat_session', id, null);
  res.json(result.rows[0]);
});

const closeSession = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query(
    `UPDATE chat_sessions SET status = 'closed' WHERE id = $1 AND status = 'open' RETURNING *`,
    [id]
  );
  if (!result.rowCount) return res.status(400).json({ message: 'Session cannot be closed from its current state' });
  await logAudit(req.user.id, 'CHAT_SESSION_CLOSED', 'chat_session', id, null);
  res.json(result.rows[0]);
});

const deleteSession = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query('DELETE FROM chat_sessions WHERE id = $1', [id]);
  if (!result.rowCount) return res.status(404).json({ message: 'Chat session not found' });
  await logAudit(req.user.id, 'CHAT_SESSION_DELETED', 'chat_session', id, null);
  res.status(204).send();
});

const clearMessages = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query('DELETE FROM chat_messages WHERE session_id = $1', [id]);
  await logAudit(req.user.id, 'CHAT_MESSAGES_CLEARED', 'chat_session', id, { count: result.rowCount, device: deviceInfo(req) });
  res.json({ deleted: result.rowCount });
});

// ---- Recurring schedules ----------------------------------------------------
// A template chatScheduler.js::generateScheduledSessions() turns into a real chat_sessions row
// each day it's due, so an admin/super_admin sets a schedule up once instead of re-creating the
// same session every time.

const VALID_DAYS = [0, 1, 2, 3, 4, 5, 6];
const TIME_RE = /^([01]\d|2[0-3]):([0-5]\d)(:[0-5]\d)?$/;

function validateRecurringSchedule(body) {
  const { session_name, days_of_week, start_time, end_time, retention_policy } = body;
  if (!session_name || !String(session_name).trim()) return 'session_name is required';
  if (!Array.isArray(days_of_week) || !days_of_week.length || !days_of_week.every((d) => VALID_DAYS.includes(d))) {
    return 'days_of_week must be a non-empty array of integers 0-6 (0=Sunday)';
  }
  if (!TIME_RE.test(String(start_time || ''))) return 'start_time must be in HH:MM format';
  if (!TIME_RE.test(String(end_time || ''))) return 'end_time must be in HH:MM format';
  // Unlike a one-off session (which has a full date+time on both ends and so can already span
  // midnight), a recurring schedule only stores a bare TIME - end_time <= start_time is treated as
  // "ends the next day" (e.g. 10:00 PM - 2:00 AM) rather than rejected, so overnight groups aren't
  // stuck with the old same-day-only restriction. Only a truly zero-length window is invalid.
  if (String(end_time) === String(start_time)) return 'start_time and end_time cannot be the same';
  if (retention_policy && !['immediate', '24h', '7d'].includes(retention_policy)) return 'invalid retention_policy';
  return null;
}

const listRecurringSchedules = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT rs.*, u.name AS created_by_name FROM chat_recurring_schedules rs
     LEFT JOIN users u ON u.id = rs.created_by
     ORDER BY rs.start_time ASC`
  );
  res.json(rows);
});

const createRecurringSchedule = asyncHandler(async (req, res) => {
  const error = validateRecurringSchedule(req.body);
  if (error) return res.status(400).json({ message: error });
  const { session_name, description, days_of_week, start_time, end_time, retention_policy } = req.body;
  const policy = ['immediate', '24h', '7d'].includes(retention_policy) ? retention_policy : 'immediate';
  const { rows } = await pool.query(
    `INSERT INTO chat_recurring_schedules (session_name, description, days_of_week, start_time, end_time, retention_policy, created_by)
     VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING *`,
    [session_name, description || null, days_of_week, start_time, end_time, policy, req.user.id]
  );
  await logAudit(req.user.id, 'CHAT_RECURRING_SCHEDULE_CREATED', 'chat_recurring_schedule', rows[0].id, { session_name });
  res.status(201).json(rows[0]);
});

const updateRecurringSchedule = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const error = validateRecurringSchedule(req.body);
  if (error) return res.status(400).json({ message: error });
  const { session_name, description, days_of_week, start_time, end_time, retention_policy, is_active } = req.body;
  const policy = ['immediate', '24h', '7d'].includes(retention_policy) ? retention_policy : 'immediate';
  const { rows } = await pool.query(
    `UPDATE chat_recurring_schedules
     SET session_name = $1, description = $2, days_of_week = $3, start_time = $4, end_time = $5,
         retention_policy = $6, is_active = $7
     WHERE id = $8 RETURNING *`,
    [session_name, description || null, days_of_week, start_time, end_time, policy, is_active !== false, id]
  );
  if (!rows.length) return res.status(404).json({ message: 'Recurring schedule not found' });
  await logAudit(req.user.id, 'CHAT_RECURRING_SCHEDULE_EDITED', 'chat_recurring_schedule', id, { session_name });
  res.json(rows[0]);
});

const deleteRecurringSchedule = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query('DELETE FROM chat_recurring_schedules WHERE id = $1', [id]);
  if (!result.rowCount) return res.status(404).json({ message: 'Recurring schedule not found' });
  await logAudit(req.user.id, 'CHAT_RECURRING_SCHEDULE_DELETED', 'chat_recurring_schedule', id, null);
  res.status(204).send();
});

// ---- Single-time schedules ---------------------------------------------------
// A one-off exception for a single calendar date - at most one per date (DB-enforced UNIQUE on
// schedule_date), and when active it outranks whatever a recurring schedule would otherwise
// generate for that date (see chatScheduler.js::generateSingleTimeSessions/generateRecurringSessions).

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function validateSingleSchedule(body) {
  const { session_name, schedule_date, start_time, end_time, retention_policy } = body;
  if (!session_name || !String(session_name).trim()) return 'session_name is required';
  if (!DATE_RE.test(String(schedule_date || ''))) return 'schedule_date must be in YYYY-MM-DD format';
  if (!TIME_RE.test(String(start_time || ''))) return 'start_time must be in HH:MM format';
  if (!TIME_RE.test(String(end_time || ''))) return 'end_time must be in HH:MM format';
  // Same overnight-allowed rule as recurring schedules - end_time <= start_time means "ends the
  // next day," only a truly zero-length window is rejected.
  if (String(end_time) === String(start_time)) return 'start_time and end_time cannot be the same';
  if (retention_policy && !['immediate', '24h', '7d'].includes(retention_policy)) return 'invalid retention_policy';
  return null;
}

// The client resolves the "recurring schedule already exists for this day" conflict dialog itself
// (it already has the recurring list loaded) and tells us the outcome via conflicted_recurring_
// schedule_id + is_active, so the audit log can distinguish a plain create/edit from an override
// decision without the server re-deriving the same conflict check redundantly.
async function logSingleScheduleAudit(req, action, id, sessionName, conflictedRecurringId) {
  const details = { session_name: sessionName, device: req.headers['user-agent'] || null, ip: req.ip };
  if (conflictedRecurringId) details.recurring_schedule_id = conflictedRecurringId;
  await logAudit(req.user.id, action, 'chat_single_schedule', id, details);
}

const listSingleSchedules = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT ss.*, u.name AS created_by_name FROM chat_single_schedules ss
     LEFT JOIN users u ON u.id = ss.created_by
     ORDER BY ss.schedule_date DESC`
  );
  res.json(rows);
});

const createSingleSchedule = asyncHandler(async (req, res) => {
  const error = validateSingleSchedule(req.body);
  if (error) return res.status(400).json({ message: error });
  const { session_name, schedule_date, start_time, end_time, remarks, retention_policy, is_active, conflicted_recurring_schedule_id } = req.body;
  const policy = ['immediate', '24h', '7d'].includes(retention_policy) ? retention_policy : 'immediate';
  let rows;
  try {
    ({ rows } = await pool.query(
      `INSERT INTO chat_single_schedules (session_name, schedule_date, start_time, end_time, remarks, retention_policy, is_active, created_by)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING *`,
      [session_name, schedule_date, start_time, end_time, remarks || null, policy, is_active !== false, req.user.id]
    ));
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ message: 'A single-time schedule already exists for this date' });
    throw err;
  }
  const created = rows[0];
  const action = conflicted_recurring_schedule_id
    ? (created.is_active ? 'CHAT_SINGLE_SCHEDULE_OVERRIDE_ACCEPTED' : 'CHAT_SINGLE_SCHEDULE_OVERRIDE_KEPT_RECURRING')
    : 'CHAT_SINGLE_SCHEDULE_CREATED';
  await logSingleScheduleAudit(req, action, created.id, session_name, conflicted_recurring_schedule_id);
  res.status(201).json(created);
});

const updateSingleSchedule = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const error = validateSingleSchedule(req.body);
  if (error) return res.status(400).json({ message: error });
  const { session_name, schedule_date, start_time, end_time, remarks, retention_policy, is_active, conflicted_recurring_schedule_id } = req.body;
  const policy = ['immediate', '24h', '7d'].includes(retention_policy) ? retention_policy : 'immediate';
  let rows;
  try {
    ({ rows } = await pool.query(
      `UPDATE chat_single_schedules
       SET session_name = $1, schedule_date = $2, start_time = $3, end_time = $4, remarks = $5,
           retention_policy = $6, is_active = $7
       WHERE id = $8 RETURNING *`,
      [session_name, schedule_date, start_time, end_time, remarks || null, policy, is_active !== false, id]
    ));
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ message: 'A single-time schedule already exists for this date' });
    throw err;
  }
  if (!rows.length) return res.status(404).json({ message: 'Single-time schedule not found' });
  const updated = rows[0];
  const action = conflicted_recurring_schedule_id
    ? (updated.is_active ? 'CHAT_SINGLE_SCHEDULE_OVERRIDE_ACCEPTED' : 'CHAT_SINGLE_SCHEDULE_OVERRIDE_KEPT_RECURRING')
    : 'CHAT_SINGLE_SCHEDULE_EDITED';
  await logSingleScheduleAudit(req, action, id, session_name, conflicted_recurring_schedule_id);
  res.json(updated);
});

const deleteSingleSchedule = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query('DELETE FROM chat_single_schedules WHERE id = $1', [id]);
  if (!result.rowCount) return res.status(404).json({ message: 'Single-time schedule not found' });
  await logSingleScheduleAudit(req, 'CHAT_SINGLE_SCHEDULE_DELETED', id, null, null);
  res.status(204).send();
});

// ---- Chat status (admin dashboard + user-facing "chat unavailable") ---------
// One shared computation reused by both Manage Chat Sessions' status header and ChatRoomScreen's
// closed-state messaging, so "what's active / what's next" only exists in one place.

const overnightEndAt = (alias, dateExpr) =>
  `(${dateExpr}::date + ${alias}.end_time + CASE WHEN ${alias}.end_time <= ${alias}.start_time THEN INTERVAL '1 day' ELSE INTERVAL '0' END)`;

// Walks forward from today (inclusive) up to 7 days, applying the same single-beats-recurring
// priority as the scheduler, and returns the first day that still has time left in its window.
async function computeNextSchedule() {
  for (let offset = 0; offset <= 7; offset++) {
    const dateExpr = `(${LOCAL_NOW}::date + INTERVAL '${offset} days')`;
    const stillUpcoming = (alias) => `(${dateExpr}::date > ${LOCAL_NOW}::date OR ${overnightEndAt(alias, dateExpr)} > ${LOCAL_NOW})`;

    const { rows: singleRows } = await pool.query(
      `SELECT session_name, schedule_date, start_time, end_time FROM chat_single_schedules
       WHERE is_active = true AND status = 'scheduled' AND schedule_date = ${dateExpr}::date
         AND ${stillUpcoming('chat_single_schedules')}
       LIMIT 1`
    );
    if (singleRows.length) {
      const s = singleRows[0];
      const dow = new Date(`${s.schedule_date}T00:00:00Z`).getUTCDay();
      return { date: s.schedule_date, day_label: DAY_LABELS[dow], start_time: s.start_time, end_time: s.end_time, session_name: s.session_name, type: 'single' };
    }

    const { rows: recurringRows } = await pool.query(
      `SELECT rs.session_name, rs.start_time, rs.end_time, ${dateExpr}::date AS d
       FROM chat_recurring_schedules rs
       WHERE rs.is_active = true
         AND EXTRACT(DOW FROM ${dateExpr})::int = ANY(rs.days_of_week)
         AND ${stillUpcoming('rs')}
         AND NOT EXISTS (SELECT 1 FROM chat_single_schedules ss WHERE ss.schedule_date = ${dateExpr}::date AND ss.is_active = true)
       LIMIT 1`
    );
    if (recurringRows.length) {
      const r = recurringRows[0];
      const dow = new Date(`${r.d}T00:00:00Z`).getUTCDay();
      return { date: r.d, day_label: DAY_LABELS[dow], start_time: r.start_time, end_time: r.end_time, session_name: r.session_name, type: 'recurring' };
    }
  }
  return null;
}

const getChatStatus = asyncHandler(async (req, res) => {
  const { rows: openRows } = await pool.query(
    `SELECT session_name, start_datetime, end_datetime, recurring_schedule_id, single_schedule_id
     FROM chat_sessions WHERE status = 'open' ORDER BY start_datetime DESC LIMIT 1`
  );
  const activeSchedule = openRows.length
    ? {
        type: openRows[0].single_schedule_id ? 'single' : 'recurring',
        session_name: openRows[0].session_name,
        start_datetime: openRows[0].start_datetime,
        end_datetime: openRows[0].end_datetime
      }
    : null;
  const nextSchedule = await computeNextSchedule();
  res.json({ isOpen: openRows.length > 0, activeSchedule, nextSchedule });
});

// ---- Messages ---------------------------------------------------------------

const getMessages = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const afterId = req.query.after_id ? Number(req.query.after_id) : null;
  const limit = Math.min(Number(req.query.limit) || 200, 500);
  const { rows } = await pool.query(
    `${MESSAGE_ROW}
     WHERE m.session_id = $1 AND m.is_deleted = false AND ($2::int IS NULL OR m.id > $2)
     ORDER BY m.id ASC LIMIT $3`,
    [id, afterId, limit]
  );
  res.json(rows);
});

const sendMessage = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const raw = String(req.body.message || '').replace(CONTROL_CHARS, '').trim();
  if (!raw) return res.status(400).json({ message: 'message is required' });
  if (raw.length > 2000) return res.status(400).json({ message: 'message is too long (max 2000 characters)' });

  const { rows: sessionRows } = await pool.query('SELECT status FROM chat_sessions WHERE id = $1', [id]);
  if (!sessionRows.length) return res.status(404).json({ message: 'Chat session not found' });
  if (sessionRows[0].status !== 'open') {
    return res.status(403).json({ message: 'This chat session is not currently open' });
  }

  const { rows: participantRows } = await pool.query(
    'SELECT is_muted, is_removed FROM chat_participants WHERE session_id = $1 AND user_id = $2',
    [id, req.user.id]
  );
  if (participantRows.length && participantRows[0].is_removed) {
    return res.status(403).json({ message: 'You have been removed from this chat session' });
  }
  if (participantRows.length && participantRows[0].is_muted) {
    return res.status(403).json({ message: 'You have been muted in this chat session' });
  }

  await pool.query(
    `INSERT INTO chat_participants (session_id, user_id, last_active_at)
     VALUES ($1, $2, NOW())
     ON CONFLICT (session_id, user_id) DO UPDATE SET last_active_at = NOW(), left_at = NULL`,
    [id, req.user.id]
  );

  const { rows } = await pool.query(
    'INSERT INTO chat_messages (session_id, user_id, message) VALUES ($1, $2, $3) RETURNING id',
    [id, req.user.id, raw]
  );
  const { rows: fullRows } = await pool.query(`${MESSAGE_ROW} WHERE m.id = $1`, [rows[0].id]);
  await logAudit(req.user.id, 'CHAT_MESSAGE_SENT', 'chat_message', rows[0].id, { session_id: Number(id), device: deviceInfo(req) });
  res.status(201).json(fullRows[0]);
});

const deleteMessage = asyncHandler(async (req, res) => {
  const { messageId } = req.params;
  const result = await pool.query('UPDATE chat_messages SET is_deleted = true WHERE id = $1', [messageId]);
  if (!result.rowCount) return res.status(404).json({ message: 'Message not found' });
  await logAudit(req.user.id, 'CHAT_MESSAGE_DELETED', 'chat_message', messageId, { device: deviceInfo(req) });
  res.status(204).send();
});

const setMessagePinned = (pinned) => asyncHandler(async (req, res) => {
  const { messageId } = req.params;
  const result = await pool.query('UPDATE chat_messages SET is_pinned = $1 WHERE id = $2', [pinned, messageId]);
  if (!result.rowCount) return res.status(404).json({ message: 'Message not found' });
  await logAudit(req.user.id, pinned ? 'CHAT_MESSAGE_PINNED' : 'CHAT_MESSAGE_UNPINNED', 'chat_message', messageId, null);
  res.status(204).send();
});

// Search is scoped to one session's own messages and blocked once that session's messages have
// actually been purged (status 'expired') - there's nothing left to search by then anyway.
const searchMessages = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows: sessionRows } = await pool.query('SELECT status FROM chat_sessions WHERE id = $1', [id]);
  if (!sessionRows.length) return res.status(404).json({ message: 'Chat session not found' });
  if (sessionRows[0].status === 'expired') {
    return res.status(400).json({ message: 'This chat session has ended and its messages have been removed' });
  }
  const query = req.query.query ? String(req.query.query) : null;
  const date = req.query.date ? String(req.query.date) : null;
  const { rows } = await pool.query(
    `${MESSAGE_ROW}
     WHERE m.session_id = $1 AND m.is_deleted = false
       AND ($2::text IS NULL OR m.message ILIKE '%' || $2 || '%' OR u.name ILIKE '%' || $2 || '%' OR u.username ILIKE '%' || $2 || '%')
       AND ($3::date IS NULL OR m.sent_at::date = $3::date)
     ORDER BY m.sent_at ASC`,
    [id, query, date]
  );
  res.json(rows);
});

// ---- Participants -------------------------------------------------------------

const listParticipants = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows } = await pool.query(
    `SELECT u.id AS user_id, u.name, u.username, u.role, u.photo_url,
            p.joined_at, p.last_active_at, p.is_muted, p.is_removed,
            (p.last_active_at > NOW() - INTERVAL '90 seconds') AS is_online
     FROM chat_participants p
     JOIN users u ON u.id = p.user_id
     WHERE p.session_id = $1 AND p.left_at IS NULL
     ORDER BY is_online DESC, u.name ASC`,
    [id]
  );
  res.json(rows);
});

const joinSession = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows: existing } = await pool.query(
    'SELECT is_removed FROM chat_participants WHERE session_id = $1 AND user_id = $2',
    [id, req.user.id]
  );
  if (existing.length && existing[0].is_removed) {
    return res.status(403).json({ message: 'You have been removed from this chat session' });
  }
  await pool.query(
    `INSERT INTO chat_participants (session_id, user_id, last_active_at)
     VALUES ($1, $2, NOW())
     ON CONFLICT (session_id, user_id) DO UPDATE SET last_active_at = NOW(), left_at = NULL`,
    [id, req.user.id]
  );
  res.status(204).send();
});

const leaveSession = asyncHandler(async (req, res) => {
  const { id } = req.params;
  await pool.query('UPDATE chat_participants SET left_at = NOW() WHERE session_id = $1 AND user_id = $2', [id, req.user.id]);
  res.status(204).send();
});

const setParticipantMuted = (muted) => asyncHandler(async (req, res) => {
  const { id, userId } = req.params;
  await pool.query(
    `INSERT INTO chat_participants (session_id, user_id, is_muted, last_active_at)
     VALUES ($1, $2, $3, NOW())
     ON CONFLICT (session_id, user_id) DO UPDATE SET is_muted = $3`,
    [id, userId, muted]
  );
  await logAudit(req.user.id, muted ? 'CHAT_USER_MUTED' : 'CHAT_USER_UNMUTED', 'user', userId, { session_id: Number(id), device: deviceInfo(req) });
  res.status(204).send();
});

const removeParticipant = asyncHandler(async (req, res) => {
  const { id, userId } = req.params;
  await pool.query(
    `INSERT INTO chat_participants (session_id, user_id, is_removed, left_at, last_active_at)
     VALUES ($1, $2, true, NOW(), NOW())
     ON CONFLICT (session_id, user_id) DO UPDATE SET is_removed = true, left_at = NOW()`,
    [id, userId]
  );
  await logAudit(req.user.id, 'CHAT_USER_REMOVED', 'user', userId, { session_id: Number(id), device: deviceInfo(req) });
  res.status(204).send();
});

// ---- Notifications -------------------------------------------------------------

const getNotifications = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT * FROM chat_notifications WHERE user_id = $1 AND status = 'unread' ORDER BY sent_at DESC LIMIT 50`,
    [req.user.id]
  );
  res.json(rows);
});

const markNotificationsRead = asyncHandler(async (req, res) => {
  const ids = Array.isArray(req.body.ids) ? req.body.ids : null;
  await pool.query(
    `UPDATE chat_notifications SET status = 'read' WHERE user_id = $1 AND ($2::int[] IS NULL OR id = ANY($2::int[]))`,
    [req.user.id, ids]
  );
  res.status(204).send();
});

module.exports = {
  listSessions, getActiveSession, getSession,
  openSession, closeSession, deleteSession, clearMessages,
  listRecurringSchedules, createRecurringSchedule, updateRecurringSchedule, deleteRecurringSchedule,
  listSingleSchedules, createSingleSchedule, updateSingleSchedule, deleteSingleSchedule,
  getChatStatus,
  getMessages, sendMessage, deleteMessage,
  pinMessage: setMessagePinned(true), unpinMessage: setMessagePinned(false),
  searchMessages,
  listParticipants, joinSession, leaveSession,
  muteParticipant: setParticipantMuted(true), unmuteParticipant: setParticipantMuted(false),
  removeParticipant,
  getNotifications, markNotificationsRead
};
