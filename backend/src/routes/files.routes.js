const express = require('express');
const ctrl = require('../controllers/files.controller');

const router = express.Router();

// Deliberately no requireAuth - see files.controller.js.
router.get('/:token', ctrl.getFile);

module.exports = router;
