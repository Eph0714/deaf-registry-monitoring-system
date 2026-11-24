const pool = require('../config/db');

// Generic per-user notification feed, separate from chat_notifications (which is hard-wired to
// chat_sessions via an ON DELETE CASCADE FK and isn't a fit for non-chat events like these).
async function createNotification(userId, type, entityType, entityId, message) {
  await pool.query(
    'INSERT INTO notifications (user_id, type, entity_type, entity_id, message) VALUES (?, ?, ?, ?, ?)',
    [userId, type, entityType, entityId || null, message]
  );
}

async function notifyAdmins(type, entityType, entityId, message) {
  const { rows } = await pool.query("SELECT id FROM users WHERE role IN ('admin','super_admin') AND is_active = true");
  await Promise.all(rows.map((r) => createNotification(r.id, type, entityType, entityId, message)));
}

module.exports = { createNotification, notifyAdmins };
