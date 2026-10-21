// modules/auth/repositories/userRepository.js
// DB queries ONLY

'use strict';

const User = require('../../../models/User');

async function findByEmail(email) {
  return User.findOne({ email: email.toLowerCase() });
}

async function findByPhone(phone) {
  return User.findOne({ phone: phone.trim() });
}

async function findById(id) {
  return User.findById(id).lean();
}

async function create(data) {
  return User.create(data);
}

async function updateById(id, updates) {
  return User.findByIdAndUpdate(id, updates, { new: true }).lean();
}

/**
 * Find user by refresh token (supporting multi-device sessions and legacy single-token).
 */
async function findByRefreshToken(tokenHash) {
  return User.findOne({
    $or: [
      { 'sessions.tokenHash': tokenHash },
      { refreshToken: tokenHash },
    ],
  }).select('+refreshToken +refreshTokenExpiry +sessions');
}

/**
 * Find user by password reset token.
 */
async function findByPasswordResetToken(token) {
  return User.findOne({ passwordResetToken: token }).select('+passwordResetToken +passwordResetExpiry');
}

/**
 * Find user by email verification token.
 */
async function findByEmailVerifyToken(token) {
  return User.findOne({ emailVerifyToken: token }).select('+emailVerifyToken +emailVerifyExpiry');
}

/**
 * Get device tokens for a user (for push notification service).
 */
async function getDeviceTokens(userId) {
  const user = await User.findById(userId).select('deviceTokens').lean();
  return user?.deviceTokens || [];
}

/**
 * Update last sync timestamp.
 */
async function updateLastSync(userId) {
  return User.findByIdAndUpdate(userId, { lastSyncAt: new Date() }, { new: true }).lean();
}

/**
 * Find user by username.
 */
async function findByUsername(username) {
  return User.findOne({ username: username.toLowerCase().trim() });
}

module.exports = {
  findByEmail,
  findByPhone,
  findByUsername,
  findById,
  create,
  updateById,
  findByRefreshToken,
  findByPasswordResetToken,
  findByEmailVerifyToken,
  getDeviceTokens,
  updateLastSync,
};
