const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');
const { uploadPhoto: uploadPhotoToStorage } = require('../utils/photoStorage');

function signToken(user) {
  return jwt.sign(
    { id: user.id, email: user.email, name: user.name, role: user.role, teacher_id: user.teacher_id },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
  );
}

const login = asyncHandler(async (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ message: 'Email and password are required' });
  }
  const { rows } = await pool.query('SELECT * FROM users WHERE email = $1 AND is_active = true', [email]);
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
  await logAudit(user.id, 'LOGIN', 'user', user.id, null);
  res.json({
    token,
    user: {
      id: user.id, name: user.name, email: user.email, role: user.role,
      teacher_id: user.teacher_id, photo_url: user.photo_url
    }
  });
});

const signup = asyncHandler(async (req, res) => {
  const { name, email, password, contact_number, location } = req.body;
  if (!name || !email || !password) {
    return res.status(400).json({ message: 'name, email and password are required' });
  }
  if (password.length < 8) {
    return res.status(400).json({ message: 'Password must be at least 8 characters' });
  }

  const passwordHash = await bcrypt.hash(password, 10);
  const { rows } = await pool.query(
    `INSERT INTO users (name, email, password_hash, role, approval_status, contact_number, location)
     VALUES ($1, $2, $3, 'conductor', 'pending', $4, $5)
     RETURNING id`,
    [name, email, passwordHash, contact_number || null, location || null]
  );
  const userId = rows[0].id;

  await logAudit(userId, 'SIGNUP', 'user', userId, { email });
  res.status(201).json({ message: 'Your request has been submitted. An administrator will review and approve your account.' });
});

const me = asyncHandler(async (req, res) => {
  const { rows } = await pool.query('SELECT id, name, email, role, teacher_id, photo_url FROM users WHERE id = $1', [req.user.id]);
  if (!rows.length) return res.status(404).json({ message: 'User not found' });
  res.json(rows[0]);
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

module.exports = { login, signup, me, changePassword, uploadPhoto, shareLocation };
