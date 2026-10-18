import { useState, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  User,
  Globe,
  Target,
  CreditCard,
  Shield,
  Check,
  LogOut,
  Download,
  Trash2,
  X,
  Crown,
  Camera,
  Flame,
} from '../lib/icons';
import { useNavigate } from 'react-router-dom';
import { useUpdateProfile } from '../hooks/useAuth';
import { transactionsApi, authApi } from '../lib/api';
import useStore from '../store/useStore';
import toast from 'react-hot-toast';
import './Settings.css';

const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP', 'AED', 'SGD'];

const BITMOJIS = [
  'https://api.dicebear.com/7.x/lorelei/svg?seed=Felix',
  'https://api.dicebear.com/7.x/lorelei/svg?seed=Aneka',
  'https://api.dicebear.com/7.x/lorelei/svg?seed=Julian',
  'https://api.dicebear.com/7.x/lorelei/svg?seed=Amaya',
  'https://api.dicebear.com/7.x/lorelei/svg?seed=Robert',
  'https://api.dicebear.com/7.x/lorelei/svg?seed=Mimi',
  'https://api.dicebear.com/7.x/lorelei/svg?seed=Casper',
  'https://api.dicebear.com/7.x/lorelei/svg?seed=Jocelyn',
];

export default function Settings() {
  const { user, logout } = useStore();
  const navigate = useNavigate();
  const updateProfile = useUpdateProfile();

  const [form, setForm] = useState({
    name: '',
    username: '',
    dateOfBirth: '',
    monthlyIncome: '',
    currency: 'INR',
    financialGoal: '',
    profileImage: '',
    avatar: 'zen_saver',
  });

  const [usernameStatus, setUsernameStatus] = useState(null);
  const [showAvatarPicker, setShowAvatarPicker] = useState(false);

  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState('');
  const [exporting, setExporting] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    if (user) {
      setForm({
        name: user.name || '',
        username: user.username || '',
        dateOfBirth: user.dateOfBirth ? user.dateOfBirth.split('T')[0] : '',
        monthlyIncome: user.monthlyIncome || '',
        currency: user.currency || 'INR',
        financialGoal: user.financialGoal || '',
        profileImage: user.profileImage || '',
        avatar: user.avatar || 'zen_saver',
      });
    }
  }, [user]);

  // Debounced username validation
  useEffect(() => {
    const clean = (form.username || '').replace(/^@/, '').toLowerCase().trim();
    const original = (user?.username || '').replace(/^@/, '').toLowerCase().trim();
    if (!clean) {
      setUsernameStatus(null);
      return;
    }
    if (clean === original) {
      setUsernameStatus('available');
      return;
    }
    if (clean.length < 3) {
      setUsernameStatus('invalid');
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const res = await authApi.checkUsername(clean);
        if (res.data?.available) {
          setUsernameStatus('available');
        } else {
          setUsernameStatus('taken');
        }
      } catch {
        setUsernameStatus('taken');
      }
    }, 400);
    return () => clearTimeout(timer);
  }, [form.username, user?.username]);

  const fld = (e) => setForm((p) => ({ ...p, [e.target.name]: e.target.value }));

  const handleFileUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;

    if (file.size > 2 * 1024 * 1024) {
      return toast.error('Image too large (max 2MB)');
    }

    const reader = new FileReader();
    reader.onloadend = () => {
      const base64 = reader.result;
      setForm((p) => ({ ...p, profileImage: base64 }));
      updateProfile.mutate({ profileImage: base64 });
    };
    reader.readAsDataURL(file);
  };

  const submit = (e) => {
    e.preventDefault();
    const cleanUsername = form.username ? form.username.replace(/^@/, '').toLowerCase().trim() : null;
    updateProfile.mutate({
      name: form.name,
      username: cleanUsername,
      dateOfBirth: form.dateOfBirth || null,
      monthlyIncome: Number(form.monthlyIncome),
      currency: form.currency,
      financialGoal: form.financialGoal,
      profileImage: form.profileImage,
      avatar: form.avatar,
    });
  };

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await authApi.logout();
    } catch {
      /* ignore — logout locally regardless */
    }
    logout();
    navigate('/login');
  };

  const handleExportData = useCallback(async () => {
    setExporting(true);
    try {
      const envelope = await transactionsApi.exportAll({});
      const transactions = envelope?.data?.transactions ?? [];
      if (transactions.length === 0) {
        toast.error('No transactions to export');
        setExporting(false);
        return;
      }
      // Build CSV
      const headers = ['Date', 'Type', 'Category', 'Amount', 'Merchant', 'Description', 'Source'];
      const rows = transactions.map((t) => [
        new Date(t.date).toLocaleDateString('en-IN'),
        t.type,
        t.category,
        t.amount,
        t.merchant || '',
        (t.description || '').replace(/,/g, ';'),
        t.source || 'manual',
      ]);
      const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');

      // Download
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `budgi-transactions-${new Date().toISOString().split('T')[0]}.csv`;
      link.click();
      URL.revokeObjectURL(url);
      toast.success(`Exported ${transactions.length} transactions`);
    } catch (err) {
      toast.error(err.userMessage || 'Export failed');
    }
    setExporting(false);
  }, []);

  const handleExportJSON = useCallback(async () => {
    setExporting(true);
    try {
      const envelope = await transactionsApi.exportAll({});
      const transactions = envelope?.data?.transactions ?? [];
      const blob = new Blob([JSON.stringify(transactions, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `budgi-data-${new Date().toISOString().split('T')[0]}.json`;
      link.click();
      URL.revokeObjectURL(url);
      toast.success(`Exported ${transactions.length} transactions as JSON`);
    } catch (err) {
      toast.error(err.userMessage || 'Export failed');
    }
    setExporting(false);
  }, []);

  const initials = user?.name
    ? user.name
        .split(' ')
        .map((w) => w[0])
        .join('')
        .slice(0, 2)
        .toUpperCase()
    : '?';

  return (
    <div className="settings-page">
      <div className="settings-inner">
        {/* Profile header */}
        <motion.div
          className="settings-profile-header"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          <div className="settings-avatar-container">
            {user?.profileImage ? (
              <img
                src={user.profileImage}
                alt={user.name}
                className="settings-avatar-img"
                onClick={() => setShowAvatarPicker(true)}
                style={{ cursor: 'pointer' }}
              />
            ) : (
              <div className="settings-avatar" onClick={() => setShowAvatarPicker(true)} style={{ cursor: 'pointer' }}>
                {initials}
              </div>
            )}
            <button
              type="button"
              className="settings-avatar-edit"
              onClick={() => document.getElementById('avatar-upload').click()}
              aria-label="Upload profile photo"
            >
              <Camera size={14} />
            </button>
            <input type="file" id="avatar-upload" hidden accept="image/*" onChange={handleFileUpload} />
          </div>
          <div className="settings-profile-info">
            <h2 className="settings-profile-name">{user?.name}</h2>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', margin: '4px 0' }}>
              <span style={{ fontSize: '13px', color: 'var(--accent-bright)', fontWeight: 600 }}>
                @{user?.username || 'member'}
              </span>
              <span style={{
                background: 'var(--amber-dim)',
                color: 'var(--amber)',
                border: '0.5px solid var(--amber)',
                borderRadius: '999px',
                padding: '2px 8px',
                fontSize: '11px',
                fontWeight: 700
              }}>
                <Flame size={11} style={{ color: 'var(--amber)', display: 'inline', verticalAlign: '-1px', marginRight: 4 }} />
                {user?.streak?.count || 1}d Streak
              </span>
              <span style={{
                background: 'var(--accent-dim)',
                color: 'var(--accent)',
                border: '0.5px solid var(--accent)',
                borderRadius: '999px',
                padding: '2px 8px',
                fontSize: '11px',
                fontWeight: 700
              }}>
                {user?.currency || 'INR'}
              </span>
            </div>
            <p className="settings-profile-email">{user?.email}</p>
          </div>
          <div className={`settings-plan-badge settings-plan-${user?.plan || 'free'}`}>{user?.plan || 'free'}</div>
        </motion.div>

        {/* Form */}
        <motion.form
          className="settings-form"
          onSubmit={submit}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.1 }}
        >
          {/* Personal info */}
          <div className="settings-section">
            <div className="settings-section-header">
              <User size={15} />
              <span>Personal Information</span>
            </div>
            <div className="settings-fields">
              <div className="settings-field">
                <label>Full name</label>
                <input
                  className="input-field input-md"
                  type="text"
                  name="name"
                  value={form.name}
                  onChange={fld}
                  placeholder="Your name"
                />
              </div>
              <div className="settings-field">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <label>Unique Username</label>
                  {usernameStatus === 'available' && <span style={{ color: 'var(--green)', fontSize: '11px', fontWeight: 600 }}>✓ Available</span>}
                  {usernameStatus === 'taken' && <span style={{ color: 'var(--red)', fontSize: '11px', fontWeight: 600 }}>✗ Already taken</span>}
                  {usernameStatus === 'invalid' && <span style={{ color: 'var(--amber)', fontSize: '11px', fontWeight: 600 }}>Min 3 chars</span>}
                </div>
                <div style={{ position: 'relative' }}>
                  <span style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)', fontWeight: 600 }}>@</span>
                  <input
                    className="input-field input-md"
                    type="text"
                    name="username"
                    value={form.username}
                    onChange={(e) => setForm(p => ({ ...p, username: e.target.value.replace(/^@/, '').replace(/\s/g, '').toLowerCase() }))}
                    placeholder="username"
                    style={{ paddingLeft: '28px' }}
                  />
                </div>
              </div>
              <div className="settings-field">
                <label>Date of Birth</label>
                <input
                  className="input-field input-md"
                  type="date"
                  name="dateOfBirth"
                  value={form.dateOfBirth}
                  onChange={fld}
                />
              </div>
              <div className="settings-field">
                <label>Email address</label>
                <input
                  className="input-field input-md"
                  type="email"
                  value={user?.email || ''}
                  disabled
                  style={{ opacity: 0.5, cursor: 'not-allowed' }}
                />
              </div>
            </div>
          </div>

          <div className="settings-divider" />

          {/* Financial settings */}
          <div className="settings-section">
            <div className="settings-section-header">
              <Globe size={15} />
              <span>Financial Settings</span>
            </div>
            <div className="settings-fields">
              <div className="settings-field">
                <label>Currency</label>
                <select name="currency" value={form.currency} onChange={fld} className="select-field">
                  {CURRENCIES.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
              </div>
              <div className="settings-field">
                <label>Monthly Income</label>
                <input
                  className="input-field input-md"
                  type="number"
                  name="monthlyIncome"
                  value={form.monthlyIncome}
                  onChange={fld}
                  placeholder="55000"
                  min="0"
                />
              </div>
            </div>
          </div>

          <div className="settings-divider" />

          {/* Goals */}
          <div className="settings-section">
            <div className="settings-section-header">
              <Target size={15} />
              <span>Financial Goals</span>
            </div>
            <div className="settings-field" style={{ maxWidth: '100%' }}>
              <label>Your goal</label>
              <input
                className="input-field input-md"
                type="text"
                name="financialGoal"
                value={form.financialGoal}
                onChange={fld}
                placeholder="Save ₹1 lakh this year, build emergency fund..."
              />
              <span
                style={{
                  fontSize: 12,
                  color: 'var(--text-muted)',
                  marginTop: 4,
                }}
              >
                This helps BudGi Copilot give you personalized advice.
              </span>
            </div>
          </div>

          {/* Security */}
          <div className="settings-section">
            <div className="settings-section-header">
              <Shield size={15} />
              <span>Security</span>
            </div>
            <div className="settings-notif-row">
              <div className="settings-notif-info">
                <span className="settings-notif-label">Password</span>
                <span className="settings-notif-desc">Change your account password</span>
              </div>
              <motion.button
                className="settings-export-btn"
                type="button"
                whileTap={{ scale: 0.97 }}
                onClick={async () => {
                  try {
                    await authApi.forgotPassword(user?.email);
                    toast.success('Password reset link sent to your email!');
                  } catch {
                    toast.error('Failed to send reset link');
                  }
                }}
                style={{ padding: '8px 16px', fontSize: 13 }}
              >
                Change Password
              </motion.button>
            </div>
            <div className="settings-notif-row">
              <div className="settings-notif-info">
                <span className="settings-notif-label">Email verification</span>
                <span className="settings-notif-desc">
                  {user?.isEmailVerified ? 'Your email is verified' : 'Verify your email address'}
                </span>
              </div>
              <div className="settings-notif-status">
                {user?.isEmailVerified ? (
                  <>
                    <span className="settings-notif-active">Verified</span>
                    <Check size={12} style={{ color: 'var(--green)' }} />
                  </>
                ) : (
                  <motion.button
                    className="settings-export-btn"
                    type="button"
                    whileTap={{ scale: 0.97 }}
                    onClick={async () => {
                      try {
                        await authApi.resendVerification(user?.email);
                        toast.success('Verification email sent!');
                      } catch {
                        toast.error('Failed to send verification');
                      }
                    }}
                    style={{ padding: '8px 16px', fontSize: 13 }}
                  >
                    Verify Now
                  </motion.button>
                )}
              </div>
            </div>
          </div>

          <div className="settings-divider" />
          <div className="settings-save-row">
            <motion.button
              type="submit"
              className="settings-save-btn"
              disabled={updateProfile.isPending}
              whileTap={{ scale: 0.98 }}
            >
              {updateProfile.isPending ? (
                <span
                  className="btn-spinner"
                  style={{
                    border: '2px solid rgba(255,255,255,0.3)',
                    borderTopColor: 'white',
                    width: 16,
                    height: 16,
                    borderRadius: '50%',
                    animation: 'spin 0.6s linear infinite',
                  }}
                />
              ) : (
                <>
                  <Check size={15} /> Save Changes
                </>
              )}
            </motion.button>
          </div>
        </motion.form>

        {/* Data Management */}
        <motion.div
          className="settings-section-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.15 }}
        >
          <div className="settings-section-header">
            <Download size={15} />
            <span>Data Management</span>
          </div>
          <div className="settings-export-row">
            <motion.button
              className="settings-export-btn"
              onClick={handleExportData}
              disabled={exporting}
              whileTap={{ scale: 0.98 }}
            >
              <Download size={14} />
              {exporting ? 'Exporting...' : 'Export as CSV'}
            </motion.button>
            <motion.button
              className="settings-export-btn settings-export-secondary"
              onClick={handleExportJSON}
              disabled={exporting}
              whileTap={{ scale: 0.98 }}
            >
              <Download size={14} />
              Export as JSON
            </motion.button>
          </div>
          <span className="settings-export-hint">Download all your transaction data. Your data, your control.</span>
        </motion.div>

        {/* Plan info */}
        <motion.div
          className="settings-plan-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.2 }}
        >
          <div className="settings-plan-left">
            <CreditCard size={18} />
            <div>
              <div className="settings-plan-title">
                {user?.plan === 'pro' ? 'BudGi Pro' : user?.plan === 'elite' ? 'BudGi Elite' : 'Free Plan'}
              </div>
              <div className="settings-plan-desc">
                {user?.plan === 'free'
                  ? 'Upgrade to Pro for BudGi Copilot, unlimited transactions, and predictions.'
                  : 'Full access to all BudGi features.'}
              </div>
            </div>
          </div>
          {user?.plan === 'free' && (
            <motion.button
              className="settings-upgrade-btn"
              onClick={() => setShowUpgradeModal(true)}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              <Crown size={14} /> Upgrade to Pro
            </motion.button>
          )}
        </motion.div>

        {/* Danger zone */}
        <motion.div
          className="settings-danger"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.25 }}
        >
          <div className="settings-section-header">
            <Shield size={15} />
            <span>Account</span>
          </div>
          <motion.button
            className="settings-logout-btn"
            onClick={handleLogout}
            disabled={loggingOut}
            whileTap={{ scale: 0.97 }}
          >
            <LogOut size={14} /> {loggingOut ? 'Signing out...' : 'Sign out of BudGi'}
          </motion.button>
          <motion.button
            className="settings-delete-btn"
            onClick={() => {
              setDeleteConfirm('');
              setShowDeleteModal(true);
            }}
            whileTap={{ scale: 0.97 }}
          >
            <Trash2 size={14} /> Delete my account
          </motion.button>
        </motion.div>
      </div>

      {/* Upgrade Modal */}
      {typeof document !== 'undefined' &&
        createPortal(
          <AnimatePresence>
            {showUpgradeModal && (
              <motion.div
                className="settings-modal-overlay"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setShowUpgradeModal(false)}
              >
                <motion.div
                  className="settings-upgrade-modal"
                  initial={{ opacity: 0, scale: 0.92, y: 20 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.92, y: 20 }}
                  transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
                  onClick={(e) => e.stopPropagation()}
                >
                  <button className="settings-modal-close" onClick={() => setShowUpgradeModal(false)}>
                    <X size={18} />
                  </button>

                  <div className="upgrade-modal-icon">
                    <Crown size={28} />
                  </div>
                  <h3 className="upgrade-modal-title">Upgrade to BudGi Pro</h3>
                  <p className="upgrade-modal-desc">Unlock the full power of advanced financial analytics</p>

                  <div className="upgrade-features">
                    {[
                      'Unlimited transactions',
                      'BudGi Copilot assistant',
                      'ML spending predictions',
                      'Smart budget alerts',
                      'Priority support',
                      'Export all data',
                    ].map((f) => (
                      <div key={f} className="upgrade-feature-item">
                        <Check size={14} style={{ color: 'var(--green)' }} />
                        <span>{f}</span>
                      </div>
                    ))}
                  </div>

                  <div className="upgrade-price-row">
                    <span className="upgrade-price">₹299</span>
                    <span className="upgrade-period">/month</span>
                  </div>

                  <motion.button
                    className="upgrade-cta-btn"
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                    onClick={() => {
                      setShowUpgradeModal(false);
                      toast.success("🎉 Pro upgrade coming soon! You'll be notified when payments are live.", {
                        duration: 4000,
                      });
                    }}
                  >
                    <Crown size={16} /> Upgrade to BudGi Pro
                  </motion.button>
                  <span className="upgrade-note">7-day free trial · Cancel anytime</span>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>,
          document.body
        )}

      {/* Delete Account Modal */}
      {typeof document !== 'undefined' &&
        createPortal(
          <AnimatePresence>
            {showDeleteModal && (
              <motion.div
                className="settings-modal-overlay"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setShowDeleteModal(false)}
              >
                <motion.div
                  className="settings-upgrade-modal"
                  style={{ maxWidth: 440 }}
                  initial={{ opacity: 0, scale: 0.92, y: 20 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.92, y: 20 }}
                  transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
                  onClick={(e) => e.stopPropagation()}
                >
                  <button className="settings-modal-close" onClick={() => setShowDeleteModal(false)}>
                    <X size={18} />
                  </button>
                  <div className="upgrade-modal-icon" style={{ background: 'var(--red-dim)' }}>
                    <Trash2 size={28} style={{ color: 'var(--red)' }} />
                  </div>
                  <h3 className="upgrade-modal-title" style={{ color: 'var(--red)' }}>
                    Delete Account
                  </h3>
                  <p className="upgrade-modal-desc">
                    This action is <strong>permanent</strong> and cannot be undone. All your transactions, budgets, goals,
                    and data will be permanently deleted.
                  </p>
                  <div style={{ marginTop: 16 }}>
                    <label style={{ fontSize: 13, color: 'var(--text-secondary)', display: 'block', marginBottom: 8 }}>
                      Type <strong style={{ color: 'var(--red)' }}>DELETE</strong> to confirm:
                    </label>
                    <input
                      className="input-field input-md"
                      type="text"
                      value={deleteConfirm}
                      onChange={(e) => setDeleteConfirm(e.target.value)}
                      placeholder="Type DELETE"
                      autoComplete="off"
                      style={{ borderColor: deleteConfirm === 'DELETE' ? 'var(--red)' : undefined }}
                    />
                  </div>
                  <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
                    <motion.button
                      className="settings-export-btn"
                      style={{ flex: 1 }}
                      onClick={() => setShowDeleteModal(false)}
                      whileTap={{ scale: 0.97 }}
                    >
                      Cancel
                    </motion.button>
                    <motion.button
                      className="settings-delete-btn"
                      style={{ flex: 1 }}
                      disabled={deleteConfirm !== 'DELETE'}
                      whileTap={{ scale: 0.97 }}
                      onClick={async () => {
                        try {
                          // Call backend to delete account
                          await authApi.deleteAccount();
                          logout();
                          navigate('/login');
                          toast.success("Account deleted. We're sorry to see you go.");
                        } catch {
                          toast.error('Failed to delete account. Please try again.');
                        }
                      }}
                    >
                      <Trash2 size={14} /> Delete Forever
                    </motion.button>
                  </div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>,
          document.body
        )}
      {/* Avatar Picker Modal */}
      {typeof document !== 'undefined' &&
        createPortal(
          <AnimatePresence>
            {showAvatarPicker && (
              <motion.div
                className="settings-modal-overlay"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setShowAvatarPicker(false)}
              >
                <motion.div
                  className="settings-upgrade-modal"
                  style={{ maxWidth: 500 }}
                  initial={{ opacity: 0, scale: 0.92, y: 20 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.92, y: 20 }}
                  transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
                  onClick={(e) => e.stopPropagation()}
                >
                  <button className="settings-modal-close" onClick={() => setShowAvatarPicker(false)}>
                    <X size={18} />
                  </button>

                  <h3 className="upgrade-modal-title">Ready-to-use Bitmojis</h3>
                  <p className="upgrade-modal-desc">Select a high-class realistic mascot that represents you.</p>

                  <div className="avatar-grid">
                    <motion.button
                      type="button"
                      className="avatar-option upload-trigger"
                      onClick={() => {
                        setShowAvatarPicker(false);
                        document.getElementById('avatar-upload').click();
                      }}
                      whileHover={{ scale: 1.05 }}
                      whileTap={{ scale: 0.95 }}
                    >
                      <div className="upload-placeholder">
                        <Camera size={24} />
                        <span>Upload</span>
                      </div>
                    </motion.button>
                    {BITMOJIS.map((url, i) => (
                      <motion.button
                        key={i}
                        type="button"
                        className={`avatar-option ${form.profileImage === url ? 'active' : ''}`}
                        onClick={() => {
                          setForm((p) => ({ ...p, profileImage: url }));
                          setShowAvatarPicker(false);
                          // Auto-save when selecting an avatar
                          updateProfile.mutate({ profileImage: url });
                        }}
                        whileHover={{ scale: 1.05 }}
                        whileTap={{ scale: 0.95 }}
                      >
                        <img src={url} alt={`Avatar ${i}`} />
                        {form.profileImage === url && (
                          <div className="avatar-check">
                            <Check size={12} />
                          </div>
                        )}
                      </motion.button>
                    ))}
                  </div>

                  <div style={{ marginTop: 24 }}>
                    <button
                      className="settings-export-btn settings-export-secondary"
                      style={{ width: '100%' }}
                      onClick={() => {
                        setForm((p) => ({ ...p, profileImage: '' }));
                        setShowAvatarPicker(false);
                        updateProfile.mutate({ profileImage: '' });
                      }}
                    >
                      Remove current photo
                    </button>
                  </div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>,
          document.body
        )}
    </div>
  );
}
