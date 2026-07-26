const express = require('express');
const { requireAuth } = require('../middleware/auth');
const upload = require('../middleware/upload');
const ctrl = require('../controllers/auth.controller');

const router = express.Router();

router.post('/login', ctrl.login);
router.post('/signup', ctrl.signup);
router.post('/forgot-password', ctrl.forgotPassword);
router.get('/me', requireAuth, ctrl.me);
router.post('/change-password', requireAuth, ctrl.changePassword);
router.post('/logout', requireAuth, ctrl.logout);
router.post('/photo', requireAuth, upload.single('photo'), ctrl.uploadPhoto);
router.put('/share-location', requireAuth, ctrl.shareLocation);

module.exports = router;
