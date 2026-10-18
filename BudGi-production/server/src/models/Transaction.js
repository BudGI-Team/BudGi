// src/models/Transaction.js
'use strict';

const mongoose = require('mongoose');
const crypto = require('crypto');

const transactionSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    amount: { type: Number, required: true, min: 0.01 },
    category: { type: String, required: true, trim: true, maxlength: 80 },
    type: { type: String, required: true, enum: ['income', 'expense'] },
    description: { type: String, default: '', trim: true, maxlength: 300 },
    merchant: { type: String, default: '', trim: true, maxlength: 120 },
    normalizedMerchant: { type: String, default: null, trim: true, maxlength: 200 },
    date: { type: Date, required: true, index: true },
    source: { type: String, enum: ['manual', 'csv', 'sms', 'import'], default: 'manual' },
    categorySource: { type: String, enum: ['AI', 'Rule', 'UserLearned', 'Manual', 'Default'], default: 'Default' },
    clientId: { type: String, index: true, sparse: true }, // For offline sync tracking

    // SMS-specific fields
    upiId: { type: String, default: null, trim: true },
    referenceNumber: { type: String, default: null, trim: true },
    accountLast4: { type: String, default: null, trim: true },
    availableBalance: { type: Number, default: null },
    rawSms: { type: String, default: null, maxlength: 500 },
    smsBodyDate: { type: String, default: null },
    sender: { type: String, default: null, trim: true },

    // AI-assigned category
    aiCategory: { type: String, default: null },
    aiConfidence: { type: Number, default: null, min: 0, max: 1 },

    // Recurring / anomaly flags
    isRecurring: { type: Boolean, default: false },
    recurringId: { type: String, default: null },
    recurringFrequency: { type: String, enum: ['daily', 'weekly', 'monthly', 'yearly'], default: null },
    recurringEndDate: { type: Date, default: null },
    isAnomaly: { type: Boolean, default: false },

    // Collaborative splits
    splitDetails: [
      {
        personName: { type: String, required: true },
        amount: { type: Number, required: true },
        settled: { type: Boolean, default: false },
      },
    ],

    /**
     * Deduplication fingerprint — SHA-256 of userId+amount+merchant+date(day).
     * Sparse so existing docs without it are not flagged as duplicate of each other.
     * Set in pre-save and pre-insertMany hooks.
     */
    _fingerprint: { type: String, default: null, index: true },
  },
  { timestamps: true },
);

// ── Compound indexes ──────────────────────────────────────────────────────────
transactionSchema.index({ userId: 1, date: -1 });
transactionSchema.index({ userId: 1, category: 1 });
transactionSchema.index({ userId: 1, type: 1, date: -1 });
transactionSchema.index({ userId: 1, isRecurring: 1 });
transactionSchema.index({ userId: 1, normalizedMerchant: 1 });
// Unique deduplication index — sparse so null values are not compared
transactionSchema.index({ userId: 1, _fingerprint: 1 }, { unique: true, sparse: true });
// SMS reference number dedup — unique per user when present
transactionSchema.index(
  { userId: 1, referenceNumber: 1 },
  { unique: true, partialFilterExpression: { referenceNumber: { $type: 'string' } } },
);

// ── Fingerprint helper ────────────────────────────────────────────────────────
function buildFingerprint(userId, amount, merchant, date, referenceNumber, source) {
  // If we have a reference number, use it as the primary fingerprint source
  if (referenceNumber) {
    const raw = `${userId}|ref:${referenceNumber}`;
    return crypto.createHash('sha256').update(raw).digest('hex');
  }

  // For SMS imports without ref numbers, rely on transactionService logical dedup
  if (source === 'sms') {
    return null;
  }

  // For manual/csv, use exact timestamp to millisecond to prevent double-click duplicates
  const raw = `${userId}|${Number(amount).toFixed(2)}|${(merchant || '').toLowerCase().trim()}|${new Date(date).getTime()}`;
  return crypto.createHash('sha256').update(raw).digest('hex');
}

transactionSchema.statics.buildFingerprint = buildFingerprint;

// Auto-set fingerprint on single save
transactionSchema.pre('save', function (next) {
  if (!this._fingerprint) {
    this._fingerprint = buildFingerprint(
      this.userId,
      this.amount,
      this.merchant,
      this.date,
      this.referenceNumber,
      this.source,
    );
  }
  next();
});

// Auto-set fingerprint on bulk insertMany — safety net for any direct insertMany callers
transactionSchema.pre('insertMany', function (next, docs) {
  if (Array.isArray(docs)) {
    docs.forEach((doc) => {
      if (!doc._fingerprint && doc.userId && doc.amount && doc.date) {
        doc._fingerprint = buildFingerprint(
          doc.userId,
          doc.amount,
          doc.merchant,
          doc.date,
          doc.referenceNumber,
          doc.source,
        );
      }
    });
  }
  next();
});

module.exports = mongoose.model('Transaction', transactionSchema);
