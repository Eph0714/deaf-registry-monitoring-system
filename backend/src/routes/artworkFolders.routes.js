const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/artworkFolders.controller');

const router = express.Router();

router.get('/', requireAuth, ctrl.list);
router.get('/:id', requireAuth, ctrl.getById);
router.post('/', requireAuth, requireAdmin, ctrl.create);
router.put('/:id', requireAuth, requireAdmin, ctrl.rename);
router.delete('/:id', requireAuth, requireAdmin, ctrl.remove);

module.exports = router;
