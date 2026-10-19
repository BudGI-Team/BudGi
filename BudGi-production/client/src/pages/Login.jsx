import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Mail, Lock, Eye, EyeOff, ArrowRight, Cpu, Shield, TrendingUp } from '../lib/icons';
import { useLogin } from '../hooks/useAuth';
import BrandLogo from '../components/ui/BrandLogo';
import toast from 'react-hot-toast';
import './Auth.css';

export default function Login() {
  const [form, setForm] = useState({ email: '', password: '' });
  const [showPass, setShowPass] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const { mutate: login, isPending } = useLogin();

  const handle = (e) => {
    setErrorMessage('');
    setForm((p) => ({ ...p, [e.target.name]: e.target.value }));
  };

  const submit = (e) => {
    e.preventDefault();
    setErrorMessage('');
    if (!form.email || !form.password) {
      setErrorMessage('Please fill in all required fields.');
      toast.error('Please fill in all required fields.');
      return;
    }
    if (form.password.length < 8) {
      setErrorMessage('Password must be at least 8 characters long.');
      toast.error('Password must be at least 8 characters long.');
      return;
    }
    login(
      {
        email: form.email.trim().toLowerCase(),
        password: form.password,
      },
      {
        onError: (err) => {
          setErrorMessage(err.userMessage || 'Invalid email or password. Please try again.');
        },
      },
    );
  };

  return (
    <div className="auth-page">
      {/* Background */}
      <div className="auth-bg">
        <div className="auth-bg-grid" />
        <div className="auth-glow-1" />
        <div className="auth-glow-2" />
      </div>

      {/* Left panel — brand */}
      <div className="auth-left-panel">
        <Link to="/" className="auth-brand-logo">
          <BrandLogo size={32} text="BudGi" />
        </Link>
        <h2 className="auth-tagline">
          Smart money.
          <br />
          <span className="gradient-text">Clear mind.</span>
        </h2>
        <p className="auth-tagline-sub">
          Track every rupee, get autonomous financial insights, and never wonder where your money went again.
        </p>
        <div className="auth-left-features">
          {[
            { icon: Cpu, text: 'BudGi Advisory — real-time treasury intelligence and portfolio health' },
            { icon: TrendingUp, text: 'Predictive Cashflow — 30-day algorithmic expense & runway forecasting' },
            { icon: Shield, text: 'Bank-Grade Encryption — AES-256 vault architecture, zero data sharing' },
          ].map(({ icon: Icon, text }) => (
            <div key={text} className="auth-left-feature">
              <div className="auth-feat-icon">
                <Icon size={14} />
              </div>
              <span>{text}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Right panel — form */}
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

          <div className="auth-header">
            <h1 className="auth-title">Welcome back</h1>
            <p className="auth-sub">Sign in to your financial dashboard</p>
          </div>

          {errorMessage && (
            <div className="auth-alert-error" role="alert">
              <span>{errorMessage}</span>
            </div>
          )}

          <form className="auth-form" onSubmit={submit}>
            <div className="auth-field">
              <label className="auth-label">Email</label>
              <div className="auth-input-wrap">
                <Mail size={15} className="auth-input-icon" />
                <input
                  className="auth-input"
                  type="email"
                  name="email"
                  value={form.email}
                  onChange={handle}
                  placeholder="you@example.com"
                  required
                  autoComplete="email"
                />
              </div>
            </div>

            <div className="auth-field">
              <div className="auth-row">
                <label className="auth-label">Password</label>
                <Link to="/forgot-password" className="auth-forgot">
                  Forgot password?
                </Link>
              </div>
              <div className="auth-input-wrap">
                <Lock size={15} className="auth-input-icon" />
                <input
                  className="auth-input"
                  type={showPass ? 'text' : 'password'}
                  name="password"
                  value={form.password}
                  onChange={handle}
                  placeholder="••••••••"
                  required
                  autoComplete="current-password"
                />
                <button type="button" className="auth-eye" onClick={() => setShowPass((p) => !p)} tabIndex={-1}>
                  {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>

            <motion.button type="submit" className="auth-submit" disabled={isPending} whileTap={{ scale: 0.98 }}>
              {isPending ? (
                <span className="auth-spinner" />
              ) : (
                <>
                  <span>Sign in</span>
                  <ArrowRight size={15} />
                </>
              )}
            </motion.button>
          </form>

          <div className="auth-divider">
            <div className="auth-divider-line" />
            <span>or</span>
            <div className="auth-divider-line" />
          </div>

          <p className="auth-footer-text">
            Don't have an account? <Link to="/register">Create one free</Link>
          </p>
        </motion.div>
      </div>
    </div>
  );
}
