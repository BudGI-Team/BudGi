// src/utils/csvParser.js
/**
 * CSV Parser — Bank statement import utility
 *
 * Handles common Indian bank CSV formats:
 *  - HDFC, ICICI, SBI, Axis, Kotak
 *  - Generic date/amount/description format
 *
 * Returns normalized transaction objects ready for DB insertion.
 */

'use strict';

const { parse } = require('csv-parse/sync');

// Common column aliases across bank exports
const COLUMN_MAP = {
  date: ['date', 'txn date', 'transaction date', 'value date', 'posting date'],
  description: ['description', 'narration', 'particulars', 'details', 'remarks', 'transaction remarks'],
  debit: ['debit', 'debit amount', 'withdrawal', 'withdrawal amt', 'dr'],
  credit: ['credit', 'credit amount', 'deposit', 'deposit amt', 'cr'],
  amount: ['amount', 'txn amount', 'transaction amount'],
  balance: ['balance', 'closing balance', 'available balance'],
};

/**
 * Parse a CSV buffer/string into normalized transaction objects.
 *
 * @param {Buffer|string} csvData
 * @param {Object} options
 * @param {string} options.defaultType  — 'expense' | 'income'
 * @returns {{ transactions: Array, errors: Array, skipped: number }}
 */
function parseCSV(csvData, { defaultType = 'expense' } = {}) {
  let records;

  try {
    records = parse(csvData, {
      columns: true, // Use first row as headers
      skip_empty_lines: true,
      trim: true,
      relax_column_count: true,
      bom: true, // Handle UTF-8 BOM from Excel exports
    });
  } catch (err) {
    throw new Error(`CSV parse error: ${err.message}`);
  }

  if (!records.length) {
    throw new Error('CSV file is empty or has no data rows');
  }

  // Normalise column headers (lowercase, trim)
  const normalisedRecords = records.map((row) => {
    const norm = {};
    for (const [key, val] of Object.entries(row)) {
      norm[key.toLowerCase().trim()] = val?.trim() ?? '';
    }
    return norm;
  });

  const headers = Object.keys(normalisedRecords[0]);
  const colIndex = resolveColumns(headers);

  const transactions = [];
  const errors = [];
  let skipped = 0;

  for (let i = 0; i < normalisedRecords.length; i++) {
    const row = normalisedRecords[i];
    const rowNum = i + 2; // +2 for 1-indexed + header row

    try {
      const tx = parseRow(row, colIndex, defaultType);
      if (tx) {
        transactions.push(tx);
      } else {
        skipped++;
      }
    } catch (err) {
      errors.push({ row: rowNum, error: err.message, data: row });
    }
  }

  return { transactions, errors, skipped };
}

// ── Internal helpers ─────────────────────────────────────────────────────────

/**
 * Map standardized column keys to actual CSV header names.
 */
function resolveColumns(headers) {
  const result = {};
  for (const [key, aliases] of Object.entries(COLUMN_MAP)) {
    const found = aliases.find((alias) => headers.includes(alias));
    if (found) result[key] = found;
  }
  return result;
}

/**
 * Parse a single CSV row into a transaction object.
 * Returns null if the row should be skipped (e.g. opening balance row).
 */
function parseRow(row, colIndex, defaultType) {
  // ── Date ──
  const rawDate = colIndex.date ? row[colIndex.date] : null;
  if (!rawDate) throw new Error('Missing date');

  const date = parseDate(rawDate);
  if (!date) throw new Error(`Unrecognised date format: "${rawDate}"`);

  // ── Amount / Type ──
  let amount = 0;
  let type = defaultType;

  if (colIndex.debit && colIndex.credit) {
    // Separate debit/credit columns (most Indian banks)
    const debit = parseAmount(row[colIndex.debit]);
    const credit = parseAmount(row[colIndex.credit]);

    if (debit > 0) {
      amount = debit;
      type = 'expense';
    } else if (credit > 0) {
      amount = credit;
      type = 'income';
    } else {
      return null; // Skip zero-amount rows
    }
  } else if (colIndex.amount) {
    amount = parseAmount(row[colIndex.amount]);
    if (!amount) return null;
  } else {
    throw new Error('No amount column found');
  }

  // ── Description ──
  const description = colIndex.description ? row[colIndex.description]?.replace(/\s+/g, ' ').slice(0, 300) || '' : '';

  // Skip rows that look like balance/summary lines
  if (SKIP_PATTERNS.some((p) => description.toLowerCase().includes(p))) {
    return null;
  }

  return {
    date,
    amount: Math.abs(parseFloat(amount.toFixed(2))),
    type,
    description,
    merchant: extractMerchant(description),
    source: 'csv',
    category: null, // Will be filled by categorizationService
  };
}

/**
 * Parse a date string in common Indian bank formats.
 */
function parseDate(str) {
  if (!str) return null;
  const s = str.trim().replace(/\//g, '-');

  // DD-MM-YYYY  or  DD-MM-YY
  let m = s.match(/^(\d{1,2})-(\d{1,2})-(\d{2,4})$/);
  if (m) {
    const year = m[3].length === 2 ? `20${m[3]}` : m[3];
    const d = new Date(Date.UTC(parseInt(year), parseInt(m[2]) - 1, parseInt(m[1])));
    if (!isNaN(d)) return d;
  }

  // YYYY-MM-DD (ISO)
  m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (m) {
    const d = new Date(s);
    if (!isNaN(d)) return d;
  }

  // DD-Mon-YYYY (e.g. 01-Jan-2024)
  m = s.match(/^(\d{1,2})-([A-Za-z]{3})-(\d{4})$/);
  if (m) {
    const d = new Date(`${m[1]} ${m[2]} ${m[3]}`);
    if (!isNaN(d)) return d;
  }

  // Try JS Date constructor as last resort
  const fallback = new Date(str);
  return isNaN(fallback) ? null : fallback;
}

/**
 * Parse amount string, handling commas and currency symbols.
 */
function parseAmount(str) {
  if (!str) return 0;
  const cleaned = str.replace(/[₹$,\s]/g, '');
  const num = parseFloat(cleaned);
  return isNaN(num) ? 0 : Math.abs(num);
}

/**
 * Extract a short merchant name from the full description.
 */
function extractMerchant(description) {
  if (!description) return '';
  // Take first meaningful segment before common delimiters
  const parts = description.split(/[-\/|*@]/);
  return parts[0].trim().slice(0, 60);
}

// Rows matching these patterns are skipped (not real transactions)
const SKIP_PATTERNS = [
  'opening balance',
  'closing balance',
  'total',
  'brought forward',
  'carried forward',
  'statement period',
];

module.exports = { parseCSV };
