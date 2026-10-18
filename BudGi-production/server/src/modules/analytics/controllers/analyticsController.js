// modules/analytics/controllers/analyticsController.js
// req/res ONLY — all logic in services

'use strict';

const analyticsService = require('../services/analyticsService');
const intelligence = require('../services/intelligence.service');
const { ok, asyncHandler } = require('../../../utils/response');

// ── Existing endpoints ────────────────────────────────────────────────────────

const getRecurring = asyncHandler(async (req, res) => {
  const result = await analyticsService.getRecurring(req.user.id);
  return ok(res, result);
});

const getAnomalies = asyncHandler(async (req, res) => {
  const result = await analyticsService.getAnomalies(req.user.id, req.query.days);
  return ok(res, result);
});

const getCurrencyRates = asyncHandler(async (req, res) => {
  const result = await analyticsService.getCurrencyRates(req.query.base);
  return ok(res, result);
});

const getMarketData = asyncHandler(async (req, res) => {
  const result = await analyticsService.getMarketData(req.query.symbol);
  return ok(res, result);
});

const getMonthlyReport = asyncHandler(async (req, res) => {
  const result = await analyticsService.getMonthlyReport(req.user.id, req.query.year, req.query.month);
  return ok(res, result);
});

// ── Intelligence endpoints ────────────────────────────────────────────────────

// GET /api/analytics/summary?year=2025&month=4
const getSpendingSummary = asyncHandler(async (req, res) => {
  const result = await intelligence.getSpendingSummary(req.user.id, req.query);
  return ok(res, result);
});

// GET /api/analytics/predict?lookbackMonths=6&byCategory=true
const getPrediction = asyncHandler(async (req, res) => {
  const result = await intelligence.getPrediction(req.user.id, {
    lookbackMonths: req.query.lookbackMonths,
    byCategory: req.query.byCategory === 'true',
  });
  return ok(res, result);
});

// POST /api/analytics/alerts/run
const runAlerts = asyncHandler(async (req, res) => {
  const result = await intelligence.runIntelligenceAlerts(req.user.id, req.body);
  return ok(res, result, `${result.triggered} alert(s) triggered`);
});

// GET /api/analytics/dashboard
const getDashboard = asyncHandler(async (req, res) => {
  const result = await intelligence.getFullDashboard(req.user.id);
  return ok(res, result);
});

// GET /api/analytics/report/pdf — download PDF spending report
const downloadPdfReport = asyncHandler(async (req, res) => {
  const reportService = require('../services/reportService');
  const aiService = require('../../ai/services/aiService');

  const now = new Date();
  const year = parseInt(req.query.year) || now.getFullYear();
  const month = parseInt(req.query.month) || now.getMonth() + 1;
  const monthName = new Date(year, month - 1, 1).toLocaleString('en', { month: 'long', year: 'numeric' });

  // Optional: Fetch a quick AI insight to inject into the PDF
  const aiInsights = [];
  try {
    // We pass empty data, AI service will use fallback logic if it needs to,
    // or we can just skip it if it's too slow.
    const aiResp = await aiService.callAI({ userMessage: 'Generate a 1-sentence insight for my monthly PDF report.' });
    if (aiResp && aiResp.insight) aiInsights.push(aiResp.insight);
    if (aiResp && aiResp.suggestion) aiInsights.push(aiResp.suggestion);
  } catch (e) {
    // Ignore AI failure for PDF
  }

  const pdfBuffer = await reportService.generateMonthlyReport(req.user.id, year, month, aiInsights);

  res.setHeader('Content-Type', 'application/pdf');
  res.setHeader('Content-Disposition', `attachment; filename="BudGi-Report-${monthName.replace(' ', '-')}.pdf"`);
  res.setHeader('Content-Length', pdfBuffer.length);
  res.end(pdfBuffer);
});

module.exports = {
  getRecurring,
  getAnomalies,
  getCurrencyRates,
  getMarketData,
  getMonthlyReport,
  downloadPdfReport,
  getSpendingSummary,
  getPrediction,
  runAlerts,
  getDashboard,
};
