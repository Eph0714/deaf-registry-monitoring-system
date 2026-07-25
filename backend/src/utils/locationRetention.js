const pool = require('../config/db');

const DEFAULT_TTL_MINUTES = 60;
const CHECK_INTERVAL_MS = 5 * 60 * 1000;

/**
 * Nulls out shared_latitude/shared_longitude/shared_location_at for any user whose share
 * is older than the admin-configured TTL (settings.location_share_ttl_minutes). Re-reads the
 * setting on every run (not cached) so an admin lowering/raising it takes effect on the next
 * tick without a server restart. Called once at startup (catches up after a Render free-tier
 * cold start) and then on an interval while the process stays alive - same shape as
 * auditRetention.js.
 */
async function clearExpiredLocations() {
  try {
    const { rows } = await pool.query(
      `SELECT "value" FROM settings WHERE "key" = 'location_share_ttl_minutes'`
    );
    const ttlMinutes = rows.length ? Number(rows[0].value) : DEFAULT_TTL_MINUTES;
    const result = await pool.query(
      `UPDATE users SET shared_latitude = NULL, shared_longitude = NULL, shared_location_at = NULL
       WHERE shared_location_at IS NOT NULL AND shared_location_at <= NOW() - ($1 || ' minutes')::interval`,
      [ttlMinutes]
    );
    if (result.rowCount) {
      console.log(`Location retention: cleared ${result.rowCount} shared location(s) older than ${ttlMinutes} minutes`);
    }
  } catch (err) {
    console.error('Location retention cleanup failed:', err.message);
  }
}

function startLocationRetention() {
  clearExpiredLocations();
  setInterval(clearExpiredLocations, CHECK_INTERVAL_MS);
}

module.exports = { startLocationRetention };
