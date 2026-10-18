// modules/budgets/services/budgetService.js
// ALL budget business logic

'use strict';

const budgetRepo = require('../repositories/budgetRepository');
const socketService = require('../../../utils/socketService');

async function listBudgets(userId) {
  const budgets = await budgetRepo.findByUser(userId);
  if (!budgets.length) return { budgets: [] };

  const now = new Date();
  const monthStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  const spending = await budgetRepo.getMonthlySpendingByCategory(userId, monthStart);
  const spentMap = Object.fromEntries(spending.map((s) => [s._id, s.spent]));

  const enriched = budgets.map((b) => {
    const spent = spentMap[b.category] || 0;
    return {
      ...b,
      spent: Math.round(spent),
      remaining: Math.round(Math.max(0, b.limit - spent)),
      percentage: b.limit > 0 ? Math.round((spent / b.limit) * 100) : 0,
      isExceeded: spent > b.limit,
      isWarning: spent / b.limit >= (b.alertAt ?? 0.9),
    };
  });

  return { budgets: enriched };
}

async function createBudget(userId, { category, limit, period = 'monthly', alertAt = 0.9, color }) {
  if (!category || !limit) {
    throw Object.assign(new Error('category and limit are required'), { statusCode: 400 });
  }
  if (parseFloat(limit) <= 0) {
    throw Object.assign(new Error('limit must be positive'), { statusCode: 400 });
  }

  const budget = await budgetRepo.create({
    userId,
    category: category.trim(),
    limit: parseFloat(limit),
    period,
    alertAt: parseFloat(alertAt),
    color: color || '#6366f1',
  });

  socketService.push(userId, 'BUDGET_ADDED', budget);

  return budget;
}

async function updateBudget(userId, id, { limit, alertAt, color, period }) {
  const budget = await budgetRepo.findById(id, userId);
  if (!budget) throw Object.assign(new Error('Budget not found'), { statusCode: 404 });

  if (limit != null) budget.limit = parseFloat(limit);
  if (alertAt != null) budget.alertAt = parseFloat(alertAt);
  if (color != null) budget.color = color;
  if (period != null) budget.period = period;

  await budgetRepo.save(budget);
  socketService.push(userId, 'BUDGET_UPDATED', budget);
  return budget;
}

async function deleteBudget(userId, id) {
  const budget = await budgetRepo.findByIdAndDelete(id, userId);
  if (!budget) throw Object.assign(new Error('Budget not found'), { statusCode: 404 });
  socketService.push(userId, 'BUDGET_DELETED', { id });
  return budget;
}

module.exports = { listBudgets, createBudget, updateBudget, deleteBudget };
