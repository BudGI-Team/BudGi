// modules/budgets/repositories/budgetRepository.js
// DB queries ONLY

'use strict';

const Budget = require('../../../models/Budget');
const Transaction = require('../../../models/Transaction');

async function findByUser(userId) {
  return Budget.find({ userId }).lean();
}

async function findById(id, userId) {
  return Budget.findOne({ _id: id, userId });
}

async function create(data) {
  return Budget.create(data);
}

async function save(doc) {
  return doc.save();
}

async function findByIdAndDelete(id, userId) {
  return Budget.findOneAndDelete({ _id: id, userId });
}

async function getMonthlySpendingByCategory(userId, monthStart) {
  return Transaction.aggregate([
    { $match: { userId, type: 'expense', date: { $gte: monthStart } } },
    { $group: { _id: '$category', spent: { $sum: '$amount' } } },
  ]);
}

module.exports = {
  findByUser,
  findById,
  create,
  save,
  findByIdAndDelete,
  getMonthlySpendingByCategory,
};
