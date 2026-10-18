// src/utils/validators.js
// Reusable input validators. Each returns null on pass or { field, message } on fail.
// Collect all failures into an array before throwing so clients get every error at once.

'use strict';

/**
 * Build a validation error.
 * Usage: throw validationError([{ field: 'amount', message: 'Must be a number' }])
 */
function validationError(errors) {
  return Object.assign(new Error(errors.map((e) => e.message).join('; ')), { statusCode: 400, errors });
}

/**
 * Validate a single transaction body.
 * Returns array of { field, message } — empty = valid.
 */
function validateTransactionBody({ amount, type, date, description, merchant }) {
  const errors = [];

  // amount
  if (amount === undefined || amount === null || amount === '') {
    errors.push({ field: 'amount', message: 'amount is required' });
  } else if (typeof amount !== 'number' && isNaN(Number(amount))) {
    errors.push({ field: 'amount', message: 'amount must be a number' });
  } else if (Number(amount) <= 0) {
    errors.push({ field: 'amount', message: 'amount must be greater than 0' });
  }

  // type
  if (!type) {
    errors.push({ field: 'type', message: 'type is required' });
  } else if (!['income', 'expense'].includes(type)) {
    errors.push({ field: 'type', message: 'type must be "income" or "expense"' });
  }

  // date
  if (!date) {
    errors.push({ field: 'date', message: 'date is required' });
  } else if (isNaN(new Date(date).getTime())) {
    errors.push({ field: 'date', message: 'date must be a valid ISO date string' });
  }

  // description optional but bounded
  if (description && typeof description !== 'string') {
    errors.push({ field: 'description', message: 'description must be a string' });
  }

  // merchant optional but bounded
  if (merchant && typeof merchant !== 'string') {
    errors.push({ field: 'merchant', message: 'merchant must be a string' });
  }

  return errors;
}

/**
 * Validate that `items` is a non-empty array capped at maxLen.
 */
function validateBulkArray(items, maxLen = 500) {
  const errors = [];
  if (!Array.isArray(items)) {
    errors.push({ field: 'transactions', message: 'transactions must be an array' });
  } else if (items.length === 0) {
    errors.push({ field: 'transactions', message: 'transactions array is empty' });
  } else if (items.length > maxLen) {
    errors.push({ field: 'transactions', message: `Maximum ${maxLen} transactions per request` });
  }
  return errors;
}

/**
 * Validate chat messages array.
 */
function validateChatMessages(messages) {
  const errors = [];
  if (!Array.isArray(messages) || messages.length === 0) {
    errors.push({ field: 'messages', message: 'messages must be a non-empty array' });
    return errors;
  }
  if (messages.length > 20) {
    errors.push({ field: 'messages', message: 'Maximum 20 messages per request' });
  }
  messages.forEach((m, i) => {
    if (!m.role || !['user', 'assistant', 'system'].includes(m.role)) {
      errors.push({ field: `messages[${i}].role`, message: 'role must be user, assistant, or system' });
    }
    if (!m.content || typeof m.content !== 'string') {
      errors.push({ field: `messages[${i}].content`, message: 'content must be a non-empty string' });
    }
  });
  return errors;
}

module.exports = {
  validationError,
  validateTransactionBody,
  validateBulkArray,
  validateChatMessages,
};
