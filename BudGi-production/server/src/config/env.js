// src/config/env.js
// Single source of truth for all environment variables.
// Validates at require()-time — missing required vars exit the process immediately.

'use strict';

require('dotenv').config();

const missing = [];
const warnings = [];

function required(name, description) {
  const value = process.env[name]?.trim();
  if (!value) {
    missing.push(`  ${name.padEnd(26)} — ${description}`);
    return '';
  }
  return value;
}

function optional(name, fallback = '') {
  return process.env[name]?.trim() ?? fallback;
}

function optionalInt(name, fallback) {
  const raw = process.env[name];
  if (!raw) return fallback;
  const n = parseInt(raw, 10);
  if (isNaN(n)) {
    warnings.push(`  ${name}: "${raw}" is not a valid integer — using default ${fallback}`);
    return fallback;
  }
  return n;
}

function warnIfMissing(name, description) {
  if (!process.env[name]?.trim()) {
    warnings.push(`  ${name.padEnd(26)} — ${description} (feature disabled)`);
  }
  return optional(name);
}

const NODE_ENV = optional('NODE_ENV', 'development');
const isProd = NODE_ENV === 'production';
const isDev = NODE_ENV === 'development';
const isTest = NODE_ENV === 'test';

const config = {
  NODE_ENV,
  isProd,
  isDev,
  isTest,

  PORT: optionalInt('PORT', 5000),
  LOG_LEVEL: optional('LOG_LEVEL', isProd ? 'http' : 'debug'),

  MONGO_URI: required('MONGO_URI', 'MongoDB connection string'),
  JWT_SECRET: required('JWT_SECRET', 'Secret key for signing JWT tokens (min 32 chars)'),
  JWT_EXPIRES_IN: optional('JWT_EXPIRES_IN', '7d'),

  CLIENT_ORIGIN: optional('CLIENT_ORIGIN', 'http://localhost:5173'),

  RATE_LIMIT_WINDOW_MS: optionalInt('RATE_LIMIT_WINDOW_MS', 15 * 60 * 1000),
  RATE_LIMIT_MAX: optionalInt('RATE_LIMIT_MAX', 200),
  RATE_LIMIT_AUTH_MAX: optionalInt('RATE_LIMIT_AUTH_MAX', 20),
  RATE_LIMIT_AI_MAX: optionalInt('RATE_LIMIT_AI_MAX', 30),

  GROQ_API_KEY: warnIfMissing('GROQ_API_KEY', 'AI insights + categorisation'),
  EXCHANGE_RATE_API_KEY: warnIfMissing('EXCHANGE_RATE_API_KEY', 'Currency conversion'),
  ALPHA_VANTAGE_API_KEY: warnIfMissing('ALPHA_VANTAGE_API_KEY', 'Market data'),
  REDIS_URL: optional('REDIS_URL', ''),
  AI_SERVICE_URL: optional('AI_SERVICE_URL', 'http://localhost:8000'),

  // ── Mobile / Android ──────────────────────────────────────────────────────
  ANDROID_PACKAGE_NAME: optional('ANDROID_PACKAGE_NAME', 'com.budgi.app'),
  ANDROID_CERT_FINGERPRINTS: optional('ANDROID_CERT_FINGERPRINTS', '').split(',').filter(Boolean),
  RATE_LIMIT_SMS_MAX: optionalInt('RATE_LIMIT_SMS_MAX', 50),
  FIREBASE_SERVICE_ACCOUNT_PATH: optional('FIREBASE_SERVICE_ACCOUNT_PATH', ''),

  // ── Dual-Anchor Identity & FinTech Auth ───────────────────────────────────
  GOOGLE_CLIENT_ID: optional('GOOGLE_CLIENT_ID', ''),
  OTP_PEPPER: optional('OTP_PEPPER', 'BudGiFinTechDevPepperSecret123456789!'),
  FIREBASE_API_KEY: optional('FIREBASE_API_KEY', ''),
  TEXTBEE_API_KEY: optional('TEXTBEE_API_KEY', ''),
  TEXTBEE_DEVICE_ID: optional('TEXTBEE_DEVICE_ID', ''),
};

// JWT strength check
if (config.JWT_SECRET && config.JWT_SECRET.length < 32) {
  warnings.push(
    `  JWT_SECRET                 — too short (${config.JWT_SECRET.length} chars). Use ≥32 chars in production.`,
  );
}

// Fail fast
if (missing.length > 0) {
  process.stderr.write(
    '\n✖  Server cannot start — required environment variables are missing:\n\n' +
      missing.join('\n') +
      '\n\nCopy .env.example to .env and fill in the values.\n\n',
  );
  process.exit(1);
}

if (warnings.length > 0 && !isTest) {
  process.stderr.write('\n⚠  Environment warnings:\n\n' + warnings.join('\n') + '\n\n');
}

module.exports = config;
