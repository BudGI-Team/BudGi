// __tests__/setup.js
// Jest test setup — configures environment for API integration tests.

'use strict';

// Set test environment variables before anything else
process.env.NODE_ENV = 'test';
process.env.MONGO_URI = process.env.MONGO_URI || 'mongodb://localhost:27017/budgi_test';
process.env.JWT_SECRET = 'test-secret-key-that-is-at-least-32-characters-long';
process.env.JWT_EXPIRES_IN = '1h';
process.env.CLIENT_ORIGIN = 'http://localhost:5173';
process.env.LOG_LEVEL = 'error'; // suppress log noise during tests

const mongoose = require('mongoose');

// ── Connect before all tests ──────────────────────────────────────────────────
beforeAll(async () => {
  // Only connect if not already connected
  if (mongoose.connection.readyState === 0) {
    await mongoose.connect(process.env.MONGO_URI);
  }
});

// ── Clean up collections after each test ──────────────────────────────────────
afterEach(async () => {
  const collections = mongoose.connection.collections;
  for (const key in collections) {
    await collections[key].deleteMany({});
  }
});

// ── Disconnect after all tests ────────────────────────────────────────────────
afterAll(async () => {
  await mongoose.connection.dropDatabase();
  await mongoose.connection.close();
});
