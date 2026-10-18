// modules/analytics/services/analyticsCore.service.js
//
// Pure calculation engine — no AI, no external calls, no side effects.
// Accepts pre-fetched transaction arrays so every function is easily unit-tested.
//
// Exports:
//   totalSpent(transactions)
//   categoryBreakdown(transactions)
//   topCategory(transactions)
//   spendingByPeriod(transactions, period)
//   savingsRate(transactions)

'use strict';

const isTransfer = (t) => (t.category || '').toLowerCase() === 'transfer';
const isExpense = (t) => (t.type === 'expense' || t.type === 'debit') && !isTransfer(t);
const isIncome = (t) => (t.type === 'income' || t.type === 'credit') && !isTransfer(t);

// ─────────────────────────────────────────────────────────────────────────────
// totalSpent
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sum all expense transactions.
 *
 * @param   {Array}  transactions  — flat array of transaction objects
 * @returns {{ total: number, count: number, currency: string }}
 *
 * Example:
 *   totalSpent([{ type:'expense', amount:500 }, { type:'income', amount:2000 }])
 *   → { total: 500, count: 1 }
 */
function totalSpent(transactions) {
  const expenses = transactions.filter(isExpense);
  const total = expenses.reduce((sum, t) => sum + Number(t.amount), 0);
  return {
    total: round(total),
    count: expenses.length,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// categoryBreakdown
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Aggregate expense amounts per category with percentage share.
 * Result is sorted highest-spend first.
 *
 * @param   {Array}   transactions
 * @param   {object}  [opts]
 * @param   {number}  [opts.limit=10]  — max categories to return (0 = all)
 * @returns {Array<{ category, total, count, percentage }>}
 *
 * Example:
 *   categoryBreakdown(transactions, { limit: 5 })
 *   → [
 *       { category: 'Food & Dining', total: 8200, count: 9, percentage: 24 },
 *       { category: 'Transport',     total: 4100, count: 6, percentage: 12 },
 *       ...
 *     ]
 */
function categoryBreakdown(transactions, { limit = 10 } = {}) {
  const expenses = transactions.filter(isExpense);
  if (!expenses.length) return [];

  const map = {};
  for (const t of expenses) {
    const cat = (t.category || 'Uncategorized').trim();
    if (!map[cat]) map[cat] = { total: 0, count: 0 };
    map[cat].total += Number(t.amount);
    map[cat].count += 1;
  }

  const grandTotal = Object.values(map).reduce((s, v) => s + v.total, 0);

  let rows = Object.entries(map).map(([category, { total, count }]) => ({
    category,
    total: round(total),
    count,
    percentage: grandTotal > 0 ? round((total / grandTotal) * 100, 1) : 0,
  }));

  rows.sort((a, b) => b.total - a.total);

  if (limit > 0) rows = rows.slice(0, limit);

  return rows;
}

// ─────────────────────────────────────────────────────────────────────────────
// topCategory
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Return the single highest-spend expense category.
 *
 * @param   {Array}  transactions
 * @returns {{ category: string, total: number, count: number, percentage: number } | null}
 *
 * Example:
 *   topCategory(transactions)
 *   → { category: 'Food & Dining', total: 8200, count: 9, percentage: 24 }
 */
function topCategory(transactions) {
  const breakdown = categoryBreakdown(transactions, { limit: 1 });
  return breakdown[0] ?? null;
}

// ─────────────────────────────────────────────────────────────────────────────
// spendingByPeriod
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bucket expense totals by time period (day | week | month).
 * Useful for bar/line charts on mobile dashboards.
 *
 * @param   {Array}   transactions
 * @param   {'day'|'week'|'month'} [period='month']
 * @returns {Array<{ label: string, total: number, count: number }>}
 *
 * Example (period='month'):
 *   → [
 *       { label: '2025-02', total: 28000, count: 14 },
 *       { label: '2025-03', total: 31500, count: 18 },
 *       { label: '2025-04', total: 34500, count: 23 },
 *     ]
 */
function spendingByPeriod(transactions, period = 'month') {
  const expenses = transactions.filter(isExpense);
  const map = {};

  for (const t of expenses) {
    const label = periodLabel(new Date(t.date), period);
    if (!map[label]) map[label] = { total: 0, count: 0 };
    map[label].total += Number(t.amount);
    map[label].count += 1;
  }

  return Object.entries(map)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([label, { total, count }]) => ({ label, total: round(total), count }));
}

// ─────────────────────────────────────────────────────────────────────────────
// savingsRate
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Calculate savings rate from a mixed income + expense array.
 *
 * @param   {Array}  transactions
 * @returns {{ income, expenses, savings, rate: number|null }}
 *          rate = null when there is no income (avoids div-by-zero)
 *
 * Example:
 *   savingsRate(transactions)
 *   → { income: 80000, expenses: 34500, savings: 45500, rate: 57 }
 */
function savingsRate(transactions) {
  const income = transactions.filter(isIncome).reduce((s, t) => s + Number(t.amount), 0);
  const expenses = transactions.filter(isExpense).reduce((s, t) => s + Number(t.amount), 0);
  const savings = income - expenses;

  return {
    income: round(income),
    expenses: round(expenses),
    savings: round(savings),
    rate: income > 0 ? round((savings / income) * 100, 1) : null,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

function round(n, decimals = 0) {
  const factor = Math.pow(10, decimals);
  return Math.round(n * factor) / factor;
}

function periodLabel(date, period) {
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  if (period === 'month') return `${y}-${m}`;
  if (period === 'day') {
    const d = String(date.getUTCDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
  if (period === 'week') {
    // ISO week: Monday-based
    const d = new Date(Date.UTC(y, date.getUTCMonth(), date.getUTCDate()));
    const day = d.getUTCDay() || 7;
    d.setUTCDate(d.getUTCDate() + 4 - day);
    const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
    const week = Math.ceil(((d - yearStart) / 86400000 + 1) / 7);
    return `${d.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
  }
  return `${y}-${m}`;
}

// ─────────────────────────────────────────────────────────────────────────────
// appleCardVelocity (Burn Rate & Safe to Spend)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Calculate Apple-Card-style daily velocity and safe-to-spend.
 * @param {Array} transactions
 * @param {Array} budgets
 */
function appleCardVelocity(transactions, budgets) {
  const expenses = transactions.filter(isExpense);
  const totalSpent = expenses.reduce((s, t) => s + Number(t.amount), 0);

  const totalBudget = budgets.reduce((s, b) => s + Number(b.limit), 0);

  const now = new Date();
  const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  const daysPassed = Math.max(1, now.getDate());
  const daysLeft = Math.max(1, daysInMonth - now.getDate());

  const dailyBurnRate = totalSpent / daysPassed;
  const safeToSpendDaily = Math.max(0, (totalBudget - totalSpent) / daysLeft);

  return {
    totalSpent: round(totalSpent),
    totalBudget: round(totalBudget),
    dailyBurnRate: round(dailyBurnRate),
    safeToSpendDaily: round(safeToSpendDaily),
    isSpendingFast: dailyBurnRate > safeToSpendDaily && safeToSpendDaily > 0,
  };
}

module.exports = {
  totalSpent,
  categoryBreakdown,
  topCategory,
  spendingByPeriod,
  savingsRate,
  appleCardVelocity,
};
