const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// Hostinger's managed Node.js App hosting runs every deploy from a fresh, isolated directory
// (hbuilds/versions/<id>/) - anything written relative to this app's own code would vanish on
// the next deploy. UPLOAD_ROOT instead points outside that ephemeral tree, at the hosting
// account's persistent public_html directory (set in Hostinger's Environment variables panel -
// see app.js for the matching static-serve path). Falls back to a local folder for dev.
const UPLOAD_ROOT = process.env.UPLOAD_ROOT || path.join(__dirname, '..', '..', 'uploads');
const UPLOAD_DIR = path.join(UPLOAD_ROOT, 'photos');

function publicUrlFor(objectName) {
  const base = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
  return `${base}/uploads/photos/${objectName}`;
}

async function uploadPhoto(file) {
  const ext = path.extname(file.originalname) || '.jpg';
  const objectName = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}${ext}`;

  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
  fs.writeFileSync(path.join(UPLOAD_DIR, objectName), file.buffer);

  return publicUrlFor(objectName);
}

module.exports = { uploadPhoto };
