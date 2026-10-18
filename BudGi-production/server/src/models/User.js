// src/models/User.js
'use strict';

const mongoose = require('mongoose');

const userSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true, maxlength: 100 },
    email: { type: String, required: true, unique: true, trim: true, lowercase: true },
    username: { type: String, unique: true, sparse: true, trim: true, lowercase: true, maxlength: 30 },
    dateOfBirth: { type: Date, default: null },
    password: { type: String, required: true, minlength: 60, maxlength: 200 },
    currency: { type: String, default: 'INR', maxlength: 10 },
    monthlyIncome: { type: Number, default: 0, min: 0 },
    financialGoal: { type: String, default: '', maxlength: 200 },
    plan: { type: String, enum: ['free', 'pro', 'elite'], default: 'free' },
    profileImage: { type: String, default: null },
    onboarded: { type: Boolean, default: false },
    mood: { type: String, default: 'neutral' },
    avatar: { type: String, default: 'zen_saver' },
    streak: {
      count: { type: Number, default: 1 },
      lastActiveDate: { type: Date, default: Date.now },
    },

    // Financial health score (0-100), recomputed by cron
    healthScore: { type: Number, default: null, min: 0, max: 100 },
    healthScoreAt: { type: Date, default: null },

    // ── Email verification ────────────────────────────────────────────────────
    isEmailVerified: { type: Boolean, default: false },
    emailVerifyToken: { type: String, default: null, select: false },
    emailVerifyExpiry: { type: Date, default: null, select: false },

    // ── Password reset ────────────────────────────────────────────────────────
    passwordResetToken: { type: String, default: null, select: false },
    passwordResetExpiry: { type: Date, default: null, select: false },

    // ── Mobile integration ────────────────────────────────────────────────────
    refreshToken: { type: String, default: null, select: false },
    refreshTokenExpiry: { type: Date, default: null, select: false },

    // FCM / push notification device tokens
    deviceTokens: [
      {
        token: { type: String, required: true },
        platform: { type: String, enum: ['android', 'ios', 'web'], default: 'android' },
        addedAt: { type: Date, default: Date.now },
      },
    ],

    // Last sync timestamp for offline-first mobile apps
    lastSyncAt: { type: Date, default: null },
  },
  { timestamps: true },
);

// Compound indexes for performance
// Compound indexes for performance (email index already on field definition)
userSchema.index({ passwordResetToken: 1 });
userSchema.index({ emailVerifyToken: 1 });

userSchema.methods.toSafeJSON = function () {
  const obj = this.toObject();
  delete obj.password;
  delete obj.refreshToken;
  delete obj.refreshTokenExpiry;
  delete obj.passwordResetToken;
  delete obj.passwordResetExpiry;
  delete obj.emailVerifyToken;
  delete obj.emailVerifyExpiry;
  return obj;
};

module.exports = mongoose.model('User', userSchema);
