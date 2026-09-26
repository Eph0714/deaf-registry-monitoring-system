require('dotenv').config();
const mysql = require('mysql2/promise');

// Mirrors two behaviors this project relied on the last time it ran on MySQL
// (before its earlier move to Postgres/Supabase):
//   * dateStrings - the Android client parses specific string shapes (e.g.
//     "YYYY-MM-DD" for birth_date, "YYYY-MM-DD HH:MM:SS" for timestamps), and
//     mysql2's default behavior of parsing these into JS Date objects would
//     silently break that (plus introduce timezone-shift bugs for DATE columns).
//   * TINYINT(1) -> boolean - the app reads columns like is_active/is_deleted/
//     email_verified/is_muted/is_removed expecting real JS booleans, which is
//     what the Postgres driver gave natively; mysql2 returns TINYINT(1) as a
//     0/1 number by default, so it's cast explicitly here instead.
function typeCast(field, next) {
  if (field.type === 'TINY' && field.length === 1) {
    const value = field.string();
    return value === null ? null : value === '1';
  }
  return next();
}

const pool = mysql.createPool({
  host: process.env.DB_HOST,
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  connectionLimit: 10,
  dateStrings: true,
  typeCast,
  // CLIENT_FOUND_ROWS: this codebase's UPDATE call sites check the affected-row
  // count to decide "not found" (a pattern written against Postgres's rowCount,
  // which counts matched rows). Without this flag, MySQL's affectedRows counts
  // only rows actually changed, so a no-op UPDATE (submitted data == current
  // data) would wrongly read as 404.
  flags: '+FOUND_ROWS'
});

module.exports = {
  async query(text, params) {
    const [rows] = await pool.query(text, params);
    return { rows };
  },
  // pg.Pool-shaped: used by call sites that run a multi-statement transaction
  // (BEGIN/COMMIT/ROLLBACK, both valid MySQL syntax too) on one connection.
  async connect() {
    const connection = await pool.getConnection();
    return {
      async query(text, params) {
        const [rows] = await connection.query(text, params);
        return { rows };
      },
      release: () => connection.release()
    };
  },
  end: () => pool.end()
};
