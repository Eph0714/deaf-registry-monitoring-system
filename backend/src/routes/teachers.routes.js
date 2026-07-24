const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/teachers.controller');

const router = express.Router();

router.get('/', requireAuth, ctrl.list);
router.post('/', requireAuth, requireAdmin, ctrl.create);
router.post('/bulk-reassign', requireAuth, requireAdmin, ctrl.bulkReassign);
router.put('/:id', requireAuth, requireAdmin, ctrl.update);
router.delete('/:id', requireAuth, requireAdmin, ctrl.remove);

module.exports = router;
