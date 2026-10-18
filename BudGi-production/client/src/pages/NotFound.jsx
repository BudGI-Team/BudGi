import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Home, ArrowRight } from '../lib/icons';
import BrandLogo from '../components/ui/BrandLogo';
import './Auth.css';

export default function NotFound() {
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
          initial={{ opacity: 0, y: 30, scale: 0.97 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
          style={{ textAlign: 'center' }}
        >
          <Link to="/" style={{ display: 'inline-flex', textDecoration: 'none', marginBottom: 24 }}>
            <BrandLogo size={28} text="BudGi" />
          </Link>

          <motion.div
            className="notfound-code"
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.6, type: 'spring', stiffness: 200 }}
          >
            404
          </motion.div>

          <h1 className="auth-title" style={{ fontSize: 22, marginBottom: 8 }}>
            Page not found
          </h1>
          <p className="auth-sub" style={{ maxWidth: 340, margin: '0 auto 24px' }}>
            The page you're looking for doesn't exist or has been moved. Let's get you back on track.
          </p>

          <div style={{ display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
            <Link
              to="/"
              className="auth-submit"
              style={{ textDecoration: 'none', justifyContent: 'center', flex: '0 0 auto', padding: '12px 24px' }}
            >
              <Home size={15} />
              <span>Go home</span>
            </Link>
            <Link
              to="/app/dashboard"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: '12px 24px',
                background: 'var(--bg-elevated)',
                border: '1px solid var(--border-strong)',
                borderRadius: 'var(--radius-lg)',
                color: 'var(--text-primary)',
                fontWeight: 600,
                fontSize: 14,
                textDecoration: 'none',
                transition: 'all 0.2s ease',
              }}
            >
              <span>Dashboard</span>
              <ArrowRight size={15} />
            </Link>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
