require('dotenv').config();
const fs = require('fs');
const path = require('path');
const { Client } = require('pg');

async function main() {
  const client = new Client({
    host: process.env.DB_HOST,
    port: Number(process.env.DB_PORT || 5432),
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME,
    ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false
  });
  await client.connect();

  const schemaSql = fs.readFileSync(path.join(__dirname, 'schema.pg.sql'), 'utf8');
  await client.query(schemaSql);

  await ensureColumns(client);
  await ensureRoleAllowsSuperAdmin(client);
  await ensureUsernames(client);

  console.log('Migration complete: schema applied to', process.env.DB_NAME);
  await client.end();
}

/**
 * Adds columns to already-existing tables that CREATE TABLE IF NOT EXISTS can't
 * retrofit. Checks INFORMATION_SCHEMA.COLUMNS first (available in Postgres too)
 * and only alters when a column is missing, so this stays safe to re-run.
 */
async function ensureColumns(client) {
  const columnsToAdd = [
    { table: 'deaf_individuals', column: 'birth_date', definition: 'DATE NULL' },
    { table: 'deaf_individuals', column: 'contact_number', definition: 'VARCHAR(30) NULL' },
    { table: 'deaf_individuals', column: 'email', definition: 'VARCHAR(190) NULL' },
    { table: 'users', column: 'photo_url', definition: 'VARCHAR(255) NULL' },
    {
      table: 'deaf_individuals',
      column: 'marital_status',
      definition: "VARCHAR(20) NULL CHECK (marital_status IS NULL OR marital_status IN ('Single','Married','Widowed','Separated','Divorced'))"
    },
    { table: 'deaf_individuals', column: 'emergency_contact_name', definition: 'VARCHAR(150) NULL' },
    { table: 'deaf_individuals', column: 'emergency_contact_number', definition: 'VARCHAR(30) NULL' },
    { table: 'users', column: 'email_verified', definition: 'BOOLEAN NOT NULL DEFAULT true' },
    { table: 'users', column: 'email_verification_token', definition: 'VARCHAR(64) NULL' },
    { table: 'users', column: 'email_verification_expires', definition: 'TIMESTAMP NULL' },
    {
      table: 'users',
      column: 'approval_status',
      definition: "VARCHAR(20) NOT NULL DEFAULT 'approved' CHECK (approval_status IN ('pending','approved','rejected'))"
    },
    { table: 'users', column: 'contact_number', definition: 'VARCHAR(30) NULL' },
    { table: 'users', column: 'location', definition: 'VARCHAR(255) NULL' },
    { table: 'users', column: 'shared_latitude', definition: 'DOUBLE PRECISION NULL' },
    { table: 'users', column: 'shared_longitude', definition: 'DOUBLE PRECISION NULL' },
    { table: 'users', column: 'shared_location_at', definition: 'TIMESTAMP NULL' },
    { table: 'users', column: 'last_login_at', definition: 'TIMESTAMP NULL' },
    { table: 'users', column: 'username', definition: 'VARCHAR(50) NULL' }
  ];

  for (const { table, column, definition } of columnsToAdd) {
    const { rows } = await client.query(
      `SELECT column_name FROM information_schema.columns
       WHERE table_schema = 'public' AND table_name = $1 AND column_name = $2`,
      [table, column]
    );
    if (!rows.length) {
      await client.query(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
      console.log(`Added column ${table}.${column}`);
    }
  }
}

/**
 * CREATE TABLE IF NOT EXISTS can't retrofit a changed CHECK constraint on an
 * already-existing table, so this drops and recreates it - safe to re-run.
 */
async function ensureRoleAllowsSuperAdmin(client) {
  await client.query('ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check');
  await client.query(
    "ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('admin','conductor','super_admin'))"
  );
}

/**
 * Backfills username for any pre-existing row that doesn't have one yet (every account created
 * before this column existed), derived from the local part of its email, deduped against every
 * other username already taken (including ones just assigned earlier in this same loop) by
 * appending the row's id when there's a collision. Then adds a UNIQUE constraint - safe to re-run,
 * and wrapped so a constraint failure (e.g. a leftover duplicate) logs instead of aborting the
 * whole migration, since every other step here already succeeded by this point.
 */
async function ensureUsernames(client) {
  const { rows: taken } = await client.query('SELECT username FROM users WHERE username IS NOT NULL');
  const usedUsernames = new Set(taken.map((r) => r.username));

  const { rows: missing } = await client.query('SELECT id, email FROM users WHERE username IS NULL');
  for (const { id, email } of missing) {
    let candidate = (email.split('@')[0] || `user${id}`).toLowerCase().replace(/[^a-z0-9._-]/g, '');
    if (!candidate) candidate = `user${id}`;
    let username = candidate;
    let suffix = 1;
    while (usedUsernames.has(username)) {
      username = `${candidate}${id}${suffix > 1 ? suffix : ''}`;
      suffix += 1;
    }
    usedUsernames.add(username);
    await client.query('UPDATE users SET username = $1 WHERE id = $2', [username, id]);
    console.log(`Backfilled username for user ${id}: ${username}`);
  }

  try {
    await client.query('ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_unique');
    await client.query('ALTER TABLE users ADD CONSTRAINT users_username_unique UNIQUE (username)');
  } catch (err) {
    console.error('Could not add users_username_unique constraint (check for duplicate usernames):', err.message);
  }
}

main().catch((err) => {
  console.error('Migration failed:', err);
  process.exit(1);
});
