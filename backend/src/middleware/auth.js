const jwt = require('jsonwebtoken');
const pool = require('../config/db');

async function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ message: 'Missing or invalid Authorization header' });
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET);
    req.user = payload;
    // "Who's online" presence stamp - every authenticated request counts as activity, not just a
    // dedicated heartbeat endpoint, so presence is accurate without the client needing to poll
    // anything extra. Awaited (not fire-and-forget) so a request that itself calls GET
    // /users/online - e.g. the Dashboard's own poll - sees its own stamp already applied instead
    // of racing its own SELECT; failure is still swallowed so a DB hiccup here never breaks the
    // actual request it's riding along on.
    await pool.query('UPDATE users SET last_seen_at = CURRENT_TIMESTAMP WHERE id = $1', [payload.id]).catch(() => {});
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
