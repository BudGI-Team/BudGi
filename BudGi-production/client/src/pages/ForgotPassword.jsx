import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Smartphone, ArrowRight, ArrowLeft } from '../lib/icons';
import BrandLogo from '../components/ui/BrandLogo';
import './Auth.css';

export default function ForgotPassword() {
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
          Passwordless
          <br />
          <span className="gradient-text">security.</span>
        </h2>
        <p className="auth-tagline-sub">
          BudGi has eliminated vulnerable passwords entirely. Your identity is verified instantly via Google OAuth and cryptographic mobile OTPs.
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

          <div className="auth-header">
            <div className="auth-success-icon" style={{ margin: '0 auto 16px' }}>
              <Smartphone size={32} />
            </div>
            <h1 className="auth-title" style={{ textAlign: 'center' }}>No password needed</h1>
            <p className="auth-sub" style={{ textAlign: 'center', maxWidth: '320px', margin: '8px auto 0' }}>
              BudGi accounts are anchored directly to your verified mobile number and Google account. You don't need a password to access your dashboard.
            </p>
          </div>

          <Link
            to="/login"
            className="auth-submit"
            style={{ textDecoration: 'none', marginTop: '20px', justifyContent: 'center' }}
          >
            <span>Sign in with Mobile or Google</span>
            <ArrowRight size={16} />
          </Link>

          <div className="auth-divider">
            <div className="auth-divider-line" />
            <span>or</span>
            <div className="auth-divider-line" />
          </div>

          <p className="auth-footer-text">
            <Link to="/register">
              <ArrowLeft size={13} style={{ verticalAlign: 'middle', marginRight: 4 }} />
              Create a new account
            </Link>
          </p>
        </motion.div>
      </div>
    </div>
  );
}
