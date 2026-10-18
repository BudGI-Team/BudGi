// modules/alerts/controllers/alertController.js
// req/res ONLY — all logic in alertService

'use strict';

const alertService = require('../services/alertService');
const { ok, asyncHandler } = require('../../../utils/response');
const { validationError } = require('../../../utils/validators');

const getAlerts = asyncHandler(async (req, res) => {
  const unreadOnly = req.query.unreadOnly === 'true';
  const limit = Math.min(parseInt(req.query.limit) || 20, 100);

  const [alerts, unreadCount] = await Promise.all([
    alertService.getAlerts(req.user.id, { limit, unreadOnly }),
    alertService.getUnreadCount(req.user.id),
  ]);

  return ok(res, { alerts, unreadCount });
});

const getUnreadCount = asyncHandler(async (req, res) => {
  const count = await alertService.getUnreadCount(req.user.id);
  return ok(res, { count });
});

const markRead = asyncHandler(async (req, res) => {
  const { alertIds } = req.body;
  if (!Array.isArray(alertIds)) {
    throw validationError([{ field: 'alertIds', message: 'alertIds must be an array' }]);
  }
  await alertService.markAlertsRead(req.user.id, alertIds);
  return ok(res, {}, 'Alerts marked as read');
});

const clearAlerts = asyncHandler(async (req, res) => {
  await alertService.clearAllAlerts(req.user.id);
  return ok(res, {}, 'All alerts cleared');
});

const runAlerts = asyncHandler(async (req, res) => {
  const alerts = await alertService.runAlertRules(req.user.id);
  return ok(res, { triggered: alerts.length, alerts }, `${alerts.length} alert(s) triggered`);
});

module.exports = { getAlerts, getUnreadCount, markRead, clearAlerts, runAlerts };
