// modules/transactions/services/merchantService.js
'use strict';

const MerchantCategoryMapping = require('../../../models/MerchantCategoryMapping');
const Transaction = require('../../../models/Transaction');

// ── UPI handles to strip ──────────────────────────────────────────────────────
const UPI_HANDLES = [
  '@ybl',
  '@paytm',
  '@okaxis',
  '@okhdfcbank',
  '@okicici',
  '@oksbi',
  '@apl',
  '@ibl',
  '@axl',
  '@upi',
  '@kotak',
  '@icici',
  '@sbi',
  '@hdfcbank',
  '@axisbank',
  '@rbl',
  '@federal',
  '@idbi',
  '@boi',
  '@citi',
  '@indus',
  '@unionbank',
  '@pnb',
  '@canara',
  '@bob',
  '@allbank',
  '@hsbc',
  '@dbs',
  '@yes',
  '@sc',
  '@bandhan',
  '@jupiteraxis',
  '@freecharge',
  '@amazonpay',
  '@slice',
  '@niyoicici',
];

/**
 * Normalize a merchant name or UPI ID into a clean fingerprint.
 *
 * Examples:
 *   "raj_kirana@ybl"      → "rajkirana"
 *   "RAJ KIRANA 123@paytm"→ "rajkirana"
 *   "paytmqr2810050501@paytm" → "paytmqr"
 *   "SWIGGY"               → "swiggy"
 */
function normalizeMerchant(raw) {
  if (!raw || typeof raw !== 'string') return '';

  let cleaned = raw.toLowerCase().trim();

  // Strip UPI handles
  for (const handle of UPI_HANDLES) {
    if (cleaned.endsWith(handle)) {
      cleaned = cleaned.slice(0, -handle.length);
      break;
    }
  }

  // Remove common business suffixes
  cleaned = cleaned
    .replace(/\b(private|pvt|ltd|limited|inc|corp|llp|llc)\b/gi, '')
    .replace(/[_.\-\s]/g, '') // Remove underscores, dots, hyphens, spaces
    .replace(/\d{4,}$/g, '') // Remove trailing 4+ digit numbers (QR codes, etc.)
    .replace(/[^a-z0-9]/g, '') // Keep only alphanumeric
    .trim();

  return cleaned || '';
}

/**
 * Look up a learned category for a given merchant/UPI.
 * Returns { category, confidence } or null if no mapping exists.
 */
async function lookupLearnedCategory(userId, merchantRaw, upiId) {
  // Try UPI first (more specific), then merchant name
  const candidates = [upiId, merchantRaw].filter(Boolean);

  for (const raw of candidates) {
    const normalized = normalizeMerchant(raw);
    if (!normalized) continue;

    const mapping = await MerchantCategoryMapping.findOne({
      userId,
      normalizedMerchant: normalized,
    }).lean();

    if (mapping) {
      return { category: mapping.category, confidence: mapping.confidence, source: 'UserLearned' };
    }
  }

  return null;
}

/**
 * Learn a merchant → category mapping for a user.
 * Upserts so we always keep the latest user preference.
 */
async function learnMerchantCategory(userId, merchantRaw, upiId, category) {
  // Determine the best key to store — prefer UPI ID if available
  const merchantKey = upiId || merchantRaw || '';
  const normalized = normalizeMerchant(merchantKey);

  if (!normalized) return null;

  const mapping = await MerchantCategoryMapping.findOneAndUpdate(
    { userId, normalizedMerchant: normalized },
    {
      userId,
      merchantKey,
      normalizedMerchant: normalized,
      category,
      confidence: 1.0,
      learnedFromUser: true,
      source: 'UserLearned',
    },
    { upsert: true, new: true, setDefaultsOnInsert: true },
  );

  return mapping;
}

/**
 * Apply a learned category to ALL past transactions from the same merchant.
 * Returns the count of updated transactions.
 */
async function applyToPastTransactions(userId, merchantRaw, upiId, category) {
  const conditions = { userId, $or: [] };

  // Match by UPI ID
  if (upiId) {
    const normalizedUpi = normalizeMerchant(upiId);
    if (normalizedUpi) {
      conditions.$or.push({ normalizedMerchant: normalizedUpi });
    }
    // Also match raw UPI (for transactions that haven't been normalized yet)
    conditions.$or.push({
      upiId: { $regex: new RegExp('^' + upiId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$', 'i') },
    });
  }

  // Match by merchant name
  if (merchantRaw) {
    const normalizedMerch = normalizeMerchant(merchantRaw);
    if (normalizedMerch) {
      conditions.$or.push({ normalizedMerchant: normalizedMerch });
    }
    conditions.$or.push({
      merchant: { $regex: new RegExp('^' + merchantRaw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$', 'i') },
    });
  }

  if (conditions.$or.length === 0) return 0;

  const result = await Transaction.updateMany(conditions, {
    $set: {
      category,
      categorySource: 'UserLearned',
      normalizedMerchant: normalizeMerchant(upiId || merchantRaw || ''),
    },
  });

  return result.modifiedCount || 0;
}

/**
 * Get all merchant mappings for a user (for sync to mobile).
 */
async function getUserMappings(userId) {
  return MerchantCategoryMapping.find({ userId }).lean();
}

module.exports = {
  normalizeMerchant,
  lookupLearnedCategory,
  learnMerchantCategory,
  applyToPastTransactions,
  getUserMappings,
};
