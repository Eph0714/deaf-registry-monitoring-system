const bcrypt = require('bcryptjs');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const list = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT u.id, u.name, u.email, u.role, u.teacher_id, u.is_active, u.updated_at, t.name AS teacher_name
     FROM users u LEFT JOIN teachers t ON t.id = u.teacher_id
     ORDER BY u.name ASC`
  );
  res.json(rows);
});

const create = asyncHandler(async (req, res) => {
  const { name, email, password, role, teacher_id } = req.body;
  if (!name || !email || !password) {
    return res.status(400).json({ message: 'name, email and password are required' });
  }
  const passwordHash = await bcrypt.hash(password, 10);
  const { rows } = await pool.query(
    'INSERT INTO users (name, email, password_hash, role, teacher_id) VALUES ($1, $2, $3, $4, $5) RETURNING id',
    [name, email, passwordHash, role || 'conductor', teacher_id || null]
  );
  const insertId = rows[0].id;
  await logAudit(req.user.id, 'CREATE', 'user', insertId, { name, email, role });
  res.status(201).json({ id: insertId, name, email, role: role || 'conductor', teacher_id: teacher_id || null });
});

const update = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { name, role, teacher_id, is_active } = req.body;
  await pool.query(
    'UPDATE users SET name = $1, role = $2, teacher_id = $3, is_active = $4 WHERE id = $5',
    [name, role, teacher_id || null, is_active === undefined ? true : !!is_active, id]
  );
  await logAudit(req.user.id, 'UPDATE', 'user', id, { name, role, teacher_id, is_active });
  res.json({ id: Number(id), name, role, teacher_id, is_active });
});

const resetPassword = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { newPassword } = req.body;
  if (!newPassword) return res.status(400).json({ message: 'newPassword is required' });
  const passwordHash = await bcrypt.hash(newPassword, 10);
  await pool.query('UPDATE users SET password_hash = $1 WHERE id = $2', [passwordHash, id]);
  await logAudit(req.user.id, 'RESET_PASSWORD', 'user', id, null);
  res.json({ message: 'Password reset' });
});

const remove = asyncHandler(async (req, res) => {
  const { id } = req.params;
  await pool.query('UPDATE users SET is_active = false WHERE id = $1', [id]);
  await logAudit(req.user.id, 'DEACTIVATE', 'user', id, null);
  res.status(204).send();
});

module.exports = { list, create, update, resetPassword, remove };
