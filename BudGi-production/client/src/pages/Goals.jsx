import { useState, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Target,
  Plus,
  Trash2,
  TrendingUp,
  Calendar,
  CheckCircle,
  X,
  Coins,
  Home,
  Car,
  Plane,
  Heart,
  GraduationCap,
  Smartphone,
  DollarSign,
  Building,
  HeartPulse,
  Shield,
  Gift,
  Gauge,
  Timer,
  Trophy,
  AlertTriangle,
  ArrowRight,
} from '../lib/icons';
import { goalsApi } from '../lib/api';
import { useAnalytics } from '../hooks/useAI';
import useStore from '../store/useStore';
import toast from 'react-hot-toast';
import Confetti from '../components/ui/Confetti';
import ConfirmModal from '../components/ui/ConfirmModal';
import ConcentricGoalsChart from '../components/charts/ConcentricGoalsChart';
import { fmt } from '../lib/utils';
import './Goals.css';

const ICON_MAP = {
  Target: Target,
  Home: Home,
  Car: Car,
  Plane: Plane,
  Heart: Heart,
  GraduationCap: GraduationCap,
  Smartphone: Smartphone,
  DollarSign: DollarSign,
  Building: Building,
  HeartPulse: HeartPulse,
  Shield: Shield,
  Gift: Gift,
  '🎯': Target,
  '🏠': Home,
  '🚗': Car,
  '✈️': Plane,
  '💍': Heart,
  '🎓': GraduationCap,
  '📱': Smartphone,
  '💰': DollarSign,
  '🏦': Building,
  '🩺': HeartPulse,
  '🛡️': Shield,
  '🎉': Gift,
};

const ICONS = [
  'Target',
  'Home',
  'Car',
  'Plane',
  'Heart',
  'GraduationCap',
  'Smartphone',
  'DollarSign',
  'Building',
  'HeartPulse',
  'Shield',
  'Gift',
];
const COLORS = [
  '#38BDF8',
  '#38A169',
  '#E07A5F',
  '#4A90E2',
  '#60BCD4',
  '#D97706',
  '#2A9D8F',
  '#D95D74',
  '#3A9AB7',
  '#5C768D',
];

export default function Goals() {
  const { user } = useStore();
  const currency = user?.currency || 'INR';
  const qc = useQueryClient();

  const [deleteId, setDeleteId] = useState(null);
  const { data: analytics } = useAnalytics();
  const { data, isLoading } = useQuery({
    queryKey: ['goals'],
    queryFn: () => goalsApi.list(),
  });

  const rawGoals = useMemo(() => data?.data?.goals || [], [data]);
  const monthlySavings = analytics?.savingsRate?.savings || 0;

  const [showCreate, setShowCreate] = useState(false);
  const [addingTo, setAddingTo] = useState(null);
  const [addAmt, setAddAmt] = useState('');

  const [form, setForm] = useState({
    title: '',
    targetAmount: '',
    deadline: '',
    category: 'savings',
    icon: 'Target',
    color: '#38BDF8',
  });
  const [showConfetti, setShowConfetti] = useState(false);

  const createMut = useMutation({
    mutationFn: (data) => goalsApi.create(data),
    onMutate: async (newGoal) => {
      await qc.cancelQueries({ queryKey: ['goals'] });
      const previous = qc.getQueryData(['goals']);
      qc.setQueryData(['goals'], (old) => {
        const tempGoal = {
          ...newGoal,
          _id: `temp-${Date.now()}`,
          currentAmount: 0,
          isCompleted: false,
        };
        if (!old?.data?.goals) return { data: { goals: [tempGoal] } };
        return { ...old, data: { ...old.data, goals: [...old.data.goals, tempGoal] } };
      });
      setShowCreate(false);
      setForm({ title: '', targetAmount: '', deadline: '', category: 'savings', icon: 'Target', color: '#38BDF8' });
      return { previous };
    },
    onError: (err, newGoal, context) => {
      qc.setQueryData(['goals'], context.previous);
      toast.error(err.userMessage || 'Failed to create milestone');
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['goals'] }),
    onSuccess: () => toast.success('Milestone created successfully!'),
  });

  const addMut = useMutation({
    mutationFn: ({ id, amount }) => goalsApi.addSavings(id, amount),
    onMutate: async ({ id, amount }) => {
      await qc.cancelQueries({ queryKey: ['goals'] });
      const previous = qc.getQueryData(['goals']);
      qc.setQueryData(['goals'], (old) => {
        if (!old?.data?.goals) return old;
        const newGoals = old.data.goals.map((g) => {
          if (g._id === id) {
            const newAmount = g.currentAmount + amount;
            return { ...g, currentAmount: newAmount, isCompleted: newAmount >= g.targetAmount };
          }
          return g;
        });
        return { ...old, data: { ...old.data, goals: newGoals } };
      });
      setAddingTo(null);
      setAddAmt('');
      return { previous };
    },
    onError: (err, variables, context) => {
      qc.setQueryData(['goals'], context.previous);
      toast.error(err.userMessage || 'Failed to add progress');
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['goals'] }),
    onSuccess: (res) => {
      const goal = res?.data?.goal;
      if (goal?.isCompleted) {
        setShowConfetti(true);
        toast.success('🎉 Goal completed! Outstanding achievement!');
      } else {
        toast.success('Progress logged successfully!');
      }
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id) => goalsApi.remove(id),
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: ['goals'] });
      const previous = qc.getQueryData(['goals']);
      qc.setQueryData(['goals'], (old) => {
        if (!old?.data?.goals) return old;
        return { ...old, data: { ...old.data, goals: old.data.goals.filter((g) => g._id !== id) } };
      });
      return { previous };
    },
    onError: (err, id, context) => {
      qc.setQueryData(['goals'], context.previous);
      toast.error('Failed to delete milestone');
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['goals'] }),
    onSuccess: () => toast.success('Milestone removed'),
  });

  // POWERFUL LOGIC: Enrich goals with smart analytics
  const enrichedGoals = useMemo(() => {
    const today = new Date();
    return rawGoals.map((goal) => {
      let monthsLeft = null;
      let requiredMonthly = null;
      let status = 'flexible';

      const remaining = Math.max(0, goal.targetAmount - goal.currentAmount);

      if (goal.deadline) {
        const deadlineDate = new Date(goal.deadline);
        monthsLeft = Math.max(0.1, (deadlineDate - today) / (1000 * 60 * 60 * 24 * 30));
        requiredMonthly = remaining / monthsLeft;

        if (remaining === 0) status = 'achieved';
        else if (requiredMonthly > monthlySavings * 0.8) status = 'at_risk';
        else if (requiredMonthly > monthlySavings * 0.4) status = 'needs_attention';
        else status = 'on_track';
      } else if (remaining === 0) {
        status = 'achieved';
      }

      return {
        ...goal,
        remaining,
        monthsLeft,
        requiredMonthly,
        status,
        progressPct: Math.min(100, Math.round((goal.currentAmount / goal.targetAmount) * 100)) || 0,
      };
    });
  }, [rawGoals, monthlySavings]);

  // Sorting: At Risk > Needs Attention > On Track > Flexible
  const activeGoals = enrichedGoals
    .filter((g) => !g.isCompleted)
    .sort((a, b) => {
      const statusWeight = { at_risk: 4, needs_attention: 3, on_track: 2, flexible: 1 };
      if (statusWeight[a.status] !== statusWeight[b.status]) {
        return statusWeight[b.status] - statusWeight[a.status]; // Higher weight first
      }
      return b.progressPct - a.progressPct;
    });

  const completedGoals = enrichedGoals.filter((g) => g.isCompleted);
  const totalSaved = enrichedGoals.reduce((s, g) => s + g.currentAmount, 0);
  const totalTarget = enrichedGoals.reduce((s, g) => s + g.targetAmount, 0);
  const remainingTotal = totalTarget - totalSaved;
  const overallMonths = monthlySavings > 0 ? Math.ceil(remainingTotal / monthlySavings) : null;

  const totalRequiredMonthly = activeGoals.reduce((s, g) => s + (g.requiredMonthly || 0), 0);

  const handleCreate = (e) => {
    e.preventDefault();
    if (!form.title || !form.targetAmount) return toast.error('Title and amount are required');
    createMut.mutate({
      ...form,
      targetAmount: Number(form.targetAmount),
      deadline: form.deadline || null,
    });
  };

  const handleAddSavings = (e) => {
    e.preventDefault();
    if (!addAmt || Number(addAmt) <= 0) return toast.error('Enter a valid amount');
    addMut.mutate({ id: addingTo.id, amount: Number(addAmt) });
  };

  const modalVariants = {
    hidden: { opacity: 0, y: 50, scale: 0.95 },
    visible: { opacity: 1, y: 0, scale: 1, transition: { type: 'spring', stiffness: 300, damping: 25 } },
    exit: { opacity: 0, y: 20, scale: 0.95, transition: { duration: 0.2 } },
  };

  return (
    <div className="goals-page">
      <Confetti active={showConfetti} />
      {/* Header */}
      <div className="goals-header">
        <div className="goals-header-left">
          <h1 className="goals-title">Goals & Milestones</h1>
          <p className="goals-subtitle">Track targets, milestone deadlines, and portfolio savings</p>
        </div>
        <motion.button
          className="goals-create-btn"
          onClick={() => setShowCreate(true)}
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.96 }}
        >
          <Plus size={18} strokeWidth={2.5} />
          <span>New Goal</span>
        </motion.button>
      </div>

      {/* Financial Intelligence Overview */}
      {enrichedGoals.length > 0 && (
        <div className="goals-intelligence-bento">
          <motion.div
            className="intel-bento-card primary-card"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4 }}
          >
            <div className="intel-card-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <Gauge size={18} style={{ color: 'var(--accent)' }} />
                <span className="intel-card-title">Savings Velocity & Pace</span>
              </div>
              <span
                className={`status-badge smart ${
                  totalRequiredMonthly > monthlySavings && monthlySavings > 0
                    ? 'warning'
                    : totalRequiredMonthly > 0
                    ? 'success'
                    : 'neutral'
                }`}
                style={{ fontSize: 11, padding: '4px 10px' }}
              >
                {totalRequiredMonthly > monthlySavings && monthlySavings > 0
                  ? 'Adjustment Needed'
                  : totalRequiredMonthly > 0
                  ? 'On Track'
                  : 'Flexible Plan'}
              </span>
            </div>

            {/* Smart Pace Alert */}
            {totalRequiredMonthly > monthlySavings && monthlySavings > 0 ? (
              <div className="copilot-alert warning">
                <AlertTriangle size={18} />
                <span>
                  Monthly savings ({fmt(monthlySavings, currency)}) is below the required{' '}
                  {fmt(totalRequiredMonthly, currency)} to hit all milestone deadlines on schedule.
                </span>
              </div>
            ) : totalRequiredMonthly > 0 ? (
              <div className="copilot-alert success">
                <CheckCircle size={18} />
                <span>
                  You are on track. Your current savings comfortably covers the{' '}
                  {fmt(totalRequiredMonthly, currency)} required monthly across active milestones.
                </span>
              </div>
            ) : (
              <div className="copilot-alert neutral">
                <Target size={18} style={{ color: 'var(--text-muted)' }} />
                <span>
                  Flexible timeline active. Add milestone target dates to any goal to unlock automated pacing schedules.
                </span>
              </div>
            )}

            {/* 4-Metric Grid */}
            <div className="intel-metrics-grid">
              <div className="intel-metric">
                <span className="metric-label">Estimated Completion</span>
                <span className="metric-val" style={{ color: 'var(--accent)' }}>
                  {overallMonths ? `${overallMonths} mo` : 'Open'}
                </span>
              </div>
              <div className="intel-metric">
                <span className="metric-label">Monthly Savings</span>
                <span className="metric-val">{fmt(monthlySavings, currency)}</span>
              </div>
              <div className="intel-metric">
                <span className="metric-label">Portfolio Target</span>
                <span className="metric-val">{fmt(totalTarget, currency)}</span>
              </div>
              <div className="intel-metric">
                <span className="metric-label">Remaining to Save</span>
                <span className="metric-val" style={{ color: 'var(--text-secondary)' }}>
                  {fmt(remainingTotal, currency)}
                </span>
              </div>
            </div>

            {/* Portfolio Funded Progress Bar */}
            <div className="portfolio-progress-bar-wrap">
              <div className="ppb-header">
                <span className="ppb-label">Total Portfolio Funded</span>
                <span className="ppb-pct">{totalTarget > 0 ? Math.round((totalSaved / totalTarget) * 100) : 0}%</span>
              </div>
              <div className="ppb-track">
                <div
                  className="ppb-fill"
                  style={{
                    width: `${totalTarget > 0 ? Math.min(100, Math.round((totalSaved / totalTarget) * 100)) : 0}%`,
                  }}
                />
              </div>
            </div>

            {/* Embedded Goal Summary Counters */}
            <div className="intel-pills-row">
              <div className="intel-pill-item">
                <div className="intel-pill-icon" style={{ background: 'var(--accent-dim)', color: 'var(--accent)' }}>
                  <Target size={16} />
                </div>
                <div>
                  <span className="intel-pill-val">{activeGoals.length}</span>
                  <span className="intel-pill-label">Active Goals</span>
                </div>
              </div>
              <div className="intel-pill-item">
                <div className="intel-pill-icon" style={{ background: 'var(--green-dim)', color: 'var(--green)' }}>
                  <Trophy size={16} />
                </div>
                <div>
                  <span className="intel-pill-val">{completedGoals.length}</span>
                  <span className="intel-pill-label">Completed</span>
                </div>
              </div>
              <div className="intel-pill-item">
                <div className="intel-pill-icon" style={{ background: 'var(--saffron-dim, rgba(245, 166, 35, 0.12))', color: 'var(--saffron, #F5A623)' }}>
                  <TrendingUp size={16} />
                </div>
                <div>
                  <span className="intel-pill-val">
                    {totalTarget > 0 && monthlySavings > 0 ? `${Math.round((monthlySavings / totalTarget) * 100)}%` : 'Active'}
                  </span>
                  <span className="intel-pill-label">Monthly Velocity</span>
                </div>
              </div>
            </div>
          </motion.div>

          <ConcentricGoalsChart
            goals={enrichedGoals}
            totalSaved={totalSaved}
            totalTarget={totalTarget}
            currency={currency}
            onNewGoal={() => setShowCreate(true)}
            onSelectGoal={(goal) => {
              const el = document.getElementById(`goal-${goal._id}`);
              if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }}
          />
        </div>
      )}

      {/* Goals Display */}
      {isLoading ? (
        <div className="goals-masonry">
          {[1, 2, 3].map((i) => (
            <div key={i} className="goal-card-premium shimmer" style={{ height: 260 }} />
          ))}
        </div>
      ) : rawGoals.length === 0 ? (
        <motion.div className="goals-empty-state" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}>
          <div className="empty-state-visual">
            <div className="floating-element f1">
              <Target size={32} />
            </div>
            <div className="floating-element f2">
              <TrendingUp size={28} />
            </div>
            <div className="floating-element f3">
              <Coins size={28} />
            </div>
          </div>
          <h2>Set Your First Goal</h2>
          <p>Track targets, milestone deadlines, and emergency funds with automated savings pace calculation.</p>
          <button className="goals-create-btn lg" onClick={() => setShowCreate(true)}>
            <Plus size={18} /> Create Goal
          </button>
        </motion.div>
      ) : (
        <>
          {activeGoals.length > 0 && (
            <div className="goals-section-container">
              <div className="goals-section-header">
                <div>
                  <h2 className="goals-section-title">Active Goals ({activeGoals.length})</h2>
                  <p className="goals-section-sub">Individual milestone targets, automated pacing, and funding contributions</p>
                </div>
              </div>
              <div className="goals-masonry">
                <AnimatePresence>
                  {activeGoals.map((goal, i) => (
                    <GoalCard
                      key={goal._id}
                      goal={goal}
                      currency={currency}
                      index={i}
                      monthlySavings={monthlySavings}
                      onAdd={(suggested) => setAddingTo({ id: goal._id, suggested })}
                      onDelete={() => setDeleteId(goal._id)}
                    />
                  ))}
                </AnimatePresence>
              </div>
            </div>
          )}

          {activeGoals.length === 0 && completedGoals.length > 0 && (
            <div className="all-goals-completed-card">
              <Trophy size={32} style={{ color: 'var(--green)' }} />
              <h3>All Milestones Achieved!</h3>
              <p>You have hit 100% of your targets across all financial milestones. Ready to set your next goal?</p>
              <button className="goals-create-btn" onClick={() => setShowCreate(true)}>
                <Plus size={16} /> Set New Goal
              </button>
            </div>
          )}

          {completedGoals.length > 0 && (
            <div className="completed-goals-section">
              <div className="goals-section-header">
                <h2 className="section-title">
                  <Trophy size={20} className="text-emerald" /> Completed Goals ({completedGoals.length})
                </h2>
              </div>
              <div className="goals-masonry">
                {completedGoals.map((goal, i) => (
                  <GoalCard
                    key={goal._id}
                    goal={goal}
                    currency={currency}
                    index={i}
                    completed
                    onDelete={() => setDeleteId(goal._id)}
                  />
                ))}
              </div>
            </div>
          )}
        </>
      )}

      {/* Create Goal Modal */}
      {typeof document !== 'undefined' &&
        createPortal(
          <AnimatePresence>
            {showCreate && (
              <motion.div
                className="premium-modal-backdrop"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setShowCreate(false)}
              >
            <motion.div
              className="premium-modal"
              variants={modalVariants}
              initial="hidden"
              animate="visible"
              exit="exit"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="premium-modal-header">
                <div>
                  <h2>Create Financial Goal</h2>
                  <p>Define a target amount and optional deadline.</p>
                </div>
                <button className="modal-close-btn" onClick={() => setShowCreate(false)}>
                  <X size={18} />
                </button>
              </div>

              <form className="premium-modal-form" onSubmit={handleCreate}>
                <div className="form-group">
                  <label>Goal Name</label>
                  <div className="input-with-icon">
                    <Target size={18} className="input-icon" />
                    <input
                      type="text"
                      value={form.title}
                      onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))}
                      placeholder="e.g. Emergency Fund, New Laptop, Vacation"
                      required
                      autoFocus
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label>Target Amount ({currency})</label>
                  <div className="input-with-icon">
                    <DollarSign size={18} className="input-icon" />
                    <input
                      type="number"
                      value={form.targetAmount}
                      onChange={(e) => setForm((p) => ({ ...p, targetAmount: e.target.value }))}
                      placeholder="50000"
                      min="1"
                      required
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label>Target Deadline (Optional)</label>
                  <div className="input-with-icon">
                    <Calendar size={18} className="input-icon" />
                    <input
                      type="date"
                      value={form.deadline}
                      onChange={(e) => setForm((p) => ({ ...p, deadline: e.target.value }))}
                    />
                  </div>
                </div>

                <div className="form-row">
                  <div className="form-group flex-1">
                    <label>Icon</label>
                    <div className="icon-grid">
                      {ICONS.map((ic) => {
                        const IconCmp = ICON_MAP[ic];
                        return (
                          <button
                            key={ic}
                            type="button"
                            className={`icon-selector ${form.icon === ic ? 'active' : ''}`}
                            onClick={() => setForm((p) => ({ ...p, icon: ic }))}
                          >
                            <IconCmp size={18} />
                          </button>
                        );
                      })}
                    </div>
                  </div>
                </div>

                <div className="form-group">
                  <label>Color Accent</label>
                  <div className="color-grid">
                    {COLORS.map((c) => (
                      <button
                        key={c}
                        type="button"
                        className={`color-selector ${form.color === c ? 'active' : ''}`}
                        style={{ '--bg': c }}
                        onClick={() => setForm((p) => ({ ...p, color: c }))}
                      >
                        {form.color === c && <CheckCircle size={14} className="color-check" />}
                      </button>
                    ))}
                  </div>
                </div>

                <motion.button
                  type="submit"
                  className="premium-submit-btn"
                  disabled={createMut.isPending}
                  whileTap={{ scale: 0.98 }}
                >
                  {createMut.isPending ? 'Saving...' : 'Create Goal'}
                </motion.button>
              </form>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>,
      document.body
    )}

      {/* Add Progress Modal */}
      {typeof document !== 'undefined' &&
        createPortal(
          <AnimatePresence>
            {addingTo && (
              <motion.div
                className="premium-modal-backdrop"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setAddingTo(null)}
              >
                <motion.div
                  className="premium-modal modal-sm"
                  variants={modalVariants}
                  initial="hidden"
                  animate="visible"
                  exit="exit"
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="premium-modal-header borderless">
                    <div>
                      <h2>Add Savings to Goal</h2>
                    </div>
                    <button className="modal-close-btn" onClick={() => setAddingTo(null)}>
                      <X size={18} />
                    </button>
                  </div>
                  <form className="premium-modal-form" onSubmit={handleAddSavings}>
                    <div className="form-group">
                      <label>Contribution Amount ({currency})</label>
                      <div className="input-with-icon">
                        <Coins size={18} className="input-icon" />
                        <input
                          type="number"
                          value={addAmt}
                          onChange={(e) => setAddAmt(e.target.value)}
                          placeholder="5000"
                          min="1"
                          required
                          autoFocus
                        />
                      </div>
                      {addingTo.suggested > 0 && (
                        <div className="suggested-allocation" onClick={() => setAddAmt(Math.round(addingTo.suggested))}>
                          Suggested monthly pace: <strong>{fmt(addingTo.suggested, currency)}</strong>{' '}
                          <ArrowRight size={14} />
                        </div>
                      )}
                    </div>
                    <motion.button
                      type="submit"
                      className="premium-submit-btn"
                      disabled={addMut.isPending}
                      whileTap={{ scale: 0.98 }}
                    >
                      {addMut.isPending ? 'Saving...' : 'Add to Savings'}
                    </motion.button>
                  </form>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>,
          document.body
        )}

      <ConfirmModal
        open={!!deleteId}
        onClose={() => setDeleteId(null)}
        onConfirm={() => {
          if (deleteId) deleteMut.mutate(deleteId);
        }}
        title="Delete Goal"
        message="Are you sure you want to delete this goal? This action cannot be undone."
      />
    </div>
  );
}

const RING_PALETTE = [
  '#F5A623', // Amber / Gold
  '#38BDF8', // BudGi Sky Blue
  '#10B981', // Mint Green
  '#CBD5E1', // Silver
];

function GoalCard({ goal, currency, index, completed, onAdd, onDelete }) {
  const cardColor = RING_PALETTE[index % RING_PALETTE.length];
  const progress = goal.progressPct;
  const etaText = goal.monthsLeft ? `${Math.ceil(goal.monthsLeft)} mo` : 'Flexible';

  const insight = useMemo(() => {
    if (completed) return 'Goal achieved! Target reached.';
    if (goal.status === 'at_risk') return 'Falling behind target schedule. Consider increasing contributions.';
    if (goal.status === 'needs_attention') return 'Adjust monthly savings to stay on track.';
    if (progress >= 90) return 'Almost there! Final stretch.';
    if (progress >= 50) return 'Over halfway towards your target.';
    return 'Goal active and tracking progress.';
  }, [progress, completed, goal.status]);

  const statusMap = {
    at_risk: { label: 'At Risk', class: 'danger' },
    needs_attention: { label: 'Needs Focus', class: 'warning' },
    on_track: { label: 'On Track', class: 'success' },
    flexible: { label: 'Flexible', class: 'neutral' },
  };

  const IconComponent = ICON_MAP[goal.icon] || Target;

  return (
    <motion.div
      id={`goal-${goal._id}`}
      className={`goal-card-premium ${completed ? 'completed' : ''}`}
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ delay: index * 0.04, duration: 0.3 }}
      style={{ '--card-color': cardColor }}
    >
      <div className="gc-header">
        <div className="gc-icon-wrap" style={{ color: cardColor }}>
          <div className="gc-icon-inner">
            <IconComponent size={22} />
          </div>
        </div>
        <div className="gc-actions">
          {!completed && goal.status && (
            <div className={`status-badge smart ${statusMap[goal.status].class}`}>{statusMap[goal.status].label}</div>
          )}
          {completed && (
            <div className="status-badge">
              <CheckCircle size={14} /> Completed
            </div>
          )}
          <button className="gc-delete" onClick={onDelete} title="Delete Goal">
            <Trash2 size={15} />
          </button>
        </div>
      </div>

      <div className="gc-body">
        <h3 className="gc-title">{goal.title}</h3>
        <p className={`gc-insight ${goal.status === 'at_risk' ? 'text-danger' : ''}`}>{insight}</p>

        <div className="gc-finances">
          <div className="gc-finance-block">
            <span className="gc-label">Saved</span>
            <span className="gc-val current">{fmt(goal.currentAmount, currency)}</span>
          </div>
          <div className="gc-finance-block text-right">
            <span className="gc-label">Target</span>
            <span className="gc-val target">{fmt(goal.targetAmount, currency)}</span>
          </div>
        </div>

        <div className="gc-progress-container">
          <div className="gc-progress-track">
            <motion.div
              className="gc-progress-fill"
              style={{ background: cardColor }}
              initial={{ width: 0 }}
              animate={{ width: `${progress}%` }}
              transition={{ duration: 0.8, ease: 'easeOut' }}
            />
          </div>
          <div className="gc-progress-stats">
            <span className="gc-percent" style={{ color: cardColor }}>{progress}% Complete</span>
            {!completed && (
              <span className="gc-eta">
                {goal.requiredMonthly > 0 ? (
                  <span title="Required monthly savings">Need {fmt(goal.requiredMonthly, currency)}/mo</span>
                ) : (
                  <>
                    <Timer size={13} /> {etaText}
                  </>
                )}
              </span>
            )}
          </div>
        </div>
      </div>

      {!completed && onAdd && (
        <div className="gc-footer">
          <button className="gc-action-btn" onClick={() => onAdd(goal.requiredMonthly)}>
            <Plus size={16} /> Add Funds
          </button>
        </div>
      )}
    </motion.div>
  );
}
