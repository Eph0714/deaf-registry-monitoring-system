require('dotenv').config();
const { Pool, types } = require('pg');

// Preserve the same "return dates as plain strings" behavior the app relied on with
// mysql2's `dateStrings: true` — the Android client parses specific string shapes
// (e.g. "YYYY-MM-DD" for birth_date, "YYYY-MM-DD HH:MM:SS" for timestamps), and pg's
// default behavior of parsing these into JS Date objects would silently break that
// (plus introduce timezone-shift bugs for DATE columns).
const DATE_OID = 1082;
const TIMESTAMP_OID = 1114;
types.setTypeParser(DATE_OID, (val) => val);
types.setTypeParser(TIMESTAMP_OID, (val) => val);

const pool = new Pool({
  host: process.env.DB_HOST,
  port: Number(process.env.DB_PORT || 5432),
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  max: 10,
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false
});

module.exports = pool;
