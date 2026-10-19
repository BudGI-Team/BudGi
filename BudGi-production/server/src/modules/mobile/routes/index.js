// modules/mobile/routes/index.js
'use strict';

const router = require('express').Router();
const auth = require('../../../middleware/auth');
const c = require('../controllers/mobileController');

// Public — app config, update checks, and direct APK download (no auth needed)
router.get('/config', c.getAppConfig);
router.get('/check-update', c.checkUpdate);
router.get('/download', c.downloadApk);

// Protected
router.get('/deep-link', auth, c.resolveDeepLink);
router.get('/widget', auth, c.getWidgetData);

module.exports = router;
