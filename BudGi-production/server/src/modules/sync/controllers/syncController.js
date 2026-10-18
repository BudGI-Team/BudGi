// modules/sync/controllers/syncController.js
'use strict';

const syncService = require('../services/syncService');
const { ok, asyncHandler } = require('../../../utils/response');

// POST /api/sync — full bidirectional sync
const fullSync = asyncHandler(async (req, res) => {
  const result = await syncService.fullSync(req.user.id, req.body);
  return ok(res, result, 'Sync complete');
});

// GET /api/sync/pull?since=<ISO_timestamp> — lightweight delta pull
const deltaPull = asyncHandler(async (req, res) => {
  const result = await syncService.deltaPull(req.user.id, req.query.since);
  return ok(res, result, 'Delta pull complete');
});

module.exports = { fullSync, deltaPull };
