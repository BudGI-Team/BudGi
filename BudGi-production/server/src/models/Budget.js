// src/models/Budget.js
'use strict';

const mongoose = require('mongoose');

const budgetSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    category: { type: String, required: true, trim: true, maxlength: 80 },
    limit: { type: Number, required: true, min: 0 },
    period: { type: String, enum: ['monthly', 'weekly'], default: 'monthly' },
    // Alert thresholds (0-1)
    alertAt: { type: Number, default: 0.9, min: 0, max: 1 },
    color: { type: String, default: '#6366f1', maxlength: 20 },
  },
  { timestamps: true },
);

budgetSchema.index({ userId: 1, category: 1 }, { unique: true });

module.exports = mongoose.model('Budget', budgetSchema);
