// modules/analytics/services/analyticsService.js
// ALL analytics business logic — recurring, anomalies, currency, market, reports

'use strict';

const analyticsRepo = require('../repositories/analyticsRepository');
const { detectRecurring, detectAnomalies } = require('./predictionService');
const { getCategoryStats } = require('../../transactions/services/categorizationService');
const env = require('../../../config/env');

// ── Recurring ─────────────────────────────────────────────────────────────────
async function getRecurring(userId) {
  return detectRecurring(userId);
}

// ── Anomalies ─────────────────────────────────────────────────────────────────
async function getAnomalies(userId, daysParam) {
  const days = Math.min(parseInt(daysParam) || 90, 365);
  return detectAnomalies(userId, days);
}

// ── Currency rates (in-memory cache, 1h TTL) ──────────────────────────────────
const _rateCache = { data: null, fetchedAt: null };
const RATE_CACHE_TTL = 60 * 60 * 1000;

async function getCurrencyRates(base = 'INR') {
  const baseCurrency = base.toUpperCase();

  if (_rateCache.data?.[baseCurrency] && Date.now() - _rateCache.fetchedAt < RATE_CACHE_TTL) {
    return { rates: _rateCache.data[baseCurrency], cached: true, base: baseCurrency };
  }

  if (!env.EXCHANGE_RATE_API_KEY) {
    throw Object.assign(new Error('Exchange rate API key not configured'), { statusCode: 503 });
  }

  const url = `https://v6.exchangerate-api.com/v6/${env.EXCHANGE_RATE_API_KEY}/latest/${baseCurrency}`;
  const response = await fetch(url);
  const data = await response.json();

  if (data.result !== 'success') {
    throw Object.assign(new Error(`Exchange rate API error: ${data['error-type']}`), { statusCode: 502 });
  }

  if (!_rateCache.data) _rateCache.data = {};
  _rateCache.data[baseCurrency] = data.conversion_rates;
  _rateCache.fetchedAt = Date.now();

  return {
    rates: data.conversion_rates,
    base: baseCurrency,
    cached: false,
    lastUpdate: data.time_last_update_utc,
  };
}

// ── Market data (in-memory cache, 15m TTL) ────────────────────────────────────
const _marketCache = { data: null, fetchedAt: null };
const MARKET_CACHE_TTL = 15 * 60 * 1000;

async function getMarketData(symbol = 'SPY') {
  if (_marketCache.data && Date.now() - _marketCache.fetchedAt < MARKET_CACHE_TTL) {
    return { indices: _marketCache.data, cached: true };
  }

  if (!env.ALPHA_VANTAGE_API_KEY) {
    throw Object.assign(new Error('Alpha Vantage API key not configured'), { statusCode: 503 });
  }

  const url = `https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=${symbol}&apikey=${env.ALPHA_VANTAGE_API_KEY}`;
  const response = await fetch(url);
  const data = await response.json();

  if (data['Note']) {
    throw Object.assign(new Error('Alpha Vantage rate limit reached. Try again in a minute.'), { statusCode: 429 });
  }

  const quote = data['Global Quote'];
  if (!quote || !quote['05. price']) {
    throw Object.assign(new Error('No market data returned for symbol'), { statusCode: 502 });
  }

  const result = {
    symbol,
    price: parseFloat(quote['05. price']),
    change: parseFloat(quote['09. change']),
    changePct: quote['10. change percent'],
    volume: parseInt(quote['06. volume']),
    high: parseFloat(quote['03. high']),
    low: parseFloat(quote['04. low']),
    latestDay: quote['07. latest trading day'],
  };

  _marketCache.data = [result];
  _marketCache.fetchedAt = Date.now();

  return { indices: [result], cached: false };
}

// ── Monthly Report ────────────────────────────────────────────────────────────
async function getMonthlyReport(userId, yearParam, monthParam) {
  const now = new Date();
  const year = parseInt(yearParam) || now.getUTCFullYear();
  const month = parseInt(monthParam) || now.getUTCMonth() + 1;

  const start = new Date(Date.UTC(year, month - 1, 1));
  const end = new Date(Date.UTC(year, month, 1));
  const transactions = await analyticsRepo.findByMonthRange(userId, start, end);

  const income = transactions.filter((t) => t.type === 'income');
  const expense = transactions.filter((t) => t.type === 'expense');

  const totalIncome = income.reduce((s, t) => s + t.amount, 0);
  const totalExpense = expense.reduce((s, t) => s + t.amount, 0);

  return {
    period: { year, month },
    totalIncome: Math.round(totalIncome),
    totalExpense: Math.round(totalExpense),
    netSavings: Math.round(totalIncome - totalExpense),
    savingsRate: totalIncome > 0 ? Math.round((1 - totalExpense / totalIncome) * 100) : null,
    incomeCount: income.length,
    expenseCount: expense.length,
    categoryBreakdown: getCategoryStats(expense),
    topExpenses: expense.sort((a, b) => b.amount - a.amount).slice(0, 5),
  };
}

module.exports = {
  getRecurring,
  getAnomalies,
  getCurrencyRates,
  getMarketData,
  getMonthlyReport,
};
