const path = require('path');
const fs = require('fs');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');

const BACKUP_DIR = path.join(__dirname, '..', '..', 'backups');
if (!fs.existsSync(BACKUP_DIR)) fs.mkdirSync(BACKUP_DIR, { recursive: true });

// The old mysqldump-based backup doesn't carry over to Supabase/Postgres (no
// pg_dump shell-out has been wired up here yet). Stub it rather than silently
// producing an empty/broken backup file. Existing backups already on disk from
// before the migration remain listable/downloadable below.
const backup = asyncHandler(async (req, res) => {
  res.status(501).json({ message: 'Server-side backup is not yet available for the Supabase database. Use Supabase\'s own backup/restore tools in the meantime.' });
});

const listBackups = asyncHandler(async (req, res) => {
  const files = fs.readdirSync(BACKUP_DIR).filter((f) => f.endsWith('.sql'));
  res.json(files);
});

const downloadBackup = asyncHandler(async (req, res) => {
  const filePath = path.join(BACKUP_DIR, req.params.fileName);
  if (!filePath.startsWith(BACKUP_DIR) || !fs.existsSync(filePath)) {
    return res.status(404).json({ message: 'Backup not found' });
  }
  res.download(filePath);
});

const auditLogs = asyncHandler(async (req, res) => {
  const limit = Math.min(Number(req.query.limit) || 100, 500);
  const { rows } = await pool.query(
    `SELECT al.id, al.action, al.entity_type, al.entity_id, al.details, al.created_at,
            u.name AS user_name, u.email AS user_email
     FROM audit_logs al
     LEFT JOIN users u ON u.id = al.user_id
     ORDER BY al.created_at DESC
     LIMIT $1`,
    [limit]
  );
  res.json(rows);
});

module.exports = { backup, listBackups, downloadBackup, auditLogs };
