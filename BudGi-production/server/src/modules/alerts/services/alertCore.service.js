// modules/alerts/services/alertCore.service.js
//
// Pure rule engine — no AI, no DB writes, no side effects.
// Every function receives pre-fetched data and returns a plain result array.
// Persistence is the caller's responsibility.
//
// Exports:
//   detectOverspending(budgets, transactions)
//   detectSpike(currentTransactions, historicalTransactions)
//   runAllRules(budgets, currentTransactions, historicalTransactions)

'use strict';

// ─────────────────────────────────────────────────────────────────────────────
// detectOverspending
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compare current-period expenses against each budget limit.
 * Returns an alert for every budget that has hit its warning threshold or exceeded it.
 *
 * @param {Array} budgets
 *   Each budget: { category, limit, alertAt? (0-1 fraction, default 0.9), period? }
 *
 * @param {Array} transactions
 *   Current-period transactions (caller decides the window — day/week/month).
 *
 * @returns {Array<OverspendingAlert>}
 *
 * OverspendingAlert shape:
 * {
 *   rule:        'budget_exceeded' | 'budget_warning'
 *   severity:    'critical'        | 'warning'
 *   category:    string
 *   spent:       number
 *   limit:       number
 *   remaining:   number    (negative when exceeded)
 *   percentage:  number    (e.g. 117 means 117% of limit)
 *   message:     string
 * }
 *
 * Example:
 *   detectOverspending(
 *     [{ category: 'Food & Dining', limit: 7000, alertAt: 0.85 }],
 *     transactions
 *   )
 *   → [{
 *       rule:       'budget_exceeded',
 *       severity:   'critical',
 *       category:   'Food & Dining',
 *       spent:      8200,
 *       limit:      7000,
 *       remaining:  -1200,
 *       percentage: 117,
 *       message:    'Food & Dining is over budget — spent ₹8,200 of ₹7,000 (117%)'
 *     }]
 */
function detectOverspending(budgets, transactions) {
  if (!budgets.length || !transactions.length) return [];

  // Aggregate expenses per category from transactions
  const spentByCategory = {};
  for (const t of transactions) {
    if (t.type !== 'expense') continue;
    const cat = (t.category || 'Uncategorized').trim();
    spentByCategory[cat] = (spentByCategory[cat] || 0) + Number(t.amount);
  }

  const alerts = [];

  for (const budget of budgets) {
    const { category, limit, alertAt = 0.9 } = budget;
    if (!limit || limit <= 0) continue;

    const spent = spentByCategory[category] || 0;
    const percentage = round((spent / limit) * 100);
    const remaining = round(limit - spent);

    if (spent > limit) {
      alerts.push({
        rule: 'budget_exceeded',
        severity: 'critical',
        category,
        spent: round(spent),
        limit: round(limit),
        remaining,
        percentage,
        message: `${category} is over budget — spent ₹${fmt(spent)} of ₹${fmt(limit)} (${percentage}%)`,
      });
    } else if (spent / limit >= alertAt) {
      alerts.push({
        rule: 'budget_warning',
        severity: 'warning',
        category,
        spent: round(spent),
        limit: round(limit),
        remaining,
        percentage,
        message: `${category} is at ${percentage}% of its ₹${fmt(limit)} limit — ₹${fmt(remaining)} remaining`,
      });
    }
  }

  // Highest percentage first
  alerts.sort((a, b) => b.percentage - a.percentage);
  return alerts;
}

// ─────────────────────────────────────────────────────────────────────────────
// detectSpike
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compare current-period per-category spending against a historical baseline.
 * A "spike" fires when the current total for a category exceeds the historical
 * average by more than `spikeThreshold` (default 1.5× = 50% increase).
 *
 * @param {Array}  currentTransactions     — expenses in the current window
 * @param {Array}  historicalTransactions  — expenses in prior N windows (same window length)
 * @param {object} [opts]
 * @param {number} [opts.spikeThreshold=1.5]  — multiplier above baseline to trigger
 * @param {number} [opts.minBaseline=100]      — ignore categories with avg < this (noise filter)
 *
 * @returns {Array<SpikeAlert>}
 *
 * SpikeAlert shape:
 * {
 *   rule:        'spending_spike'
 *   severity:    'warning'
 *   category:    string
 *   current:     number   (total this period)
 *   baseline:    number   (average per prior period)
 *   multiplier:  number   (e.g. 1.8 = 80% above baseline)
 *   changePct:   number   (e.g. 80)
 *   message:     string
 * }
 *
 * Example:
 *   detectSpike(currentTxs, historicalTxs, { spikeThreshold: 1.5 })
 *   → [{
 *       rule:       'spending_spike',
 *       severity:   'warning',
 *       category:   'Food & Dining',
 *       current:    8200,
 *       baseline:   4800,
 *       multiplier: 1.71,
 *       changePct:  71,
 *       message:    'Food & Dining spending is up 71% vs your usual ₹4,800 average'
 *     }]
 */
function detectSpike(currentTransactions, historicalTransactions, { spikeThreshold = 1.5, minBaseline = 100 } = {}) {
  if (!currentTransactions.length || !historicalTransactions.length) return [];

  // Current period totals per category
  const current = {};
  for (const t of currentTransactions) {
    if (t.type !== 'expense') continue;
    const cat = (t.category || 'Uncategorized').trim();
    current[cat] = (current[cat] || 0) + Number(t.amount);
  }

  // Historical totals per category — then average across however many periods exist
  // Caller passes a flat array; we split into same-length windows using month buckets
  const historicalByPeriod = groupByMonth(historicalTransactions.filter((t) => t.type === 'expense'));
  const numPeriods = Object.keys(historicalByPeriod).length || 1;

  const historicalTotals = {};
  for (const [, txs] of Object.entries(historicalByPeriod)) {
    for (const t of txs) {
      const cat = (t.category || 'Uncategorized').trim();
      historicalTotals[cat] = (historicalTotals[cat] || 0) + Number(t.amount);
    }
  }

  // Convert to per-period averages
  const baseline = {};
  for (const [cat, total] of Object.entries(historicalTotals)) {
    baseline[cat] = total / numPeriods;
  }

  const alerts = [];

  for (const [category, currentTotal] of Object.entries(current)) {
    const avg = baseline[category] || 0;
    if (avg < minBaseline) continue; // not enough history / noise

    const multiplier = currentTotal / avg;
    if (multiplier < spikeThreshold) continue;

    const changePct = round((multiplier - 1) * 100);

    alerts.push({
      rule: 'spending_spike',
      severity: 'warning',
      category,
      current: round(currentTotal),
      baseline: round(avg),
      multiplier: round(multiplier, 2),
      changePct,
      message: `${category} spending is up ${changePct}% vs your usual ₹${fmt(avg)} average`,
    });
  }

  // Largest multiplier first
  alerts.sort((a, b) => b.multiplier - a.multiplier);
  return alerts;
}

// ─────────────────────────────────────────────────────────────────────────────
// runAllRules
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Convenience wrapper — runs both rule sets and returns a unified alert list.
 *
 * @param {Array}  budgets
 * @param {Array}  currentTransactions
 * @param {Array}  historicalTransactions
 * @param {object} [opts]                   — forwarded to detectSpike
 * @returns {Array<OverspendingAlert|SpikeAlert>}
 *
 * Example:
 *   const alerts = runAllRules(budgets, currentTxs, historicalTxs);
 *   // [
 *   //   { rule: 'budget_exceeded', severity: 'critical', ... },
 *   //   { rule: 'spending_spike',  severity: 'warning',  ... },
 *   // ]
 */
function runAllRules(budgets, currentTransactions, historicalTransactions, opts = {}) {
  return [
    ...detectOverspending(budgets, currentTransactions),
    ...detectSpike(currentTransactions, historicalTransactions, opts),
  ];
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

function round(n, decimals = 0) {
  const f = Math.pow(10, decimals);
  return Math.round(n * f) / f;
}

function fmt(n) {
  return Number(n).toLocaleString('en-IN');
}

function groupByMonth(transactions) {
  const map = {};
  for (const t of transactions) {
    const d = new Date(t.date);
    const key = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
    if (!map[key]) map[key] = [];
    map[key].push(t);
  }
  return map;
}

module.exports = {
  detectOverspending,
  detectSpike,
  runAllRules,
};
