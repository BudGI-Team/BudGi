// modules/sms/routes/index.js
'use strict';

const router = require('express').Router();
const auth = require('../../../middleware/auth');
const c = require('../controllers/smsController');

router.use(auth);

router.post('/parse', c.parseSingle); // Parse single SMS (preview)
router.post('/parse-batch', c.parseBatch); // Parse batch (preview)
router.post('/parse-llm', c.parseLLM); // Parse batch via LLM (preview)
router.post('/import', c.importSMS); // Parse + save as transactions
router.post('/verify-scam', c.verifyScam); // Verify if SMS is a scam

module.exports = router;
