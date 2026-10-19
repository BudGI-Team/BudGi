// modules/auth/services/authService.js
// ALL auth business logic — hashing, JWT signing, refresh tokens, device tokens,
// password reset, email verification

'use strict';

const crypto = require('crypto');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const env = require('../../../config/env');
const userRepo = require('../repositories/userRepository');
const emailSvc = require('../../../utils/emailService');

const SALT_ROUNDS = 12;
const REFRESH_TOKEN_BYTES = 48;
const REFRESH_TOKEN_TTL = 30 * 24 * 60 * 60 * 1000; // 30 days
const MAX_DEVICE_TOKENS = 5;
const RESET_TOKEN_TTL = 60 * 60 * 1000; // 1 hour
const VERIFY_TOKEN_TTL = 24 * 60 * 60 * 1000; // 24 hours

// ─────────────────────────────────────────────────────────────────────────────
// Token generation
// ─────────────────────────────────────────────────────────────────────────────

function signAccessToken(user) {
  return jwt.sign({ id: user._id, email: user.email }, env.JWT_SECRET, { expiresIn: env.JWT_EXPIRES_IN });
}

function generateRefreshToken() {
  return crypto.randomBytes(REFRESH_TOKEN_BYTES).toString('hex');
}

function generateHexToken(bytes = 32) {
  return crypto.randomBytes(bytes).toString('hex');
}

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

async function saveRefreshToken(userId, token) {
  await userRepo.updateById(userId, {
    refreshToken: hashToken(token),
    refreshTokenExpiry: new Date(Date.now() + REFRESH_TOKEN_TTL),
  });
}

function buildTokenPair(user) {
  const accessToken = signAccessToken(user);
  const refreshToken = generateRefreshToken();
  return { accessToken, refreshToken };
}

// ─────────────────────────────────────────────────────────────────────────────
// Unique Username Generator
// ─────────────────────────────────────────────────────────────────────────────

async function generateUniqueUsername(base) {
  const cleanBase = (base || 'user')
    .toLowerCase()
    .replace(/^@/, '')
    .replace(/[^a-z0-9_]/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 18) || 'user';

  const prefix = cleanBase.length >= 3 ? cleanBase : `${cleanBase}_bg`;
  let candidate = prefix;
  let attempts = 0;

  while (await userRepo.findByUsername(candidate)) {
    attempts++;
    const suffix = Math.floor(1000 + Math.random() * 9000);
    candidate = `${prefix.slice(0, 20)}_${suffix}`;
    if (attempts > 20) {
      candidate = `user_${Date.now().toString(36)}`;
      break;
    }
  }
  return candidate;
}

// ─────────────────────────────────────────────────────────────────────────────
// Register
// ─────────────────────────────────────────────────────────────────────────────

async function register({ name, email, password, username, dateOfBirth, avatar, currency = 'INR', monthlyIncome = 0 }) {
  if (!name || !email || !password) {
    throw Object.assign(new Error('Name, email and password are required'), { statusCode: 400 });
  }
  if (password.length < 8) {
    throw Object.assign(new Error('Password must be at least 8 characters'), { statusCode: 400 });
  }

  const exists = await userRepo.findByEmail(email);
  if (exists) {
    throw Object.assign(new Error('Email already registered'), { statusCode: 400 });
  }

  let cleanUsername = null;
  if (username) {
    cleanUsername = username.replace(/^@/, '').toLowerCase().trim();
    if (cleanUsername.length < 3 || cleanUsername.length > 30) {
      throw Object.assign(new Error('Username must be between 3 and 30 characters'), { statusCode: 400 });
    }
    if (!/^[a-z0-9_]+$/.test(cleanUsername)) {
      throw Object.assign(new Error('Username can only contain letters, numbers, and underscores'), { statusCode: 400 });
    }
    const uExists = await userRepo.findByUsername(cleanUsername);
    if (uExists) {
      throw Object.assign(new Error('Username already taken'), { statusCode: 400 });
    }
  } else {
    cleanUsername = await generateUniqueUsername(email.split('@')[0] || name);
  }

  const hashed = await bcrypt.hash(password, SALT_ROUNDS);
  const verifyToken = generateHexToken();

  const user = await userRepo.create({
    name: name.trim(),
    email: email.toLowerCase().trim(),
    username: cleanUsername,
    dateOfBirth: dateOfBirth ? new Date(dateOfBirth) : null,
    avatar: avatar || 'zen_saver',
    password: hashed,
    currency,
    monthlyIncome: parseFloat(monthlyIncome) || 0,
    isEmailVerified: false,
    onboarded: true,
    streak: { count: 1, lastActiveDate: new Date() },
    emailVerifyToken: verifyToken,
    emailVerifyExpiry: new Date(Date.now() + VERIFY_TOKEN_TTL),
  });

  // Send verification email (non-blocking)
  const verifyUrl = `${env.CLIENT_ORIGIN}/verify-email?token=${verifyToken}`;
  const { subject, html } = emailSvc.verificationEmail(user.name, verifyUrl);
  emailSvc.sendEmail({ to: user.email, subject, html }).catch((err) => {
    console.error('[Auth] Failed to send verification email:', err.message);
  });

  const { accessToken, refreshToken } = buildTokenPair(user);
  await saveRefreshToken(user._id, refreshToken);

  return { token: accessToken, refreshToken, user: user.toSafeJSON() };
}

// ─────────────────────────────────────────────────────────────────────────────
// Verify Email
// ─────────────────────────────────────────────────────────────────────────────

async function verifyEmail(token) {
  if (!token) {
    throw Object.assign(new Error('Verification token is required'), { statusCode: 400 });
  }

  const user = await userRepo.findByEmailVerifyToken(token);
  if (!user) {
    throw Object.assign(new Error('Invalid or expired verification link'), { statusCode: 400 });
  }

  if (!user.emailVerifyExpiry || new Date() > user.emailVerifyExpiry) {
    await userRepo.updateById(user._id, { emailVerifyToken: null, emailVerifyExpiry: null });
    throw Object.assign(new Error('Verification link has expired. Request a new one.'), { statusCode: 400 });
  }

  await userRepo.updateById(user._id, {
    isEmailVerified: true,
    emailVerifyToken: null,
    emailVerifyExpiry: null,
  });

  return { message: 'Email verified successfully' };
}

// ─────────────────────────────────────────────────────────────────────────────
// Resend Verification Email
// ─────────────────────────────────────────────────────────────────────────────

async function resendVerification(email) {
  if (!email) {
    throw Object.assign(new Error('Email is required'), { statusCode: 400 });
  }

  const user = await userRepo.findByEmail(email);
  if (!user) {
    // Don't reveal whether email exists
    return { message: 'If that email is registered, a verification link has been sent.' };
  }

  if (user.isEmailVerified) {
    return { message: 'Email is already verified.' };
  }

  const verifyToken = generateHexToken();
  await userRepo.updateById(user._id, {
    emailVerifyToken: verifyToken,
    emailVerifyExpiry: new Date(Date.now() + VERIFY_TOKEN_TTL),
  });

  const verifyUrl = `${env.CLIENT_ORIGIN}/verify-email?token=${verifyToken}`;
  const { subject, html } = emailSvc.verificationEmail(user.name, verifyUrl);
  emailSvc.sendEmail({ to: user.email, subject, html }).catch((err) => {
    console.error('[Auth] Failed to resend verification email:', err.message);
  });

  return { message: 'If that email is registered, a verification link has been sent.' };
}

// ─────────────────────────────────────────────────────────────────────────────
// Login
// ─────────────────────────────────────────────────────────────────────────────

async function login({ email, password }) {
  if (!email || !password) {
    throw Object.assign(new Error('Email and password are required'), { statusCode: 400 });
  }

  const user = await userRepo.findByEmail(email);
  if (!user) {
    throw Object.assign(new Error('Invalid credentials'), { statusCode: 401 });
  }

  const match = await bcrypt.compare(password, user.password);
  if (!match) {
    throw Object.assign(new Error('Invalid credentials'), { statusCode: 401 });
  }

  const { accessToken, refreshToken } = buildTokenPair(user);
  await saveRefreshToken(user._id, refreshToken);

  return { token: accessToken, refreshToken, user: user.toSafeJSON() };
}

// ─────────────────────────────────────────────────────────────────────────────
// Google Sign-In — Verify ID token & authenticate/register user
// ─────────────────────────────────────────────────────────────────────────────

async function googleLogin(idToken) {
  if (!idToken) {
    throw Object.assign(new Error('Google ID token is required'), { statusCode: 400 });
  }

  let email, name, picture;
  let verified = false;

  // 1. Attempt verification with Firebase Admin if configured
  if (env.FIREBASE_SERVICE_ACCOUNT_PATH) {
    try {
      const admin = require('firebase-admin');
      const decoded = await admin.auth().verifyIdToken(idToken);
      email = decoded.email;
      name = decoded.name;
      picture = decoded.picture;
      verified = true;
    } catch {
      // Fall through to Google tokeninfo
    }
  }

  // 2. Google OAuth2 tokeninfo endpoint fallback
  if (!verified) {
    try {
      const res = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`);
      if (!res.ok) {
        throw new Error('Google token verification failed');
      }
      const data = await res.json();
      if (!data.email) {
        throw new Error('Email not present in Google token');
      }
      email = data.email;
      name = data.name || data.given_name || email.split('@')[0];
      picture = data.picture;
    } catch (err) {
      throw Object.assign(new Error('Invalid or expired Google credential'), { statusCode: 401 });
    }
  }

  const cleanEmail = email.toLowerCase().trim();
  let user = await userRepo.findByEmail(cleanEmail);

  if (!user) {
    // Generate secure random password for OAuth user
    const randomPassword = crypto.randomBytes(32).toString('hex');
    const hashed = await bcrypt.hash(randomPassword, SALT_ROUNDS);

    const googleUsername = await generateUniqueUsername(cleanEmail.split('@')[0] || name);

    user = await userRepo.create({
      name: (name || cleanEmail.split('@')[0]).trim(),
      email: cleanEmail,
      username: googleUsername,
      password: hashed,
      profileImage: picture || null,
      isEmailVerified: true,
      currency: 'INR',
      onboarded: false,
    });
  } else if (!user.isEmailVerified) {
    await userRepo.updateById(user._id, { isEmailVerified: true });
  }

  const { accessToken, refreshToken } = buildTokenPair(user);
  await saveRefreshToken(user._id, refreshToken);

  return { token: accessToken, refreshToken, user: user.toSafeJSON() };
}

// ─────────────────────────────────────────────────────────────────────────────
// Forgot Password — send reset link
// ─────────────────────────────────────────────────────────────────────────────

async function forgotPassword(email) {
  if (!email) {
    throw Object.assign(new Error('Email is required'), { statusCode: 400 });
  }

  const user = await userRepo.findByEmail(email);
  // Always return success message to prevent email enumeration
  const safeMsg = 'If that email is registered, a reset link has been sent.';

  if (!user) return { message: safeMsg };

  const resetToken = generateHexToken();
  await userRepo.updateById(user._id, {
    passwordResetToken: resetToken,
    passwordResetExpiry: new Date(Date.now() + RESET_TOKEN_TTL),
  });

  const resetUrl = `${env.CLIENT_ORIGIN}/reset-password?token=${resetToken}`;
  const { subject, html } = emailSvc.passwordResetEmail(user.name, resetUrl);
  emailSvc.sendEmail({ to: user.email, subject, html }).catch((err) => {
    console.error('[Auth] Failed to send password reset email:', err.message);
  });

  return { message: safeMsg };
}

// ─────────────────────────────────────────────────────────────────────────────
// Reset Password — validate token + set new password
// ─────────────────────────────────────────────────────────────────────────────

async function resetPassword(token, newPassword) {
  if (!token || !newPassword) {
    throw Object.assign(new Error('Token and new password are required'), { statusCode: 400 });
  }
  if (newPassword.length < 8) {
    throw Object.assign(new Error('Password must be at least 8 characters'), { statusCode: 400 });
  }

  const user = await userRepo.findByPasswordResetToken(token);
  if (!user) {
    throw Object.assign(new Error('Invalid or expired reset link'), { statusCode: 400 });
  }

  if (!user.passwordResetExpiry || new Date() > user.passwordResetExpiry) {
    await userRepo.updateById(user._id, { passwordResetToken: null, passwordResetExpiry: null });
    throw Object.assign(new Error('Reset link has expired. Request a new one.'), { statusCode: 400 });
  }

  const hashed = await bcrypt.hash(newPassword, SALT_ROUNDS);
  await userRepo.updateById(user._id, {
    password: hashed,
    passwordResetToken: null,
    passwordResetExpiry: null,
    // Also clear refresh tokens to force re-login on all devices
    refreshToken: null,
    refreshTokenExpiry: null,
  });

  return { message: 'Password reset successful. Please log in with your new password.' };
}

// ─────────────────────────────────────────────────────────────────────────────
// Refresh Token — exchange a valid refresh token for a new access + refresh pair
// ─────────────────────────────────────────────────────────────────────────────

async function refreshAccessToken(incomingRefreshToken) {
  if (!incomingRefreshToken) {
    throw Object.assign(new Error('Refresh token is required'), { statusCode: 400 });
  }

  const hashedToken = hashToken(incomingRefreshToken);
  const user = await userRepo.findByRefreshToken(hashedToken);
  if (!user) {
    throw Object.assign(new Error('Invalid or expired refresh token'), { statusCode: 401 });
  }

  if (!user.refreshTokenExpiry || new Date() > user.refreshTokenExpiry) {
    await userRepo.updateById(user._id, { refreshToken: null, refreshTokenExpiry: null });
    throw Object.assign(new Error('Refresh token expired — please log in again'), { statusCode: 401 });
  }

  const { accessToken, refreshToken: newRefreshToken } = buildTokenPair(user);
  await saveRefreshToken(user._id, newRefreshToken);

  return { token: accessToken, refreshToken: newRefreshToken, user: user.toSafeJSON() };
}

// ─────────────────────────────────────────────────────────────────────────────
// Logout — invalidate refresh token
// ─────────────────────────────────────────────────────────────────────────────

async function logout(userId) {
  await userRepo.updateById(userId, { refreshToken: null, refreshTokenExpiry: null });
  return { message: 'Logged out' };
}

// ─────────────────────────────────────────────────────────────────────────────
// Device Token Management (FCM push notifications)
// ─────────────────────────────────────────────────────────────────────────────

async function registerDeviceToken(userId, { token, platform = 'android' }) {
  if (!token) {
    throw Object.assign(new Error('Device token is required'), { statusCode: 400 });
  }
  if (!['android', 'ios', 'web'].includes(platform)) {
    throw Object.assign(new Error('Platform must be android, ios, or web'), { statusCode: 400 });
  }

  const user = await userRepo.findById(userId);
  if (!user) throw Object.assign(new Error('User not found'), { statusCode: 404 });

  const existing = (user.deviceTokens || []).find((dt) => dt.token === token);
  if (existing) return { message: 'Device token already registered' };

  const tokens = user.deviceTokens || [];
  if (tokens.length >= MAX_DEVICE_TOKENS) {
    tokens.sort((a, b) => new Date(a.addedAt) - new Date(b.addedAt));
    tokens.shift();
  }

  tokens.push({ token, platform, addedAt: new Date() });
  await userRepo.updateById(userId, { deviceTokens: tokens });

  return { message: 'Device token registered', count: tokens.length };
}

async function removeDeviceToken(userId, token) {
  if (!token) {
    throw Object.assign(new Error('Device token is required'), { statusCode: 400 });
  }

  const user = await userRepo.findById(userId);
  if (!user) throw Object.assign(new Error('User not found'), { statusCode: 404 });

  const filtered = (user.deviceTokens || []).filter((dt) => dt.token !== token);
  await userRepo.updateById(userId, { deviceTokens: filtered });

  return { message: 'Device token removed' };
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile
// ─────────────────────────────────────────────────────────────────────────────

async function getProfile(userId) {
  const user = await userRepo.findById(userId);
  if (!user) throw Object.assign(new Error('User not found'), { statusCode: 404 });
  delete user.password;
  return user;
}

async function checkUsername(rawUsername) {
  if (!rawUsername) {
    throw Object.assign(new Error('Username is required'), { statusCode: 400 });
  }
  const username = rawUsername.replace(/^@/, '').toLowerCase().trim();
  if (username.length < 3 || username.length > 30) {
    return { available: false, username, reason: 'Username must be between 3 and 30 characters' };
  }
  if (!/^[a-z0-9_]+$/.test(username)) {
    return { available: false, username, reason: 'Username can only contain letters, numbers, and underscores' };
  }
  const existing = await userRepo.findByUsername(username);
  return { available: !existing, username };
}

async function updateProfile(
  userId,
  { name, username, dateOfBirth, currency, monthlyIncome, financialGoal, profileImage, onboarded, mood, avatar, streak },
) {
  const updates = {};
  if (name) updates.name = name.trim();
  if (username !== undefined) {
    const cleanUsername = username ? username.replace(/^@/, '').toLowerCase().trim() : null;
    if (cleanUsername) {
      if (cleanUsername.length < 3 || cleanUsername.length > 30) {
        throw Object.assign(new Error('Username must be between 3 and 30 characters'), { statusCode: 400 });
      }
      if (!/^[a-z0-9_]+$/.test(cleanUsername)) {
        throw Object.assign(new Error('Username can only contain letters, numbers, and underscores'), { statusCode: 400 });
      }
      const existing = await userRepo.findByUsername(cleanUsername);
      if (existing && existing._id.toString() !== userId.toString()) {
        throw Object.assign(new Error('Username already taken'), { statusCode: 400 });
      }
      updates.username = cleanUsername;
    } else {
      updates.username = null;
    }
  }
  if (dateOfBirth !== undefined) {
    updates.dateOfBirth = dateOfBirth ? new Date(dateOfBirth) : null;
  }
  if (currency) updates.currency = currency;
  if (monthlyIncome != null) updates.monthlyIncome = parseFloat(monthlyIncome) || 0;
  if (financialGoal != null) updates.financialGoal = financialGoal;
  if (profileImage !== undefined) {
    updates.profileImage = profileImage === '' || profileImage === null ? null : profileImage;
  }
  if (onboarded != null) updates.onboarded = !!onboarded;
  if (mood != null) updates.mood = mood;
  if (avatar != null) updates.avatar = avatar;
  if (streak != null) updates.streak = streak;

  const user = await userRepo.updateById(userId, updates);
  delete user.password;
  return user;
}

module.exports = {
  register,
  login,
  googleLogin,
  verifyEmail,
  resendVerification,
  forgotPassword,
  resetPassword,
  refreshAccessToken,
  logout,
  registerDeviceToken,
  removeDeviceToken,
  getProfile,
  updateProfile,
  checkUsername,
};
