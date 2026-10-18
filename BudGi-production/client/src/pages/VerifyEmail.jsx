import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { CheckCircle, AlertCircle, Loader } from '../lib/icons';
import { authApi } from '../lib/api';
import BrandLogo from '../components/ui/BrandLogo';
import './Auth.css';

export default function VerifyEmail() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [status, setStatus] = useState('loading'); // loading | success | error
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!token) {
      setStatus('error');
      setMessage('Missing verification token.');
      return;
    }

    authApi
      .verifyEmail(token)
      .then(() => {
        setStatus('success');
        setMessage('Your email has been verified! You can now use all BudGi features.');
      })
      .catch((err) => {
        setStatus('error');
        setMessage(err.userMessage || 'Invalid or expired verification link.');
      });
  }, [token]);

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

          {status === 'loading' && (
            <motion.div className="auth-success-state" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
              <div className="auth-success-icon auth-spin-icon">
                <Loader size={36} />
              </div>
              <h2 className="auth-title">Verifying your email...</h2>
              <p className="auth-sub">Please wait a moment.</p>
            </motion.div>
          )}

          {status === 'success' && (
            <motion.div
              className="auth-success-state"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.4 }}
            >
              <div className="auth-success-icon">
                <CheckCircle size={36} />
              </div>
              <h2 className="auth-title">Email verified!</h2>
              <p className="auth-sub" style={{ maxWidth: 340, margin: '0 auto' }}>
                {message}
              </p>
              <Link
                to="/app/dashboard"
                className="auth-submit"
                style={{ marginTop: 20, textDecoration: 'none', justifyContent: 'center' }}
              >
                Go to Dashboard
              </Link>
            </motion.div>
          )}

          {status === 'error' && (
            <motion.div
              className="auth-success-state"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.4 }}
            >
              <div className="auth-success-icon" style={{ color: 'var(--red)' }}>
                <AlertCircle size={36} />
              </div>
              <h2 className="auth-title">Verification failed</h2>
              <p className="auth-sub" style={{ maxWidth: 340, margin: '0 auto' }}>
                {message}
              </p>
              <Link
                to="/login"
                className="auth-submit"
                style={{ marginTop: 20, textDecoration: 'none', justifyContent: 'center' }}
              >
                Back to login
              </Link>
            </motion.div>
          )}
        </motion.div>
      </div>
    </div>
  );
}
