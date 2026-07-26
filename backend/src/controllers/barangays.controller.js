const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const list = asyncHandler(async (req, res) => {
  const { municipality_id } = req.query;
  const conditions = [];
  const params = [];
  if (municipality_id) {
    params.push(municipality_id);
    conditions.push(`b.municipality_id = $${params.length}`);
  }
  const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
  const { rows } = await pool.query(
    `SELECT b.id, b.name, b.municipality_id, m.name AS municipality_name, b.updated_at
     FROM barangays b JOIN municipalities m ON m.id = b.municipality_id
     ${where}
     ORDER BY b.name ASC`,
    params
  );
  res.json(rows);
});

const create = asyncHandler(async (req, res) => {
  const { name, municipality_id } = req.body;
  if (!name || !municipality_id) return res.status(400).json({ message: 'name and municipality_id are required' });
  const { rows } = await pool.query('INSERT INTO barangays (name, municipality_id) VALUES ($1, $2) RETURNING id', [name, municipality_id]);
  const insertId = rows[0].id;
  await logAudit(req.user.id, 'CREATE', 'barangay', insertId, { name, municipality_id });
  res.status(201).json({ id: insertId, name, municipality_id });
});

const update = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { name, municipality_id } = req.body;
  await pool.query('UPDATE barangays SET name = $1, municipality_id = $2 WHERE id = $3', [name, municipality_id, id]);
  await logAudit(req.user.id, 'UPDATE', 'barangay', id, { name, municipality_id });
  res.json({ id: Number(id), name, municipality_id });
});

const remove = asyncHandler(async (req, res) => {
  const { id } = req.params;
  await pool.query('DELETE FROM barangays WHERE id = $1', [id]);
  await logAudit(req.user.id, 'DELETE', 'barangay', id, null);
  res.status(204).send();
});

// Unauthenticated - used by the public Sign Up form's Barangay dropdown, filtered by the
// Municipality already chosen there. Same "no sensitive stats" reasoning as
// municipalities.controller.js::publicList - just id/name.
const publicList = asyncHandler(async (req, res) => {
  const { municipality_id } = req.query;
  if (!municipality_id) return res.status(400).json({ message: 'municipality_id is required' });
  const { rows } = await pool.query(
    'SELECT id, name FROM barangays WHERE municipality_id = $1 ORDER BY name ASC',
    [municipality_id]
  );
  res.json(rows);
});

module.exports = { list, create, update, remove, publicList };
