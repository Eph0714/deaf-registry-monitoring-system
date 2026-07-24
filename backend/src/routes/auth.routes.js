const express = require('express');
const { requireAuth } = require('../middleware/auth');
const upload = require('../middleware/upload');
const ctrl = require('../controllers/auth.controller');

const router = express.Router();

router.post('/login', ctrl.login);
router.post('/signup', ctrl.signup);
router.get('/verify-email', ctrl.verifyEmail);
router.get('/me', requireAuth, ctrl.me);
router.post('/change-password', requireAuth, ctrl.changePassword);
router.post('/photo', requireAuth, upload.single('photo'), ctrl.uploadPhoto);

module.exports = router;
