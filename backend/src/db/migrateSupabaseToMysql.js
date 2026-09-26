/**
 * One-off data migration: copies every row from the existing Supabase Postgres
 * database into the new Hostinger MySQL database, and re-hosts photo files
 * from Supabase Storage onto local disk (backend/uploads/photos/).
 *
 * Read-only against Supabase - only ever SELECTs there. Run manually, once,
 * from this machine:
 *
 *   node src/db/migrateSupabaseToMysql.js
 *
 * Uses its own env vars (independent of config/db.js, which now only knows
 * about the MySQL side) so it's unambiguous which database is source vs
 * destination regardless of what the local .env's DB_* currently point to:
 *
 *   SUPABASE_DB_HOST, SUPABASE_DB_PORT, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD,
 *   SUPABASE_DB_NAME, SUPABASE_DB_SSL   (source, Postgres)
 *   MYSQL_DB_HOST, MYSQL_DB_PORT, MYSQL_DB_USER, MYSQL_DB_PASSWORD, MYSQL_DB_NAME
 *   (destination, the Hostinger MySQL database created via databases/setup)
 */
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const https = require('https');
const http = require('http');
const { Pool } = require('pg');
const mysql = require('mysql2/promise');

const UPLOAD_DIR = path.join(__dirname, '..', '..', 'uploads', 'photos');

// Order matters - each table only references ones already copied before it.
const TABLES = [
  'municipalities',
  'barangays',
  'teachers',
  'users',
  'deaf_individuals',
  'visits',
  'remarks',
  'user_devices',
  'password_reset_requests',
  'teacher_assignment_history',
  'settings',
  'calendar_events',
  'audit_logs',
  'chat_recurring_schedules',
  'chat_single_schedules',
  'chat_sessions',
  'chat_messages',
  'chat_participants',
  'chat_notifications'
];

// The only Postgres array column in the schema - stored as JSON in MySQL.
const JSON_ARRAY_COLUMNS = { chat_recurring_schedules: ['days_of_week'] };

// Columns holding a photo URL that needs re-hosting from Supabase Storage to local disk.
const PHOTO_URL_COLUMNS = { users: ['photo_url'], deaf_individuals: ['photo_url'] };

function downloadFile(url) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith('https:') ? https : http;
    client.get(url, (res) => {
      if (res.statusCode !== 200) {
        res.resume();
        return reject(new Error(`Download failed (${res.statusCode}) for ${url}`));
      }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
      res.on('error', reject);
    }).on('error', reject);
  });
}

async function rehostPhoto(url) {
  if (!url) return url;
  try {
    const buffer = await downloadFile(url);
    const ext = path.extname(new URL(url).pathname) || '.jpg';
    const objectName = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}${ext}`;
    fs.mkdirSync(UPLOAD_DIR, { recursive: true });
    fs.writeFileSync(path.join(UPLOAD_DIR, objectName), buffer);
    const base = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
    return `${base}/uploads/photos/${objectName}`;
  } catch (err) {
    console.error(`  ! Could not re-host photo ${url}: ${err.message} (keeping original URL)`);
    return url;
  }
}

async function migrateTable(pgClient, mysqlConn, table) {
  const { rows } = await pgClient.query(`SELECT * FROM ${table} ORDER BY 1`);
  if (!rows.length) {
    console.log(`${table}: 0 rows`);
    return;
  }

  const jsonColumns = JSON_ARRAY_COLUMNS[table] || [];
  const photoColumns = PHOTO_URL_COLUMNS[table] || [];

  for (const row of rows) {
    for (const col of jsonColumns) {
      if (row[col] !== null && row[col] !== undefined) row[col] = JSON.stringify(row[col]);
    }
    for (const col of photoColumns) {
      if (row[col]) row[col] = await rehostPhoto(row[col]);
    }
  }

  const columns = Object.keys(rows[0]);
  const placeholders = `(${columns.map(() => '?').join(', ')})`;
  const sql = `INSERT INTO ${table} (${columns.map((c) => `\`${c}\``).join(', ')}) VALUES ${rows.map(() => placeholders).join(', ')}`;
  const params = rows.flatMap((row) => columns.map((c) => row[c]));

  await mysqlConn.query(sql, params);

  if (columns.includes('id')) {
    const maxId = Math.max(...rows.map((r) => r.id));
    await mysqlConn.query(`ALTER TABLE ${table} AUTO_INCREMENT = ?`, [maxId + 1]);
  }

  console.log(`${table}: copied ${rows.length} row(s)`);
}

async function main() {
  const pgPool = new Pool({
    host: process.env.SUPABASE_DB_HOST,
    port: Number(process.env.SUPABASE_DB_PORT || 5432),
    user: process.env.SUPABASE_DB_USER,
    password: process.env.SUPABASE_DB_PASSWORD,
    database: process.env.SUPABASE_DB_NAME,
    ssl: process.env.SUPABASE_DB_SSL === 'true' ? { rejectUnauthorized: false } : false
  });
  const pgClient = await pgPool.connect();

  // A pool, not a single long-lived connection - the per-row photo re-hosting downloads between
  // table batches can take a while, and an idle connection gets dropped by the server in that gap.
  const mysqlPool = mysql.createPool({
    host: process.env.MYSQL_DB_HOST,
    port: Number(process.env.MYSQL_DB_PORT || 3306),
    user: process.env.MYSQL_DB_USER,
    password: process.env.MYSQL_DB_PASSWORD,
    database: process.env.MYSQL_DB_NAME,
    connectionLimit: 3
  });

  try {
    for (const table of TABLES) {
      await migrateTable(pgClient, mysqlPool, table);
    }
    console.log('Data migration complete.');
  } finally {
    pgClient.release();
    await pgPool.end();
    await mysqlPool.end();
  }
}

main().catch((err) => {
  console.error('Data migration failed:', err);
  process.exit(1);
});
