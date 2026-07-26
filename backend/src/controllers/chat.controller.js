const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

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

const createSession = asyncHandler(async (req, res) => {
  const { session_name, description, start_datetime, end_datetime, retention_policy } = req.body;
  if (!session_name || !start_datetime || !end_datetime) {
    return res.status(400).json({ message: 'session_name, start_datetime and end_datetime are required' });
  }
  if (new Date(end_datetime) <= new Date(start_datetime)) {
    return res.status(400).json({ message: 'end_datetime must be after start_datetime' });
  }
  const policy = ['immediate', '24h', '7d'].includes(retention_policy) ? retention_policy : 'immediate';
  const { rows } = await pool.query(
    `INSERT INTO chat_sessions (session_name, description, start_datetime, end_datetime, retention_policy, created_by)
     VALUES ($1, $2, $3, $4, $5, $6) RETURNING *`,
    [session_name, description || null, start_datetime, end_datetime, policy, req.user.id]
  );
  await logAudit(req.user.id, 'CHAT_SESSION_CREATED', 'chat_session', rows[0].id, { session_name });
  res.status(201).json(rows[0]);
});

const updateSession = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { session_name, description, start_datetime, end_datetime, retention_policy } = req.body;
  const { rows: existingRows } = await pool.query('SELECT status FROM chat_sessions WHERE id = $1', [id]);
  if (!existingRows.length) return res.status(404).json({ message: 'Chat session not found' });
  if (existingRows[0].status === 'expired') {
    return res.status(400).json({ message: 'An expired chat session can no longer be edited' });
  }
  if (!session_name || !start_datetime || !end_datetime) {
    return res.status(400).json({ message: 'session_name, start_datetime and end_datetime are required' });
  }
  if (new Date(end_datetime) <= new Date(start_datetime)) {
    return res.status(400).json({ message: 'end_datetime must be after start_datetime' });
  }
  const policy = ['immediate', '24h', '7d'].includes(retention_policy) ? retention_policy : 'immediate';
  const { rows } = await pool.query(
    `UPDATE chat_sessions SET session_name = $1, description = $2, start_datetime = $3, end_datetime = $4, retention_policy = $5
     WHERE id = $6 RETURNING *`,
    [session_name, description || null, start_datetime, end_datetime, policy, id]
  );
  await logAudit(req.user.id, 'CHAT_SESSION_EDITED', 'chat_session', id, { session_name });
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
  listSessions, getActiveSession, getSession, createSession, updateSession,
  openSession, closeSession, deleteSession, clearMessages,
  getMessages, sendMessage, deleteMessage,
  pinMessage: setMessagePinned(true), unpinMessage: setMessagePinned(false),
  searchMessages,
  listParticipants, joinSession, leaveSession,
  muteParticipant: setParticipantMuted(true), unmuteParticipant: setParticipantMuted(false),
  removeParticipant,
  getNotifications, markNotificationsRead
};
