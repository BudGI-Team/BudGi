const mongoose = require('mongoose');

const userPreferenceSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
    key: {
      type: String,
      required: true,
      trim: true,
    },
    value: {
      type: mongoose.Schema.Types.Mixed,
      required: true,
    },
  },
  {
    timestamps: true,
  },
);

// Ensure a user only has one preference per key
userPreferenceSchema.index({ userId: 1, key: 1 }, { unique: true });

module.exports = mongoose.model('UserPreference', userPreferenceSchema);
