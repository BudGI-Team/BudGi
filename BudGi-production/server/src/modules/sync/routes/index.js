// modules/sync/routes/index.js
'use strict';

const router = require('express').Router();
const auth = require('../../../middleware/auth');
const c = require('../controllers/syncController');

router.use(auth);

router.post('/', c.fullSync); // Full bidirectional sync
router.get('/pull', c.deltaPull); // Lightweight delta pull

module.exports = router;
