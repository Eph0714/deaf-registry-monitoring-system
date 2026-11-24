const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/artworkRequests.controller');

const router = express.Router();

router.post('/delete', requireAuth, ctrl.submitDeleteRequest);
router.get('/', requireAuth, requireAdmin, ctrl.listRequests);
router.get('/mine', requireAuth, ctrl.listMyRequests);
router.get('/:id', requireAuth, requireAdmin, ctrl.getRequestDetail);
router.post('/:id/approve', requireAuth, requireAdmin, ctrl.approveRequest);
router.post('/:id/reject', requireAuth, requireAdmin, ctrl.rejectRequest);
router.post('/:id/cancel', requireAuth, ctrl.cancelOwnRequest);

module.exports = router;
