const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/devices.controller');

const router = express.Router();

router.get('/pending', requireAuth, requireAdmin, ctrl.listPending);
router.post('/:id/approve', requireAuth, requireAdmin, ctrl.approve);
router.post('/:id/reject', requireAuth, requireAdmin, ctrl.reject);

module.exports = router;
