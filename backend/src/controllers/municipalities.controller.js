const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const list = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT m.id, m.name, m.updated_at,
           COUNT(d.id)::int AS deaf_count
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
  const { rows } = await pool.query('INSERT INTO municipalities (name) VALUES ($1) RETURNING id', [name]);
  const insertId = rows[0].id;
  await logAudit(req.user.id, 'CREATE', 'municipality', insertId, { name });
  res.status(201).json({ id: insertId, name });
});

const update = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { name } = req.body;
  await pool.query('UPDATE municipalities SET name = $1 WHERE id = $2', [name, id]);
  await logAudit(req.user.id, 'UPDATE', 'municipality', id, { name });
  res.json({ id: Number(id), name });
});

const remove = asyncHandler(async (req, res) => {
  const { id } = req.params;
  await pool.query('DELETE FROM municipalities WHERE id = $1', [id]);
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
