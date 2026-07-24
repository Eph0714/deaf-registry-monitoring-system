const { v4: uuidv4 } = require('uuid');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');
const { uploadPhoto: uploadPhotoToStorage } = require('../utils/photoStorage');

const BASE_SELECT = `
  SELECT d.*, m.name AS municipality_name, b.name AS barangay_name, t.name AS teacher_name, t.contact_number AS teacher_contact
  FROM deaf_individuals d
  JOIN municipalities m ON m.id = d.municipality_id
  JOIN barangays b ON b.id = d.barangay_id
  LEFT JOIN teachers t ON t.id = d.assigned_teacher_id
`;

const list = asyncHandler(async (req, res) => {
  const { municipality_id, barangay_id, skill_level, monitoring_status, gender, q, updated_since } = req.query;
  const conditions = ['d.is_deleted = false'];
  const params = [];

  if (municipality_id) { params.push(municipality_id); conditions.push(`d.municipality_id = $${params.length}`); }
  if (barangay_id) { params.push(barangay_id); conditions.push(`d.barangay_id = $${params.length}`); }
  if (skill_level) { params.push(skill_level); conditions.push(`d.skill_level = $${params.length}`); }
  if (monitoring_status) { params.push(monitoring_status); conditions.push(`d.monitoring_status = $${params.length}`); }
  if (gender) { params.push(gender); conditions.push(`d.gender = $${params.length}`); }
  if (updated_since) { params.push(updated_since); conditions.push(`d.updated_at > $${params.length}`); }
  if (q) {
    const like = `%${q}%`;
    const placeholders = [];
    for (let i = 0; i < 6; i++) { params.push(like); placeholders.push(`$${params.length}`); }
    conditions.push(
      `(d.full_name ILIKE ${placeholders[0]} OR b.name ILIKE ${placeholders[1]} OR m.name ILIKE ${placeholders[2]} OR t.name ILIKE ${placeholders[3]} OR d.skill_level ILIKE ${placeholders[4]} OR d.monitoring_status ILIKE ${placeholders[5]})`
    );
  }

  const where = `WHERE ${conditions.join(' AND ')}`;
  const { rows } = await pool.query(`${BASE_SELECT} ${where} ORDER BY d.full_name ASC`, params);
  res.json(rows);
});

const getById = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows } = await pool.query(`${BASE_SELECT} WHERE d.id = $1 AND d.is_deleted = false`, [id]);
  if (!rows.length) return res.status(404).json({ message: 'Not found' });

  const { rows: visits } = await pool.query(
    `SELECT v.*, t.name AS conductor_teacher_name FROM visits v LEFT JOIN teachers t ON t.id = v.conductor_id
     WHERE v.deaf_individual_id = $1 ORDER BY v.visit_datetime DESC`,
    [id]
  );
  const visitIds = visits.map((v) => v.id);
  let remarksByVisit = {};
  if (visitIds.length) {
    const { rows: remarks } = await pool.query(
      `SELECT * FROM remarks WHERE visit_id = ANY($1::int[]) ORDER BY created_at DESC`,
      [visitIds]
    );
    remarksByVisit = remarks.reduce((acc, r) => {
      (acc[r.visit_id] = acc[r.visit_id] || []).push(r);
      return acc;
    }, {});
  }
  const visitsWithRemarks = visits.map((v) => ({ ...v, remarks: remarksByVisit[v.id] || [] }));

  res.json({ ...rows[0], visits: visitsWithRemarks });
});

const create = asyncHandler(async (req, res) => {
  const {
    full_name, birth_date, gender, barangay_id, purok, municipality_id,
    latitude, longitude, skill_level, monitoring_status,
    assigned_teacher_id, assigned_date, contact_number, email, marital_status,
    emergency_contact_name, emergency_contact_number, notes, photo_url, uuid: clientUuid
  } = req.body;

  if (!full_name || !gender || !barangay_id || !municipality_id) {
    return res.status(400).json({ message: 'full_name, gender, barangay_id and municipality_id are required' });
  }

  const uuid = clientUuid || uuidv4();
  const { rows } = await pool.query(
    `INSERT INTO deaf_individuals
     (uuid, full_name, photo_url, birth_date, gender, barangay_id, purok, municipality_id, latitude, longitude,
      skill_level, monitoring_status, assigned_teacher_id, assigned_date, contact_number, email,
      marital_status, emergency_contact_name, emergency_contact_number, notes, created_by)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21)
     RETURNING id`,
    [uuid, full_name, photo_url || null, birth_date || null, gender, barangay_id, purok || null, municipality_id,
      latitude || null, longitude || null, skill_level || 'Natural', monitoring_status || 'Unlocated',
      assigned_teacher_id || null, assigned_date || null, contact_number || null, email || null,
      marital_status || null, emergency_contact_name || null, emergency_contact_number || null, notes || null, req.user.id]
  );
  const insertId = rows[0].id;
  await logAudit(req.user.id, 'CREATE', 'deaf_individual', insertId, { full_name });
  res.status(201).json({ id: insertId, uuid });
});

const update = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const {
    full_name, birth_date, gender, barangay_id, purok, municipality_id,
    latitude, longitude, skill_level, monitoring_status,
    assigned_teacher_id, assigned_date, contact_number, email, marital_status,
    emergency_contact_name, emergency_contact_number, notes, photo_url
  } = req.body;

  const { rows: existingRows } = await pool.query('SELECT assigned_teacher_id FROM deaf_individuals WHERE id = $1', [id]);
  const oldTeacherId = existingRows[0]?.assigned_teacher_id ?? null;
  const newTeacherId = assigned_teacher_id || null;

  await pool.query(
    `UPDATE deaf_individuals SET
       full_name = $1, birth_date = $2, gender = $3, barangay_id = $4, purok = $5, municipality_id = $6,
       latitude = $7, longitude = $8, skill_level = $9, monitoring_status = $10,
       assigned_teacher_id = $11, assigned_date = $12, contact_number = $13, email = $14,
       marital_status = $15, emergency_contact_name = $16, emergency_contact_number = $17, notes = $18,
       photo_url = COALESCE($19, photo_url)
     WHERE id = $20`,
    [full_name, birth_date || null, gender, barangay_id, purok || null, municipality_id,
      latitude || null, longitude || null, skill_level, monitoring_status,
      newTeacherId, assigned_date || null, contact_number || null, email || null,
      marital_status || null, emergency_contact_name || null, emergency_contact_number || null, notes || null, photo_url || null, id]
  );
  await logAudit(req.user.id, 'UPDATE', 'deaf_individual', id, { full_name });

  if (String(oldTeacherId) !== String(newTeacherId)) {
    await pool.query(
      `INSERT INTO teacher_assignment_history (deaf_individual_id, old_teacher_id, new_teacher_id, changed_by) VALUES ($1, $2, $3, $4)`,
      [id, oldTeacherId, newTeacherId, req.user.id]
    );
    await logAudit(req.user.id, 'REASSIGN_TEACHER', 'deaf_individual', id, { oldTeacherId, newTeacherId });
  }
  res.json({ id: Number(id) });
});

const getAssignmentHistory = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { rows } = await pool.query(
    `SELECT h.*, ot.name AS old_teacher_name, nt.name AS new_teacher_name, u.name AS changed_by_name
     FROM teacher_assignment_history h
     LEFT JOIN teachers ot ON ot.id = h.old_teacher_id
     LEFT JOIN teachers nt ON nt.id = h.new_teacher_id
     LEFT JOIN users u ON u.id = h.changed_by
     WHERE h.deaf_individual_id = $1 ORDER BY h.changed_at DESC`,
    [id]
  );
  res.json(rows);
});

const remove = asyncHandler(async (req, res) => {
  const { id } = req.params;
  await pool.query('UPDATE deaf_individuals SET is_deleted = true WHERE id = $1', [id]);
  await logAudit(req.user.id, 'DELETE', 'deaf_individual', id, null);
  res.status(204).send();
});

const uploadPhoto = asyncHandler(async (req, res) => {
  const { id } = req.params;
  if (!req.file) return res.status(400).json({ message: 'photo file is required' });
  const photoUrl = await uploadPhotoToStorage(req.file);
  await pool.query('UPDATE deaf_individuals SET photo_url = $1 WHERE id = $2', [photoUrl, id]);
  await logAudit(req.user.id, 'UPDATE_PHOTO', 'deaf_individual', id, { photoUrl });
  res.json({ photo_url: photoUrl });
});

module.exports = { list, getById, create, update, remove, uploadPhoto, getAssignmentHistory };
