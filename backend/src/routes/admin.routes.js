const express = require('express');
const { requireAuth, requireAdmin, requireSuperAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/admin.controller');

const router = express.Router();

router.post('/backup', requireAuth, requireAdmin, ctrl.backup);
router.get('/backups', requireAuth, requireAdmin, ctrl.listBackups);
router.get('/backups/:fileName', requireAuth, requireAdmin, ctrl.downloadBackup);
router.get('/audit-logs', requireAuth, requireAdmin, ctrl.auditLogs);
router.delete('/audit-logs', requireAuth, requireAdmin, ctrl.deleteAllAuditLogs);
router.post('/reset-all', requireAuth, requireSuperAdmin, ctrl.resetAllData);

module.exports = router;
