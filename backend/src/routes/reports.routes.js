const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const ctrl = require('../controllers/reports.controller');

const router = express.Router();

router.get('/summary', requireAuth, requireAdmin, ctrl.summary);
router.get('/by-municipality', requireAuth, requireAdmin, ctrl.byMunicipality);
router.get('/by-municipality-status', requireAuth, requireAdmin, ctrl.byMunicipalityStatus);
router.get('/by-barangay', requireAuth, requireAdmin, ctrl.byBarangay);
router.get('/by-gender', requireAuth, requireAdmin, ctrl.byGender);
router.get('/by-skill', requireAuth, requireAdmin, ctrl.bySkill);
router.get('/by-status', requireAuth, requireAdmin, ctrl.byStatus);
router.get('/by-conductor', requireAuth, requireAdmin, ctrl.byConductor);
router.get('/recent-visits', requireAuth, requireAdmin, ctrl.recentVisits);
router.get('/not-visited', requireAuth, requireAdmin, ctrl.notVisited);

module.exports = router;
