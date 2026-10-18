// modules/mobile/controllers/mobileController.js
//
// Mobile-specific endpoints: deep linking, widget data, app config

'use strict';

const mongoose = require('mongoose');
const { ok, asyncHandler } = require('../../../utils/response');
const Transaction = require('../../../models/Transaction');
const Budget = require('../../../models/Budget');

// ─────────────────────────────────────────────────────────────────────────────
// Deep Link resolver
// GET /api/mobile/deep-link?type=transaction&id=xxx
// Returns the data needed for the Android app to navigate to the right screen
// ─────────────────────────────────────────────────────────────────────────────

const resolveDeepLink = asyncHandler(async (req, res) => {
  const { type, id } = req.query;

  if (!type) {
    return ok(res, { screen: 'home' });
  }

  switch (type) {
    case 'transaction': {
      if (!id) return ok(res, { screen: 'transactions' });
      const tx = await Transaction.findOne({ _id: id, userId: req.user.id }).lean();
      return ok(res, tx ? { screen: 'transaction_detail', data: tx } : { screen: 'transactions' });
    }
    case 'budget': {
      if (!id) return ok(res, { screen: 'budgets' });
      const budget = await Budget.findOne({ _id: id, userId: req.user.id }).lean();
      return ok(res, budget ? { screen: 'budget_detail', data: budget } : { screen: 'budgets' });
    }
    case 'alert':
      return ok(res, { screen: 'alerts', data: { alertId: id } });
    case 'copilot':
      return ok(res, { screen: 'copilot', data: { prompt: req.query.prompt || null } });
    case 'insights':
      return ok(res, { screen: 'insights' });
    default:
      return ok(res, { screen: 'home' });
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// Widget data — lightweight summary for Android home screen widgets
// GET /api/mobile/widget
// ─────────────────────────────────────────────────────────────────────────────

const getWidgetData = asyncHandler(async (req, res) => {
  const userId = req.user.id;
  const now = new Date();
  const monthStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));

  // Get this month's spending summary
  const [expenseAgg, incomeAgg, recentTxns, budgets] = await Promise.all([
    Transaction.aggregate([
      { $match: { userId: new mongoose.Types.ObjectId(userId), type: 'expense', date: { $gte: monthStart } } },
      { $group: { _id: null, total: { $sum: '$amount' }, count: { $sum: 1 } } },
    ]),
    Transaction.aggregate([
      { $match: { userId: new mongoose.Types.ObjectId(userId), type: 'income', date: { $gte: monthStart } } },
      { $group: { _id: null, total: { $sum: '$amount' } } },
    ]),
    Transaction.find({ userId }).sort({ date: -1 }).limit(5).lean(),
    Budget.find({ userId }).lean(),
  ]);

  const totalExpense = expenseAgg[0]?.total || 0;
  const totalIncome = incomeAgg[0]?.total || 0;
  const txCount = expenseAgg[0]?.count || 0;

  // Budget status — find most critical
  const budgetAlerts = budgets
    .map((b) => {
      const monthSpending = recentTxns
        .filter((t) => t.category === b.category && t.type === 'expense')
        .reduce((s, t) => s + t.amount, 0);
      return {
        category: b.category,
        limit: b.limit,
        spent: monthSpending,
        percentage: b.limit > 0 ? Math.round((monthSpending / b.limit) * 100) : 0,
      };
    })
    .filter((b) => b.percentage > 75);

  return ok(res, {
    monthlyExpense: Math.round(totalExpense),
    monthlyIncome: Math.round(totalIncome),
    netSavings: Math.round(totalIncome - totalExpense),
    transactionCount: txCount,
    recentTransactions: recentTxns.slice(0, 3).map((t) => ({
      merchant: t.merchant || t.description,
      amount: t.amount,
      type: t.type,
      category: t.category,
      date: t.date,
    })),
    budgetAlerts,
    lastUpdated: now.toISOString(),
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// App config — version check, feature flags
// GET /api/mobile/config
// ─────────────────────────────────────────────────────────────────────────────

const getAppConfig = asyncHandler(async (_req, res) => {
  return ok(res, {
    minAppVersion: '1.0.0',
    latestAppVersion: '1.0.0',
    forceUpdate: false,
    maintenance: false,
    features: {
      smsParsing: true,
      aiCopilot: true,
      offlineSync: true,
      csvImport: true,
      deepLinks: true,
      pushNotifications: true,
    },
    apiVersion: 'v1',
  });
});

module.exports = { resolveDeepLink, getWidgetData, getAppConfig };
