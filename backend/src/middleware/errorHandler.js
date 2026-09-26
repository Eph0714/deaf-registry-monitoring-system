// Unique key names that get a specific, friendly message instead of the generic fallback below -
// add to this map as new unique constraints are introduced, rather than ever surfacing MySQL's
// own raw "Duplicate entry '...' for key '...'" detail text to the client.
const DUPLICATE_MESSAGES = {
  uq_users_username: 'This username is already taken. Please choose a different one.',
  email: 'An account with this email already exists.'
};

// mysql2 doesn't put the key name on a structured field like pg's err.constraint - it's only in
// the message text ("Duplicate entry 'x' for key 'users.uq_users_username'" or, on older MySQL,
// just "for key 'uq_users_username'").
function duplicateKeyName(message) {
  const match = /for key '(?:[^.']+\.)?([^']+)'/.exec(message || '');
  return match ? match[1] : null;
}

module.exports = function errorHandler(err, req, res, next) {
  console.error(err);
  if (err.code === 'ER_DUP_ENTRY') {
    const key = duplicateKeyName(err.sqlMessage || err.message);
    return res.status(409).json({ message: DUPLICATE_MESSAGES[key] || 'This value is already in use. Please choose a different one.' });
  }
  const status = err.status || 500;
  const message = status < 500 && err.message ? err.message : 'Something went wrong. Please try again. If the problem continues, contact the administrator.';
  res.status(status).json({ message });
};
