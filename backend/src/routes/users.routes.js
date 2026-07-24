const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/users.controller');

const router = express.Router();

router.get('/', requireAuth, requireAdmin, ctrl.list);
router.post('/', requireAuth, requireAdmin, ctrl.create);
router.put('/:id', requireAuth, requireAdmin, ctrl.update);
router.post('/:id/reset-password', requireAuth, requireAdmin, ctrl.resetPassword);
router.delete('/:id', requireAuth, requireAdmin, ctrl.remove);

module.exports = router;
