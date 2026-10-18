// src/models/Alert.js
'use strict';

const mongoose = require('mongoose');

const alertSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    type: {
      type: String,
      enum: ['budget_warning', 'budget_exceeded', 'anomaly', 'spending_spike', 'recurring_due', 'weekly_summary'],
      required: true,
    },
    message: { type: String, required: true, maxlength: 500 },
    severity: { type: String, enum: ['info', 'warning', 'critical'], default: 'info' },
    category: { type: String, default: null },
    isRead: { type: Boolean, default: false, index: true },
    metadata: { type: mongoose.Schema.Types.Mixed, default: {} },
  },
  { timestamps: true },
);

alertSchema.index({ userId: 1, isRead: 1, createdAt: -1 });

module.exports = mongoose.model('Alert', alertSchema);
