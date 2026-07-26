const pool = require('../config/db');
const { logAudit } = require('../utils/audit');

const RETENTION_INTERVALS = { immediate: null, '24h': '24 hours', '7d': '7 days' };

// chat_sessions.start_datetime/end_datetime are TIMESTAMP WITHOUT TIME ZONE, filled in by the
// Android app from the device's own local clock (native DatePickerDialog/TimePickerDialog, no
// timezone info attached) - so a literal value like "08:57:04" means 8:57 AM in whatever timezone
// the admin's phone is set to, which for this project is always Philippine time (this app only
// serves Nueva Vizcaya). Comparing that literal value against Postgres's NOW() (an absolute UTC
// instant) is wrong: NOW() only reads "08:57:04" 8 hours after the admin's actual intended moment,
// so sessions would silently fail to open until up to 8 hours after their real scheduled start.
// LOCAL_NOW converts NOW() to the same naive Philippine-local wall-clock reading the client used,
// so both sides of every start_datetime/end_datetime comparison are in the same frame.
const LOCAL_NOW = `(NOW() AT TIME ZONE 'Asia/Manila')`;

async function notifyUsers(userIds, sessionId, message) {
  if (!userIds.length) return;
  const values = userIds.map((_, i) => `($${i * 3 + 1}, $${i * 3 + 2}, $${i * 3 + 3})`).join(', ');
  const params = userIds.flatMap((userId) => [userId, sessionId, message]);
  await pool.query(`INSERT INTO chat_notifications (user_id, session_id, message) VALUES ${values}`, params);
}

async function notifyAllActiveUsers(sessionId, message) {
  const { rows } = await pool.query('SELECT id FROM users WHERE is_active = true');
  await notifyUsers(rows.map((r) => r.id), sessionId, message);
}

async function notifyParticipants(sessionId, message) {
  const { rows } = await pool.query('SELECT DISTINCT user_id FROM chat_participants WHERE session_id = $1', [sessionId]);
  await notifyUsers(rows.map((r) => r.user_id), sessionId, message);
}

// Promotes any 'scheduled' session whose start time has arrived to 'open', so the chat becomes
// available exactly per its configured schedule without requiring an admin to be online to click
// "Open" at the right moment.
async function autoOpenSessions() {
  const { rows } = await pool.query(
    `UPDATE chat_sessions SET status = 'open' WHERE status = 'scheduled' AND start_datetime <= ${LOCAL_NOW} RETURNING id, session_name`
  );
  for (const session of rows) {
    await logAudit(null, 'CHAT_SESSION_OPENED', 'chat_session', session.id, { auto: true });
    await notifyAllActiveUsers(session.id, `Chat session "${session.session_name}" is now open.`);
  }
}

// Fires the "5 minutes remaining" notification exactly once per session (five_min_warning_sent
// guards against re-notifying every time this job runs while still inside that 5-minute window).
async function sendFiveMinuteWarnings() {
  const { rows } = await pool.query(
    `UPDATE chat_sessions
     SET five_min_warning_sent = true
     WHERE status = 'open' AND five_min_warning_sent = false
       AND end_datetime > ${LOCAL_NOW} AND end_datetime <= ${LOCAL_NOW} + INTERVAL '5 minutes'
     RETURNING id, session_name`
  );
  for (const session of rows) {
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
    const interval = RETENTION_INTERVALS[session.retention_policy];
    if (interval) {
      await pool.query(
        `UPDATE chat_sessions SET status = 'expired', expired_at = NOW(), purge_at = NOW() + $2::interval WHERE id = $1`,
        [session.id, interval]
      );
    } else {
      await pool.query(
        `UPDATE chat_sessions SET status = 'expired', expired_at = NOW(), purge_at = NOW(), messages_purged = true WHERE id = $1`,
        [session.id]
      );
      const deleted = await pool.query('DELETE FROM chat_messages WHERE session_id = $1', [session.id]);
      if (deleted.rowCount) {
        await logAudit(null, 'CHAT_MESSAGES_AUTO_DELETED', 'chat_session', session.id, { count: deleted.rowCount, auto: true });
      }
    }
    await pool.query('UPDATE chat_participants SET left_at = NOW() WHERE session_id = $1 AND left_at IS NULL', [session.id]);
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
    const deleted = await pool.query('DELETE FROM chat_messages WHERE session_id = $1', [session.id]);
    await pool.query('UPDATE chat_sessions SET messages_purged = true WHERE id = $1', [session.id]);
    await logAudit(null, 'CHAT_MESSAGES_AUTO_DELETED', 'chat_session', session.id, { count: deleted.rowCount, auto: true, retention: true });
  }
}

async function runChatScheduler() {
  try {
    await autoOpenSessions();
    await sendFiveMinuteWarnings();
    await expireSessions();
    await purgeExpiredMessages();
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
