const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/municipalities.controller');

const router = express.Router();

router.get('/', requireAuth, ctrl.list);
router.post('/', requireAuth, requireAdmin, ctrl.create);
router.put('/:id', requireAuth, requireAdmin, ctrl.update);
router.delete('/:id', requireAuth, requireAdmin, ctrl.remove);

module.exports = router;
