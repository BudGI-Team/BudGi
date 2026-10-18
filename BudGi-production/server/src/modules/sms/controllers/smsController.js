// modules/sms/controllers/smsController.js
// Handles SMS parsing requests from the Android app

'use strict';

const smsParser = require('../services/smsParserService');
const txService = require('../../transactions/services/transactionService');
const { ok, asyncHandler } = require('../../../utils/response');
const { validationError } = require('../../../utils/validators');

// POST /api/sms/parse — parse a single SMS without saving
const parseSingle = asyncHandler(async (req, res) => {
  const { text, sender, timestamp } = req.body;
  if (!text) {
    throw validationError([{ field: 'text', message: 'SMS text is required' }]);
  }

  const result = await smsParser.parseSMS(text, sender, timestamp);
  return ok(res, result);
});

// POST /api/sms/parse-batch — parse multiple SMS messages without saving
const parseBatch = asyncHandler(async (req, res) => {
  const { messages } = req.body;
  if (!Array.isArray(messages)) {
    throw validationError([{ field: 'messages', message: 'messages must be an array' }]);
  }
  if (messages.length > 200) {
    throw validationError([{ field: 'messages', message: 'Maximum 200 SMS messages per batch' }]);
  }

  const result = await smsParser.parseSMSBatch(messages);
  return ok(res, result, `${result.transactions.length} SMS parsed successfully`);
});

// POST /api/sms/parse-llm — parse a batch of unparsed SMS using LLM
const parseLLM = asyncHandler(async (req, res) => {
  const { messages } = req.body;
  if (!Array.isArray(messages)) {
    throw validationError([{ field: 'messages', message: 'messages must be an array' }]);
  }
  if (messages.length > 50) {
    throw validationError([{ field: 'messages', message: 'Maximum 50 SMS messages per LLM batch' }]);
  }

  const result = await smsParser.parseSMSBatchLLM(messages);
  return ok(res, result, `${result.transactions.length} SMS parsed via LLM successfully`);
});

// POST /api/sms/import — parse and SAVE SMS as transactions
const importSMS = asyncHandler(async (req, res) => {
  const { messages } = req.body;
  if (!Array.isArray(messages)) {
    throw validationError([{ field: 'messages', message: 'messages must be an array' }]);
  }
  if (messages.length > 200) {
    throw validationError([{ field: 'messages', message: 'Maximum 200 SMS messages per batch' }]);
  }

  // Step 1: Parse all SMS
  const parsed = await smsParser.parseSMSBatch(messages);

  if (parsed.transactions.length === 0) {
    return ok(
      res,
      {
        imported: 0,
        parsed: 0,
        failed: parsed.failed.length,
        duplicates: 0,
        total: parsed.total,
        failures: parsed.failed.slice(0, 10), // show first 10 failures
      },
      'No valid transactions found in SMS messages',
    );
  }

  // Step 2: Bulk create (dedup handled by transactionService)
  const txns = parsed.transactions.map((t) => ({
    amount: t.amount,
    type: t.type,
    category: t.category,
    description: t.description,
    merchant: t.merchant,
    date: t.date,
    source: 'sms',
    upiId: t.upiId,
    referenceNumber: t.referenceNumber,
    accountLast4: t.accountLast4,
    availableBalance: t.availableBalance,
    smsBodyDate: t.smsBodyDate,
    sender: t.sender,
    rawSms: t.rawSms,
  }));

  const importResult = await txService.createTransaction(req.user.id, txns);

  return ok(
    res,
    {
      imported: importResult.imported || 0,
      parsed: parsed.transactions.length,
      failed: parsed.failed.length,
      duplicates: importResult.duplicates || 0,
      total: parsed.total,
      failures: parsed.failed.slice(0, 10),
    },
    `${importResult.imported || 0} transaction(s) imported from SMS`,
  );
});

// POST /api/sms/verify-scam — check if SMS is a scam
const verifyScam = asyncHandler(async (req, res) => {
  const { text } = req.body;
  if (!text) {
    throw validationError([{ field: 'text', message: 'SMS text is required' }]);
  }

  const { verifySmishing } = require('../../ai/services/aiService');
  const result = await verifySmishing(text);

  return ok(res, result, result.isScam ? 'Scam detected' : 'Safe');
});

module.exports = { parseSingle, parseBatch, parseLLM, importSMS, verifyScam };
