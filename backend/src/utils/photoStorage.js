const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const UPLOAD_DIR = path.join(__dirname, '..', '..', 'uploads', 'photos');

/**
 * Saves a multer memory-storage file buffer to local disk (backend/uploads/photos/)
 * and returns its public URL, served by the express.static mount in app.js.
 * Replaces the old Supabase Storage upload now that the DB and storage both
 * live on the same Hostinger host as the app itself.
 */
async function uploadPhoto(file) {
  const ext = path.extname(file.originalname) || '.jpg';
  const objectName = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}${ext}`;

  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
  fs.writeFileSync(path.join(UPLOAD_DIR, objectName), file.buffer);

  const base = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
  return `${base}/uploads/photos/${objectName}`;
}

module.exports = { uploadPhoto };
