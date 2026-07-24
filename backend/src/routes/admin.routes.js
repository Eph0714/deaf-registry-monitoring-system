const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/admin.controller');

const router = express.Router();

router.post('/backup', requireAuth, requireAdmin, ctrl.backup);
router.get('/backups', requireAuth, requireAdmin, ctrl.listBackups);
router.get('/backups/:fileName', requireAuth, requireAdmin, ctrl.downloadBackup);

module.exports = router;
