const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { logAudit } = require('../utils/audit');

const listPending = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(
    `SELECT d.id, d.device_id, d.device_label, d.created_at, u.id AS user_id, u.name AS user_name, u.email AS user_email
     FROM user_devices d
     JOIN users u ON u.id = d.user_id
     WHERE d.status = 'pending'
     ORDER BY d.created_at ASC`
  );
  res.json(rows);
});

const approve = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query(
    "UPDATE user_devices SET status = 'approved', approved_by = $1, approved_at = NOW() WHERE id = $2",
    [req.user.id, id]
  );
  if (!result.rowCount) return res.status(404).json({ message: 'Not found' });
  await logAudit(req.user.id, 'DEVICE_APPROVED', 'user_device', id, null);
  res.json({ id: Number(id), status: 'approved' });
});

const reject = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const result = await pool.query(
    "UPDATE user_devices SET status = 'rejected', approved_by = $1, approved_at = NOW() WHERE id = $2",
    [req.user.id, id]
  );
  if (!result.rowCount) return res.status(404).json({ message: 'Not found' });
  await logAudit(req.user.id, 'DEVICE_REJECTED', 'user_device', id, null);
  res.json({ id: Number(id), status: 'rejected' });
});

module.exports = { listPending, approve, reject };
