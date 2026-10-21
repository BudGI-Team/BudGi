// modules/auth/services/smsService.js
'use strict';

const logger = require('../../../utils/logger');
const env = require('../../../config/env');

/**
 * Dispatch real cellular telecom SMS via TextBee Android Gateway
 * @param {Object} options
 * @param {string} options.phone - Recipient mobile number (e.g. +916390277049 or 10 digits)
 * @param {string} options.message - Text message content
 */
async function sendSms({ phone, message }) {
  const apiKey = env.TEXTBEE_API_KEY || process.env.TEXTBEE_API_KEY;
  const deviceId = env.TEXTBEE_DEVICE_ID || process.env.TEXTBEE_DEVICE_ID;

  if (!apiKey || !deviceId) {
    logger.warn('[SMS] TextBee API Key or Device ID not configured in environment. Skipping telecom dispatch.');
    return { success: false, warning: 'TEXTBEE_NOT_CONFIGURED' };
  }

  // Format cleanly: preserve E.164 with +, default to +91 if 10-digit national number
  let formattedPhone = (phone || '').toString().trim();
  if (formattedPhone.startsWith('+')) {
    formattedPhone = `+${formattedPhone.replace(/\D/g, '')}`;
  } else {
    const digits = formattedPhone.replace(/\D/g, '');
    formattedPhone = digits.length === 10 ? `+91${digits}` : `+${digits}`;
  }

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);

    const res = await fetch(`https://api.textbee.dev/api/v1/gateway/devices/${deviceId}/sendSMS`, {
      method: 'POST',
      headers: {
        'x-api-key': apiKey,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        recipients: [formattedPhone],
        message: message,
      }),
      signal: controller.signal,
    });
    clearTimeout(timeout);

    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(data.message || `HTTP ${res.status}`);
    }

    const batchId = data?.data?.smsBatchId || data?.data?.id;
    logger.info(`[SMS] Real cellular telecom SMS dispatched to ${formattedPhone} via TextBee. Batch ID: ${batchId}`);
    return { success: true, batchId, data };
  } catch (err) {
    const errMsg = err.message;
    logger.error(`[SMS] TextBee telecom dispatch failed for ${formattedPhone}: ${errMsg}`);
    throw Object.assign(new Error(`Failed to send SMS to ${formattedPhone}: ${errMsg}`), { statusCode: 502 });
  }
}

/**
 * Dispatch 6-digit cryptographic security code via real SMS
 * @param {string} phone
 * @param {string} otp
 */
async function sendOtpSms(phone, otp) {
  const message = `BudGi Security Code: ${otp}. Valid for 5 minutes. Never share this code with anyone.`;
  return sendSms({ phone, message });
}

module.exports = {
  sendSms,
  sendOtpSms,
};
