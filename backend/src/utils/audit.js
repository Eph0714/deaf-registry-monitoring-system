const pool = require('../config/db');

async function logAudit(userId, action, entityType, entityId, details) {
  try {
    await pool.query(
      'INSERT INTO audit_logs (user_id, action, entity_type, entity_id, details) VALUES (?, ?, ?, ?, ?)',
      [userId || null, action, entityType, entityId || null, details ? JSON.stringify(details) : null]
    );
  } catch (err) {
    console.error('Audit log failed:', err.message);
  }
}

module.exports = { logAudit };
