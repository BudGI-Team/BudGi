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

// DELETE /api/auth/account — permanently delete user account + all data (Google Play Policy Compliant)
const deleteAccount = asyncHandler(async (req, res) => {
  const userId = req.user.id;
  const User = require('../../../models/User');
  const Transaction = require('../../../models/Transaction');
  const Budget = require('../../../models/Budget');
  const Goal = require('../../../models/Goal');
  const Alert = require('../../../models/Alert');
  const ChatMessage = require('../../../models/ChatMessage');
  const UserPreference = require('../../../models/UserPreference');
  const MerchantCategoryMapping = require('../../../models/MerchantCategoryMapping');

  // Cascade delete all user data across all collections
  const deletions = [
    Transaction.deleteMany({ userId }),
    Budget.deleteMany({ userId }),
    Goal.deleteMany({ userId }),
    Alert.deleteMany({ userId }),
    ChatMessage.deleteMany({ userId }),
    UserPreference.deleteMany({ userId }),
    MerchantCategoryMapping.deleteMany({ userId }),
  ];

  try {
    const Space = require('../../spaces/models/Space');
    if (Space) {
      deletions.push(Space.deleteMany({ createdBy: userId }));
      deletions.push(Space.updateMany({ 'members.userId': userId }, { $pull: { members: { userId } } }));
    }
  } catch (_) {}

  await Promise.all(deletions);
  await User.findByIdAndDelete(userId);

  return ok(res, {}, 'Account and all associated data permanently deleted');
});

module.exports = {
  register,
  login,
  googleLogin,
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
