import { useState, useMemo, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { format } from 'date-fns';
import { useQueryClient } from '@tanstack/react-query';
import { transactionsApi } from '../lib/api';
import toast from 'react-hot-toast';
import {
  Plus,
  Search,
  Download,
  Trash2,
  Edit2,
  ArrowUpCircle,
  ArrowDownCircle,
  ArrowLeftRight,
  X,
  Wallet,
  ArrowRight,
  Users,
  Camera,
  Repeat,
  AlertTriangle,
} from '../lib/icons';
import {
  useTransactions,
  useCreateTransaction,
  useDeleteTransaction,
  useUpdateTransaction,
} from '../hooks/useTransactions';
import useStore from '../store/useStore';
import Modal from '../components/ui/Modal';
import ConfirmModal from '../components/ui/ConfirmModal';
import { fmt, CATEGORIES } from '../lib/utils';
import { CategoryPill, CategoryAvatar } from '../lib/categoryIcons';
import './Transactions.css';

const emptyForm = {
  amount: '',
  category: 'Food & Dining',
  type: 'expense',
  description: '',
  merchant: '',
  date: new Date().toISOString().slice(0, 10),
  isRecurring: false,
  recurringFrequency: 'monthly',
  splitDetails: [],
};

export default function Transactions() {
  const { user } = useStore();
  const currency = user?.currency || 'INR';

  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [catFilter, setCatFilter] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [showClearConfirm, setShowClearConfirm] = useState(false);
  const [isClearing, setIsClearing] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [importMsgIdx, setImportMsgIdx] = useState(0);
  const IMPORT_MESSAGES = useMemo(
    () => [
      'Scanning statement securely...',
      'BudGi Copilot is identifying merchants...',
      'Auto-categorizing your spending...',
      'Checking for duplicate transactions...',
      'Finalizing your dashboard...',
    ],
    [],
  );

  useEffect(() => {
    let interval;
    if (isImporting) {
      setImportMsgIdx(0);
      interval = setInterval(() => {
        setImportMsgIdx((prev) => Math.min(prev + 1, IMPORT_MESSAGES.length - 1));
      }, 2000);
    }
    return () => clearInterval(interval);
  }, [isImporting, IMPORT_MESSAGES]);

  const [editTx, setEditTx] = useState(null);
  const [deleteId, setDeleteId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [visibleCount, setVisibleCount] = useState(25);

  const queryClient = useQueryClient();
  const txFilterParams = useMemo(() => ({ limit: 500 }), []);
  const { data: transactions = [], isLoading } = useTransactions(txFilterParams);
  const createTx = useCreateTransaction();
  const deleteTx = useDeleteTransaction();
  const updateTx = useUpdateTransaction();

  const filtered = useMemo(() => {
    return transactions.filter((tx) => {
      const matchSearch =
        !search ||
        (tx.merchant || '').toLowerCase().includes(search.toLowerCase()) ||
        (tx.description || '').toLowerCase().includes(search.toLowerCase()) ||
        (tx.category || '').toLowerCase().includes(search.toLowerCase());

      const isInc = tx.type === 'income' || tx.type === 'credit';
      const isExp = (tx.type === 'expense' || tx.type === 'debit') && tx.category !== 'Transfer';
      const isTransfer = tx.type === 'transfer' || tx.category === 'Transfer';

      let matchType = true;
      if (typeFilter === 'income') matchType = isInc;
      else if (typeFilter === 'expense') matchType = isExp;
      else if (typeFilter === 'transfer') matchType = isTransfer;
      else if (typeFilter) matchType = tx.type === typeFilter;

      const matchCat = !catFilter || tx.category === catFilter;

      const txDate = new Date(tx.date);
      const start = startDate ? new Date(startDate) : null;
      const end = endDate ? new Date(endDate) : null;
      if (end) end.setHours(23, 59, 59, 999);

      const matchStart = !start || txDate >= start;
      const matchEnd = !end || txDate <= end;

      return matchSearch && matchType && matchCat && matchStart && matchEnd;
    });
  }, [transactions, search, typeFilter, catFilter, startDate, endDate]);

  const totals = useMemo(
    () => ({
      income: filtered.filter((t) => t.type === 'income' || t.type === 'credit').reduce((s, t) => s + t.amount, 0),
      expense: filtered.filter((t) => (t.type === 'expense' || t.type === 'debit') && t.category !== 'Transfer').reduce((s, t) => s + t.amount, 0),
    }),
    [filtered],
  );

  const openAdd = () => {
    setForm(emptyForm);
    setEditTx(null);
    setShowModal(true);
  };
  const openEdit = (tx) => {
    setForm({
      amount: tx.amount,
      category: tx.category,
      type: tx.type,
      description: tx.description || '',
      merchant: tx.merchant || '',
      date: new Date(tx.date).toISOString().slice(0, 10),
      isRecurring: tx.isRecurring || false,
      recurringFrequency: tx.recurringFrequency || 'monthly',
      splitDetails: tx.splitDetails || [],
    });
    setEditTx(tx);
    setShowModal(true);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const payload = { ...form, amount: Number(form.amount), date: new Date(form.date) };
    if (editTx) {
      updateTx.mutate({ id: editTx._id, data: payload }, { onSuccess: () => setShowModal(false) });
    } else {
      createTx.mutate(payload, { onSuccess: () => setShowModal(false) });
    }
  };

  const handleFileUpload = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append('file', file);

    setIsImporting(true);
    try {
      const res = await transactionsApi.importCSV(formData);
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      queryClient.invalidateQueries({ queryKey: ['ai', 'health-score'] });
      queryClient.invalidateQueries({ queryKey: ['goals'] });
      toast.success(`Successfully imported ${res?.data?.imported || 'transactions'}!`);
      setShowImportModal(false);
    } catch (err) {
      toast.error(err.userMessage || 'Failed to import CSV');
    } finally {
      setIsImporting(false);
      event.target.value = null; // reset input
    }
  };

  const handleClearAll = async () => {
    setIsClearing(true);
    try {
      // If no year/month provided, the backend (with my upcoming change) will clear everything for the user
      await transactionsApi.clear();
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
      toast.success('Successfully cleared all transactions!');
      setShowClearConfirm(false);
    } catch (err) {
      toast.error(err.userMessage || 'Failed to clear transactions');
    } finally {
      setIsClearing(false);
    }
  };

  const handleScanReceipt = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append('receipt', file);

    setIsScanning(true);
    toast.loading('BudGi AI is scanning your receipt...', { id: 'scan-loading' });

    try {
      const res = await transactionsApi.scanReceipt(formData);
      const data = res.data;

      // Pre-fill the form with extracted data
      setForm({
        ...emptyForm,
        amount: data.amount || '',
        merchant: data.merchant || '',
        category: data.category || 'Food',
        description: data.description || '',
        date: data.date || new Date().toISOString().slice(0, 10),
      });

      setEditTx(null);
      setShowModal(true);
      toast.success('Receipt scanned! Please review the details.', { id: 'scan-loading' });
    } catch (err) {
      toast.error(err.userMessage || 'Failed to scan receipt', { id: 'scan-loading' });
    } finally {
      setIsScanning(false);
      event.target.value = null;
    }
  };

  const fld = (e) => setForm((p) => ({ ...p, [e.target.name]: e.target.value }));

  const addSplit = () => {
    setForm((p) => ({ ...p, splitDetails: [...p.splitDetails, { personName: '', amount: 0, settled: false }] }));
  };
  const removeSplit = (index) => {
    setForm((p) => ({ ...p, splitDetails: p.splitDetails.filter((_, i) => i !== index) }));
  };
  const updateSplit = (index, field, value) => {
    setForm((p) => {
      const newSplits = [...p.splitDetails];
      newSplits[index] = { ...newSplits[index], [field]: value };
      return { ...p, splitDetails: newSplits };
    });
  };

  return (
    <div className="txns-page">
      {/* Summary Cards */}
      <div className="txns-summary">
        <motion.div
          className="txns-summary-item txns-income"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
        >
          <div className="summary-icon-wrap">
            <ArrowUpCircle size={32} strokeWidth={1.5} />
          </div>
          <div>
            <div className="summary-label">Total Income (Period)</div>
            <div className="summary-value">{fmt(totals.income, currency)}</div>
          </div>
        </motion.div>
        <motion.div
          className="txns-summary-item txns-expense"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <div className="summary-icon-wrap">
            <ArrowDownCircle size={32} strokeWidth={1.5} />
          </div>
          <div>
            <div className="summary-label">Monthly Expenses</div>
            <div className="summary-value">{fmt(totals.expense, currency)}</div>
          </div>
        </motion.div>
        <motion.div
          className="txns-summary-item txns-net"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
        >
          <div className="summary-icon-wrap">
            <Wallet size={32} strokeWidth={1.5} style={{ color: 'var(--accent)' }} />
          </div>
          <div>
            <div className="summary-label">Net Balance</div>
            <div
              className="summary-value"
              style={{ color: totals.income - totals.expense >= 0 ? 'var(--green)' : 'var(--red)' }}
            >
              {fmt(totals.income - totals.expense, currency)}
            </div>
          </div>
        </motion.div>
      </div>

      {/* Control Bar */}
      <div className="txns-bar">
        <div className="txns-search-wrap">
          <Search size={18} className="txns-search-icon" />
          <input
            className="txns-search"
            placeholder="Search by merchant, category or note..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          {search && (
            <button className="txns-clear" onClick={() => setSearch('')}>
              <X size={14} />
            </button>
          )}
        </div>

        <div className="txns-filters">
          <input
            type="date"
            className="txns-select"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            title="Start Date"
          />
          <input
            type="date"
            className="txns-select"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            title="End Date"
          />
          <select className="txns-select" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
            <option value="">All Types</option>
            <option value="income">Income</option>
            <option value="expense">Expense</option>
            <option value="transfer">Transfer</option>
          </select>
          <select className="txns-select" value={catFilter} onChange={(e) => setCatFilter(e.target.value)}>
            <option value="">All Categories</option>
            {CATEGORIES.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </div>

        <div className="txns-actions" style={{ gap: 12 }}>
          <input
            type="file"
            accept="image/*"
            capture="environment"
            id="scan-receipt-input"
            style={{ display: 'none' }}
            onChange={handleScanReceipt}
          />
          <motion.button
            className="txns-export-btn"
            whileTap={{ scale: 0.95 }}
            onClick={() => document.getElementById('scan-receipt-input').click()}
            disabled={isScanning}
          >
            <Camera size={16} /> {isScanning ? 'Scanning...' : 'Scan Receipt'}
          </motion.button>
          <motion.button
            className="txns-export-btn"
            whileTap={{ scale: 0.95 }}
            onClick={() => setShowImportModal(true)}
          >
            <ArrowUpCircle size={16} /> Import
          </motion.button>
          <motion.button
            className="txns-export-btn"
            style={{ color: 'var(--red)', borderColor: 'var(--red-dim)' }}
            whileTap={{ scale: 0.95 }}
            onClick={() => setShowClearConfirm(true)}
          >
            <Trash2 size={16} /> Clear All
          </motion.button>
          <motion.button
            className="txns-export-btn"
            whileTap={{ scale: 0.95 }}
            onClick={() => {
              const rows = [
                ['Date', 'Description', 'Merchant', 'Category', 'Type', 'Amount'],
                ...filtered.map((t) => [
                  format(new Date(t.date), 'dd/MM/yyyy'),
                  t.description || '',
                  t.merchant || '',
                  t.category,
                  t.type,
                  t.amount,
                ]),
              ];
              const csv = rows.map((r) => r.join(',')).join('\n');
              const blob = new Blob([csv], { type: 'text/csv' });
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = `budgi_transactions_${format(new Date(), 'yyyy_MM_dd')}.csv`;
              a.click();
            }}
          >
            <Download size={16} /> Export
          </motion.button>
          <motion.button
            className="txns-add-btn"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={openAdd}
          >
            <Plus size={18} /> Add New
          </motion.button>
        </div>
      </div>

      {/* Table Section */}
      {/* Table Section (Desktop) */}
      <div className="txns-table-wrap desktop-only">
        {isLoading ? (
          <div className="txns-loading">
            {Array.from({ length: 8 }).map((_, i) => (
              <div
                key={i}
                className="txn-skeleton shimmer"
                style={{ height: 72, margin: '0 24px', borderBottom: '1px solid var(--border)' }}
              />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="txns-empty">
            <div className="empty-icon-wrap">
              <ArrowLeftRight size={32} />
            </div>
            <h3>No transactions found</h3>
            <p>
              {search || typeFilter || catFilter
                ? "Try adjusting your filters to find what you're looking for."
                : 'Start tracking your expenses by adding your first transaction.'}
            </p>
            <button className="txns-add-btn" onClick={openAdd} style={{ margin: '0 auto' }}>
              <Plus size={16} /> Add Transaction
            </button>
          </div>
        ) : (
          <table className="txns-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Description</th>
                <th>Category</th>
                <th>Type</th>
                <th style={{ textAlign: 'right' }}>Amount</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              <AnimatePresence>
                {filtered.slice(0, visibleCount).map((tx, i) => (
                  <motion.tr
                    key={tx._id}
                    className="txns-row"
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 10 }}
                    transition={{ delay: i * 0.03 }}
                  >
                    <td className="txn-date">{format(new Date(tx.date), 'dd MMM yyyy')}</td>
                    <td className="txn-desc">
                      <div className="txn-title">{tx.merchant || tx.description || 'Uncategorized'}</div>
                      {tx.description && tx.merchant && <div className="txn-sub">{tx.description}</div>}
                      {tx.splitDetails?.length > 0 && (
                        <div
                          className="txn-sub"
                          style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 4, color: 'var(--blue)' }}
                        >
                          <Users size={12} /> Split ({tx.splitDetails.filter((s) => s.settled).length}/{tx.splitDetails.length} settled)
                        </div>
                      )}
                    </td>
                    <td>
                      <CategoryPill category={tx.category} />
                      {tx.isRecurring && (
                        <span className="txn-tag-badge recurring" title="Recurring Mandate / Subscription" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                          <Repeat size={10} /> Recurring
                        </span>
                      )}
                      {tx.isAnomaly && (
                        <span className="txn-tag-badge anomaly" title="Unusual Spending Spike" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                          <AlertTriangle size={10} /> Spike
                        </span>
                      )}
                    </td>
                    <td>
                      <span className={`txn-type-badge txn-type-${tx.type}`}>{tx.type}</span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      {tx.type === 'transfer' || tx.category === 'Transfer' ? (
                        <span className="txn-transfer">{fmt(tx.amount, currency)}</span>
                      ) : (
                        <span className={tx.type === 'income' || tx.type === 'credit' ? 'txn-plus' : 'txn-minus'}>
                          {tx.type === 'income' || tx.type === 'credit' ? '+' : '-'}
                          {fmt(tx.amount, currency)}
                        </span>
                      )}
                    </td>
                    <td>
                      <div className="txn-row-actions">
                        <button className="txn-action-btn" onClick={() => openEdit(tx)}>
                          <Edit2 size={14} />
                        </button>
                        <button className="txn-action-btn delete" onClick={() => setDeleteId(tx._id)}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </tbody>
          </table>
        )}
      </div>

      {/* Mobile Card List */}
      <div className="txns-mobile-list mobile-only">
        {isLoading ? (
          <div className="txns-loading">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="txn-mobile-skeleton shimmer" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="txns-empty-mobile">
            <div className="empty-icon-wrap">
              <ArrowLeftRight size={32} />
            </div>
            <p>No transactions found</p>
          </div>
        ) : (
          <div className="txns-cards-grid">
            <AnimatePresence>
              {filtered.slice(0, visibleCount).map((tx, i) => (
                <motion.div
                  key={tx._id}
                  className="txn-mobile-card interactive-card"
                  initial={{ opacity: 0, y: 15 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.04 }}
                  whileHover={{ y: -2 }}
                  whileTap={{ scale: 0.985 }}
                  onClick={() => openEdit(tx)}
                >
                  <div className="txn-card-header">
                    <CategoryAvatar category={tx.category} size={38} />
                    <div className="txn-card-main">
                      <div className="txn-card-title">{tx.merchant || tx.description || 'Uncategorized'}</div>
                      <div className="txn-card-date" style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 3 }}>
                        <CategoryPill category={tx.category} size="xs" />
                        <span style={{ color: 'var(--text-muted)' }}>•</span>
                        <span>{format(new Date(tx.date), 'dd MMM')}</span>
                      </div>
                    </div>
                    <div className="txn-card-amount">
                      {tx.type === 'transfer' || tx.category === 'Transfer' ? (
                        <span className="txn-transfer">{fmt(tx.amount, currency)}</span>
                      ) : (
                        <span className={tx.type === 'income' || tx.type === 'credit' ? 'txn-plus' : 'txn-minus'}>
                          {tx.type === 'income' || tx.type === 'credit' ? '+' : '-'}
                          {fmt(tx.amount, currency)}
                        </span>
                      )}
                    </div>
                  </div>
                    <div className="txn-card-footer">
                      <div className="txn-card-badges">
                        <span className={`txn-type-badge-sm txn-type-${tx.type}`}>{tx.type}</span>
                        {tx.isRecurring && <span className="txn-recurring-badge" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><Repeat size={10} /> Recurring</span>}
                        {tx.isAnomaly && <span className="txn-anomaly-badge" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><AlertTriangle size={10} /> Spike</span>}
                        {tx.splitDetails?.length > 0 && (
                          <span className="txn-split-badge">
                            <Users size={10} /> Split ({tx.splitDetails.filter((s) => s.settled).length}/{tx.splitDetails.length})
                          </span>
                        )}
                      </div>
                    <div className="txn-card-actions">
                      <button
                        className="txn-card-btn"
                        onClick={(e) => {
                          e.stopPropagation();
                          setDeleteId(tx._id);
                        }}
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>
        )}
      </div>

      {/* Pagination */}
      {filtered.length > visibleCount && (
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 24 }}>
          <motion.button
            className="txns-export-btn"
            whileHover={{ background: 'var(--bg-overlay)' }}
            onClick={() => setVisibleCount((v) => v + 25)}
          >
            Load More Transactions <ArrowRight size={14} />
          </motion.button>
        </div>
      )}

      {/* Import Modal */}
      <Modal
        open={showImportModal}
        onClose={() => !isImporting && setShowImportModal(false)}
        title="Import Transactions via CSV"
      >
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '24px 0', gap: 16 }}>
          {isImporting ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
              <div
                className="btn-spinner"
                style={{
                  width: 40,
                  height: 40,
                  border: '3px solid var(--border-strong)',
                  borderTopColor: 'var(--accent)',
                  borderRadius: '50%',
                  animation: 'spin 1s linear infinite',
                }}
              />
              <div style={{ textAlign: 'center' }}>
                <motion.div
                  key={importMsgIdx}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  transition={{ duration: 0.4 }}
                >
                  <h3 style={{ margin: 0, color: 'var(--text-primary)', fontSize: 18 }}>
                    {importMsgIdx === 0 ? 'BudGi Copilot is analyzing...' : 'Working on it...'}
                  </h3>
                  <p style={{ margin: '8px 0 0', color: 'var(--accent-bright)', fontSize: 14, fontWeight: 500 }}>
                    {IMPORT_MESSAGES[importMsgIdx]}
                  </p>
                </motion.div>
              </div>
            </div>
          ) : (
            <>
              <div style={{ textAlign: 'center', marginBottom: 8 }}>
                <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: 14 }}>
                  Upload your bank statement. We support standard CSV formats from major banks (HDFC, ICICI, SBI, etc.).
                </p>
              </div>

              <label
                style={{
                  width: '100%',
                  border: '2px dashed var(--border-strong)',
                  borderRadius: 16,
                  padding: '48px 24px',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  cursor: 'pointer',
                  background: 'var(--bg-overlay)',
                  transition: 'all 0.2s',
                  gap: 12,
                }}
                onDragOver={(e) => {
                  e.preventDefault();
                  e.currentTarget.style.borderColor = 'var(--accent)';
                }}
                onDragLeave={(e) => {
                  e.currentTarget.style.borderColor = 'var(--border-strong)';
                }}
                onDrop={(e) => {
                  e.preventDefault();
                  e.currentTarget.style.borderColor = 'var(--border-strong)';
                  if (e.dataTransfer.files && e.dataTransfer.files[0]) {
                    handleFileUpload({ target: { files: e.dataTransfer.files } });
                  }
                }}
              >
                <ArrowUpCircle size={32} style={{ color: 'var(--accent)' }} />
                <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>Click to upload or drag and drop</span>
                <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>CSV files only (Max 5MB)</span>
                <input type="file" accept=".csv" style={{ display: 'none' }} onChange={handleFileUpload} />
              </label>
            </>
          )}
        </div>
      </Modal>

      {/* Transaction Modal */}
      <Modal
        open={showModal}
        onClose={() => setShowModal(false)}
        title={editTx ? 'Edit Transaction' : 'Add New Transaction'}
      >
        <form onSubmit={handleSubmit} className="txn-form-compact">
          <div className="modal-grid">
            <div className="modal-field">
              <label>Transaction Type</label>
              <select name="type" value={form.type} onChange={fld} className="txns-select" style={{ width: '100%' }}>
                <option value="expense">Expense</option>
                <option value="income">Income</option>
              </select>
            </div>
            <div className="modal-field">
              <label>Amount ({currency})</label>
              <input
                className="txns-search"
                type="number"
                name="amount"
                value={form.amount}
                onChange={fld}
                placeholder="0.00"
                min="0.01"
                step="0.01"
                required
              />
            </div>
          </div>
          <div className="modal-field">
            <label>Category</label>
            <select
              name="category"
              value={form.category}
              onChange={fld}
              className="txns-select"
              style={{ width: '100%' }}
            >
              {CATEGORIES.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </div>
          <div className="modal-field">
            <label>Merchant / Payee</label>
            <input
              className="txns-search"
              type="text"
              name="merchant"
              value={form.merchant}
              onChange={fld}
              placeholder="e.g. Starbucks, Amazon..."
            />
          </div>
          <div className="modal-field">
            <label>Notes (Optional)</label>
            <input
              className="txns-search"
              type="text"
              name="description"
              value={form.description}
              onChange={fld}
              placeholder="What was this for?"
            />
          </div>
          <div className="modal-field">
            <label>Transaction Date</label>
            <input className="txns-search" type="date" name="date" value={form.date} onChange={fld} required />
          </div>
          <div
            className="modal-field"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              cursor: 'pointer',
              background: 'var(--bg-elevated)',
              padding: 12,
              borderRadius: 12,
            }}
          >
            <input
              type="checkbox"
              checked={form.isRecurring}
              onChange={(e) => setForm((p) => ({ ...p, isRecurring: e.target.checked }))}
              style={{ width: 18, height: 18, accentColor: 'var(--accent)' }}
            />
            <div>
              <div style={{ fontSize: 14, fontWeight: 700 }}>Recurring Transaction</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Automatically repeat this transaction</div>
            </div>
          </div>
          {form.isRecurring && (
            <div className="modal-field">
              <label>Frequency</label>
              <select
                name="recurringFrequency"
                value={form.recurringFrequency}
                onChange={fld}
                className="txns-select"
                style={{ width: '100%' }}
              >
                <option value="daily">Daily</option>
                <option value="weekly">Weekly</option>
                <option value="monthly">Monthly</option>
                <option value="yearly">Yearly</option>
              </select>
            </div>
          )}

          {/* Split Details Section */}
          <div className="modal-field" style={{ marginTop: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <label style={{ margin: 0 }}>Split Bill (Collaborative)</label>
              <button
                type="button"
                onClick={addSplit}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: 'var(--accent)',
                  fontSize: 13,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                <Plus size={14} /> Add Person
              </button>
            </div>
            {form.splitDetails.map((split, i) => (
              <div
                key={i}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '2fr 1fr auto auto',
                  gap: 8,
                  marginBottom: 8,
                  alignItems: 'center',
                }}
              >
                <input
                  className="txns-search"
                  type="text"
                  placeholder="Name"
                  value={split.personName}
                  onChange={(e) => updateSplit(i, 'personName', e.target.value)}
                  required
                />
                <input
                  className="txns-search"
                  type="number"
                  placeholder="Amount"
                  value={split.amount}
                  onChange={(e) => updateSplit(i, 'amount', Number(e.target.value))}
                  min="0"
                  step="0.01"
                  required
                />
                <button
                  type="button"
                  onClick={() => updateSplit(i, 'settled', !split.settled)}
                  className={`txns-split-settle-btn ${split.settled ? 'settled' : ''}`}
                  title={split.settled ? 'Mark as Unpaid' : 'Mark as Paid'}
                >
                  {split.settled ? '✓ Paid' : 'Unpaid'}
                </button>
                <button
                  type="button"
                  onClick={() => removeSplit(i)}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: 'var(--red)',
                    cursor: 'pointer',
                    padding: 8,
                  }}
                >
                  <X size={16} />
                </button>
              </div>
            ))}
          </div>

          <div style={{ display: 'flex', gap: 12, marginTop: 8 }}>
            <button
              type="button"
              onClick={() => setShowModal(false)}
              className="txns-export-btn"
              style={{ flex: 1, justifyContent: 'center' }}
            >
              Cancel
            </button>
            <button type="submit" className="txns-add-btn" style={{ flex: 2, justifyContent: 'center' }}>
              {editTx ? 'Update Transaction' : 'Save Transaction'}
            </button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        open={!!deleteId}
        onClose={() => setDeleteId(null)}
        onConfirm={() => {
          if (deleteId) deleteTx.mutate(deleteId);
        }}
        title="Delete Transaction"
        message="Are you sure you want to permanently delete this transaction? This action cannot be undone."
      />

      <ConfirmModal
        open={showClearConfirm}
        onClose={() => !isClearing && setShowClearConfirm(false)}
        onConfirm={handleClearAll}
        title="Clear All Transactions?"
        message="This will permanently delete ALL your transaction history. This action cannot be undone. Are you sure?"
        confirmText="Yes, Clear All"
        loading={isClearing}
        variant="danger"
      />
    </div>
  );
}
