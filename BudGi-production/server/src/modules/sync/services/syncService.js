// modules/sync/services/syncService.js
//
// Offline sync engine for mobile apps.
// Handles bidirectional sync: pulls server changes + pushes local changes.
//
// Strategy: Last-write-wins with conflict detection.

'use strict';

const Transaction = require('../../../models/Transaction');
const Budget = require('../../../models/Budget');
const Goal = require('../../../models/Goal');
const Alert = require('../../../models/Alert');
const txService = require('../../transactions/services/transactionService');
const goalService = require('../../goals/services/goalService');
const userRepo = require('../../auth/repositories/userRepository');

/**
 * Full bidirectional sync.
 *
 * @param {string} userId
 * @param {object} payload
 * @param {string} payload.lastSyncAt          — ISO timestamp of last successful sync
 * @param {Array}  payload.pendingTransactions  — transactions created offline
 * @param {Array}  payload.pendingUpdates       — transactions modified offline { id, changes, updatedAt }
 * @param {Array}  payload.pendingDeletes       — transaction IDs deleted offline
 * @param {Array}  payload.pendingGoals         — goals created offline
 * @param {Array}  payload.pendingBudgets       — budgets created offline
 *
 * @returns {{
 *   newTransactions:     Array,   — transactions created on server since lastSync
 *   updatedTransactions: Array,   — transactions modified on server since lastSync
 *   deletedTransactionIds: Array, — transaction IDs deleted on server since lastSync
 *   budgets:             Array,   — current budget state
 *   goals:               Array,   — current goals state
 *   alerts:              Array,   — unread alerts
 *   importResult:        object,  — result of importing pending transactions
 *   conflicts:           Array,   — records modified on both sides
 *   syncTimestamp:        string   — new sync timestamp to store on client
 * }}
 */
async function fullSync(
  userId,
  {
    lastSyncAt = null,
    pendingTransactions = [],
    pendingUpdates = [],
    pendingDeletes = [],
    pendingGoals = [],
    pendingBudgets = [],
  } = {},
) {
  const syncStart = new Date();
  const since = lastSyncAt ? new Date(lastSyncAt) : new Date(0);

  // ── 1. Push pending offline transactions ────────────────────────────────────
  let importResult = { imported: 0, duplicates: 0, failed: 0, total: 0 };
  if (pendingTransactions.length > 0) {
    try {
      const capped = pendingTransactions.slice(0, 200);
      const result = await txService.createTransaction(userId, capped);
      importResult = {
        imported: result.imported || (result.transaction ? 1 : 0),
        duplicates: result.duplicates || 0,
        failed: result.failed || 0,
        total: capped.length,
      };
    } catch (err) {
      importResult = {
        imported: 0,
        duplicates: 0,
        failed: pendingTransactions.length,
        total: pendingTransactions.length,
        error: err.message,
      };
    }
  }

  // ── 2. Apply pending updates ────────────────────────────────────────────────
  const conflicts = [];
  for (const update of pendingUpdates.slice(0, 50)) {
    try {
      const serverTx = await Transaction.findOne({ _id: update.id, userId }).lean();
      if (!serverTx) continue;

      // Conflict detection: if server version was updated after the client's version
      const clientUpdateTime = new Date(update.updatedAt || 0);
      if (new Date(serverTx.updatedAt) > clientUpdateTime) {
        conflicts.push({
          id: update.id,
          type: 'transaction',
          resolution: 'server_wins', // client can override if needed
        });
        continue;
      }

      // No conflict — apply client update
      await txService.updateTransaction(userId, update.id, update.changes);
    } catch (err) {
      conflicts.push({
        id: update.id,
        error: err.message,
        resolution: 'failed',
      });
    }
  }

  // ── 3. Apply pending deletes ────────────────────────────────────────────────
  for (const deleteId of pendingDeletes.slice(0, 50)) {
    try {
      await txService.deleteTransaction(userId, deleteId);
    } catch {
      /* already deleted or not found — ignore */
    }
  }

  // ── 4. Pull server changes ──────────────────────────────────────────────────
  // New/updated transactions on server since last sync
  const serverChanges = await Transaction.find({
    userId,
    updatedAt: { $gt: since },
  })
    .lean()
    .limit(500)
    .sort({ updatedAt: -1 });

  // Separate into new vs updated based on createdAt
  const newTransactions = serverChanges.filter((t) => new Date(t.createdAt) > since);
  const updatedTransactions = serverChanges.filter(
    (t) => new Date(t.createdAt) <= since && new Date(t.updatedAt) > since,
  );

  // Deleted transactions — we track via a soft approach: if client has IDs that
  // don't exist on server anymore, those are deletes. For now, we send empty
  // array since we do hard deletes.
  const deletedTransactionIds = [];

  // Current alerts
  const alerts = await Alert.find({ userId, isRead: false }).sort({ createdAt: -1 }).limit(50).lean();

  // ── 5. Push pending offline goals ───────────────────────────────────────────
  for (const goalData of pendingGoals.slice(0, 50)) {
    try {
      await goalService.createGoal(userId, goalData);
    } catch {
      /* skip duplicates / invalid */
    }
  }

  // ── 7. Push pending offline budgets ─────────────────────────────────────────
  for (const budgetData of pendingBudgets.slice(0, 50)) {
    try {
      const existing = await Budget.findOne({ userId, category: budgetData.category });
      if (existing) {
        // Update existing budget
        existing.limit = budgetData.limit || existing.limit;
        existing.period = budgetData.period || existing.period;
        existing.color = budgetData.color || existing.color;
        await existing.save();
      } else {
        await Budget.create({ ...budgetData, userId });
      }
    } catch {
      /* skip */
    }
  }

  // ── 8. Update sync timestamp ────────────────────────────────────────────────

  await userRepo.updateLastSync(userId);

  // Re-fetch goals after pushes (to include newly created ones)
  const [finalGoals, finalBudgets] = await Promise.all([
    Goal.find({ userId }).sort({ createdAt: -1 }).lean(),
    Budget.find({ userId }).lean(),
  ]);

  const finalEnrichedGoals = finalGoals.map((g) => ({
    ...g,
    progress: g.targetAmount > 0 ? Math.min(100, Math.round((g.currentAmount / g.targetAmount) * 100)) : 100,
  }));

  return {
    newTransactions,
    updatedTransactions,
    deletedTransactionIds,
    budgets: finalBudgets,
    goals: finalEnrichedGoals,
    alerts,
    importResult,
    conflicts,
    syncTimestamp: syncStart.toISOString(),
  };
}

/**
 * Lightweight delta sync — just pull changes since lastSyncAt.
 * No push. Used for periodic background refresh.
 */
async function deltaPull(userId, lastSyncAt) {
  const since = lastSyncAt ? new Date(lastSyncAt) : new Date(0);
  const now = new Date();

  const [transactions, budgets, goals, alerts] = await Promise.all([
    Transaction.find({ userId, updatedAt: { $gt: since } })
      .lean()
      .limit(500)
      .sort({ updatedAt: -1 }),
    Budget.find({ userId }).lean(),
    Goal.find({ userId }).sort({ createdAt: -1 }).lean(),
    Alert.find({ userId, isRead: false }).sort({ createdAt: -1 }).limit(50).lean(),
  ]);

  // Enrich goals with progress
  const enrichedGoals = goals.map((g) => ({
    ...g,
    progress: g.targetAmount > 0 ? Math.min(100, Math.round((g.currentAmount / g.targetAmount) * 100)) : 100,
  }));

  return {
    transactions,
    deletedTransactionIds: [],
    budgets,
    goals: enrichedGoals,
    alerts,
    syncTimestamp: now.toISOString(),
  };
}

module.exports = { fullSync, deltaPull };
