// modules/budgets/controllers/budgetController.js
// req/res ONLY — all logic in budgetService

'use strict';

const budgetService = require('../services/budgetService');
const { ok, created, asyncHandler } = require('../../../utils/response');

const getBudgets = asyncHandler(async (req, res) => {
  const result = await budgetService.listBudgets(req.user.id);
  return ok(res, result);
});

const createBudget = asyncHandler(async (req, res) => {
  const budget = await budgetService.createBudget(req.user.id, req.body);
  return created(res, { budget }, 'Budget created');
});

const updateBudget = asyncHandler(async (req, res) => {
  const budget = await budgetService.updateBudget(req.user.id, req.params.id, req.body);
  return ok(res, { budget }, 'Budget updated');
});

const deleteBudget = asyncHandler(async (req, res) => {
  await budgetService.deleteBudget(req.user.id, req.params.id);
  return ok(res, {}, 'Budget deleted');
});

module.exports = { getBudgets, createBudget, updateBudget, deleteBudget };
