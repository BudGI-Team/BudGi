'use strict';

const spaceService = require('../services/spaceService');
const { ok, created, asyncHandler } = require('../../../utils/response');

exports.createSpace = asyncHandler(async (req, res) => {
  const space = await spaceService.createSpace(req.user.id, req.body);
  return created(res, { space }, 'Space created successfully');
});

exports.getSpaces = asyncHandler(async (req, res) => {
  const spaces = await spaceService.getUserSpaces(req.user.id);
  return ok(res, { spaces });
});

exports.getSpaceDetails = asyncHandler(async (req, res) => {
  const space = await spaceService.getSpaceById(req.user.id, req.params.id);
  return ok(res, { space });
});

exports.joinSpace = asyncHandler(async (req, res) => {
  const space = await spaceService.joinSpace(req.user.id, req.params.code);
  return ok(res, { space }, 'Joined space successfully');
});

exports.addGoal = asyncHandler(async (req, res) => {
  const space = await spaceService.addGoal(req.user.id, req.params.id, req.body);
  return created(res, { space }, 'Goal added successfully');
});
