module.exports = function errorHandler(err, req, res, next) {
  console.error(err);
  if (err.code === '23505') {
    return res.status(409).json({ message: 'Duplicate entry', detail: err.detail });
  }
  const status = err.status || 500;
  res.status(status).json({ message: err.message || 'Internal server error' });
};
