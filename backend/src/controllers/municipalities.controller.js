const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const list = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT m.id, m.name, m.updated_at,
           COUNT(d.id) AS deaf_count
    FROM municipalities m
    LEFT JOIN deaf_individuals d ON d.municipality_id = m.id AND d.is_deleted = false
    GROUP BY m.id, m.name, m.updated_at
    ORDER BY m.name ASC
  `);
  res.json(rows);
});

const create = asyncHandler(async (req, res) => {
  const { name } = req.body;
  if (!name) return res.status(400).json({ message: 'name is required' });
  const { rows } = await pool.query('INSERT INTO municipalities (name) VALUES (?)', [name]);
  const insertId = rows.insertId;
  await logAudit(req.user.id, 'CREATE', 'municipality', insertId, { name });
  res.status(201).json({ id: insertId, name });
});

const update = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { name } = req.body;
  await pool.query('UPDATE municipalities SET name = ? WHERE id = ?', [name, id]);
  await logAudit(req.user.id, 'UPDATE', 'municipality', id, { name });
  res.json({ id: Number(id), name });
});

const remove = asyncHandler(async (req, res) => {
  const { id } = req.params;
  await pool.query('DELETE FROM municipalities WHERE id = ?', [id]);
  await logAudit(req.user.id, 'DELETE', 'municipality', id, null);
  res.status(204).send();
});

// Unauthenticated - used by the public Sign Up form's Municipality dropdown, before the user has
// an account/token. Deliberately returns only id/name (not the deaf_count the authenticated list
// endpoint includes) - registry size by municipality isn't something to expose pre-login.
const publicList = asyncHandler(async (req, res) => {
  const { rows } = await pool.query('SELECT id, name FROM municipalities ORDER BY name ASC');
  res.json(rows);
});

module.exports = { list, create, update, remove, publicList };
