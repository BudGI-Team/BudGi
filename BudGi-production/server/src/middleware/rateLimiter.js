// src/middleware/rateLimiter.js
//
// Tiered rate limiters — all return the standard { success, message, data } envelope.
// Three tiers:
//   general  — all API routes       500 req / 15 min (per user when authenticated)
//   auth     — /api/auth/*           20 req / 15 min  (brute-force protection)
//   ai       — /api/ai/*             60 req / 15 min  (LLM cost protection)

'use strict';

const rateLimit = require('express-rate-limit');
const env = require('../config/env');
const logger = require('../utils/logger');

// ── Shared response handler ───────────────────────────────────────────────────

function onLimitReached(req, _res) {
  logger.warn('Rate limit exceeded', {
    ip: req.ip,
    path: req.path,
    userId: req.user?.id ?? null,
  });
}

function envelope(message) {
  return { success: false, message, data: null };
}

// ── General limiter — applied globally ───────────────────────────────────────

const general = rateLimit({
  windowMs: env.RATE_LIMIT_WINDOW_MS,
  max: env.NODE_ENV === 'development' ? 1000 : env.RATE_LIMIT_MAX || 500,
  standardHeaders: true, // RateLimit-* headers (RFC 6585)
  legacyHeaders: false, // no X-RateLimit-* headers
  // Use user ID when authenticated, IP for anonymous — prevents all tabs of same user sharing IP bucket
  keyGenerator: (req) => req.user?.id ?? req.ip,
  handler(req, res) {
    onLimitReached(req, res);
    res.status(429).json(envelope(`Rate limit exceeded. Please wait a moment before making more requests.`));
  },
});

// ── Auth limiter — stricter, brute-force protection ───────────────────────────

const auth = rateLimit({
  windowMs: env.RATE_LIMIT_WINDOW_MS,
  max: env.RATE_LIMIT_AUTH_MAX,
  standardHeaders: true,
  legacyHeaders: false,
  skipSuccessfulRequests: true, // only count failed/non-2xx responses
  keyGenerator: (req) => req.ip,
  handler(req, res) {
    onLimitReached(req, res);
    res
      .status(429)
      .json(envelope(`Too many authentication attempts. Please wait ${env.RATE_LIMIT_WINDOW_MS / 60000} minutes.`));
  },
});

// ── AI limiter — protects Groq API costs ─────────────────────────────────────

const ai = rateLimit({
  windowMs: env.RATE_LIMIT_WINDOW_MS,
  max: env.NODE_ENV === 'development' ? 200 : env.RATE_LIMIT_AI_MAX || 60,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: (req) => req.user?.id ?? req.ip, // per user, not per IP
  handler(req, res) {
    onLimitReached(req, res);
    res.status(429).json(envelope(`AI request limit reached. Please wait a moment before trying again.`));
  },
});

module.exports = { general, auth, ai };
