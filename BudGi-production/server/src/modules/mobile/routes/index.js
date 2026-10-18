// modules/mobile/routes/index.js
'use strict';

const router = require('express').Router();
const auth = require('../../../middleware/auth');
const c = require('../controllers/mobileController');

// Public — app config (no auth needed, used before login)
router.get('/config', c.getAppConfig);

// Protected
router.get('/deep-link', auth, c.resolveDeepLink);
router.get('/widget', auth, c.getWidgetData);

module.exports = router;
