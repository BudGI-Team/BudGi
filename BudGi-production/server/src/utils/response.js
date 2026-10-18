// src/utils/response.js
// Strict response envelope: every endpoint returns { success, message, data }
// Errors additionally include { errors: [] } for field-level detail.

'use strict';

/**
 * 200 OK  →  { success: true, message, data, meta? }
 * Pass meta for pagination: { page, limit, total, pages }
 */
function ok(res, data = {}, message = 'Success', meta = null) {
  const body = { success: true, message, data };
  if (meta) body.meta = meta;
  return res.status(200).json(body);
}

/**
 * 201 Created  →  { success: true, message, data }
 */
function created(res, data = {}, message = 'Created') {
  return res.status(201).json({ success: true, message, data });
}

/**
 * Error  →  { success: false, message, data: null, errors? }
 * `errors` is an optional array of { field, message } objects for validation detail.
 */
function error(res, message = 'An error occurred', statusCode = 400, errors = null) {
  const body = { success: false, message, data: null };
  if (errors) body.errors = errors;
  return res.status(statusCode).json(body);
}

function notFound(res, message = 'Resource not found') {
  return error(res, message, 404);
}

function unauthorized(res, message = 'Unauthorized') {
  return error(res, message, 401);
}

function forbidden(res, message = 'Forbidden') {
  return error(res, message, 403);
}

const logger = require('./logger');

function serverError(res, err) {
  logger.error('API Internal server error', err);
  return error(res, 'Internal server error', 500);
}

/**
 * Async handler — catches ALL thrown errors and maps them to the envelope.
 * Services signal errors via:
 *   throw Object.assign(new Error('msg'), { statusCode: 400, errors: [...] })
 */
function asyncHandler(fn) {
  return (req, res, next) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
}

module.exports = {
  ok,
  created,
  error,
  notFound,
  unauthorized,
  forbidden,
  serverError,
  asyncHandler,
};
