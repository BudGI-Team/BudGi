// modules/ai/repositories/aiRepository.js
// DB queries ONLY — fetches all data needed before callAI

'use strict';

const Transaction = require('../../../models/Transaction');
const Budget = require('../../../models/Budget');
const Alert = require('../../../models/Alert');
const Goal = require('../../../models/Goal');

async function getRecentTransactions(userId, days) {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - days);
  return Transaction.find({ userId, date: { $gte: cutoff } }).lean();
}

async function getBudgets(userId) {
  return Budget.find({ userId }).lean();
}

/**
 * Fetch most recent unread alerts — fed into callAI as the alerts block.
 * @param {string} userId
 * @param {number} limit
 */
async function getActiveAlerts(userId, limit = 5) {
  return Alert.find({ userId, isRead: false }).sort({ createdAt: -1 }).limit(limit).lean();
}

async function getGoals(userId) {
  return Goal.find({ userId, isCompleted: false }).lean();
}

module.exports = {
  getRecentTransactions,
  getBudgets,
  getActiveAlerts,
  getGoals,
};
