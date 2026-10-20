// modules/auth/controllers/authController.js
// req/res ONLY — all logic in authService

'use strict';

const authService = require('../services/authService');
const { ok, created, asyncHandler } = require('../../../utils/response');

const register = asyncHandler(async (req, res) => {
  const result = await authService.register(req.body);
  return created(res, result, 'Account created');
});

const login = asyncHandler(async (req, res) => {
  const result = await authService.login(req.body);
  return ok(res, result, 'Login successful');
});

const googleLogin = asyncHandler(async (req, res) => {
  const result = await authService.googleLogin(req.body.idToken || req.body.token);
  return ok(res, result, 'Google login successful');
});

const sendPhoneOtp = asyncHandler(async (req, res) => {
  const preAuthToken = req.headers.authorization?.replace(/^Bearer\s+/i, '') || req.body.preAuthToken;
  const result = await authService.sendPhoneOtp({
    preAuthToken,
    phone: req.body.phone,
  });
  return ok(res, result, result.message);
});

const verifyPhoneOtp = asyncHandler(async (req, res) => {
  const preAuthToken = req.headers.authorization?.replace(/^Bearer\s+/i, '') || req.body.preAuthToken;
  const result = await authService.verifyPhoneOtp({
    preAuthToken,
    phone: req.body.phone,
    otp: req.body.otp,
  });
  return ok(res, result, 'Phone verified and authenticated successfully');
});

const phoneLoginRequest = asyncHandler(async (req, res) => {
  const result = await authService.phoneLoginRequest({ phone: req.body.phone });
  return ok(res, result, result.message);
});

const phoneLoginVerify = asyncHandler(async (req, res) => {
  const result = await authService.phoneLoginVerify({ phone: req.body.phone, otp: req.body.otp });
  return ok(res, result, 'Phone verified and authenticated successfully');
});

const phoneRegisterRequest = asyncHandler(async (req, res) => {
  const result = await authService.phoneRegisterRequest({
    phone: req.body.phone,
    name: req.body.name,
    username: req.body.username,
  });
  return ok(res, result, result.message);
});

const phoneRegisterVerify = asyncHandler(async (req, res) => {
  const result = await authService.phoneRegisterVerify({
    phone: req.body.phone,
    otp: req.body.otp,
    name: req.body.name,
    username: req.body.username,
  });
  return ok(res, result, 'Phone registered and account created successfully');
});

const firebasePhoneLogin = asyncHandler(async (req, res) => {
  const result = await authService.firebasePhoneLogin({ idToken: req.body.idToken });
  return ok(res, result, 'Firebase phone login successful');
});

const firebasePhoneRegister = asyncHandler(async (req, res) => {
  const result = await authService.firebasePhoneRegister({
    idToken: req.body.idToken,
    name: req.body.name,
    username: req.body.username,
  });
  return ok(res, result, 'Firebase phone registration successful');
});

// POST /api/auth/refresh — exchange refresh token for new token pair
const refresh = asyncHandler(async (req, res) => {
  const result = await authService.refreshAccessToken(req.body.refreshToken);
  return ok(res, result, 'Token refreshed');
});

// POST /api/auth/logout — invalidate refresh token
const logout = asyncHandler(async (req, res) => {
  await authService.logout(req.user.id);
  return ok(res, {}, 'Logged out');
});

const getMe = asyncHandler(async (req, res) => {
  const user = await authService.getProfile(req.user.id);
  return ok(res, { user });
});

const updateProfile = asyncHandler(async (req, res) => {
  const user = await authService.updateProfile(req.user.id, req.body);
  return ok(res, { user }, 'Profile updated');
});

const checkUsername = asyncHandler(async (req, res) => {
  const result = await authService.checkUsername(req.query.username);
  return ok(res, result);
});

const uploadProfilePicture = asyncHandler(async (req, res) => {
  if (!req.file) {
    throw new Error('No file uploaded');
  }

  // Construct public URL
  const fileUrl = `/uploads/${req.file.filename}`;

  // Save to DB
  const user = await authService.updateProfile(req.user.id, { profileImage: fileUrl });

  return ok(res, { user, profileImage: fileUrl }, 'Profile picture uploaded');
});

// POST /api/auth/forgot-password — send reset link
const forgotPassword = asyncHandler(async (req, res) => {
  const result = await authService.forgotPassword(req.body.email);
  return ok(res, result);
});

// POST /api/auth/reset-password — validate token + set new password
const resetPassword = asyncHandler(async (req, res) => {
  const result = await authService.resetPassword(req.body.token, req.body.password);
  return ok(res, result);
});

// POST /api/auth/verify-email — verify email with token
const verifyEmail = asyncHandler(async (req, res) => {
  const result = await authService.verifyEmail(req.body.token);
  return ok(res, result, 'Email verified');
});

// POST /api/auth/resend-verification — resend verification email
const resendVerification = asyncHandler(async (req, res) => {
  const result = await authService.resendVerification(req.body.email);
  return ok(res, result);
});

// POST /api/auth/device-token — register FCM token for push notifications
const registerDeviceToken = asyncHandler(async (req, res) => {
  const result = await authService.registerDeviceToken(req.user.id, req.body);
  return ok(res, result);
});

// DELETE /api/auth/device-token — unregister FCM token
const removeDeviceToken = asyncHandler(async (req, res) => {
  const result = await authService.removeDeviceToken(req.user.id, req.body.token);
  return ok(res, result);
});

// DELETE /api/v1/auth/account & /api/v1/auth/me — permanently delete user account + all data from MongoDB
const deleteAccount = asyncHandler(async (req, res) => {
  const userId = req.user.id;
  const mongoose = require('mongoose');
  const path = require('path');
  const fs = require('fs');
  const logger = require('../../../utils/logger');
  const cache = require('../../../utils/cacheService');

  const User = require('../../../models/User');
  const Transaction = require('../../../models/Transaction');
  const Budget = require('../../../models/Budget');
  const Goal = require('../../../models/Goal');
  const Alert = require('../../../models/Alert');
  const ChatMessage = require('../../../models/ChatMessage');
  const UserPreference = require('../../../models/UserPreference');
  const MerchantCategoryMapping = require('../../../models/MerchantCategoryMapping');

  // Match both ObjectId and String forms to guarantee complete cascade removal
  const userObjectId = mongoose.Types.ObjectId.isValid(userId) ? new mongoose.Types.ObjectId(userId) : null;
  const userFilter = userObjectId ? { $in: [userId, userObjectId] } : userId;

  logger.info(`[DELETE ACCOUNT] Initiating permanent MongoDB cascade wipe for user ${userId}`);

  // 1. Wipe uploaded profile pictures from disk
  try {
    const userDoc = await User.findById(userId);
    if (userDoc?.profileImage && userDoc.profileImage.startsWith('/uploads/')) {
      const filePath = path.join(__dirname, '../../../../public', userDoc.profileImage);
      if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
      }
    }
  } catch (fileErr) {
    logger.warn('Error wiping user avatar file:', { error: fileErr.message });
  }

  // 2. Cascade delete all documents across all user-owned MongoDB collections
  const deletions = [
    Transaction.deleteMany({ userId: userFilter }),
    Budget.deleteMany({ userId: userFilter }),
    Goal.deleteMany({ userId: userFilter }),
    Alert.deleteMany({ userId: userFilter }),
    ChatMessage.deleteMany({ userId: userFilter }),
    UserPreference.deleteMany({ userId: userFilter }),
    MerchantCategoryMapping.deleteMany({ userId: userFilter }),
  ];

  // 3. Shared Spaces cleanup (delete sole-member spaces, remove member from others)
  try {
    const Space = require('../../spaces/models/Space');
    if (Space) {
      const spaces = await Space.find({ 'members.userId': userFilter });
      for (const space of spaces) {
        if (!space.members || space.members.length <= 1) {
          deletions.push(Space.findByIdAndDelete(space._id));
        } else {
          deletions.push(
            Space.findByIdAndUpdate(space._id, {
              $pull: { members: { userId: userFilter } },
            })
          );
        }
      }
    }
  } catch (spaceErr) {
    logger.warn('Error wiping space memberships:', { error: spaceErr.message });
  }

  // Execute all deletions in parallel
  await Promise.allSettled(deletions);

  // 4. Delete the User record from MongoDB
  await User.findByIdAndDelete(userId);
  if (userObjectId) {
    await User.deleteMany({ _id: userFilter });
  }

  // 5. Invalidate all Redis/in-memory caches for this user
  try {
    await cache.del(`user:${userId}`);
    await cache.del(`token:${userId}`);
    await cache.del(`analytics:${userId}`);
    await cache.delPattern?.(`*${userId}*`);
  } catch (_) {}

  logger.info(`[DELETE ACCOUNT] Successfully deleted user ${userId} and all associated MongoDB records.`);

  return ok(res, {}, 'Account and all associated MongoDB data permanently deleted');
});

module.exports = {
  register,
  login,
  googleLogin,
  sendPhoneOtp,
  verifyPhoneOtp,
  phoneLoginRequest,
  phoneLoginVerify,
  phoneRegisterRequest,
  phoneRegisterVerify,
  firebasePhoneLogin,
  firebasePhoneRegister,
  refresh,
  logout,
  getMe,
  updateProfile,
  checkUsername,
  forgotPassword,
  resetPassword,
  verifyEmail,
  resendVerification,
  registerDeviceToken,
  removeDeviceToken,
  deleteAccount,
  uploadProfilePicture,
};
