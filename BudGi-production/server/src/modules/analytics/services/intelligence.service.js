// modules/analytics/services/intelligence.service.js
//
// Orchestration layer — owns the data-fetch + core-service call + persistence cycle.
// The three core services (analyticsCore, alertCore, predictionCore) are pure math.
// This is the only file allowed to touch the DB for intelligence features.
//
// Exports:
//   getSpendingSummary(userId, opts)
//   getPrediction(userId, opts)
//   runIntelligenceAlerts(userId, opts)
//   getFullDashboard(userId, opts)

'use strict';

const analyticsRepo = require('../repositories/analyticsRepository');
const Alert = require('../../../models/Alert');

const analytics = require('./analyticsCore.service');
const alertCore = require('../../alerts/services/alertCore.service');
const prediction = require('./predictionCore.service');

// ─────────────────────────────────────────────────────────────────────────────
// getSpendingSummary
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full spending summary for a given month (or current month by default).
 *
 * @param {string} userId
 * @param {object} [opts]
 * @param {number} [opts.year]   — defaults to current UTC year
 * @param {number} [opts.month]  — 1-12, defaults to current UTC month
 * @returns {SpendingSummary}
 *
 * SpendingSummary shape:
 * {
 *   period:            { year, month }
 *   totalSpent:        { total, count }
 *   savingsRate:       { income, expenses, savings, rate }
 *   categoryBreakdown: [{ category, total, count, percentage }]
 *   topCategory:       { category, total, count, percentage } | null
 *   spendingByWeek:    [{ label, total, count }]
 * }
 */
async function getSpendingSummary(userId, { year, month } = {}) {
  const now = new Date();
  const y = year ? parseInt(year) : now.getUTCFullYear();
  const m = month ? parseInt(month) : now.getUTCMonth() + 1;

  const currentStart = new Date(Date.UTC(y, m - 1, 1));
  const currentEnd = new Date(Date.UTC(y, m, 1));

  // For trends, we look back 6 months from the end of the requested month
  const trendStart = new Date(currentEnd);
  trendStart.setUTCMonth(trendStart.getUTCMonth() - 6);

  const [currentTransactions, trendTransactions] = await Promise.all([
    analyticsRepo.findByMonthRange(userId, currentStart, currentEnd),
    analyticsRepo.findByMonthRange(userId, trendStart, currentEnd),
  ]);

  return {
    period: { year: y, month: m },
    totalSpent: analytics.totalSpent(currentTransactions),
    savingsRate: analytics.savingsRate(currentTransactions),
    categoryBreakdown: analytics.categoryBreakdown(currentTransactions, { limit: 10 }),
    topCategory: analytics.topCategory(currentTransactions),
    spendingByWeek: analytics.spendingByPeriod(currentTransactions, 'week'),
    spendingByMonth: analytics.spendingByPeriod(trendTransactions, 'month'),
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// getPrediction
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Predict next month's spending from recent history.
 *
 * @param {string} userId
 * @param {object} [opts]
 * @param {number} [opts.lookbackMonths=6]
 * @param {number} [opts.decayFactor=0.85]
 * @param {boolean}[opts.byCategory=false]  — also return per-category forecasts
 * @returns {PredictionResult}
 *
 * PredictionResult shape:
 * {
 *   overall:    Forecast                          (see predictionCore for Forecast shape)
 *   byCategory: [{ category, forecast }] | null   (null unless byCategory=true)
 * }
 */
async function getPrediction(userId, { lookbackMonths = 6, decayFactor = 0.85, byCategory = false } = {}) {
  const transactions = await analyticsRepo.findRecentMonths(userId, lookbackMonths);

  const overall = prediction.estimateNextMonth(transactions, { lookbackMonths, decayFactor });

  const categories = byCategory
    ? prediction.estimateNextMonthByCategory(transactions, { lookbackMonths, decayFactor })
    : null;

  return { overall, byCategory: categories };
}

// ─────────────────────────────────────────────────────────────────────────────
// runIntelligenceAlerts
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Evaluate all intelligence-layer alert rules (overspending + spikes),
 * deduplicate against this month's existing alerts, and persist new ones.
 *
 * Call this after any batch of new transactions is saved, or on a schedule.
 *
 * @param {string} userId
 * @param {object} [opts]
 * @param {number} [opts.historicalMonths=3]   — how many months to use as spike baseline
 * @param {number} [opts.spikeThreshold=1.5]
 * @param {number} [opts.minBaseline=100]
 * @returns {{ triggered: number, alerts: Alert[] }}
 */
async function runIntelligenceAlerts(userId, { historicalMonths = 3, spikeThreshold = 1.5, minBaseline = 100 } = {}) {
  const [budgets, current, historical] = await Promise.all([
    analyticsRepo.findBudgetsByUser(userId),
    analyticsRepo.findCurrentMonth(userId),
    analyticsRepo.findHistoricalMonths(userId, historicalMonths),
  ]);

  // Pure math — no DB inside these calls
  const rawAlerts = alertCore.runAllRules(budgets, current, historical, {
    spikeThreshold,
    minBaseline,
  });

  if (!rawAlerts.length) return { triggered: 0, alerts: [] };

  // Dedup: skip any alert type+category already created this calendar month
  const monthStart = startOfCurrentMonth();
  const created = [];

  for (const raw of rawAlerts) {
    const exists = await Alert.findOne({
      userId,
      type: raw.rule,
      category: raw.category ?? null,
      createdAt: { $gte: monthStart },
    });

    if (exists) continue;

    const doc = await Alert.create({
      userId,
      type: raw.rule,
      severity: raw.severity,
      category: raw.category ?? null,
      message: raw.message,
      metadata: buildMetadata(raw),
    });

    created.push(doc);
  }

  return { triggered: created.length, alerts: created };
}

// ─────────────────────────────────────────────────────────────────────────────
// getFullDashboard
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Single call for mobile dashboard — returns summary + prediction + active alerts
 * in one round-trip.
 *
 * @param {string} userId
 * @returns {{ summary, prediction, activeAlerts }}
 */
async function getFullDashboard(userId) {
  const [summary, pred, activeAlerts] = await Promise.all([
    getSpendingSummary(userId),
    getPrediction(userId),
    Alert.find({ userId, isRead: false }).sort({ createdAt: -1 }).limit(5).lean(),
  ]);

  return { summary, prediction: pred, activeAlerts };
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

function startOfCurrentMonth() {
  const now = new Date();
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
}

function buildMetadata(raw) {
  // Strip the message field (already on the doc) and the rule field (stored as type)
  const { rule: _rule, message: _message, ...rest } = raw;
  return rest;
}

module.exports = {
  getSpendingSummary,
  getPrediction,
  runIntelligenceAlerts,
  getFullDashboard,
};
