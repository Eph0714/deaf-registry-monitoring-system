const express = require('express');
const { requireAuth } = require('../middleware/auth');
const visitsCtrl = require('../controllers/visits.controller');
const remarksCtrl = require('../controllers/remarks.controller');

const router = express.Router();

router.get('/', requireAuth, visitsCtrl.listAll);
router.delete('/:id', requireAuth, visitsCtrl.remove);
router.get('/:visitId/remarks', requireAuth, remarksCtrl.listForVisit);
router.post('/:visitId/remarks', requireAuth, remarksCtrl.create);
router.put('/:visitId/remarks/:id', requireAuth, remarksCtrl.update);
router.delete('/:visitId/remarks/:id', requireAuth, remarksCtrl.remove);

module.exports = router;
