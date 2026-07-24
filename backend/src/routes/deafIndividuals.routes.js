const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const upload = require('../middleware/upload');
const ctrl = require('../controllers/deafIndividuals.controller');
const visitsCtrl = require('../controllers/visits.controller');

const router = express.Router();

router.get('/', requireAuth, ctrl.list);
router.get('/:id', requireAuth, ctrl.getById);
router.post('/', requireAuth, ctrl.create);
router.put('/:id', requireAuth, ctrl.update);
router.delete('/:id', requireAuth, requireAdmin, ctrl.remove);
router.post('/:id/photo', requireAuth, upload.single('photo'), ctrl.uploadPhoto);
router.get('/:id/assignment-history', requireAuth, ctrl.getAssignmentHistory);

router.get('/:deafId/visits', requireAuth, visitsCtrl.listForDeaf);
router.post('/:deafId/visits', requireAuth, visitsCtrl.create);

module.exports = router;
