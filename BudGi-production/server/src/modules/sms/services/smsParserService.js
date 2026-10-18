// modules/sms/services/smsParserService.js
//
// Production-grade SMS parser for Indian bank/UPI transaction messages.
// BudGi Precision Intelligent Heuristics
//
// Exports:
//   parseSMS(smsText, senderAddress?, timestamp?)  → { parsed, transaction } | { parsed, reason }
//   parseSMSBatch(messages)                        → { transactions, failed, total }
//   parseSMSLLMFallback(smsText, ...)
//   parseSMSBatchLLM(messages)

'use strict';

const { categorizeTransaction } = require('../../ai/services/aiService');

// ─────────────────────────────────────────────────────────────────────────────
// Amount extraction patterns
// ─────────────────────────────────────────────────────────────────────────────

const AMOUNT_PATTERNS = [
  // Rs.1,234.56 or Rs 1234 or INR 1,234.00 or ₹500
  /(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)/i,
  // 1234.00 debited/credited
  /([\d,]+\.?\d*)\s*(?:debited|credited|paid|received)/i,
  // amt: Rs.1234
  /(?:amt|amount|txn\s*amt)[:\s]*(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)/i,
];

// ─────────────────────────────────────────────────────────────────────────────
// Transaction type detection — heuristic proximity
// ─────────────────────────────────────────────────────────────────────────────

const DEBIT_KEYWORDS = [
  'dr.',
  'dr ',
  'debited',
  'debit',
  'withdrawn',
  'spent',
  'paid',
  'purchase',
  'payment',
  'sent to',
  'transferred to',
  'withdrawal',
];
const CREDIT_KEYWORDS = [
  'cr. in',
  'credited',
  'received',
  'deposited',
  'refund',
  'reversed',
  'salary',
  'transferred from',
  'neft from',
  'imps from',
  'cr.',
];

function detectType(text) {
  const lower = text.toLowerCase();

  // 1. Explicit combined contexts (highest priority)
  if (lower.includes('dr. from a/c') && lower.includes('cr. to')) return 'expense';
  if (lower.includes('cr. in a/c')) return 'income';

  // 2. Proximity-based detection
  let amountIdx = -1;
  for (const pattern of AMOUNT_PATTERNS) {
    const match = lower.match(pattern);
    if (match) {
      amountIdx = match.index;
      break;
    }
  }

  if (amountIdx !== -1) {
    const windowStart = Math.max(0, amountIdx - 50);
    const windowEnd = Math.min(lower.length, amountIdx + 50);
    const window = lower.substring(windowStart, windowEnd);

    let debitIdx = Infinity;
    DEBIT_KEYWORDS.forEach((kw) => {
      const idx = window.indexOf(kw);
      if (idx !== -1 && idx < debitIdx) debitIdx = idx;
    });

    let creditIdx = Infinity;
    CREDIT_KEYWORDS.forEach((kw) => {
      const idx = window.indexOf(kw);
      if (idx !== -1 && idx < creditIdx) creditIdx = idx;
    });

    if (debitIdx < creditIdx) return 'expense';
    if (creditIdx < debitIdx) return 'income';
  }

  // 3. Fallback to simple presence
  for (const kw of DEBIT_KEYWORDS) {
    if (lower.includes(kw)) return 'expense';
  }
  for (const kw of CREDIT_KEYWORDS) {
    if (lower.includes(kw)) return 'income';
  }
  return 'expense'; // default
}

// ─────────────────────────────────────────────────────────────────────────────
// Merchant / payee extraction
// ─────────────────────────────────────────────────────────────────────────────

const NOISE_WORDS = new Set([
  'a',
  'an',
  'the',
  'to',
  'from',
  'for',
  'of',
  'in',
  'on',
  'your',
  'bank',
  'account',
  'card',
  'upi',
  'neft',
  'imps',
  'and',
  'is',
  'was',
  'has',
  'been',
  'ref',
  'avlbal',
]);

const MERCHANT_PATTERNS = [
  // "Cr. to merchant@bank"
  /(?:cr\.?\s+to|paid\s+to|sent\s+to|transferred\s+to|to)\s+([A-Za-z0-9@._\-]+(?:\s+[A-Za-z0-9@._\-]+)*)(?:\.|\s+ref|\s+on|\n|$)/i,
  // "transfer from Divyanshu Dixit Ref No"
  /(?:from)\s+([A-Z][A-Za-z0-9\s&'.\-]{2,30}?)(?:\s+ref|\s*\.|\s*$)/i,
  // "AT POS TID-3074036A/P"
  /AT\s+POS\s+([A-Za-z0-9\s\-]+?)(?:,|\.|\s+ref)/i,
];

function extractMerchant(text, upiId) {
  if (upiId) {
    const upiMerchant = extractMerchantFromUpiId(upiId);
    if (upiMerchant) return upiMerchant;
  }

  for (const pattern of MERCHANT_PATTERNS) {
    const match = text.match(pattern);
    if (match) {
      let merchant = match[1].trim();
      if (merchant.length > 1 && !NOISE_WORDS.has(merchant.toLowerCase()) && !/^\d+$/.test(merchant)) {
        merchant = merchant.replace(/\s+(ref|on|via|txn|at|date).*$/i, '');
        return merchant.charAt(0).toUpperCase() + merchant.slice(1);
      }
    }
  }
  return null;
}

// ─────────────────────────────────────────────────────────────────────────────
// UPI ID extraction
// ─────────────────────────────────────────────────────────────────────────────

function extractUpiId(text) {
  const match = text.match(/([a-zA-Z0-9._-]+@[a-zA-Z]{2,20})/i);
  if (match) {
    const id = match[1].toLowerCase();
    // Skip email addresses
    if (/gmail|yahoo|hotmail|outlook|rediffmail/.test(id)) return null;
    if (id.length >= 5 && id.length <= 50) return id;
  }
  return null;
}

function extractMerchantFromUpiId(upiId) {
  const localPart = upiId.split('@')[0];
  if (/^\d+$/.test(localPart)) return null;
  if (localPart.length < 2) return null;

  const cleaned = localPart
    .replace(/\.(payu|razorpay|payments?|pay|merchant|upi)$/i, '')
    .replace(/[.\-_]/g, ' ')
    .trim();

  if (!cleaned || cleaned.length < 2) return null;
  return cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
}

// ─────────────────────────────────────────────────────────────────────────────
// Reference number extraction
// ─────────────────────────────────────────────────────────────────────────────

function extractRefNumber(text) {
  let match = text.match(/(?:ref\s*no|ref|txn\s*id|txn\s*no|txn|upi\s*ref|imps\s*ref)[:\s-]*(\d{8,22})/i);
  if (match) return match[1];

  match = text.match(/from:CRA\/(\d{10,22})/i); // Bank of Baroda
  if (match) return match[1];

  return null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Available balance extraction
// ─────────────────────────────────────────────────────────────────────────────

function extractBalance(text) {
  let match = text.match(
    /(?:avl\s*bal|avlbl\s*amt|avlbl|available\s*balance|bal|avlbal)[:\s-]*(?:rs\.?|inr|₹)?\s*([\d,]+\.?\d*)(?:cr|dr)?/i,
  );
  if (match) {
    return parseFloat(match[1].replace(/,/g, ''));
  }

  match = text.match(/Total\s*Bal[:\s]*(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)/i);
  if (match) {
    return parseFloat(match[1].replace(/,/g, ''));
  }

  return null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Date extraction — Triangulation
// ─────────────────────────────────────────────────────────────────────────────

const DATE_PATTERNS = [
  // (2026:03:31 02:43:21) or (2026:03:25 05:44:33)
  /\(?(\d{4}):(\d{2}):(\d{2})\s+(\d{2}):(\d{2}):(\d{2})\)?/,
  // (23-02-2026 13:12:37) or 13-02-26 20:52:06
  /(\d{2})[-/](\d{2})[-/](\d{4}|\d{2})(?:\s+(\d{2}):(\d{2}):(\d{2}))?/,
  // 02Apr26 or 28 Apr 2025
  /(\d{1,2})\s*(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s*'?(\d{2,4})/i,
];

const MONTH_MAP = {
  jan: 0,
  feb: 1,
  mar: 2,
  apr: 3,
  may: 4,
  jun: 5,
  jul: 6,
  aug: 7,
  sep: 8,
  oct: 9,
  nov: 10,
  dec: 11,
};

function isReasonableDate(date) {
  const now = Date.now();
  const sixMonthsAgo = now - 180 * 24 * 60 * 60 * 1000;
  const oneDayAhead = now + 24 * 60 * 60 * 1000;
  return date.getTime() >= sixMonthsAgo && date.getTime() <= oneDayAhead;
}

function extractDate(text) {
  // Try YYYY:MM:DD HH:mm:ss first
  const ymdMatch = text.match(DATE_PATTERNS[0]);
  if (ymdMatch) {
    const year = parseInt(ymdMatch[1], 10);
    const month = parseInt(ymdMatch[2], 10) - 1;
    const day = parseInt(ymdMatch[3], 10);
    const hour = parseInt(ymdMatch[4], 10);
    const min = parseInt(ymdMatch[5], 10);
    const sec = parseInt(ymdMatch[6], 10);
    if (year >= 2020 && year <= 2030 && month >= 0 && month <= 11 && day >= 1 && day <= 31) {
      const d = new Date(Date.UTC(year, month, day, hour - 5, min - 30, sec));
      if (isReasonableDate(d)) return d;
    }
  }

  // Try dd-MM-yyyy HH:mm:ss
  const numMatch = text.match(DATE_PATTERNS[1]);
  if (numMatch) {
    const day = parseInt(numMatch[1], 10);
    const month = parseInt(numMatch[2], 10) - 1;
    let year = parseInt(numMatch[3], 10);
    if (year < 100) year += 2000;

    const hour = parseInt(numMatch[4] || 0, 10);
    const min = parseInt(numMatch[5] || 0, 10);
    const sec = parseInt(numMatch[6] || 0, 10);

    if (month >= 0 && month <= 11 && day >= 1 && day <= 31 && year >= 2020 && year <= 2030) {
      const d = new Date(Date.UTC(year, month, day, hour - 5, min - 30, sec));
      if (isReasonableDate(d)) return d;
    }
  }

  // Try "dd MMM yyyy"
  const namedMatch = text.match(DATE_PATTERNS[2]);
  if (namedMatch) {
    const day = parseInt(namedMatch[1], 10);
    const month = MONTH_MAP[namedMatch[2].toLowerCase().slice(0, 3)];
    let year = parseInt(namedMatch[3], 10);
    if (year < 100) year += 2000;
    if (month !== undefined && day >= 1 && day <= 31 && year >= 2020 && year <= 2030) {
      const d = new Date(Date.UTC(year, month, day, -5, -30, 0));
      if (isReasonableDate(d)) return d;
    }
  }

  return null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Account number extraction (last 4-6 digits)
// ─────────────────────────────────────────────────────────────────────────────

function extractAccount(text) {
  let match = text.match(/(?:a\/c|acct|ac|account)\s*(?:no\.?\s*)?[-:\s]*[*Xx.]+\s*(\d{4,6})/i);
  if (match) return match[1];

  match = text.match(/(?:a\/c|acct|ac|account)\s*(?:no\.?\s*)?[-:\s]*X+(\d{4,6})/i);
  if (match) return match[1];

  match = text.match(/(?:a\/c|acct|ac|account)\s*(?:no\.?\s*)?[-:\s]*x+(\d{4,6})/i);
  if (match) return match[1];

  match = text.match(/a\/c\s*\.{3}(\d{4,6})/i);
  if (match) return match[1];

  return null;
}

// ─────────────────────────────────────────────────────────────────────────────
// OTP / Promotional / Transaction detection
// ─────────────────────────────────────────────────────────────────────────────

const OTP_REGEX =
  /\b(?:otp|one time password|verification code|do not share|don't share|valid for|expires in|one-time|verify your|security code|2fa|two factor|login code|authenticate)\b/i;
const PROMO_REGEX =
  /\b(?:pre-?approved|loan offer|apply now|instant loan|personal loan|home loan|credit limit|emi offer|cashback offer|reward points|bonus|winner|lucky|contest|prize|limited time|special offer|exclusive offer|hurry|last chance|don't miss|offer valid|use code|promo code|discount|subscribe|upgrade|activate|click here|download app|congratulations|gift card|invest now|insurance plan|start sip)\b/i;
const TRANSACTION_SIGNAL_REGEX =
  /\b(?:dr\.|cr\.|debited|credited|withdrawn|transferred|paid|received|purchase|payment|debit|credit|withdrawal|txn|transaction|spent|deposited|refund|a\/c|avlbal|avl bal|available balance)\b/i;

// ─────────────────────────────────────────────────────────────────────────────
// Main parser
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parse a single SMS message into a transaction object.
 */
async function parseSMS(smsText, sender = '', timestamp = null) {
  if (!smsText || typeof smsText !== 'string' || smsText.length < 15) {
    return { parsed: false, reason: 'SMS text too short or missing' };
  }

  const text = smsText.trim();

  // Skip OTP messages
  if (OTP_REGEX.test(text)) {
    return { parsed: false, reason: 'OTP/verification message — skipped' };
  }

  // Skip promotional if no transaction signal
  if (PROMO_REGEX.test(text) && !TRANSACTION_SIGNAL_REGEX.test(text)) {
    return { parsed: false, reason: 'Promotional message — skipped' };
  }

  // Must have a transaction signal
  if (!TRANSACTION_SIGNAL_REGEX.test(text)) {
    return { parsed: false, reason: 'No transaction signal found' };
  }

  // Extract amount
  let amount = null;
  for (const pattern of AMOUNT_PATTERNS) {
    const match = text.match(pattern);
    if (match) {
      amount = parseFloat(match[1].replace(/,/g, ''));
      if (amount > 0) break;
    }
  }

  if (!amount || amount <= 0) {
    return { parsed: false, reason: 'No valid amount found in SMS' };
  }

  // Extract all fields
  const type = detectType(text);
  const upiId = extractUpiId(text);
  const merchant = extractMerchant(text, upiId);
  const refNumber = extractRefNumber(text);
  const balance = extractBalance(text);
  const account = extractAccount(text);

  const bodyDate = extractDate(text);
  const date = bodyDate || (timestamp ? new Date(timestamp) : new Date());

  const description = merchant
    ? `${type === 'income' ? 'Received from' : 'Payment to'} ${merchant}`
    : text.slice(0, 120);

  // AI categorization
  let category = null;
  try {
    const result = await categorizeTransaction(merchant || upiId || description);
    category = result.category;
  } catch {
    category = 'Other';
  }

  return {
    parsed: true,
    transaction: {
      amount,
      type,
      category: category || 'Other',
      description: description.slice(0, 300),
      merchant: (merchant || '').slice(0, 120),
      date: date.toISOString(),
      source: 'sms',
      upiId: upiId || null,
      referenceNumber: refNumber || null,
      accountLast4: account || null,
      availableBalance: balance || null,
      smsBodyDate: bodyDate ? bodyDate.toISOString() : null,
      sender: sender || null,
      rawSms: text.slice(0, 500),
    },
  };
}

/**
 * Parse a batch of SMS messages.
 */
async function parseSMSBatch(messages) {
  if (!Array.isArray(messages) || messages.length === 0) {
    return { transactions: [], failed: [], total: 0 };
  }

  const cap = Math.min(messages.length, 200);
  const transactions = [];
  const failed = [];

  for (let i = 0; i < cap; i++) {
    const msg = messages[i];
    try {
      const result = await parseSMS(msg.text || msg.body, msg.sender || msg.address, msg.timestamp || msg.date);
      if (result.parsed) {
        transactions.push(result.transaction);
      } else {
        failed.push({ index: i, reason: result.reason, preview: (msg.text || msg.body || '').slice(0, 80) });
      }
    } catch (err) {
      failed.push({ index: i, reason: err.message, preview: (msg.text || msg.body || '').slice(0, 80) });
    }
  }

  return { transactions, failed, total: messages.length };
}

/**
 * Parse a single SMS using LLM fallback
 */
async function parseSMSLLMFallback(smsText, sender = '', timestamp = null) {
  if (!smsText || typeof smsText !== 'string' || smsText.length < 15) {
    return { parsed: false, reason: 'SMS text too short' };
  }

  const { parseSMSWithLLM, categorizeTransaction } = require('../../ai/services/aiService');

  const text = smsText.trim();
  const llmResult = await parseSMSWithLLM(text);

  if (!llmResult || !llmResult.amount || llmResult.amount <= 0) {
    return { parsed: false, reason: 'LLM could not extract valid amount' };
  }

  const date = llmResult.date ? new Date(llmResult.date) : timestamp ? new Date(timestamp) : new Date();
  const type = llmResult.type === 'income' ? 'income' : 'expense';

  const description = llmResult.merchant
    ? `${type === 'income' ? 'Received from' : 'Payment to'} ${llmResult.merchant}`
    : text.slice(0, 120);

  let category = 'Other';
  try {
    const result = await categorizeTransaction(llmResult.merchant || llmResult.upiId || description);
    category = result.category;
  } catch {
    category = 'Other';
  }

  return {
    parsed: true,
    transaction: {
      amount: llmResult.amount,
      type,
      category,
      description: description.slice(0, 300),
      merchant: (llmResult.merchant || '').slice(0, 120),
      date: date.toISOString(),
      source: 'sms_llm',
      upiId: llmResult.upiId || null,
      referenceNumber: llmResult.referenceNumber ? String(llmResult.referenceNumber) : null,
      accountLast4: llmResult.accountLast4 ? String(llmResult.accountLast4) : null,
      availableBalance: llmResult.availableBalance || null,
      smsBodyDate: llmResult.date ? date.toISOString() : null,
      sender: sender || null,
      rawSms: text.slice(0, 500),
    },
  };
}

/**
 * Parse a batch of SMS messages using LLM.
 */
async function parseSMSBatchLLM(messages) {
  if (!Array.isArray(messages) || messages.length === 0) {
    return { transactions: [], failed: [], total: 0 };
  }

  const cap = Math.min(messages.length, 50);
  const transactions = [];
  const failed = [];

  for (let i = 0; i < cap; i++) {
    const msg = messages[i];
    try {
      const result = await parseSMSLLMFallback(
        msg.text || msg.body,
        msg.sender || msg.address,
        msg.timestamp || msg.date,
      );
      if (result.parsed) {
        transactions.push(result.transaction);
      } else {
        failed.push({ index: i, reason: result.reason, preview: (msg.text || msg.body || '').slice(0, 80) });
      }
    } catch (err) {
      failed.push({ index: i, reason: err.message, preview: (msg.text || msg.body || '').slice(0, 80) });
    }
  }

  return { transactions, failed, total: messages.length };
}

module.exports = { parseSMS, parseSMSBatch, parseSMSLLMFallback, parseSMSBatchLLM };
