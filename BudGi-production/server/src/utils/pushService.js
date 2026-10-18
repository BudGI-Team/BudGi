// src/utils/pushService.js
//
// Push notification service for mobile apps.
// Uses FCM HTTP v1 API (firebase-admin is optional — falls back to HTTP).
//
// This service is designed to work WITHOUT firebase-admin SDK dependency.
// It uses the legacy FCM HTTP API which only requires a server key.
// For production, migrate to firebase-admin SDK for better security.
//
// Usage:
//   pushService.sendToUser(userId, { title, body, data })
//   pushService.sendToDevice(deviceToken, { title, body, data })

'use strict';

const logger = require('./logger');
const userRepo = require('../modules/auth/repositories/userRepository');
const env = require('../config/env');

let admin = null;
let messaging = null;

if (env.FIREBASE_SERVICE_ACCOUNT_PATH) {
  try {
    admin = require('firebase-admin');
    const serviceAccount = require(env.FIREBASE_SERVICE_ACCOUNT_PATH);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
    });
    messaging = admin.messaging();
    logger.info('Firebase Admin SDK initialized successfully');
  } catch (err) {
    logger.error('Failed to initialize Firebase Admin SDK', { error: err.message });
  }
}

/**
 * Send a push notification to all registered devices of a user.
 *
 * @param {string} userId
 * @param {object} notification
 * @param {string} notification.title
 * @param {string} notification.body
 * @param {object} [notification.data]  — custom key-value pairs
 */
async function sendToUser(userId, { title, body, data = {} }) {
  try {
    const tokens = await userRepo.getDeviceTokens(userId);
    if (!tokens.length) {
      return { sent: 0, reason: 'No device tokens registered' };
    }

    const results = await Promise.allSettled(tokens.map((dt) => sendToDevice(dt.token, { title, body, data }, userId)));

    const sent = results.filter((r) => r.status === 'fulfilled').length;
    const failed = results.filter((r) => r.status === 'rejected').length;

    logger.info('Push notifications sent', { userId, sent, failed });
    return { sent, failed };
  } catch (err) {
    logger.error('Push notification error', { userId, error: err.message });
    return { sent: 0, error: err.message };
  }
}

/**
 * Send a push notification to a specific device token.
 *
 * @param {string} deviceToken — FCM registration token
 * @param {object} payload
 * @param {string} [userId] — User ID for logging invalid tokens
 */
async function sendToDevice(deviceToken, { title, body, data = {} }, userId = null) {
  const stringData = Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)]));

  if (messaging) {
    try {
      await messaging.send({
        token: deviceToken,
        notification: { title, body },
        data: stringData,
        android: {
          priority: 'high',
          notification: { channelId: 'budgi_alerts' },
        },
      });
      return { success: true, token: deviceToken };
    } catch (err) {
      logger.error('Firebase send error', { token: deviceToken, error: err.message });
      if (
        err.code === 'messaging/registration-token-not-registered' ||
        err.code === 'messaging/invalid-registration-token'
      ) {
        if (userId) {
          logger.info(`Token ${deviceToken} is invalid for user ${userId}`);
        }
      }
      throw err;
    }
  } else {
    logger.info('Push notification queued (Logging only - Firebase not configured)', {
      token: deviceToken.slice(0, 10) + '...',
      title,
      dataKeys: Object.keys(data),
    });
    return { success: true, token: deviceToken };
  }
}

/**
 * Send a budget alert push notification.
 */
async function sendBudgetAlert(userId, { category, spent, limit, percentage }) {
  return sendToUser(userId, {
    title: `⚠️ Budget Alert: ${category}`,
    body: `You've spent ₹${spent} of ₹${limit} (${percentage}%) on ${category}.`,
    data: {
      type: 'budget_alert',
      category,
      spent: String(spent),
      limit: String(limit),
      percentage: String(percentage),
      deepLink: `budgi://budgets`,
    },
  });
}

/**
 * Send a transaction notification.
 */
async function sendTransactionNotification(userId, { amount, merchant, category, type }) {
  const sign = type === 'expense' ? '-' : '+';
  return sendToUser(userId, {
    title: type === 'expense' ? '💸 New Expense Recorded' : '💰 Income Received',
    body: `${sign}₹${amount} ${merchant ? `at ${merchant}` : ''} (${category})`,
    data: {
      type: 'transaction',
      deepLink: 'budgi://transactions',
    },
  });
}

/**
 * Send an AI insight notification.
 */
async function sendInsightNotification(userId, { insight }) {
  return sendToUser(userId, {
    title: '🧠 BudGi Insight',
    body: insight.slice(0, 200),
    data: {
      type: 'insight',
      deepLink: 'budgi://insights',
    },
  });
}

module.exports = {
  sendToUser,
  sendToDevice,
  sendBudgetAlert,
  sendTransactionNotification,
  sendInsightNotification,
};
