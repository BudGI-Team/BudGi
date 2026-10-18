// modules/analytics/routes/index.js
'use strict';

const router = require('express').Router();
const auth = require('../../../middleware/auth');
const c = require('../controllers/analyticsController');

router.use(auth);

// Existing
router.get('/recurring', c.getRecurring);
router.get('/anomalies', c.getAnomalies);
router.get('/currency', c.getCurrencyRates);
router.get('/market', c.getMarketData);
router.get('/report', c.getMonthlyReport);

// Intelligence layer
router.get('/summary', c.getSpendingSummary); // totalSpent + categoryBreakdown + topCategory
router.get('/predict', c.getPrediction); // next-month estimate
router.get('/dashboard', c.getDashboard); // summary + prediction + active alerts
router.post('/alerts/run', c.runAlerts); // trigger overspending + spike checks
router.get('/report/pdf', c.downloadPdfReport); // PDF spending report download

module.exports = router;
