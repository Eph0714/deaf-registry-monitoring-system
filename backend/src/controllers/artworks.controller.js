const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');
const { createNotification, notifyAdmins } = require('../utils/notify');
const { validateImageFile } = require('../utils/fileValidator');
const { optimizeImage } = require('../utils/imageOptimizer');
const { uploadArtworkSet, removeObjects } = require('../utils/artworkStorage');
const { inClause } = require('../utils/sql');

const MIME_BY_FORMAT = { jpg: 'image/jpeg', png: 'image/png', webp: 'image/webp' };

const ARTWORK_SELECT = `
  SELECT a.id, a.folder_id, a.file_name, a.public_url_original, a.public_url_optimized,
    a.public_url_thumbnail, a.format, a.width_px, a.height_px, a.original_size_bytes,
    a.optimized_size_bytes, a.publish_status, a.deleted_status, a.uploaded_by, a.uploaded_at,
    a.published_at, a.deleted_at, u.name AS uploaded_by_name
  FROM artworks a LEFT JOIN users u ON u.id = a.uploaded_by
`;

function isAdmin(req) {
  return req.user.role === 'admin' || req.user.role === 'super_admin';
}

async function maxBatchSize() {
  const { rows } = await pool.query("SELECT `value` FROM settings WHERE `key` = 'artwork_max_batch_size'");
  return rows.length ? Number(rows[0].value) : 20;
}

// Validates, sniffs real content and optimizes every file in req.files, storing each as a
// database blob (see artworkStorage.js). Throws (via validateImageFile's err.status) on the
// first invalid file - nothing is inserted into the DB for a batch that fails validation.
async function processUploadedFiles(folderId, files, ownerId) {
  const results = [];
  for (const file of files) {
    const { format, mime } = await validateImageFile(file.buffer);
    const optimized = await optimizeImage(file.buffer, format);
    const stored = await uploadArtworkSet(
      folderId,
      format,
      optimized.originalBuffer,
      optimized.optimizedBuffer,
      optimized.thumbnailBuffer,
      MIME_BY_FORMAT[format],
      ownerId
    );
    results.push({
      fileName: file.originalname,
      format,
      width: optimized.width,
      height: optimized.height,
      originalSizeBytes: optimized.originalSizeBytes,
      optimizedSizeBytes: optimized.optimizedSizeBytes,
      ...stored
    });
  }
  return results;
}

async function insertArtworkRows(folderId, uploadedBy, publishStatus, files) {
  const ids = [];
  for (const f of files) {
    const { rows } = await pool.query(
      `INSERT INTO artworks (
        folder_id, file_name, storage_object_original, storage_object_optimized, storage_object_thumbnail,
        public_url_original, public_url_optimized, public_url_thumbnail, format, width_px, height_px,
        original_size_bytes, optimized_size_bytes, publish_status, uploaded_by, published_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ${publishStatus === 'published' ? 'CURRENT_TIMESTAMP' : 'NULL'})`,
      [
        folderId, f.fileName, f.storageObjectOriginal, f.storageObjectOptimized, f.storageObjectThumbnail,
        f.publicUrlOriginal, f.publicUrlOptimized, f.publicUrlThumbnail, f.format, f.width, f.height,
        f.originalSizeBytes, f.optimizedSizeBytes, publishStatus, uploadedBy
      ]
    );
    ids.push(rows.insertId);
  }
  return ids;
}

const listArtworks = asyncHandler(async (req, res) => {
  const { folderId, includeAll } = req.query;
  if (!folderId) return res.status(400).json({ message: 'folderId is required' });

  const params = [folderId];
  let sql = `${ARTWORK_SELECT} WHERE a.folder_id = ?`;
  if (isAdmin(req) && includeAll === 'true') {
    sql += " AND a.deleted_status != 'deleted'";
  } else {
    sql += " AND a.publish_status = 'published' AND a.deleted_status != 'deleted'";
  }
  sql += ' ORDER BY a.file_name ASC';

  const { rows } = await pool.query(sql, params);
  res.json(rows);
});

const getArtworkInfo = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`${ARTWORK_SELECT} WHERE a.id = ?`, [req.params.id]);
  if (!rows.length) return res.status(404).json({ message: 'Artwork not found' });
  const artwork = rows[0];
  if (!isAdmin(req) && (artwork.publish_status !== 'published' || artwork.deleted_status === 'deleted')) {
    return res.status(404).json({ message: 'Artwork not found' });
  }
  res.json(artwork);
});

// Admin-only route (enforced by requireAdmin in the route chain, not a body flag) - validates,
// optimizes and publishes directly. publish_status is a SQL literal here, never read from the
// request body, so this is also the only code path that can ever force-publish.
const uploadDirect = asyncHandler(async (req, res) => {
  const { folderId } = req.params;
  if (!req.files || !req.files.length) return res.status(400).json({ message: 'At least one image file is required' });

  const limit = await maxBatchSize();
  if (req.files.length > limit) {
    return res.status(400).json({ message: `You can upload at most ${limit} images at once` });
  }

  const { rows: folderRows } = await pool.query('SELECT id FROM artwork_folders WHERE id = ? AND is_deleted = false', [folderId]);
  if (!folderRows.length) return res.status(404).json({ message: 'Folder not found' });

  const processed = await processUploadedFiles(folderId, req.files, req.user.id);
  const ids = await insertArtworkRows(folderId, req.user.id, 'published', processed);
  await logAudit(req.user.id, 'ARTWORK_UPLOAD_PUBLISHED', 'artwork', null, { folderId: Number(folderId), artworkIds: ids });
  res.status(201).json({ artworkIds: ids });
});

// Regular-user path: images are stored as 'pending' and invisible in the public gallery until
// an admin approves the grouping artwork_requests row created here.
const uploadRequest = asyncHandler(async (req, res) => {
  const { folderId } = req.params;
  const { note } = req.body;
  if (!req.files || !req.files.length) return res.status(400).json({ message: 'At least one image file is required' });

  const limit = await maxBatchSize();
  if (req.files.length > limit) {
    return res.status(400).json({ message: `You can submit at most ${limit} images in one request` });
  }

  const { rows: folderRows } = await pool.query('SELECT id FROM artwork_folders WHERE id = ? AND is_deleted = false', [folderId]);
  if (!folderRows.length) return res.status(404).json({ message: 'Folder not found' });

  const processed = await processUploadedFiles(folderId, req.files, req.user.id);
  const artworkIds = await insertArtworkRows(folderId, req.user.id, 'pending', processed);

  const { rows: reqRows } = await pool.query(
    `INSERT INTO artwork_requests (request_type, folder_id, requested_by, reason) VALUES ('upload', ?, ?, ?)`,
    [folderId, req.user.id, note || null]
  );
  const requestId = reqRows.insertId;
  for (const artworkId of artworkIds) {
    await pool.query('INSERT INTO artwork_request_items (request_id, artwork_id) VALUES (?, ?)', [requestId, artworkId]);
  }

  await logAudit(req.user.id, 'ARTWORK_UPLOAD_REQUESTED', 'artwork_request', requestId, { folderId: Number(folderId), artworkIds });
  await notifyAdmins(
    'artwork_upload_requested',
    'artwork_request',
    requestId,
    `${req.user.name || 'A user'} submitted ${artworkIds.length} image(s) for approval`
  );

  res.status(201).json({ requestId, artworkIds });
});

const renameArtwork = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { fileName } = req.body;
  if (!fileName || !fileName.trim()) return res.status(400).json({ message: 'File name is required' });

  const result = await pool.query('UPDATE artworks SET file_name = ? WHERE id = ?', [fileName.trim(), id]);
  if (!result.rows.affectedRows) return res.status(404).json({ message: 'Artwork not found' });
  await logAudit(req.user.id, 'ARTWORK_RENAMED', 'artwork', id, { fileName: fileName.trim() });
  res.json({ id: Number(id), fileName: fileName.trim() });
});

const moveArtwork = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { folderId } = req.body;
  if (!folderId) return res.status(400).json({ message: 'folderId is required' });

  const { rows: folderRows } = await pool.query('SELECT id FROM artwork_folders WHERE id = ? AND is_deleted = false', [folderId]);
  if (!folderRows.length) return res.status(404).json({ message: 'Target folder not found' });

  const result = await pool.query('UPDATE artworks SET folder_id = ? WHERE id = ?', [folderId, id]);
  if (!result.rows.affectedRows) return res.status(404).json({ message: 'Artwork not found' });
  await logAudit(req.user.id, 'ARTWORK_MOVED', 'artwork', id, { folderId: Number(folderId) });
  res.json({ id: Number(id), folderId: Number(folderId) });
});

const listRecycleBin = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`${ARTWORK_SELECT} WHERE a.deleted_status = 'deleted' ORDER BY a.deleted_at DESC`);
  res.json(rows);
});

const restoreArtwork = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query(
    `UPDATE artworks SET deleted_status = 'none', deleted_at = NULL WHERE id = ? AND deleted_status = 'deleted'`,
    [id]
  );
  if (!result.rows.affectedRows) return res.status(404).json({ message: 'Artwork not found in Recycle Bin' });
  await logAudit(req.user.id, 'ARTWORK_RESTORED', 'artwork', id, null);
  res.json({ id: Number(id), restored: true });
});

// Admin-only direct delete (bypasses the approval workflow entirely - that workflow exists to
// gate REGULAR USERS, not to gate admins deleting their own gallery). Moves straight to the
// Recycle Bin (deleted_status='deleted') rather than permanently erasing, so it stays recoverable
// the same way an approved user deletion request would be.
const adminDeleteArtwork = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query(
    `UPDATE artworks SET deleted_status = 'deleted', deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_status != 'deleted'`,
    [id]
  );
  if (!result.rows.affectedRows) return res.status(404).json({ message: 'Artwork not found' });
  await logAudit(req.user.id, 'ARTWORK_DELETED_BY_ADMIN', 'artwork', id, null);
  res.json({ id: Number(id), deletedStatus: 'deleted' });
});

const permanentDelete = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows } = await pool.query(
    `SELECT storage_object_original, storage_object_optimized, storage_object_thumbnail
     FROM artworks WHERE id = ? AND deleted_status = 'deleted'`,
    [id]
  );
  if (!rows.length) return res.status(404).json({ message: 'Artwork not found in Recycle Bin' });
  const { storage_object_original, storage_object_optimized, storage_object_thumbnail } = rows[0];

  await removeObjects([storage_object_original, storage_object_optimized, storage_object_thumbnail]);
  await pool.query('DELETE FROM artworks WHERE id = ?', [id]);
  await logAudit(req.user.id, 'ARTWORK_PERMANENT_DELETE', 'artwork', id, null);
  res.json({ id: Number(id), deleted: true });
});

module.exports = {
  listArtworks,
  getArtworkInfo,
  uploadDirect,
  uploadRequest,
  renameArtwork,
  moveArtwork,
  listRecycleBin,
  restoreArtwork,
  adminDeleteArtwork,
  permanentDelete
};
