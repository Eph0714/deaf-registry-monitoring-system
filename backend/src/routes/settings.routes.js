const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/settings.controller');

const router = express.Router();

router.get('/overdue-days', requireAuth, ctrl.getOverdueDays);
router.put('/overdue-days', requireAuth, requireAdmin, ctrl.updateOverdueDays);

module.exports = router;
