// src/services/reportService.js
/**
 * Report Service — Monthly PDF report generation
 *
 * Generates a clean, branded PDF financial report for a user's month.
 * Uses PDFKit (already in package.json).
 *
 * Report sections:
 *   1. Cover — BudGi logo, user name, month/year, health score badge
 *   2. Summary — Income / Expenses / Savings / Savings Rate
 *   3. Category Breakdown — bar chart (ASCII-style via rectangles) + table
 *   4. Top 5 Transactions — table
 *   5. Budget Performance — progress bars per category
 *   6. AI Insights snapshot (if available)
 *   7. Footer — generated date, version
 *
 * Returns a Buffer (PDF bytes) — caller pipes to HTTP response or saves to disk.
 */

'use strict';

const PDFDocument = require('pdfkit');
const Transaction = require('../../../models/Transaction');
const Budget = require('../../../models/Budget');
const User = require('../../../models/User');

// ── Brand palette ─────────────────────────────────────────────────────────────
const COLORS = {
  accent: '#7C6AF5',
  accentDark: '#5B4ED4',
  green: '#2ECC8F',
  red: '#F5616A',
  amber: '#F5A623',
  blue: '#4D9CF5',
  bg: '#F4F3FF',
  text: '#0D0C1A',
  muted: '#9896B0',
  border: '#E5E3F5',
  white: '#FFFFFF',
};

const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

/**
 * Generate a monthly PDF report.
 *
 * @param {string} userId
 * @param {number} year
 * @param {number} month  — 1-indexed
 * @param {string[]} [aiInsights]  — optional pre-fetched AI insights
 * @returns {Promise<Buffer>}
 */
async function generateMonthlyReport(userId, year, month, aiInsights = []) {
  const [user, transactions, budgets] = await Promise.all([
    User.findById(userId).lean(),
    Transaction.find({
      userId,
      date: {
        $gte: new Date(Date.UTC(year, month - 1, 1)),
        $lt: new Date(Date.UTC(year, month, 1)),
      },
    })
      .sort({ date: -1 })
      .lean(),
    Budget.find({ userId }).lean(),
  ]);

  const income = transactions.filter((t) => t.type === 'income');
  const expense = transactions.filter((t) => t.type === 'expense');

  const totalIncome = income.reduce((s, t) => s + t.amount, 0);
  const totalExpense = expense.reduce((s, t) => s + t.amount, 0);
  const netSavings = totalIncome - totalExpense;
  const savingsRate = totalIncome > 0 ? (netSavings / totalIncome) * 100 : 0;

  // Category totals
  const byCat = {};
  for (const t of expense) {
    byCat[t.category] = (byCat[t.category] || 0) + t.amount;
  }
  const catSorted = Object.entries(byCat).sort((a, b) => b[1] - a[1]);

  // Return as Buffer
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({ size: 'A4', margin: 48, compress: true });
    const chunks = [];
    doc.on('data', (c) => chunks.push(c));
    doc.on('end', () => resolve(Buffer.concat(chunks)));
    doc.on('error', reject);

    buildPDF(doc, {
      user,
      year,
      month,
      totalIncome,
      totalExpense,
      netSavings,
      savingsRate,
      catSorted,
      transactions,
      budgets,
      aiInsights,
    });

    doc.end();
  });
}

// ── PDF builder ───────────────────────────────────────────────────────────────

function buildPDF(doc, data) {
  const {
    user,
    year,
    month,
    totalIncome,
    totalExpense,
    netSavings,
    savingsRate,
    catSorted,
    transactions,
    budgets,
    aiInsights,
  } = data;

  const PAGE_W = doc.page.width - 96; // usable width (48 margins each side)
  const monthLabel = `${MONTH_NAMES[month - 1]} ${year}`;

  // ── Cover section ────────────────────────────────────────────────────────
  // Purple gradient header bar
  doc.rect(0, 0, doc.page.width, 160).fill(COLORS.accent);

  // BudGi wordmark
  doc.font('Helvetica-Bold').fontSize(32).fillColor(COLORS.white).text('BudGi', 48, 38);

  doc.font('Helvetica').fontSize(11).fillColor('rgba(255,255,255,0.75)').text('Financial Report', 48, 78);

  // Report title
  doc.font('Helvetica-Bold').fontSize(18).fillColor(COLORS.white).text(monthLabel, 48, 105);

  // User name (right-aligned)
  if (user?.name) {
    doc.font('Helvetica').fontSize(11).fillColor('rgba(255,255,255,0.85)').text(user.name, 48, 110, { align: 'right' });
  }

  // Health score badge
  if (user?.healthScore != null) {
    const band = user.healthScore >= 80 ? COLORS.green : user.healthScore >= 60 ? COLORS.amber : COLORS.red;
    doc.roundedRect(doc.page.width - 120, 40, 72, 32, 8).fill(band);
    doc
      .font('Helvetica-Bold')
      .fontSize(18)
      .fillColor(COLORS.white)
      .text(user.healthScore, doc.page.width - 108, 47);
    doc
      .font('Helvetica')
      .fontSize(7)
      .fillColor('rgba(255,255,255,0.8)')
      .text('/100', doc.page.width - 78, 58);
    doc
      .font('Helvetica')
      .fontSize(7)
      .fillColor('rgba(255,255,255,0.7)')
      .text('Health Score', doc.page.width - 118, 75);
  }

  let y = 188;

  // ── Summary cards ────────────────────────────────────────────────────────
  const summaryItems = [
    { label: 'Total Income', value: `₹${fmt(totalIncome)}`, color: COLORS.green },
    { label: 'Total Expenses', value: `₹${fmt(totalExpense)}`, color: COLORS.red },
    {
      label: 'Net Savings',
      value: `₹${fmt(Math.abs(netSavings))}`,
      color: netSavings >= 0 ? COLORS.green : COLORS.red,
    },
    { label: 'Savings Rate', value: `${savingsRate.toFixed(1)}%`, color: COLORS.accent },
  ];

  const cardW = (PAGE_W - 30) / 4;
  summaryItems.forEach((item, i) => {
    const x = 48 + i * (cardW + 10);
    doc.roundedRect(x, y, cardW, 64, 8).fill(COLORS.bg);
    doc.rect(x, y, 3, 64).fill(item.color);
    doc
      .font('Helvetica')
      .fontSize(9)
      .fillColor(COLORS.muted)
      .text(item.label, x + 12, y + 10, { width: cardW - 16 });
    doc
      .font('Helvetica-Bold')
      .fontSize(15)
      .fillColor(COLORS.text)
      .text(item.value, x + 10, y + 28, { width: cardW - 16 });
  });

  y += 88;
  divider(doc, y);
  y += 18;

  // ── Category breakdown ────────────────────────────────────────────────────
  section(doc, y, 'Spending by Category');
  y += 28;

  const maxCatAmt = catSorted[0]?.[1] || 1;
  const barMaxW = PAGE_W * 0.48;

  catSorted.slice(0, 8).forEach(([cat, amt]) => {
    const pct = amt / maxCatAmt;
    const barW = Math.max(4, pct * barMaxW);
    const catColor = categoryColor(cat);

    doc.font('Helvetica').fontSize(9).fillColor(COLORS.text).text(cat, 48, y, { width: 140, lineBreak: false });
    doc.roundedRect(196, y - 1, barW, 11, 3).fill(catColor);
    doc
      .font('Helvetica-Bold')
      .fontSize(9)
      .fillColor(COLORS.text)
      .text(`₹${fmt(amt)}`, 196 + barW + 8, y);
    y += 18;
  });

  y += 8;
  divider(doc, y);
  y += 18;

  // ── Budget performance ────────────────────────────────────────────────────
  if (budgets.length) {
    section(doc, y, 'Budget Performance');
    y += 28;

    const spendByCat = Object.fromEntries(catSorted);
    budgets.slice(0, 6).forEach((b) => {
      const spent = spendByCat[b.category] || 0;
      const ratio = spent / b.limit;
      const exceeded = ratio > 1;
      const barColor = exceeded ? COLORS.red : ratio > 0.8 ? COLORS.amber : COLORS.green;
      const barW = Math.min(1, ratio) * (PAGE_W * 0.52);

      doc
        .font('Helvetica')
        .fontSize(9)
        .fillColor(COLORS.text)
        .text(b.category, 48, y, { width: 120, lineBreak: false });
      // Track background
      doc.roundedRect(176, y - 1, PAGE_W * 0.52, 11, 3).fill(COLORS.border);
      // Filled portion
      if (barW > 0) doc.roundedRect(176, y - 1, barW, 11, 3).fill(barColor);
      // Label
      doc
        .font('Helvetica')
        .fontSize(9)
        .fillColor(exceeded ? COLORS.red : COLORS.muted)
        .text(`${Math.round(ratio * 100)}%  (₹${fmt(spent)} / ₹${fmt(b.limit)})`, 176 + PAGE_W * 0.52 + 8, y);
      y += 18;
    });

    y += 8;
    divider(doc, y);
    y += 18;
  }

  // ── Top transactions ──────────────────────────────────────────────────────
  section(doc, y, 'Top Transactions');
  y += 28;

  const topTxns = [...transactions].sort((a, b) => b.amount - a.amount).slice(0, 6);
  topTxns.forEach((t) => {
    const isIncome = t.type === 'income';
    const dateStr = new Date(t.date).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });

    doc.font('Helvetica').fontSize(9).fillColor(COLORS.muted).text(dateStr, 48, y, { width: 52, lineBreak: false });
    doc
      .font('Helvetica')
      .fontSize(9)
      .fillColor(COLORS.text)
      .text(t.description?.slice(0, 36) || t.category, 104, y, { width: 240, lineBreak: false });
    doc
      .font('Helvetica')
      .fontSize(9)
      .fillColor(COLORS.muted)
      .text(t.category, 350, y, { width: 100, lineBreak: false });
    doc
      .font('Helvetica-Bold')
      .fontSize(9)
      .fillColor(isIncome ? COLORS.green : COLORS.red)
      .text(`${isIncome ? '+' : '-'}₹${fmt(t.amount)}`, 454, y, { align: 'right', width: 90 });

    y += 16;
    if (y > doc.page.height - 80) {
      doc.addPage();
      y = 48;
    }
  });

  y += 8;
  divider(doc, y);
  y += 18;

  // ── AI Insights ───────────────────────────────────────────────────────────
  if (aiInsights.length) {
    if (y > doc.page.height - 120) {
      doc.addPage();
      y = 48;
    }

    section(doc, y, 'AI Insights');
    y += 28;

    aiInsights.slice(0, 4).forEach((insight, i) => {
      doc.roundedRect(48, y, PAGE_W, 36, 6).fill(COLORS.bg);
      doc.rect(48, y, 3, 36).fill(COLORS.accent);
      doc
        .font('Helvetica')
        .fontSize(9)
        .fillColor(COLORS.text)
        .text(`${i + 1}. ${insight}`, 60, y + 8, { width: PAGE_W - 20, height: 22, ellipsis: true });
      y += 44;
    });
  }

  // ── Footer ────────────────────────────────────────────────────────────────
  const footerY = doc.page.height - 40;
  doc.rect(0, footerY - 8, doc.page.width, 1).fill(COLORS.border);

  doc
    .font('Helvetica')
    .fontSize(8)
    .fillColor(COLORS.muted)
    .text(
      `Generated by BudGi · ${new Date().toLocaleDateString('en-IN')} · This report is for personal use only`,
      48,
      footerY,
      { align: 'center', width: PAGE_W },
    );
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function fmt(n) {
  return Math.round(n).toLocaleString('en-IN');
}

function section(doc, y, title) {
  doc.font('Helvetica-Bold').fontSize(12).fillColor(COLORS.accentDark).text(title, 48, y);
}

function divider(doc, y) {
  doc.rect(48, y, doc.page.width - 96, 1).fill(COLORS.border);
}

function categoryColor(cat) {
  const map = {
    'Food & Dining': '#FF8800',
    Groceries: '#F5A623',
    Transportation: '#4D9CF5',
    Shopping: '#7C6AF5',
    'Bills & Utilities': '#F5616A',
    Entertainment: '#9B59B6',
    Healthcare: '#2ECC8F',
    Education: '#3498DB',
    Travel: '#1ABC9C',
    'Personal Care': '#E91E63',
    Investments: '#27AE60',
    Salary: '#2ECC8F',
  };
  return map[cat] || '#8785A6';
}

module.exports = { generateMonthlyReport };
