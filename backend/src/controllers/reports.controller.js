const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');

// COUNT(*) in Postgres returns a bigint, which node-postgres surfaces as a string
// (bigints can exceed JS's safe integer range). These reports' counts are always
// small, so cast to ::int in SQL rather than parsing every result in JS.

const summary = asyncHandler(async (req, res) => {
  const { rows } = await pool.query('SELECT COUNT(*)::int AS total FROM deaf_individuals WHERE is_deleted = false');
  res.json({ total: rows[0].total });
});

const byMunicipality = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT m.name AS municipality, COUNT(d.id)::int AS total
    FROM municipalities m LEFT JOIN deaf_individuals d ON d.municipality_id = m.id AND d.is_deleted = false
    GROUP BY m.id, m.name ORDER BY m.name ASC`);
  res.json(rows);
});

const byMunicipalityStatus = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT m.name AS municipality,
           COUNT(*) FILTER (WHERE d.monitoring_status = 'BS')::int AS bs,
           COUNT(*) FILTER (WHERE d.monitoring_status = 'RV')::int AS rv,
           COUNT(*) FILTER (WHERE d.monitoring_status = 'Transferred')::int AS transferred,
           COUNT(*) FILTER (WHERE d.monitoring_status = 'Unlocated')::int AS unlocated
    FROM municipalities m
    LEFT JOIN deaf_individuals d ON d.municipality_id = m.id AND d.is_deleted = false
    GROUP BY m.id, m.name ORDER BY m.name ASC`);
  res.json(rows);
});

const byBarangay = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT m.name AS municipality, b.name AS barangay, COUNT(d.id)::int AS total
    FROM barangays b
    JOIN municipalities m ON m.id = b.municipality_id
    LEFT JOIN deaf_individuals d ON d.barangay_id = b.id AND d.is_deleted = false
    GROUP BY b.id, m.name, b.name ORDER BY m.name ASC, b.name ASC`);
  res.json(rows);
});

const byGender = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT gender, COUNT(*)::int AS total FROM deaf_individuals WHERE is_deleted = false GROUP BY gender`);
  res.json(rows);
});

const bySkill = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT skill_level, COUNT(*)::int AS total FROM deaf_individuals WHERE is_deleted = false GROUP BY skill_level`);
  res.json(rows);
});

const byStatus = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT monitoring_status, COUNT(*)::int AS total FROM deaf_individuals WHERE is_deleted = false GROUP BY monitoring_status`);
  res.json(rows);
});

const byConductor = asyncHandler(async (req, res) => {
  const { rows } = await pool.query(`
    SELECT t.name AS conductor, COUNT(d.id)::int AS total
    FROM teachers t LEFT JOIN deaf_individuals d ON d.assigned_teacher_id = t.id AND d.is_deleted = false
    GROUP BY t.id, t.name ORDER BY t.name ASC`);
  res.json(rows);
});

const recentVisits = asyncHandler(async (req, res) => {
  const limit = Math.min(Number(req.query.limit) || 20, 200);
  const { rows } = await pool.query(`
    SELECT v.id, v.visit_datetime, v.conductor_name, d.full_name, d.id AS deaf_individual_id
    FROM visits v JOIN deaf_individuals d ON d.id = v.deaf_individual_id
    ORDER BY v.visit_datetime DESC LIMIT $1`, [limit]);
  res.json(rows);
});

const notVisited = asyncHandler(async (req, res) => {
  const days = Number(req.query.days) || 30;
  const { rows } = await pool.query(`
    SELECT d.id, d.full_name, m.name AS municipality, b.name AS barangay,
           MAX(v.visit_datetime) AS last_visit
    FROM deaf_individuals d
    JOIN municipalities m ON m.id = d.municipality_id
    JOIN barangays b ON b.id = d.barangay_id
    LEFT JOIN visits v ON v.deaf_individual_id = d.id
    WHERE d.is_deleted = false
    GROUP BY d.id, d.full_name, m.name, b.name
    HAVING MAX(v.visit_datetime) IS NULL OR MAX(v.visit_datetime) < NOW() - make_interval(days => $1)
    ORDER BY last_visit ASC`, [days]);
  res.json(rows);
});

module.exports = { summary, byMunicipality, byMunicipalityStatus, byBarangay, byGender, bySkill, byStatus, byConductor, recentVisits, notVisited };
