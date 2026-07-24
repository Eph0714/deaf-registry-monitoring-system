const pool = require('../config/db');

const RETENTION_DAYS = 7;

/**
 * Deletes audit_logs rows older than RETENTION_DAYS. Called once at server startup (so a
 * cold start after Render's free tier put the process to sleep still catches up) and then
 * on an interval while the process keeps running.
 */
async function cleanupOldAuditLogs() {
  try {
    const result = await pool.query(
      `DELETE FROM audit_logs WHERE created_at < NOW() - ($1 || ' days')::interval`,
      [RETENTION_DAYS]
    );
    if (result.rowCount) {
      console.log(`Audit log retention: removed ${result.rowCount} entries older than ${RETENTION_DAYS} days`);
    }
  } catch (err) {
    console.error('Audit log retention cleanup failed:', err.message);
  }
}

function startAuditLogRetention() {
  cleanupOldAuditLogs();
  const TWELVE_HOURS_MS = 12 * 60 * 60 * 1000;
  setInterval(cleanupOldAuditLogs, TWELVE_HOURS_MS);
}

module.exports = { startAuditLogRetention };
