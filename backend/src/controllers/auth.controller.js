const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');
const { uploadPhoto: uploadPhotoToStorage } = require('../utils/photoStorage');
const { usernameError, passwordError } = require('../utils/validation');

function signToken(user) {
  return jwt.sign(
    { id: user.id, email: user.email, username: user.username, name: user.name, role: user.role, teacher_id: user.teacher_id },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
  );
}

const login = asyncHandler(async (req, res) => {
  const { username, password } = req.body;
  if (!username || !password) {
    return res.status(400).json({ message: 'Username and password are required' });
  }
  // Case-insensitive, and also falls back to matching on email - a lot of users are still typing
  // the email they used to log in with before login switched from email to username, and there's
  // no reason to make that a hard failure when the account is unambiguous either way.
  const { rows } = await pool.query(
    'SELECT * FROM users WHERE (LOWER(username) = LOWER($1) OR LOWER(email) = LOWER($1)) AND is_active = true LIMIT 1',
    [username]
  );
  const user = rows[0];
  if (!user) {
    return res.status(401).json({ message: 'Invalid credentials' });
  }
  const valid = await bcrypt.compare(password, user.password_hash);
  if (!valid) {
    return res.status(401).json({ message: 'Invalid credentials' });
  }

  if (user.approval_status === 'pending') {
    return res.status(403).json({
      code: 'ACCOUNT_PENDING',
      message: 'Your account is awaiting administrator approval.'
    });
  }
  if (user.approval_status === 'rejected') {
    return res.status(403).json({
      code: 'ACCOUNT_REJECTED',
      message: 'Your registration was not approved. Contact your administrator.'
    });
  }

  const token = signToken(user);
  const { rows: loginRows } = await pool.query(
    'UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = $1 RETURNING last_login_at',
    [user.id]
  );
  await logAudit(user.id, 'LOGIN', 'user', user.id, null);
  res.json({
    token,
    user: {
      id: user.id, name: user.name, email: user.email, username: user.username, role: user.role,
      teacher_id: user.teacher_id, photo_url: user.photo_url,
      last_login_at: loginRows[0].last_login_at
    }
  });
});

const logout = asyncHandler(async (req, res) => {
  await logAudit(req.user.id, 'LOGOUT', 'user', req.user.id, null);
  res.json({ message: 'Logged out' });
});

const signup = asyncHandler(async (req, res) => {
  const { name, username, password, contact_number, location } = req.body;
  if (!name || !username || !password) {
    return res.status(400).json({ message: 'name, username and password are required' });
  }
  const usernameProblem = usernameError(username);
  if (usernameProblem) return res.status(400).json({ message: usernameProblem });
  const passwordProblem = passwordError(password);
  if (passwordProblem) return res.status(400).json({ message: passwordProblem });
  if (!contact_number || !location) {
    return res.status(400).json({ message: 'contact_number and location are required' });
  }

  const trimmedUsername = username.trim();
  // Explicit pre-check rather than relying solely on the DB constraint + generic error handler:
  // the synthesized placeholder email below is deterministic from the username, so a duplicate
  // username always also collides on email, and Postgres may report whichever constraint it
  // happens to check first - which could surface "email already exists" for a signup that never
  // even had an email field. Checking username first guarantees the message the user sees always
  // matches the field they actually filled in.
  const { rows: existing } = await pool.query('SELECT id FROM users WHERE LOWER(username) = LOWER($1)', [trimmedUsername]);
  if (existing.length) {
    return res.status(409).json({ message: 'This username is already taken. Please choose a different one.' });
  }

  const passwordHash = await bcrypt.hash(password, 10);
  // Self-service signup no longer collects an email address (Username/Password only per the
  // registration redesign) - users.email is still NOT NULL/UNIQUE though (kept for admin-created
  // accounts and internal reference), so a placeholder derived from the now-guaranteed-unique
  // username is synthesized here instead of asking for a real address. It's never used to send
  // anything - this app has no email-sending capability at all (email verification was removed
  // earlier in this project's history, see memory).
  const placeholderEmail = `${trimmedUsername.toLowerCase()}@no-email.deafregistry.local`;
  const { rows } = await pool.query(
    `INSERT INTO users (name, email, username, password_hash, role, approval_status, contact_number, location)
     VALUES ($1, $2, $3, $4, 'conductor', 'pending', $5, $6)
     RETURNING id`,
    [name, placeholderEmail, trimmedUsername, passwordHash, contact_number, location]
  );
  const userId = rows[0].id;

  await logAudit(userId, 'SIGNUP', 'user', userId, { username: trimmedUsername });
  res.status(201).json({ message: 'Your request has been submitted. An administrator will review and approve your account.' });
});

const me = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    'SELECT id, name, email, username, role, teacher_id, photo_url, last_login_at FROM users WHERE id = $1',
    [req.user.id]
  );
  if (!rows.length) return res.status(404).json({ message: 'User not found' });
  res.json(rows[0]);
});

// Public, unauthenticated - a person who forgot their password can't log in to request a reset
// themselves. Accepts any username without checking whether it matches a real account (avoids
// leaking which usernames exist via the response), and always responds the same way either way -
// an admin/super-admin reviews the queue in Password Reset Requests and resolves it manually,
// since this project has no working email delivery for arbitrary users (see Forgot Password design
// notes - Resend's sandbox sender can only reach one address).
const forgotPassword = asyncHandler(async (req, res) => {
  const { username, note } = req.body;
  if (!username) {
    return res.status(400).json({ message: 'Username is required' });
  }
  const { rows } = await pool.query('SELECT id FROM users WHERE username = $1', [username]);
  const matchedUserId = rows[0]?.id || null;
  await pool.query(
    'INSERT INTO password_reset_requests (username, note) VALUES ($1, $2)',
    [username, note || null]
  );
  await logAudit(matchedUserId, 'PASSWORD_RESET_REQUESTED', 'user', matchedUserId, { username });
  res.status(201).json({ message: 'Your request has been submitted. An administrator will review it and reset your password.' });
});

const uploadPhoto = asyncHandler(async (req, res) => {
  if (!req.file) return res.status(400).json({ message: 'photo file is required' });
  const photoUrl = await uploadPhotoToStorage(req.file);
  await pool.query('UPDATE users SET photo_url = $1 WHERE id = $2', [photoUrl, req.user.id]);
  await logAudit(req.user.id, 'UPDATE_PHOTO', 'user', req.user.id, { photoUrl });
  res.json({ photo_url: photoUrl });
});

const shareLocation = asyncHandler(async (req, res) => {
  const { latitude, longitude } = req.body;
  if (typeof latitude !== 'number' || typeof longitude !== 'number') {
    return res.status(400).json({ message: 'latitude and longitude are required numbers' });
  }
  const { rows } = await pool.query(
    `UPDATE users SET shared_latitude = $1, shared_longitude = $2, shared_location_at = CURRENT_TIMESTAMP
     WHERE id = $3 RETURNING shared_latitude, shared_longitude, shared_location_at`,
    [latitude, longitude, req.user.id]
  );
  res.json(rows[0]);
});

// Manually clears this user's own shared location immediately, rather than waiting for it to age
// out past the admin-configured TTL (see users.controller.js::listLocations / locationRetention.js).
const stopSharingLocation = asyncHandler(async (req, res) => {
  await pool.query(
    `UPDATE users SET shared_latitude = NULL, shared_longitude = NULL, shared_location_at = NULL WHERE id = $1`,
    [req.user.id]
  );
  res.status(204).send();
});

const changePassword = asyncHandler(async (req, res) => {
  const { currentPassword, newPassword } = req.body;
  if (!currentPassword || !newPassword) {
    return res.status(400).json({ message: 'currentPassword and newPassword are required' });
  }
  const { rows } = await pool.query('SELECT * FROM users WHERE id = $1', [req.user.id]);
  const user = rows[0];
  const valid = await bcrypt.compare(currentPassword, user.password_hash);
  if (!valid) return res.status(401).json({ message: 'Current password is incorrect' });
  const newHash = await bcrypt.hash(newPassword, 10);
  await pool.query('UPDATE users SET password_hash = $1 WHERE id = $2', [newHash, user.id]);
  res.json({ message: 'Password updated' });
});

module.exports = { login, signup, me, changePassword, uploadPhoto, shareLocation, stopSharingLocation, logout, forgotPassword };
