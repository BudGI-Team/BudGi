// src/middleware/requestLogger.js
//
// Attaches a unique requestId to every request (X-Request-Id header or generated).
// Logs each request on arrival and each response on finish with duration.
// The requestId flows into every log line emitted during that request's lifecycle.

'use strict';

const crypto = require('crypto');
const logger = require('../utils/logger');

// Paths to skip logging entirely (health checks, static assets)
const SKIP_PATHS = new Set(['/health', '/favicon.ico']);

function requestLogger(req, res, next) {
  if (SKIP_PATHS.has(req.path)) return next();

  // ── Attach request ID ─────────────────────────────────────────────────────
  const requestId = req.headers['x-request-id'] || crypto.randomUUID();
  req.requestId = requestId;
  res.setHeader('X-Request-Id', requestId);

  // ── Build child logger scoped to this request ────────────────────────────
  req.log = logger.child({
    requestId,
    method: req.method,
    path: req.path,
    userId: null, // populated by auth middleware after this runs
  });

  const startAt = process.hrtime.bigint();

  req.log.http('→ Incoming', {
    ip: req.ip,
    userAgent: req.headers['user-agent'] ?? '',
    contentType: req.headers['content-type'] ?? '',
  });

  // ── Log on response finish ───────────────────────────────────────────────
  res.on('finish', () => {
    const durationMs = Number(process.hrtime.bigint() - startAt) / 1_000_000;
    const level = res.statusCode >= 500 ? 'error' : res.statusCode >= 400 ? 'warn' : 'http';

    // Update userId now that auth middleware has run
    const log = logger.child({
      requestId,
      method: req.method,
      path: req.path,
      userId: req.user?.id ?? null,
      status: res.statusCode,
      durationMs: Math.round(durationMs),
    });

    log[level](`← ${res.statusCode} ${req.method} ${req.path} (${Math.round(durationMs)}ms)`);
  });

  next();
}

/**
 * Morgan-compatible token for request IDs.
 * Use as: morgan(':method :url :status — :request-id', { stream: logger.stream })
 */
requestLogger.morganTokens = function registerTokens(morgan) {
  morgan.token('request-id', (req) => req.requestId ?? '-');
  morgan.token('user-id', (req) => req.user?.id ?? '-');
};

module.exports = requestLogger;
