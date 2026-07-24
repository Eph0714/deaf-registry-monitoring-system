const { v4: uuidv4 } = require('uuid');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const listForVisit = asyncHandler(async (req, res) => {
  const { visitId } = req.params;
  const { rows } = await pool.query('SELECT * FROM remarks WHERE visit_id = $1 ORDER BY created_at DESC', [visitId]);
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

  const { rows } = await pool.query('SELECT * FROM remarks WHERE id = $1 AND visit_id = $2', [id, visitId]);
  if (!rows.length) return res.status(404).json({ message: 'Not found' });
  const remark = rows[0];
  if (req.user.role !== 'admin' && remark.user_id !== req.user.id) {
    return res.status(403).json({ message: 'Only the author or an admin may edit this remark' });
  }

  await pool.query('UPDATE remarks SET remark_text = $1 WHERE id = $2', [remark_text, id]);
  await logAudit(req.user.id, 'UPDATE', 'remark', id, { visitId });
  res.json({ id: Number(id) });
});

const remove = asyncHandler(async (req, res) => {
  const { visitId, id } = req.params;
  const { rows } = await pool.query('SELECT * FROM remarks WHERE id = $1 AND visit_id = $2', [id, visitId]);
  if (!rows.length) return res.status(404).json({ message: 'Not found' });
  const remark = rows[0];
  if (req.user.role !== 'admin' && remark.user_id !== req.user.id) {
    return res.status(403).json({ message: 'Only the author or an admin may delete this remark' });
  }

  await pool.query('DELETE FROM remarks WHERE id = $1', [id]);
  await logAudit(req.user.id, 'DELETE', 'remark', id, { visitId });
  res.status(204).send();
});

module.exports = { listForVisit, create, update, remove };
