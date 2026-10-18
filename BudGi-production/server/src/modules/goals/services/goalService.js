// modules/goals/services/goalService.js
'use strict';

const goalRepo = require('../repositories/goalRepository');
const socketService = require('../../../utils/socketService');

async function listGoals(userId) {
  return goalRepo.findByUser(userId);
}

async function createGoal(userId, body) {
  const { title, targetAmount, currentAmount, deadline, category, icon, color } = body;

  if (!title || !targetAmount) {
    throw Object.assign(new Error('Title and target amount are required'), { statusCode: 400 });
  }

  const goal = await goalRepo.create({
    userId,
    title: title.trim(),
    targetAmount: Number(targetAmount),
    currentAmount: Number(currentAmount || 0),
    deadline: deadline || null,
    category: category || 'savings',
    icon: icon || '🎯',
    color: color || '#7C6AF5',
  });

  socketService.push(userId, 'GOAL_ADDED', goal);

  return goal;
}

async function updateGoal(userId, goalId, body) {
  const goal = await goalRepo.findById(goalId, userId);
  if (!goal) {
    throw Object.assign(new Error('Goal not found'), { statusCode: 404 });
  }

  const { title, targetAmount, currentAmount, addAmount, deadline, category, icon, color } = body;

  if (title) goal.title = title.trim();
  if (targetAmount != null) goal.targetAmount = Number(targetAmount);
  if (currentAmount != null) goal.currentAmount = Number(currentAmount);
  if (addAmount) goal.currentAmount = Math.max(0, goal.currentAmount + Number(addAmount));
  if (deadline !== undefined) goal.deadline = deadline || null;
  if (category) goal.category = category;
  if (icon) goal.icon = icon;
  if (color) goal.color = color;

  // Auto-complete check
  if (goal.currentAmount >= goal.targetAmount && !goal.isCompleted) {
    goal.isCompleted = true;
    goal.completedAt = new Date();
  }
  if (goal.currentAmount < goal.targetAmount && goal.isCompleted) {
    goal.isCompleted = false;
    goal.completedAt = null;
  }

  await goal.save();
  socketService.push(userId, 'GOAL_UPDATED', goal);
  return goal;
}

async function deleteGoal(userId, goalId) {
  const goal = await goalRepo.deleteById(goalId, userId);
  if (!goal) {
    throw Object.assign(new Error('Goal not found'), { statusCode: 404 });
  }
  socketService.push(userId, 'GOAL_DELETED', { id: goalId });
  return goal;
}

module.exports = {
  listGoals,
  createGoal,
  updateGoal,
  deleteGoal,
};
