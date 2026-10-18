import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Mail, ArrowRight, ArrowLeft, CheckCircle } from '../lib/icons';
import { authApi } from '../lib/api';
import BrandLogo from '../components/ui/BrandLogo';
import toast from 'react-hot-toast';
import './Auth.css';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const cleanEmail = email.trim().toLowerCase();
    if (!cleanEmail) return toast.error('Please enter your email');
    setLoading(true);
    try {
      await authApi.forgotPassword(cleanEmail);
      setSent(true);
    } catch (err) {
      toast.error(err.userMessage || 'Something went wrong');
    }
    setLoading(false);
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
          Forgot your
          <br />
          <span className="gradient-text">password?</span>
        </h2>
        <p className="auth-tagline-sub">
          No worries — we'll send you a link to reset it. Check your email after submitting.
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

          {!sent ? (
            <>
              <div className="auth-header">
                <h1 className="auth-title">Reset password</h1>
                <p className="auth-sub">Enter the email you used to sign up.</p>
              </div>

              <form className="auth-form" onSubmit={submit}>
                <div className="auth-field">
                  <label className="auth-label">Email</label>
                  <div className="auth-input-wrap">
                    <Mail size={15} className="auth-input-icon" />
                    <input
                      className="auth-input"
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="you@example.com"
                      required
                      autoComplete="email"
                      autoFocus
                    />
                  </div>
                </div>

                <motion.button type="submit" className="auth-submit" disabled={loading} whileTap={{ scale: 0.98 }}>
                  {loading ? (
                    <span className="auth-spinner" />
                  ) : (
                    <>
                      <span>Send reset link</span>
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
                Check your email
              </h2>
              <p className="auth-sub" style={{ textAlign: 'center', maxWidth: 320, margin: '0 auto' }}>
                If <strong>{email}</strong> is registered, we've sent a password reset link. Check your inbox (and spam
                folder).
              </p>
              <button
                className="auth-submit"
                style={{ marginTop: 20 }}
                onClick={() => {
                  setSent(false);
                  setEmail('');
                }}
              >
                <span>Try another email</span>
              </button>
            </motion.div>
          )}

          <div className="auth-divider">
            <div className="auth-divider-line" />
            <span>or</span>
            <div className="auth-divider-line" />
          </div>

          <p className="auth-footer-text">
            <Link to="/login">
              <ArrowLeft size={13} style={{ verticalAlign: 'middle', marginRight: 4 }} />
              Back to login
            </Link>
          </p>
        </motion.div>
      </div>
    </div>
  );
}
