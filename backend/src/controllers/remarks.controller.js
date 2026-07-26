const { v4: uuidv4 } = require('uuid');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const listForVisit = asyncHandler(async (req, res) => {
  const { visitId } = req.params;
  const { rows } = await pool.query('SELECT * FROM remarks WHERE visit_id = $1 ORDER BY created_at DESC', [visitId]);
  res.json(rows);
});

// Every remark across the whole (non-deleted) roster in one call - nothing ever pulled remarks in
// bulk before this, so a remark only ever showed up locally on the exact device it was created on;
// it never synced anywhere else, and was lost entirely if that device's local data was ever wiped
// (e.g. the Room schema-version bump that shipped alongside adding Visit edit/delete). Mirrors
// visits.controller.js::listAll exactly.
const listAll = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT r.* FROM remarks r
    JOIN visits v ON v.id = r.visit_id
    JOIN deaf_individuals d ON d.id = v.deaf_individual_id AND d.is_deleted = false
    ORDER BY r.created_at DESC`);
  res.json(rows);
});

const create = asyncHandler(async (req, res) => {
  const { visitId } = req.params;
  const { remark_text, uuid: clientUuid } = req.body;
  if (!remark_text) return res.status(400).json({ message: 'remark_text is required' });
  const uuid = clientUuid || uuidv4();
  const { rows } = await pool.query(
    'INSERT INTO remarks (uuid, visit_id, user_id, user_name, remark_text) VALUES ($1, $2, $3, $4, $5) RETURNING id',
    [uuid, visitId, req.user.id, req.user.name, remark_text]
  );
  const insertId = rows[0].id;
  await logAudit(req.user.id, 'CREATE', 'remark', insertId, { visitId });
  res.status(201).json({ id: insertId, uuid });
});

const update = asyncHandler(async (req, res) => {
  const { visitId, id } = req.params;
  const { remark_text } = req.body;
  if (!remark_text) return res.status(400).json({ message: 'remark_text is required' });

  // Author-or-admin-only was dropped when remarks stopped being their own add/edit/delete list
  // and became part of editing the visit itself (Edit Visit dialog) - that flow is already open to
  // any authenticated user, same as editing the visit's date/publisher, so this stayed enforcing a
  // restriction the client no longer honors, silently failing (403) any remark edit by someone
  // other than its original author.
  const { rows } = await pool.query('SELECT * FROM remarks WHERE id = $1 AND visit_id = $2', [id, visitId]);
  if (!rows.length) return res.status(404).json({ message: 'Not found' });

  await pool.query('UPDATE remarks SET remark_text = $1 WHERE id = $2', [remark_text, id]);
  await logAudit(req.user.id, 'UPDATE', 'remark', id, { visitId });
  res.json({ id: Number(id) });
});

const remove = asyncHandler(async (req, res) => {
  const { visitId, id } = req.params;
  const { rows } = await pool.query('SELECT * FROM remarks WHERE id = $1 AND visit_id = $2', [id, visitId]);
  if (!rows.length) return res.status(404).json({ message: 'Not found' });

  await pool.query('DELETE FROM remarks WHERE id = $1', [id]);
  await logAudit(req.user.id, 'DELETE', 'remark', id, { visitId });
  res.status(204).send();
});

module.exports = { listForVisit, listAll, create, update, remove };
