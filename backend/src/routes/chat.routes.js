const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/chat.controller');

const router = express.Router();

// Sessions
router.get('/sessions', requireAuth, requireAdmin, ctrl.listSessions);
router.get('/sessions/active', requireAuth, ctrl.getActiveSession);
router.get('/sessions/:id', requireAuth, ctrl.getSession);
router.post('/sessions/:id/open', requireAuth, requireAdmin, ctrl.openSession);
router.post('/sessions/:id/close', requireAuth, requireAdmin, ctrl.closeSession);
router.delete('/sessions/:id', requireAuth, requireAdmin, ctrl.deleteSession);
router.post('/sessions/:id/clear-messages', requireAuth, requireAdmin, ctrl.clearMessages);

// Recurring schedules
router.get('/recurring', requireAuth, requireAdmin, ctrl.listRecurringSchedules);
router.post('/recurring', requireAuth, requireAdmin, ctrl.createRecurringSchedule);
router.put('/recurring/:id', requireAuth, requireAdmin, ctrl.updateRecurringSchedule);
router.delete('/recurring/:id', requireAuth, requireAdmin, ctrl.deleteRecurringSchedule);

// Messages
router.get('/sessions/:id/messages', requireAuth, ctrl.getMessages);
router.post('/sessions/:id/messages', requireAuth, ctrl.sendMessage);
router.delete('/sessions/:id/messages/:messageId', requireAuth, requireAdmin, ctrl.deleteMessage);
router.post('/sessions/:id/messages/:messageId/pin', requireAuth, requireAdmin, ctrl.pinMessage);
router.post('/sessions/:id/messages/:messageId/unpin', requireAuth, requireAdmin, ctrl.unpinMessage);
router.get('/sessions/:id/search', requireAuth, ctrl.searchMessages);

// Participants
router.get('/sessions/:id/participants', requireAuth, ctrl.listParticipants);
router.post('/sessions/:id/join', requireAuth, ctrl.joinSession);
router.post('/sessions/:id/leave', requireAuth, ctrl.leaveSession);
router.post('/sessions/:id/participants/:userId/mute', requireAuth, requireAdmin, ctrl.muteParticipant);
router.post('/sessions/:id/participants/:userId/unmute', requireAuth, requireAdmin, ctrl.unmuteParticipant);
router.post('/sessions/:id/participants/:userId/remove', requireAuth, requireAdmin, ctrl.removeParticipant);

// Notifications
router.get('/notifications', requireAuth, ctrl.getNotifications);
router.post('/notifications/read', requireAuth, ctrl.markNotificationsRead);

module.exports = router;
