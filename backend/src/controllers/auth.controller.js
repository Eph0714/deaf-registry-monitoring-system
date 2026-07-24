const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

function signToken(user) {
  return jwt.sign(
    { id: user.id, email: user.email, name: user.name, role: user.role, teacher_id: user.teacher_id },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
  );
}

const login = asyncHandler(async (req, res) => {
  const { email, password, device_id, device_label } = req.body;
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

  if (user.role !== 'admin' && device_id) {
    const { rows: deviceRows } = await pool.query(
      'SELECT * FROM user_devices WHERE user_id = $1 AND device_id = $2',
      [user.id, device_id]
    );
    const device = deviceRows[0];

    if (!device) {
      const { rows: countRows } = await pool.query('SELECT COUNT(*)::int AS c FROM user_devices WHERE user_id = $1', [user.id]);
      const isFirstDevice = countRows[0].c === 0;
      if (isFirstDevice) {
        await pool.query(
          'INSERT INTO user_devices (user_id, device_id, device_label, status, approved_by, approved_at) VALUES ($1, $2, $3, $4, $5, NOW())',
          [user.id, device_id, device_label || null, 'approved', user.id]
        );
        await logAudit(user.id, 'DEVICE_REGISTERED', 'user_device', user.id, { device_id, device_label });
      } else {
        await pool.query(
          'INSERT INTO user_devices (user_id, device_id, device_label, status) VALUES ($1, $2, $3, $4)',
          [user.id, device_id, device_label || null, 'pending']
        );
        await logAudit(user.id, 'DEVICE_PENDING', 'user_device', user.id, { device_id, device_label });
        return res.status(403).json({ code: 'DEVICE_PENDING', message: 'This device is awaiting administrator approval.' });
      }
    } else if (device.status === 'pending') {
      return res.status(403).json({ code: 'DEVICE_PENDING', message: 'This device is awaiting administrator approval.' });
    } else if (device.status === 'rejected') {
      return res.status(403).json({ code: 'DEVICE_REJECTED', message: 'This device has been denied access. Contact your administrator.' });
    }
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

const me = asyncHandler(async (req, res) => {
  const { rows } = await pool.query('SELECT id, name, email, role, teacher_id, photo_url FROM users WHERE id = $1', [req.user.id]);
  if (!rows.length) return res.status(404).json({ message: 'User not found' });
  res.json(rows[0]);
});

const uploadPhoto = asyncHandler(async (req, res) => {
  if (!req.file) return res.status(400).json({ message: 'photo file is required' });
  const photoUrl = `/uploads/photos/${req.file.filename}`;
  await pool.query('UPDATE users SET photo_url = $1 WHERE id = $2', [photoUrl, req.user.id]);
  await logAudit(req.user.id, 'UPDATE_PHOTO', 'user', req.user.id, { photoUrl });
  res.json({ photo_url: photoUrl });
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

module.exports = { login, me, changePassword, uploadPhoto };
