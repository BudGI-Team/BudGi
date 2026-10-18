// src/services/cacheService.js
/**
 * Cache Service — Redis-backed caching with in-memory fallback
 *
 * Replaces all the scattered in-memory caches (_rateCache, _marketCache, etc.)
 * with a unified, Redis-backed TTL cache.
 *
 * Usage:
 *   await cache.set('rates:INR', data, 3600);   // TTL in seconds
 *   const v = await cache.get('rates:INR');      // null if miss/expired
 *   await cache.del('rates:INR');
 *
 * Falls back to in-memory Map if Redis is not configured (REDIS_URL not set).
 * This means the app works in development without Redis installed.
 */

'use strict';

const env = require('../config/env');
const logger = require('./logger');

// ── Redis client (lazy) ──────────────────────────────────────────────────────
let _redis = null;

async function getRedis() {
  if (_redis) return _redis;
  if (!env.REDIS_URL) return null;

  try {
    // ioredis is optional — only required if REDIS_URL is set
    const Redis = require('ioredis');
    _redis = new Redis(env.REDIS_URL, {
      lazyConnect: true,
      maxRetriesPerRequest: 2,
      connectTimeout: 3000,
    });
    _redis.on('error', (err) => {
      logger.warn('Redis error, falling back to memory:', { error: err.message });
      _redis = null;
    });
    await _redis.connect();
    logger.info('Redis connected');
    return _redis;
  } catch (err) {
    logger.warn('Redis not available, using in-memory cache:', { error: err.message });
    _redis = null;
    return null;
  }
}

// ── In-memory fallback ────────────────────────────────────────────────────────
// Simple TTL store — good enough for single-instance dev environments
const _memStore = new Map(); // key → { value, expiresAt }

function memGet(key) {
  const entry = _memStore.get(key);
  if (!entry) return null;
  if (Date.now() > entry.expiresAt) {
    _memStore.delete(key);
    return null;
  }
  return entry.value;
}

function memSet(key, value, ttlSeconds) {
  _memStore.set(key, {
    value,
    expiresAt: Date.now() + ttlSeconds * 1000,
  });
  // Prevent unbounded growth: keep at most 500 keys
  if (_memStore.size > 500) {
    const firstKey = _memStore.keys().next().value;
    _memStore.delete(firstKey);
  }
}

function memDel(key) {
  _memStore.delete(key);
}

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Get a cached value. Returns null on miss or expiry.
 * @param {string} key
 * @returns {Promise<any|null>}
 */
async function get(key) {
  try {
    const redis = await getRedis();
    if (redis) {
      const raw = await redis.get(key);
      return raw ? JSON.parse(raw) : null;
    }
    return memGet(key);
  } catch (err) {
    logger.error('Cache get error:', err);
    return memGet(key); // fallback
  }
}

/**
 * Set a cached value with TTL.
 * @param {string} key
 * @param {any}    value    — will be JSON-serialised
 * @param {number} ttl      — seconds (default: 300 = 5 min)
 */
async function set(key, value, ttl = 300) {
  try {
    const redis = await getRedis();
    if (redis) {
      await redis.setex(key, ttl, JSON.stringify(value));
    } else {
      memSet(key, value, ttl);
    }
  } catch (err) {
    logger.error('Cache set error:', err);
    memSet(key, value, ttl); // fallback
  }
}

/**
 * Delete a cached key.
 * @param {string} key
 */
async function del(key) {
  try {
    const redis = await getRedis();
    if (redis) {
      await redis.del(key);
    } else {
      memDel(key);
    }
  } catch (err) {
    logger.error('Cache del error:', err);
  }
}

/**
 * Cache-aside helper: returns cached value, or calls fn() and caches result.
 *
 * @param {string}   key
 * @param {Function} fn      — async function that returns the value to cache
 * @param {number}   ttl     — seconds
 */
async function remember(key, fn, ttl = 300) {
  const cached = await get(key);
  if (cached !== null) return cached;

  const fresh = await fn();
  if (fresh !== null && fresh !== undefined) {
    await set(key, fresh, ttl);
  }
  return fresh;
}

/**
 * Invalidate all keys matching a prefix pattern (Redis SCAN + DEL / mem filter).
 * @param {string} prefix  — e.g. 'user:abc123:'
 */
async function invalidatePrefix(prefix) {
  try {
    const redis = await getRedis();
    if (redis) {
      // Use SCAN to avoid blocking with KEYS
      const keys = await scanKeys(redis, `${prefix}*`);
      if (keys.length) await redis.del(...keys);
    } else {
      // Memory fallback
      for (const key of _memStore.keys()) {
        if (key.startsWith(prefix)) _memStore.delete(key);
      }
    }
  } catch (err) {
    logger.error('Cache invalidatePrefix error:', err);
  }
}

// ── Standard cache TTLs (seconds) ────────────────────────────────────────────
const TTL = {
  EXCHANGE_RATES: 60 * 60, // 1 hour  — currency rates
  MARKET_DATA: 15 * 60, // 15 min  — stock/index quotes
  AI_INSIGHTS: 30 * 60, // 30 min  — Groq insights per user
  HEALTH_SCORE: 60 * 60, // 1 hour  — health score
  MONTHLY_REPORT: 10 * 60, // 10 min  — monthly analytics
  USER_SUMMARY: 5 * 60, // 5 min   — dashboard summary
};

// ── Internal helpers ──────────────────────────────────────────────────────────

async function scanKeys(redis, pattern) {
  const keys = [];
  let cursor = '0';
  do {
    const [nextCursor, batch] = await redis.scan(cursor, 'MATCH', pattern, 'COUNT', 100);
    cursor = nextCursor;
    keys.push(...batch);
  } while (cursor !== '0');
  return keys;
}

/**
 * Graceful shutdown: close Redis connection.
 */
async function close() {
  if (_redis) {
    await _redis.quit();
    _redis = null;
  }
}

module.exports = { get, set, del, remember, invalidatePrefix, close, TTL };
