// modules/alerts/routes/index.js
'use strict';

const router = require('express').Router();
const auth = require('../../../middleware/auth');
const c = require('../controllers/alertController');

router.use(auth);

router.get('/', c.getAlerts);
router.get('/count', c.getUnreadCount);
router.put('/read', c.markRead);
router.delete('/', c.clearAlerts);
router.post('/run', c.runAlerts);

module.exports = router;
