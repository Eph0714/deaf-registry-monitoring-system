// Constraint names that get a specific, friendly message instead of the generic fallback below -
// add to this map as new unique constraints are introduced, rather than ever surfacing Postgres's
// own raw "Key (username)=(...) already exists" detail text to the client.
const DUPLICATE_MESSAGES = {
  users_username_lower_unique: 'This username is already taken. Please choose a different one.',
  users_email_key: 'An account with this email already exists.'
};

module.exports = function errorHandler(err, req, res, next) {
  console.error(err);
  if (err.code === '23505') {
    return res.status(409).json({ message: DUPLICATE_MESSAGES[err.constraint] || 'This value is already in use. Please choose a different one.' });
  }
  const status = err.status || 500;
  const message = status < 500 && err.message ? err.message : 'Something went wrong. Please try again. If the problem continues, contact the administrator.';
  res.status(status).json({ message });
};
