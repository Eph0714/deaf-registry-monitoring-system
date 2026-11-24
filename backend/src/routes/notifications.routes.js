const express = require('express');
const { requireAuth } = require('../middleware/auth');
const ctrl = require('../controllers/notifications.controller');

const router = express.Router();

router.get('/', requireAuth, ctrl.getNotifications);
router.post('/read', requireAuth, ctrl.markNotificationsRead);

module.exports = router;
