// src/services/healthScoreService.js
/**
 * Financial Health Score — 0-100 composite score
 *
 * Pillars (weighted):
 *  1. Savings Rate          (30 pts) — income vs expenses
 *  2. Budget Adherence      (25 pts) — % of budgets not exceeded
 *  3. Expense Diversity     (15 pts) — not over-reliant on one category
 *  4. Spending Trend        (15 pts) — MoM change
 *  5. Recurring Control     (15 pts) — recurring as % of total spend
 *
 * Score bands:
 *  80-100 → Excellent
 *  60-79  → Good
 *  40-59  → Fair
 *  0-39   → Needs Work
 */

'use strict';

const Transaction = require('../../../models/Transaction');
const Budget = require('../../../models/Budget');
const User = require('../../../models/User');

/**
 * Compute and persist financial health score for a user.
 * @param {string} userId
 * @returns {{ score, band, breakdown, tips }}
 */
async function computeHealthScore(userId) {
  const now = new Date();
  const monthStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  const prevMonthStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1));

  const [user, currentTxs, prevMonthTxs, budgets] = await Promise.all([
    User.findById(userId).lean(),
    Transaction.find({ userId, date: { $gte: monthStart } }).lean(),
    Transaction.find({ userId, date: { $gte: prevMonthStart, $lt: monthStart } }).lean(),
    Budget.find({ userId }).lean(),
  ]);

  const currentExpenses = currentTxs.filter((t) => t.type === 'expense');
  const currentIncome = currentTxs.filter((t) => t.type === 'income');

  const totalExpense = currentExpenses.reduce((s, t) => s + t.amount, 0);
  const totalIncome = currentIncome.reduce((s, t) => s + t.amount, 0) || user?.monthlyIncome || 0;

  const spendByCat = {};
  currentExpenses.forEach((t) => {
    spendByCat[t.category] = (spendByCat[t.category] || 0) + t.amount;
  });

  // Pillar 1 — Savings Rate (30 pts)
  const savingsScore = scoreSavingsRate(totalIncome, totalExpense);

  // Pillar 2 — Budget Adherence (25 pts)
  const budgetScore = scoreBudgetAdherence(budgets, spendByCat);

  // Pillar 3 — Expense Diversity (15 pts)
  const diversityScore = scoreExpenseDiversity(currentExpenses);

  // Pillar 4 — Spending Trend (15 pts)
  const prevExpense = prevMonthTxs.filter((t) => t.type === 'expense').reduce((s, t) => s + t.amount, 0);
  const trendScore = scoreSpendingTrend(prevExpense, totalExpense);

  // Pillar 5 — Recurring Control (15 pts)
  const recurringScore = scoreRecurringControl(currentExpenses);

  const total = Math.round(savingsScore + budgetScore + diversityScore + trendScore + recurringScore);
  const score = Math.min(100, Math.max(0, total));
  const band = scoreBand(score);

  const breakdown = {
    savingsRate: { score: Math.round(savingsScore), max: 30 },
    budgetAdherence: { score: Math.round(budgetScore), max: 25 },
    diversity: { score: Math.round(diversityScore), max: 15 },
    trend: { score: Math.round(trendScore), max: 15 },
    recurringControl: { score: Math.round(recurringScore), max: 15 },
  };

  const tips = generateTips(breakdown, { totalIncome, totalExpense, budgets });

  // Persist to user document
  await User.updateOne({ _id: userId }, { $set: { healthScore: score, healthScoreAt: new Date() } });

  const adherentCount = budgets.filter((b) => (spendByCat[b.category] || 0) <= b.limit).length;

  return {
    score,
    band,
    breakdown,
    tips,
    factors: {
      savingsRate: totalIncome > 0 ? Math.round(((totalIncome - totalExpense) / totalIncome) * 100) : 0,
      budgetsOnTrack: budgets.length > 0 ? `${adherentCount}/${budgets.length}` : '0/0',
    },
  };
}

// ── Scoring functions ────────────────────────────────────────────────────────

function scoreSavingsRate(income, expense) {
  if (!income) return 10; // No income data — neutral score
  const rate = (income - expense) / income;
  if (rate >= 0.3) return 30;
  if (rate >= 0.2) return 24;
  if (rate >= 0.1) return 18;
  if (rate >= 0) return 12;
  return 0; // Spending more than earning
}

function scoreBudgetAdherence(budgets, spendByCat) {
  if (!budgets.length) return 12; // No budgets set — partial credit

  const adherent = budgets.filter((b) => (spendByCat[b.category] || 0) <= b.limit).length;
  return Math.round((adherent / budgets.length) * 25);
}

function scoreExpenseDiversity(expenses) {
  if (!expenses.length) return 10;

  const total = expenses.reduce((s, t) => s + t.amount, 0);
  const byCat = {};
  expenses.forEach((t) => {
    byCat[t.category] = (byCat[t.category] || 0) + t.amount;
  });

  const topCatPct = Math.max(...Object.values(byCat)) / total;

  // Low concentration = good diversity
  if (topCatPct < 0.4) return 15;
  if (topCatPct < 0.6) return 10;
  if (topCatPct < 0.8) return 5;
  return 2;
}

function scoreSpendingTrend(prevTotal, currTotal) {
  if (!prevTotal) return 10; // No history
  const change = (currTotal - prevTotal) / prevTotal;
  if (change <= -0.1) return 15; // Spending dropped ≥10%
  if (change <= 0) return 12;
  if (change <= 0.1) return 8;
  if (change <= 0.2) return 4;
  return 1; // Spending jumped >20%
}

function scoreRecurringControl(expenses) {
  if (!expenses.length) return 10;
  const total = expenses.reduce((s, t) => s + t.amount, 0);
  const recurring = expenses.filter((t) => t.isRecurring).reduce((s, t) => s + t.amount, 0);
  const pct = total > 0 ? recurring / total : 0;

  if (pct <= 0.3) return 15;
  if (pct <= 0.5) return 10;
  if (pct <= 0.7) return 5;
  return 2;
}

// ── Band & Tips ──────────────────────────────────────────────────────────────

function scoreBand(score) {
  if (score >= 80) return { label: 'Excellent', color: '#22c55e', emoji: '🏆' };
  if (score >= 60) return { label: 'Good', color: '#84cc16', emoji: '👍' };
  if (score >= 40) return { label: 'Fair', color: '#f59e0b', emoji: '⚠️' };
  return { label: 'Needs Work', color: '#ef4444', emoji: '🔴' };
}

function generateTips(breakdown, { totalIncome, totalExpense, budgets }) {
  const tips = [];

  if (breakdown.savingsRate.score < 20) {
    const deficit = totalExpense - totalIncome;
    tips.push(
      deficit > 0
        ? `You're overspending by ₹${deficit.toFixed(0)} this month. Identify and cut your top 2 expense categories.`
        : `Your savings rate is below 20%. Try the 50/30/20 rule: 50% needs, 30% wants, 20% savings.`,
    );
  }

  if (breakdown.budgetAdherence.score < 15) {
    tips.push('Several budgets are being exceeded. Tighten limits or reduce spending in those categories.');
  }

  if (breakdown.trend.score < 8) {
    tips.push('Your spending increased this month. Review new or increased expenses vs last month.');
  }

  if (breakdown.recurringControl.score < 8) {
    tips.push('Over half your spending is recurring. Audit subscriptions — cancel unused ones.');
  }

  if (!budgets.length) {
    tips.push('Set monthly budgets per category to unlock Budget Adherence scoring and alerts.');
  }

  if (!tips.length) {
    tips.push('Great financial health! Consider increasing your investment/savings allocation.');
  }

  return tips.slice(0, 3);
}

module.exports = { computeHealthScore };
