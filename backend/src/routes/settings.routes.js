const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/settings.controller');

const router = express.Router();

router.get('/overdue-days', requireAuth, ctrl.getOverdueDays);
router.put('/overdue-days', requireAuth, requireAdmin, ctrl.updateOverdueDays);
router.get('/app-version', requireAuth, ctrl.getAppVersion);
router.put('/app-version', requireAuth, requireAdmin, ctrl.updateAppVersion);
router.get('/theme', requireAuth, ctrl.getTheme);
router.put('/theme', requireAuth, requireAdmin, ctrl.updateTheme);
router.get('/location-share-ttl', requireAuth, ctrl.getLocationShareTtl);
router.put('/location-share-ttl', requireAuth, requireAdmin, ctrl.updateLocationShareTtl);

module.exports = router;
