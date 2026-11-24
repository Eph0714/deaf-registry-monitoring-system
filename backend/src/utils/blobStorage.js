const crypto = require('crypto');
const pool = require('../config/db');

// See schema.mysql.sql's file_blobs comment for why files live in the database rather than on
// disk or an external service on this host. `path` is an internal identifier (used only to find
// a specific blob again later, e.g. to delete it); `token` is the public, unguessable id a URL
// is built from.
async function putFile(path, buffer, mime, ownerId) {
  const token = crypto.randomBytes(24).toString('base64url');
  await pool.query(
    'INSERT INTO file_blobs (path, token, owner, mime, size_bytes, data) VALUES (?, ?, ?, ?, ?, ?)',
    [path, token, ownerId ?? null, mime, buffer.length, buffer]
  );
  return token;
}

async function getFileByToken(token) {
  const { rows } = await pool.query('SELECT mime, data FROM file_blobs WHERE token = ? LIMIT 1', [token]);
  return rows.length ? rows[0] : null;
}

async function deleteFile(path) {
  await pool.query('DELETE FROM file_blobs WHERE path = ?', [path]);
}

function publicUrlFor(token) {
  const base = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
  return `${base}/api/files/${token}`;
}

module.exports = { putFile, getFileByToken, deleteFile, publicUrlFor };
