// src/jobs/cronJobs.js
/**
 * Cron Jobs — Scheduled background tasks
 *
 * Schedule:
 *  • Every day   06:00 IST  → run alert rules for all users
 *  • Every day   05:30 IST  → auto-create recurring transactions
 *  • Every Mon   08:00 IST  → generate weekly summaries
 *  • Every 1st   07:00 IST  → recompute health scores for all users
 *  • Every 6h              → detect recurring transactions
 */

'use strict';

const cron = require('node-cron');
const User = require('../models/User');
const Transaction = require('../models/Transaction');
const alertService = require('../modules/alerts/services/alertService');
const { computeHealthScore } = require('../modules/ai/services/healthScoreService');
const { detectRecurring } = require('../modules/analytics/services/predictionService');
const logger = require('../utils/logger');

let jobsStarted = false;

function startCronJobs() {
  if (jobsStarted) return;
  jobsStarted = true;

  logger.info('Starting scheduled jobs...');

  // ── Daily recurring transactions — 05:30 IST (00:00 UTC) ────────────────
  cron.schedule(
    '0 0 * * *',
    async () => {
      logger.info('Processing recurring transactions...');
      try {
        const today = new Date();
        today.setUTCHours(0, 0, 0, 0);

        // Find recurring templates where the next occurrence is today or past
        const templates = await Transaction.find({
          isRecurring: true,
          recurringFrequency: { $ne: null },
          $or: [{ recurringEndDate: null }, { recurringEndDate: { $gte: today } }],
        }).lean();

        let created = 0;
        for (const tpl of templates) {
          // Compute next date from last transaction with this recurringId
          const lastTx = await Transaction.findOne({
            recurringId: tpl.recurringId || tpl._id.toString(),
            userId: tpl.userId,
          })
            .sort({ date: -1 })
            .lean();

          const lastDate = lastTx ? new Date(lastTx.date) : new Date(tpl.date);
          const nextDate = computeNextDate(lastDate, tpl.recurringFrequency);

          if (nextDate <= today) {
            await Transaction.create({
              userId: tpl.userId,
              amount: tpl.amount,
              category: tpl.category,
              type: tpl.type,
              description: tpl.description,
              merchant: tpl.merchant,
              date: nextDate,
              source: 'manual',
              isRecurring: false,
              recurringId: tpl.recurringId || tpl._id.toString(),
            });
            created++;
          }
        }

        logger.info(`Recurring: ${created} transactions auto-created from ${templates.length} templates`);
      } catch (err) {
        logger.error('Recurring transaction error:', err);
      }
    },
    { timezone: 'UTC' },
  );

  // ── Daily alert rules — 06:00 IST (00:30 UTC) ──────────────────────────
  cron.schedule(
    '30 0 * * *',
    async () => {
      logger.info('Running daily alert rules...');
      const users = await User.find({}, '_id').lean();
      const results = await Promise.allSettled(users.map((u) => alertService.runAlertRules(u._id.toString())));
      const total = results.reduce((s, r) => s + (r.value?.length || 0), 0);
      logger.info(`Daily alerts: ${total} alerts triggered for ${users.length} users`);
    },
    { timezone: 'UTC' },
  );

  // ── Weekly summary — Monday 08:00 IST (02:30 UTC) ──────────────────────
  cron.schedule(
    '30 2 * * 1',
    async () => {
      logger.info('Generating weekly summaries...');
      const users = await User.find({}, '_id').lean();
      const results = await Promise.allSettled(users.map((u) => alertService.generateWeeklySummary(u._id.toString())));
      const sent = results.filter((r) => r.status === 'fulfilled' && r.value).length;
      logger.info(`Weekly summaries: ${sent}/${users.length} generated`);
    },
    { timezone: 'UTC' },
  );

  // ── Monthly health scores — 1st of month 07:00 IST (01:30 UTC) ─────────
  cron.schedule(
    '30 1 1 * *',
    async () => {
      logger.info('Recomputing health scores...');
      const users = await User.find({}, '_id').lean();
      const results = await Promise.allSettled(users.map((u) => computeHealthScore(u._id.toString())));
      const ok = results.filter((r) => r.status === 'fulfilled').length;
      logger.info(`Health scores: ${ok}/${users.length} updated`);
    },
    { timezone: 'UTC' },
  );

  // ── Recurring detection — every 6 hours ─────────────────────────────────
  cron.schedule(
    '0 */6 * * *',
    async () => {
      logger.info('Detecting recurring transactions...');
      const users = await User.find({}, '_id').lean();
      await Promise.allSettled(users.map((u) => detectRecurring(u._id.toString())));
      logger.info(`Recurring detection done for ${users.length} users`);
    },
    { timezone: 'UTC' },
  );

  // ── Nightly AI Copilot Briefing — 07:00 IST (01:30 UTC) ──────────────────
  cron.schedule(
    '30 1 * * *',
    async () => {
      logger.info('Generating Nightly AI Copilot Briefings...');
      const users = await User.find({}, '_id').lean();
      const results = await Promise.allSettled(
        users.map((u) => alertService.generateNightlyAIBriefing(u._id.toString())),
      );
      const ok = results.filter((r) => r.status === 'fulfilled' && r.value).length;
      logger.info(`AI Briefings: ${ok}/${users.length} generated`);
    },
    { timezone: 'UTC' },
  );

  logger.info('All jobs scheduled.');
}

function computeNextDate(fromDate, frequency) {
  const d = new Date(fromDate);
  switch (frequency) {
    case 'daily':
      d.setDate(d.getDate() + 1);
      break;
    case 'weekly':
      d.setDate(d.getDate() + 7);
      break;
    case 'monthly':
      d.setMonth(d.getMonth() + 1);
      break;
    case 'yearly':
      d.setFullYear(d.getFullYear() + 1);
      break;
  }
  return d;
}

module.exports = { startCronJobs };
