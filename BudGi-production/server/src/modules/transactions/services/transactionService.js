// modules/transactions/services/transactionService.js
// ALL business logic — validation, dedup, categorization, alert triggers

'use strict';

const Transaction = require('../../../models/Transaction');
const txRepo = require('../repositories/transactionRepository');
const { categorize, categorizeBatch } = require('./categorizationService');
const merchantService = require('./merchantService');
const { runAlertRules } = require('../../alerts/services/alertService');
const { runIntelligenceAlerts } = require('../../analytics/services/intelligence.service');
const socketService = require('../../../utils/socketService');
const { parseCSV } = require('../../../utils/csvParser');
const { validationError, validateTransactionBody, validateBulkArray } = require('../../../utils/validators');
const cache = require('../../../utils/cacheService');

// ── Build Mongo filter from query params ──────────────────────────────────────
function buildFilter(userId, { type, category, startDate, endDate, search }) {
  const filter = { userId };
  if (type) filter.type = type;
  if (category) filter.category = category;
  if (search) filter.description = { $regex: search, $options: 'i' };
  if (startDate || endDate) {
    filter.date = {};
    if (startDate) filter.date.$gte = new Date(startDate);
    if (endDate) filter.date.$lte = new Date(endDate);
  }
  return filter;
}

// ── Allowed sort fields (whitelist — prevents field injection) ─────────────────
const ALLOWED_SORT_FIELDS = new Set(['date', 'amount', 'createdAt', 'merchant', 'category', 'type']);

// ── List with pagination ──────────────────────────────────────────────────────
async function listTransactions(userId, query) {
  const { page = 1, limit = 50, sort = 'date', order = 'desc', ...filterParams } = query;

  // Guard sort field against injection
  const safeSort = ALLOWED_SORT_FIELDS.has(sort) ? sort : 'date';
  const safeOrder = order === 'asc' ? 1 : -1;

  const filter = buildFilter(userId, filterParams);
  const skip = Math.max(0, (parseInt(page) || 1) - 1) * Math.min(parseInt(limit) || 50, 200);
  const lim = Math.min(parseInt(limit) || 50, 200);
  const sortObj = { [safeSort]: safeOrder };

  const [transactions, total] = await Promise.all([
    txRepo.findByFilter(filter, { sort: sortObj, skip, limit: lim }),
    txRepo.countByFilter(filter),
  ]);

  return {
    transactions,
    pagination: { page: parseInt(page), limit: lim, total, pages: Math.ceil(total / lim) },
  };
}

// ── Build a single transaction doc (shared by single + bulk) ──────────────────
async function buildDoc(userId, item, index = null) {
  const errors = validateTransactionBody(item);
  if (errors.length) {
    // Prefix field names with index in bulk mode
    const prefixed =
      index !== null ? errors.map((e) => ({ ...e, field: `transactions[${index}].${e.field}` })) : errors;
    throw validationError(prefixed);
  }

  const {
    amount,
    description,
    merchant,
    type,
    date,
    category: manualCategory,
    isRecurring,
    recurringFrequency,
    splitDetails,
    upiId,
    referenceNumber,
    accountLast4,
    availableBalance,
    rawSms,
    smsBodyDate,
    sender,
  } = item;

  let category = manualCategory;
  let aiCategory = null;
  let aiConfidence = null;

  if (!category) {
    const result = await categorize(description, merchant);
    category = result.category;
    aiCategory = result.source !== 'keyword' ? result.category : null;
    aiConfidence = result.confidence;
  }

  const fingerprint = Transaction.buildFingerprint(
    userId,
    amount,
    merchant,
    date,
    referenceNumber,
    item.source || 'manual',
  );
  const normalizedMerchant = merchantService.normalizeMerchant(merchant) || null;

  return {
    userId,
    amount: Number(amount),
    description: (description || '').toString().trim().slice(0, 300),
    merchant: (merchant || '').toString().trim().slice(0, 120),
    normalizedMerchant,
    type,
    date: new Date(date),
    category,
    categorySource: manualCategory ? 'Manual' : aiCategory ? 'AI' : 'Rule',
    aiCategory,
    aiConfidence,
    isRecurring: Boolean(isRecurring),
    recurringFrequency: isRecurring ? recurringFrequency || 'monthly' : null,
    splitDetails: Array.isArray(splitDetails) ? splitDetails : [],
    source: item.source || 'manual',
    clientId: item.clientId || null,
    upiId: upiId || null,
    referenceNumber: referenceNumber || null,
    accountLast4: accountLast4 || null,
    availableBalance: availableBalance || null,
    rawSms: rawSms || null,
    smsBodyDate: smsBodyDate || null,
    sender: sender || null,
    _fingerprint: fingerprint,
  };
}

// ── Create — single OR bulk ───────────────────────────────────────────────────
// Accepts either a bare array [...] (bulk) or a plain object (single).
async function createTransaction(userId, body) {
  // ── Bulk path — bare array: POST [{ ... }, { ... }]
  if (Array.isArray(body)) {
    return createBulk(userId, body);
  }

  // ── Single path
  const doc = await buildDoc(userId, body);

  // Duplicate check
  const existing = await txRepo.findByFingerprint(userId, doc._fingerprint);
  if (existing) {
    throw Object.assign(new Error('Duplicate transaction — same amount, merchant, and date already recorded'), {
      statusCode: 409,
      errors: [{ field: '_fingerprint', message: 'Transaction already exists', existingId: existing._id }],
    });
  }

  const tx = await txRepo.create(doc);

  if (doc.type === 'expense') {
    runAlertRules(userId).catch((err) => console.error('[Alerts] Rule check failed:', err.message));
    runIntelligenceAlerts(userId).catch((err) => console.error('[Intelligence] Alert check failed:', err.message));
    // Recompute health score in background after expense changes
    const { computeHealthScore } = require('../../ai/services/healthScoreService');
    computeHealthScore(userId)
      .then((result) => {
        socketService.push(userId, socketService.EVENTS.HEALTH_SCORE, result);
      })
      .catch((err) => console.error('[HealthScore] Recompute failed:', err.message));
  }

  socketService.push(userId, socketService.EVENTS.TRANSACTION_ADDED, tx);

  // Invalidate AI insights/predictions
  cache.invalidatePrefix(`ai:insights:${userId}`).catch(() => {});
  cache.invalidatePrefix(`ai:suggestions:${userId}`).catch(() => {});
  cache.invalidatePrefix(`ai:predict:${userId}`).catch(() => {});

  return { transaction: tx };
}

// ── Bulk create ───────────────────────────────────────────────────────────────
async function createBulk(userId, items) {
  const arrayErrors = validateBulkArray(items, 500);
  if (arrayErrors.length) throw validationError(arrayErrors);

  // Validate all items up-front — collect all errors before rejecting
  const allErrors = [];
  items.forEach((item, i) => {
    const errs = validateTransactionBody(item);
    errs.forEach((e) => allErrors.push({ ...e, field: `transactions[${i}].${e.field}` }));
  });
  if (allErrors.length) throw validationError(allErrors);

  // Categorize all in one batch
  const categories = await categorizeBatch(items);

  const docs = items.map((item, i) => {
    const {
      amount,
      description,
      merchant,
      type,
      date,
      category: manualCat,
      isRecurring,
      recurringFrequency,
      splitDetails,
      upiId,
      referenceNumber,
      accountLast4,
      availableBalance,
      rawSms,
      smsBodyDate,
      sender,
    } = item;
    const cat = manualCat || categories[i]?.category || 'Other';
    const fingerprint = Transaction.buildFingerprint(
      userId,
      amount,
      merchant,
      date,
      referenceNumber,
      item.source || 'manual',
    );
    const normalizedMerchant = merchantService.normalizeMerchant(merchant) || null;
    return {
      userId,
      amount: Number(amount),
      description: (description || '').toString().trim().slice(0, 300),
      merchant: (merchant || '').toString().trim().slice(0, 120),
      normalizedMerchant,
      type,
      date: new Date(date),
      category: cat,
      categorySource: manualCat ? 'Manual' : categories[i]?.source === 'groq' ? 'AI' : 'Rule',
      aiCategory: categories[i]?.source === 'groq' ? cat : null,
      aiConfidence: categories[i]?.confidence || null,
      isRecurring: Boolean(isRecurring),
      recurringFrequency: isRecurring ? recurringFrequency || 'monthly' : null,
      splitDetails: Array.isArray(splitDetails) ? splitDetails : [],
      source: item.source || 'manual',
      clientId: item.clientId || null,
      upiId: upiId || null,
      referenceNumber: referenceNumber || null,
      accountLast4: accountLast4 || null,
      availableBalance: availableBalance || null,
      rawSms: rawSms || null,
      smsBodyDate: smsBodyDate || null,
      sender: sender || null,
      _fingerprint: fingerprint,
    };
  });

  const { inserted, duplicates, failed } = await txRepo.insertMany(docs);

  runAlertRules(userId).catch(() => {});
  runIntelligenceAlerts(userId).catch(() => {});

  if (inserted.length > 0) {
    socketService.push(userId, socketService.EVENTS.IMPORT_DONE, { imported: inserted.length });
    cache.invalidatePrefix(`ai:insights:${userId}`).catch(() => {});
    cache.invalidatePrefix(`ai:suggestions:${userId}`).catch(() => {});
    cache.invalidatePrefix(`ai:predict:${userId}`).catch(() => {});
  }

  return {
    imported: inserted.length,
    duplicates,
    failed,
    total: items.length,
  };
}

// ── Update ────────────────────────────────────────────────────────────────────
async function updateTransaction(userId, id, body) {
  const tx = await txRepo.findById(id, userId);
  if (!tx) throw Object.assign(new Error('Transaction not found'), { statusCode: 404 });

  const {
    amount,
    description,
    merchant,
    type,
    date,
    category,
    isRecurring,
    recurringFrequency,
    splitDetails,
    applyToPast,
    applyToFuture,
  } = body;

  if (amount !== undefined) {
    const parsed = Number(amount);
    if (isNaN(parsed) || parsed <= 0) {
      throw validationError([{ field: 'amount', message: 'amount must be a positive number' }]);
    }
    tx.amount = parsed;
  }
  if (type != null) tx.type = type;
  if (date != null) tx.date = new Date(date);
  if (description != null) tx.description = description.toString().trim().slice(0, 300);
  if (merchant != null) tx.merchant = merchant.toString().trim().slice(0, 120);
  if (isRecurring != null) tx.isRecurring = Boolean(isRecurring);
  if (recurringFrequency != null) tx.recurringFrequency = recurringFrequency;
  if (splitDetails != null) tx.splitDetails = Array.isArray(splitDetails) ? splitDetails : [];

  // ── Merchant Learning: when category is manually changed ────────────
  let bulkUpdatedCount = 0;
  if (category != null) {
    tx.category = category;
    tx.categorySource = 'Manual';

    const merchantKey = tx.upiId || tx.merchant || '';
    const normalizedMerch = merchantService.normalizeMerchant(merchantKey);

    if (normalizedMerch) {
      tx.normalizedMerchant = normalizedMerch;

      // Save future mapping if requested (or by default)
      if (applyToFuture !== false) {
        await merchantService.learnMerchantCategory(userId, tx.merchant, tx.upiId, category);
      }

      // Bulk-update past transactions from same merchant if requested
      if (applyToPast) {
        bulkUpdatedCount = await merchantService.applyToPastTransactions(userId, tx.merchant, tx.upiId, category);
      }
    }
  }

  // Recalculate fingerprint if key fields changed
  if (amount != null || merchant != null || date != null) {
    tx._fingerprint = Transaction.buildFingerprint(tx.userId, tx.amount, tx.merchant, tx.date);
  }

  await txRepo.save(tx);

  // Invalidate AI cache
  cache.invalidatePrefix(`ai:insights:${userId}`).catch(() => {});
  cache.invalidatePrefix(`ai:suggestions:${userId}`).catch(() => {});
  cache.invalidatePrefix(`ai:predict:${userId}`).catch(() => {});

  socketService.push(userId, socketService.EVENTS.TRANSACTION_ADDED, tx);

  return { transaction: tx, bulkUpdatedCount };
}

// ── Delete ────────────────────────────────────────────────────────────────────
async function deleteTransaction(userId, id) {
  const tx = await txRepo.findByIdAndDelete(id, userId);
  if (!tx) throw Object.assign(new Error('Transaction not found'), { statusCode: 404 });

  // Invalidate AI cache
  cache.invalidatePrefix(`ai:insights:${userId}`).catch(() => {});
  cache.invalidatePrefix(`ai:suggestions:${userId}`).catch(() => {});
  cache.invalidatePrefix(`ai:predict:${userId}`).catch(() => {});

  socketService.push(userId, socketService.EVENTS.TRANSACTION_DELETED, { id });

  return {};
}

// ── Clear / Reset ─────────────────────────────────────────────────────────────
async function clearTransactions(userId, { year, month } = {}) {
  const filter = { userId };

  if (year) {
    const y = parseInt(year);
    const m = month ? parseInt(month) : null;

    if (m) {
      const start = new Date(Date.UTC(y, m - 1, 1));
      const end = new Date(Date.UTC(y, m, 1));
      filter.date = { $gte: start, $lt: end };
    } else {
      const start = new Date(Date.UTC(y, 0, 1));
      const end = new Date(Date.UTC(y + 1, 0, 1));
      filter.date = { $gte: start, $lt: end };
    }
  }

  const result = await txRepo.deleteManyByFilter(filter);

  // Invalidate AI cache
  cache.invalidatePrefix(`ai:insights:${userId}`).catch(() => {});
  cache.invalidatePrefix(`ai:suggestions:${userId}`).catch(() => {});
  cache.invalidatePrefix(`ai:predict:${userId}`).catch(() => {});

  socketService.push(userId, socketService.EVENTS.TRANSACTION_DELETED, { all: true, year, month });

  return { deletedCount: result.deletedCount };
}

// ── CSV Import ────────────────────────────────────────────────────────────────
async function importFromCSV(userId, fileBuffer) {
  if (!fileBuffer) {
    throw validationError([{ field: 'file', message: 'CSV file is required' }]);
  }

  const { transactions: parsed, errors, skipped } = parseCSV(fileBuffer);

  if (!parsed.length) {
    throw Object.assign(new Error('No valid transactions found in CSV'), {
      statusCode: 400,
      errors: errors.slice(0, 10).map((e) => ({ field: 'csv', message: e })),
    });
  }

  const result = await createBulk(
    userId,
    parsed.map((t) => ({ ...t, source: 'csv' })),
  );
  return { ...result, skipped, parseErrors: errors.length };
}

// ── Summary ───────────────────────────────────────────────────────────────────
async function getSummary(userId, { year, month } = {}) {
  const now = new Date();
  const y = year ? parseInt(year) : now.getUTCFullYear();
  const m = month ? parseInt(month) : now.getUTCMonth() + 1;

  // Selected month boundaries
  const currentMonthStart = new Date(Date.UTC(y, m - 1, 1));
  const currentMonthEnd = new Date(Date.UTC(y, m, 1));
  currentMonthEnd.setMilliseconds(-1);

  // Previous month boundaries (for trends)
  const prevMonthStart = new Date(currentMonthStart);
  prevMonthStart.setUTCMonth(prevMonthStart.getUTCMonth() - 1);
  const prevMonthEnd = new Date(currentMonthStart);
  prevMonthEnd.setMilliseconds(-1);

  // Aggregation filters
  const kpiFilter = {
    userId,
    date: { $gte: currentMonthStart, $lte: currentMonthEnd },
  };

  const prevKpiFilter = {
    userId,
    date: { $gte: prevMonthStart, $lte: prevMonthEnd },
  };

  const [income, expense, byCategory, prevIncome, prevExpense] = await Promise.all([
    txRepo.aggregateIncome(kpiFilter),
    txRepo.aggregateExpense(kpiFilter),
    txRepo.aggregateByCategory(kpiFilter, 10),
    txRepo.aggregateIncome(prevKpiFilter),
    txRepo.aggregateExpense(prevKpiFilter),
  ]);

  const totalIncome = income[0]?.total || 0;
  const totalExpense = expense[0]?.total || 0;
  const oldIncome = prevIncome[0]?.total || 0;
  const oldExpense = prevExpense[0]?.total || 0;

  const calcTrend = (curr, prev) => {
    if (prev === 0) return curr > 0 ? 100 : 0;
    return +(((curr - prev) / prev) * 100).toFixed(1);
  };

  return {
    totalIncome: Math.round(totalIncome),
    totalExpense: Math.round(totalExpense),
    netSavings: Math.round(totalIncome - totalExpense),
    savingsRate: totalIncome > 0 ? Math.round(((totalIncome - totalExpense) / totalIncome) * 100) : null,
    incomeTrend: calcTrend(totalIncome, oldIncome),
    expenseTrend: calcTrend(totalExpense, oldExpense),
    balanceTrend: calcTrend(totalIncome - totalExpense, oldIncome - oldExpense),
    incomeCount: income[0]?.count || 0,
    expenseCount: expense[0]?.count || 0,
    byCategory: byCategory.map((c) => ({
      category: c._id,
      total: Math.round(c.total),
      count: c.count,
      percentage: totalExpense > 0 ? Math.round((c.total / totalExpense) * 100) : 0,
    })),
  };
}



module.exports = {
  listTransactions,
  createTransaction,
  updateTransaction,
  deleteTransaction,
  clearTransactions,
  importFromCSV,
  getSummary,
};
