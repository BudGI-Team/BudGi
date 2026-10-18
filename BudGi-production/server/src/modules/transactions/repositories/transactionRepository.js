// modules/transactions/repositories/transactionRepository.js
// DB queries ONLY — no business logic here

'use strict';

const mongoose = require('mongoose');
const Transaction = require('../../../models/Transaction');

async function findByFilter(filter, { sort, skip, limit }) {
  return Transaction.find(filter).sort(sort).skip(skip).limit(limit).lean();
}

async function countByFilter(filter) {
  return Transaction.countDocuments(filter);
}

async function findById(id, userId) {
  return Transaction.findOne({ _id: id, userId });
}

async function findByFingerprint(userId, fingerprint) {
  return Transaction.findOne({ userId, _fingerprint: fingerprint }).lean();
}

async function create(data) {
  return Transaction.create(data);
}

async function save(doc) {
  return doc.save();
}

async function findByIdAndDelete(id, userId) {
  return Transaction.findOneAndDelete({ _id: id, userId });
}

async function deleteManyByFilter(filter) {
  return Transaction.deleteMany(filter);
}

/**
 * Bulk insert with ordered:false so partial successes work.
 * Returns { inserted, duplicates, errors }.
 */
async function insertMany(docs) {
  try {
    const result = await Transaction.insertMany(docs, { ordered: false });
    return { inserted: result, duplicates: 0, failed: 0 };
  } catch (bulkErr) {
    const inserted = bulkErr.insertedDocs || [];
    const writeErrors = bulkErr.writeErrors || [];
    const duplicates = writeErrors.filter((e) => e.code === 11000).length;
    const failed = writeErrors.filter((e) => e.code !== 11000).length;
    return { inserted, duplicates, failed };
  }
}

async function aggregateIncome(filter) {
  const { userId, ...rest } = filter;
  const match = {
    ...rest,
    type: 'income',
    userId: typeof userId === 'string' ? new mongoose.Types.ObjectId(userId) : userId,
  };
  return Transaction.aggregate([
    { $match: match },
    { $group: { _id: null, total: { $sum: '$amount' }, count: { $sum: 1 } } },
  ]);
}

async function aggregateExpense(filter) {
  const { userId, ...rest } = filter;
  const match = {
    ...rest,
    type: 'expense',
    userId: typeof userId === 'string' ? new mongoose.Types.ObjectId(userId) : userId,
  };
  return Transaction.aggregate([
    { $match: match },
    { $group: { _id: null, total: { $sum: '$amount' }, count: { $sum: 1 } } },
  ]);
}

async function aggregateByCategory(filter, limit = 10) {
  const { userId, ...rest } = filter;
  const match = {
    ...rest,
    type: 'expense',
    userId: typeof userId === 'string' ? new mongoose.Types.ObjectId(userId) : userId,
  };
  return Transaction.aggregate([
    { $match: match },
    { $group: { _id: '$category', total: { $sum: '$amount' }, count: { $sum: 1 } } },
    { $sort: { total: -1 } },
    { $limit: limit },
  ]);
}

async function findRecent(userId, days) {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - days);
  return Transaction.find({ userId, date: { $gte: cutoff } }).lean();
}

async function findByDateRange(userId, start, end) {
  return Transaction.find({ userId, date: { $gte: start, $lt: end } }).lean();
}

module.exports = {
  findByFilter,
  countByFilter,
  findById,
  findByFingerprint,
  create,
  save,
  findByIdAndDelete,
  insertMany,
  aggregateIncome,
  aggregateExpense,
  aggregateByCategory,
  findRecent,
  findByDateRange,
  deleteManyByFilter,
};
