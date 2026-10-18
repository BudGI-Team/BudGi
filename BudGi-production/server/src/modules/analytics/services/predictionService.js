// src/services/predictionService.js
/**
 * Prediction Service — Statistical forecasting engine
 *
 * Provides:
 *  • forecastNextMonth(userId)        → linear regression on monthly spend
 *  • detectRecurring(userId)          → find recurring transactions
 *  • detectAnomalies(userId)          → flag statistical outliers
 *  • getTrends(userId, months)        → month-over-month breakdown
 */

'use strict';

const Transaction = require('../../../models/Transaction');

// ── Next-Month Forecast ──────────────────────────────────────────────────────

/**
 * Forecast total expenses for the upcoming month using simple linear regression.
 * Uses last N months of data.
 *
 * @param {string} userId
 * @param {number} monthsBack  — how many months of history to use
 * @returns {{ predictedTotal, monthlyTotals, monthlyLabels, trendDirection }}
 */
async function forecastNextMonth(userId, monthsBack = 6) {
  const cutoff = new Date();
  cutoff.setMonth(cutoff.getMonth() - monthsBack);

  const transactions = await Transaction.find({
    userId,
    type: { $in: ['expense', 'debit'] },
    category: { $ne: 'Transfer' },
    date: { $gte: cutoff },
  }).lean();

  if (!transactions.length) {
    return { predictedTotal: 0, monthlyTotals: [], monthlyLabels: [], trendDirection: 'stable' };
  }

  // Group by YYYY-MM
  const byMonth = {};
  for (const t of transactions) {
    const key = monthKey(t.date);
    byMonth[key] = (byMonth[key] || 0) + t.amount;
  }

  const sorted = Object.entries(byMonth).sort(([a], [b]) => a.localeCompare(b));
  const labels = sorted.map(([k]) => k);
  const totals = sorted.map(([, v]) => v);

  if (totals.length < 2) {
    return {
      predictedTotal: totals[0] || 0,
      monthlyTotals: totals,
      monthlyLabels: labels,
      trendDirection: 'stable',
    };
  }

  // Linear regression: y = a + b*x
  const n = totals.length;
  const xs = totals.map((_, i) => i);
  const xMean = xs.reduce((s, x) => s + x, 0) / n;
  const yMean = totals.reduce((s, y) => s + y, 0) / n;

  const num = xs.reduce((s, x, i) => s + (x - xMean) * (totals[i] - yMean), 0);
  const den = xs.reduce((s, x) => s + Math.pow(x - xMean, 2), 0);

  const slope = den !== 0 ? num / den : 0;
  const intercept = yMean - slope * xMean;
  const predicted = Math.max(0, intercept + slope * n);

  const trendDirection = slope > totals[0] * 0.05 ? 'increasing' : slope < -totals[0] * 0.05 ? 'decreasing' : 'stable';

  return {
    predictedTotal: Math.round(predicted),
    monthlyTotals: totals,
    monthlyLabels: labels,
    trendDirection,
    slope: Math.round(slope),
    confidence: Math.min(0.95, 0.5 + n * 0.07), // More months = higher confidence
  };
}

// ── Recurring Transaction Detection ─────────────────────────────────────────

/**
 * Detect recurring expenses by finding transactions with similar descriptions
 * and amounts that appear at roughly monthly or weekly intervals.
 *
 * @param {string} userId
 * @returns {{ recurring: Array }}
 */
async function detectRecurring(userId) {
  const sixMonthsAgo = new Date();
  sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6);

  const transactions = await Transaction.find({
    userId,
    type: { $in: ['expense', 'debit'] },
    category: { $ne: 'Transfer' },
    date: { $gte: sixMonthsAgo },
  })
    .sort({ date: 1 })
    .lean();

  if (transactions.length < 4) return { recurring: [] };

  // Group by normalised description + rough amount bucket
  const groups = {};
  for (const t of transactions) {
    const key = normaliseKey(t.description, t.merchant, t.amount);
    if (!groups[key]) groups[key] = [];
    groups[key].push(t);
  }

  const recurring = [];

  for (const [key, txs] of Object.entries(groups)) {
    if (txs.length < 2) continue;

    // Check if dates are roughly periodic (monthly ±10 days or weekly ±3 days)
    const dates = txs.map((t) => new Date(t.date)).sort((a, b) => a - b);
    const gaps = [];
    for (let i = 1; i < dates.length; i++) {
      gaps.push((dates[i] - dates[i - 1]) / (1000 * 60 * 60 * 24)); // days
    }

    const avgGap = gaps.reduce((s, g) => s + g, 0) / gaps.length;
    const isMonthly = avgGap >= 20 && avgGap <= 40;
    const isWeekly = avgGap >= 4 && avgGap <= 10;

    if (!isMonthly && !isWeekly) continue;

    const avgAmount = txs.reduce((s, t) => s + t.amount, 0) / txs.length;
    const nextDue = new Date(dates[dates.length - 1]);
    nextDue.setDate(nextDue.getDate() + Math.round(avgGap));

    recurring.push({
      description: txs[0].description || txs[0].merchant || 'Unknown',
      merchant: txs[0].merchant,
      category: txs[0].category,
      avgAmount: Math.round(avgAmount),
      frequency: isMonthly ? 'monthly' : 'weekly',
      occurrences: txs.length,
      lastDate: dates[dates.length - 1],
      nextDue,
      transactionIds: txs.map((t) => t._id),
    });

    // Tag transactions as recurring in DB (background, non-blocking)
    Transaction.updateMany(
      { _id: { $in: txs.map((t) => t._id) } },
      { $set: { isRecurring: true, recurringId: key } },
    ).catch(() => {});
  }

  return { recurring: recurring.slice(0, 20) };
}

// ── Anomaly Detection ────────────────────────────────────────────────────────

/**
 * Find transactions that are statistical outliers (z-score > 2) within their category.
 *
 * @param {string} userId
 * @param {number} lookbackDays
 * @returns {{ anomalies: Array }}
 */
async function detectAnomalies(userId, lookbackDays = 90) {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - lookbackDays);

  const transactions = await Transaction.find({
    userId,
    type: { $in: ['expense', 'debit'] },
    category: { $ne: 'Transfer' },
    date: { $gte: cutoff },
  }).lean();

  if (transactions.length < 5) return { anomalies: [] };

  // Group by category
  const byCategory = {};
  for (const t of transactions) {
    const cat = t.category || 'Other';
    if (!byCategory[cat]) byCategory[cat] = [];
    byCategory[cat].push(t);
  }

  const anomalies = [];

  for (const [category, txs] of Object.entries(byCategory)) {
    if (txs.length < 3) continue;

    const amounts = txs.map((t) => t.amount);
    const mean = amounts.reduce((s, a) => s + a, 0) / amounts.length;
    const stdDev = Math.sqrt(amounts.reduce((s, a) => s + Math.pow(a - mean, 2), 0) / amounts.length);

    if (stdDev === 0) continue;

    for (const t of txs) {
      const zScore = Math.abs((t.amount - mean) / stdDev);
      if (zScore > 2) {
        anomalies.push({
          transaction: t,
          zScore: Math.round(zScore * 100) / 100,
          categoryMean: Math.round(mean),
          categoryStdDev: Math.round(stdDev),
          isHigher: t.amount > mean,
          message: `₹${t.amount.toFixed(0)} in ${category} is ${zScore.toFixed(1)}σ ${t.amount > mean ? 'above' : 'below'} your typical ₹${mean.toFixed(0)}.`,
        });

        // Flag in DB
        Transaction.updateOne({ _id: t._id }, { $set: { isAnomaly: true } }).catch(() => {});
      }
    }
  }

  // Sort by z-score descending
  anomalies.sort((a, b) => b.zScore - a.zScore);
  return { anomalies: anomalies.slice(0, 10) };
}

// ── Trends ───────────────────────────────────────────────────────────────────

/**
 * Month-over-month spending breakdown by category.
 */
async function getTrends(userId, months = 6) {
  const cutoff = new Date();
  cutoff.setMonth(cutoff.getMonth() - months);

  const transactions = await Transaction.find({
    userId,
    type: { $in: ['expense', 'debit'] },
    category: { $ne: 'Transfer' },
    date: { $gte: cutoff },
  }).lean();

  // Build: { 'YYYY-MM': { category: amount } }
  const byMonthCategory = {};
  for (const t of transactions) {
    const mk = monthKey(t.date);
    const cat = t.category || 'Other';
    if (!byMonthCategory[mk]) byMonthCategory[mk] = {};
    byMonthCategory[mk][cat] = (byMonthCategory[mk][cat] || 0) + t.amount;
  }

  const sortedMonths = Object.keys(byMonthCategory).sort();
  const allCategories = [...new Set(transactions.map((t) => t.category || 'Other'))];

  // Overall month totals
  const monthlyTotals = sortedMonths.map((mk) => Object.values(byMonthCategory[mk]).reduce((s, v) => s + v, 0));

  // MoM change for last two months
  const momChange =
    monthlyTotals.length >= 2 ? ((monthlyTotals.at(-1) - monthlyTotals.at(-2)) / monthlyTotals.at(-2)) * 100 : null;

  return {
    months: sortedMonths,
    monthlyTotals,
    byCategory: Object.fromEntries(
      allCategories.map((cat) => [cat, sortedMonths.map((mk) => byMonthCategory[mk]?.[cat] || 0)]),
    ),
    momChange: momChange != null ? Math.round(momChange) : null,
    topCategory: getTopCategory(transactions),
  };
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function monthKey(date) {
  const d = new Date(date);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

function normaliseKey(description, merchant, amount) {
  const text = (description || merchant || '').toLowerCase().trim().slice(0, 30);
  // Round amount to nearest 100 for bucketing
  const bucket = Math.round(amount / 100) * 100;
  return `${text}:${bucket}`;
}

function getTopCategory(transactions) {
  const byCategory = {};
  for (const t of transactions) {
    byCategory[t.category] = (byCategory[t.category] || 0) + t.amount;
  }
  const sorted = Object.entries(byCategory).sort((a, b) => b[1] - a[1]);
  return sorted[0] ? { category: sorted[0][0], amount: sorted[0][1] } : null;
}

module.exports = {
  forecastNextMonth,
  detectRecurring,
  detectAnomalies,
  getTrends,
};
