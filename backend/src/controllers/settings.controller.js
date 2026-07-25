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

const APP_VERSION_KEYS = {
  version_code: 'latest_version_code',
  version_name: 'latest_version_name',
  apk_url: 'latest_apk_url',
  release_notes: 'latest_release_notes'
};

const getAppVersion = asyncHandler(async (req, res) => {
  const { rows } = await pool.query('SELECT "key", "value" FROM settings WHERE "key" = ANY($1::text[])', [Object.values(APP_VERSION_KEYS)]);
  const byKey = Object.fromEntries(rows.map((r) => [r.key, r.value]));
  res.json({
    version_code: Number(byKey[APP_VERSION_KEYS.version_code] || 0),
    version_name: byKey[APP_VERSION_KEYS.version_name] || null,
    apk_url: byKey[APP_VERSION_KEYS.apk_url] || null,
    release_notes: byKey[APP_VERSION_KEYS.release_notes] || null
  });
});

const updateAppVersion = asyncHandler(async (req, res) => {
  const { version_code, version_name, apk_url, release_notes } = req.body;
  const code = Number(version_code);
  if (!Number.isInteger(code) || code < 1) {
    return res.status(400).json({ message: 'version_code must be a positive integer' });
  }
  if (!version_name || !apk_url) {
    return res.status(400).json({ message: 'version_name and apk_url are required' });
  }
  // settings.value is VARCHAR(255) - truncate defensively so a long release note doesn't 500.
  const entries = [
    [APP_VERSION_KEYS.version_code, String(code)],
    [APP_VERSION_KEYS.version_name, String(version_name).slice(0, 255)],
    [APP_VERSION_KEYS.apk_url, String(apk_url).slice(0, 255)],
    [APP_VERSION_KEYS.release_notes, String(release_notes || '').slice(0, 255)]
  ];
  for (const [key, value] of entries) {
    await pool.query(
      'INSERT INTO settings ("key", "value") VALUES ($1, $2) ON CONFLICT ("key") DO UPDATE SET "value" = EXCLUDED.value',
      [key, value]
    );
  }
  await logAudit(req.user.id, 'UPDATE', 'setting', null, { key: 'app_version', version_code: code, version_name });
  res.json({ version_code: code, version_name, apk_url, release_notes: release_notes || null });
});

const THEME_KEY = 'app_theme';
const VALID_THEMES = ['light_blue', 'dark_purple'];

const getTheme = asyncHandler(async (req, res) => {
  const { rows } = await pool.query('SELECT "value" FROM settings WHERE "key" = $1', [THEME_KEY]);
  res.json({ theme: rows.length && VALID_THEMES.includes(rows[0].value) ? rows[0].value : VALID_THEMES[0] });
});

const updateTheme = asyncHandler(async (req, res) => {
  const { theme } = req.body;
  if (!VALID_THEMES.includes(theme)) {
    return res.status(400).json({ message: `theme must be one of: ${VALID_THEMES.join(', ')}` });
  }
  await pool.query(
    'INSERT INTO settings ("key", "value") VALUES ($1, $2) ON CONFLICT ("key") DO UPDATE SET "value" = EXCLUDED.value',
    [THEME_KEY, theme]
  );
  await logAudit(req.user.id, 'UPDATE', 'setting', null, { key: THEME_KEY, value: theme });
  res.json({ theme });
});

const LOCATION_SHARE_TTL_KEY = 'location_share_ttl_minutes';
const DEFAULT_LOCATION_SHARE_TTL_MINUTES = 60;

const getLocationShareTtl = asyncHandler(async (req, res) => {
  const { rows } = await pool.query('SELECT "value" FROM settings WHERE "key" = $1', [LOCATION_SHARE_TTL_KEY]);
  res.json({ location_share_ttl_minutes: rows.length ? Number(rows[0].value) : DEFAULT_LOCATION_SHARE_TTL_MINUTES });
});

const updateLocationShareTtl = asyncHandler(async (req, res) => {
  const { location_share_ttl_minutes } = req.body;
  const minutes = Number(location_share_ttl_minutes);
  if (!Number.isInteger(minutes) || minutes < 1) {
    return res.status(400).json({ message: 'location_share_ttl_minutes must be a positive integer' });
  }
  await pool.query(
    'INSERT INTO settings ("key", "value") VALUES ($1, $2) ON CONFLICT ("key") DO UPDATE SET "value" = EXCLUDED.value',
    [LOCATION_SHARE_TTL_KEY, String(minutes)]
  );
  await logAudit(req.user.id, 'UPDATE', 'setting', null, { key: LOCATION_SHARE_TTL_KEY, value: minutes });
  res.json({ location_share_ttl_minutes: minutes });
});

module.exports = {
  getOverdueDays, updateOverdueDays, getAppVersion, updateAppVersion, getTheme, updateTheme,
  getLocationShareTtl, updateLocationShareTtl
};
