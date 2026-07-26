const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/users.controller');

const router = express.Router();

router.get('/', requireAuth, requireAdmin, ctrl.list);
router.get('/locations', requireAuth, ctrl.listLocations);
router.post('/', requireAuth, requireAdmin, ctrl.create);
router.get('/pending-signups', requireAuth, requireAdmin, ctrl.listPendingSignups);
router.post('/:id/approve-signup', requireAuth, requireAdmin, ctrl.approveSignup);
router.post('/:id/reject-signup', requireAuth, requireAdmin, ctrl.rejectSignup);
router.get('/password-reset-requests', requireAuth, requireAdmin, ctrl.listPasswordResetRequests);
router.post('/password-reset-requests/:id/resolve', requireAuth, requireAdmin, ctrl.resolvePasswordResetRequest);
router.put('/:id', requireAuth, requireAdmin, ctrl.update);
router.post('/:id/reset-password', requireAuth, requireAdmin, ctrl.resetPassword);
router.delete('/:id', requireAuth, requireAdmin, ctrl.remove);
router.delete('/:id/permanent', requireAuth, requireAdmin, ctrl.permanentlyDelete);

module.exports = router;
