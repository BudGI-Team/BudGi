import { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, Trash2, Edit2, Check, X, Sliders, Layers, Zap } from '../lib/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { budgetApi } from '../lib/api';
import { useAnalytics, useSmartBudgets } from '../hooks/useAI';
import useStore from '../store/useStore';
import Modal from '../components/ui/Modal';
import ConfirmModal from '../components/ui/ConfirmModal';
import { fmt, CATEGORIES, getCategoryMeta } from '../lib/utils';
import { CategoryAvatar } from '../lib/categoryIcons';
import './Budgets.css';

// BudGi signature minimal calm category palette
const COLORS = [
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

const emptyForm = { category: 'Food & Dining', limit: '', color: COLORS[0] };

export default function Budgets() {
  const { user } = useStore();
  const currency = user?.currency || 'INR';
  const qc = useQueryClient();

  const [showModal, setShowModal] = useState(false);
  const [editBudget, setEditBudget] = useState(null);
  const [deleteId, setDeleteId] = useState(null);
  const [form, setForm] = useState(emptyForm);

  const { data: budgets = [], isLoading } = useQuery({
    queryKey: ['budgets'],
    queryFn: budgetApi.list,
    // FIX: unwrap backend envelope { success, data: { budgets } }
    select: (envelope) => envelope?.data?.budgets ?? [],
    staleTime: 30_000,
  });

  const { data: analytics } = useAnalytics();
  // FIX: analytics now comes from /api/analytics/summary
  // categoryBreakdown is an array; build a map for easy lookup
  const spendByCategory = Object.fromEntries((analytics?.categoryBreakdown ?? []).map((c) => [c.category, c.total]));

  const upsertBudget = useMutation({
    mutationFn: budgetApi.upsert,
    onMutate: async (newBudget) => {
      await qc.cancelQueries({ queryKey: ['budgets'] });
      const previous = qc.getQueryData(['budgets']);
      qc.setQueryData(['budgets'], (old) => {
        const current = Array.isArray(old) ? old : [];
        // If it has an _id, it's an update
        if (newBudget._id) {
          return current.map((b) => (b._id === newBudget._id ? { ...b, ...newBudget } : b));
        }
        // Otherwise, it's a creation (mock an ID)
        return [
          ...current,
          { ...newBudget, _id: `temp-${Date.now()}`, spent: 0, remaining: Number(newBudget.limit), percentage: 0 },
        ];
      });
      setShowModal(false);
      return { previous };
    },
    onError: (err, newBudget, context) => {
      // FIX: Guard against undefined context (prevents crash when onMutate fails)
      if (context?.previous !== undefined) {
        qc.setQueryData(['budgets'], context.previous);
      }
      toast.error(err?.userMessage || 'Failed to save budget');
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['budgets'] });
      qc.invalidateQueries({ queryKey: ['analytics'] });
    },
    onSuccess: () => toast.success(editBudget ? 'Budget updated' : 'Budget created'),
  });

  const deleteBudget = useMutation({
    mutationFn: budgetApi.remove,
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: ['budgets'] });
      const previous = qc.getQueryData(['budgets']);
      qc.setQueryData(['budgets'], (old) => {
        const current = Array.isArray(old) ? old : [];
        return current.filter((b) => b._id !== id);
      });
      return { previous };
    },
    onError: (err, id, context) => {
      // FIX: Guard against undefined context
      if (context?.previous !== undefined) {
        qc.setQueryData(['budgets'], context.previous);
      }
      toast.error('Failed to delete budget');
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['budgets'] });
      qc.invalidateQueries({ queryKey: ['analytics'] });
    },
    onSuccess: () => toast.success('Budget removed'),
  });

  const totalBudget = useMemo(() => budgets.reduce((s, b) => s + b.limit, 0), [budgets]);
  const totalSpent = useMemo(() => Object.values(spendByCategory).reduce((s, v) => s + v, 0), [spendByCategory]);

  const openAdd = () => {
    setForm(emptyForm);
    setEditBudget(null);
    setShowModal(true);
  };
  const openEdit = (b) => {
    setForm({
      category: b.category,
      limit: b.limit,
      color: b.color || COLORS[0],
    });
    setEditBudget(b);
    setShowModal(true);
  };

  const submit = (e) => {
    e.preventDefault();
    upsertBudget.mutate({ ...form, limit: Number(form.limit), ...(editBudget?._id ? { _id: editBudget._id } : {}) });
  };

  const fld = (e) => setForm((p) => ({ ...p, [e.target.name]: e.target.value }));

  return (
    <div className="budgets-page">
      {/* Summary */}
      <div className="budgets-summary">
        <motion.div
          className="budget-summary-card"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          <div className="bscard-label">Total Budget</div>
          <div className="bscard-value">{fmt(totalBudget, currency)}</div>
          <div className="bscard-sub">{budgets.length} categories set</div>
        </motion.div>

        <motion.div
          className="budget-summary-card"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.06 }}
        >
          <div className="bscard-label">Total Spent</div>
          <div
            className="bscard-value"
            style={{
              color: totalSpent > totalBudget ? 'var(--red)' : 'var(--text-primary)',
            }}
          >
            {fmt(totalSpent, currency)}
          </div>
          <div className="bscard-sub">
            {totalBudget > 0 ? `${Math.round((totalSpent / totalBudget) * 100)}% of budget used` : '—'}
          </div>
        </motion.div>

        <motion.div
          className="budget-summary-card budget-summary-highlight"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.12 }}
        >
          <div className="bscard-label">Remaining</div>
          <div
            className="bscard-value"
            style={{
              color: totalBudget - totalSpent < 0 ? 'var(--red)' : 'var(--green)',
            }}
          >
            {fmt(Math.abs(totalBudget - totalSpent), currency)}
          </div>
          <div className="bscard-sub">{totalBudget - totalSpent < 0 ? 'Over budget!' : 'Safe to spend'}</div>
        </motion.div>

        <motion.div
          className="budget-add-card"
          onClick={openAdd}
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.18 }}
          whileHover={{ scale: 1.01 }}
          whileTap={{ scale: 0.99 }}
        >
          <div className="budget-add-icon">
            <Plus size={22} />
          </div>
          <div className="budget-add-text">New Budget</div>
        </motion.div>
      </div>

      {/* Budget cards grid */}
      {isLoading ? (
        <div className="budgets-grid">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="budget-card shimmer" style={{ height: 160, opacity: 0.4 }} />
          ))}
        </div>
      ) : budgets.length === 0 ? (
        <SmartBudgetSetup currency={currency} fmt={fmt} onManualCreate={openAdd} upsertBudget={upsertBudget} />
      ) : (
        <>
          <SmartSuggestionsBar currency={currency} fmt={fmt} upsertBudget={upsertBudget} />
          <div className="budgets-grid">
            <AnimatePresence>
              {budgets.map((budget, i) => {
                const spent = spendByCategory[budget.category] || 0;
                const pct = budget.limit > 0 ? Math.min(100, (spent / budget.limit) * 100) : 0;
                const over = pct >= 100;
                const warn = pct >= 80;
                const meta = getCategoryMeta(budget.category);
                const color = budget.color || meta.color;

                return (
                  <motion.div
                    key={budget._id}
                    className={`budget-card interactive-card ${over ? 'budget-card-over' : warn ? 'budget-card-warn' : ''}`}
                    initial={{ opacity: 0, scale: 0.97 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.95 }}
                    transition={{ duration: 0.3, delay: i * 0.05 }}
                    whileHover={{ y: -3 }}
                    style={{ '--card-color': color }}
                  >
                    <div className="budget-card-top">
                      <CategoryAvatar category={budget.category} size={32} />
                      <span className="budget-card-cat">{budget.category}</span>
                      <div className="budget-card-actions">
                        <button className="baction edit" onClick={() => openEdit(budget)}>
                          <Edit2 size={13} />
                        </button>
                        <button className="baction delete" onClick={() => setDeleteId(budget._id)}>
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </div>

                    <div className="budget-card-amounts">
                      <span
                        className="budget-card-spent"
                        style={{
                          color: over ? 'var(--red)' : warn ? 'var(--amber)' : 'var(--text-primary)',
                        }}
                      >
                        {fmt(spent, currency)}
                      </span>
                      <span className="budget-card-limit">/ {fmt(budget.limit, currency)}</span>
                    </div>

                    {/* Progress */}
                    <div className="budget-progress-wrap">
                      <div className="budget-progress-bar">
                        <motion.div
                          className="budget-progress-fill"
                          style={{
                            background: over ? 'var(--red)' : warn ? 'var(--amber)' : color,
                          }}
                          initial={{ width: 0 }}
                          animate={{ width: `${pct}%` }}
                          transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
                        />
                      </div>
                      <span
                        className="budget-pct"
                        style={{
                          color: over ? 'var(--red)' : warn ? 'var(--amber)' : 'var(--text-muted)',
                        }}
                      >
                        {Math.round(pct)}%
                      </span>
                    </div>

                    <div className="budget-card-status">
                      {over ? (
                        <span className="status-over">⚠ Over budget by {fmt(spent - budget.limit, currency)}</span>
                      ) : warn ? (
                        <span className="status-warn">Approaching limit</span>
                      ) : (
                        <span className="status-ok">✓ On track</span>
                      )}
                    </div>
                  </motion.div>
                );
              })}
            </AnimatePresence>
          </div>
        </>
      )}

      {/* Modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)} title={editBudget ? 'Edit Budget' : 'New Budget'}>
        <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div className="modal-field">
            <label>Category</label>
            <select
              name="category"
              value={form.category}
              onChange={fld}
              className="select-field"
              disabled={!!editBudget}
            >
              {CATEGORIES.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </div>

          <div className="modal-field">
            <label>Monthly Limit ({currency})</label>
            <input
              className="input-field input-md"
              type="number"
              name="limit"
              value={form.limit}
              onChange={fld}
              placeholder="5000"
              min="1"
              required
            />
          </div>

          <div className="modal-field">
            <label>Color</label>
            <div className="color-picker">
              {COLORS.map((c) => (
                <button
                  key={c}
                  type="button"
                  className={`color-swatch ${form.color === c ? 'color-swatch-active' : ''}`}
                  style={{ background: c }}
                  onClick={() => setForm((p) => ({ ...p, color: c }))}
                >
                  {form.color === c && <Check size={12} color="white" />}
                </button>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
            <button
              type="button"
              onClick={() => setShowModal(false)}
              style={{
                flex: 1,
                height: 40,
                background: 'var(--bg-elevated)',
                border: '1px solid var(--border)',
                borderRadius: 'var(--radius-md)',
                color: 'var(--text-secondary)',
                cursor: 'pointer',
                fontSize: 14,
                fontFamily: 'var(--font-body)',
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={upsertBudget.isPending}
              style={{
                flex: 2,
                height: 40,
                background: 'var(--btn-primary-bg)',
                border: 'none',
                borderRadius: 'var(--radius-md)',
                color: 'var(--btn-primary-text)',
                cursor: 'pointer',
                fontSize: 14,
                fontWeight: 600,
                fontFamily: 'var(--font-body)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6,
              }}
            >
              {upsertBudget.isPending ? (
                <span
                  className="btn-spinner"
                  style={{
                    borderColor: 'rgba(255,255,255,0.3)',
                    borderTopColor: 'currentColor',
                  }}
                />
              ) : (
                <>
                  <Check size={14} /> {editBudget ? 'Update Budget' : 'Save Budget'}
                </>
              )}
            </button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        open={!!deleteId}
        onClose={() => setDeleteId(null)}
        onConfirm={() => {
          if (deleteId) deleteBudget.mutate(deleteId);
        }}
        title="Delete Budget"
        message="Are you sure you want to permanently delete this budget? This action cannot be undone."
      />
    </div>
  );
}

/* ─── Smart Budget Setup (empty state) ───────────────────────────── */
function SmartBudgetSetup({ currency, fmt, onManualCreate, upsertBudget }) {
  const { data: smartData, isLoading } = useSmartBudgets();
  const suggestions = smartData?.suggestions ?? [];

  const handleCreateBudget = (s) => {
    upsertBudget.mutate({
      category: s.category,
      limit: s.suggestedLimit,
      color: s.color,
    });
  };

  const handleCreateAll = () => {
    for (const s of suggestions) {
      upsertBudget.mutate({
        category: s.category,
        limit: s.suggestedLimit,
        color: s.color,
      });
    }
  };

  return (
    <motion.div className="budgets-empty" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.3 }}>
      <div
        className="budget-empty-icon"
        style={{ background: 'linear-gradient(135deg, rgba(124,106,245,0.15), rgba(77,156,245,0.1))' }}
      >
        <Sliders size={26} style={{ color: 'var(--accent-bright)' }} />
      </div>
      <h3>Smart Budget Setup</h3>
      <p style={{ marginBottom: 16 }}>
        BudGi analyzed your spending history and created personalized budget suggestions.
      </p>

      {isLoading ? (
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', justifyContent: 'center' }}>
          {[0, 1, 2].map((i) => (
            <div key={i} className="shimmer" style={{ width: 200, height: 80, borderRadius: 12 }} />
          ))}
        </div>
      ) : suggestions.length > 0 ? (
        <>
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 12,
              justifyContent: 'center',
              marginBottom: 20,
              maxWidth: 680,
            }}
          >
            {suggestions.map((s, i) => (
              <motion.div
                key={s.category}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.1 + i * 0.05 }}
                style={{
                  padding: '14px 18px',
                  background: 'var(--glass-bg)',
                  border: '1px solid var(--border)',
                  borderRadius: 12,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 6,
                  minWidth: 190,
                  flex: '1 1 180px',
                  maxWidth: 220,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, fontSize: 13 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: s.color }} />
                    {s.category}
                  </span>
                  <motion.button
                    onClick={() => handleCreateBudget(s)}
                    whileTap={{ scale: 0.9 }}
                    style={{
                      background: 'var(--btn-primary-bg)',
                      border: 'none',
                      borderRadius: 6,
                      color: 'var(--btn-primary-text)',
                      padding: '4px 8px',
                      fontSize: 11,
                      cursor: 'pointer',
                      fontWeight: 600,
                      display: 'flex',
                      alignItems: 'center',
                      gap: 3,
                    }}
                    disabled={upsertBudget.isPending}
                  >
                    <Plus size={10} /> Add
                  </motion.button>
                </div>
                <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--text-primary)' }}>
                  {fmt(s.suggestedLimit, currency)}
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                  Avg: {fmt(s.avgMonthlySpend, currency)}/mo · {s.monthsAnalyzed}mo data
                </div>
              </motion.div>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <motion.button
              className="budget-primary-btn"
              onClick={handleCreateAll}
              whileTap={{ scale: 0.97 }}
              style={{ display: 'flex', alignItems: 'center', gap: 6 }}
              disabled={upsertBudget.isPending}
            >
              <Zap size={14} /> Create All ({suggestions.length})
            </motion.button>
            <button
              className="budget-primary-btn"
              onClick={onManualCreate}
              style={{
                background: 'var(--bg-elevated)',
                color: 'var(--text-secondary)',
                border: '1px solid var(--border)',
              }}
            >
              <Plus size={15} /> Custom Budget
            </button>
          </div>
        </>
      ) : (
        <>
          <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>
            Add transactions first so BudGi can analyze your spending patterns.
          </p>
          <button className="budget-primary-btn" onClick={onManualCreate}>
            <Plus size={15} /> Create Budget Manually
          </button>
        </>
      )}
    </motion.div>
  );
}

/* ─── Smart Suggestions Bar (shown when budgets exist) ───────────── */
function SmartSuggestionsBar({ currency, fmt, upsertBudget }) {
  const { data: smartData } = useSmartBudgets();
  const [dismissed, setDismissed] = useState(false);
  const suggestions = smartData?.suggestions ?? [];

  if (dismissed || suggestions.length === 0) return null;

  return (
    <motion.div
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      style={{
        marginBottom: 16,
        padding: '12px 16px',
        background: 'linear-gradient(135deg, rgba(124,106,245,0.06), rgba(77,156,245,0.04))',
        border: '1px solid rgba(124,106,245,0.15)',
        borderRadius: 12,
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        flexWrap: 'wrap',
      }}
    >
      <span
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          fontSize: 13,
          fontWeight: 600,
          color: 'var(--accent-bright)',
          flexShrink: 0,
        }}
      >
        <Layers size={14} /> Analysis found {suggestions.length} suggestions:
      </span>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', flex: 1 }}>
        {suggestions.slice(0, 3).map((s) => (
          <motion.button
            key={s.category}
            onClick={() => upsertBudget.mutate({ category: s.category, limit: s.suggestedLimit, color: s.color })}
            whileTap={{ scale: 0.95 }}
            style={{
              background: 'var(--glass-bg)',
              border: '1px solid var(--border)',
              borderRadius: 8,
              padding: '5px 10px',
              fontSize: 12,
              cursor: 'pointer',
              color: 'var(--text-primary)',
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              fontFamily: 'var(--font-body)',
            }}
            disabled={upsertBudget.isPending}
          >
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: s.color }} />
            {s.category} — {fmt(s.suggestedLimit, currency)}
            <Plus size={10} />
          </motion.button>
        ))}
      </div>
      <button
        onClick={() => setDismissed(true)}
        style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', padding: 4 }}
      >
        <X size={14} />
      </button>
    </motion.div>
  );
}
