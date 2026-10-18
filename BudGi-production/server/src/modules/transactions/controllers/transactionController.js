// modules/transactions/controllers/transactionController.js
// req/res ONLY — all logic in transactionService

'use strict';

const txService = require('../services/transactionService');
const { ok, created, asyncHandler } = require('../../../utils/response');

// GET /api/transactions
const getTransactions = asyncHandler(async (req, res) => {
  const { transactions, pagination } = await txService.listTransactions(req.user.id, req.query);
  return ok(res, { transactions }, 'OK', pagination);
});

// POST /api/transactions  (single body OR { transactions: [...] } for bulk)
const createTransaction = asyncHandler(async (req, res) => {
  const result = await txService.createTransaction(req.user.id, req.body);
  // Single: { transaction }  |  Bulk: { imported, duplicates, failed, total }
  const isBulk = !result.transaction;
  return isBulk
    ? ok(res, result, `${result.imported} transaction(s) imported`)
    : created(res, result, 'Transaction created');
});

// PUT /api/transactions/:id
const updateTransaction = asyncHandler(async (req, res) => {
  const result = await txService.updateTransaction(req.user.id, req.params.id, req.body);
  return ok(res, result, 'Transaction updated');
});

// DELETE /api/transactions/:id
const deleteTransaction = asyncHandler(async (req, res) => {
  await txService.deleteTransaction(req.user.id, req.params.id);
  return ok(res, {}, 'Transaction deleted');
});

// DELETE /api/transactions/clear
const clearTransactions = asyncHandler(async (req, res) => {
  const result = await txService.clearTransactions(req.user.id, req.query);
  return ok(res, result, `Successfully cleared ${result.deletedCount} transactions`);
});

// POST /api/transactions/import  (CSV file upload)
const importCSV = asyncHandler(async (req, res) => {
  const result = await txService.importFromCSV(req.user.id, req.file?.buffer);
  return ok(res, result, `${result.imported} transaction(s) imported`);
});

// POST /api/transactions/scan (Image file upload)
const scanReceipt = asyncHandler(async (req, res) => {
  if (!req.file) {
    return res.status(400).json({ success: false, message: 'Receipt image required' });
  }

  const Tesseract = require('tesseract.js');
  const aiService = require('../../ai/services/aiService');

  // 1. Run Tesseract OCR on the buffer
  const {
    data: { text },
  } = await Tesseract.recognize(req.file.buffer, 'eng');

  if (!text || text.trim().length < 5) {
    return res.status(400).json({ success: false, message: 'Could not extract text from image' });
  }

  // 2. Pass text to AI for parsing
  const parsed = await aiService.parseReceiptText(text);

  if (!parsed) {
    return res.status(500).json({ success: false, message: 'Failed to parse receipt data' });
  }

  return ok(res, parsed, 'Receipt scanned successfully');
});

// GET /api/transactions/summary
const getSummary = asyncHandler(async (req, res) => {
  const result = await txService.getSummary(req.user.id, req.query);
  return ok(res, result);
});

module.exports = {
  getTransactions,
  createTransaction,
  updateTransaction,
  deleteTransaction,
  clearTransactions,
  importCSV,
  scanReceipt,
  getSummary,
};
