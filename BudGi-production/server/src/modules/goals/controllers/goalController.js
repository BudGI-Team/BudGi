// modules/goals/controllers/goalController.js
'use strict';

const goalService = require('../services/goalService');
const { ok, created, asyncHandler } = require('../../../utils/response');

const listGoals = asyncHandler(async (req, res) => {
  const goals = await goalService.listGoals(req.user.id);
  return ok(res, { goals });
});

const createGoal = asyncHandler(async (req, res) => {
  const goal = await goalService.createGoal(req.user.id, req.body);
  return created(res, { goal }, 'Goal created');
});

const updateGoal = asyncHandler(async (req, res) => {
  const goal = await goalService.updateGoal(req.user.id, req.params.id, req.body);
  return ok(res, { goal }, 'Goal updated');
});

const deleteGoal = asyncHandler(async (req, res) => {
  await goalService.deleteGoal(req.user.id, req.params.id);
  return ok(res, {}, 'Goal deleted');
});

module.exports = {
  listGoals,
  createGoal,
  updateGoal,
  deleteGoal,
};
