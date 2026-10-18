import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Mail, Lock, Eye, EyeOff, ArrowRight, User, Cpu, Shield, TrendingUp, Zap, Crown } from '../lib/icons';
import { useRegister } from '../hooks/useAuth';
import BrandLogo from '../components/ui/BrandLogo';
import toast from 'react-hot-toast';
import './Auth.css';

export default function Register() {
  const [searchParams] = useSearchParams();
  const selectedPlan = searchParams.get('plan') || 'free';
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [showPass, setShowPass] = useState(false);
  const { mutate: register, isPending } = useRegister();

  const handle = (e) => setForm((p) => ({ ...p, [e.target.name]: e.target.value }));
  const submit = (e) => {
    e.preventDefault();
    if (!form.name || !form.email || !form.password) {
      toast.error('Please fill in all required fields.');
      return;
    }
    if (form.password.length < 8) {
      toast.error('Password must be at least 8 characters long.');
      return;
    }
    register({
      name: form.name.trim(),
      email: form.email.trim().toLowerCase(),
      password: form.password,
    });
  };

  return (
    <div className="auth-page">
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
          Your money,
          <br />
          <span className="gradient-text">your control.</span>
        </h2>
        <p className="auth-tagline-sub">Join 2,400+ people who save smarter. Free forever. No credit card needed.</p>
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
            <h1 className="auth-title">Create your account</h1>
            <p className="auth-sub">
              {selectedPlan === 'pro'
                ? 'Start your 7-day free Pro trial.'
                : selectedPlan === 'elite'
                  ? 'Start with BudGi Elite.'
                  : 'Free forever. Start in 2 minutes.'}
            </p>
            {selectedPlan !== 'free' && (
              <div className="auth-plan-chip">
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
                  {selectedPlan === 'pro' ? <><Zap size={13} /> Pro</> : <><Crown size={13} /> Elite</>}
                </span>
              </div>
            )}
          </div>

          <form className="auth-form" onSubmit={submit}>
            <div className="auth-field">
              <label className="auth-label">Full Name</label>
              <div className="auth-input-wrap">
                <User size={15} className="auth-input-icon" />
                <input
                  className="auth-input"
                  type="text"
                  name="name"
                  value={form.name}
                  onChange={handle}
                  placeholder="Rahul Sharma"
                  required
                  autoComplete="name"
                />
              </div>
            </div>

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
              <label className="auth-label">Password</label>
              <div className="auth-input-wrap">
                <Lock size={15} className="auth-input-icon" />
                <input
                  className="auth-input"
                  type={showPass ? 'text' : 'password'}
                  name="password"
                  value={form.password}
                  onChange={handle}
                  placeholder="Min. 8 characters"
                  required
                  minLength={8}
                  autoComplete="new-password"
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
                  <span>Create free account</span>
                  <ArrowRight size={15} />
                </>
              )}
            </motion.button>
          </form>

          <p className="auth-terms">
            By signing up, you agree to our{' '}
            <a href="/#terms" target="_blank" rel="noopener noreferrer">
              Terms of Service
            </a>{' '}
            and{' '}
            <a href="/#privacy" target="_blank" rel="noopener noreferrer">
              Privacy Policy
            </a>
            .
          </p>

          <div className="auth-divider">
            <div className="auth-divider-line" />
            <span>already have an account?</span>
            <div className="auth-divider-line" />
          </div>

          <p className="auth-footer-text">
            <Link to="/login">Sign in instead</Link>
          </p>
        </motion.div>
      </div>
    </div>
  );
}
