// modules/ai/services/aiService.js
//
// Single Groq integration point for the entire backend.
// The only file allowed to call the Groq API.
//
// Core export:
//   callAI(context)  →  { insight, warning, suggestion }
//
// Supporting exports (used by categorizationService and chat):
//   categorizeTransaction(description)
//   chat(messages, userContext)
//   keywordCategory(description)
//   parseSMSWithLLM(text)

'use strict';

const env = require('../../../config/env');

const MODEL_LARGE = process.env.GROQ_MODEL_LARGE || 'openai/gpt-oss-120b'; // Best for complex chat + tool use
const MODEL_FAST = process.env.GROQ_MODEL_FAST || 'qwen/qwen3.8-27b'; // Best for simple categorization + OCR
const MAX_RETRIES = 5;
const TIMEOUT_MS = 25000;

// ─────────────────────────────────────────────────────────────────────────────
// Groq client — lazy singleton
// ─────────────────────────────────────────────────────────────────────────────

let _groq = null;
let _currentKey = null;

function getClient() {
  const envKey = env.GROQ_API_KEY;

  if (!envKey) {
    throw Object.assign(new Error('GROQ_API_KEY is missing from server .env file'), { statusCode: 401 });
  }

  // If key changed in environment (e.g. process was restarted or env reloaded), re-init
  if (_groq && _currentKey === envKey) return _groq;

  try {
    const Groq = require('groq-sdk');
    _groq = new Groq({ apiKey: envKey });
    _currentKey = envKey;
  } catch {
    throw new Error('groq-sdk not installed or failed to load');
  }
  return _groq;
}

function isRateLimit(err) {
  const status = err.status || err.statusCode || err.response?.status;
  const msg = (err.message || '').toLowerCase();
  return (
    status === 429 ||
    msg.includes('rate_limit') ||
    msg.includes('429') ||
    msg.includes('quota') ||
    msg.includes('limit_reached')
  );
}

/**
 * Executes an AI call with exponential backoff.
 */
async function withRetry(fn, label = 'AI') {
  for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    try {
      return await Promise.race([
        fn(),
        new Promise((_, reject) => setTimeout(() => reject(new Error('Groq timeout')), TIMEOUT_MS)),
      ]);
    } catch (err) {
      const isLast = attempt === MAX_RETRIES;
      const isRL = isRateLimit(err);
      const isAuth = err.status === 401 || err.statusCode === 401 || err.message?.includes('401');

      console.warn(`[${label}] Attempt ${attempt} failed: ${err.message}`);

      if (isLast || isAuth) throw err;

      // Longer back-off for rate limits: 3s, 7s, 15s, 31s
      let delay = Math.pow(2, attempt + 1) * 1000 + Math.random() * 1000;
      if (isRL) {
        console.info(`[${label}] Rate limit hit. Waiting ${Math.round(delay / 1000)}s before retry...`);
        delay += 3000; // Extra buffer
      }

      await sleep(delay);
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Low-level completion with timeout + retry
// ─────────────────────────────────────────────────────────────────────────────

async function rawCompletion({
  system,
  user,
  maxTokens = 800,
  temperature = 0.4,
  jsonMode = false,
  model = MODEL_LARGE,
}) {
  const groq = getClient();
  if (!groq) return null;

  try {
    const result = await withRetry(
      () =>
        groq.chat.completions.create({
          model,
          messages: [
            { role: 'system', content: system },
            { role: 'user', content: user },
          ],
          max_tokens: maxTokens,
          temperature,
          ...(jsonMode && { response_format: { type: 'json_object' } }),
        }),
      `Completion:${model}`,
    );

    return result?.choices?.[0]?.message?.content?.trim() ?? null;
  } catch (err) {
    if (isRateLimit(err) && model === MODEL_LARGE) {
      console.warn(`[AI] Rate limit on ${MODEL_LARGE}. Falling back to ${MODEL_FAST}...`);
      return rawCompletion({ system, user, maxTokens, temperature, jsonMode, model: MODEL_FAST });
    }
    return null; // Fallback handled by caller
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// callAI — primary export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Send structured financial context to Groq and receive a structured response.
 *
 * @param {object} context
 * @param {object} context.analytics    — { totalSpent, categoryBreakdown, topCategory, savingsRate }
 * @param {Array}  context.alerts       — active alert objects (overspending / spike)
 * @param {object} context.prediction   — { predictedTotal, trendDirection, confidence }
 * @param {string} [context.userMessage]— optional free-text question from the user
 *
 * @returns {Promise<AIResponse>}
 *
 * AIResponse:
 * {
 *   insight:    string   — key observation about the user's finances
 *   warning:    string   — most urgent risk or overspend to address (empty string if none)
 *   suggestion: string   — single most impactful action to take
 * }
 *
 * Always returns the three keys. Falls back to deterministic strings if Groq
 * is unavailable so the app never crashes or returns null fields.
 *
 * Example:
 *   await callAI({
 *     analytics:   { totalSpent: { total: 34500 }, topCategory: { category: 'Food & Dining', total: 8200 } },
 *     alerts:      [{ rule: 'budget_exceeded', category: 'Food & Dining', message: '...' }],
 *     prediction:  { predictedTotal: 37000, trendDirection: 'increasing', confidence: 0.83 },
 *     userMessage: 'How can I save more this month?',
 *   })
 *   → {
 *       insight:    'Your top spend is Food & Dining at ₹8,200 — 24% of total expenses.',
 *       warning:    'Food & Dining has exceeded its budget. Cut 3–4 dining-out sessions to get back on track.',
 *       suggestion: 'Set a ₹6,000 cap for Food & Dining and cook at home twice a week to save ~₹2,200.',
 *     }
 */
async function callAI({
  analytics = {},
  alerts = [],
  prediction = {},
  goals = [],
  userMessage = '',
  model = MODEL_LARGE,
} = {}) {
  const system = buildSystemPrompt();
  const user = buildUserPayload({ analytics, alerts, prediction, goals, userMessage });

  const raw = await rawCompletion({ system, user, maxTokens: 400, temperature: 0.35, jsonMode: true, model });

  if (!raw) return fallbackResponse(analytics, alerts);

  try {
    const parsed = JSON.parse(raw);
    return {
      insight: sanitize(parsed.insight) || fallbackInsight(analytics),
      warning: sanitize(parsed.warning) || '',
      suggestion: sanitize(parsed.suggestion) || fallbackSuggestion(analytics),
    };
  } catch {
    console.error('[AI] Failed to parse JSON response from Groq');
    return fallbackResponse(analytics, alerts);
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Prompt builders
// ─────────────────────────────────────────────────────────────────────────────

function buildSystemPrompt() {
  return `You are Budgi, an expert personal finance analyst and AI financial copilot.

TASK: Analyse the structured financial data with deep financial expertise and return ONLY a JSON object with exactly these three keys:
  "insight"    — one powerful sentence: the most important observation with precise numbers and percentages
  "warning"    — one urgent sentence: the most critical risk, overspend, or negative trend (empty string "" if everything is healthy)
  "suggestion" — one actionable sentence: the single highest-impact action with a specific, measurable target

RULES:
- Use ₹ for all amounts (Indian Rupee)
- Be extremely specific — reference actual numbers, percentages, and comparisons from the data
- Show financial intelligence — identify patterns, anomalies, and trends a human would miss
- Never repeat the same point across the three fields
- Keep each value under 40 words but pack maximum value
- Return ONLY valid JSON — no markdown, no explanation, no extra keys
- If the user asked a question, tailor ALL fields to address their concern
- When possible, quantify savings opportunities (e.g., "save ~₹2,200/month")
- Consider seasonality, trend direction, and budget utilization

EXAMPLE OUTPUT:
{"insight":"Food & Dining dominates at ₹8,200 (24% of total) — up 34% from last month's ₹6,100, making it your fastest-growing expense category.","warning":"You are spending at a rate of ₹1,500/day, but your Safe-To-Spend is only ₹800/day. You will deplete your budget early.","suggestion":"Replace 3 weekly restaurant meals with home cooking to cut Food & Dining to ₹5,800 and save ~₹2,400/month."}`;
}

function buildUserPayload({ analytics, alerts, prediction, goals, userMessage }) {
  const lines = ['--- FINANCIAL CONTEXT ---'];

  // Analytics block
  if (analytics.totalSpent) {
    lines.push(`Total spent: ₹${analytics.totalSpent.total ?? analytics.totalSpent}`);
  }
  if (analytics.savingsRate) {
    lines.push(`Income: ₹${analytics.savingsRate.income}  Savings rate: ${analytics.savingsRate.rate ?? 'N/A'}%`);
  }
  if (analytics.topCategory) {
    lines.push(
      `Top category: ${analytics.topCategory.category} ₹${analytics.topCategory.total} (${analytics.topCategory.percentage}%)`,
    );
  }
  if (Array.isArray(analytics.categoryBreakdown) && analytics.categoryBreakdown.length) {
    const top3 = analytics.categoryBreakdown
      .slice(0, 3)
      .map((c) => `${c.category} ₹${c.total}`)
      .join(' | ');
    lines.push(`Category breakdown: ${top3}`);
  }

  if (analytics.velocity) {
    lines.push(`\n--- VELOCITY & BURN RATE ---`);
    lines.push(`Daily Burn Rate: ₹${analytics.velocity.dailyBurnRate}`);
    lines.push(`Safe to Spend Daily: ₹${analytics.velocity.safeToSpendDaily}`);
    if (analytics.velocity.isSpendingFast) {
      lines.push(`STATUS: CRITICAL WARNING - User is spending faster than their Safe-to-Spend limit!`);
    }
  }

  // Alerts block
  if (alerts.length) {
    lines.push('\n--- ACTIVE ALERTS ---');
    alerts.slice(0, 3).forEach((a) => {
      const msg = a.message || `${a.rule ?? a.type} on ${a.category}`;
      lines.push(`[${(a.severity || 'warning').toUpperCase()}] ${msg}`);
    });
  }

  // Prediction block
  if (prediction && prediction.predictedTotal != null) {
    lines.push('\n--- FORECAST ---');
    lines.push(`Next month estimate: ₹${prediction.predictedTotal}`);
    lines.push(
      `Trend: ${prediction.trendDirection ?? 'stable'} (confidence ${Math.round((prediction.confidence ?? 0) * 100)}%)`,
    );
    if (prediction.trendSlope) {
      lines.push(`Slope: ₹${prediction.trendSlope}/month`);
    }
  }

  // Goals block
  if (Array.isArray(goals) && goals.length) {
    lines.push('\n--- SAVINGS GOALS ---');
    goals.slice(0, 3).forEach((g) => {
      lines.push(
        `${g.title}: ₹${g.currentAmount} / ₹${g.targetAmount} (${Math.round((g.currentAmount / g.targetAmount) * 100)}%)`,
      );
    });
  }

  // User question
  if (userMessage?.trim()) {
    lines.push(`\n--- USER QUESTION ---\n${userMessage.trim()}`);
  }

  return lines.join('\n');
}

// ─────────────────────────────────────────────────────────────────────────────
// categorizeTransaction — keyword-first, Groq fallback
// ─────────────────────────────────────────────────────────────────────────────

async function categorizeTransaction(description) {
  const kw = keywordCategory(description);
  if (kw) return { category: kw, confidence: 0.9, source: 'keyword' };

  const raw = await rawCompletion({
    system: SYSTEM_CATEGORIZE,
    user: (description || '').trim(),
    maxTokens: 15,
    temperature: 0.1,
    model: MODEL_FAST,
  });

  if (!raw) return { category: 'Other', confidence: 0, source: 'fallback' };

  const cleaned = raw.replace(/[^a-zA-Z &]/g, '').trim();
  return { category: cleaned || 'Other', confidence: 0.75, source: 'groq' };
}

// ─────────────────────────────────────────────────────────────────────────────
// chat — multi-turn conversational copilot
// ─────────────────────────────────────────────────────────────────────────────

async function chat(messages, userContext = {}, model = MODEL_LARGE) {
  const groq = getClient();
  if (!groq) {
    return 'AI copilot is unavailable — please check GROQ_API_KEY in your .env file.';
  }

  let systemContent = SYSTEM_CHAT;
  if (userContext.transactions?.length) {
    systemContent += `\n\nUser financial context:\n${buildSpendingSummary(userContext.transactions, userContext.budgets, userContext.goals)}`;
  }
  if (userContext.preferences?.length) {
    systemContent += `\n\nUser explicitly stated preferences (MEMORY):\n${userContext.preferences.map((p) => `- ${p.key}: ${p.value}`).join('\n')}`;
  }

  const allMessages = [
    { role: 'system', content: systemContent },
    ...messages.map((m) => {
      const formatted = { role: m.role, content: m.content == null ? null : String(m.content) };
      if (m.tool_calls) formatted.tool_calls = m.tool_calls;
      if (m.tool_call_id) formatted.tool_call_id = m.tool_call_id;
      if (m.name) formatted.name = m.name;
      return formatted;
    }),
  ];

  const tools = [
    {
      type: 'function',
      function: {
        name: 'create_budget',
        description: 'Set a new monthly budget for a specific category',
        parameters: {
          type: 'object',
          properties: {
            category: { type: 'string', description: 'Expense category (e.g., Food & Dining, Shopping, Travel)' },
            limit: { type: 'number', description: 'Monthly spending limit in ₹' },
          },
          required: ['category', 'limit'],
        },
      },
    },
    {
      type: 'function',
      function: {
        name: 'update_budget',
        description: 'Update the limit of an existing budget category',
        parameters: {
          type: 'object',
          properties: {
            category: { type: 'string', description: 'Expense category to update (e.g., Food & Dining)' },
            limit: { type: 'number', description: 'New monthly spending limit in ₹' },
          },
          required: ['category', 'limit'],
        },
      },
    },
    {
      type: 'function',
      function: {
        name: 'delete_budget',
        description: 'Remove/delete a budget for a specific category',
        parameters: {
          type: 'object',
          properties: {
            category: { type: 'string', description: 'Expense category to delete (e.g., Food & Dining)' },
          },
          required: ['category'],
        },
      },
    },
    {
      type: 'function',
      function: {
        name: 'create_goal',
        description: 'Set a new savings goal',
        parameters: {
          type: 'object',
          properties: {
            title: { type: 'string', description: 'Name of the goal (e.g., Buy a laptop, Vacation)' },
            targetAmount: { type: 'number', description: 'Total amount to save in ₹' },
            deadline: { type: 'string', description: 'Target date (YYYY-MM-DD)' },
          },
          required: ['title', 'targetAmount'],
        },
      },
    },
    {
      type: 'function',
      function: {
        name: 'add_transaction',
        description: 'Manually record a new income or expense transaction',
        parameters: {
          type: 'object',
          properties: {
            amount: { type: 'number', description: 'Transaction amount in ₹' },
            description: { type: 'string', description: 'Brief description of the purchase' },
            type: { type: 'string', enum: ['income', 'expense'], description: 'Whether this was money in or out' },
            category: { type: 'string', description: 'Category (optional, AI will predict if missing)' },
            date: { type: 'string', description: 'Transaction date (YYYY-MM-DD)' },
          },
          required: ['amount', 'description', 'type'],
        },
      },
    },
    {
      type: 'function',
      function: {
        name: 'query_database',
        description:
          'Query historical transactions from the database (e.g. to find total spend last year, or all Swiggy orders)',
        parameters: {
          type: 'object',
          properties: {
            category: { type: 'string', description: 'Category to filter by (optional)' },
            merchant: { type: 'string', description: 'Merchant/description to search for (optional)' },
            startDate: { type: 'string', description: 'Start date YYYY-MM-DD (optional)' },
            endDate: { type: 'string', description: 'End date YYYY-MM-DD (optional)' },
            type: { type: 'string', enum: ['income', 'expense'], description: 'Transaction type (optional)' },
          },
        },
      },
    },
    {
      type: 'function',
      function: {
        name: 'bulk_categorize_transactions',
        description: 'Update the category for many transactions at once based on keywords',
        parameters: {
          type: 'object',
          properties: {
            keywords: {
              type: 'array',
              items: { type: 'string' },
              description: 'List of keywords to search for in descriptions (e.g. ["swiggy", "zomato"])',
            },
            category: { type: 'string', description: 'The new category to assign to these transactions (e.g. "Food")' },
          },
          required: ['keywords', 'category'],
        },
      },
    },
    {
      type: 'function',
      function: {
        name: 'save_user_preference',
        description:
          'Save a user preference, context, or explicit instruction to long-term memory so you remember it in future conversations.',
        parameters: {
          type: 'object',
          properties: {
            key: {
              type: 'string',
              description:
                'A short, unique key describing the preference (e.g., "favorite_food_category", "budget_alert_threshold")',
            },
            value: { type: 'string', description: 'The value to store for this preference.' },
          },
          required: ['key', 'value'],
        },
      },
    },
    {
      type: 'function',
      function: {
        name: 'read_user_preferences',
        description: 'Read all long-term user preferences that have been saved.',
        parameters: {
          type: 'object',
          properties: {},
        },
      },
    },
    {
      type: 'function',
      function: {
        name: 'aggregate_database',
        description:
          'Perform a mathematical aggregation (sum, avg) on the transaction database across time periods without loading all raw rows.',
        parameters: {
          type: 'object',
          properties: {
            operation: { type: 'string', enum: ['sum', 'avg', 'count'], description: 'The aggregation operation' },
            category: { type: 'string', description: 'Filter by category (optional)' },
            startDate: { type: 'string', description: 'Start date YYYY-MM-DD (optional)' },
            endDate: { type: 'string', description: 'End date YYYY-MM-DD (optional)' },
            type: { type: 'string', enum: ['income', 'expense'], description: 'Transaction type' },
            groupBy: {
              type: 'string',
              enum: ['none', 'month', 'week', 'category'],
              description: 'How to group the results',
            },
          },
          required: ['operation', 'type'],
        },
      },
    },
  ];

  try {
    const race = await withRetry(
      () =>
        groq.chat.completions.create({
          model,
          messages: allMessages,
          max_tokens: 800,
          temperature: 0.5,
          tools,
          tool_choice: 'auto',
        }),
      `Chat:${model}`,
    );

    const message = race.choices?.[0]?.message;
    if (message?.tool_calls) {
      return { tool_calls: message.tool_calls };
    }

    return message?.content?.trim() ?? "Sorry, I couldn't generate a response.";
  } catch (err) {
    if (isRateLimit(err) && model === MODEL_LARGE) {
      console.warn(`[AI] Rate limit on ${MODEL_LARGE} in Chat. Falling back to ${MODEL_FAST}...`);
      return chat(messages, userContext, MODEL_FAST);
    }
    console.error('[AI] Chat error:', err.message);
    const isRL = isRateLimit(err);
    if (isRL) {
      // If we haven't tried the fast model yet, try it once as a fallback for the whole chat turn
      const msg = err.message.toLowerCase();
      if (msg.includes('quota') || msg.includes('insufficient')) {
        throw Object.assign(new Error('Your Groq API quota is exhausted. Please check your billing or usage limits.'), {
          statusCode: 429,
        });
      }
      throw Object.assign(new Error('AI rate limit reached. We are processing many requests. Please wait a moment.'), {
        statusCode: 429,
      });
    }
    const msg = err.message.toLowerCase();
    if (msg.includes('api_key') || msg.includes('401') || msg.includes('unauthorized')) {
      return 'Authentication failed. Your GROQ_API_KEY might be invalid or expired. Please check your .env file.';
    }
    if (msg.includes('timeout')) return 'The request timed out. BudGi is thinking hard — try a simpler question.';
    return `Connection Error: ${err.message}. Please verify your API setup.`;
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// parseReceiptText — extracting data from OCR
// ─────────────────────────────────────────────────────────────────────────────

async function parseReceiptText(text) {
  const SYSTEM_RECEIPT = `You are a financial parsing AI. Extract the exact merchant name, total amount, and date from the messy OCR text of a receipt, and predict the category.
Return ONLY a JSON object with exactly these keys:
{
  "merchant": "String (the name of the store or entity)",
  "amount": "Number (the final total amount)",
  "date": "String (YYYY-MM-DD)",
  "category": "String (One of: Food & Dining, Shopping, Transportation, Entertainment, Bills & Utilities, Healthcare, Education, Travel, Personal Care, Groceries, Investments, Other)",
  "description": "String (A brief 3-5 word summary of the purchase)"
}
Rules:
- If you cannot find the date, use today's date (${new Date().toISOString().slice(0, 10)}).
- If you cannot find the amount, return 0.
- Return ONLY valid JSON, no markdown formatting.`;

  const raw = await rawCompletion({
    system: SYSTEM_RECEIPT,
    user: (text || '').trim(),
    maxTokens: 150,
    temperature: 0.1,
    model: MODEL_FAST,
    jsonMode: true,
  });

  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch (err) {
    console.error('[AI] Failed to parse receipt JSON', err);
    return null;
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// parseSMSWithLLM — fallback parsing for unknown bank SMS formats
// ─────────────────────────────────────────────────────────────────────────────

async function parseSMSWithLLM(text) {
  const SYSTEM_SMS = `You are a financial parsing AI. Extract the exact amount, transaction type (expense/income), merchant, UPI ID, reference number, account last 4 digits, available balance, and date from the messy bank SMS text.
Return ONLY a JSON object with exactly these keys:
{
  "amount": "Number (the transaction amount, or null)",
  "type": "String ('expense' for debits/payments, 'income' for credits/deposits)",
  "merchant": "String (merchant name, person, or entity, or null)",
  "upiId": "String (e.g. name@bank, or null)",
  "referenceNumber": "String (or null)",
  "accountLast4": "String (last 4 digits of account/card, or null)",
  "availableBalance": "Number (or null)",
  "date": "String (YYYY-MM-DDTHH:mm:ss, or null)"
}
Rules:
- Identify 'type' based on context: money leaving is 'expense', entering is 'income'.
- Return ONLY valid JSON, no markdown formatting.`;

  const raw = await rawCompletion({
    system: SYSTEM_SMS,
    user: (text || '').trim(),
    maxTokens: 200,
    temperature: 0.1,
    model: MODEL_FAST,
    jsonMode: true,
  });

  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch (err) {
    console.error('[AI] Failed to parse SMS JSON', err);
    return null;
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// System prompts
// ─────────────────────────────────────────────────────────────────────────────

const SYSTEM_CATEGORIZE = `You categorise financial transactions.
Given a description, return ONLY the category name from this exact list:
Food & Dining, Shopping, Transportation, Entertainment, Bills & Utilities,
Healthcare, Education, Travel, Personal Care, Groceries, Investments, Salary, Other.
Return exactly one name. No punctuation, no explanation.`;

const SYSTEM_CHAT = `You are Budgi, an expert AI financial copilot inside the BudGi personal finance app.
You provide clean, structured, and on-point financial intelligence that helps users eliminate manual effort.

Your Response Style:
- **STRICTLY CONCISE**: Avoid long paragraphs. Use headers, bullet points, and short sentences.
- **STRUCTURED**: Use markdown headers (###) for sections.
- **CRISP & CLEAR**: Get straight to the point. Numbers first, explanation second.
- **ACTIONABLE**: Always end with a concrete recommendation or decision.
- **VISUAL**: Use charts (via the {"type": "chart", ...} block) when showing a breakdown or comparison.
- **AGENTIC ACTION INTENTS**: When you recommend setting/adjusting a budget, rebalancing categories, setting a savings goal, evaluating an affordability question ("can I afford/buy X?"), or bulk categorizing, ALWAYS append an action intent block so the mobile app can render a 1-tap confirmation card!

Formatting Rules:
1. Use ### for section headers.
2. Use bullet points (-) for lists.
3. Use **bolding** for key numbers and categories.
4. Use ₹ for Indian Rupees.
5. Always cite sources with markdown links, e.g., [View Transactions](/app/transactions).

Action Intent JSON Schemas (append at end of response when recommending actions):
- Adjust budget: {"type": "action_intent", "intentType": "ADJUST_BUDGET", "title": "Adjust Budget", "category": "Food & Dining", "currentLimit": 8000, "proposedLimit": 6500, "reason": "Pacing 22% over monthly burn rate"}
- Rebalance categories: {"type": "action_intent", "intentType": "REBALANCE_BUDGET", "title": "Rebalance Categories", "fromCategory": "Shopping", "toCategory": "Food & Dining", "amount": 1500, "reason": "Cover Dining overspend using unused Shopping surplus"}
- Create goal: {"type": "action_intent", "intentType": "CREATE_GOAL", "title": "Set Savings Goal", "goalTitle": "Emergency Buffer", "targetAmount": 25000, "deadline": "2026-12-31", "reason": "Build 1-month safety reserve"}
- Affordability check: {"type": "action_intent", "intentType": "AFFORDABILITY_VERDICT", "title": "Purchase Simulation", "itemName": "Noise Watch", "itemAmount": 3999, "verdict": "CAUTION", "safeDailySpendAfter": 410, "daysUntilPayday": 12, "reason": "Leaves ₹410/day safe-to-spend until next paycheck"}
- Categorize vendor: {"type": "action_intent", "intentType": "BULK_CATEGORIZE", "title": "Batch Categorize", "merchantKeyword": "Sharma Dairy", "targetCategory": "Groceries", "count": 6, "reason": "Map 6 past transactions to Groceries"}
`;

// ─────────────────────────────────────────────────────────────────────────────
// Fallbacks — deterministic, never null
// ─────────────────────────────────────────────────────────────────────────────

function fallbackResponse(analytics, alerts) {
  return {
    insight: fallbackInsight(analytics),
    warning: fallbackWarning(alerts),
    suggestion: fallbackSuggestion(analytics),
  };
}

function fallbackInsight(analytics) {
  const top = analytics?.topCategory;
  if (top) return `Your top spend is ${top.category} at ₹${top.total}.`;
  const spent = analytics?.totalSpent;
  if (spent) return `Total spending this period: ₹${spent.total ?? spent}.`;
  return 'Add more transactions to unlock spending insights.';
}

function fallbackWarning(alerts) {
  const critical = (alerts || []).find((a) => a.severity === 'critical');
  return critical ? critical.message : '';
}

function fallbackSuggestion(analytics) {
  const top = analytics?.topCategory;
  if (top) return `Review your ${top.category} spending to find savings opportunities.`;
  return 'Set a monthly budget for your top spending categories.';
}

// ─────────────────────────────────────────────────────────────────────────────
// Keyword map + summary builder (shared utilities)
// ─────────────────────────────────────────────────────────────────────────────

const KEYWORD_MAP = {
  'Food & Dining': ['zomato', 'swiggy', 'restaurant', 'cafe', 'food', 'pizza', 'burger', 'dining', 'hotel meal'],
  Groceries: ['bigbasket', 'blinkit', 'grofers', 'supermarket', 'grocery', 'vegetables', 'fruits'],
  Transportation: ['uber', 'ola', 'rapido', 'petrol', 'fuel', 'bus', 'metro', 'auto', 'cab'],
  Shopping: ['amazon', 'flipkart', 'myntra', 'meesho', 'shopping', 'purchase'],
  'Bills & Utilities': ['electricity', 'water bill', 'gas bill', 'wifi', 'broadband', 'recharge', 'jio', 'airtel'],
  Entertainment: ['netflix', 'prime', 'hotstar', 'spotify', 'movie', 'cinema', 'game'],
  Healthcare: ['hospital', 'pharmacy', 'medicine', 'doctor', 'clinic', 'apollo', 'medplus'],
  Education: ['udemy', 'coursera', 'books', 'school fee', 'tuition', 'college'],
  Travel: ['flight', 'irctc', 'hotel', 'makemytrip', 'goibibo', 'booking.com'],
  'Personal Care': ['salon', 'spa', 'haircut', 'gym', 'fitness'],
  Salary: ['salary', 'payroll', 'wages', 'stipend'],
  Investments: ['mutual fund', 'sip', 'stocks', 'zerodha', 'groww', 'fd', 'ppf'],
};

function keywordCategory(description) {
  if (!description) return null;
  const lower = description.toLowerCase();
  for (const [category, keywords] of Object.entries(KEYWORD_MAP)) {
    if (keywords.some((kw) => lower.includes(kw))) return category;
  }
  return null;
}

function buildSpendingSummary(transactions, budgets = [], goals = []) {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - 90);
  const recent = transactions.filter((t) => new Date(t.date) >= cutoff);
  const expenses = recent.filter((t) => t.type === 'expense');
  const income = recent.filter((t) => t.type === 'income');

  const totalExpense = expenses.reduce((s, t) => s + t.amount, 0);
  const totalIncome = income.reduce((s, t) => s + t.amount, 0);

  const byCategory = {};
  expenses.forEach((t) => {
    byCategory[t.category] = (byCategory[t.category] || 0) + t.amount;
  });
  const topCats = Object.entries(byCategory)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([cat, amt]) => `${cat}: ₹${amt.toFixed(0)}`)
    .join(', ');

  const budgetSummary = budgets.length
    ? budgets.map((b) => `${b.category} limit ₹${b.limit}`).join(', ')
    : 'No budgets set';

  return [
    `Period: last 60 days`,
    `Total income: ₹${totalIncome.toFixed(0)}`,
    `Total expenses: ₹${totalExpense.toFixed(0)}`,
    `Net: ₹${(totalIncome - totalExpense).toFixed(0)}`,
    `Top categories: ${topCats || 'none'}`,
    `Budgets: ${budgetSummary}`,
    `Goals: ${goals.length ? goals.map((g) => `${g.title} (${Math.round((g.currentAmount / g.targetAmount) * 100)}%)`).join(', ') : 'None'}`,
  ].join('\n');
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function sanitize(val) {
  if (typeof val !== 'string') return '';
  return val.trim().replace(/^["']|["']$/g, '');
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// ─────────────────────────────────────────────────────────────────────────────
// Smishing (Fraud) Detection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Verifies if an SMS is a potential smishing/fraud attempt.
 * @param {string} text - The raw SMS text
 * @returns {Promise<{isScam: boolean, reason: string}>}
 */
async function verifySmishing(text) {
  if (!text || text.trim().length < 10) return { isScam: false, reason: 'Too short to analyze' };

  const systemPrompt = `You are a cybersecurity expert analyzing SMS messages in India.
Determine if the provided SMS is a scam, phishing, or "smishing" attempt.
Look for:
- Urgent threats (e.g., account block, electricity disconnect, KYC suspension)
- Suspicious links (bit.ly, unexpected domains)
- Requests to call unknown numbers urgently
- Fake lottery or loan approvals
Return ONLY a valid JSON object matching exactly this schema:
{"isScam": boolean, "reason": "brief 1-sentence explanation"}`;

  try {
    const rawResult = await rawCompletion({
      system: systemPrompt,
      user: `Analyze this SMS:\n\n"${text.trim()}"`,
      model: MODEL_FAST,
      maxTokens: 150,
      temperature: 0.1,
      jsonMode: true,
    });

    if (!rawResult) return { isScam: false, reason: 'Analysis failed' };

    const parsed = JSON.parse(rawResult);
    return {
      isScam: Boolean(parsed.isScam),
      reason: parsed.reason || 'Flagged by security heuristics',
    };
  } catch (err) {
    console.error('[AI] Smishing verification failed:', err.message);
    return { isScam: false, reason: 'Analysis error' };
  }
}

module.exports = {
  callAI,
  categorizeTransaction,
  chat,
  keywordCategory,
  parseReceiptText,
  parseSMSWithLLM,
  verifySmishing,
  MODEL_LARGE,
  MODEL_FAST,
};
