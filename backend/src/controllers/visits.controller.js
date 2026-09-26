const { v4: uuidv4 } = require('uuid');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

// Every visit across every (non-deleted) individual, for the Android client's Sync button to pull
// the whole roster's visit history in one call instead of only ever fetching visits for whichever
// individual profile happens to have been opened on that specific device (see VisitRepository.kt's
// refreshForDeaf, which stayed per-individual - this is the bulk companion it was missing).
const listAll = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT v.*, t.name AS conductor_teacher_name FROM visits v
     LEFT JOIN teachers t ON t.id = v.conductor_id
     JOIN deaf_individuals d ON d.id = v.deaf_individual_id AND d.is_deleted = false
     ORDER BY v.visit_datetime DESC`
  );
  res.json(rows);
});

const listForDeaf = asyncHandler(async (req, res) => {
  const { deafId } = req.params;
  const { rows } = await pool.query(
    `SELECT v.*, t.name AS conductor_teacher_name FROM visits v LEFT JOIN teachers t ON t.id = v.conductor_id
     WHERE v.deaf_individual_id = ? ORDER BY v.visit_datetime DESC`,
    [deafId]
  );
  res.json(rows);
});

const create = asyncHandler(async (req, res) => {
  const { deafId } = req.params;
  const { latitude, longitude, conductor_id, conductor_name, visit_datetime, uuid: clientUuid } = req.body;
  const uuid = clientUuid || uuidv4();
  const datetime = visit_datetime || new Date().toISOString().slice(0, 19).replace('T', ' ');
  const { rows } = await pool.query(
    `INSERT INTO visits (uuid, deaf_individual_id, visit_datetime, latitude, longitude, conductor_id, conductor_name, created_by)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [uuid, deafId, datetime, latitude || null, longitude || null, conductor_id || null, conductor_name || req.user.name, req.user.id]
  );
  const insertId = rows.insertId;
  await logAudit(req.user.id, 'CREATE', 'visit', insertId, { deafId });
  res.status(201).json({ id: insertId, uuid, visit_datetime: datetime });
});

const update = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { latitude, longitude, conductor_id, conductor_name, visit_datetime } = req.body;
  if (!visit_datetime) {
    return res.status(400).json({ message: 'visit_datetime is required' });
  }
  const result = await pool.query(
    `UPDATE visits SET visit_datetime = ?, latitude = ?, longitude = ?, conductor_id = ?, conductor_name = ?
     WHERE id = ?`,
    [visit_datetime, latitude || null, longitude || null, conductor_id || null, conductor_name || null, id]
  );
  if (!result.rows.affectedRows) return res.status(404).json({ message: 'Visit not found' });
  const { rows } = await pool.query('SELECT * FROM visits WHERE id = ?', [id]);
  await logAudit(req.user.id, 'UPDATE', 'visit', id, null);
  res.json(rows[0]);
});

const remove = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query('DELETE FROM visits WHERE id = ?', [id]);
  if (!result.rows.affectedRows) return res.status(404).json({ message: 'Visit not found' });
  await logAudit(req.user.id, 'DELETE', 'visit', id, null);
  res.status(204).send();
});

module.exports = { listAll, listForDeaf, create, update, remove };
