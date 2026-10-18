'use strict';

const mongoose = require('mongoose');
const { Schema } = mongoose;

const SpaceMemberSchema = new Schema(
  {
    userId: {
      type: Schema.Types.ObjectId,
      ref: 'User',
      required: true,
    },
    role: {
      type: String,
      enum: ['ADMIN', 'MEMBER'],
      default: 'MEMBER',
    },
    joinedAt: {
      type: Date,
      default: Date.now,
    },
  },
  { _id: false },
);

const SpaceGoalSchema = new Schema({
  title: {
    type: String,
    required: true,
    trim: true,
  },
  targetAmount: {
    type: Number,
    required: true,
    min: 0,
  },
  currentAmount: {
    type: Number,
    default: 0,
    min: 0,
  },
  category: {
    type: String,
    required: true,
  },
  createdAt: {
    type: Date,
    default: Date.now,
  },
});

const SpaceSchema = new Schema(
  {
    name: {
      type: String,
      required: true,
      trim: true,
    },
    type: {
      type: String,
      enum: ['COUPLE', 'ROOMMATES', 'FAMILY', 'CUSTOM'],
      default: 'CUSTOM',
    },
    inviteCode: {
      type: String,
      unique: true,
      required: true,
    },
    members: [SpaceMemberSchema],
    goals: [SpaceGoalSchema],
  },
  {
    timestamps: true,
    toJSON: { virtuals: true },
    toObject: { virtuals: true },
  },
);

// Ensure a user isn't added multiple times
SpaceSchema.index({ _id: 1, 'members.userId': 1 }, { unique: true });

const Space = mongoose.model('Space', SpaceSchema);
module.exports = Space;
