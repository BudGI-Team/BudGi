// src/server.js — Process entry point
//
// Responsibilities:
//   1. Boot sequence:  env → DB → HTTP server → WebSocket → cron
//   2. Graceful shutdown on SIGTERM / SIGINT
//   3. All boot errors logged + process exits with code 1

'use strict';

const http = require('http');
const env = require('./config/env'); // validates env vars first
const logger = require('./utils/logger');
const app = require('./app');
const { connectDB } = require('./config/db');
const { startCronJobs } = require('./jobs/cronJobs');
const socketService = require('./utils/socketService');

let httpServer;

async function boot() {
  // ── 1. Database ─────────────────────────────────────────────────────────────
  logger.info('Connecting to MongoDB...');
  await connectDB();
  logger.info('MongoDB connected');

  // ── 2. HTTP server ───────────────────────────────────────────────────────────
  httpServer = http.createServer(app);

  // ── 3. WebSocket ─────────────────────────────────────────────────────────────
  socketService.attach(httpServer);

  // ── 4. Listen ────────────────────────────────────────────────────────────────
  await new Promise((resolve, reject) => {
    httpServer.listen(env.PORT, '0.0.0.0', resolve);
    httpServer.once('error', reject);
  });

  logger.info('Server started', {
    port: env.PORT,
    env: env.NODE_ENV,
    pid: process.pid,
    health: `http://localhost:${env.PORT}/health`,
  });

  // ── 5. Cron jobs ─────────────────────────────────────────────────────────────
  if (!env.isTest) {
    startCronJobs();
    logger.info('Cron jobs started');
  }
}

// ── Graceful shutdown ─────────────────────────────────────────────────────────

async function shutdown(signal) {
  logger.info(`${signal} received — shutting down gracefully`);

  // Stop accepting new connections
  httpServer?.close(async () => {
    logger.info('HTTP server closed');

    try {
      // Close Redis / cache
      const { close: closeCache } = require('./utils/cacheService');
      await closeCache();
      logger.info('Cache closed');
    } catch {
      /* redis is optional */
    }

    try {
      await require('mongoose').connection.close();
      logger.info('MongoDB connection closed');
    } catch (err) {
      logger.error('Error closing MongoDB', err);
    }

    logger.info('Shutdown complete');
    process.exit(0);
  });

  // Force exit if graceful shutdown takes too long
  setTimeout(() => {
    logger.error('Forced shutdown after timeout');
    process.exit(1);
  }, 10_000).unref();
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

// ── Boot ──────────────────────────────────────────────────────────────────────

boot().catch((err) => {
  logger.error('Failed to start server', err);
  process.exit(1);
});
