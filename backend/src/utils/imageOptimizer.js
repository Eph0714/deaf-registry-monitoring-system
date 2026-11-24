const sharp = require('sharp');

const THUMBNAIL_MAX_DIMENSION = 400;

// Re-encodes with format-appropriate, optimized settings WITHOUT resizing - the spec
// requires preserving original width/height and never upscaling or needlessly degrading
// quality, so the only size reduction comes from better encoding, not smaller dimensions.
// Falls back to the original bytes whenever re-encoding wouldn't actually shrink the file
// ("skip recompression if already optimized").
async function optimizeImage(buffer, format) {
  const image = sharp(buffer);
  const metadata = await image.metadata();

  let encoded;
  if (format === 'jpg') {
    encoded = await image.jpeg({ quality: 85, mozjpeg: true }).toBuffer();
  } else if (format === 'png') {
    encoded = await image.png({ compressionLevel: 9, adaptiveFiltering: true, palette: false }).toBuffer();
  } else {
    encoded = await image.webp({ quality: 90, effort: 6 }).toBuffer();
  }

  const optimizedBuffer = encoded.length < buffer.length ? encoded : buffer;

  const thumbnailBuffer = await sharp(buffer)
    .resize({ width: THUMBNAIL_MAX_DIMENSION, height: THUMBNAIL_MAX_DIMENSION, fit: 'inside', withoutEnlargement: true })
    .jpeg({ quality: 75 })
    .toBuffer();

  return {
    width: metadata.width,
    height: metadata.height,
    originalBuffer: buffer,
    optimizedBuffer,
    thumbnailBuffer,
    originalSizeBytes: buffer.length,
    optimizedSizeBytes: optimizedBuffer.length
  };
}

module.exports = { optimizeImage };
