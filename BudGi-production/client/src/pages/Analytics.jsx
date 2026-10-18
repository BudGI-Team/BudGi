import { useState, useMemo } from 'react';
import { motion } from 'framer-motion';
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts';
import { format, subMonths } from 'date-fns';
import {
  TrendingUp,
  TrendingDown,
  Target,
  FileDown,
  LineChart as LineIcon,
  Cpu,
  Zap,
} from '../lib/icons';
import { useAnalytics, useSpendingPrediction, useSuggestions } from '../hooks/useAI';
import { useTransactions } from '../hooks/useTransactions';
import { analyticsApi } from '../lib/api';
import useStore from '../store/useStore';
import { fmt } from '../lib/utils';
import BrandLoader from '../components/ui/BrandLoader';
import RadialInsightsChart from '../components/charts/RadialInsightsChart';
import './Analytics.css';

const CustomTooltip = ({ active, payload, label, currency }) => {
  if (!active || !payload?.length) return null;
  return (
    <div
      style={{
        background: 'var(--bg-elevated)',
        border: '1px solid var(--border-strong)',
        borderRadius: 10,
        padding: '10px 14px',
        fontSize: 12,
        color: 'var(--text-primary)',
      }}
    >
      <div style={{ color: 'var(--text-muted)', marginBottom: 6 }}>{label}</div>
      {payload.map((p) => (
        <div
          key={p.dataKey}
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            marginTop: 2,
          }}
        >
          <span
            style={{
              width: 8,
              height: 8,
              borderRadius: 2,
              background: p.color,
              flexShrink: 0,
            }}
          />
          <span
            style={{
              color: 'var(--text-secondary)',
              textTransform: 'capitalize',
            }}
          >
            {p.dataKey}:
          </span>
          <span style={{ fontWeight: 600 }}>{fmt(p.value, currency)}</span>
        </div>
      ))}
    </div>
  );
};

export default function Analytics() {
  // NOTE: analytics data from /api/analytics/summary is already selected via useAnalytics hook
  const { user } = useStore();
  const currency = user?.currency || 'INR';

  const [selectedDate, setSelectedDate] = useState(new Date());

  const queryParams = useMemo(() => ({
    year: selectedDate.getUTCFullYear(),
    month: selectedDate.getUTCMonth() + 1,
  }), [selectedDate]);

  const txParams = useMemo(() => ({ limit: 500 }), []);

  const { data: analytics, isLoading } = useAnalytics(queryParams);
  const { data: transactions = [] } = useTransactions(txParams);

  // Real backend prediction from linear regression model
  const { data: predictionData } = useSpendingPrediction(queryParams);

  // Real AI-generated suggestions from Groq
  const { data: aiSuggestions } = useSuggestions();

  /* ── 6-month trend — use server-computed spendingByMonth when available ── */
  const monthlyTrend = useMemo(() => {
    // Build from transaction data for income+expense+net (server summary only has expenses by month)
    const isInc = (t) => t.type === 'income' || t.type === 'credit';
    const isExp = (t) => (t.type === 'expense' || t.type === 'debit') && t.category !== 'Transfer';

    return Array.from({ length: 6 }, (_, i) => {
      const d = subMonths(new Date(), 5 - i);
      const key = format(d, 'yyyy-MM');
      const label = format(d, 'MMM');
      const txs = transactions.filter((t) => t.date?.slice(0, 7) === key);
      const income = txs.filter(isInc).reduce((s, t) => s + t.amount, 0);
      const expense = txs.filter(isExp).reduce((s, t) => s + t.amount, 0);
      return { name: label, income, expense, net: income - expense };
    });
  }, [transactions]);

  /* ── Category breakdown — prefer server-computed data, fallback to client ── */
  const categoryBreakdown = useMemo(() => {
    // Use server analytics if available (more accurate — includes all transactions)
    if (analytics?.categoryBreakdown?.length) {
      return analytics.categoryBreakdown.slice(0, 8).map((c) => ({ name: c.category, value: c.total }));
    }
    // Fallback to client-side computation
    const isExp = (t) => (t.type === 'expense' || t.type === 'debit') && t.category !== 'Transfer';
    const cats = {};
    transactions
      .filter(isExp)
      .forEach((t) => {
        cats[t.category] = (cats[t.category] || 0) + t.amount;
      });
    return Object.entries(cats)
      .sort(([, a], [, b]) => b - a)
      .slice(0, 8)
      .map(([name, value]) => ({ name, value }));
  }, [transactions, analytics]);

  /* ── Day-of-week spending ── */
  const dowData = useMemo(() => {
    const isExp = (t) => (t.type === 'expense' || t.type === 'debit') && t.category !== 'Transfer';
    const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const totals = new Array(7).fill(0);
    transactions
      .filter(isExp)
      .forEach((t) => {
        const dow = (new Date(t.date).getDay() + 6) % 7;
        totals[dow] += t.amount;
      });
    return days.map((name, i) => ({ name, amount: totals[i] }));
  }, [transactions]);

  /* ── Current month totals for Radial Insights ── */
  const monthTotals = useMemo(() => {
    const isInc = (t) => t.type === 'income' || t.type === 'credit';
    const isExp = (t) => (t.type === 'expense' || t.type === 'debit') && t.category !== 'Transfer';
    const key = format(selectedDate, 'yyyy-MM');
    const txs = transactions.filter((t) => t.date?.slice(0, 7) === key);
    const spending = txs.filter(isExp).reduce((s, t) => s + t.amount, 0);
    const income = txs.filter(isInc).reduce((s, t) => s + t.amount, 0);
    return {
      spending: spending || analytics?.totalExpense || 0,
      income: income || analytics?.totalIncome || 0,
      net: (income || analytics?.totalIncome || 0) - (spending || analytics?.totalExpense || 0),
    };
  }, [transactions, selectedDate, analytics]);

  /* ── Real prediction from backend predictionCore (linear regression) ── */
  const topCat = analytics?.topCategory;

  // Build prediction chart data with real backend forecast
  const predData = useMemo(() => {
    const actuals = monthlyTrend.map((m) => ({ name: m.name, actual: m.expense }));
    // Add next month predicted bar from real backend prediction
    const nextMonthPredicted = predictionData?.overall?.predictedTotal ?? predictionData?.predictedTotal ?? 0;
    if (nextMonthPredicted > 0) {
      const nextMonth = format(subMonths(new Date(), -1), 'MMM');
      actuals.push({ name: nextMonth, actual: 0, predicted: nextMonthPredicted });
    }
    return actuals;
  }, [monthlyTrend, predictionData]);

  // Real prediction values from backend
  const prediction = {
    predicted_next_month_expenses_total: predictionData?.overall?.predictedTotal ?? predictionData?.predictedTotal ?? 0,
    confidence: predictionData?.overall?.confidence ?? predictionData?.confidence ?? 0,
    trendDirection: predictionData?.overall?.trendDirection ?? predictionData?.trendDirection ?? 'stable',
  };
  const spendingAnalysis = {
    highest_spending_category: topCat?.category ?? '—',
    monthly_comparison: {
      delta_percent:
        monthlyTrend.length >= 2
          ? (() => {
            const last = monthlyTrend[monthlyTrend.length - 1]?.expense ?? 0;
            const prev = monthlyTrend[monthlyTrend.length - 2]?.expense ?? 0;
            return prev > 0 ? +(((last - prev) / prev) * 100).toFixed(1) : 0;
          })()
          : 0,
    },
  };

  // Real AI suggestions from Groq (via /api/ai/suggestions endpoint)
  const suggestions = useMemo(() => {
    const items = [];
    if (aiSuggestions?.insight) items.push(aiSuggestions.insight);
    if (aiSuggestions?.warning) items.push(aiSuggestions.warning);
    if (aiSuggestions?.suggestion) items.push(aiSuggestions.suggestion);
    // If AI returned results, use them; otherwise fallback to category-based tips
    if (items.length > 0) return items;
    return (analytics?.categoryBreakdown ?? [])
      .slice(0, 3)
      .map(
        (c) =>
          `Review your ${c.category} spending (${fmt(c.total, currency)}) for savings opportunities.`,
      );
  }, [aiSuggestions, analytics, currency]);

  const handlePrevMonth = () => setSelectedDate((prev) => subMonths(prev, 1));
  const handleNextMonth = () =>
    setSelectedDate((prev) => {
      const next = new Date(prev);
      next.setMonth(next.getMonth() + 1);
      return next;
    });
  const isCurrentMonth =
    selectedDate.getUTCMonth() === new Date().getUTCMonth() &&
    selectedDate.getUTCFullYear() === new Date().getUTCFullYear();

  const monthName = format(selectedDate, 'MMMM yyyy');

  const [pdfLoading, setPdfLoading] = useState(false);

  const handlePdf = async () => {
    setPdfLoading(true);
    try {
      await analyticsApi.downloadPdf();
    } catch (e) {
      console.error('PDF download failed:', e);
    }
    setPdfLoading(false);
  };

  if (isLoading) {
    return (
      <div className="analytics-page">
        <BrandLoader minHeight="65vh" message="Computing financial analytics..." />
      </div>
    );
  }

  return (
    <div className="analytics-page">
      {/* ── Page header with PDF button ── */}
      <div className="analytics-header">
        <div>
          <h1 className="analytics-page-title">Analytics</h1>
          <p className="analytics-page-sub">Comprehensive spending trends and forecasts</p>
        </div>

        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          <div
            className="month-selector"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              background: 'var(--glass-bg)',
              padding: '6px 12px',
              borderRadius: '14px',
              border: '1px solid var(--border)',
            }}
          >
            <button
              onClick={handlePrevMonth}
              className="month-nav-btn"
              style={{
                background: 'transparent',
                border: 'none',
                color: 'var(--text-secondary)',
                cursor: 'pointer',
                display: 'flex',
                padding: 4,
              }}
            >
              <TrendingDown size={18} style={{ transform: 'rotate(90deg)' }} />
            </button>
            <span
              style={{
                fontWeight: 700,
                fontSize: 14,
                minWidth: 120,
                textAlign: 'center',
                color: 'var(--text-primary)',
              }}
            >
              {monthName}
            </span>
            <button
              onClick={handleNextMonth}
              className="month-nav-btn"
              style={{
                background: 'transparent',
                border: 'none',
                color: 'var(--text-secondary)',
                cursor: 'pointer',
                display: 'flex',
                padding: 4,
              }}
              disabled={isCurrentMonth}
            >
              <TrendingUp size={18} style={{ transform: 'rotate(-90deg)', opacity: isCurrentMonth ? 0.3 : 1 }} />
            </button>
          </div>

          <motion.button
            className="analytics-pdf-btn"
            onClick={handlePdf}
            disabled={pdfLoading}
            whileTap={{ scale: 0.96 }}
          >
            <FileDown size={15} />
            {pdfLoading ? 'Generating...' : 'Download PDF'}
          </motion.button>
        </div>
      </div>

      {/* ── Overview cards ── */}
      <div className="analytics-overview">
        {[
          {
            label: 'Highest Spend Category',
            value: spendingAnalysis?.highest_spending_category || '—',
            icon: TrendingDown,
            color: 'red',
          },
          {
            label: 'Next Month Forecast',
            value: prediction?.predicted_next_month_expenses_total
              ? fmt(prediction.predicted_next_month_expenses_total, currency)
              : '—',
            icon: LineIcon,
            color: 'accent',
          },
          {
            label: 'Monthly Δ',
            value:
              spendingAnalysis?.monthly_comparison?.delta_percent != null
                ? `${spendingAnalysis.monthly_comparison.delta_percent > 0 ? '+' : ''}${spendingAnalysis.monthly_comparison.delta_percent.toFixed(1)}%`
                : '—',
            icon: TrendingUp,
            color: (spendingAnalysis?.monthly_comparison?.delta_percent || 0) > 0 ? 'red' : 'green',
          },
          {
            label: 'Available Insights',
            value: suggestions.length.toString(),
            icon: Target,
            color: 'blue',
          },
        ].map((card, i) => (
          <motion.div
            key={card.label}
            className={`analytics-card analytics-card-${card.color}`}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: i * 0.07 }}
          >
            <div className={`analytics-card-icon analytics-icon-${card.color}`}>
              <card.icon size={16} />
            </div>
            <div className="analytics-card-value">{card.value}</div>
            <div className="analytics-card-label">{card.label}</div>
          </motion.div>
        ))}
      </div>

      {/* ── Charts grid ── */}
      <div className="analytics-grid">
        {/* Monthly income vs expense area chart */}
        <motion.div
          className="analytics-chart-card span-2"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.3 }}
        >
          <div className="chart-header">
            <div>
              <h3>Income vs Expenses</h3>
              <p>6-month trend overview</p>
            </div>
            <div className="chart-legend">
              <span>
                <i style={{ background: 'var(--green)' }} /> Income
              </span>
              <span>
                <i style={{ background: 'var(--accent)' }} /> Expenses
              </span>
              <span>
                <i style={{ background: 'var(--blue)' }} /> Net
              </span>
            </div>
          </div>
          <div style={{ height: 220 }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={monthlyTrend}>
                <defs>
                  <linearGradient id="colorIncome" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#30D68C" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#30D68C" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="colorExpense" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="var(--accent, #38BDF8)" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="var(--accent, #38BDF8)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis
                  dataKey="name"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                />
                <YAxis hide />
                <Tooltip content={<CustomTooltip currency={currency} />} />
                <Area
                  type="monotone"
                  dataKey="income"
                  stroke="#30D68C"
                  strokeWidth={2}
                  fill="url(#colorIncome)"
                  dot={false}
                />
                <Area
                  type="monotone"
                  dataKey="expense"
                  stroke="var(--accent, #38BDF8)"
                  strokeWidth={2}
                  fill="url(#colorExpense)"
                  dot={false}
                />
                <Line
                  type="monotone"
                  dataKey="net"
                  stroke="#4D9CF5"
                  strokeWidth={1.5}
                  dot={false}
                  strokeDasharray="4 4"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </motion.div>

        {/* Category Breakdown (Radial Insights Clock Ray Dial inspired by Image 1) */}
        <RadialInsightsChart
          categories={categoryBreakdown}
          totalSpending={monthTotals.spending}
          totalIncome={monthTotals.income}
          netSavings={monthTotals.net}
          currency={currency}
          monthLabel={monthName}
          className="span-2"
        />

        {/* Day of week bar */}
        <motion.div
          className="analytics-chart-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.44 }}
        >
          <div className="chart-header">
            <div>
              <h3>Spending by Day</h3>
              <p>When you spend most</p>
            </div>
          </div>
          <div style={{ height: 200 }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={dowData} barSize={16}>
                <XAxis
                  dataKey="name"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                />
                <YAxis hide />
                <Tooltip
                  contentStyle={{
                    background: 'var(--bg-elevated)',
                    border: '1px solid var(--border-strong)',
                    borderRadius: 10,
                    fontSize: 12,
                    color: 'var(--text-primary)',
                  }}
                  formatter={(v) => [fmt(v, currency)]}
                />
                <Bar dataKey="amount" radius={[4, 4, 0, 0]}>
                  {dowData.map((entry, i) => (
                    <Cell key={i} fill={i >= 5 ? 'var(--accent)' : 'var(--blue)'} opacity={0.8} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </motion.div>

        {/* Budget prediction */}
        <motion.div
          className="analytics-chart-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.5 }}
        >
          <div className="chart-header">
            <div>
              <h3>AI Spending Forecast</h3>
              <p>Predicted expenses for next month</p>
            </div>
          </div>
          {prediction && (
            <div className="prediction-badge">{fmt(prediction.predicted_next_month_expenses_total, currency)}</div>
          )}
          {predData.length > 0 ? (
            <div style={{ height: 200 }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={predData} barSize={16}>
                  <XAxis
                    dataKey="name"
                    axisLine={false}
                    tickLine={false}
                    tick={{ fill: 'var(--text-muted)', fontSize: 10 }}
                  />
                  <YAxis hide />
                  <Tooltip
                    contentStyle={{
                      background: 'var(--bg-elevated)',
                      border: '1px solid var(--border-strong)',
                      borderRadius: 10,
                      fontSize: 12,
                      color: 'var(--text-primary)',
                    }}
                    formatter={(v) => [fmt(v, currency)]}
                  />
                  <Bar dataKey="actual" fill="var(--blue)" radius={[4, 4, 0, 0]} opacity={0.7} />
                  <Bar dataKey="predicted" fill="var(--accent)" radius={[4, 4, 0, 0]} opacity={0.9} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <div className="chart-empty">Add more transactions for predictions</div>
          )}
        </motion.div>

        <motion.div
          className="analytics-chart-card span-2"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.56 }}
        >
          <div className="chart-header">
            <div>
              <h3>AI-Powered Recommendations</h3>
              <p>Personalized insights from BudGi Copilot</p>
            </div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                color: 'var(--accent-bright)',
                fontSize: 11,
                fontWeight: 600,
              }}
            >
              <Cpu size={12} /> Predictive Engine
            </div>
          </div>
          {suggestions.length > 0 ? (
            <div className="suggestions-list">
              {suggestions.map((s, i) => (
                <motion.div
                  key={i}
                  className="suggestion-item"
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.6 + i * 0.08 }}
                >
                  <span
                    className="suggestion-num"
                    style={{
                      background: i === 0 ? 'var(--accent)' : i === 1 ? 'var(--amber)' : 'var(--blue)',
                      color: '#fff',
                    }}
                  >
                    {i === 0 ? <Zap size={11} /> : String(i + 1).padStart(2, '0')}
                  </span>
                  <p>{s}</p>
                </motion.div>
              ))}
            </div>
          ) : (
            <div className="chart-empty">Add transactions and budgets to get personalized AI suggestions</div>
          )}
        </motion.div>
      </div>
    </div>
  );
}
