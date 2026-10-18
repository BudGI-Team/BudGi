// modules/analytics/repositories/analyticsRepository.js
// DB queries ONLY — all reads needed by the intelligence layer

'use strict';

const Transaction = require('../../../models/Transaction');
const Budget = require('../../../models/Budget');

async function findByMonthRange(userId, start, end) {
  return Transaction.find({ userId, date: { $gte: start, $lt: end } }).lean();
}

async function findExpensesInRange(userId, start, end) {
  return Transaction.find({
    userId,
    type: 'expense',
    date: { $gte: start, $lt: end },
  }).lean();
}

// N months of all transactions (expenses + income) for forecasting
async function findRecentMonths(userId, months) {
  const cutoff = new Date();
  cutoff.setUTCDate(1);
  cutoff.setUTCHours(0, 0, 0, 0);
  cutoff.setUTCMonth(cutoff.getUTCMonth() - months);
  return Transaction.find({ userId, date: { $gte: cutoff } }).lean();
}

// Current-month transactions from the 1st of this UTC month
async function findCurrentMonth(userId) {
  const now = new Date();
  const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  return Transaction.find({ userId, date: { $gte: start } }).lean();
}

// N months of history BEFORE the current month (baseline for spike detection)
async function findHistoricalMonths(userId, months) {
  const now = new Date();
  const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  const start = new Date(end);
  start.setUTCMonth(start.getUTCMonth() - months);
  return Transaction.find({ userId, date: { $gte: start, $lt: end } }).lean();
}

async function findBudgetsByUser(userId) {
  return Budget.find({ userId }).lean();
}

module.exports = {
  findByMonthRange,
  findExpensesInRange,
  findRecentMonths,
  findCurrentMonth,
  findHistoricalMonths,
  findBudgetsByUser,
};
