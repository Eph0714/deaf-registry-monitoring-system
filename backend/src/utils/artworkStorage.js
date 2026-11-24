const { createClient } = require('@supabase/supabase-js');
const crypto = require('crypto');

const BUCKET = process.env.SUPABASE_STORAGE_BUCKET || 'photos';

const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY);

// Reuses the same bucket as photoStorage.js (no new env var to provision) - artworks are
// namespaced under an "artworks/<folderId>/" path prefix instead of a dedicated bucket. See
// photoStorage.js for why this can't be local disk on Hostinger's managed Node.js hosting.
async function uploadObject(objectName, buffer, contentType) {
  const { error } = await supabase.storage.from(BUCKET).upload(objectName, buffer, { contentType, upsert: false });
  if (error) throw error;
  const { data } = supabase.storage.from(BUCKET).getPublicUrl(objectName);
  return data.publicUrl;
}

async function removeObjects(objectNames) {
  if (!objectNames.length) return;
  const { error } = await supabase.storage.from(BUCKET).remove(objectNames);
  if (error) throw error;
}

// Uploads the original, optimized and thumbnail buffers for one artwork image. The original
// is retained alongside the optimized copy so an admin can later download the untouched file -
// that's only possible if its bytes still physically exist in storage.
async function uploadArtworkSet(folderId, format, originalBuffer, optimizedBuffer, thumbnailBuffer, mime) {
  const suffix = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}`;
  const prefix = `artworks/${folderId}/${suffix}`;

  const storageObjectOriginal = `${prefix}-original.${format}`;
  const storageObjectOptimized = `${prefix}-optimized.${format}`;
  const storageObjectThumbnail = `${prefix}-thumbnail.jpg`;

  const [publicUrlOriginal, publicUrlOptimized, publicUrlThumbnail] = await Promise.all([
    uploadObject(storageObjectOriginal, originalBuffer, mime),
    uploadObject(storageObjectOptimized, optimizedBuffer, mime),
    uploadObject(storageObjectThumbnail, thumbnailBuffer, 'image/jpeg')
  ]);

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
