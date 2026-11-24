require('dotenv').config();
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

async function main() {
  const connection = await mysql.createConnection({
    host: process.env.DB_HOST,
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME,
    multipleStatements: true
  });

  const schemaSql = fs.readFileSync(path.join(__dirname, 'schema.mysql.sql'), 'utf8');
  await connection.query(schemaSql);

  // One-off, idempotent-safe retrofits for tables that already existed before a column/
  // constraint was added to their CREATE TABLE IF NOT EXISTS definition above - that clause
  // only takes effect for a brand-new table, so an already-live table needs an explicit ALTER.
  await connection.query('ALTER TABLE artwork_folders ADD COLUMN IF NOT EXISTS parent_id INT NULL AFTER id');
  await connection.query('ALTER TABLE artwork_folders ADD INDEX IF NOT EXISTS idx_artwork_folders_parent (parent_id)');
  try {
    await connection.query(
      'ALTER TABLE artwork_folders ADD CONSTRAINT fk_artwork_folders_parent FOREIGN KEY (parent_id) REFERENCES artwork_folders(id) ON DELETE RESTRICT'
    );
  } catch (err) {
    if (err.errno !== 1826 && err.errno !== 1061 && err.code !== 'ER_DUP_KEYNAME' && err.code !== 'ER_FK_DUP_NAME') throw err;
  }

  console.log('Migration complete: schema applied to', process.env.DB_NAME);
  await connection.end();
}

main().catch((err) => {
  console.error('Migration failed:', err);
  process.exit(1);
});
