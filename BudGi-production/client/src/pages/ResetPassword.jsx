import { useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Lock, Eye, EyeOff, ArrowRight, CheckCircle, AlertCircle } from '../lib/icons';
import { authApi } from '../lib/api';
import BrandLogo from '../components/ui/BrandLogo';
import toast from 'react-hot-toast';
import './Auth.css';

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setError('');

    if (password.length < 8) {
      return setError('Password must be at least 8 characters');
    }
    if (password !== confirm) {
      return setError('Passwords do not match');
    }

    setLoading(true);
    try {
      await authApi.resetPassword(token, password);
      setDone(true);
      toast.success('Password reset successful!');
    } catch (err) {
      setError(err.userMessage || 'Invalid or expired reset link');
    }
    setLoading(false);
  };

  if (!token) {
    return (
      <div className="auth-page">
        <div className="auth-bg">
          <div className="auth-bg-grid" />
          <div className="auth-glow-1" />
          <div className="auth-glow-2" />
        </div>
        <div className="auth-right-panel" style={{ gridColumn: '1 / -1' }}>
          <motion.div
            className="auth-card"
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
          >
            <div className="auth-success-icon" style={{ color: 'var(--red)' }}>
              <AlertCircle size={36} />
            </div>
            <h2 className="auth-title" style={{ textAlign: 'center' }}>
              Invalid link
            </h2>
            <p className="auth-sub" style={{ textAlign: 'center' }}>
              This password reset link is invalid. Please request a new one.
            </p>
            <Link
              to="/forgot-password"
              className="auth-submit"
              style={{ marginTop: 20, textDecoration: 'none', justifyContent: 'center' }}
            >
              Request new link
            </Link>
          </motion.div>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-bg">
        <div className="auth-bg-grid" />
        <div className="auth-glow-1" />
        <div className="auth-glow-2" />
      </div>

      <div className="auth-left-panel">
        <Link to="/" className="auth-brand-logo">
          <BrandLogo size={32} text="BudGi" />
        </Link>
        <h2 className="auth-tagline">
          Choose a new
          <br />
          <span className="gradient-text">password.</span>
        </h2>
        <p className="auth-tagline-sub">
          Make it strong — at least 8 characters with a mix of letters, numbers, and symbols.
        </p>
      </div>

      <div className="auth-right-panel">
        <motion.div
          className="auth-card"
          initial={{ opacity: 0, y: 30, scale: 0.97 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        >
          <Link to="/" className="auth-logo-mobile">
            <BrandLogo size={28} text="BudGi" />
          </Link>

          {!done ? (
            <>
              <div className="auth-header">
                <h1 className="auth-title">Set new password</h1>
                <p className="auth-sub">Enter your new password below.</p>
              </div>

              {error && (
                <motion.div
                  className="auth-error-banner"
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                >
                  <AlertCircle size={14} />
                  <span>{error}</span>
                </motion.div>
              )}

              <form className="auth-form" onSubmit={submit}>
                <div className="auth-field">
                  <label className="auth-label">New Password</label>
                  <div className="auth-input-wrap">
                    <Lock size={15} className="auth-input-icon" />
                    <input
                      className="auth-input"
                      type={showPass ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="Min. 8 characters"
                      required
                      minLength={8}
                      autoComplete="new-password"
                      autoFocus
                    />
                    <button type="button" className="auth-eye" onClick={() => setShowPass((p) => !p)} tabIndex={-1}>
                      {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
                    </button>
                  </div>
                </div>

                <div className="auth-field">
                  <label className="auth-label">Confirm Password</label>
                  <div className="auth-input-wrap">
                    <Lock size={15} className="auth-input-icon" />
                    <input
                      className="auth-input"
                      type={showPass ? 'text' : 'password'}
                      value={confirm}
                      onChange={(e) => setConfirm(e.target.value)}
                      placeholder="Re-enter password"
                      required
                      minLength={8}
                      autoComplete="new-password"
                    />
                  </div>
                </div>

                {/* Password strength indicator */}
                <div className="auth-password-strength">
                  {[
                    { label: '8+ chars', ok: password.length >= 8 },
                    { label: 'Number', ok: /\d/.test(password) },
                    { label: 'Uppercase', ok: /[A-Z]/.test(password) },
                    { label: 'Match', ok: password.length > 0 && password === confirm },
                  ].map((r) => (
                    <span key={r.label} className={`strength-rule ${r.ok ? 'strength-ok' : ''}`}>
                      {r.ok ? '✓' : '○'} {r.label}
                    </span>
                  ))}
                </div>

                <motion.button type="submit" className="auth-submit" disabled={loading} whileTap={{ scale: 0.98 }}>
                  {loading ? (
                    <span className="auth-spinner" />
                  ) : (
                    <>
                      <span>Reset password</span>
                      <ArrowRight size={15} />
                    </>
                  )}
                </motion.button>
              </form>
            </>
          ) : (
            <motion.div
              className="auth-success-state"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.4 }}
            >
              <div className="auth-success-icon">
                <CheckCircle size={36} />
              </div>
              <h2 className="auth-title" style={{ textAlign: 'center' }}>
                Password updated!
              </h2>
              <p className="auth-sub" style={{ textAlign: 'center' }}>
                Your password has been reset. You can now sign in with your new password.
              </p>
              <motion.button
                className="auth-submit"
                style={{ marginTop: 20 }}
                onClick={() => navigate('/login')}
                whileTap={{ scale: 0.98 }}
              >
                <span>Go to login</span>
                <ArrowRight size={15} />
              </motion.button>
            </motion.div>
          )}
        </motion.div>
      </div>
    </div>
  );
}
