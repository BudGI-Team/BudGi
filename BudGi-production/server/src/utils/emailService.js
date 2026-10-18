// src/utils/emailService.js
// Centralized email service — logs in dev, sends via SMTP in production.
// Uses nodemailer if available, otherwise gracefully falls back to console logging.

'use strict';

const logger = require('./logger');

let transporter = null;

// Try to create a real transporter if SMTP config exists
async function getTransporter() {
  if (transporter) return transporter;

  const host = process.env.SMTP_HOST;
  const port = process.env.SMTP_PORT;
  const user = process.env.SMTP_USER;
  const pass = process.env.SMTP_PASS;

  if (host && user && pass) {
    try {
      const nodemailer = require('nodemailer');
      transporter = nodemailer.createTransport({
        host,
        port: parseInt(port) || 587,
        secure: parseInt(port) === 465,
        auth: { user, pass },
      });
      await transporter.verify();
      logger.info('SMTP transport ready');
      return transporter;
    } catch (err) {
      logger.warn('SMTP setup failed, falling back to console:', { error: err.message });
      transporter = null;
    }
  }

  return null;
}

const FROM_ADDRESS = process.env.SMTP_FROM || 'BudGi <noreply@budgi.app>';

/**
 * Send an email. In development without SMTP, logs to console.
 * @param {{ to: string, subject: string, html: string, text?: string }} opts
 */
async function sendEmail({ to, subject, html, text }) {
  const transport = await getTransporter();

  if (transport) {
    await transport.sendMail({
      from: FROM_ADDRESS,
      to,
      subject,
      html,
      text: text || html.replace(/<[^>]*>/g, ''),
    });
    logger.info(`Sent email to ${to}`, { subject });
    return { sent: true };
  }

  // Dev fallback — log to debug logger
  logger.debug('Email Dev Fallback Logs', {
    to,
    subject,
    body: text || html.replace(/<[^>]*>/g, ''),
  });
  return { sent: false, logged: true };
}

// ── Pre-built email templates ──────────────────────────────────────────────

function verificationEmail(name, verificationUrl) {
  const html = `
    <div style="font-family: 'Inter', -apple-system, sans-serif; max-width: 520px; margin: 0 auto; padding: 40px 20px;">
      <div style="text-align: center; margin-bottom: 32px;">
        <h1 style="font-size: 28px; font-weight: 800; color: #7C6AF5; margin: 0;">BudGi</h1>
        <p style="color: #64748b; font-size: 14px; margin-top: 4px;">Smart Financial Tracker</p>
      </div>
      <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 32px;">
        <h2 style="font-size: 20px; font-weight: 700; color: #1e293b; margin: 0 0 12px;">Verify your email</h2>
        <p style="color: #475569; font-size: 14px; line-height: 1.7;">
          Hey ${name}! Welcome to BudGi. Click the button below to verify your email and start tracking your finances.
        </p>
        <div style="text-align: center; margin: 28px 0;">
          <a href="${verificationUrl}" style="background: linear-gradient(135deg, #7C6AF5, #6b5ce7); color: white; padding: 14px 36px; border-radius: 10px; font-size: 15px; font-weight: 700; text-decoration: none; display: inline-block;">
            Verify Email
          </a>
        </div>
        <p style="color: #94a3b8; font-size: 12px; text-align: center;">
          This link expires in 24 hours. If you didn't create a BudGi account, ignore this email.
        </p>
      </div>
    </div>
  `;
  return { subject: 'Verify your BudGi email', html };
}

function passwordResetEmail(name, resetUrl) {
  const html = `
    <div style="font-family: 'Inter', -apple-system, sans-serif; max-width: 520px; margin: 0 auto; padding: 40px 20px;">
      <div style="text-align: center; margin-bottom: 32px;">
        <h1 style="font-size: 28px; font-weight: 800; color: #7C6AF5; margin: 0;">BudGi</h1>
        <p style="color: #64748b; font-size: 14px; margin-top: 4px;">Smart Financial Tracker</p>
      </div>
      <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 32px;">
        <h2 style="font-size: 20px; font-weight: 700; color: #1e293b; margin: 0 0 12px;">Reset your password</h2>
        <p style="color: #475569; font-size: 14px; line-height: 1.7;">
          Hey ${name}, we received a request to reset your password. Click below to choose a new one.
        </p>
        <div style="text-align: center; margin: 28px 0;">
          <a href="${resetUrl}" style="background: linear-gradient(135deg, #7C6AF5, #6b5ce7); color: white; padding: 14px 36px; border-radius: 10px; font-size: 15px; font-weight: 700; text-decoration: none; display: inline-block;">
            Reset Password
          </a>
        </div>
        <p style="color: #94a3b8; font-size: 12px; text-align: center;">
          This link expires in 1 hour. If you didn't request this, ignore this email — your password won't change.
        </p>
      </div>
    </div>
  `;
  return { subject: 'Reset your BudGi password', html };
}

module.exports = {
  sendEmail,
  verificationEmail,
  passwordResetEmail,
};
