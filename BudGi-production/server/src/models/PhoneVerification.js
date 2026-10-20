// src/models/PhoneVerification.js
// Holds transient HMAC-SHA256 hashed OTPs for Dual-Anchor phone verification.
// Auto-deleted via MongoDB native TTL index after 300 seconds (5 minutes).

'use strict';

const mongoose = require('mongoose');

const phoneVerificationSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: false,
      default: null,
      index: true,
    },
    phone: {
      type: String,
      required: true,
      trim: true,
      index: true,
    },
    action: {
      type: String,
      enum: ['login', 'register', 'link'],
      default: 'login',
    },
    status: {
      type: String,
      enum: ['active', 'superseded', 'verified', 'exhausted'],
      default: 'active',
      index: true,
    },
    metadata: {
      type: mongoose.Schema.Types.Mixed,
      default: {},
    },
    otpHash: {
      type: String,
      required: true,
    },
    attempts: {
      type: Number,
      default: 0,
      max: 3,
    },
    expiresAt: {
      type: Date,
      required: true,
      index: { expireAfterSeconds: 0 },
    },
  },
  { timestamps: true },
);

module.exports = mongoose.model('PhoneVerification', phoneVerificationSchema);
