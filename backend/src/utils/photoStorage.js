const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// Hostinger's managed Node.js hosting runs each build from its own isolated,
// versioned directory (hbuilds/versions/<uuid>/nodejs/) - a path relative to
// this file (__dirname) gets wiped on every redeploy. UPLOADS_DIR must point
// at a location outside that build system - the persistent public_html tree -
// so photos survive across deploys. Falls back to a path relative to this
// project for local dev, where that isolated-build concern doesn't apply.
const UPLOAD_DIR = process.env.UPLOADS_DIR
  ? path.join(process.env.UPLOADS_DIR, 'photos')
  : path.join(__dirname, '..', '..', 'uploads', 'photos');

/**
 * Saves a multer memory-storage file buffer to local disk (UPLOAD_DIR) and
 * returns its public URL, served by the express.static mount in app.js.
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
