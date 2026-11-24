const asyncHandler = require('../utils/asyncHandler');
const { getFileByToken } = require('../utils/blobStorage');

// No auth: the token itself is the secret (unguessable, 24 random bytes), the same model as the
// old Supabase/signed-URL approach - needed because the app's image loaders (Coil, <img src>)
// can't attach an Authorization header.
const getFile = asyncHandler(async (req, res) => {
  const file = await getFileByToken(req.params.token);
  if (!file) return res.status(404).end();
  res.set('Content-Type', file.mime);
  res.set('X-Content-Type-Options', 'nosniff');
  res.set('Cache-Control', 'public, max-age=31536000, immutable');
  res.send(file.data);
});

module.exports = { getFile };
