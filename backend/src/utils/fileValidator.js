const { fromBuffer } = require('file-type');

const ALLOWED_EXTENSIONS = { jpg: 'jpg', jpeg: 'jpg', png: 'png', webp: 'webp' };

// Sniffs the real magic bytes rather than trusting the client-supplied mimetype/filename
// extension (both are spoofable) - multer's own fileFilter in middleware/upload.js only
// sees those, so this runs separately once the buffer is fully in memory.
async function validateImageFile(buffer) {
  const type = await fromBuffer(buffer);
  if (!type || !ALLOWED_EXTENSIONS[type.ext]) {
    const err = new Error('Only JPG, PNG and WEBP images are allowed');
    err.status = 422;
    throw err;
  }
  return { format: ALLOWED_EXTENSIONS[type.ext], mime: type.mime };
}

module.exports = { validateImageFile };
