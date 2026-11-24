const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// Same persistent-path approach as photoStorage.js (see its comment) - artworks are namespaced
// under uploads/artworks/<folderId>/ instead of a dedicated bucket/prefix. Object names are
// paths relative to UPLOAD_ROOT (e.g. "artworks/3/167-original.jpg"), stored as-is in
// storage_object_* columns and resolved back to a real path by removeObjects.
const UPLOAD_ROOT = process.env.UPLOAD_ROOT || path.join(__dirname, '..', '..', 'uploads');

function publicUrlFor(objectName) {
  const base = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
  return `${base}/uploads/${objectName}`;
}

function writeObject(objectName, buffer) {
  const fullPath = path.join(UPLOAD_ROOT, objectName);
  fs.mkdirSync(path.dirname(fullPath), { recursive: true });
  fs.writeFileSync(fullPath, buffer);
  return publicUrlFor(objectName);
}

async function removeObjects(objectNames) {
  for (const objectName of objectNames) {
    try {
      fs.unlinkSync(path.join(UPLOAD_ROOT, objectName));
    } catch (err) {
      if (err.code !== 'ENOENT') throw err;
    }
  }
}

// Writes the original, optimized and thumbnail buffers for one artwork image. The original
// is retained alongside the optimized copy so an admin can later download the untouched file -
// that's only possible if its bytes still physically exist in storage.
async function uploadArtworkSet(folderId, format, originalBuffer, optimizedBuffer, thumbnailBuffer) {
  const suffix = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}`;
  const prefix = `artworks/${folderId}/${suffix}`;

  const storageObjectOriginal = `${prefix}-original.${format}`;
  const storageObjectOptimized = `${prefix}-optimized.${format}`;
  const storageObjectThumbnail = `${prefix}-thumbnail.jpg`;

  const publicUrlOriginal = writeObject(storageObjectOriginal, originalBuffer);
  const publicUrlOptimized = writeObject(storageObjectOptimized, optimizedBuffer);
  const publicUrlThumbnail = writeObject(storageObjectThumbnail, thumbnailBuffer);

  return {
    storageObjectOriginal,
    storageObjectOptimized,
    storageObjectThumbnail,
    publicUrlOriginal,
    publicUrlOptimized,
    publicUrlThumbnail
  };
}

module.exports = { uploadArtworkSet, removeObjects };
