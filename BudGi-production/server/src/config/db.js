// src/config/db.js
// MongoDB connection with retry logic and graceful shutdown

'use strict';

const mongoose = require('mongoose');
const env = require('./env');
const logger = require('../utils/logger');

const MAX_RETRIES = 5;
const RETRY_DELAY_MS = 3000;

async function connectDB(retries = MAX_RETRIES) {
  try {
    await mongoose.connect(env.MONGO_URI, {
      maxPoolSize: 10,
      serverSelectionTimeoutMS: 5000,
    });
    const safeUri = env.MONGO_URI.replace(/\/\/([^:]+:[^@]+)@/, '//***:***@');
    logger.info('MongoDB connected', { uri: safeUri });
  } catch (err) {
    if (retries > 0) {
      logger.warn(`MongoDB connection failed — retrying in ${RETRY_DELAY_MS}ms`, { retriesLeft: retries });
      await new Promise((r) => setTimeout(r, RETRY_DELAY_MS));
      return connectDB(retries - 1);
    }
    logger.error('MongoDB: all retries exhausted', err);
    process.exit(1);
  }
}

// Mongoose connection event logging
mongoose.connection.on('disconnected', () => logger.warn('MongoDB disconnected'));
mongoose.connection.on('reconnected', () => logger.info('MongoDB reconnected'));
mongoose.connection.on('error', (err) => logger.error('MongoDB error', err));

module.exports = { connectDB };
