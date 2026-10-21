import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Mail, ArrowRight, ArrowLeft, CheckCircle, AlertCircle, Smartphone } from '../lib/icons';
import { authApi } from '../lib/api';
import BrandLogo from '../components/ui/BrandLogo';
import toast from 'react-hot-toast';
import './Auth.css';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email) return;
    setError('');
    setLoading(true);

    try {
      await authApi.forgotPassword(email);
      setSubmitted(true);
      toast.success('Password reset link sent to your email.');
    } catch (err) {
      const msg = err.userMessage || err.message || 'Failed to send reset link. Please try again.';
      setError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

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
          Reset your
          <br />
          <span className="gradient-text">access securely.</span>
        </h2>
        <p className="auth-tagline-sub">
          Enter your registered email address and we'll send you an encrypted reset link. You can also sign in passwordlessly using your phone number at any time.
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

          {!submitted ? (
            <>
              <div className="auth-header">
                <h1 className="auth-title">Forgot password?</h1>
                <p className="auth-sub">
                  Enter your email address and we'll send a secure password reset link.
                </p>
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

              <form className="auth-form" onSubmit={handleSubmit}>
                <div className="auth-field">
                  <label className="auth-label">Email Address</label>
                  <div className="auth-input-wrap">
                    <input
                      className="auth-input"
                      type="email"
                      placeholder="you@example.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      required
                      autoFocus
                    />
                    <Mail size={16} className="auth-input-icon" />
                  </div>
                </div>

                <motion.button
                  type="submit"
                  className={`auth-submit ${loading ? 'is-loading' : ''}`}
                  disabled={loading || !email}
                  whileTap={!loading && email ? { scale: 0.985 } : {}}
                >
                  {loading ? (
                    <div className="auth-btn-loader-wrap">
                      <span className="auth-spinner-ring" />
                      <span className="auth-btn-loader-text">Sending reset link...</span>
                    </div>
                  ) : (
                    <>
                      <span>Send Reset Link</span>
                      <ArrowRight size={16} />
                    </>
                  )}
                </motion.button>
              </form>

              <div className="auth-divider">
                <div className="auth-divider-line" />
                <span>or</span>
                <div className="auth-divider-line" />
              </div>

              <Link
                to="/login"
                className="auth-secondary-btn"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px',
                  width: '100%',
                  padding: '11px',
                  borderRadius: '10px',
                  border: '1px solid var(--border)',
                  background: 'var(--surface-2)',
                  color: 'var(--text-primary)',
                  fontSize: '13px',
                  fontWeight: 500,
                  textDecoration: 'none',
                  transition: 'all 0.2s ease',
                }}
              >
                <Smartphone size={15} />
                <span>Sign in with Phone OTP instead</span>
              </Link>
            </>
          ) : (
            <motion.div
              className="auth-success-state"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.4 }}
            >
              <div className="auth-success-icon" style={{ margin: '0 auto 16px' }}>
                <CheckCircle size={36} />
              </div>
              <h2 className="auth-title" style={{ textAlign: 'center' }}>
                Check your inbox
              </h2>
              <p className="auth-sub" style={{ textAlign: 'center', margin: '8px auto 20px', maxWidth: '320px' }}>
                If <strong>{email}</strong> is registered with BudGi, we've sent a password reset link. Please check your spam folder if it doesn't appear within 2 minutes.
              </p>
              <Link
                to="/login"
                className="auth-submit"
                style={{ textDecoration: 'none', justifyContent: 'center' }}
              >
                <span>Return to Login</span>
                <ArrowRight size={16} />
              </Link>
            </motion.div>
          )}

          <p className="auth-footer-text" style={{ marginTop: '24px' }}>
            <Link to="/login">
              <ArrowLeft size={13} style={{ verticalAlign: 'middle', marginRight: 4 }} />
              Back to sign in
            </Link>
          </p>
        </motion.div>
      </div>
    </div>
  );
}
