const bcrypt = require('bcryptjs');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');
const { usernameError, passwordError } = require('../utils/validation');

// Guards against a plain admin editing/deactivating/deleting a Super Admin's account.
async function isTargetSuperAdmin(id) {
  const { rows } = await pool.query('SELECT role FROM users WHERE id = $1', [id]);
  return rows.length > 0 && rows[0].role === 'super_admin';
}

const list = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT u.id, u.name, u.email, u.username, u.role, u.teacher_id, u.is_active, u.updated_at, t.name AS teacher_name
     FROM users u LEFT JOIN teachers t ON t.id = u.teacher_id
     ORDER BY u.name ASC`
  );
  res.json(rows);
});

const create = asyncHandler(async (req, res) => {
  const { name, email, username, password, role, teacher_id } = req.body;
  if (!name || !email || !username || !password) {
    return res.status(400).json({ message: 'name, email, username and password are required' });
  }
  const usernameProblem = usernameError(username);
  if (usernameProblem) return res.status(400).json({ message: usernameProblem });
  const passwordProblem = passwordError(password);
  if (passwordProblem) return res.status(400).json({ message: passwordProblem });
  if (role === 'super_admin' && req.user.role !== 'super_admin') {
    return res.status(403).json({ message: 'Only a Super Administrator can create a Super Admin account' });
  }
  const passwordHash = await bcrypt.hash(password, 10);
  const { rows } = await pool.query(
    'INSERT INTO users (name, email, username, password_hash, role, teacher_id) VALUES ($1, $2, $3, $4, $5, $6) RETURNING id',
    [name, email, username, passwordHash, role || 'conductor', teacher_id || null]
  );
  const insertId = rows[0].id;
  await logAudit(req.user.id, 'CREATE', 'user', insertId, { name, email, username, role });
  res.status(201).json({ id: insertId, name, email, username, role: role || 'conductor', teacher_id: teacher_id || null });
});

// Username and password updates are only meaningful for another user's account here since a user
// editing their own username still goes through this same endpoint - both are gated behind the
// same Super Admin-vs-Super-Admin-target guard the rest of this file already uses.
const update = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { name, username, role, teacher_id, is_active } = req.body;
  const usernameProblem = usernameError(username);
  if (usernameProblem) return res.status(400).json({ message: usernameProblem });
  if (role === 'super_admin' && req.user.role !== 'super_admin') {
    return res.status(403).json({ message: 'Only a Super Administrator can grant Super Admin access' });
  }
  if (req.user.role !== 'super_admin' && Number(id) !== req.user.id && (await isTargetSuperAdmin(id))) {
    return res.status(403).json({ message: 'Only a Super Administrator can modify a Super Admin account' });
  }
  await pool.query(
    'UPDATE users SET name = $1, username = $2, role = $3, teacher_id = $4, is_active = $5 WHERE id = $6',
    [name, username, role, teacher_id || null, is_active === undefined ? true : !!is_active, id]
  );
  await logAudit(req.user.id, 'UPDATE', 'user', id, { name, username, role, teacher_id, is_active });
  res.json({ id: Number(id), name, username, role, teacher_id, is_active });
});

const resetPassword = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { newPassword } = req.body;
  if (!newPassword) return res.status(400).json({ message: 'newPassword is required' });
  if (req.user.role !== 'super_admin' && Number(id) !== req.user.id && (await isTargetSuperAdmin(id))) {
    return res.status(403).json({ message: 'Only a Super Administrator can reset a Super Admin account\'s password' });
  }
  const passwordHash = await bcrypt.hash(newPassword, 10);
  await pool.query('UPDATE users SET password_hash = $1 WHERE id = $2', [passwordHash, id]);
  await logAudit(req.user.id, 'RESET_PASSWORD', 'user', id, null);
  res.json({ message: 'Password reset' });
});

const remove = asyncHandler(async (req, res) => {
  const { id } = req.params;
  if (req.user.role !== 'super_admin' && (await isTargetSuperAdmin(id))) {
    return res.status(403).json({ message: 'Only a Super Administrator can deactivate a Super Admin account' });
  }
  await pool.query('UPDATE users SET is_active = false WHERE id = $1', [id]);
  await logAudit(req.user.id, 'DEACTIVATE', 'user', id, null);
  res.status(204).send();
});

// Only allows hard-deleting accounts that are already deactivated - a safety guard so an
// active account always has to go through remove() (soft delete) first. All FKs referencing
// users.id are ON DELETE SET NULL (or CASCADE for user_devices), so this is safe to run.
const permanentlyDelete = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows } = await pool.query('SELECT name, email, role FROM users WHERE id = $1 AND is_active = false', [id]);
  if (!rows.length) {
    return res.status(404).json({ message: 'No deactivated account with this id was found' });
  }
  if (rows[0].role === 'super_admin' && req.user.role !== 'super_admin') {
    return res.status(403).json({ message: 'Only a Super Administrator can delete a Super Admin account' });
  }
  const { name, email } = rows[0];
  await pool.query('DELETE FROM users WHERE id = $1', [id]);
  await logAudit(req.user.id, 'PERMANENTLY_DELETED', 'user', id, { name, email });
  res.status(204).send();
});

// Any authenticated user can see who has shared their location - a simple team
// location board, not gated to admins (confirmed with the user). Entries older than the
// admin-configured TTL (settings.location_share_ttl_minutes, see locationRetention.js for
// the job that actually clears them) are excluded here too, so a share never briefly
// reappears in the window between retention-job runs.
const listLocations = asyncHandler(async (req, res) => {
  const { rows: ttlRows } = await pool.query(
    `SELECT "value" FROM settings WHERE "key" = 'location_share_ttl_minutes'`
  );
  const ttlMinutes = ttlRows.length ? Number(ttlRows[0].value) : 60;
  const { rows } = await pool.query(
    `SELECT id, name, role, shared_latitude, shared_longitude, shared_location_at
     FROM users
     WHERE is_active = true AND shared_location_at IS NOT NULL
       AND shared_location_at > NOW() - ($1 || ' minutes')::interval
     ORDER BY shared_location_at DESC`,
    [ttlMinutes]
  );
  res.json(rows);
});

const listPendingSignups = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT id, name, email, username, contact_number, location, created_at
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

// Declining an unverified signup request deletes it outright rather than leaving a
// permanently-'rejected' row behind - that dead row would otherwise block the same email
// from ever signing up again, with no way for the person to retry.
const rejectSignup = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows } = await pool.query(
    `SELECT name, email FROM users WHERE id = $1 AND approval_status = 'pending'`,
    [id]
  );
  if (!rows.length) return res.status(404).json({ message: 'Not found' });
  const { name, email } = rows[0];
  await pool.query('DELETE FROM users WHERE id = $1', [id]);
  await logAudit(req.user.id, 'SIGNUP_DECLINED', 'user', id, { name, email });
  res.json({ id: Number(id), deleted: true });
});

const listPasswordResetRequests = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT id, username, note, status, requested_at
     FROM password_reset_requests WHERE status = 'pending' ORDER BY requested_at ASC`
  );
  res.json(rows);
});

// Resolves a Forgot Password request. If new_password is provided, it's applied to whichever
// account currently has that username (same Super Admin guard as resetPassword, since this is
// still "an admin resetting another user's password" under the hood) and the request is marked
// resolved either way; omitting new_password just dismisses the request without changing anything
// (e.g. the username didn't match a real account, or the admin handled it another way).
const resolvePasswordResetRequest = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { new_password } = req.body;
  const { rows: requestRows } = await pool.query(
    `SELECT id, username FROM password_reset_requests WHERE id = $1 AND status = 'pending'`,
    [id]
  );
  if (!requestRows.length) return res.status(404).json({ message: 'Request not found' });
  const { username } = requestRows[0];

  if (new_password) {
    const { rows: userRows } = await pool.query('SELECT id, role FROM users WHERE username = $1', [username]);
    if (!userRows.length) {
      return res.status(404).json({ message: `No account found with username "${username}"` });
    }
    const targetUser = userRows[0];
    if (targetUser.role === 'super_admin' && req.user.role !== 'super_admin') {
      return res.status(403).json({ message: 'Only a Super Administrator can reset a Super Admin account\'s password' });
    }
    const passwordHash = await bcrypt.hash(new_password, 10);
    await pool.query('UPDATE users SET password_hash = $1 WHERE id = $2', [passwordHash, targetUser.id]);
    await logAudit(req.user.id, 'RESET_PASSWORD', 'user', targetUser.id, { via: 'forgot_password_request' });
  }

  await pool.query(
    `UPDATE password_reset_requests SET status = 'resolved', resolved_at = CURRENT_TIMESTAMP, resolved_by = $1 WHERE id = $2`,
    [req.user.id, id]
  );
  res.json({ id: Number(id), status: 'resolved' });
});

module.exports = {
  list, create, update, resetPassword, remove, permanentlyDelete,
  listPendingSignups, approveSignup, rejectSignup, listLocations,
  listPasswordResetRequests, resolvePasswordResetRequest
};
