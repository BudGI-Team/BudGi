// modules/alerts/repositories/alertRepository.js
// DB queries ONLY — thin wrapper, alertService already has most logic

'use strict';

// alertService owns the Alert model directly; this repo exists for
// any future direct queries needed by the controller layer.
// Re-export from alertService to keep the interface consistent.
const alertService = require('../services/alertService');

module.exports = alertService;
