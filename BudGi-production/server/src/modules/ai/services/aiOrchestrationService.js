// modules/ai/services/aiOrchestrationService.js
//
// Orchestration layer — fetches data, assembles the structured context object,
// calls callAI(), and shapes the final response.
//
// RULE: This is the only file that calls aiRepo and passes context to callAI.
//       aiService (Groq) must never be called with raw transaction arrays.

'use strict';

const aiRepo = require('../repositories/aiRepository');
const { callAI, chat: groqChat, MODEL_FAST } = require('./aiService');
const { estimateNextMonth } = require('../../analytics/services/predictionCore.service');
const { computeHealthScore } = require('./healthScoreService');
const { getTrends } = require('../../analytics/services/predictionService');
const {
  totalSpent,
  categoryBreakdown,
  topCategory,
  savingsRate,
  appleCardVelocity,
} = require('../../analytics/services/analyticsCore.service');
const { validationError, validateChatMessages } = require('../../../utils/validators');
const cache = require('../../../utils/cacheService');

// Tool execution imports
const budgetService = require('../../budgets/services/budgetService');
const txService = require('../../transactions/services/transactionService');
const goalService = require('../../goals/services/goalService');
const ChatMessage = require('../../../models/ChatMessage');
const UserPreference = require('../../../models/UserPreference');
// Still needed for aiRepo? No, repo handles it. But we use it for find etc.

// ─────────────────────────────────────────────────────────────────────────────
// buildContext — shared data-fetching + computation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fetch all data needed by callAI and compute the analytics / prediction blocks.
 * Returns the full { analytics, alerts, prediction } context object.
 *
 * @param {string} userId
 * @returns {Promise<AIContext>}
 */
async function buildContext(userId) {
  const [transactions, budgets, activeAlerts, goals] = await Promise.all([
    aiRepo.getRecentTransactions(userId, 90),
    aiRepo.getBudgets(userId),
    aiRepo.getActiveAlerts(userId, 5),
    aiRepo.getGoals(userId),
  ]);

  // Pure-math analytics (no AI)
  const analyticsBlock = {
    totalSpent: totalSpent(transactions),
    categoryBreakdown: categoryBreakdown(transactions, { limit: 5 }),
    topCategory: topCategory(transactions),
    savingsRate: savingsRate(transactions),
    velocity: appleCardVelocity(transactions, budgets),
  };

  // Pure-math prediction (no AI)
  const predictionBlock = estimateNextMonth(transactions, { lookbackMonths: 6 });

  return {
    analytics: analyticsBlock,
    alerts: activeAlerts,
    prediction: predictionBlock,
    goals,
    transactions, // kept for chat context
    budgets, // kept for chat context
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// getInsights
// GET /api/ai/insights
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @returns {{ insight, warning, suggestion, analytics, prediction }}
 */
async function getInsights(userId) {
  return cache.remember(
    `ai:insights:${userId}`,
    async () => {
      const ctx = await buildContext(userId);

      const aiResponse = await callAI({
        analytics: ctx.analytics,
        alerts: ctx.alerts,
        prediction: ctx.prediction,
        goals: ctx.goals,
        model: MODEL_FAST,
      });

      return {
        ...aiResponse, // insight, warning, suggestion
        analytics: ctx.analytics,
        prediction: {
          predictedTotal: ctx.prediction.predictedTotal,
          trendDirection: ctx.prediction.trendDirection,
          confidence: ctx.prediction.confidence,
        },
      };
    },
    cache.TTL.AI_INSIGHTS,
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// getSuggestions
// GET /api/ai/suggestions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Same callAI path, userMessage nudges toward actionable advice.
 * @returns {{ insight, warning, suggestion }}
 */
async function getSuggestions(userId) {
  return cache.remember(
    `ai:suggestions:${userId}`,
    async () => {
      const ctx = await buildContext(userId);

      return callAI({
        analytics: ctx.analytics,
        alerts: ctx.alerts,
        prediction: ctx.prediction,
        goals: ctx.goals,
        userMessage: 'Give me one specific action I can take to reduce spending this month.',
        model: MODEL_FAST,
      });
    },
    cache.TTL.AI_INSIGHTS,
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// analyze — call callAI with an explicit user question
// POST /api/ai/analyze
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param {string} userId
 * @param {string} userMessage  — free-text question from the client
 * @returns {{ insight, warning, suggestion }}
 */
async function analyze(userId, userMessage) {
  const ctx = await buildContext(userId);

  return callAI({
    analytics: ctx.analytics,
    alerts: ctx.alerts,
    prediction: ctx.prediction,
    goals: ctx.goals,
    userMessage: (userMessage || '').trim(),
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// chat — multi-turn conversational copilot
// POST /api/ai/chat
// ─────────────────────────────────────────────────────────────────────────────

async function chat(userId, messages) {
  const errors = validateChatMessages(messages);
  if (errors.length) throw validationError(errors);

  // Save the latest user message if it's the first time we see it
  const lastMsg = messages[messages.length - 1];
  if (lastMsg && lastMsg.role === 'user') {
    await ChatMessage.create({
      userId,
      role: 'user',
      content: lastMsg.content,
      time: lastMsg.time || new Date(),
    });
  }

  const [transactions, budgets, goals, preferences] = await Promise.all([
    aiRepo.getRecentTransactions(userId, 60),
    aiRepo.getBudgets(userId),
    aiRepo.getGoals(userId),
    UserPreference.find({ userId }).lean(),
  ]);

  let currentMessages = [...messages];
  const executedTools = [];
  let result = await groqChat(currentMessages, { transactions, budgets, goals, preferences });
  let loopCount = 0;
  const MAX_LOOPS = 4;

  while (result && typeof result === 'object' && result.tool_calls && loopCount < MAX_LOOPS) {
    const toolMessages = [...currentMessages];

    for (const toolCall of result.tool_calls) {
      const { name, arguments: argsString } = toolCall.function;
      let args = {};
      try {
        args = argsString ? JSON.parse(argsString) : {};
      } catch (e) {
        // Handle malformed JSON
      }
      executedTools.push(name);
      let output;

      try {
        switch (name) {
          case 'create_budget':
            await budgetService.createBudget(userId, args);
            output = { success: true, message: `Budget for ${args.category} set to ₹${args.limit}` };
            break;
          case 'update_budget': {
            const b = budgets.find((b) => b.category.toLowerCase() === args.category.toLowerCase());
            if (!b) throw new Error(`Budget for ${args.category} not found`);
            await budgetService.updateBudget(userId, b._id || b.id, { limit: args.limit });
            output = { success: true, message: `Budget for ${args.category} updated to ₹${args.limit}` };
            break;
          }
          case 'delete_budget': {
            const b = budgets.find((b) => b.category.toLowerCase() === args.category.toLowerCase());
            if (!b) throw new Error(`Budget for ${args.category} not found`);
            await budgetService.deleteBudget(userId, b._id || b.id);
            output = { success: true, message: `Budget for ${args.category} deleted` };
            break;
          }
          case 'create_goal':
            await goalService.createGoal(userId, args);
            output = {
              success: true,
              message: `Savings goal "${args.title}" created with target ₹${args.targetAmount}`,
            };
            break;
          case 'add_transaction':
            await txService.createTransaction(userId, args);
            output = {
              success: true,
              message: `${args.type === 'income' ? 'Income' : 'Expense'} of ₹${args.amount} recorded: ${args.description}`,
            };
            break;
          case 'query_database': {
            const Transaction = require('../../../models/Transaction');
            const query = { userId };
            if (args.category) query.category = new RegExp(args.category, 'i');
            if (args.merchant) query.description = new RegExp(args.merchant, 'i');
            if (args.type) query.type = args.type;
            if (args.startDate || args.endDate) {
              query.date = {};
              if (args.startDate) query.date.$gte = new Date(args.startDate);
              if (args.endDate) query.date.$lte = new Date(args.endDate);
            }
            const txs = await Transaction.find(query).sort({ date: -1 }).limit(25).lean();
            const total = txs.reduce((sum, t) => sum + t.amount, 0);
            output = {
              success: true,
              count: txs.length,
              hasMore: txs.length >= 25,
              totalSpentOrReceived: total,
              transactions: txs.map((t) => ({
                date: t.date.toISOString().split('T')[0],
                amount: t.amount,
                category: t.category,
                description: t.description,
              })),
            };
            break;
          }
          case 'bulk_categorize_transactions': {
            const Transaction = require('../../../models/Transaction');
            if (!args.keywords || !args.keywords.length) throw new Error('Keywords array is required');
            const regexStr = args.keywords.join('|');
            const result = await Transaction.updateMany(
              { userId, description: { $regex: regexStr, $options: 'i' } },
              { $set: { category: args.category } },
            );
            output = {
              success: true,
              message: `Updated ${result.modifiedCount} transactions to category "${args.category}"`,
            };
            break;
          }
          case 'save_user_preference': {
            await UserPreference.findOneAndUpdate(
              { userId, key: args.key },
              { value: args.value },
              { upsert: true, new: true },
            );
            output = { success: true, message: `Preference '${args.key}' saved successfully.` };
            break;
          }
          case 'read_user_preferences': {
            const prefs = await UserPreference.find({ userId }).lean();
            output = { success: true, preferences: prefs.map((p) => ({ key: p.key, value: p.value })) };
            break;
          }
          case 'aggregate_database': {
            const Transaction = require('../../../models/Transaction');
            const matchQuery = { userId, type: args.type || 'expense' };
            if (args.category) matchQuery.category = new RegExp(args.category, 'i');
            if (args.startDate || args.endDate) {
              matchQuery.date = {};
              if (args.startDate) matchQuery.date.$gte = new Date(args.startDate);
              if (args.endDate) matchQuery.date.$lte = new Date(args.endDate);
            }

            const groupStage = { _id: null };
            if (args.groupBy === 'category') groupStage._id = '$category';
            else if (args.groupBy === 'month') groupStage._id = { $dateToString: { format: '%Y-%m', date: '$date' } };
            else if (args.groupBy === 'week') groupStage._id = { $dateToString: { format: '%Y-%U', date: '$date' } };

            if (args.operation === 'sum') groupStage.result = { $sum: '$amount' };
            else if (args.operation === 'avg') groupStage.result = { $avg: '$amount' };
            else if (args.operation === 'count') groupStage.result = { $sum: 1 };

            const aggResult = await Transaction.aggregate([
              { $match: matchQuery },
              { $group: groupStage },
              { $sort: { _id: 1 } },
            ]);
            output = { success: true, aggregation: aggResult };
            break;
          }
          default:
            output = { error: 'Unknown tool' };
        }
      } catch (err) {
        output = { error: err.message };
      }

      toolMessages.push({ role: 'assistant', tool_calls: [toolCall], content: null });
      toolMessages.push({ role: 'tool', tool_call_id: toolCall.id, name, content: JSON.stringify(output) });
    }

    currentMessages = toolMessages;
    result = await groqChat(currentMessages, { transactions, budgets, goals, preferences });
    loopCount++;
  }

  let finalContent = result;
  if (finalContent && typeof finalContent === 'object' && finalContent.tool_calls) {
    finalContent = "I've completed those actions for you. Let me know if you need anything else!";
  }

  const replyString = typeof finalContent === 'string' ? finalContent : JSON.stringify(finalContent);

  // Extract structured action intents if present in LLM response
  const intents = [];
  const intentMatch = replyString.match(/\{[\s\S]*?"type"\s*:\s*"action_intent"[\s\S]*?\}/);
  if (intentMatch) {
    try {
      const parsed = JSON.parse(intentMatch[0]);
      intents.push(parsed);
    } catch (_err) {
      // Ignore malformed JSON
    }
  }

  // Save AI response
  await ChatMessage.create({
    userId,
    role: 'assistant',
    content: replyString,
    time: new Date(),
  });

  return { reply: replyString, executedTools, intents };
}

async function getChatHistory(userId, limit = 50) {
  return ChatMessage.find({ userId })
    .sort({ createdAt: 1 }) // Chronological for chat UI
    .limit(limit)
    .lean();
}

async function clearChatHistory(userId) {
  return ChatMessage.deleteMany({ userId });
}

// ─────────────────────────────────────────────────────────────────────────────
// predict
// GET /api/ai/predict
// ─────────────────────────────────────────────────────────────────────────────

async function predict(userId) {
  return cache.remember(
    `ai:predict:${userId}`,
    async () => {
      const ctx = await buildContext(userId);
      const forecast = ctx.prediction;

      // Enrich the prediction with an AI narrative via callAI
      const aiResponse = await callAI({
        analytics: ctx.analytics,
        alerts: ctx.alerts,
        prediction: forecast,
        goals: ctx.goals,
        userMessage: 'Explain the spending trend and what the forecast means for me.',
        model: MODEL_FAST,
      });

      return {
        ...forecast,
        aiNarrative: aiResponse.insight,
        warning: aiResponse.warning,
        suggestion: aiResponse.suggestion,
      };
    },
    cache.TTL.AI_INSIGHTS,
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// trends / health score — no callAI needed (pure data)
// ─────────────────────────────────────────────────────────────────────────────

async function trends(userId, monthsParam) {
  const months = Math.min(parseInt(monthsParam) || 6, 12);
  return getTrends(userId, months);
}

async function getHealthScore(userId) {
  return computeHealthScore(userId);
}

// ─────────────────────────────────────────────────────────────────────────────
// smartBudgetSuggestions — Analyze spending history and suggest budgets
// ─────────────────────────────────────────────────────────────────────────────

async function smartBudgetSuggestions(userId) {
  const Budget = require('../../../models/Budget');
  const Transaction = require('../../../models/Transaction');

  // Get last 3 months of transactions
  const threeMonthsAgo = new Date();
  threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3);

  const [transactions, existingBudgets] = await Promise.all([
    Transaction.find({
      userId,
      type: 'expense',
      date: { $gte: threeMonthsAgo },
    }).lean(),
    Budget.find({ userId }).lean(),
  ]);

  const existingCategories = new Set(existingBudgets.map((b) => b.category));

  // Aggregate spending per category per month
  const categoryMonthly = {};
  for (const tx of transactions) {
    const cat = tx.category || 'Other';
    const monthKey = `${new Date(tx.date).getFullYear()}-${new Date(tx.date).getMonth()}`;
    if (!categoryMonthly[cat]) categoryMonthly[cat] = {};
    categoryMonthly[cat][monthKey] = (categoryMonthly[cat][monthKey] || 0) + tx.amount;
  }

  // For each category, compute average monthly spend and suggest a budget
  const COLORS = ['#7C6AF5', '#4D9CF5', '#30D68C', '#F5A623', '#F5616A', '#A78BFA', '#34D399', '#60A5FA'];
  const suggestions = [];
  let colorIdx = 0;

  for (const [category, monthlyData] of Object.entries(categoryMonthly)) {
    const monthlyValues = Object.values(monthlyData);
    if (monthlyValues.length === 0) continue;

    const avgMonthly = monthlyValues.reduce((s, v) => s + v, 0) / monthlyValues.length;
    const maxMonthly = Math.max(...monthlyValues);

    // Only suggest if the user doesn't already have a budget for this category
    // and the spending is meaningful (> ₹500/month average)
    if (existingCategories.has(category) || avgMonthly < 500) continue;

    // Suggest budget as avg + 10% buffer, rounded to nearest 500
    const suggestedLimit = Math.ceil((avgMonthly * 1.1) / 500) * 500;

    suggestions.push({
      category,
      suggestedLimit,
      avgMonthlySpend: Math.round(avgMonthly),
      maxMonthlySpend: Math.round(maxMonthly),
      monthsAnalyzed: monthlyValues.length,
      color: COLORS[colorIdx % COLORS.length],
      alreadyExists: false,
    });
    colorIdx++;
  }

  // Sort by average spend (highest first)
  suggestions.sort((a, b) => b.avgMonthlySpend - a.avgMonthlySpend);

  return {
    suggestions: suggestions.slice(0, 8),
    existingBudgetCount: existingBudgets.length,
    totalCategories: Object.keys(categoryMonthly).length,
  };
}

module.exports = {
  getInsights,
  getSuggestions,
  analyze,
  chat,
  predict,
  trends,
  getHealthScore,
  getChatHistory,
  clearChatHistory,
  smartBudgetSuggestions,
};
