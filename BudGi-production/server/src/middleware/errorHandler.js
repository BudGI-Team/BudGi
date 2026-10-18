// src/middleware/errorHandler.js
//
// Global Express error handler — the last line of defence.
// GUARANTEES: every error, regardless of origin, returns the standard envelope.
// GUARANTEES: server never crashes from a request error.
// GUARANTEES: no stack traces leak to clients in production.

'use strict';

const logger = require('../utils/logger');
const env = require('../config/env');

// ── Error classifier ──────────────────────────────────────────────────────────

function classify(err) {
  // Mongoose: document validation failed
  if (err.name === 'ValidationError') {
    const errors = Object.values(err.errors).map((e) => ({
      field: e.path,
      message: e.message,
    }));
    return { statusCode: 400, message: 'Validation failed', errors };
  }

  // Mongoose: duplicate key (unique index violation)
  if (err.code === 11000) {
    const field = Object.keys(err.keyPattern ?? {})[0] ?? 'field';
    return {
      statusCode: 409,
      message: `Duplicate entry — ${field} already exists`,
      errors: [{ field, message: `${field} already exists` }],
    };
  }

  // Mongoose: bad ObjectId / type cast
  if (err.name === 'CastError') {
    return {
      statusCode: 400,
      message: `Invalid value for '${err.path}'`,
      errors: [{ field: err.path, message: `'${err.value}' is not a valid ${err.kind}` }],
    };
  }

  // Mongoose: connection / topology errors
  if (err.name === 'MongoNetworkError' || err.name === 'MongoServerSelectionError') {
    return { statusCode: 503, message: 'Database temporarily unavailable' };
  }

  // JWT
  if (err.name === 'JsonWebTokenError') {
    return { statusCode: 401, message: 'Invalid token' };
  }
  if (err.name === 'TokenExpiredError') {
    return { statusCode: 401, message: 'Token expired — please log in again' };
  }

  // Multer
  if (err.code === 'LIMIT_FILE_SIZE') {
    return { statusCode: 400, message: 'File too large. Maximum size is 5 MB.' };
  }
  if (err.code === 'LIMIT_UNEXPECTED_FILE') {
    return { statusCode: 400, message: 'Unexpected file field in upload.' };
  }

  // JSON body parse error (express.json() throws this)
  if (err.type === 'entity.parse.failed') {
    return { statusCode: 400, message: 'Invalid JSON in request body.' };
  }

  // Payload too large
  if (err.type === 'entity.too.large') {
    return { statusCode: 413, message: 'Request body too large.' };
  }

  // Service-thrown errors (have .statusCode attached)
  if (err.statusCode || err.status) {
    return {
      statusCode: err.statusCode ?? err.status,
      message: err.message || 'Request failed',
      errors: err.errors ?? null,
    };
  }

  // Unknown — treat as 500
  return { statusCode: 500, message: 'Internal server error' };
}

// ── Error handler ─────────────────────────────────────────────────────────────

function errorHandler(err, req, res, _next) {
  const { statusCode, message, errors } = classify(err);

  // Always log — attach request context when available
  const logContext = {
    requestId: req.requestId ?? null,
    userId: req.user?.id ?? null,
    method: req.method,
    path: req.path,
    statusCode,
  };

  if (statusCode >= 500) {
    logger.error(message, { ...logContext, stack: err.stack });
  } else if (statusCode >= 400) {
    logger.warn(message, logContext);
  }

  // Build response — never expose stack in production
  const body = {
    success: false,
    message,
    data: null,
  };

  if (errors) body.errors = errors;
  if (!env.isProd && err.stack) body._stack = err.stack;

  return res.status(statusCode).json(body);
}

// ── Unhandled rejection / exception safety net ───────────────────────────────
// These fire for errors that escape all Express handlers (e.g. in async startup code).

process.on('unhandledRejection', (reason) => {
  logger.error('Unhandled promise rejection', reason instanceof Error ? reason : { reason });
  // Do NOT exit — let the request finish normally. A separate health-check will catch degradation.
});

process.on('uncaughtException', (err) => {
  logger.error('Uncaught exception', err);
  // Uncaught exceptions leave the process in an undefined state — exit gracefully
  process.exit(1);
});

module.exports = errorHandler;
