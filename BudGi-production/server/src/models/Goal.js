// src/models/Goal.js
'use strict';

const mongoose = require('mongoose');

const goalSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    title: { type: String, required: true, trim: true, maxlength: 120 },
    targetAmount: { type: Number, required: true, min: 1 },
    currentAmount: { type: Number, default: 0, min: 0 },
    deadline: { type: Date, default: null },
    category: { type: String, default: 'savings', trim: true, maxlength: 60 },
    icon: { type: String, default: '🎯', maxlength: 10 },
    color: { type: String, default: '#7C6AF5', maxlength: 20 },
    isCompleted: { type: Boolean, default: false },
    completedAt: { type: Date, default: null },
  },
  { timestamps: true },
);

goalSchema.index({ userId: 1, isCompleted: 1 });

goalSchema.virtual('progress').get(function () {
  if (this.targetAmount <= 0) return 100;
  return Math.min(100, Math.round((this.currentAmount / this.targetAmount) * 100));
});

goalSchema.set('toJSON', { virtuals: true });
goalSchema.set('toObject', { virtuals: true });

module.exports = mongoose.model('Goal', goalSchema);
