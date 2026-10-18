// src/models/MerchantCategoryMapping.js
'use strict';

const mongoose = require('mongoose');

/**
 * Stores user-specific merchant → category learning.
 * When a user manually categorizes a transaction, we store the normalizedMerchant
 * so all future (and optionally past) transactions from the same merchant
 * automatically receive the learned category.
 */
const merchantCategoryMappingSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    merchantKey: { type: String, required: true, trim: true, maxlength: 200 }, // Original merchant/UPI as-is
    normalizedMerchant: { type: String, required: true, trim: true, maxlength: 200 }, // Cleaned fingerprint
    category: { type: String, required: true, trim: true, maxlength: 80 },
    confidence: { type: Number, default: 1.0, min: 0, max: 1 },
    learnedFromUser: { type: Boolean, default: true },
    source: { type: String, enum: ['UserLearned', 'AI', 'Rule'], default: 'UserLearned' },
  },
  { timestamps: true },
);

// Unique per user + normalized merchant — also covers single userId queries as left prefix
merchantCategoryMappingSchema.index({ userId: 1, normalizedMerchant: 1 }, { unique: true });

module.exports = mongoose.model('MerchantCategoryMapping', merchantCategoryMappingSchema);
