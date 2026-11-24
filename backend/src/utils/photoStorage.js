const crypto = require('crypto');
const path = require('path');
const { putFile, publicUrlFor } = require('./blobStorage');

// See blobStorage.js / schema.mysql.sql for why this is a database BLOB and not a file on disk
// or an external storage service.
async function uploadPhoto(file, ownerId) {
  const ext = path.extname(file.originalname) || '.jpg';
  const objectPath = `photos/${Date.now()}-${crypto.randomBytes(6).toString('hex')}${ext}`;
  const token = await putFile(objectPath, file.buffer, file.mimetype, ownerId);
  return publicUrlFor(token);
}

module.exports = { uploadPhoto };
