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
// App config & Update Information — version check, feature flags, download URLs
// GET /api/v1/mobile/config
// ─────────────────────────────────────────────────────────────────────────────

const GITHUB_REPO = 'BudGI-Team/BudGi';
const LATEST_APK_URL = `https://github.com/${GITHUB_REPO}/releases/latest/download/BudGi.apk`;
const RELEASES_PAGE_URL = `https://github.com/${GITHUB_REPO}/releases`;

const APP_UPDATE_INFO = {
  latestVersionCode: 6,
  latestVersionName: '1.2.0',
  minSupportedVersionCode: 1,
  forceUpdate: false,
  maintenance: false,
  apkUrl: LATEST_APK_URL,
  apkChecksumSha256: null,
  releasesUrl: RELEASES_PAGE_URL,
  releaseNotes: '• Precision Engine: 15+ Indian bank template parsers (HDFC, SBI, ICICI, Axis, Kotak, etc.)\n• 1-Tap Lockscreen & Shade payment categorization with zero clutter\n• Indian Market Categories: Vegetables/Mandi, Dairy/Milk, Salon, Domestic Help, Pooja\n• 1-Click Micro-Spend Catch-Up Deck with smart category suggestions\n• Auto-reconciled UPI refunds & pre-debit mandate warning shield\n• Real-time multi-account bank liquidity ledger & seamless in-app updater',
  publishedAt: new Date().toISOString(),
};

const getAppConfig = asyncHandler(async (_req, res) => {
  return ok(res, {
    minAppVersion: '1.0.0',
    latestAppVersion: APP_UPDATE_INFO.latestVersionName,
    ...APP_UPDATE_INFO,
    features: {
      smsParsing: true,
      aiCopilot: true,
      offlineSync: true,
      csvImport: true,
      deepLinks: true,
      pushNotifications: true,
      inAppUpdate: true,
    },
    apiVersion: 'v1',
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Check Update endpoint
// GET /api/v1/mobile/check-update?versionCode=1
// ─────────────────────────────────────────────────────────────────────────────
const checkUpdate = asyncHandler(async (req, res) => {
  const clientVersionCode = parseInt(req.query.versionCode, 10) || 0;
  const updateAvailable = clientVersionCode < APP_UPDATE_INFO.latestVersionCode;
  const isForced = clientVersionCode < APP_UPDATE_INFO.minSupportedVersionCode || APP_UPDATE_INFO.forceUpdate;

  return ok(res, {
    updateAvailable,
    isForced,
    currentVersionCode: clientVersionCode,
    latestVersionCode: APP_UPDATE_INFO.latestVersionCode,
    latestVersionName: APP_UPDATE_INFO.latestVersionName,
    apkUrl: APP_UPDATE_INFO.apkUrl,
    apkChecksumSha256: APP_UPDATE_INFO.apkChecksumSha256,
    releasesUrl: APP_UPDATE_INFO.releasesUrl,
    releaseNotes: APP_UPDATE_INFO.releaseNotes,
    publishedAt: APP_UPDATE_INFO.publishedAt,
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Direct APK Download redirect
// GET /api/v1/mobile/download
// ─────────────────────────────────────────────────────────────────────────────
const downloadApk = asyncHandler(async (_req, res) => {
  // 302 Redirect to latest release APK asset on GitHub
  return res.redirect(LATEST_APK_URL);
});

module.exports = {
  resolveDeepLink,
  getWidgetData,
  getAppConfig,
  checkUpdate,
  downloadApk,
  APP_UPDATE_INFO,
};
