const express = require('express');
const { requireAuth } = require('../middleware/auth');
const upload = require('../middleware/upload');
const ctrl = require('../controllers/auth.controller');

const router = express.Router();

router.post('/login', ctrl.login);
router.get('/me', requireAuth, ctrl.me);
router.post('/change-password', requireAuth, ctrl.changePassword);
router.post('/photo', requireAuth, upload.single('photo'), ctrl.uploadPhoto);

module.exports = router;
