// src/middleware/security.js
//
// All security-related middleware in one place.
// Applied in app.js before any route handler.
//
// Exports:
//   helmet()    — HTTP security headers
//   cors()      — origin whitelist
//   sanitize()  — strips __proto__ pollution and trims strings

'use strict';

const helmetLib = require('helmet');
const corsLib = require('cors');
const env = require('../config/env');
const logger = require('../utils/logger');

// ── Allowed origins ───────────────────────────────────────────────────────────

const RAW_ORIGINS = env.CLIENT_ORIGIN.split(',')
  .map((o) => o.trim())
  .filter(Boolean);
const ORIGIN_SET = new Set(RAW_ORIGINS);

// In development also allow localhost variants automatically
if (!env.isProd) {
  ['http://localhost:3000', 'http://localhost:5173', 'http://127.0.0.1:5173'].forEach((o) => ORIGIN_SET.add(o));
}

const ALLOWED_ORIGINS = [...ORIGIN_SET];

function corsOriginHandler(origin, callback) {
  // Allow non-browser clients (Postman, mobile apps) — no Origin header
  if (!origin) return callback(null, true);

  if (ORIGIN_SET.has(origin)) {
    return callback(null, true);
  }

  // In development, automatically allow private network / LAN origins
  if (!env.isProd && /^https?:\/\/(localhost|127\.0\.0\.1|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})(:\d+)?$/.test(origin)) {
    return callback(null, true);
  }

  logger.warn('CORS: rejected origin', { origin });
  callback(new Error(`Origin '${origin}' not allowed by CORS`));
}

// ── Exports ───────────────────────────────────────────────────────────────────

/**
 * Helmet with a production-appropriate CSP.
 * Disable default CSP in development so browser devtools work freely.
 */
function helmet() {
  return helmetLib({
    contentSecurityPolicy: env.isProd
      ? {
          directives: {
            defaultSrc: ["'self'"],
            scriptSrc: ["'self'"],
            styleSrc: ["'self'", "'unsafe-inline'"],
            imgSrc: ["'self'", 'data:', 'https:'],
            connectSrc: ["'self'"],
            fontSrc: ["'self'"],
            objectSrc: ["'none'"],
            upgradeInsecureRequests: [],
          },
        }
      : false,
    crossOriginEmbedderPolicy: false, // allow mobile WebView embedding
  });
}

/**
 * CORS with per-request origin validation.
 */
function cors() {
  return corsLib({
    origin: corsOriginHandler,
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-Request-Id'],
    exposedHeaders: ['X-Request-Id', 'RateLimit-Limit', 'RateLimit-Remaining', 'RateLimit-Reset'],
    maxAge: 86400, // preflight cache: 24h
  });
}

/**
 * Body sanitizer — removes prototype-pollution keys, MongoDB operators, trims string values.
 * Runs after express.json() so req.body is already parsed.
 */
function sanitize(req, _res, next) {
  if (req.body && typeof req.body === 'object') {
    req.body = deepSanitize(req.body);
  }
  // Also sanitize query params against NoSQL injection
  if (req.query && typeof req.query === 'object') {
    req.query = deepSanitize(req.query);
  }
  next();
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

const DANGEROUS_KEYS = new Set(['__proto__', 'constructor', 'prototype']);

function isDangerousKey(key) {
  if (DANGEROUS_KEYS.has(key)) return true;
  // Block MongoDB operators ($gt, $ne, $in, $regex, etc.)
  if (typeof key === 'string' && key.startsWith('$')) return true;
  return false;
}

function deepSanitize(obj, depth = 0) {
  if (depth > 10) return obj; // prevent infinite recursion on circular objects
  if (Array.isArray(obj)) {
    return obj.map((item) => deepSanitize(item, depth + 1));
  }
  if (obj !== null && typeof obj === 'object') {
    const clean = {};
    for (const [key, value] of Object.entries(obj)) {
      if (isDangerousKey(key)) continue; // drop pollution & injection keys
      clean[key] = deepSanitize(value, depth + 1);
    }
    return clean;
  }
  if (typeof obj === 'string') {
    return obj.trim();
  }
  return obj;
}

module.exports = { helmet, cors, sanitize, ALLOWED_ORIGINS };
