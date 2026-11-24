const express = require('express');
const { requireAuth, requireAdmin } = require('../middleware/auth');
const upload = require('../middleware/upload');
const ctrl = require('../controllers/artworks.controller');

const router = express.Router();

router.get('/', requireAuth, ctrl.listArtworks);
router.get('/recycle-bin', requireAuth, requireAdmin, ctrl.listRecycleBin);
router.get('/:id', requireAuth, ctrl.getArtworkInfo);
router.post('/:folderId/upload-direct', requireAuth, requireAdmin, upload.array('images', 10), ctrl.uploadDirect);
router.post('/:folderId/upload-request', requireAuth, upload.array('images', 10), ctrl.uploadRequest);
router.put('/:id/rename', requireAuth, requireAdmin, ctrl.renameArtwork);
router.put('/:id/move', requireAuth, requireAdmin, ctrl.moveArtwork);
router.delete('/:id', requireAuth, requireAdmin, ctrl.adminDeleteArtwork);
router.post('/:id/restore', requireAuth, requireAdmin, ctrl.restoreArtwork);
router.delete('/:id/permanent', requireAuth, requireAdmin, ctrl.permanentDelete);

module.exports = router;
