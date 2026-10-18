import { useMemo, useState, useCallback } from 'react';
import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import {
  Wallet,
  TrendingDown,
  TrendingUp,
  Vault,
  AlertTriangle,
  AlertCircle,
  ArrowRight,
  Compass,
  Plus,
  ShieldCheck,
  Calendar,
  Receipt,
  Clock,
  Trash2,
  RefreshCw,
  CheckCircle2,
  BarChart2,
} from '../lib/icons';
import { getCategoryIcon, CategoryAvatar } from '../lib/categoryIcons';
import budgiLogo from '../logo/budgi-logo-new.png';
import {
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
  PieChart,
  Pie,
  Sector,
  AreaChart,
  Area,
  CartesianGrid,
  Brush,
} from 'recharts';
import { format, subMonths } from 'date-fns';
import { useAnalytics, useHealthScore, useInsights, useSpendingPrediction } from '../hooks/useAI';
import { useTransactions, useTransactionSummary } from '../hooks/useTransactions';
import { alertsApi, goalsApi, budgetApi, transactionsApi } from '../lib/api';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import useStore from '../store/useStore';
import StatCard from '../components/ui/StatCard';
import ConfirmModal from '../components/ui/ConfirmModal';
import toast from 'react-hot-toast';
import './Dashboard.css';
import { fmt, getCategoryMeta } from '../lib/utils';

// BudGi signature minimal calm category palette
const CATEGORY_COLORS = [
  '#5E72E4',
  '#38A169',
  '#E07A5F',
  '#4A90E2',
  '#8E7CC3',
  '#D97706',
  '#2A9D8F',
  '#D95D74',
  '#3A9AB7',
  '#5C768D',
];

export default function Dashboard() {
  const { user } = useStore();
  const queryClient = useQueryClient();
  const currency = user?.currency || 'INR';

  const [selectedDate, setSelectedDate] = useState(new Date());
  const [view, setView] = useState('overview');
  const [showClearConfirm, setShowClearConfirm] = useState(false);
  const [isClearing, setIsClearing] = useState(false);
  const [activeCategoryIndex, setActiveCategoryIndex] = useState(null);

  const queryParams = useMemo(() => ({
    year: selectedDate.getUTCFullYear(),
    month: selectedDate.getUTCMonth() + 1,
  }), [selectedDate]);

  const txFilter = useMemo(() => ({
    startDate: new Date(Date.UTC(selectedDate.getUTCFullYear(), selectedDate.getUTCMonth(), 1)).toISOString(),
    endDate: new Date(Date.UTC(selectedDate.getUTCFullYear(), selectedDate.getUTCMonth() + 1, 1)).toISOString(),
    limit: 1000,
  }), [selectedDate]);

  const { data: analytics } = useAnalytics(queryParams);
  const { data: summary, isLoading: summaryLoading } = useTransactionSummary(queryParams);
  const { data: transactions = [], isLoading: txLoading } = useTransactions(txFilter);
  const isDataLoading = summaryLoading || txLoading;

  const upcomingSubs = useMemo(() => {
    const subKeywords = [
      'netflix', 'spotify', 'prime', 'hotstar', 'disney', 'youtube', 'apple', 'google',
      'icloud', 'chatgpt', 'openai', 'github', 'patreon', 'gym', 'wifi', 'broadband',
      'jio', 'airtel', 'fiber', 'playstation', 'xbox', 'audible', 'nyt', 'medium',
      'substack', 'zee5', 'sonyliv', 'swiggy', 'zomato', 'subscription', 'recurring'
    ];
    const matches = transactions.filter((t) => {
      if (t.type !== 'expense' && t.type !== 'debit') return false;
      if (t.category === 'Transfer') return false;
      if (t.isRecurring) return true;
      const cat = (t.category || '').toLowerCase();
      if (cat.includes('subscription') || cat.includes('recurring')) return true;
      const desc = `${t.merchant || ''} ${t.description || ''}`.toLowerCase();
      return subKeywords.some((kw) => desc.includes(kw));
    });
    const seen = new Set();
    const deduped = [];
    for (const m of matches) {
      const key = (m.merchant || m.description || '').toLowerCase().trim();
      if (!seen.has(key)) {
        seen.add(key);
        deduped.push(m);
      }
    }
    return deduped.slice(0, 4);
  }, [transactions]);

  // FIX: fetch live alerts from /api/alerts (not analytics)
  const { data: alertsEnv } = useQuery({
    queryKey: ['alerts', { unreadOnly: true, limit: 4 }],
    queryFn: () => alertsApi.list({ unreadOnly: true, limit: 4 }),
    select: (env) => env?.data?.alerts ?? [],
    staleTime: 60_000,
  });
  const alerts = alertsEnv ?? [];

  const { data: budgets = [] } = useQuery({
    queryKey: ['budgets'],
    queryFn: budgetApi.list,
    select: (env) => env?.data?.budgets ?? [],
    staleTime: 30_000,
  });

  const { data: goalsRes } = useQuery({
    queryKey: ['goals'],
    queryFn: () => goalsApi.list(),
    staleTime: 60_000,
  });
  const goals = useMemo(() => goalsRes?.data?.goals || [], [goalsRes]);
  const totalGoalTarget = useMemo(() => {
    return goals.reduce((sum, g) => sum + (g.targetAmount || 0), 0);
  }, [goals]);

  // Analytics shape from /api/analytics/summary:
  // { totalSpent:{total,count}, categoryBreakdown:[{category,total,percentage}],
  //   topCategory, savingsRate:{income,expenses,savings,rate}, spendingByWeek }
  const catBreakdown = analytics?.categoryBreakdown ?? [];

  // FIX: build spendByCategory map from categoryBreakdown for budget health panel
  const spendByCategory = Object.fromEntries(catBreakdown.map((c) => [c.category, c.total]));

  /* ── Derived monthly stats and trends from transactions ── */
  const stats = useMemo(() => {
    if (!transactions.length) {
      return { income: 0, expenses: 0, balance: 0, savings: 0, incomeTrend: 0, expenseTrend: 0, balanceTrend: 0 };
    }

    const monthStart = new Date(Date.UTC(selectedDate.getUTCFullYear(), selectedDate.getUTCMonth(), 1));
    const prevMonthStart = new Date(monthStart);
    prevMonthStart.setUTCMonth(prevMonthStart.getUTCMonth() - 1);
    const prevMonthEnd = new Date(monthStart.getTime() - 1);

    const thisMonth = transactions.filter((t) => new Date(t.date) >= monthStart);
    const lastMonth = transactions.filter((t) => {
      const d = new Date(t.date);
      return d >= prevMonthStart && d <= prevMonthEnd;
    });

    const isExpenseTx = (t) => (t.type === 'expense' || t.type === 'debit') && t.category !== 'Transfer';
    const isIncomeTx = (t) => (t.type === 'income' || t.type === 'credit') && t.category !== 'Transfer';

    const income = thisMonth.filter(isIncomeTx).reduce((s, t) => s + t.amount, 0);
    const expenses = thisMonth.filter(isExpenseTx).reduce((s, t) => s + t.amount, 0);

    const prevIncome = lastMonth.filter(isIncomeTx).reduce((s, t) => s + t.amount, 0);
    const prevExpenses = lastMonth.filter(isExpenseTx).reduce((s, t) => s + t.amount, 0);

    const calcTrend = (curr, prev) => {
      if (prev === 0) return curr > 0 ? 100 : 0;
      return +(((curr - prev) / prev) * 100).toFixed(1);
    };

    const incomeTrend = calcTrend(income, prevIncome);
    const expenseTrend = calcTrend(expenses, prevExpenses);
    const balanceTrend = calcTrend(income - expenses, prevIncome - prevExpenses);

    return {
      income,
      expenses,
      balance: income - expenses,
      savings: Math.max(0, income - expenses),
      incomeTrend,
      expenseTrend,
      balanceTrend,
    };
  }, [transactions, selectedDate]);

  /* ── 6-month trend chart (from backend + client for income) ── */
  const trendData = useMemo(() => {
    const backendTrends = analytics?.spendingByMonth ?? [];
    // Build from both backend expense totals and transaction data for income
    return Array.from({ length: 6 }, (_, i) => {
      const d = subMonths(new Date(), 5 - i);
      const key = format(d, 'yyyy-MM');
      const found = backendTrends.find((t) => t.label === key);
      // Calculate income from transactions for this month
      const monthIncome = transactions
        .filter((t) => (t.type === 'income' || t.type === 'credit') && t.category !== 'Transfer' && t.date?.slice(0, 7) === key)
        .reduce((s, t) => s + t.amount, 0);
      return {
        name: format(d, 'MMM'),
        expense: found?.total || 0,
        income: monthIncome || user?.monthlyIncome || 0,
      };
    });
  }, [analytics, transactions, user?.monthlyIncome]);

  /* ── Category pie (from backend analytics) ── */
  const categoryData = useMemo(() => {
    return (analytics?.categoryBreakdown ?? []).slice(0, 6).map((c) => ({ name: c.category, value: c.total }));
  }, [analytics]);

  const totalCategorySpend = useMemo(() => {
    return categoryData.reduce((acc, curr) => acc + (curr.value || 0), 0);
  }, [categoryData]);

  const renderActiveShape = (props) => {
    const { cx, cy, innerRadius, outerRadius, startAngle, endAngle, fill } = props;
    return (
      <g>
        <Sector
          cx={cx}
          cy={cy}
          innerRadius={innerRadius - 3}
          outerRadius={outerRadius + 7}
          startAngle={startAngle}
          endAngle={endAngle}
          fill={fill}
        />
        <Sector
          cx={cx}
          cy={cy}
          startAngle={startAngle}
          endAngle={endAngle}
          innerRadius={outerRadius + 10}
          outerRadius={outerRadius + 12}
          fill={fill}
          opacity={0.8}
        />
      </g>
    );
  };

  const recentTx = transactions.slice(0, 8);

  // FIX: Use server summary as primary, fall back to client-computed stats
  // We use current month for stats if summary is for current month
  const isCurrentMonth =
    selectedDate.getUTCMonth() === new Date().getUTCMonth() &&
    selectedDate.getUTCFullYear() === new Date().getUTCFullYear();

  const kpiIncome = summary?.totalIncome ?? (isCurrentMonth ? stats.income : 0);
  const kpiExpense = summary?.totalExpense ?? (isCurrentMonth ? stats.expenses : 0);
  const kpiBalance = summary?.netSavings ?? (isCurrentMonth ? stats.balance : 0);
  const kpiSavings = Math.max(0, kpiBalance);
  const kpiIncomeTrend = summary?.incomeTrend ?? (isCurrentMonth ? stats.incomeTrend : 0);
  const kpiExpenseTrend = summary?.expenseTrend ?? (isCurrentMonth ? stats.expenseTrend : 0);
  const kpiBalanceTrend = summary?.balanceTrend ?? (isCurrentMonth ? stats.balanceTrend : 0);
  const rawSavingsRate =
    summary?.savingsRate ?? (kpiIncome > 0 ? Math.round(((kpiIncome - kpiExpense) / kpiIncome) * 100) : null);
  const kpiSavingsRate =
    rawSavingsRate != null ? Math.min(100, Math.max(-100, Math.round(rawSavingsRate))) : null;

  const handlePrevMonth = () => setSelectedDate((prev) => subMonths(prev, 1));
  const handleNextMonth = () =>
    setSelectedDate((prev) => {
      const next = new Date(prev);
      next.setMonth(next.getMonth() + 1);
      return next;
    });
  const handleCurrentMonth = () => setSelectedDate(new Date());

  const handleClearMonth = useCallback(async () => {
    setIsClearing(true);
    const mName = format(selectedDate, 'MMMM yyyy');
    try {
      await transactionsApi.clear({
        year: selectedDate.getUTCFullYear(),
        month: selectedDate.getUTCMonth() + 1,
      });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
      toast.success(`Cleared all transactions for ${mName}`);
      setShowClearConfirm(false);
    } catch (err) {
      toast.error(err.userMessage || 'Failed to clear transactions');
    } finally {
      setIsClearing(false);
    }
  }, [selectedDate, queryClient]);

  const monthName = format(selectedDate, 'MMMM yyyy');

  return (
    <div className="dashboard">
      {/* ── Dashboard Header with Month Selector ── */}
      <div className="dash-header">
        <div />

        <div className="month-selector">
          <button onClick={handlePrevMonth} className="month-nav-btn">
            <TrendingDown size={18} style={{ transform: 'rotate(90deg)' }} />
          </button>
          <span className="month-selector-label">{monthName}</span>
          <button onClick={handleNextMonth} className="month-nav-btn" disabled={isCurrentMonth}>
            <TrendingUp size={18} style={{ transform: 'rotate(-90deg)', opacity: isCurrentMonth ? 0.3 : 1 }} />
          </button>
          {!isCurrentMonth && (
            <button onClick={handleCurrentMonth} className="month-current-btn">
              Current
            </button>
          )}
        </div>
      </div>

      {/* ── KPI Cards ── */}
      <div className="dashboard-stats">
        <StatCard
          title="Income"
          rawValue={kpiIncome}
          formatter={(v) => fmt(v, currency)}
          trend={kpiIncomeTrend}
          trendLabel="vs prev month"
          icon={TrendingUp}
          color="green"
          loading={summaryLoading || txLoading}
          index={0}
        />
        <StatCard
          title="Expenses"
          rawValue={kpiExpense}
          formatter={(v) => fmt(v, currency)}
          trend={kpiExpenseTrend}
          trendLabel="vs prev month"
          icon={TrendingDown}
          color="red"
          loading={summaryLoading || txLoading}
          index={1}
        />
        <StatCard
          title="Balance"
          rawValue={kpiBalance}
          formatter={(v) => fmt(v, currency)}
          trend={kpiBalanceTrend}
          trendLabel="vs prev month"
          icon={Wallet}
          color="accent"
          loading={summaryLoading || txLoading}
          index={2}
        />
        <StatCard
          title="Savings"
          rawValue={kpiSavings}
          formatter={(v) => fmt(v, currency)}
          trend={
            kpiSavingsRate != null
              ? kpiSavingsRate >= 20
                ? Math.abs(kpiSavingsRate - 20)
                : -(20 - kpiSavingsRate)
              : (kpiBalanceTrend ?? 0)
          }
          trendLabel={kpiSavingsRate != null ? `${kpiSavingsRate}% savings rate` : 'on track'}
          icon={Vault}
          color="blue"
          loading={summaryLoading || txLoading}
          index={3}
          subvalue={
            totalGoalTarget > 0
              ? `Target: ${fmt(totalGoalTarget, currency)}`
              : user?.financialGoal
                ? `Goal: ${user.financialGoal}`
                : ''
          }
        />
      </div>

      {/* ── Health Score + Weekly Digest ── */}
      <div className="dashboard-insights-row">
        <HealthScoreCard />
        <WeeklyDigestCard
          transactions={transactions}
          summary={summary}
          analytics={analytics}
          currency={currency}
          fmt={fmt}
          selectedDate={selectedDate}
          loading={txLoading}
        />
      </div>

      {/* ── View Toggle (moved above AI Insights so it's always visible) ── */}
      <div className="segmented-pill-group" style={{ marginBottom: 24 }}>
        <button
          onClick={() => setView('overview')}
          className={`segmented-pill-item ${view === 'overview' ? 'active' : ''}`}
        >
          Overview
        </button>
        <button
          onClick={() => setView('analytics')}
          className={`segmented-pill-item ${view === 'analytics' ? 'active' : ''}`}
        >
          Deep Analytics
        </button>
      </div>

      {/* ── AI Copilot Insights ── */}
      <AIInsightsCard transactions={transactions} currency={currency} fmt={fmt} />

      {/* ── Main grid ── */}
      {/* Main Grid */}
      <motion.div
        className="dashboard-grid"
        initial="hidden"
        animate="show"
        variants={{
          hidden: { opacity: 0 },
          show: { opacity: 1, transition: { staggerChildren: 0.1 } },
        }}
      >
        {isDataLoading ? (
          <>
            {[0, 1, 2, 3].map((i) => (
              <motion.div
                key={i}
                className="dash-card"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: i * 0.08 }}
              >
                <div className="dash-card-header">
                  <div>
                    <div className="shimmer" style={{ width: 120, height: 14, borderRadius: 6 }} />
                    <div className="shimmer" style={{ width: 180, height: 10, borderRadius: 6, marginTop: 8 }} />
                  </div>
                </div>
                <div className="shimmer" style={{ width: '100%', height: 160, borderRadius: 12, marginTop: 16 }} />
              </motion.div>
            ))}
          </>
        ) : (
          <>
            {view === 'analytics' && (
              <>
                {/* Spending Trend */}
                <motion.div
                  layout
                  className="dash-card chart-card"
                  variants={{
                    hidden: { opacity: 0, y: 20 },
                    show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut' } },
                  }}
                >
                  <div className="dash-card-header">
                    <div>
                      <h3 className="dash-card-title">Spending Trend</h3>
                      <p className="dash-card-sub">6-month income vs expenses</p>
                    </div>
                  </div>
                  <div style={{ height: 200, marginTop: 8 }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={trendData} margin={{ top: 10, right: 0, left: 0, bottom: 0 }}>
                        <defs>
                          <linearGradient id="colorIncome" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="var(--green)" stopOpacity={0.4} />
                            <stop offset="95%" stopColor="var(--green)" stopOpacity={0} />
                          </linearGradient>
                          <linearGradient id="colorExpense" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="var(--accent)" stopOpacity={0.4} />
                            <stop offset="95%" stopColor="var(--accent)" stopOpacity={0} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(255,255,255,0.05)" />
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
                            color: 'var(--text-primary)',
                            fontSize: 12,
                          }}
                          cursor={{ stroke: 'rgba(255,255,255,0.1)', strokeWidth: 2 }}
                          formatter={(v) => [fmt(v, currency)]}
                        />
                        <Area
                          type="monotone"
                          dataKey="income"
                          stroke="var(--green)"
                          strokeWidth={3}
                          fillOpacity={1}
                          fill="url(#colorIncome)"
                          activeDot={{
                            r: 5,
                            strokeWidth: 2,
                            stroke: 'var(--bg-surface)',
                            fill: 'var(--green)',
                          }}
                        />
                        <Area
                          type="monotone"
                          dataKey="expense"
                          stroke="var(--accent)"
                          strokeWidth={3}
                          fillOpacity={1}
                          fill="url(#colorExpense)"
                          activeDot={{
                            r: 5,
                            strokeWidth: 2,
                            stroke: 'var(--bg-surface)',
                            fill: 'var(--accent)',
                          }}
                        />
                        <Brush
                          dataKey="name"
                          height={20}
                          stroke="rgba(255,255,255,0.1)"
                          fill="var(--bg-surface)"
                          travellerWidth={10}
                        />
                      </AreaChart>
                    </ResponsiveContainer>
                  </div>
                  <div className="chart-legend">
                    <span>
                      <i style={{ background: 'var(--green)' }} /> Income
                    </span>
                    <span>
                      <i style={{ background: 'var(--accent)' }} /> Expenses
                    </span>
                  </div>
                </motion.div>

                {/* Category Pie */}
                <motion.div
                  layout
                  className="dash-card chart-card"
                  variants={{
                    hidden: { opacity: 0, y: 20 },
                    show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut' } },
                  }}
                >
                  <div className="dash-card-header">
                    <div>
                      <h3 className="dash-card-title">By Category</h3>
                      <p className="dash-card-sub">Interactive spending breakdown</p>
                    </div>
                  </div>
                  {categoryData.length > 0 ? (
                    <>
                      <div style={{ position: 'relative', height: 210, marginTop: 8 }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie
                              activeIndex={activeCategoryIndex !== null ? activeCategoryIndex : undefined}
                              activeShape={renderActiveShape}
                              data={categoryData}
                              cx="50%"
                              cy="50%"
                              innerRadius={54}
                              outerRadius={78}
                              paddingAngle={4}
                              dataKey="value"
                              onMouseEnter={(_, index) => setActiveCategoryIndex(index)}
                              onMouseLeave={() => setActiveCategoryIndex(null)}
                            >
                              {categoryData.map((_, i) => (
                                <Cell
                                  key={i}
                                  fill={CATEGORY_COLORS[i % CATEGORY_COLORS.length]}
                                  style={{
                                    cursor: 'pointer',
                                    transition: 'opacity 0.2s ease',
                                    opacity: activeCategoryIndex === null || activeCategoryIndex === i ? 1 : 0.45,
                                  }}
                                />
                              ))}
                            </Pie>
                            <Tooltip
                              contentStyle={{
                                background: 'var(--bg-elevated)',
                                border: '1px solid var(--border-strong)',
                                borderRadius: 10,
                                fontSize: 12,
                                color: 'var(--text-primary)',
                                boxShadow: 'var(--shadow-md)',
                              }}
                              formatter={(v, name) => [
                                `${fmt(v, currency)} (${totalCategorySpend > 0 ? Math.round((v / totalCategorySpend) * 100) : 0}%)`,
                                name,
                              ]}
                            />
                          </PieChart>
                        </ResponsiveContainer>

                        {/* Donut Center Display */}
                        <div
                          style={{
                            position: 'absolute',
                            top: '50%',
                            left: '50%',
                            transform: 'translate(-50%, -50%)',
                            textAlign: 'center',
                            pointerEvents: 'none',
                            width: 100,
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          {activeCategoryIndex !== null && categoryData[activeCategoryIndex] ? (
                            <>
                              <span
                                style={{
                                  fontSize: 11,
                                  fontWeight: 600,
                                  color: 'var(--text-muted)',
                                  textTransform: 'uppercase',
                                  letterSpacing: '0.04em',
                                  maxWidth: 90,
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  whiteSpace: 'nowrap',
                                }}
                              >
                                {categoryData[activeCategoryIndex].name}
                              </span>
                              <span
                                style={{
                                  fontSize: 14,
                                  fontWeight: 800,
                                  color: 'var(--text-primary)',
                                  fontFamily: 'var(--font-display)',
                                  marginTop: 2,
                                }}
                              >
                                {fmt(categoryData[activeCategoryIndex].value, currency)}
                              </span>
                              <span
                                style={{
                                  fontSize: 10,
                                  fontWeight: 700,
                                  color: CATEGORY_COLORS[activeCategoryIndex % CATEGORY_COLORS.length],
                                  marginTop: 2,
                                }}
                              >
                                {totalCategorySpend > 0
                                  ? `${Math.round((categoryData[activeCategoryIndex].value / totalCategorySpend) * 100)}%`
                                  : ''}
                              </span>
                            </>
                          ) : (
                            <>
                              <span
                                style={{
                                  fontSize: 11,
                                  fontWeight: 600,
                                  color: 'var(--text-muted)',
                                  textTransform: 'uppercase',
                                  letterSpacing: '0.04em',
                                }}
                              >
                                Total Spend
                              </span>
                              <span
                                style={{
                                  fontSize: 14,
                                  fontWeight: 800,
                                  color: 'var(--text-primary)',
                                  fontFamily: 'var(--font-display)',
                                  marginTop: 2,
                                }}
                              >
                                {fmt(totalCategorySpend, currency)}
                              </span>
                              <span
                                style={{
                                  fontSize: 10,
                                  fontWeight: 600,
                                  color: 'var(--text-secondary)',
                                  marginTop: 2,
                                }}
                              >
                                {categoryData.length} Categories
                              </span>
                            </>
                          )}
                        </div>
                      </div>

                      <div className="pie-legend">
                        {categoryData.slice(0, 5).map((d, i) => {
                          const meta = getCategoryMeta(d.name);
                          const Icon = getCategoryIcon(d.name);
                          const color = CATEGORY_COLORS[i % CATEGORY_COLORS.length];
                          const isHovered = activeCategoryIndex === i;
                          const pct = totalCategorySpend > 0 ? Math.round((d.value / totalCategorySpend) * 100) : 0;
                          return (
                            <div
                              key={d.name}
                              className={`pie-legend-item ${isHovered ? 'active' : ''}`}
                              onMouseEnter={() => setActiveCategoryIndex(i)}
                              onMouseLeave={() => setActiveCategoryIndex(null)}
                            >
                              <div
                                className="pie-icon-chip"
                                style={{
                                  background: meta.bg,
                                  color: color,
                                  border: `1px solid ${isHovered ? color : meta.border}`,
                                  transform: isHovered ? 'scale(1.15)' : 'none',
                                  transition: 'all 0.2s ease',
                                }}
                              >
                                <Icon size={12} strokeWidth={2.2} />
                              </div>
                              <div style={{ flex: 1, minWidth: 0 }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 3 }}>
                                  <span
                                    className="pie-label truncate"
                                    style={{
                                      color: isHovered ? 'var(--text-primary)' : 'var(--text-secondary)',
                                      fontWeight: isHovered ? 700 : 500,
                                    }}
                                  >
                                    {d.name}
                                  </span>
                                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                    <span className="pie-pct-badge">{pct}%</span>
                                    <span className="pie-value">{fmt(d.value, currency)}</span>
                                  </div>
                                </div>
                                <div className="pie-track">
                                  <div
                                    className="pie-track-fill"
                                    style={{
                                      width: `${pct}%`,
                                      background: color,
                                      opacity: isHovered ? 1 : 0.75,
                                    }}
                                  />
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </>
                  ) : (
                    <div className="empty-mini">No expense data yet</div>
                  )}
                </motion.div>
              </>
            )}

            {/* Budget Health — FIX: now uses /api/budgets + spendByCategory from analytics */}
            <motion.div
              layout
              className="dash-card"
              variants={{
                hidden: { opacity: 0, y: 20 },
                show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut' } },
              }}
            >
              <div className="dash-card-header">
                <div>
                  <h3 className="dash-card-title">Budget Health</h3>
                  <p className="dash-card-sub">This month's limits</p>
                </div>
                <Link to="/app/budgets" className="dash-see-all">
                  Manage <ArrowRight size={12} />
                </Link>
              </div>
              {budgets.length > 0 ? (
                <div className="budget-list">
                  {budgets.slice(0, 5).map((b) => {
                    const spent = spendByCategory[b.category] ?? b.spent ?? 0;
                    const pct = b.limit > 0 ? Math.min(100, (spent / b.limit) * 100) : 0;
                    const over = pct >= 100;
                    const warn = pct >= 80;
                    const meta = getCategoryMeta(b.category);
                    const Icon = getCategoryIcon(b.category);
                    const color = b.color || meta.color;
                    return (
                      <div key={b._id} className="budget-row interactive-card">
                        <div className="budget-row-info">
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <div
                              className="budget-cat-icon"
                              style={{ background: meta.bg, color: color, border: `1px solid ${meta.border}` }}
                            >
                              <Icon size={12} strokeWidth={2.2} />
                            </div>
                            <span className="budget-cat">{b.category}</span>
                          </div>
                          <span className="budget-amounts">
                            <span
                              style={{ color: over ? 'var(--red)' : warn ? 'var(--amber)' : 'var(--text-secondary)' }}
                            >
                              {fmt(spent, currency)}
                            </span>
                            {' / '}
                            <span style={{ color: 'var(--text-muted)' }}>{fmt(b.limit, currency)}</span>
                          </span>
                        </div>
                        <div className="progress-bar">
                          <motion.div
                            className="progress-fill"
                            style={{
                              background: over ? 'var(--red)' : warn ? 'var(--amber)' : color,
                            }}
                            initial={{ width: 0 }}
                            animate={{ width: `${pct}%` }}
                            transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="empty-mini">
                  <Link to="/app/budgets" className="dash-see-all">
                    Set up budgets →
                  </Link>
                </div>
              )}
            </motion.div>

            {/* Activity Alerts */}
            <motion.div
              layout
              className="dash-card"
              variants={{
                hidden: { opacity: 0, y: 20 },
                show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut' } },
              }}
            >
              <div className="dash-card-header">
                <div>
                  <h3 className="dash-card-title">Recent Alerts</h3>
                  <p className="dash-card-sub">Important account updates</p>
                </div>
                <Link to="/app/ai" className="dash-see-all">
                  BudGi Copilot <ArrowRight size={12} />
                </Link>
              </div>
              {alerts.length > 0 ? (
                <div className="alerts-list">
                  {alerts.slice(0, 4).map((a, i) => (
                    <motion.div
                      key={a._id ?? i}
                      className={`alert-item alert-${a.severity ?? 'warning'}`}
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: 0.6 + i * 0.05 }}
                    >
                      {a.severity === 'critical' ? <AlertCircle size={14} /> : <AlertTriangle size={14} />}
                      <span>{a.message}</span>
                    </motion.div>
                  ))}
                </div>
              ) : (
                <div className="empty-mini">
                  <span style={{ color: 'var(--green)', fontSize: 13 }}>✓ Your finances look stable today</span>
                </div>
              )}
            </motion.div>

            {view === 'overview' && (
              <>
                <SubscriptionsWidget subscriptions={upcomingSubs} currency={currency} fmt={fmt} />
                {/* Goals Progress Widget — strictly in overview, removed completely from Deep Analysis */}
                <GoalsWidget currency={currency} fmt={fmt} />
              </>
            )}

            {/* Recent Transactions */}
            <motion.div
              layout
              className="dash-card full-width"
              variants={{
                hidden: { opacity: 0, y: 20 },
                show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut' } },
              }}
            >
              <div className="dash-card-header">
                <div>
                  <h3 className="dash-card-title">Recent Transactions</h3>
                  <p className="dash-card-sub">Latest activity</p>
                </div>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  {!txLoading && transactions.length > 0 && (
                    <button
                      onClick={() => setShowClearConfirm(true)}
                      className="dash-clear-btn"
                      title="Clear all transactions for this month"
                    >
                      <Trash2 size={14} /> Clear
                    </button>
                  )}
                  <Link to="/app/transactions" className="dash-see-all">
                    View all <ArrowRight size={12} />
                  </Link>
                  <Link to="/app/transactions" className="dash-add-btn">
                    <Plus size={14} /> Add
                  </Link>
                </div>
              </div>
              {recentTx.length > 0 ? (
                <div className="txn-table">
                  {recentTx.map((tx, i) => {
                    const catMeta = getCategoryMeta(tx.category);
                    return (
                      <motion.div
                        key={tx._id}
                        className="txn-row interactive-card"
                        initial={{ opacity: 0, x: -8 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.65 + i * 0.04 }}
                        whileHover={{ x: 2 }}
                      >
                        <CategoryAvatar category={tx.category} size={36} />
                        <div className="txn-info">
                          <span className="txn-name">{tx.merchant || tx.description || 'Transaction'}</span>
                          <span className="txn-cat">
                            <span style={{ color: catMeta.color, fontWeight: 600 }}>{tx.category}</span> ·{' '}
                            {format(new Date(tx.date), 'dd MMM')}
                          </span>
                        </div>
                        <span className={`txn-amount ${tx.type === 'income' ? 'txn-income' : 'txn-expense'}`}>
                          {tx.type === 'income' ? '+' : '-'}
                          {fmt(tx.amount, currency)}
                        </span>
                      </motion.div>
                    );
                  })}
                </div>
              ) : (
                <EmptyState
                  icon={Receipt}
                  title="No transactions yet"
                  sub="Add your first transaction and BudGi will start tracking your financial story."
                  cta="Add transaction"
                  to="/app/transactions"
                />
              )}
            </motion.div>
          </>
        )}
      </motion.div>

      <ConfirmModal
        open={showClearConfirm}
        onClose={() => !isClearing && setShowClearConfirm(false)}
        onConfirm={handleClearMonth}
        title={`Clear ${monthName} Data?`}
        message={`This will permanently delete all transactions for ${monthName}. You can then upload a fresh CSV or add transactions manually.`}
        confirmText="Yes, Clear Data"
        cancelText="Cancel"
        loading={isClearing}
        variant="danger"
      />
    </div>
  );
}

/* ─── Financial Health Score Card ─────────────────────────── */
function HealthScoreCard() {
  const { data: healthData } = useHealthScore();
  const score = healthData?.score ?? 50;

  const grade =
    score >= 80
      ? { label: 'Excellent', color: 'var(--green)', bg: 'rgba(48,214,140,0.1)' }
      : score >= 60
        ? { label: 'Good', color: 'var(--blue)', bg: 'rgba(77,156,245,0.1)' }
        : score >= 40
          ? { label: 'Fair', color: 'var(--amber)', bg: 'rgba(245,166,35,0.1)' }
          : { label: 'Needs work', color: 'var(--red)', bg: 'rgba(245,97,106,0.1)' };

  const circumference = 2 * Math.PI * 36;
  const dash = ((100 - score) / 100) * circumference;

  return (
    <motion.div
      className="dash-card dash-health-card"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.25 }}
    >
      <div className="dash-card-header">
        <div>
          <h3 className="dash-card-title">Financial Standing</h3>
          <p className="dash-card-sub">Overview of your status</p>
        </div>
        <ShieldCheck size={16} style={{ color: grade.color, marginTop: 2 }} />
      </div>
      <div className="health-score-body">
        <div className="health-score-ring-wrap" style={{ position: 'relative', width: 90, height: 90, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <svg width="90" height="90" viewBox="0 0 90 90" style={{ transform: 'rotate(-90deg)' }}>
            <circle
              cx="45"
              cy="45"
              r="36"
              fill="none"
              stroke="var(--border-strong, rgba(255,255,255,0.08))"
              strokeWidth="7"
            />
            <circle
              cx="45"
              cy="45"
              r="36"
              fill="none"
              stroke={grade.color}
              strokeWidth="7"
              strokeDasharray={circumference}
              strokeDashoffset={dash}
              strokeLinecap="round"
              style={{ transition: 'stroke-dashoffset 0.8s cubic-bezier(0.16, 1, 0.3, 1)' }}
            />
          </svg>
          <div
            className="health-score-center"
            style={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              transform: 'translate(-50%, -50%)',
              pointerEvents: 'none',
              textAlign: 'center',
            }}
          >
            <motion.span
              className="health-score-num"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.2 }}
              style={{ fontSize: '1.45rem', fontWeight: 800, color: 'var(--text-primary)' }}
            >
              {score}
            </motion.span>
          </div>
        </div>
        <div className="health-score-info">
          <div className="health-grade-badge" style={{ background: grade.bg, color: grade.color }}>
            {grade.label}
          </div>
          <div className="health-score-items">
            <div className="health-score-item">
              <span className="hsi-label">Savings rate</span>
              <span className="hsi-val" style={{ color: (healthData?.factors?.savingsRate ?? 0) >= 0 ? 'var(--green)' : 'var(--red)' }}>
                {healthData?.factors?.savingsRate != null
                  ? `${Math.min(100, Math.max(-100, Math.round(healthData.factors.savingsRate)))}%`
                  : '0%'}
              </span>
            </div>
            <div className="health-score-item">
              <span className="hsi-label">Budgets on track</span>
              <span className="hsi-val" style={{ color: 'var(--green)' }}>
                {healthData?.factors?.budgetsOnTrack ?? '—'}
              </span>
            </div>
          </div>
          <Link to="/app/analytics" className="dash-see-all" style={{ marginTop: 4 }}>
            Full analysis <ArrowRight size={12} />
          </Link>
        </div>
      </div>
    </motion.div>
  );
}

/* ─── Weekly Digest Card ──────────────────────────────────── */
function WeeklyDigestCard({ transactions, analytics, currency, fmt, selectedDate, loading }) {
  const digest = useMemo(() => {
    const referenceDate = selectedDate || new Date();
    const now = referenceDate;
    const thisWeekStart = new Date(now);
    thisWeekStart.setDate(now.getDate() - now.getDay());
    thisWeekStart.setHours(0, 0, 0, 0);
    const lastWeekStart = new Date(thisWeekStart);
    lastWeekStart.setDate(thisWeekStart.getDate() - 7);
    const lastWeekEnd = new Date(thisWeekStart);

    // Compute actual weekly totals from transaction data
    const isExp = (t) => (t.type === 'expense' || t.type === 'debit') && t.category !== 'Transfer';
    const thisWeekTxs = transactions.filter((t) => isExp(t) && new Date(t.date) >= thisWeekStart);
    const lastWeekTxs = transactions.filter(
      (t) => isExp(t) && new Date(t.date) >= lastWeekStart && new Date(t.date) < lastWeekEnd,
    );
    const thisWeek = thisWeekTxs.reduce((s, t) => s + t.amount, 0);
    const lastWeek = lastWeekTxs.reduce((s, t) => s + t.amount, 0);
    const diff = lastWeek > 0 ? ((thisWeek - lastWeek) / lastWeek) * 100 : 0;

    // Day-by-day bar chart for the current week
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const dayBars = days.map((d, idx) => {
      const dayStart = new Date(thisWeekStart);
      dayStart.setDate(thisWeekStart.getDate() + idx);
      const dayEnd = new Date(dayStart);
      dayEnd.setDate(dayStart.getDate() + 1);
      const amt = transactions
        .filter((t) => isExp(t) && new Date(t.date) >= dayStart && new Date(t.date) < dayEnd)
        .reduce((s, t) => s + t.amount, 0);
      return { d, amt };
    });
    const maxAmt = Math.max(...dayBars.map((b) => b.amt), 1);

    return {
      thisWeek,
      lastWeek,
      diff,
      topCat: analytics?.topCategory ? [analytics.topCategory.category, analytics.topCategory.total] : null,
      dayBars,
      maxAmt,
    };
  }, [transactions, analytics, selectedDate]);

  const isDown = digest.diff < 0;

  return (
    <motion.div
      className="dash-card dash-digest-card"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.32 }}
    >
      {loading ? (
        <div className="shimmer" style={{ width: '100%', height: '100%', borderRadius: 12 }} />
      ) : (
        <>
          <div className="dash-card-header">
            <div>
              <h3 className="dash-card-title">Weekly Summary</h3>
              <p className="dash-card-sub">Activity this week</p>
            </div>
            <Calendar size={16} style={{ color: 'var(--text-muted)', marginTop: 2 }} />
          </div>
          <div className="digest-body">
            <div className="digest-top">
              <div>
                <div className="digest-amount">{fmt(digest.thisWeek, currency)}</div>
                <div className="digest-label">spent this week</div>
              </div>
              <div />
              {digest.lastWeek > 0 && (
                <div className={`digest-delta ${isDown ? 'digest-delta-good' : 'digest-delta-bad'}`}>
                  {isDown ? '▼' : '▲'} {Math.abs(Math.round(digest.diff))}%<span>vs last week</span>
                </div>
              )}
            </div>

            {/* Day bars */}
            <div className="digest-bars">
              {digest.dayBars.map((b, i) => (
                <div key={b.d} className="digest-bar-col">
                  <motion.div
                    className="digest-bar-fill"
                    style={{
                      height: `${digest.maxAmt > 0 ? Math.max(4, (b.amt / digest.maxAmt) * 52) : 4}px`,
                      background: i === new Date().getDay() ? 'var(--accent)' : 'var(--bg-overlay)',
                      border: i === new Date().getDay() ? 'none' : '1px solid var(--border-strong)',
                    }}
                    initial={{ scaleY: 0 }}
                    animate={{ scaleY: 1 }}
                    transition={{ duration: 0.5, delay: 0.5 + i * 0.05, ease: [0.16, 1, 0.3, 1] }}
                  />
                  <span
                    className="digest-bar-label"
                    style={{ color: i === new Date().getDay() ? 'var(--accent-bright)' : 'var(--text-muted)' }}
                  >
                    {b.d}
                  </span>
                </div>
              ))}
            </div>

            {digest.topCat && (
              <div className="digest-top-cat">
                <span className="digest-top-cat-label">Highest spend</span>
                <span className="digest-top-cat-val">
                  {digest.topCat[0]} · {fmt(digest.topCat[1], currency)}
                </span>
              </div>
            )}
          </div>
        </>
      )}
    </motion.div>
  );
}

/* ─── Goals Widget ────────────────────────────────────────── */
function GoalsWidget({ currency, fmt }) {
  const { data } = useQuery({
    queryKey: ['goals'],
    queryFn: () => goalsApi.list(),
    staleTime: 60_000,
  });
  const goals = (data?.data?.goals || []).filter((g) => !g.isCompleted).slice(0, 3);

  return (
    <motion.div
      layout
      className="dash-card"
      variants={{
        hidden: { opacity: 0, y: 20 },
        show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut' } },
      }}
    >
      <div className="dash-card-header">
        <div>
          <h3 className="dash-card-title">Savings Goals</h3>
          <p className="dash-card-sub">Track your progress</p>
        </div>
        <Link to="/app/goals" className="dash-see-all">
          View all <ArrowRight size={12} />
        </Link>
      </div>
      {goals.length > 0 ? (
        <div className="budget-list">
          {goals.map((g) => {
            const pct = g.targetAmount > 0 ? Math.min(100, Math.round((g.currentAmount / g.targetAmount) * 100)) : 100;
            return (
              <div key={g._id} className="budget-row">
                <div className="budget-row-info">
                  <span className="budget-cat">
                    {g.icon && g.icon.length <= 2 ? `${g.icon} ` : ''}{g.title}
                  </span>
                  <span className="budget-amounts">
                    <span style={{ color: 'var(--accent-bright)' }}>{fmt(g.currentAmount, currency)}</span>
                    {' / '}
                    <span style={{ color: 'var(--text-muted)' }}>{fmt(g.targetAmount, currency)}</span>
                  </span>
                </div>
                <div className="progress-bar">
                  <motion.div
                    className="progress-fill"
                    style={{ background: g.color || 'var(--accent)' }}
                    initial={{ width: 0 }}
                    animate={{ width: `${pct}%` }}
                    transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="empty-mini">
          <Link to="/app/goals" className="dash-see-all">
            Set your first goal →
          </Link>
        </div>
      )}
    </motion.div>
  );
}

/* ─── Empty state with mascot ─────────────────────────────── */
function EmptyState({ icon: Icon, title, sub, cta, to }) {
  return (
    <div className="empty-state-fancy">
      <div className="empty-state-mascot">
        <img src={budgiLogo} alt="BudGi" />
      </div>
      <div className="empty-state-icon-ring">
        {Icon && <Icon size={32} strokeWidth={1.5} className="empty-state-icon" />}
      </div>
      <div className="empty-state-title">{title}</div>
      <div className="empty-state-sub">{sub}</div>
      {cta && to && (
        <Link to={to} className="empty-state-cta">
          {cta} <ArrowRight size={12} />
        </Link>
      )}
    </div>
  );
}

/* ─── Subscriptions Widget ────────────────────────────────────────── */
function SubscriptionsWidget({ subscriptions, currency, fmt }) {
  return (
    <motion.div
      layout
      className="dash-card"
      variants={{
        hidden: { opacity: 0, y: 20 },
        show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut' } },
      }}
    >
      <div className="dash-card-header">
        <div>
          <h3 className="dash-card-title">Subscriptions</h3>
          <p className="dash-card-sub">Upcoming recurring bills</p>
        </div>
        <Clock size={16} style={{ color: 'var(--text-muted)' }} />
      </div>
      {subscriptions.length > 0 ? (
        <div className="budget-list">
          {subscriptions.map((s, i) => (
            <div
              key={s._id || i}
              className="budget-row"
              style={{ padding: '12px', background: 'var(--bg-overlay)', borderRadius: '8px' }}
            >
              <div className="budget-row-info" style={{ marginBottom: 0 }}>
                <span className="budget-cat" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <RefreshCw size={16} style={{ color: 'var(--accent)', flexShrink: 0 }} />
                  <div>
                    <div style={{ color: 'var(--text-primary)' }}>{s.merchant || s.description || s.category}</div>
                    <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>{s.recurringFrequency}</div>
                  </div>
                </span>
                <span style={{ color: 'var(--text-primary)', fontWeight: 600 }}>{fmt(s.amount, currency)}</span>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="empty-mini">No active subscriptions detected</div>
      )}
    </motion.div>
  );
}

/* ─── AI Copilot Insights Card ───────────────────────────────────── */
function AIInsightsCard({ transactions = [], currency = 'INR', fmt }) {
  const { data: insights, isLoading } = useInsights();
  const { data: prediction } = useSpendingPrediction();

  const recentAnomaly = useMemo(() => {
    return transactions.find((t) => t.isAnomaly);
  }, [transactions]);

  if (isLoading) {
    return (
      <motion.div
        className="dash-card"
        style={{ marginBottom: 24 }}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        <div className="dash-card-header">
          <div />
        </div>
        <div className="shimmer" style={{ width: '100%', height: 80, borderRadius: 10, marginTop: 12 }} />
      </motion.div>
    );
  }

  const hasInsight = insights?.insight || insights?.warning || insights?.suggestion || recentAnomaly;
  const predTotal = prediction?.overall?.predictedTotal ?? prediction?.predictedTotal ?? 0;
  const predTrend = prediction?.overall?.trendDirection ?? prediction?.trendDirection ?? 'stable';
  const predConf = prediction?.overall?.confidence ?? prediction?.confidence ?? 0;

  if (!hasInsight && !predTotal) return null;

  return (
    <motion.div
      className="dash-card"
      style={{
        marginBottom: 24,
        background: 'linear-gradient(135deg, rgba(124,106,245,0.06), rgba(77,156,245,0.04))',
        border: '1px solid rgba(124,106,245,0.15)',
      }}
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.1 }}
    >
      <div className="dash-card-header">
        <div>
          <h3 className="dash-card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Compass size={16} style={{ color: 'var(--accent-bright)' }} />
            BudGi Copilot Insights
          </h3>
          <p className="dash-card-sub">Algorithmic analysis of your finances</p>
        </div>
        <Link
          to="/app/ai"
          className="copilot-action-btn"
        >
          <span>Ask Copilot</span>
          <ArrowRight size={13} className="copilot-btn-arrow" />
        </Link>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 12 }}>
        {recentAnomaly && (
          <motion.div
            style={{
              display: 'flex',
              gap: 10,
              padding: '10px 12px',
              background: 'var(--red-dim)',
              border: '1px solid var(--red)',
              borderRadius: 10,
              alignItems: 'flex-start',
            }}
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.1 }}
          >
            <AlertCircle size={18} style={{ color: 'var(--red)', flexShrink: 0, marginTop: 2 }} />
            <span style={{ fontSize: 13, color: 'var(--red)', lineHeight: 1.5 }}>
              <strong>Unusual Spending Spike:</strong>{' '}
              {recentAnomaly.merchant || recentAnomaly.description || 'Transaction'} (
              {fmt ? fmt(recentAnomaly.amount, currency) : `₹${recentAnomaly.amount}`}) deviates significantly from your typical {recentAnomaly.category} spend.
            </span>
          </motion.div>
        )}
        {insights?.insight && (
          <motion.div
            style={{
              display: 'flex',
              gap: 10,
              padding: '10px 12px',
              background: 'var(--accent-dim)',
              borderRadius: 10,
              alignItems: 'flex-start',
            }}
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.15 }}
          >
            <Compass size={16} style={{ color: 'var(--accent)', flexShrink: 0, marginTop: 2 }} />
            <span style={{ fontSize: 13, color: 'var(--text-primary)', lineHeight: 1.5 }}>{insights.insight}</span>
          </motion.div>
        )}
        {insights?.warning && (
          <motion.div
            style={{
              display: 'flex',
              gap: 10,
              padding: '10px 12px',
              background: 'var(--amber-dim)',
              borderRadius: 10,
              alignItems: 'flex-start',
            }}
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.25 }}
          >
            <AlertTriangle size={16} style={{ color: 'var(--amber)', flexShrink: 0, marginTop: 2 }} />
            <span style={{ fontSize: 13, color: 'var(--amber)', lineHeight: 1.5 }}>{insights.warning}</span>
          </motion.div>
        )}
        {insights?.suggestion && (
          <motion.div
            style={{
              display: 'flex',
              gap: 10,
              padding: '10px 12px',
              background: 'var(--green-dim)',
              borderRadius: 10,
              alignItems: 'flex-start',
            }}
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.35 }}
          >
            <CheckCircle2 size={16} style={{ color: 'var(--green)', flexShrink: 0, marginTop: 2 }} />
            <span style={{ fontSize: 13, color: 'var(--green)', lineHeight: 1.5 }}>{insights.suggestion}</span>
          </motion.div>
        )}
        {predTotal > 0 && (
          <motion.div
            style={{
              display: 'flex',
              gap: 10,
              padding: '10px 12px',
              background: 'var(--blue-dim)',
              borderRadius: 10,
              alignItems: 'flex-start',
            }}
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.45 }}
          >
            <BarChart2 size={16} style={{ color: 'var(--blue)', flexShrink: 0, marginTop: 2 }} />
            <span style={{ fontSize: 13, color: 'var(--text-primary)', lineHeight: 1.5 }}>
              <strong>Next Month Forecast:</strong> {fmt ? fmt(predTotal, currency) : `₹${Math.round(predTotal).toLocaleString('en-IN')}`}
              {predTrend === 'increasing'
                ? ' (trending up ↑)'
                : predTrend === 'decreasing'
                  ? ' (trending down ↓)'
                  : ' (stable →)'}
              {predConf > 0 && (
                <span style={{ color: 'var(--text-muted)', fontSize: 11 }}>
                  {' '}
                  · {Math.round(predConf * 100)}% confidence
                </span>
              )}
            </span>
          </motion.div>
        )}
      </div>
    </motion.div>
  );
}
