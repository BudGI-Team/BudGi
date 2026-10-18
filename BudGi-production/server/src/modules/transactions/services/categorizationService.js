// src/services/categorizationService.js
/**
 * Categorization Service — Hybrid keyword + AI transaction categorizer
 *
 * Strategy:
 *  1. Keyword match (instant, free)
 *  2. Groq AI fallback (for unknown merchants)
 *  3. Batch categorize CSV imports efficiently
 */

'use strict';

const { categorizeTransaction, keywordCategory } = require('../../ai/services/aiService');

/**
 * Categorize a single transaction.
 * Returns { category, confidence, source }
 */
async function categorize(description, merchant = '') {
  const text = [description, merchant].filter(Boolean).join(' ');

  // 1. Fast keyword match
  const kw = keywordCategory(text);
  if (kw) return { category: kw, confidence: 0.95, source: 'keyword' };

  // 2. AI fallback
  return categorizeTransaction(text);
}

/**
 * Batch categorize an array of transaction objects.
 * Uses keyword first; falls back to AI for unknowns.
 * Throttles AI calls to avoid rate limits.
 *
 * @param {Array<{description, merchant}>} items
 * @returns {Promise<Array<{category, confidence, source}>>}
 */
async function categorizeBatch(items) {
  const results = [];
  const aiQueue = []; // indices needing AI

  // First pass: keyword only
  for (let i = 0; i < items.length; i++) {
    const { description, merchant, category: manualCategory } = items[i];
    if (manualCategory) {
      results[i] = { category: manualCategory, confidence: 1.0, source: 'manual' };
      continue;
    }

    const text = [description, merchant].filter(Boolean).join(' ');
    const kw = keywordCategory(text);
    if (kw) {
      results[i] = { category: kw, confidence: 0.95, source: 'keyword' };
    } else {
      results[i] = null;
      aiQueue.push(i);
    }
  }

  // Second pass: AI for unknowns (with throttling — 5 concurrent)
  const CONCURRENCY = 5;
  for (let i = 0; i < aiQueue.length; i += CONCURRENCY) {
    const batch = aiQueue.slice(i, i + CONCURRENCY);
    await Promise.all(
      batch.map(async (idx) => {
        const { description, merchant } = items[idx];
        const text = [description, merchant].filter(Boolean).join(' ');
        results[idx] = await categorizeTransaction(text);
      }),
    );
    // Small delay between AI batches to avoid rate limiting
    if (i + CONCURRENCY < aiQueue.length) {
      await new Promise((r) => setTimeout(r, 200));
    }
  }

  return results;
}

/**
 * Return category statistics for a set of transactions.
 */
function getCategoryStats(transactions) {
  const stats = {};
  for (const t of transactions) {
    const cat = t.category || 'Other';
    if (!stats[cat]) stats[cat] = { count: 0, total: 0 };
    stats[cat].count++;
    stats[cat].total += t.amount;
  }
  return Object.entries(stats)
    .sort((a, b) => b[1].total - a[1].total)
    .map(([category, data]) => ({ category, ...data, total: Math.round(data.total) }));
}

module.exports = { categorize, categorizeBatch, getCategoryStats };
