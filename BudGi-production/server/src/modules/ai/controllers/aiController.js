// modules/ai/controllers/aiController.js
// req/res ONLY — all logic in aiOrchestrationService

'use strict';

const svc = require('../services/aiOrchestrationService');
const { ok, asyncHandler } = require('../../../utils/response');
const { validationError } = require('../../../utils/validators');

// GET /api/ai/insights
// Returns { insight, warning, suggestion } + analytics + prediction summary
const getInsights = asyncHandler(async (req, res) => {
  const result = await svc.getInsights(req.user.id);
  return ok(res, result);
});

// GET /api/ai/suggestions
// Returns { insight, warning, suggestion } nudged toward actionable advice
const getSuggestions = asyncHandler(async (req, res) => {
  const result = await svc.getSuggestions(req.user.id);
  return ok(res, result);
});

// POST /api/ai/analyze
// Body: { analytics?, alerts?, prediction?, userMessage }
// Accepts an optional full context override or falls back to fetching from DB
const analyze = asyncHandler(async (req, res) => {
  const { userMessage } = req.body;
  if (!userMessage || typeof userMessage !== 'string' || !userMessage.trim()) {
    throw validationError([{ field: 'userMessage', message: 'userMessage is required' }]);
  }
  const result = await svc.analyze(req.user.id, userMessage);
  return ok(res, result);
});

// POST /api/ai/chat
// Body: { messages: [{ role, content }] }
const chat = asyncHandler(async (req, res) => {
  const result = await svc.chat(req.user.id, req.body.messages);
  return ok(res, result);
});

// GET /api/ai/predict
// Returns forecast + aiNarrative + warning + suggestion
const predict = asyncHandler(async (req, res) => {
  const result = await svc.predict(req.user.id);
  return ok(res, result);
});

// GET /api/ai/trends?months=6
const trends = asyncHandler(async (req, res) => {
  const result = await svc.trends(req.user.id, req.query.months);
  return ok(res, result);
});

// GET /api/ai/health-score
const getHealthScore = asyncHandler(async (req, res) => {
  const result = await svc.getHealthScore(req.user.id);
  return ok(res, result);
});

// GET /api/ai/chat/history
const getChatHistory = asyncHandler(async (req, res) => {
  const result = await svc.getChatHistory(req.user.id);
  return ok(res, { messages: result });
});

// DELETE /api/ai/chat/history
const clearChatHistory = asyncHandler(async (req, res) => {
  await svc.clearChatHistory(req.user.id);
  return ok(res, { success: true }, 'Chat history cleared');
});

// GET /api/ai/smart-budgets
// Returns AI-analyzed spending patterns with suggested budget limits per category
const smartBudgetSuggestions = asyncHandler(async (req, res) => {
  const result = await svc.smartBudgetSuggestions(req.user.id);
  return ok(res, result);
});

module.exports = {
  getInsights,
  getSuggestions,
  analyze,
  chat,
  predict,
  trends,
  getHealthScore,
  getChatHistory,
  clearChatHistory,
  smartBudgetSuggestions,
};
