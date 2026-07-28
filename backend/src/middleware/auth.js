const jwt = require('jsonwebtoken');
const pool = require('../config/db');

function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ message: 'Missing or invalid Authorization header' });
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET);
    req.user = payload;
    // Fire-and-forget "who's online" presence stamp - every authenticated request counts as
    // activity, not just a dedicated heartbeat endpoint, so presence is accurate without the
    // client needing to poll anything extra. Never awaited/blocking and any failure is swallowed -
    // this must never slow down or break the actual request it's riding along on.
    pool.query('UPDATE users SET last_seen_at = CURRENT_TIMESTAMP WHERE id = $1', [payload.id]).catch(() => {});
    next();
  } catch (err) {
    return res.status(401).json({ message: 'Invalid or expired token' });
  }
}

// super_admin has every admin capability plus its own exclusive ones (see requireSuperAdmin).
function requireAdmin(req, res, next) {
  if (req.user?.role !== 'admin' && req.user?.role !== 'super_admin') {
    return res.status(403).json({ message: 'Administrator access required' });
  }
  next();
}

function requireSuperAdmin(req, res, next) {
  if (req.user?.role !== 'super_admin') {
    return res.status(403).json({ message: 'Super Administrator access required' });
  }
  next();
}

module.exports = { requireAuth, requireAdmin, requireSuperAdmin };
