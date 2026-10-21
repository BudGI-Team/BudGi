// src/middleware/auth.js
/**
 * JWT Authentication Middleware
 * Attaches req.user = { id, email } on success.
 */

'use strict';

const jwt = require('jsonwebtoken');
const env = require('../config/env');
const { unauthorized } = require('../utils/response');

function authMiddleware(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    return unauthorized(res, 'No token provided');
  }

  const token = authHeader.slice(7);
  try {
    const payload = jwt.verify(token, env.JWT_SECRET);

    // Block pre-auth tokens from accessing protected routes
    if (payload.stage === 'PHONE_VERIFICATION_REQUIRED') {
      return unauthorized(res, 'Phone verification required. Please complete verification first.');
    }

    req.user = { id: payload.id, email: payload.email };
    next();
  } catch (err) {
    const message = err.name === 'TokenExpiredError' ? 'Token expired' : 'Invalid token';
    return unauthorized(res, message);
  }
}

module.exports = authMiddleware;
