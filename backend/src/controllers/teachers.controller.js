const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const list = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT t.id, t.name, t.contact_number, t.updated_at,
           COUNT(d.id)::int AS assigned_count
    FROM teachers t
    LEFT JOIN deaf_individuals d ON d.assigned_teacher_id = t.id AND d.is_deleted = false
    GROUP BY t.id, t.name, t.contact_number, t.updated_at
    ORDER BY t.name ASC
  `);
  res.json(rows);
});

const create = asyncHandler(async (req, res) => {
  const { name, contact_number } = req.body;
  if (!name) return res.status(400).json({ message: 'name is required' });
  const { rows } = await pool.query('INSERT INTO teachers (name, contact_number) VALUES ($1, $2) RETURNING id', [name, contact_number || null]);
  const insertId = rows[0].id;
  await logAudit(req.user.id, 'CREATE', 'teacher', insertId, { name, contact_number });
  res.status(201).json({ id: insertId, name, contact_number: contact_number || null });
});

const update = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { name, contact_number } = req.body;
  await pool.query('UPDATE teachers SET name = $1, contact_number = $2 WHERE id = $3', [name, contact_number || null, id]);
  await logAudit(req.user.id, 'UPDATE', 'teacher', id, { name, contact_number });
  res.json({ id: Number(id), name, contact_number: contact_number || null });
});

const remove = asyncHandler(async (req, res) => {
  const { id } = req.params;
  await pool.query('DELETE FROM teachers WHERE id = $1', [id]);
  await logAudit(req.user.id, 'DELETE', 'teacher', id, null);
  res.status(204).send();
});

const bulkReassign = asyncHandler(async (req, res) => {
  const { from_teacher_id, to_teacher_id, reason } = req.body;
  if (!from_teacher_id || !to_teacher_id) {
    return res.status(400).json({ message: 'from_teacher_id and to_teacher_id are required' });
  }
  if (String(from_teacher_id) === String(to_teacher_id)) {
    return res.status(400).json({ message: 'from and to teacher must differ' });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: affected } = await client.query(
      'SELECT id FROM deaf_individuals WHERE assigned_teacher_id = $1 AND is_deleted = false',
      [from_teacher_id]
    );
    for (const row of affected) {
      await client.query(
        'UPDATE deaf_individuals SET assigned_teacher_id = $1, assigned_date = CURRENT_DATE WHERE id = $2',
        [to_teacher_id, row.id]
      );
      await client.query(
        `INSERT INTO teacher_assignment_history (deaf_individual_id, old_teacher_id, new_teacher_id, changed_by, reason) VALUES ($1, $2, $3, $4, $5)`,
        [row.id, from_teacher_id, to_teacher_id, req.user.id, reason || null]
      );
    }
    await client.query('COMMIT');
    await logAudit(req.user.id, 'BULK_REASSIGN', 'teacher', to_teacher_id, { from_teacher_id, to_teacher_id, count: affected.length });
    res.json({ reassigned_count: affected.length });
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
});

module.exports = { list, create, update, remove, bulkReassign };
