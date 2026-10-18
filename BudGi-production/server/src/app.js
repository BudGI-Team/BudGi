// src/app.js
//
// Application factory — middleware stack + routes.
// Nothing in this file crashes the server. Every layer is ordered deliberately:
//
//   Security headers
//   CORS
//   Rate limiting
//   Request ID + logging
//   Body parsing + sanitization
//   Compression (gzip)
//   Routes (v1 + legacy)
//   Deep linking (Android App Links)
//   404 handler
//   Global error handler           ← catches everything above

'use strict';

const express = require('express');
const morgan = require('morgan');
const compression = require('compression');
const path = require('path');

// ── Config & utilities ────────────────────────────────────────────────────────
const env = require('./config/env'); // validates on require
const logger = require('./utils/logger');

// ── Middleware ────────────────────────────────────────────────────────────────
const security = require('./middleware/security');
const { general: generalLimiter, auth: authLimiter, ai: aiLimiter } = require('./middleware/rateLimiter');
const requestLogger = require('./middleware/requestLogger');
const errorHandler = require('./middleware/errorHandler');

// ── Routes ────────────────────────────────────────────────────────────────────
const authRoutes = require('./modules/auth/routes');
const transactionRoutes = require('./modules/transactions/routes');
const budgetRoutes = require('./modules/budgets/routes');
const aiRoutes = require('./modules/ai/routes');
const alertRoutes = require('./modules/alerts/routes');
const analyticsRoutes = require('./modules/analytics/routes');
const smsRoutes = require('./modules/sms/routes');
const syncRoutes = require('./modules/sync/routes');
const mobileRoutes = require('./modules/mobile/routes');
const goalRoutes = require('./modules/goals/routes');
const spaceRoutes = require('./modules/spaces/routes/spaceRoutes');

// ── Android App Links ─────────────────────────────────────────────────────────
const { getAssetLinks } = require('./modules/mobile/assetlinks');

// ─────────────────────────────────────────────────────────────────────────────

const app = express();

// ── 1. Trust proxy (required when behind Nginx / Railway / Render) ────────────
app.set('trust proxy', 1);

// ── 2. Security headers ───────────────────────────────────────────────────────
app.use(security.helmet());

// ── 3. CORS — must be before rate limiters so OPTIONS preflight is handled ────
app.use(security.cors());
app.options('*', security.cors()); // explicit preflight for all routes

// ── 4. Global rate limiter ────────────────────────────────────────────────────
app.use(generalLimiter);

// ── 5. Request ID + structured logging ───────────────────────────────────────
app.use(requestLogger);

// Register morgan tokens and use morgan as a secondary HTTP log stream
requestLogger.morganTokens(morgan);
const morganFormat = env.isDev
  ? ':method :url :status :response-time ms — :request-id'
  : ':remote-addr :method :url :status :response-time ms :user-id :request-id';

app.use(
  morgan(morganFormat, {
    stream: logger.stream,
    skip: (req) => req.path === '/health',
  }),
);

// ── 6. Body parsing ───────────────────────────────────────────────────────────
app.use(express.json({ limit: '2mb' })); // increased for SMS batch imports
app.use(express.urlencoded({ extended: true, limit: '2mb' }));

// ── 7. Body sanitization (prototype pollution protection) ─────────────────────
app.use(security.sanitize);

// ── 8. Response compression (gzip) — critical for mobile bandwidth ────────────
app.use(
  compression({
    level: 6,
    threshold: 1024, // only compress responses > 1KB
    filter: (req, res) => {
      if (req.headers['x-no-compression']) return false;
      return compression.filter(req, res);
    },
  }),
);

// ── 8.5 Root welcome endpoint ────────────────────────────────────────────────
app.get('/', (_req, res) => {
  res.json({
    success: true,
    message: 'BudGi API engine is running',
    version: '3.0.0',
    status: 'healthy',
  });
});

// ── 9. Health check — no auth, no rate limit ─────────────────────────────────
app.get('/health', (_req, res) => {
  res.json({
    success: true,
    message: 'OK',
    data: {
      status: 'healthy',
      version: process.env.npm_package_version ?? '3.0.0',
      env: env.NODE_ENV,
      uptime: Math.round(process.uptime()),
      timestamp: new Date().toISOString(),
      apiVersions: ['v1'],
    },
  });
});

// ── 10. Android App Links — Digital Asset Links ───────────────────────────────
// Served at /.well-known/assetlinks.json for Android deep link verification
app.get('/.well-known/assetlinks.json', (_req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.json(getAssetLinks(env.ANDROID_PACKAGE_NAME, env.ANDROID_CERT_FINGERPRINTS));
});

// ── 11. API Routes — versioned (v1) + legacy aliases ──────────────────────────

app.use('/uploads', express.static(path.join(__dirname, '../public/uploads')));

// V1 routes (canonical — mobile apps should use these)
app.use('/api/v1/auth', authLimiter, authRoutes);
app.use('/api/v1/transactions', transactionRoutes);
app.use('/api/v1/budgets', budgetRoutes);
app.use('/api/v1/ai', aiLimiter, aiRoutes);
app.use('/api/v1/alerts', alertRoutes);
app.use('/api/v1/analytics', analyticsRoutes);
app.use('/api/v1/sms', aiLimiter, smsRoutes); // rate-limited (AI categorization)
app.use('/api/v1/sync', syncRoutes);
app.use('/api/v1/mobile', mobileRoutes);
app.use('/api/v1/goals', goalRoutes);
app.use('/api/v1/spaces', spaceRoutes);

// Legacy routes (backward compat — web clients still use /api/xxx)
app.use('/api/auth', authLimiter, authRoutes);
app.use('/api/transactions', transactionRoutes);
app.use('/api/budgets', budgetRoutes);
app.use('/api/ai', aiLimiter, aiRoutes);
app.use('/api/alerts', alertRoutes);
app.use('/api/analytics', analyticsRoutes);
app.use('/api/sms', aiLimiter, smsRoutes);
app.use('/api/sync', syncRoutes);
app.use('/api/mobile', mobileRoutes);
app.use('/api/goals', goalRoutes);
app.use('/api/spaces', spaceRoutes);

// ── 12. 404 — unknown routes ──────────────────────────────────────────────────
app.use((req, res) => {
  logger.warn('404 Not Found', { method: req.method, path: req.path, requestId: req.requestId });
  res.status(404).json({
    success: false,
    message: `Cannot ${req.method} ${req.path}`,
    data: null,
  });
});

// ── 13. Global error handler — MUST be last ───────────────────────────────────
app.use(errorHandler);

module.exports = app;
