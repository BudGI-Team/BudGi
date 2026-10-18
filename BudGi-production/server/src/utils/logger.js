// src/utils/logger.js
//
// Structured logger — JSON in production, pretty-printed in development.
// Drop-in replacement for console.log throughout the codebase.
// Never throws. Safe to call from any context including error handlers.
//
// Usage:
//   const logger = require('./utils/logger');
//   logger.info('Server started', { port: 5000 });
//   logger.warn('Cache miss', { key: 'rates:INR' });
//   logger.error('DB connection failed', err);
//   logger.http('POST /api/transactions 201 45ms');   // used by morgan stream

'use strict';

const env = require('../config/env');

// ── Log levels ────────────────────────────────────────────────────────────────
const LEVELS = { error: 0, warn: 1, info: 2, http: 3, debug: 4 };

const ACTIVE_LEVEL = (() => {
  if (env.LOG_LEVEL && LEVELS[env.LOG_LEVEL] !== undefined) return LEVELS[env.LOG_LEVEL];
  return env.isProd ? LEVELS.http : LEVELS.debug;
})();

// ── Colour codes for dev terminal ─────────────────────────────────────────────
const COLOURS = {
  error: '\x1b[31m', // red
  warn: '\x1b[33m', // yellow
  info: '\x1b[36m', // cyan
  http: '\x1b[35m', // magenta
  debug: '\x1b[90m', // grey
  reset: '\x1b[0m',
};

// ── Core write function ───────────────────────────────────────────────────────

function write(level, message, meta) {
  if (LEVELS[level] > ACTIVE_LEVEL) return;

  const ts = new Date().toISOString();

  if (env.isProd) {
    // ── Production: newline-delimited JSON (compatible with CloudWatch, Datadog, etc.)
    const entry = { ts, level, message };
    if (meta instanceof Error) {
      entry.error = { message: meta.message, name: meta.name, stack: meta.stack };
    } else if (meta !== undefined && meta !== null) {
      entry.meta = sanitizeMeta(meta);
    }
    try {
      process.stdout.write(JSON.stringify(entry) + '\n');
    } catch {
      // absolute last resort — stdout write failed
    }
  } else {
    // ── Development: coloured, human-readable
    const colour = COLOURS[level] ?? '';
    const reset = COLOURS.reset;
    const prefix = `${colour}[${level.toUpperCase().padEnd(5)}]${reset} ${ts}`;
    const suffix = formatMeta(meta);
    try {
      if (level === 'error') {
        process.stderr.write(`${prefix} ${message}${suffix}\n`);
      } else {
        process.stdout.write(`${prefix} ${message}${suffix}\n`);
      }
    } catch {
      // silently drop if stdout/stderr is closed
    }
  }
}

// ── Public API ────────────────────────────────────────────────────────────────

const logger = {
  error: (msg, meta) => write('error', msg, meta),
  warn: (msg, meta) => write('warn', msg, meta),
  info: (msg, meta) => write('info', msg, meta),
  http: (msg, meta) => write('http', msg, meta),
  debug: (msg, meta) => write('debug', msg, meta),

  /**
   * Morgan-compatible write stream.
   * Usage in app.js: morgan(format, { stream: logger.stream })
   */
  stream: {
    write(message) {
      write('http', message.trim());
    },
  },

  /**
   * Attach request context for correlated log lines.
   * Returns a child logger scoped to a single request.
   * Usage: const log = logger.child({ requestId, userId });
   */
  child(context) {
    return {
      error: (msg, meta) => write('error', msg, { ...context, ...normMeta(meta) }),
      warn: (msg, meta) => write('warn', msg, { ...context, ...normMeta(meta) }),
      info: (msg, meta) => write('info', msg, { ...context, ...normMeta(meta) }),
      http: (msg, meta) => write('http', msg, { ...context, ...normMeta(meta) }),
      debug: (msg, meta) => write('debug', msg, { ...context, ...normMeta(meta) }),
    };
  },
};

// ── Helpers ───────────────────────────────────────────────────────────────────

function normMeta(meta) {
  if (!meta) return {};
  if (meta instanceof Error) return { error: meta.message };
  return meta;
}

function sanitizeMeta(obj) {
  try {
    // Remove secrets from logs
    const REDACTED = ['password', 'token', 'secret', 'authorization', 'apiKey', 'api_key'];
    const str = JSON.stringify(obj, (key, val) =>
      REDACTED.some((r) => key.toLowerCase().includes(r)) ? '[REDACTED]' : val,
    );
    return JSON.parse(str);
  } catch {
    return String(obj);
  }
}

function formatMeta(meta) {
  if (meta === undefined || meta === null) return '';
  if (meta instanceof Error) {
    return ` — ${meta.message}\n${meta.stack ?? ''}`;
  }
  try {
    const str = JSON.stringify(sanitizeMeta(meta));
    return str === '{}' ? '' : ` ${str}`;
  } catch {
    return '';
  }
}

module.exports = logger;
