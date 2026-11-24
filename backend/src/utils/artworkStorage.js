const crypto = require('crypto');
const { putFile, deleteFile, publicUrlFor } = require('./blobStorage');

// Artworks are namespaced under an "artworks/<folderId>/" path prefix instead of a dedicated
// bucket/table. See blobStorage.js / schema.mysql.sql for why these are database BLOBs.
// storage_object_* columns store the `path` (used only to delete the row later); public_url_*
// columns store the token-based download URL.
async function uploadArtworkSet(folderId, format, originalBuffer, optimizedBuffer, thumbnailBuffer, mime, ownerId) {
  const suffix = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}`;
  const prefix = `artworks/${folderId}/${suffix}`;

  const storageObjectOriginal = `${prefix}-original.${format}`;
  const storageObjectOptimized = `${prefix}-optimized.${format}`;
  const storageObjectThumbnail = `${prefix}-thumbnail.jpg`;

  const [tokenOriginal, tokenOptimized, tokenThumbnail] = await Promise.all([
    putFile(storageObjectOriginal, originalBuffer, mime, ownerId),
    putFile(storageObjectOptimized, optimizedBuffer, mime, ownerId),
    putFile(storageObjectThumbnail, thumbnailBuffer, 'image/jpeg', ownerId)
  ]);

  return {
    storageObjectOriginal,
    storageObjectOptimized,
    storageObjectThumbnail,
    publicUrlOriginal: publicUrlFor(tokenOriginal),
    publicUrlOptimized: publicUrlFor(tokenOptimized),
    publicUrlThumbnail: publicUrlFor(tokenThumbnail)
  };
}

async function removeObjects(objectNames) {
  for (const objectName of objectNames) {
    await deleteFile(objectName);
  }
}

module.exports = { uploadArtworkSet, removeObjects };
