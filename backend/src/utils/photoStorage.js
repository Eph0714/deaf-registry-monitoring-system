const { createClient } = require('@supabase/supabase-js');
const crypto = require('crypto');
const path = require('path');

const BUCKET = process.env.SUPABASE_STORAGE_BUCKET || 'photos';

const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY);

/**
 * Uploads a multer memory-storage file buffer to Supabase Storage and returns
 * its public URL. Kept on Supabase even after the relational database moved
 * to Hostinger's MySQL - Hostinger's managed Node.js hosting runs each build
 * from a fresh, isolated directory, so local disk storage doesn't survive a
 * redeploy the way Supabase Storage (or the old Render host) does.
 */
async function uploadPhoto(file) {
  const ext = path.extname(file.originalname) || '.jpg';
  const objectName = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}${ext}`;

  const { error } = await supabase.storage
    .from(BUCKET)
    .upload(objectName, file.buffer, { contentType: file.mimetype, upsert: false });

  if (error) throw error;

  const { data } = supabase.storage.from(BUCKET).getPublicUrl(objectName);
  return data.publicUrl;
}

module.exports = { uploadPhoto };
