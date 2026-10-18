// modules/goals/repositories/goalRepository.js
'use strict';

const Goal = require('../../../models/Goal');

async function findByUser(userId) {
  const goals = await Goal.find({ userId }).sort({ createdAt: -1 }).lean();
  // Virtuals don't run on lean — compute manually
  return goals.map((g) => ({
    ...g,
    progress: g.targetAmount > 0 ? Math.min(100, Math.round((g.currentAmount / g.targetAmount) * 100)) : 100,
  }));
}

async function findById(id, userId) {
  return Goal.findOne({ _id: id, userId });
}

async function create(data) {
  return Goal.create(data);
}

async function deleteById(id, userId) {
  return Goal.findOneAndDelete({ _id: id, userId });
}

async function findUpdatedSince(userId, since) {
  return Goal.find({ userId, updatedAt: { $gt: since } }).lean();
}

module.exports = {
  findByUser,
  findById,
  create,
  deleteById,
  findUpdatedSince,
};
