// modules/analytics/services/predictionCore.service.js
//
// Pure statistical forecasting — no AI, no DB calls, no external dependencies.
// All math runs on plain transaction arrays passed in by the caller.
//
// Exports:
//   estimateNextMonth(transactions)
//   linearRegression(xs, ys)           — utility, exported for testing
//   weightedAverage(values, weights)   — utility, exported for testing

'use strict';

// ─────────────────────────────────────────────────────────────────────────────
// estimateNextMonth
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Estimate total expenses for the upcoming calendar month.
 *
 * Strategy:
 *  1. Group past expenses by calendar month.
 *  2. Apply exponential weights so recent months count more.
 *  3. Run linear regression across those weighted monthly totals.
 *  4. Project one period ahead.
 *  5. If fewer than 2 data points exist, fall back to the weighted average.
 *
 * RULE: This function does pure arithmetic only. The AI layer may add
 * narrative text on top — it must not touch the numbers.
 *
 * @param {Array}  transactions  — all historical expense + income transactions
 * @param {object} [opts]
 * @param {number} [opts.lookbackMonths=6]  — how many months of history to use
 * @param {number} [opts.decayFactor=0.85]  — weight applied per older month (0-1)
 *
 * @returns {Forecast}
 *
 * Forecast shape:
 * {
 *   predictedTotal:    number   — estimated expense total for next month
 *   confidence:        number   — 0–1, higher = more data points
 *   trendDirection:    'increasing' | 'decreasing' | 'stable'
 *   trendSlope:        number   — ₹ change per month (positive = growing spend)
 *   monthlyTotals:     number[] — expense totals per historical month (oldest first)
 *   monthlyLabels:     string[] — 'YYYY-MM' labels (same order)
 *   avgMonthlySpend:   number   — simple unweighted average
 *   highestMonth:      { label, total }
 *   lowestMonth:       { label, total }
 *   dataPoints:        number   — how many months fed into model
 * }
 *
 * Example:
 *   estimateNextMonth(transactions, { lookbackMonths: 6 })
 *   → {
 *       predictedTotal:  37200,
 *       confidence:      0.83,
 *       trendDirection:  'increasing',
 *       trendSlope:      800,
 *       monthlyTotals:   [28000, 30000, 31500, 33000, 34500, 36000],
 *       monthlyLabels:   ['2024-11','2024-12','2025-01','2025-02','2025-03','2025-04'],
 *       avgMonthlySpend: 32167,
 *       highestMonth:    { label: '2025-04', total: 36000 },
 *       lowestMonth:     { label: '2024-11', total: 28000 },
 *       dataPoints:      6
 *     }
 */
const isTransfer = (t) => (t.category || '').toLowerCase() === 'transfer';
const isExpense = (t) => (t.type === 'expense' || t.type === 'debit') && !isTransfer(t);

function estimateNextMonth(transactions, { lookbackMonths = 6, decayFactor = 0.85 } = {}) {
  // Step 1 — determine anchor date: use the latest transaction date (not today)
  // so that historical datasets are never silently filtered out.
  const expenseDates = transactions
    .filter(isExpense)
    .map((t) => new Date(t.date).getTime())
    .filter((ts) => !isNaN(ts));

  if (!expenseDates.length) return emptyForecast();

  const anchor = new Date(Math.max(...expenseDates));
  anchor.setUTCDate(1);
  anchor.setUTCHours(0, 0, 0, 0);

  const cutoff = new Date(anchor);
  cutoff.setUTCMonth(cutoff.getUTCMonth() - lookbackMonths);

  const expenses = transactions.filter((t) => isExpense(t) && new Date(t.date) >= cutoff);

  if (!expenses.length) return emptyForecast();

  // Step 2 — group by calendar month
  const monthMap = {};
  for (const t of expenses) {
    const key = monthKey(new Date(t.date));
    monthMap[key] = (monthMap[key] || 0) + Number(t.amount);
  }

  const sorted = Object.entries(monthMap).sort(([a], [b]) => a.localeCompare(b));
  const labels = sorted.map(([k]) => k);
  const totals = sorted.map(([, v]) => v);
  const n = totals.length;

  if (n === 0) return emptyForecast();

  // Step 3 — descriptive stats
  const avg = totals.reduce((s, v) => s + v, 0) / n;
  const highest = totals.reduce((max, v, i) => (v > max.total ? { label: labels[i], total: v } : max), {
    label: labels[0],
    total: totals[0],
  });
  const lowest = totals.reduce((min, v, i) => (v < min.total ? { label: labels[i], total: v } : min), {
    label: labels[0],
    total: totals[0],
  });

  // Step 4 — exponential weights (newest month = weight 1.0, each older month * decayFactor)
  const weights = totals.map((_, i) => Math.pow(decayFactor, n - 1 - i));

  if (n === 1) {
    return {
      predictedTotal: round(totals[0]),
      confidence: 0.3,
      trendDirection: 'stable',
      trendSlope: 0,
      monthlyTotals: totals.map(round),
      monthlyLabels: labels,
      avgMonthlySpend: round(avg),
      highestMonth: { label: highest.label, total: round(highest.total) },
      lowestMonth: { label: lowest.label, total: round(lowest.total) },
      dataPoints: 1,
    };
  }

  // Step 5 — weighted linear regression
  const xs = totals.map((_, i) => i);
  const { slope, intercept } = linearRegression(xs, totals, weights);
  const predicted = Math.max(0, intercept + slope * n);

  // Step 6 — trend direction (slope > 2% of average = directional)
  const threshold = avg * 0.02;
  const trendDirection = slope > threshold ? 'increasing' : slope < -threshold ? 'decreasing' : 'stable';

  // Step 7 — confidence: 30% base + 7% per data point, capped at 95%
  const confidence = Math.min(0.95, 0.3 + n * 0.07);

  return {
    predictedTotal: round(predicted),
    confidence: round(confidence, 2),
    trendDirection,
    trendSlope: round(slope),
    monthlyTotals: totals.map(round),
    monthlyLabels: labels,
    avgMonthlySpend: round(avg),
    highestMonth: { label: highest.label, total: round(highest.total) },
    lowestMonth: { label: lowest.label, total: round(lowest.total) },
    dataPoints: n,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// estimateNextMonthByCategory
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Run estimateNextMonth independently per category.
 * Useful for budget suggestion screens.
 *
 * @param {Array}  transactions
 * @param {object} [opts]  — forwarded to estimateNextMonth
 * @returns {Array<{ category: string, forecast: Forecast }>}
 *
 * Example:
 *   estimateNextMonthByCategory(transactions)
 *   → [
 *       { category: 'Food & Dining', forecast: { predictedTotal: 8500, ... } },
 *       { category: 'Transport',     forecast: { predictedTotal: 4300, ... } },
 *     ]
 */
function estimateNextMonthByCategory(transactions, opts = {}) {
  const categories = [
    ...new Set(transactions.filter((t) => t.type === 'expense').map((t) => t.category || 'Uncategorized')),
  ];

  return categories
    .map((category) => ({
      category,
      forecast: estimateNextMonth(
        // Pass ALL transactions so anchor detection uses global latest date,
        // but pre-filter to this category's expenses + all income (for context)
        transactions.filter((t) => t.type === 'income' || (t.category || 'Uncategorized') === category),
        opts,
      ),
    }))
    .sort((a, b) => b.forecast.predictedTotal - a.forecast.predictedTotal);
}

// ─────────────────────────────────────────────────────────────────────────────
// linearRegression  (exported for unit testing)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Weighted ordinary-least-squares linear regression.
 * y = intercept + slope * x
 *
 * @param {number[]} xs       — independent variable values
 * @param {number[]} ys       — dependent variable values
 * @param {number[]} [weights] — optional per-point weights (same length as xs)
 * @returns {{ slope: number, intercept: number }}
 *
 * Example:
 *   linearRegression([0,1,2,3,4,5], [28000,30000,31500,33000,34500,36000])
 *   → { slope: 1580, intercept: 28040 }
 */
function linearRegression(xs, ys, weights) {
  const n = xs.length;
  if (n === 0) return { slope: 0, intercept: 0 };

  const w = weights ?? Array(n).fill(1);

  const wSum = w.reduce((s, v) => s + v, 0);
  const xMean = xs.reduce((s, x, i) => s + x * w[i], 0) / wSum;
  const yMean = ys.reduce((s, y, i) => s + y * w[i], 0) / wSum;

  let num = 0;
  let den = 0;
  for (let i = 0; i < n; i++) {
    num += w[i] * (xs[i] - xMean) * (ys[i] - yMean);
    den += w[i] * Math.pow(xs[i] - xMean, 2);
  }

  const slope = den !== 0 ? num / den : 0;
  const intercept = yMean - slope * xMean;
  return { slope, intercept };
}

// ─────────────────────────────────────────────────────────────────────────────
// weightedAverage  (exported for unit testing)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param {number[]} values
 * @param {number[]} weights
 * @returns {number}
 *
 * Example:
 *   weightedAverage([100, 200, 300], [0.25, 0.25, 0.50]) → 225
 */
function weightedAverage(values, weights) {
  const wSum = weights.reduce((s, w) => s + w, 0);
  if (wSum === 0) return 0;
  return values.reduce((s, v, i) => s + v * weights[i], 0) / wSum;
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

function emptyForecast() {
  return {
    predictedTotal: 0,
    confidence: 0,
    trendDirection: 'stable',
    trendSlope: 0,
    monthlyTotals: [],
    monthlyLabels: [],
    avgMonthlySpend: 0,
    highestMonth: null,
    lowestMonth: null,
    dataPoints: 0,
  };
}

function monthKey(date) {
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

function round(n, decimals = 0) {
  const f = Math.pow(10, decimals);
  return Math.round(n * f) / f;
}

module.exports = {
  estimateNextMonth,
  estimateNextMonthByCategory,
  linearRegression,
  weightedAverage,
};
