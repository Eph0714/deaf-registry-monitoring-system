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

// Only allows hard-deleting accounts that are already deactivated - a safety guard so an
// active account always has to go through remove() (soft delete) first. All FKs referencing
// users.id are ON DELETE SET NULL (or CASCADE for user_devices), so this is safe to run.
const permanentlyDelete = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows } = await pool.query('SELECT name, email FROM users WHERE id = $1 AND is_active = false', [id]);
  if (!rows.length) {
    return res.status(404).json({ message: 'No deactivated account with this id was found' });
  }
  const { name, email } = rows[0];
  await pool.query('DELETE FROM users WHERE id = $1', [id]);
  await logAudit(req.user.id, 'PERMANENTLY_DELETED', 'user', id, { name, email });
  res.status(204).send();
});

const listPendingSignups = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT id, name, email, contact_number, location, created_at
     FROM users WHERE approval_status = 'pending' ORDER BY created_at ASC`
  );
  res.json(rows);
});

const approveSignup = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query(
    `UPDATE users SET approval_status = 'approved' WHERE id = $1 AND approval_status = 'pending'`,
    [id]
  );
  if (!result.rowCount) return res.status(404).json({ message: 'Not found' });
  await logAudit(req.user.id, 'SIGNUP_APPROVED', 'user', id, null);
  res.json({ id: Number(id), approval_status: 'approved' });
});

const rejectSignup = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query(
    `UPDATE users SET approval_status = 'rejected' WHERE id = $1 AND approval_status = 'pending'`,
    [id]
  );
  if (!result.rowCount) return res.status(404).json({ message: 'Not found' });
  await logAudit(req.user.id, 'SIGNUP_REJECTED', 'user', id, null);
  res.json({ id: Number(id), approval_status: 'rejected' });
});

module.exports = {
  list, create, update, resetPassword, remove, permanentlyDelete,
  listPendingSignups, approveSignup, rejectSignup
};
