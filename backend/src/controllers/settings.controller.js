const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const getOverdueDays = asyncHandler(async (req, res) => {
  const { rows } = await pool.query('SELECT "value" FROM settings WHERE "key" = \'overdue_visit_days\'');
  res.json({ overdue_days: rows.length ? Number(rows[0].value) : 30 });
});

const updateOverdueDays = asyncHandler(async (req, res) => {
  const { overdue_days } = req.body;
  const days = Number(overdue_days);
  if (!Number.isInteger(days) || days < 1) {
    return res.status(400).json({ message: 'overdue_days must be a positive integer' });
  }
  await pool.query(
    'INSERT INTO settings ("key", "value") VALUES (\'overdue_visit_days\', $1) ON CONFLICT ("key") DO UPDATE SET "value" = EXCLUDED.value',
    [String(days)]
  );
  await logAudit(req.user.id, 'UPDATE', 'setting', null, { key: 'overdue_visit_days', value: days });
  res.json({ overdue_days: days });
});

module.exports = { getOverdueDays, updateOverdueDays };
