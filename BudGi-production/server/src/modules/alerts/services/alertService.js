// src/services/alertService.js
/**
 * Alert Service — Rule-based alert engine
 *
 * Rules:
 *  1. Budget exceeded        → critical alert per category
 *  2. Budget at threshold    → warning alert per category
 *  3. Unusual spending       → anomaly detection (2× historical avg)
 *  4. Weekly summary         → digest of the past 7 days
 *
 * Alerts are persisted to MongoDB (Alert model) and deduplicated
 * within the same calendar month to avoid spam.
 */

'use strict';

const Alert = require('../../../models/Alert');
const Budget = require('../../../models/Budget');
const Transaction = require('../../../models/Transaction');
const pushService = require('../../../utils/pushService');
const socketService = require('../../../utils/socketService');
const aiService = require('../../ai/services/aiService');
const analyticsService = require('../../analytics/services/analyticsService');

// ── Rule Engine ──────────────────────────────────────────────────────────────

/**
 * Run all alert rules for a given userId and persist new alerts.
 * Call this after every new transaction is created.
 *
 * @param {string} userId
 * @returns {Promise<Alert[]>} newly created alerts
 */
async function runAlertRules(userId) {
  const [budgets, transactions] = await Promise.all([
    Budget.find({ userId }).lean(),
    Transaction.find({
      userId,
      date: { $gte: startOfCurrentMonth() },
    }).lean(),
  ]);

  const newAlerts = [];

  // Rule 1 & 2: Budget threshold checks
  const budgetAlerts = checkBudgetRules(budgets, transactions);
  for (const a of budgetAlerts) {
    const isDupe = await isDuplicateThisMonth(userId, a.type, a.category);
    if (!isDupe) {
      const alert = await Alert.create({ userId, ...a });
      newAlerts.push(alert);
      // Push to mobile devices + WebSocket
      pushService.sendBudgetAlert(userId, a.metadata).catch(() => {});
      if (a.type === 'budget_exceeded') {
        socketService.push(userId, socketService.EVENTS.BUDGET_EXCEEDED, alert);
      } else {
        socketService.push(userId, socketService.EVENTS.ALERT_NEW, alert);
      }
    }
  }

  // Rule 3: Anomaly detection — flag if latest tx is > 2× historical avg
  const anomalyAlerts = await checkAnomalyRules(userId, transactions);
  for (const a of anomalyAlerts) {
    const alert = await Alert.create({ userId, ...a });
    newAlerts.push(alert);

    // Push to mobile devices + WebSocket
    pushService
      .sendToUser(userId, {
        title: '🔍 Unusual Spending Detected',
        body: a.message,
        data: { type: 'anomaly', deepLink: 'budgi://alerts' },
      })
      .catch(() => {});
    socketService.push(userId, socketService.EVENTS.ALERT_NEW, alert);
  }

  return newAlerts;
}

/**
 * Generate weekly summary alert for a user.
 * Called by cron job every Monday.
 */
async function generateWeeklySummary(userId) {
  const sevenDaysAgo = new Date();
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);

  const transactions = await Transaction.find({
    userId,
    date: { $gte: sevenDaysAgo },
    type: 'expense',
  }).lean();

  if (!transactions.length) return null;

  const total = transactions.reduce((s, t) => s + t.amount, 0);
  const topCat = getTopCategory(transactions);
  const txCount = transactions.length;

  const message = [
    `📊 Weekly summary: ₹${total.toFixed(0)} spent across ${txCount} transactions.`,
    topCat ? `Top category: ${topCat.category} (₹${topCat.amount.toFixed(0)}).` : '',
    total > 10000
      ? 'Tip: Review your top expenses to find savings opportunities.'
      : 'Great job keeping expenses in check!',
  ]
    .filter(Boolean)
    .join(' ');

  return Alert.create({
    userId,
    type: 'weekly_summary',
    message,
    severity: 'info',
    metadata: { total, txCount, topCategory: topCat?.category },
  });
}

/**
 * Generate a personalized Nightly AI Copilot Briefing.
 * Uses Groq to analyze the user's recent spending and queue a personalized alert.
 */
async function generateNightlyAIBriefing(userId) {
  try {
    const User = require('../../../models/User');
    const user = await User.findById(userId).lean();
    if (!user) return null;

    const report = await analyticsService.getMonthlyReport(userId);
    if (!report || report.expenseCount === 0) return null;

    const analytics = {
      totalSpent: report.totalExpense,
      topCategory: report.topExpenses?.[0]
        ? {
            category: report.topExpenses[0].category,
            total: report.topExpenses[0].amount,
            percentage: Math.round((report.topExpenses[0].amount / report.totalExpense) * 100),
          }
        : null,
      categoryBreakdown: report.categoryBreakdown,
      savingsRate: { income: report.totalIncome, rate: report.savingsRate },
    };

    const aiResponse = await aiService.callAI({
      analytics,
      userMessage: 'Give me a proactive daily financial tip or warning based strictly on my spending data.',
    });

    if (!aiResponse || (!aiResponse.suggestion && !aiResponse.warning)) return null;

    const isWarning = !!aiResponse.warning;
    const message = `🧠 Copilot Insight: ${aiResponse.insight} ${isWarning ? '⚠️ ' + aiResponse.warning : ''} 💡 Tip: ${aiResponse.suggestion}`;

    const alert = await Alert.create({
      userId,
      type: 'ai_nightly_briefing',
      message,
      severity: isWarning ? 'warning' : 'info',
      metadata: { insight: aiResponse.insight, warning: aiResponse.warning, suggestion: aiResponse.suggestion },
    });

    // Push to mobile devices + WebSocket
    pushService
      .sendToUser(userId, {
        title: isWarning ? '⚠️ BudGi Alert' : '🧠 Your Nightly Copilot Briefing',
        body: aiResponse.suggestion || aiResponse.warning,
        data: { type: 'ai_briefing', deepLink: 'budgi://alerts' },
      })
      .catch(() => {});
    socketService.push(userId, socketService.EVENTS.ALERT_NEW, alert);

    // Send email via emailService
    const emailService = require('../../../utils/emailService');
    const emailHtml = `
      <div style="font-family: sans-serif; padding: 20px;">
        <h2 style="color: #7C6AF5;">BudGi Copilot Insight</h2>
        <p>Hey ${user.name || 'there'},</p>
        <p><strong>Insight:</strong> ${aiResponse.insight}</p>
        ${isWarning ? `<p style="color: #ef4444;"><strong>Warning:</strong> ${aiResponse.warning}</p>` : ''}
        <p style="color: #10b981;"><strong>Tip:</strong> ${aiResponse.suggestion}</p>
        <p style="margin-top: 30px; font-size: 12px; color: #666;">This is an automated insight generated based on your recent spending patterns.</p>
      </div>
    `;
    emailService
      .sendEmail({
        to: user.email,
        subject: isWarning ? '⚠️ BudGi Budget Warning' : '🧠 Your Nightly Copilot Briefing',
        html: emailHtml,
      })
      .catch(() => {});

    return alert;
  } catch (err) {
    console.error(`[AlertService] Failed to generate AI briefing for ${userId}:`, err.message);
    return null;
  }
}

/**
 * Mark alerts as read.
 */
async function markAlertsRead(userId, alertIds) {
  return Alert.updateMany({ userId, _id: { $in: alertIds } }, { $set: { isRead: true } });
}

/**
 * Clear all alerts for a user.
 */
async function clearAllAlerts(userId) {
  return Alert.deleteMany({ userId });
}

/**
 * Get unread alerts for a user.
 */
async function getAlerts(userId, { limit = 20, unreadOnly = false } = {}) {
  const filter = { userId };
  if (unreadOnly) filter.isRead = false;
  return Alert.find(filter).sort({ createdAt: -1 }).limit(limit).lean();
}

/**
 * Count unread alerts.
 */
async function getUnreadCount(userId) {
  return Alert.countDocuments({ userId, isRead: false });
}

// ── Rule implementations ─────────────────────────────────────────────────────

function checkBudgetRules(budgets, transactions) {
  const alerts = [];
  const spendByCategory = {};

  // Aggregate current-month expenses
  for (const t of transactions) {
    if (t.type !== 'expense') continue;
    const cat = t.category || 'Uncategorized';
    spendByCategory[cat] = (spendByCategory[cat] || 0) + t.amount;
  }

  for (const budget of budgets) {
    if (!budget.limit || budget.limit <= 0) continue;
    const spent = spendByCategory[budget.category] || 0;
    const ratio = spent / budget.limit;
    const threshold = budget.alertAt ?? 0.9;

    if (ratio > 1) {
      alerts.push({
        type: 'budget_exceeded',
        severity: 'critical',
        category: budget.category,
        message: `⚠ ${budget.category} is over budget! Spent ₹${spent.toFixed(0)} of ₹${budget.limit} limit.`,
        metadata: { spent, limit: budget.limit, ratio },
      });
    } else if (ratio >= threshold) {
      alerts.push({
        type: 'budget_warning',
        severity: 'warning',
        category: budget.category,
        message: `${budget.category} is at ${Math.round(ratio * 100)}% of its ₹${budget.limit} limit. ₹${(budget.limit - spent).toFixed(0)} remaining.`,
        metadata: { spent, limit: budget.limit, ratio },
      });
    }
  }

  return alerts;
}

async function checkAnomalyRules(userId, currentMonthTxs) {
  const alerts = [];

  // Compare latest transaction against 3-month historical average per category
  if (!currentMonthTxs.length) return alerts;

  // Get the most recently created expense transaction
  const latest = [...currentMonthTxs]
    .filter((t) => t.type === 'expense')
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))[0];

  if (!latest) return alerts;

  // Historical average for this category (last 3 months, excluding current month)
  const threeMonthsAgo = new Date();
  threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3);

  const historical = await Transaction.find({
    userId,
    type: 'expense',
    category: latest.category,
    date: { $gte: threeMonthsAgo, $lt: startOfCurrentMonth() },
  }).lean();

  if (historical.length < 3) return alerts; // Not enough history

  const avgAmount = historical.reduce((s, t) => s + t.amount, 0) / historical.length;
  const ANOMALY_MULTIPLIER = 2.5;

  if (latest.amount > avgAmount * ANOMALY_MULTIPLIER) {
    alerts.push({
      type: 'anomaly',
      severity: 'warning',
      category: latest.category,
      message: `Unusual spend detected: ₹${latest.amount.toFixed(0)} in ${latest.category} is ${(latest.amount / avgAmount).toFixed(1)}× your average.`,
      metadata: {
        transactionId: latest._id,
        amount: latest.amount,
        avgAmount,
        category: latest.category,
      },
    });
  }

  return alerts;
}

// ── Deduplication ────────────────────────────────────────────────────────────

/**
 * Prevent spamming the same alert type for the same category within a month.
 */
async function isDuplicateThisMonth(userId, type, category) {
  const monthStart = startOfCurrentMonth();
  const existing = await Alert.findOne({
    userId,
    type,
    category: category || null,
    createdAt: { $gte: monthStart },
  });
  return !!existing;
}

// ── Utilities ────────────────────────────────────────────────────────────────

function startOfCurrentMonth() {
  const now = new Date();
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
}

function getTopCategory(transactions) {
  const byCategory = {};
  for (const t of transactions) {
    byCategory[t.category] = (byCategory[t.category] || 0) + t.amount;
  }
  const sorted = Object.entries(byCategory).sort((a, b) => b[1] - a[1]);
  if (!sorted.length) return null;
  return { category: sorted[0][0], amount: sorted[0][1] };
}

module.exports = {
  runAlertRules,
  generateWeeklySummary,
  generateNightlyAIBriefing,
  markAlertsRead,
  clearAllAlerts,
  getAlerts,
  getUnreadCount,
};
