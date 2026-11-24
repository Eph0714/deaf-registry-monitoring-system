const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');
const { createNotification, notifyAdmins } = require('../utils/notify');
const { removeObjects } = require('../utils/artworkStorage');
const { inClause } = require('../utils/sql');

const REQUEST_SELECT = `
  SELECT r.id, r.request_type, r.folder_id, r.requested_by, r.reason, r.status, r.rejection_reason,
    r.decided_by, r.decided_at, r.created_at,
    u.name AS requested_by_name, d.name AS decided_by_name, f.name AS folder_name
  FROM artwork_requests r
  LEFT JOIN users u ON u.id = r.requested_by
  LEFT JOIN users d ON d.id = r.decided_by
  LEFT JOIN artwork_folders f ON f.id = r.folder_id
`;

async function itemsForRequest(requestId) {
  const { rows } = await pool.query(
    `SELECT a.id, a.file_name, a.public_url_thumbnail, a.public_url_optimized, a.format
     FROM artwork_request_items i JOIN artworks a ON a.id = i.artwork_id
     WHERE i.request_id = ?`,
    [requestId]
  );
  return rows;
}

// Regular user: marks artworks as pending_delete while keeping them visible (deleted_status is
// independent of publish_status), groups them under one request for the Approval Center.
const submitDeleteRequest = asyncHandler(async (req, res) => {
  const { artworkIds, reason } = req.body;
  if (!Array.isArray(artworkIds) || !artworkIds.length) {
    return res.status(400).json({ message: 'artworkIds (non-empty array) is required' });
  }
  if (!reason || !reason.trim()) return res.status(400).json({ message: 'A reason is required' });

  const { sql, params } = inClause(artworkIds);
  const { rows: artworkRows } = await pool.query(
    `SELECT id, folder_id FROM artworks WHERE id IN (${sql}) AND publish_status = 'published' AND deleted_status = 'none'`,
    params
  );
  if (artworkRows.length !== artworkIds.length) {
    return res.status(404).json({ message: 'One or more artworks were not found or are not eligible for deletion' });
  }
  const folderId = artworkRows[0].folder_id;

  const { rows: reqRows } = await pool.query(
    `INSERT INTO artwork_requests (request_type, folder_id, requested_by, reason) VALUES ('delete', ?, ?, ?)`,
    [folderId, req.user.id, reason.trim()]
  );
  const requestId = reqRows.insertId;
  for (const artwork of artworkRows) {
    await pool.query('INSERT INTO artwork_request_items (request_id, artwork_id) VALUES (?, ?)', [requestId, artwork.id]);
  }
  await pool.query(`UPDATE artworks SET deleted_status = 'pending_delete' WHERE id IN (${sql})`, params);

  await logAudit(req.user.id, 'ARTWORK_DELETE_REQUESTED', 'artwork_request', requestId, { artworkIds });
  await notifyAdmins(
    'artwork_delete_requested',
    'artwork_request',
    requestId,
    `${req.user.name || 'A user'} requested deletion of ${artworkIds.length} image(s)`
  );

  res.status(201).json({ requestId });
});

const listRequests = asyncHandler(async (req, res) => {
  const { status, type } = req.query;
  const params = [];
  let sql = REQUEST_SELECT + ' WHERE 1=1';
  if (status) { sql += ' AND r.status = ?'; params.push(status); }
  if (type) { sql += ' AND r.request_type = ?'; params.push(type); }
  sql += ' ORDER BY r.created_at DESC';

  const { rows } = await pool.query(sql, params);
  const withItems = await Promise.all(rows.map(async (r) => ({ ...r, items: await itemsForRequest(r.id) })));
  res.json(withItems);
});

const listMyRequests = asyncHandler(async (req, res) => {
  const { status } = req.query;
  const params = [req.user.id];
  let sql = REQUEST_SELECT + ' WHERE r.requested_by = ?';
  if (status) { sql += ' AND r.status = ?'; params.push(status); }
  sql += ' ORDER BY r.created_at DESC';

  const { rows } = await pool.query(sql, params);
  const withItems = await Promise.all(rows.map(async (r) => ({ ...r, items: await itemsForRequest(r.id) })));
  res.json(withItems);
});

const getRequestDetail = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`${REQUEST_SELECT} WHERE r.id = ?`, [req.params.id]);
  if (!rows.length) return res.status(404).json({ message: 'Request not found' });
  const request = rows[0];
  request.items = await itemsForRequest(request.id);
  res.json(request);
});

const approveRequest = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows: reqRows } = await pool.query(`SELECT * FROM artwork_requests WHERE id = ? AND status = 'pending'`, [id]);
  if (!reqRows.length) return res.status(404).json({ message: 'Not found or already processed' });
  const request = reqRows[0];

  const { rows: items } = await pool.query('SELECT artwork_id FROM artwork_request_items WHERE request_id = ?', [id]);
  const artworkIds = items.map((i) => i.artwork_id);
  const { sql, params } = inClause(artworkIds);

  if (request.request_type === 'upload') {
    await pool.query(`UPDATE artworks SET publish_status = 'published', published_at = CURRENT_TIMESTAMP WHERE id IN (${sql})`, params);
  } else {
    await pool.query(`UPDATE artworks SET deleted_status = 'deleted', deleted_at = CURRENT_TIMESTAMP WHERE id IN (${sql})`, params);
  }

  // Conditional UPDATE's affected-row count is the authoritative double-processing guard - the
  // initial SELECT above could race with a second concurrent approve/reject call.
  const result = await pool.query(
    `UPDATE artwork_requests SET status = 'approved', decided_by = ?, decided_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'pending'`,
    [req.user.id, id]
  );
  if (!result.rows.affectedRows) return res.status(409).json({ message: 'Already processed' });

  const action = request.request_type === 'upload' ? 'ARTWORK_UPLOAD_APPROVED' : 'ARTWORK_DELETE_APPROVED';
  await logAudit(req.user.id, action, 'artwork_request', id, { artworkIds });
  if (request.requested_by) {
    await createNotification(
      request.requested_by,
      'artwork_request_approved',
      'artwork_request',
      id,
      `Your ${request.request_type} request was approved`
    );
  }
  res.json({ id: Number(id), status: 'approved' });
});

const rejectRequest = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { reason } = req.body;
  if (!reason || !reason.trim()) return res.status(400).json({ message: 'A rejection reason is required' });

  const { rows: reqRows } = await pool.query(`SELECT * FROM artwork_requests WHERE id = ? AND status = 'pending'`, [id]);
  if (!reqRows.length) return res.status(404).json({ message: 'Not found or already processed' });
  const request = reqRows[0];

  const { rows: items } = await pool.query('SELECT artwork_id FROM artwork_request_items WHERE request_id = ?', [id]);
  const artworkIds = items.map((i) => i.artwork_id);
  const { sql, params } = inClause(artworkIds);

  if (request.request_type === 'upload') {
    await pool.query(`UPDATE artworks SET publish_status = 'rejected' WHERE id IN (${sql})`, params);
  } else {
    await pool.query(`UPDATE artworks SET deleted_status = 'none' WHERE id IN (${sql})`, params);
  }

  const result = await pool.query(
    `UPDATE artwork_requests SET status = 'rejected', rejection_reason = ?, decided_by = ?, decided_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'pending'`,
    [reason.trim(), req.user.id, id]
  );
  if (!result.rows.affectedRows) return res.status(409).json({ message: 'Already processed' });

  const action = request.request_type === 'upload' ? 'ARTWORK_UPLOAD_REJECTED' : 'ARTWORK_DELETE_REJECTED';
  await logAudit(req.user.id, action, 'artwork_request', id, { artworkIds, reason: reason.trim() });
  if (request.requested_by) {
    await createNotification(
      request.requested_by,
      'artwork_request_rejected',
      'artwork_request',
      id,
      `Your ${request.request_type} request was rejected: ${reason.trim()}`
    );
  }
  res.json({ id: Number(id), status: 'rejected' });
});

const cancelOwnRequest = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows: reqRows } = await pool.query(
    `SELECT * FROM artwork_requests WHERE id = ? AND requested_by = ? AND status = 'pending'`,
    [id, req.user.id]
  );
  if (!reqRows.length) return res.status(404).json({ message: 'Not found, not yours, or already processed' });
  const request = reqRows[0];

  const { rows: items } = await pool.query('SELECT artwork_id FROM artwork_request_items WHERE request_id = ?', [id]);
  const artworkIds = items.map((i) => i.artwork_id);
  if (artworkIds.length) {
    const { sql, params } = inClause(artworkIds);
    if (request.request_type === 'upload') {
      // Cancelling a pending upload removes the never-published artwork rows (and their
      // Supabase objects) entirely - nothing was ever visible, so nothing to keep.
      const { rows: toDelete } = await pool.query(
        `SELECT storage_object_original, storage_object_optimized, storage_object_thumbnail
         FROM artworks WHERE id IN (${sql}) AND publish_status = 'pending'`,
        params
      );
      await pool.query(`DELETE FROM artworks WHERE id IN (${sql}) AND publish_status = 'pending'`, params);
      await removeObjects(toDelete.flatMap((r) => [r.storage_object_original, r.storage_object_optimized, r.storage_object_thumbnail]));
    } else {
      await pool.query(`UPDATE artworks SET deleted_status = 'none' WHERE id IN (${sql})`, params);
    }
  }

  const result = await pool.query(`UPDATE artwork_requests SET status = 'cancelled' WHERE id = ? AND status = 'pending'`, [id]);
  if (!result.rows.affectedRows) return res.status(409).json({ message: 'Already processed' });

  await logAudit(req.user.id, 'ARTWORK_REQUEST_CANCELLED', 'artwork_request', id, { artworkIds });
  res.json({ id: Number(id), status: 'cancelled' });
});

module.exports = {
  submitDeleteRequest,
  listRequests,
  listMyRequests,
  getRequestDetail,
  approveRequest,
  rejectRequest,
  cancelOwnRequest
};
