const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const list = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT e.id, e.title, e.description, e.event_date, e.created_by, u.name AS created_by_name,
           e.created_at, e.updated_at
    FROM calendar_events e
    LEFT JOIN users u ON u.id = e.created_by
    ORDER BY e.event_date ASC
  `);
  res.json(rows);
});

const create = asyncHandler(async (req, res) => {
  const { title, description, event_date } = req.body;
  if (!title || !event_date) {
    return res.status(400).json({ message: 'title and event_date are required' });
  }
  const result = await pool.query(
    'INSERT INTO calendar_events (title, description, event_date, created_by) VALUES (?, ?, ?, ?)',
    [title, description || null, event_date, req.user.id]
  );
  const insertId = result.rows.insertId;
  const { rows } = await pool.query('SELECT created_at, updated_at FROM calendar_events WHERE id = ?', [insertId]);
  await logAudit(req.user.id, 'CREATE', 'calendar_event', insertId, { title, event_date });
  res.status(201).json({
    id: insertId,
    title,
    description: description || null,
    event_date,
    created_by: req.user.id,
    created_by_name: req.user.name,
    created_at: rows[0].created_at,
    updated_at: rows[0].updated_at
  });
});

const update = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { title, description, event_date } = req.body;
  if (!title || !event_date) {
    return res.status(400).json({ message: 'title and event_date are required' });
  }
  const result = await pool.query(
    'UPDATE calendar_events SET title = ?, description = ?, event_date = ? WHERE id = ?',
    [title, description || null, event_date, id]
  );
  if (!result.rows.affectedRows) return res.status(404).json({ message: 'Not found' });
  await logAudit(req.user.id, 'UPDATE', 'calendar_event', id, { title, event_date });
  res.json({ id: Number(id), title, description: description || null, event_date });
});

const remove = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query('DELETE FROM calendar_events WHERE id = ?', [id]);
  if (!result.rows.affectedRows) return res.status(404).json({ message: 'Not found' });
  await logAudit(req.user.id, 'DELETE', 'calendar_event', id, null);
  res.status(204).send();
});

module.exports = { list, create, update, remove };
