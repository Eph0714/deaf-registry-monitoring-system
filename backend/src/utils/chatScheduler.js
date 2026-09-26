const pool = require('../config/db');
const { logAudit } = require('../utils/audit');
const { inClause } = require('../utils/sql');

// In hours - purge_at = NOW() + INTERVAL ? HOUR needs a numeric unit count, unlike Postgres's
// interval-string-cast approach.
const RETENTION_HOURS = { immediate: null, '24h': 24, '7d': 24 * 7 };

// chat_sessions.start_datetime/end_datetime are DATETIME, filled in by the Android app from the
// device's own local clock (native DatePickerDialog/TimePickerDialog, no timezone info attached)
// - so a literal value like "08:57:04" means 8:57 AM in whatever timezone the admin's phone is set
// to, which for this project is always Philippine time (this app only serves Nueva Vizcaya).
// Comparing that literal value against MySQL's NOW() (server-local, but this server's clock isn't
// guaranteed to be Philippine time either) is wrong - LOCAL_NOW converts the current UTC instant to
// the same naive Philippine-local wall-clock reading the client used, so both sides of every
// start_datetime/end_datetime comparison are in the same frame. Philippine time has no DST, so a
// fixed +8h offset off UTC_TIMESTAMP() (unaffected by session time_zone) is exact.
const LOCAL_NOW = `DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR)`;

async function notifyUsers(userIds, sessionId, message) {
  if (!userIds.length) return;
  const values = userIds.map(() => '(?, ?, ?)').join(', ');
  const params = userIds.flatMap((userId) => [userId, sessionId, message]);
  await pool.query(`INSERT INTO chat_notifications (user_id, session_id, message) VALUES ${values}`, params);
}

async function notifyAllActiveUsers(sessionId, message) {
  const { rows } = await pool.query('SELECT id FROM users WHERE is_active = true');
  await notifyUsers(rows.map((r) => r.id), sessionId, message);
}

async function notifyParticipants(sessionId, message) {
  const { rows } = await pool.query('SELECT DISTINCT user_id FROM chat_participants WHERE session_id = ?', [sessionId]);
  await notifyUsers(rows.map((r) => r.user_id), sessionId, message);
}

// Both schedule types turn into a real chat_sessions row on the days they're due, gated on "does a
// session already exist for this schedule today" (a live NOT EXISTS check against chat_sessions,
// not a one-way date flag) - self-healing if an admin deletes a generated session mid-day, and
// catches up if the scheduler tick was missed (server restart, schedule created mid-day) instead
// of only ever generating right at midnight.
//
// end_time <= start_time means the schedule spans past midnight (e.g. 10 PM - 2 AM) rather than
// being invalid (see validateSingleSchedule/validateRecurringSchedule in chat.controller.js) -
// overnightEnd() below adds a day to end_time's date in that case, everywhere end_datetime is
// computed or compared, so both the generation cutoff and the stored session correctly reflect a
// real multi-hour window instead of one that (read as same-day times) would already look expired.
// MySQL has no date+time `+` operator like Postgres, so TIMESTAMP(date, time) joins the two.
const overnightEnd = (alias) =>
  `(TIMESTAMP(DATE(${LOCAL_NOW}), ${alias}.end_time) + INTERVAL (CASE WHEN ${alias}.end_time <= ${alias}.start_time THEN 1 ELSE 0 END) DAY)`;

// Single-time schedules take priority over recurring ones for the one date they're active on
// (spec: Single-Time > Recurring > Chat Closed) - generateSingleTimeSessions() runs first each
// tick, then generateRecurringSessions() explicitly skips any day that already has an active
// single-time schedule, regardless of whether that schedule's own session has been generated yet.
async function generateSingleTimeSessions() {
  const { rows } = await pool.query(
    `SELECT id, session_name, remarks, start_time, end_time, retention_policy, created_by
     FROM chat_single_schedules
     WHERE is_active = true AND status = 'scheduled' AND schedule_date = DATE(${LOCAL_NOW})
       AND ${overnightEnd('chat_single_schedules')} > ${LOCAL_NOW}
       AND NOT EXISTS (
         SELECT 1 FROM chat_sessions cs
         WHERE cs.single_schedule_id = chat_single_schedules.id AND DATE(cs.start_datetime) = DATE(${LOCAL_NOW})
       )`
  );
  for (const schedule of rows) {
    const result = await pool.query(
      `INSERT INTO chat_sessions (session_name, description, start_datetime, end_datetime, retention_policy, created_by, single_schedule_id)
       VALUES (
         ?, ?, TIMESTAMP(DATE(${LOCAL_NOW}), ?),
         TIMESTAMP(DATE(${LOCAL_NOW}), ?) + INTERVAL (CASE WHEN ? <= ? THEN 1 ELSE 0 END) DAY,
         ?, ?, ?
       )`,
      [schedule.session_name, schedule.remarks, schedule.start_time,
        schedule.end_time, schedule.end_time, schedule.start_time,
        schedule.retention_policy, schedule.created_by, schedule.id]
    );
    const sessionId = result.rows.insertId;
    await logAudit(schedule.created_by, 'CHAT_SESSION_CREATED', 'chat_session', sessionId, { session_name: schedule.session_name, auto: true, single: true });
    await notifyAllActiveUsers(sessionId, `Chat session "${schedule.session_name}" is scheduled for today.`);
  }
}

async function generateRecurringSessions() {
  const { rows } = await pool.query(
    `SELECT rs.id, rs.session_name, rs.description, rs.start_time, rs.end_time, rs.retention_policy, rs.created_by
     FROM chat_recurring_schedules rs
     WHERE rs.is_active = true
       AND JSON_CONTAINS(rs.days_of_week, CAST((DAYOFWEEK(${LOCAL_NOW}) - 1) AS JSON))
       AND ${overnightEnd('rs')} > ${LOCAL_NOW}
       AND NOT EXISTS (
         SELECT 1 FROM chat_sessions cs
         WHERE cs.recurring_schedule_id = rs.id AND DATE(cs.start_datetime) = DATE(${LOCAL_NOW})
       )
       AND NOT EXISTS (
         SELECT 1 FROM chat_single_schedules ss
         WHERE ss.schedule_date = DATE(${LOCAL_NOW}) AND ss.is_active = true
       )`
  );
  for (const schedule of rows) {
    const result = await pool.query(
      `INSERT INTO chat_sessions (session_name, description, start_datetime, end_datetime, retention_policy, created_by, recurring_schedule_id)
       VALUES (
         ?, ?, TIMESTAMP(DATE(${LOCAL_NOW}), ?),
         TIMESTAMP(DATE(${LOCAL_NOW}), ?) + INTERVAL (CASE WHEN ? <= ? THEN 1 ELSE 0 END) DAY,
         ?, ?, ?
       )`,
      [schedule.session_name, schedule.description, schedule.start_time,
        schedule.end_time, schedule.end_time, schedule.start_time,
        schedule.retention_policy, schedule.created_by, schedule.id]
    );
    await pool.query(
      `UPDATE chat_recurring_schedules SET last_generated_date = DATE(${LOCAL_NOW}) WHERE id = ?`,
      [schedule.id]
    );
    const sessionId = result.rows.insertId;
    await logAudit(schedule.created_by, 'CHAT_SESSION_CREATED', 'chat_session', sessionId, { session_name: schedule.session_name, auto: true, recurring: true });
    await notifyAllActiveUsers(sessionId, `Chat session "${schedule.session_name}" is scheduled for today.`);
  }
}

// Archives any single-time schedule whose date has passed - not deleted, just marked 'completed'
// so it stays in chat_single_schedules for audit history. Nothing needs to "reactivate" the
// recurring schedule for that weekday: the exclusion in generateRecurringSessions() above is
// scoped to today's date each tick, so it naturally stops applying once the date has passed.
async function completeExpiredSingleSchedules() {
  const { rows: due } = await pool.query(
    `SELECT id, session_name, created_by FROM chat_single_schedules
     WHERE status = 'scheduled' AND schedule_date < DATE(${LOCAL_NOW})`
  );
  if (due.length) {
    const { sql, params } = inClause(due.map((s) => s.id));
    await pool.query(`UPDATE chat_single_schedules SET status = 'completed' WHERE id IN (${sql})`, params);
  }
  for (const schedule of due) {
    await logAudit(schedule.created_by, 'CHAT_SINGLE_SCHEDULE_EXPIRED', 'chat_single_schedule', schedule.id, { session_name: schedule.session_name, auto: true });
  }
}

// Promotes any 'scheduled' session whose start time has arrived to 'open', so the chat becomes
// available exactly per its configured schedule without requiring an admin to be online to click
// "Open" at the right moment.
async function autoOpenSessions() {
  const { rows: due } = await pool.query(
    `SELECT id, session_name FROM chat_sessions WHERE status = 'scheduled' AND start_datetime <= ${LOCAL_NOW}`
  );
  if (due.length) {
    const { sql, params } = inClause(due.map((s) => s.id));
    await pool.query(`UPDATE chat_sessions SET status = 'open' WHERE id IN (${sql})`, params);
  }
  for (const session of due) {
    await logAudit(null, 'CHAT_SESSION_OPENED', 'chat_session', session.id, { auto: true });
    await notifyAllActiveUsers(session.id, `Chat session "${session.session_name}" is now open.`);
  }
}

// Fires the "5 minutes remaining" notification exactly once per session (five_min_warning_sent
// guards against re-notifying every time this job runs while still inside that 5-minute window).
async function sendFiveMinuteWarnings() {
  const { rows: due } = await pool.query(
    `SELECT id, session_name FROM chat_sessions
     WHERE status = 'open' AND five_min_warning_sent = false
       AND end_datetime > ${LOCAL_NOW} AND end_datetime <= ${LOCAL_NOW} + INTERVAL 5 MINUTE`
  );
  if (due.length) {
    const { sql, params } = inClause(due.map((s) => s.id));
    await pool.query(`UPDATE chat_sessions SET five_min_warning_sent = true WHERE id IN (${sql})`, params);
  }
  for (const session of due) {
    await notifyParticipants(session.id, `Chat session "${session.session_name}" closes in 5 minutes.`);
  }
}

// The core auto-expiration behavior (spec section 7/16): once end_datetime passes, the room locks,
// new messages are refused (sendMessage already checks status = 'open'), and - per the configured
// retention_policy - messages are either wiped immediately or scheduled for a later purge so the
// "recommended enhancement" retention window still applies. Participants are notified either way.
async function expireSessions() {
  const { rows } = await pool.query(
    `SELECT id, session_name, retention_policy FROM chat_sessions
     WHERE status IN ('open', 'closed') AND end_datetime <= ${LOCAL_NOW}`
  );
  for (const session of rows) {
    const hours = RETENTION_HOURS[session.retention_policy];
    if (hours) {
      await pool.query(
        `UPDATE chat_sessions SET status = 'expired', expired_at = NOW(), purge_at = NOW() + INTERVAL ? HOUR WHERE id = ?`,
        [hours, session.id]
      );
    } else {
      await pool.query(
        `UPDATE chat_sessions SET status = 'expired', expired_at = NOW(), purge_at = NOW(), messages_purged = true WHERE id = ?`,
        [session.id]
      );
      const deleted = await pool.query('DELETE FROM chat_messages WHERE session_id = ?', [session.id]);
      if (deleted.rows.affectedRows) {
        await logAudit(null, 'CHAT_MESSAGES_AUTO_DELETED', 'chat_session', session.id, { count: deleted.rows.affectedRows, auto: true });
      }
    }
    await pool.query('UPDATE chat_participants SET left_at = NOW() WHERE session_id = ? AND left_at IS NULL', [session.id]);
    await logAudit(null, 'CHAT_AUTO_EXPIRED', 'chat_session', session.id, { auto: true, retention_policy: session.retention_policy });
    await notifyParticipants(session.id, `Chat session "${session.session_name}" has ended.`);
  }
}

// Second-stage cleanup for the 24h/7d retention policies - the messages stayed readable for
// auditing until purge_at, and get deleted here once that window elapses. Immediate-policy
// sessions never reach this (messages_purged is already true from expireSessions above).
async function purgeExpiredMessages() {
  const { rows } = await pool.query(
    `SELECT id FROM chat_sessions WHERE status = 'expired' AND messages_purged = false AND purge_at IS NOT NULL AND purge_at <= NOW()`
  );
  for (const session of rows) {
    const deleted = await pool.query('DELETE FROM chat_messages WHERE session_id = ?', [session.id]);
    await pool.query('UPDATE chat_sessions SET messages_purged = true WHERE id = ?', [session.id]);
    await logAudit(null, 'CHAT_MESSAGES_AUTO_DELETED', 'chat_session', session.id, { count: deleted.rows.affectedRows, auto: true, retention: true });
  }
}

async function runChatScheduler() {
  try {
    await generateSingleTimeSessions();
    await generateRecurringSessions();
    await autoOpenSessions();
    await sendFiveMinuteWarnings();
    await expireSessions();
    await purgeExpiredMessages();
    await completeExpiredSingleSchedules();
  } catch (err) {
    console.error('Chat scheduler run failed:', err.message);
  }
}

function startChatScheduler() {
  runChatScheduler();
  const ONE_MINUTE_MS = 60 * 1000;
  setInterval(runChatScheduler, ONE_MINUTE_MS);
}

module.exports = { startChatScheduler };
