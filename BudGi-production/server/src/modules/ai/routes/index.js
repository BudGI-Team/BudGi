// modules/ai/routes/index.js
'use strict';

const router = require('express').Router();
const auth = require('../../../middleware/auth');
const c = require('../controllers/aiController');

router.use(auth);

router.get('/insights', c.getInsights);
router.get('/suggestions', c.getSuggestions);
router.post('/analyze', c.analyze);
router.post('/chat', c.chat);
router.get('/chat/history', c.getChatHistory);
router.delete('/chat/history', c.clearChatHistory);
router.get('/predict', c.predict);
router.get('/trends', c.trends);
router.get('/health-score', c.getHealthScore);
router.get('/smart-budgets', c.smartBudgetSuggestions);

module.exports = router;
