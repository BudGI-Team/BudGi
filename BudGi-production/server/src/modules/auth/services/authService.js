// modules/auth/services/authService.js
// ALL auth business logic — hashing, JWT signing, refresh tokens, device tokens,
// password reset, email verification

'use strict';

const crypto = require('crypto');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const env = require('../../../config/env');
const userRepo = require('../repositories/userRepository');
const User = require('../../../models/User');
const PhoneVerification = require('../../../models/PhoneVerification');
const emailSvc = require('../../../utils/emailService');
const smsService = require('./smsService');

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

const logger = require('../../../utils/logger');

async function saveRefreshToken(userId, token, { platform = 'web', deviceId = 'unknown', userAgent = '' } = {}) {
  const tokenHash = hashToken(token);
  const expiresAt = new Date(Date.now() + REFRESH_TOKEN_TTL);
  const MAX_SESSIONS = 5;

  const user = await User.findById(userId).select('+sessions +refreshToken +refreshTokenExpiry');
  if (!user) return;

  // Filter out expired sessions
  let sessions = (user.sessions || []).filter((s) => s.expiresAt && new Date() < s.expiresAt);

  // If at capacity, remove oldest
  if (sessions.length >= MAX_SESSIONS) {
    sessions.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    sessions.shift();
  }

  sessions.push({
    tokenHash,
    deviceId: deviceId || 'unknown',
    platform: ['android', 'ios', 'web'].includes(platform) ? platform : 'web',
    userAgent: (userAgent || '').slice(0, 200),
    createdAt: new Date(),
    expiresAt,
  });

  user.sessions = sessions;
  user.refreshToken = tokenHash;
  user.refreshTokenExpiry = expiresAt;

  await user.save();
}

function buildTokenPair(user) {
  const accessToken = signAccessToken(user);
  const refreshToken = generateRefreshToken();
  return { accessToken, refreshToken };
}

function issuePreAuthToken(user) {
  return jwt.sign(
    { id: user._id, email: user.email, stage: 'PHONE_VERIFICATION_REQUIRED' },
    env.JWT_SECRET,
    { expiresIn: '15m' },
  );
}

function verifyPreAuthToken(token) {
  try {
    const decoded = jwt.verify(token, env.JWT_SECRET);
    if (decoded.stage !== 'PHONE_VERIFICATION_REQUIRED') {
      throw new Error('Invalid pre-auth token stage');
    }
    return decoded;
  } catch (err) {
    throw Object.assign(new Error('Invalid or expired verification session. Please sign in again.'), { statusCode: 401 });
  }
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

  const cleanEmail = (email || '').toLowerCase().trim();
  const emailRegex = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/;
  if (!emailRegex.test(cleanEmail)) {
    throw Object.assign(new Error('Please enter a valid email address'), { statusCode: 400 });
  }

  const exists = await userRepo.findByEmail(cleanEmail);
  if (exists) {
    throw Object.assign(new Error('This email address is already registered. Please log in instead.'), { statusCode: 409 });
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
    authProvider: 'local',
    dateOfBirth: dateOfBirth ? new Date(dateOfBirth) : null,
    avatar: avatar || 'zen_saver',
    password: hashed,
    currency,
    monthlyIncome: parseFloat(monthlyIncome) || 0,
    isEmailVerified: false,
    onboarded: true,
    streak: { count: 1, lastActiveDate: new Date() },
    emailVerifyToken: hashToken(verifyToken),
    emailVerifyExpiry: new Date(Date.now() + VERIFY_TOKEN_TTL),
  });

  // Send verification email (non-blocking)
  const verifyUrl = `${env.CLIENT_ORIGIN}/verify-email?token=${verifyToken}`;
  const { subject, html } = emailSvc.verificationEmail(user.name, verifyUrl);
  emailSvc.sendEmail({ to: user.email, subject, html }).catch((err) => {
    logger.warn('[Auth] Failed to send verification email', { error: err.message });
  });

  // Dual-Anchor requirement: User must verify phone number before receiving access tokens!
  const preAuthToken = issuePreAuthToken(user);

  return {
    requiresPhoneVerification: true,
    status: 'PHONE_VERIFICATION_REQUIRED',
    preAuthToken,
    user: user.toSafeJSON(),
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Verify Email
// ─────────────────────────────────────────────────────────────────────────────

async function verifyEmail(token) {
  if (!token) {
    throw Object.assign(new Error('Verification token is required'), { statusCode: 400 });
  }

  const hashed = hashToken(token);
  let user = await userRepo.findByEmailVerifyToken(hashed);
  if (!user) {
    user = await userRepo.findByEmailVerifyToken(token);
  }
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
    emailVerifyToken: hashToken(verifyToken),
    emailVerifyExpiry: new Date(Date.now() + VERIFY_TOKEN_TTL),
  });

  const verifyUrl = `${env.CLIENT_ORIGIN}/verify-email?token=${verifyToken}`;
  const { subject, html } = emailSvc.verificationEmail(user.name, verifyUrl);
  emailSvc.sendEmail({ to: user.email, subject, html }).catch((err) => {
    logger.warn('[Auth] Failed to resend verification email', { error: err.message });
  });

  return { message: 'If that email is registered, a verification link has been sent.' };
}

// ─────────────────────────────────────────────────────────────────────────────
// Login
// ─────────────────────────────────────────────────────────────────────────────

async function login({ email, password, platform = 'web', userAgent = '' }) {
  if (!email || !password) {
    throw Object.assign(new Error('Email and password are required'), { statusCode: 400 });
  }

  const cleanEmail = email.toLowerCase().trim();
  const user = await userRepo.findByEmail(cleanEmail);
  if (!user) {
    throw Object.assign(new Error('Invalid email or password'), { statusCode: 401 });
  }

  const userDoc = await User.findById(user._id);
  if (userDoc?.lockUntil && new Date() < userDoc.lockUntil) {
    const mins = Math.max(1, Math.ceil((userDoc.lockUntil - Date.now()) / 60000));
    throw Object.assign(
      new Error(`Account temporarily locked due to repeated failed attempts. Please try again in ${mins} minute${mins > 1 ? 's' : ''}.`),
      { statusCode: 429 }
    );
  }

  const match = await bcrypt.compare(password, user.password);
  if (!match) {
    if (userDoc) {
      userDoc.loginAttempts = (userDoc.loginAttempts || 0) + 1;
      if (userDoc.loginAttempts >= 5) {
        userDoc.lockUntil = new Date(Date.now() + 30 * 60 * 1000); // 30 min lock
      }
      await userDoc.save();
    }
    throw Object.assign(new Error('Invalid email or password'), { statusCode: 401 });
  }

  if (userDoc && (userDoc.loginAttempts > 0 || userDoc.lockUntil)) {
    userDoc.loginAttempts = 0;
    userDoc.lockUntil = null;
    await userDoc.save();
  }

  // Dual-Anchor requirement: Check if user has completed phone OTP verification
  if (!user.isPhoneVerified) {
    const preAuthToken = issuePreAuthToken(user);
    return {
      requiresPhoneVerification: true,
      status: 'PHONE_VERIFICATION_REQUIRED',
      preAuthToken,
      user: user.toSafeJSON ? user.toSafeJSON() : user,
    };
  }

  const { accessToken, refreshToken } = buildTokenPair(user);
  await saveRefreshToken(user._id, refreshToken, { platform, userAgent });

  return { token: accessToken, refreshToken, user: user.toSafeJSON() };
}

// ─────────────────────────────────────────────────────────────────────────────
// Google Sign-In — Verify ID token & authenticate/register user
// ─────────────────────────────────────────────────────────────────────────────

async function googleLogin(idToken, { platform = 'web', userAgent = '' } = {}) {
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
      authProvider: 'google',
      password: hashed,
      profileImage: picture || null,
      isEmailVerified: true,
      currency: 'INR',
      onboarded: false,
    });
  } else if (!user.isEmailVerified) {
    await userRepo.updateById(user._id, { isEmailVerified: true });
  }

  // Dual-Anchor requirement: User must verify phone number before receiving access tokens
  if (!user.isPhoneVerified) {
    const preAuthToken = issuePreAuthToken(user);
    return {
      requiresPhoneVerification: true,
      status: 'PHONE_VERIFICATION_REQUIRED',
      preAuthToken,
      user: user.toSafeJSON ? user.toSafeJSON() : user,
    };
  }

  const { accessToken, refreshToken } = buildTokenPair(user);
  await saveRefreshToken(user._id, refreshToken, { platform, userAgent });

  return { status: 'AUTHENTICATED', token: accessToken, refreshToken, user: user.toSafeJSON ? user.toSafeJSON() : user };
}

// ─────────────────────────────────────────────────────────────────────────────
// Phone OTP Verification (Dual-Anchor Identity Anchor 2)
// ─────────────────────────────────────────────────────────────────────────────

async function sendPhoneOtp({ preAuthToken, phone }) {
  if (!preAuthToken) {
    throw Object.assign(new Error('Verification session required. Please sign in or register first.'), { statusCode: 401 });
  }
  if (!phone) {
    throw Object.assign(new Error('Mobile contact number is required'), { statusCode: 400 });
  }

  const decoded = verifyPreAuthToken(preAuthToken);
  const user = await userRepo.findById(decoded.id);
  if (!user) throw Object.assign(new Error('User account not found'), { statusCode: 404 });

  // E.164 phone formatting & sanitization
  const cleanPhone = phone.replace(/[^\d+]/g, '').trim();
  if (!cleanPhone || cleanPhone.length < 10) {
    throw Object.assign(new Error('Please enter a valid mobile number with country code (e.g. +91 9876543210)'), { statusCode: 400 });
  }

  // Ensure phone is not already bound to ANOTHER user
  const existingPhone = await User.findOne({ phone: cleanPhone, _id: { $ne: user._id } });
  if (existingPhone) {
    throw Object.assign(new Error('This mobile number is already registered to another account. Please log in instead.'), { statusCode: 409 });
  }

  // Rate Limiting: Max 3 requests in the last 15 minutes
  const recentRequests = await PhoneVerification.countDocuments({
    phone: cleanPhone,
    createdAt: { $gte: new Date(Date.now() - 15 * 60 * 1000) },
  });
  if (recentRequests >= 3) {
    throw Object.assign(new Error('Too many OTP attempts. Please wait 15 minutes before requesting again.'), { statusCode: 429 });
  }

  // Generate cryptographically secure 6-digit OTP
  const otp = crypto.randomInt(100000, 999999).toString();

  if (env.isDev) {
    logger.debug(`[Auth] Dual-anchor OTP issued for ${cleanPhone}: ****${otp.slice(-2)}`);
  }

  // Zero-Knowledge HMAC-SHA256 hashed storage
  const otpHash = crypto
    .createHmac('sha256', env.OTP_PEPPER)
    .update(otp + user._id.toString() + cleanPhone)
    .digest('hex');

  // Wipe any active OTPs for this user
  await PhoneVerification.deleteMany({ userId: user._id });

  await PhoneVerification.create({
    userId: user._id,
    phone: cleanPhone,
    otpHash,
    attempts: 0,
    expiresAt: new Date(Date.now() + 5 * 60 * 1000), // 5-minute TTL
  });

  // 1. Dispatch real cellular SMS via TextBee Android Gateway
  let smsDelivered = false;
  try {
    const smsResult = await smsService.sendOtpSms(cleanPhone, otp);
    smsDelivered = smsResult?.success === true;
  } catch (smsErr) {
    logger.warn('[Auth] TextBee SMS dispatch failed on dual-anchor', { phone: cleanPhone, error: smsErr.message });
  }

  // 2. Send backup OTP to user's registered/Google email
  let emailDelivered = false;
  if (user?.email) {
    try {
      const { subject, html } = emailSvc.otpEmail(cleanPhone, otp);
      const emailResult = await emailSvc.sendEmail({
        to: user.email,
        subject,
        html,
      });
      emailDelivered = emailResult?.sent === true;
    } catch (err) {
      logger.warn('[Auth] Failed to send email OTP backup', { error: err.message });
    }
  }

  // 3. Fail honestly if BOTH delivery channels failed
  if (!smsDelivered && !emailDelivered) {
    throw Object.assign(
      new Error('Unable to deliver verification code via SMS or email. Please check your network and try again in a moment.'),
      { statusCode: 502 }
    );
  }

  const deliveryMessage = smsDelivered
    ? `Verification code sent to ${cleanPhone}${emailDelivered ? ` (and email backup to ${user.email})` : ''}`
    : `SMS gateway unavailable. Verification code sent to your registered email (${user.email}).`;

  return {
    success: true,
    message: deliveryMessage,
    phone: cleanPhone,
    expiresIn: 300,
  };
}

async function verifyPhoneOtp({ preAuthToken, phone, otp, platform = 'web', userAgent = '' }) {
  if (!preAuthToken) {
    throw Object.assign(new Error('Verification session required. Please sign in or register first.'), { statusCode: 401 });
  }
  if (!phone || !otp) {
    throw Object.assign(new Error('Phone number and 6-digit verification code are required'), { statusCode: 400 });
  }

  const decoded = verifyPreAuthToken(preAuthToken);
  const user = await userRepo.findById(decoded.id);
  if (!user) throw Object.assign(new Error('User account not found'), { statusCode: 404 });

  const cleanPhone = phone.replace(/[^\d+]/g, '').trim();
  const cleanOtp = otp.toString().trim();

  if (cleanOtp.length !== 6) {
    throw Object.assign(new Error('Verification code must be exactly 6 digits'), { statusCode: 400 });
  }

  const record = await PhoneVerification.findOne({ userId: user._id, phone: cleanPhone });
  if (!record) {
    throw Object.assign(new Error('Verification code expired or not found. Please request a new code.'), { statusCode: 400 });
  }

  if (record.attempts >= 3) {
    await PhoneVerification.deleteMany({ userId: user._id });
    throw Object.assign(new Error('Too many incorrect attempts. Please request a new code.'), { statusCode: 429 });
  }

  // Constant-time comparison to prevent side-channel timing attacks
  const computedHash = crypto
    .createHmac('sha256', env.OTP_PEPPER)
    .update(cleanOtp + user._id.toString() + cleanPhone)
    .digest('hex');

  const isMatch = crypto.timingSafeEqual(Buffer.from(computedHash, 'hex'), Buffer.from(record.otpHash, 'hex'));

  if (!isMatch) {
    record.attempts += 1;
    await record.save();
    const remaining = 3 - record.attempts;
    if (remaining <= 0) {
      await PhoneVerification.deleteMany({ userId: user._id });
      throw Object.assign(new Error('Incorrect code. Attempts exceeded — please request a new code.'), { statusCode: 400 });
    }
    throw Object.assign(new Error(`Incorrect code. ${remaining} attempt(s) remaining.`), { statusCode: 400 });
  }

  // Ensure phone is not already bound to ANOTHER user
  const existingPhone = await User.findOne({ phone: cleanPhone, _id: { $ne: user._id } });
  if (existingPhone) {
    throw Object.assign(new Error('This mobile number is already registered to another account. Please log in instead.'), { statusCode: 409 });
  }

  // Success! Mark phone verified and delete OTP
  const updatedUser = await userRepo.updateById(user._id, {
    phone: cleanPhone,
    isPhoneVerified: true,
  });
  await PhoneVerification.deleteMany({ userId: user._id });

  // Issue full production token pair
  const { accessToken, refreshToken } = buildTokenPair(updatedUser);
  await saveRefreshToken(updatedUser._id, refreshToken, { platform, userAgent });

  const safeUser = { ...updatedUser };
  delete safeUser.password;
  delete safeUser.refreshToken;
  delete safeUser.refreshTokenExpiry;
  delete safeUser.passwordResetToken;
  delete safeUser.passwordResetExpiry;
  delete safeUser.emailVerifyToken;
  delete safeUser.emailVerifyExpiry;

  return {
    status: 'AUTHENTICATED',
    token: accessToken,
    refreshToken,
    user: safeUser,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Direct Phone Authentication (Zero Fake Credentials, Zero Passwords)
// ─────────────────────────────────────────────────────────────────────────────

function formatCleanPhone(rawPhone) {
  if (!rawPhone) return '';
  const str = rawPhone.toString().trim();
  const digits = str.replace(/\D/g, '');
  if (digits.length === 10) {
    return `+91${digits}`;
  }
  if (digits.length === 12 && digits.startsWith('91')) {
    return `+${digits}`;
  }
  if (str.startsWith('+') && digits.length >= 10 && digits.length <= 15) {
    return `+${digits}`;
  }
  return '';
}

async function phoneLoginRequest({ phone }) {
  const cleanPhone = formatCleanPhone(phone);
  if (!cleanPhone || cleanPhone.length < 10) {
    throw Object.assign(new Error('Please enter a valid 10-digit mobile number'), { statusCode: 400 });
  }

  // Check if user exists with this phone
  const user = await userRepo.findByPhone(cleanPhone);
  if (!user) {
    throw Object.assign(new Error('No account found with this mobile number. Please register first.'), { statusCode: 404 });
  }

  // Rate limiting: max 3 requests per 15 min
  const recentRequests = await PhoneVerification.countDocuments({
    phone: cleanPhone,
    createdAt: { $gte: new Date(Date.now() - 15 * 60 * 1000) },
  });
  if (recentRequests >= 3) {
    throw Object.assign(new Error('Too many OTP attempts. Please wait 15 minutes before requesting again.'), { statusCode: 429 });
  }

  const otp = crypto.randomInt(100000, 999999).toString();
  const otpHash = crypto
    .createHmac('sha256', env.OTP_PEPPER)
    .update(otp + user._id.toString() + cleanPhone + 'login')
    .digest('hex');

  // Invalidate prior active OTPs without deleting history
  await PhoneVerification.updateMany(
    { phone: cleanPhone, action: 'login', status: 'active' },
    { $set: { status: 'superseded' } }
  );

  await PhoneVerification.create({
    userId: user._id,
    phone: cleanPhone,
    otpHash,
    action: 'login',
    status: 'active',
    attempts: 0,
    expiresAt: new Date(Date.now() + 5 * 60 * 1000), // 5-minute TTL
  });

  if (env.isDev) {
    logger.debug(`[Auth] Phone login OTP issued for ${cleanPhone}: ****${otp.slice(-2)}`);
  }

  // Dispatch real cellular SMS via TextBee Android Gateway
  let smsDelivered = false;
  try {
    const smsResult = await smsService.sendOtpSms(cleanPhone, otp);
    smsDelivered = smsResult?.success === true;
  } catch (smsErr) {
    logger.warn('[Auth] TextBee SMS dispatch failed on phone login', { phone: cleanPhone, error: smsErr.message });
  }

  // Backup: if SMS fails and user has registered email, send OTP via email
  let emailDelivered = false;
  if (!smsDelivered && user.email) {
    try {
      const { subject, html } = emailSvc.otpEmail(cleanPhone, otp);
      const emailResult = await emailSvc.sendEmail({ to: user.email, subject, html });
      emailDelivered = emailResult?.sent === true;
    } catch (err) {
      logger.warn('[Auth] Email fallback failed on phone login', { error: err.message });
    }
  }

  if (!smsDelivered && !emailDelivered) {
    throw Object.assign(
      new Error('Unable to deliver verification code. Please check your mobile number or try again in a moment.'),
      { statusCode: 502 }
    );
  }

  const message = smsDelivered
    ? `Verification code sent to ${cleanPhone}`
    : `SMS delivery failed. We sent your verification code to your email (${user.email}).`;

  return {
    success: true,
    message,
    phone: cleanPhone,
    expiresIn: 300,
  };
}

async function phoneLoginVerify({ phone, otp, platform = 'web', userAgent = '' }) {
  const cleanPhone = formatCleanPhone(phone);
  const cleanOtp = (otp || '').toString().trim();

  if (!cleanPhone || cleanOtp.length !== 6) {
    throw Object.assign(new Error('Mobile number and 6-digit verification code are required'), { statusCode: 400 });
  }

  const record = await PhoneVerification.findOne({
    phone: cleanPhone,
    action: 'login',
    status: 'active',
  }).sort({ createdAt: -1 });

  if (!record) {
    throw Object.assign(new Error('Verification code expired or not found. Please request a new code.'), { statusCode: 400 });
  }

  if (new Date() > record.expiresAt) {
    record.status = 'expired';
    await record.save();
    throw Object.assign(new Error('Verification code has expired. Please request a new code.'), { statusCode: 400 });
  }

  if (record.attempts >= 3) {
    record.status = 'exhausted';
    await record.save();
    throw Object.assign(new Error('Too many incorrect attempts. Please request a new code.'), { statusCode: 429 });
  }

  const user = await userRepo.findById(record.userId);
  if (!user) {
    throw Object.assign(new Error('User account not found'), { statusCode: 404 });
  }

  const computedHash = crypto
    .createHmac('sha256', env.OTP_PEPPER)
    .update(cleanOtp + user._id.toString() + cleanPhone + 'login')
    .digest('hex');

  const isMatch = crypto.timingSafeEqual(Buffer.from(computedHash, 'hex'), Buffer.from(record.otpHash, 'hex'));

  if (!isMatch) {
    record.attempts += 1;
    if (record.attempts >= 3) {
      record.status = 'exhausted';
    }
    await record.save();
    const remaining = 3 - record.attempts;
    if (remaining <= 0) {
      throw Object.assign(new Error('Incorrect code. Attempts exceeded — please request a new code.'), { statusCode: 400 });
    }
    throw Object.assign(new Error(`Incorrect code. ${remaining} attempt(s) remaining.`), { statusCode: 400 });
  }

  // Ensure isPhoneVerified is true and mark OTP record verified
  const updatedUser = await userRepo.updateById(user._id, { isPhoneVerified: true });
  record.status = 'verified';
  await record.save();

  const { accessToken, refreshToken } = buildTokenPair(updatedUser);
  await saveRefreshToken(updatedUser._id, refreshToken, { platform, userAgent });

  const safeUser = { ...updatedUser };
  delete safeUser.password;
  delete safeUser.refreshToken;
  delete safeUser.refreshTokenExpiry;

  return {
    status: 'AUTHENTICATED',
    token: accessToken,
    refreshToken,
    user: safeUser,
  };
}

async function phoneRegisterRequest({ phone, name, username }) {
  const cleanPhone = formatCleanPhone(phone);
  if (!cleanPhone || cleanPhone.length < 10) {
    throw Object.assign(new Error('Please enter a valid 10-digit mobile number'), { statusCode: 400 });
  }

  if (!name || name.trim().length < 2) {
    throw Object.assign(new Error('Please enter your full name'), { statusCode: 400 });
  }

  // 1. Check if user already exists with this phone
  const existingUser = await userRepo.findByPhone(cleanPhone);
  if (existingUser) {
    throw Object.assign(
      new Error('This mobile number is already registered. Please log in instead.'),
      { statusCode: 409 }
    );
  }

  // 2. Check if username is taken (if provided)
  if (username && username.trim()) {
    const cleanUsername = username.toLowerCase().trim().replace(/^@/, '');
    const uExists = await userRepo.findByUsername(cleanUsername);
    if (uExists) {
      throw Object.assign(
        new Error('This username is already taken. Please choose another.'),
        { statusCode: 409 }
      );
    }
  }

  // 3. Rate limiting: max 3 requests per 15 min
  const recentRequests = await PhoneVerification.countDocuments({
    phone: cleanPhone,
    createdAt: { $gte: new Date(Date.now() - 15 * 60 * 1000) },
  });
  if (recentRequests >= 3) {
    throw Object.assign(
      new Error('Too many OTP attempts. Please wait 15 minutes before requesting again.'),
      { statusCode: 429 }
    );
  }

  const otp = crypto.randomInt(100000, 999999).toString();
  const otpHash = crypto
    .createHmac('sha256', env.OTP_PEPPER)
    .update(otp + cleanPhone + 'register')
    .digest('hex');

  // Invalidate previous active registration OTPs
  await PhoneVerification.updateMany(
    { phone: cleanPhone, action: 'register', status: 'active' },
    { $set: { status: 'superseded' } }
  );

  await PhoneVerification.create({
    phone: cleanPhone,
    otpHash,
    action: 'register',
    status: 'active',
    attempts: 0,
    metadata: {
      name: name.trim(),
      username: username ? username.trim().toLowerCase().replace(/^@/, '') : undefined,
    },
    expiresAt: new Date(Date.now() + 5 * 60 * 1000), // 5-minute TTL
  });

  if (env.isDev) {
    logger.debug(`[Auth] Phone registration OTP issued for ${cleanPhone}: ****${otp.slice(-2)}`);
  }

  // Dispatch real SMS via TextBee
  let smsDelivered = false;
  try {
    const smsResult = await smsService.sendOtpSms(cleanPhone, otp);
    smsDelivered = smsResult?.success === true;
  } catch (smsErr) {
    logger.warn('[Auth] TextBee SMS dispatch failed on register', { phone: cleanPhone, error: smsErr.message });
  }

  if (!smsDelivered) {
    throw Object.assign(
      new Error('Unable to deliver SMS verification code right now. Please check your mobile number or sign up with Google.'),
      { statusCode: 502 }
    );
  }

  return {
    success: true,
    message: `Verification code sent to ${cleanPhone}`,
    phone: cleanPhone,
    expiresIn: 300,
  };
}

async function phoneRegisterVerify({ phone, otp, name, username, platform = 'web', userAgent = '' }) {
  const cleanPhone = formatCleanPhone(phone);
  const cleanOtp = (otp || '').toString().trim();

  if (!cleanPhone || cleanOtp.length !== 6) {
    throw Object.assign(new Error('Mobile number and 6-digit verification code are required'), { statusCode: 400 });
  }

  const record = await PhoneVerification.findOne({
    phone: cleanPhone,
    action: 'register',
    status: 'active',
  }).sort({ createdAt: -1 });

  if (!record) {
    throw Object.assign(new Error('Verification code expired or not found. Please request a new code.'), { statusCode: 400 });
  }

  if (new Date() > record.expiresAt) {
    record.status = 'expired';
    await record.save();
    throw Object.assign(new Error('Verification code has expired. Please request a new code.'), { statusCode: 400 });
  }

  if (record.attempts >= 3) {
    record.status = 'exhausted';
    await record.save();
    throw Object.assign(new Error('Too many incorrect attempts. Please request a new code.'), { statusCode: 429 });
  }

  const computedHash = crypto
    .createHmac('sha256', env.OTP_PEPPER)
    .update(cleanOtp + cleanPhone + 'register')
    .digest('hex');

  const isMatch = crypto.timingSafeEqual(Buffer.from(computedHash, 'hex'), Buffer.from(record.otpHash, 'hex'));

  if (!isMatch) {
    record.attempts += 1;
    await record.save();
    const remaining = 3 - record.attempts;
    throw Object.assign(
      new Error(`Invalid verification code. ${remaining} attempt${remaining === 1 ? '' : 's'} remaining.`),
      { statusCode: 400 }
    );
  }

  record.status = 'verified';
  await record.save();

  // Ensure user doesn't already exist
  const existingUser = await userRepo.findByPhone(cleanPhone);
  if (existingUser) {
    throw Object.assign(
      new Error('This mobile number is already registered. Please log in instead.'),
      { statusCode: 409 }
    );
  }

  const finalName = (name || record.metadata?.name || 'BudGi User').trim();
  const rawUsername = username || record.metadata?.username;
  let finalUsername = null;

  if (rawUsername) {
    const cleanU = rawUsername.toLowerCase().trim().replace(/^@/, '');
    const uExists = await userRepo.findByUsername(cleanU);
    finalUsername = uExists ? await generateUniqueUsername(cleanU) : cleanU;
  } else {
    finalUsername = await generateUniqueUsername(cleanPhone.slice(-6));
  }

  const user = await userRepo.create({
    name: finalName,
    phone: cleanPhone,
    username: finalUsername,
    authProvider: 'phone',
    isPhoneVerified: true,
    isEmailVerified: false,
    onboarded: false,
    currency: 'INR',
    monthlyIncome: 0,
    streak: { count: 1, lastActiveDate: new Date() },
  });

  const { accessToken, refreshToken } = buildTokenPair(user);
  await saveRefreshToken(user._id, refreshToken, { platform, userAgent });

  const safeUser = user.toSafeJSON ? user.toSafeJSON() : { ...user };
  delete safeUser.password;
  delete safeUser.refreshToken;
  delete safeUser.refreshTokenExpiry;

  return {
    status: 'AUTHENTICATED',
    token: accessToken,
    refreshToken,
    user: safeUser,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Forgot Password — send reset link
// ─────────────────────────────────────────────────────────────────────────────

async function forgotPassword(email) {
  if (!email) {
    throw Object.assign(new Error('Email is required'), { statusCode: 400 });
  }

  const cleanEmail = email.toLowerCase().trim();
  const user = await userRepo.findByEmail(cleanEmail);
  const safeMsg = 'If that email is registered, a password reset link has been sent.';

  if (!user) return { message: safeMsg };

  const resetToken = generateHexToken();
  await userRepo.updateById(user._id, {
    passwordResetToken: hashToken(resetToken),
    passwordResetExpiry: new Date(Date.now() + RESET_TOKEN_TTL),
  });

  const resetUrl = `${env.CLIENT_ORIGIN}/reset-password?token=${resetToken}`;
  if (env.isDev) {
    logger.debug('[Auth] Password reset link generated', { targetEmail: cleanEmail, resetUrl });
  }

  const { subject, html } = emailSvc.passwordResetEmail(user.name, resetUrl);
  emailSvc.sendEmail({ to: user.email, subject, html }).catch((err) => {
    logger.warn('[Auth] Failed to send password reset email', { error: err.message });
  });

  return {
    message: safeMsg,
    devResetUrl: process.env.NODE_ENV !== 'production' ? resetUrl : undefined,
    devToken: process.env.NODE_ENV !== 'production' ? resetToken : undefined,
  };
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

  const hashedToken = hashToken(token);
  let user = await userRepo.findByPasswordResetToken(hashedToken);
  if (!user) {
    user = await userRepo.findByPasswordResetToken(token);
  }
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
    // Clear sessions & legacy tokens across all devices for safety
    refreshToken: null,
    refreshTokenExpiry: null,
    sessions: [],
  });

  return { message: 'Password reset successful. Please log in with your new password.' };
}

// ─────────────────────────────────────────────────────────────────────────────
// Refresh Token — exchange a valid refresh token for a new access + refresh pair
// ─────────────────────────────────────────────────────────────────────────────

async function refreshAccessToken(incomingRefreshToken, meta = {}) {
  if (!incomingRefreshToken) {
    throw Object.assign(new Error('Refresh token is required'), { statusCode: 400 });
  }

  const hashedToken = hashToken(incomingRefreshToken);
  const user = await userRepo.findByRefreshToken(hashedToken);
  if (!user) {
    throw Object.assign(new Error('Invalid or expired refresh token'), { statusCode: 401 });
  }

  const userDoc = await User.findById(user._id).select('+sessions +refreshToken +refreshTokenExpiry');
  if (!userDoc) {
    throw Object.assign(new Error('User account not found'), { statusCode: 404 });
  }

  const matchingSession = (userDoc.sessions || []).find((s) => s.tokenHash === hashedToken);
  const isLegacyMatch = userDoc.refreshToken === hashedToken;

  if (!matchingSession && !isLegacyMatch) {
    throw Object.assign(new Error('Invalid or expired refresh token'), { statusCode: 401 });
  }

  const isExpired = matchingSession
    ? new Date() > matchingSession.expiresAt
    : (userDoc.refreshTokenExpiry && new Date() > userDoc.refreshTokenExpiry);

  if (isExpired) {
    if (userDoc.sessions) {
      userDoc.sessions = userDoc.sessions.filter((s) => s.tokenHash !== hashedToken);
      await userDoc.save();
    }
    throw Object.assign(new Error('Refresh token expired — please log in again'), { statusCode: 401 });
  }

  const { accessToken, refreshToken: newRefreshToken } = buildTokenPair(user);
  const newHash = hashToken(newRefreshToken);
  const newExpiresAt = new Date(Date.now() + REFRESH_TOKEN_TTL);

  if (matchingSession) {
    matchingSession.tokenHash = newHash;
    matchingSession.expiresAt = newExpiresAt;
    matchingSession.createdAt = new Date();
    if (meta.platform) matchingSession.platform = meta.platform;
    if (meta.userAgent) matchingSession.userAgent = meta.userAgent.slice(0, 200);
    userDoc.refreshToken = newHash;
    userDoc.refreshTokenExpiry = newExpiresAt;
    await userDoc.save();
  } else {
    await saveRefreshToken(user._id, newRefreshToken, meta);
  }

  return { token: accessToken, refreshToken: newRefreshToken, user: user.toSafeJSON() };
}

// ─────────────────────────────────────────────────────────────────────────────
// Logout — invalidate current session or all refresh tokens
// ─────────────────────────────────────────────────────────────────────────────

async function logout(userId, incomingRefreshToken) {
  const user = await User.findById(userId).select('+sessions');
  if (user) {
    if (incomingRefreshToken) {
      const hashed = hashToken(incomingRefreshToken);
      user.sessions = (user.sessions || []).filter((s) => s.tokenHash !== hashed);
      if (user.refreshToken === hashed) {
        user.refreshToken = null;
        user.refreshTokenExpiry = null;
      }
    } else {
      user.sessions = [];
      user.refreshToken = null;
      user.refreshTokenExpiry = null;
    }
    await user.save();
  }
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

// ─────────────────────────────────────────────────────────────────────────────
// Firebase Phone Authentication (Real Carrier-Delivered SMS Verification)
// ─────────────────────────────────────────────────────────────────────────────

async function verifyFirebaseToken(idToken) {
  if (!idToken) {
    throw Object.assign(new Error('Firebase verification token is required'), { statusCode: 400 });
  }

  // 1. If firebase-admin initialized with credentials, use admin SDK
  try {
    const admin = require('firebase-admin');
    if (admin && admin.apps && admin.apps.length > 0) {
      const decoded = await admin.auth().verifyIdToken(idToken);
      if (decoded && decoded.phone_number) {
        return {
          phone: decoded.phone_number,
          uid: decoded.uid,
          email: decoded.email,
        };
      }
    }
  } catch {
    // Fall through to Identity Toolkit REST API
  }

  // 2. Google Identity Toolkit REST verification using BudGi project API key
  const apiKey = env.FIREBASE_API_KEY;
  if (!apiKey) {
    throw Object.assign(
      new Error('Firebase Phone Authentication requires FIREBASE_API_KEY to be configured in server environment.'),
      { statusCode: 503 }
    );
  }
  const url = `https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${apiKey}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken }),
  });

  if (!res.ok) {
    const errData = await res.json().catch(() => ({}));
    throw Object.assign(
      new Error(errData.error?.message || 'Invalid or expired Firebase phone verification token'),
      { statusCode: 401 }
    );
  }

  const data = await res.json();
  const userRecord = data.users?.[0];
  if (!userRecord || !userRecord.phoneNumber) {
    throw Object.assign(new Error('No verified phone number found in Firebase token'), { statusCode: 400 });
  }

  return {
    phone: userRecord.phoneNumber,
    uid: userRecord.localId,
    email: userRecord.email,
  };
}

async function firebasePhoneLogin({ idToken, platform = 'web', userAgent = '' }) {
  const { phone } = await verifyFirebaseToken(idToken);
  const cleanPhone = formatCleanPhone(phone);

  const user = await userRepo.findByPhone(cleanPhone);
  if (!user) {
    throw Object.assign(
      new Error('No account found with this mobile number. Please register first.'),
      { statusCode: 404 }
    );
  }

  const { accessToken, refreshToken } = buildTokenPair(user);
  await saveRefreshToken(user._id, refreshToken, { platform, userAgent });

  const safeUser = user.toSafeJSON ? user.toSafeJSON() : { ...user };
  delete safeUser.password;
  delete safeUser.refreshToken;
  delete safeUser.refreshTokenExpiry;

  return {
    status: 'AUTHENTICATED',
    token: accessToken,
    refreshToken,
    user: safeUser,
  };
}

async function firebasePhoneRegister({ idToken, name, username, platform = 'web', userAgent = '' }) {
  const { phone } = await verifyFirebaseToken(idToken);
  const cleanPhone = formatCleanPhone(phone);

  if (!name || name.trim().length < 2) {
    throw Object.assign(new Error('Please enter your full name'), { statusCode: 400 });
  }

  const existingUser = await userRepo.findByPhone(cleanPhone);
  if (existingUser) {
    throw Object.assign(
      new Error('This mobile number is already registered. Please log in instead.'),
      { statusCode: 409 }
    );
  }

  let finalUsername = null;
  if (username) {
    const cleanUsername = username.toLowerCase().trim().replace(/^@/, '');
    const uExists = await userRepo.findByUsername(cleanUsername);
    finalUsername = uExists ? await generateUniqueUsername(cleanUsername) : cleanUsername;
  } else {
    finalUsername = await generateUniqueUsername(cleanPhone.slice(-6));
  }

  const user = await userRepo.create({
    name: name.trim(),
    phone: cleanPhone,
    username: finalUsername,
    authProvider: 'phone',
    isPhoneVerified: true,
    isEmailVerified: false,
    onboarded: false,
    currency: 'INR',
    monthlyIncome: 0,
    streak: { count: 1, lastActiveDate: new Date() },
  });

  const { accessToken, refreshToken } = buildTokenPair(user);
  await saveRefreshToken(user._id, refreshToken, { platform, userAgent });

  const safeUser = user.toSafeJSON ? user.toSafeJSON() : { ...user };
  delete safeUser.password;
  delete safeUser.refreshToken;
  delete safeUser.refreshTokenExpiry;

  return {
    status: 'AUTHENTICATED',
    token: accessToken,
    refreshToken,
    user: safeUser,
  };
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
  sendPhoneOtp,
  verifyPhoneOtp,
  phoneLoginRequest,
  phoneLoginVerify,
  phoneRegisterRequest,
  phoneRegisterVerify,
  verifyFirebaseToken,
  firebasePhoneLogin,
  firebasePhoneRegister,
};
