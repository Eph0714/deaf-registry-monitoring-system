const express = require('express');
const { requireAuth } = require('../middleware/auth');
const ctrl = require('../controllers/reports.controller');

const router = express.Router();

router.get('/summary', requireAuth, ctrl.summary);
router.get('/by-municipality', requireAuth, ctrl.byMunicipality);
router.get('/by-barangay', requireAuth, ctrl.byBarangay);
router.get('/by-gender', requireAuth, ctrl.byGender);
router.get('/by-skill', requireAuth, ctrl.bySkill);
router.get('/by-status', requireAuth, ctrl.byStatus);
router.get('/by-conductor', requireAuth, ctrl.byConductor);
router.get('/recent-visits', requireAuth, ctrl.recentVisits);
router.get('/not-visited', requireAuth, ctrl.notVisited);

module.exports = router;
