const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const SORT_COLUMNS = { name: 'f.name', created_at: 'f.created_at', updated_at: 'f.updated_at' };

// Folder cover image and image count are deliberately not stored columns - they're computed
// live here so they can never drift as artworks are uploaded, approved, moved or deleted.
const FOLDER_SELECT = `
  SELECT f.id, f.name, f.created_by, f.created_at, f.updated_at,
    (SELECT COUNT(*) FROM artworks a WHERE a.folder_id = f.id AND a.deleted_status != 'deleted') AS image_count,
    (SELECT a.public_url_thumbnail FROM artworks a
       WHERE a.folder_id = f.id AND a.publish_status = 'published' AND a.deleted_status != 'deleted'
       ORDER BY a.uploaded_at DESC LIMIT 1) AS cover_url
  FROM artwork_folders f
  WHERE f.is_deleted = false
`;

const list = asyncHandler(async (req, res) => {
  const { search, sortBy = 'name', order = 'asc' } = req.query;
  const column = SORT_COLUMNS[sortBy] || SORT_COLUMNS.name;
  const direction = order === 'desc' ? 'DESC' : 'ASC';

  const params = [];
  let sql = FOLDER_SELECT;
  if (search) {
    sql += ' AND f.name LIKE ?';
    params.push(`%${search}%`);
  }
  sql += ` ORDER BY ${column} ${direction}`;

  const { rows } = await pool.query(sql, params);
  res.json(rows);
});

const getById = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`${FOLDER_SELECT} AND f.id = ?`, [req.params.id]);
  if (!rows.length) return res.status(404).json({ message: 'Folder not found' });
  res.json(rows[0]);
});

const create = asyncHandler(async (req, res) => {
  const { name } = req.body;
  if (!name || !name.trim()) return res.status(400).json({ message: 'Folder name is required' });

  const { rows } = await pool.query(
    'INSERT INTO artwork_folders (name, created_by) VALUES (?, ?)',
    [name.trim(), req.user.id]
  );
  const insertId = rows.insertId;
  await logAudit(req.user.id, 'ARTWORK_FOLDER_CREATED', 'artwork_folder', insertId, { name: name.trim() });
  res.status(201).json({ id: insertId, name: name.trim() });
});

const rename = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { name } = req.body;
  if (!name || !name.trim()) return res.status(400).json({ message: 'Folder name is required' });

  const result = await pool.query(
    'UPDATE artwork_folders SET name = ? WHERE id = ? AND is_deleted = false',
    [name.trim(), id]
  );
  if (!result.rows.affectedRows) return res.status(404).json({ message: 'Folder not found' });
  await logAudit(req.user.id, 'ARTWORK_FOLDER_RENAMED', 'artwork_folder', id, { name: name.trim() });
  res.json({ id: Number(id), name: name.trim() });
});

const remove = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows: countRows } = await pool.query(
    `SELECT COUNT(*) AS count FROM artworks WHERE folder_id = ? AND deleted_status != 'deleted'`,
    [id]
  );
  if (countRows[0].count > 0) {
    return res.status(409).json({ message: 'Move or remove this folder’s artworks before deleting it' });
  }

  const result = await pool.query(
    'UPDATE artwork_folders SET is_deleted = true WHERE id = ? AND is_deleted = false',
    [id]
  );
  if (!result.rows.affectedRows) return res.status(404).json({ message: 'Folder not found' });
  await logAudit(req.user.id, 'ARTWORK_FOLDER_DELETED', 'artwork_folder', id, null);
  res.json({ id: Number(id), deleted: true });
});

module.exports = { list, getById, create, rename, remove };
