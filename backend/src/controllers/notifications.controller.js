const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { inClause } = require('../utils/sql');

// Mirrors chat.controller.js's getNotifications/markNotificationsRead shape, against the
// generic `notifications` table instead of the chat-specific `chat_notifications` one.
const getNotifications = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT * FROM notifications WHERE user_id = ? AND status = 'unread' ORDER BY created_at DESC LIMIT 50`,
    [req.user.id]
  );
  res.json(rows);
});

const markNotificationsRead = asyncHandler(async (req, res) => {
  const ids = Array.isArray(req.body.ids) && req.body.ids.length ? req.body.ids : null;
  if (ids) {
    const { sql, params } = inClause(ids);
    await pool.query(`UPDATE notifications SET status = 'read' WHERE user_id = ? AND id IN (${sql})`, [req.user.id, ...params]);
  } else {
    await pool.query(`UPDATE notifications SET status = 'read' WHERE user_id = ?`, [req.user.id]);
  }
  res.status(204).send();
});

module.exports = { getNotifications, markNotificationsRead };
