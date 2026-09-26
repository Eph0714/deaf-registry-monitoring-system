const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');

const summary = asyncHandler(async (req, res) => {
  const { rows } = await pool.query('SELECT COUNT(*) AS total FROM deaf_individuals WHERE is_deleted = false');
  res.json({ total: rows[0].total });
});

const byMunicipality = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT m.name AS municipality, COUNT(d.id) AS total
    FROM municipalities m LEFT JOIN deaf_individuals d ON d.municipality_id = m.id AND d.is_deleted = false
    GROUP BY m.id, m.name ORDER BY m.name ASC`);
  res.json(rows);
});

const byMunicipalityStatus = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT m.name AS municipality,
           SUM(CASE WHEN d.monitoring_status = 'BS' THEN 1 ELSE 0 END) AS bs,
           SUM(CASE WHEN d.monitoring_status = 'RV' THEN 1 ELSE 0 END) AS rv,
           SUM(CASE WHEN d.monitoring_status = 'Transferred' THEN 1 ELSE 0 END) AS transferred,
           SUM(CASE WHEN d.monitoring_status = 'Unlocated' THEN 1 ELSE 0 END) AS unlocated
    FROM municipalities m
    LEFT JOIN deaf_individuals d ON d.municipality_id = m.id AND d.is_deleted = false
    GROUP BY m.id, m.name ORDER BY m.name ASC`);
  res.json(rows);
});

const byBarangay = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT m.name AS municipality, b.name AS barangay, COUNT(d.id) AS total
    FROM barangays b
    JOIN municipalities m ON m.id = b.municipality_id
    LEFT JOIN deaf_individuals d ON d.barangay_id = b.id AND d.is_deleted = false
    GROUP BY b.id, m.name, b.name ORDER BY m.name ASC, b.name ASC`);
  res.json(rows);
});

const byGender = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT gender, COUNT(*) AS total FROM deaf_individuals WHERE is_deleted = false GROUP BY gender`);
  res.json(rows);
});

const bySkill = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT skill_level, COUNT(*) AS total FROM deaf_individuals WHERE is_deleted = false GROUP BY skill_level`);
  res.json(rows);
});

const byStatus = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT monitoring_status, COUNT(*) AS total FROM deaf_individuals WHERE is_deleted = false GROUP BY monitoring_status`);
  res.json(rows);
});

const byConductor = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT t.name AS conductor, COUNT(d.id) AS total
    FROM teachers t LEFT JOIN deaf_individuals d ON d.assigned_teacher_id = t.id AND d.is_deleted = false
    GROUP BY t.id, t.name ORDER BY t.name ASC`);
  res.json(rows);
});

const recentVisits = asyncHandler(async (req, res) => {
  const limit = Math.min(Number(req.query.limit) || 20, 200);
  const { rows } = await pool.query(`
    SELECT v.id, v.visit_datetime, v.conductor_name, d.full_name, d.id AS deaf_individual_id, d.uuid
    FROM visits v JOIN deaf_individuals d ON d.id = v.deaf_individual_id
    WHERE d.is_deleted = false
    ORDER BY v.visit_datetime DESC LIMIT ?`, [limit]);
  res.json(rows);
});

const notVisited = asyncHandler(async (req, res) => {
  const days = Number(req.query.days) || 30;
  const { rows } = await pool.query(`
    SELECT d.id, d.uuid, d.full_name, m.name AS municipality, b.name AS barangay,
           MAX(v.visit_datetime) AS last_visit
    FROM deaf_individuals d
    JOIN municipalities m ON m.id = d.municipality_id
    JOIN barangays b ON b.id = d.barangay_id
    LEFT JOIN visits v ON v.deaf_individual_id = d.id
    WHERE d.is_deleted = false
    GROUP BY d.id, d.uuid, d.full_name, m.name, b.name
    HAVING MAX(v.visit_datetime) IS NULL OR MAX(v.visit_datetime) < NOW() - INTERVAL ? DAY
    ORDER BY last_visit ASC`, [days]);
  res.json(rows);
});

module.exports = { summary, byMunicipality, byMunicipalityStatus, byBarangay, byGender, bySkill, byStatus, byConductor, recentVisits, notVisited };
