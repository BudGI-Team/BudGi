import { useState, useRef, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowRight, Cpu, Shield, TrendingUp, AlertCircle, Mail, Lock, Smartphone } from '../lib/icons';
import BrandLogo from '../components/ui/BrandLogo';
import PhoneVerificationModal from '../components/auth/PhoneVerificationModal';
import { authApi } from '../lib/api';
import useStore from '../store/useStore';
import toast from 'react-hot-toast';
import './Auth.css';

export default function Login() {
  const navigate = useNavigate();
  const { setAuth } = useStore();
  
  const [loginMode, setLoginMode] = useState('phone'); // 'phone' | 'email'
  const [step, setStep] = useState('phone'); // 'phone' | 'otp'
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [digits, setDigits] = useState(['', '', '', '', '', '']);
  const [countdown, setCountdown] = useState(60);
  const [canResend, setCanResend] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [verificationModal, setVerificationModal] = useState({
    isOpen: false,
    preAuthToken: null,
    user: null,
  });

  const googleBtnRef = useRef(null);
  const digitRefs = useRef([]);

  // Countdown timer for resend OTP
  useEffect(() => {
    let timer = null;
    if (step === 'otp' && countdown > 0) {
      timer = setInterval(() => {
        setCountdown((c) => {
          if (c <= 1) {
            setCanResend(true);
            return 0;
          }
          return c - 1;
        });
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [step, countdown]);

  // Seamless Google Sign-In with Dual-Anchor phone verification support
  const handleGoogleResponse = useCallback(async (response) => {
    if (!response.credential) return;
    setErrorMessage('');
    try {
      const res = await authApi.googleLogin(response.credential);
      const data = res?.data || res;

      if (data?.requiresPhoneVerification && data?.preAuthToken) {
        setVerificationModal({
          isOpen: true,
          preAuthToken: data.preAuthToken,
          user: data.user,
        });
        toast('Google verified. Please verify your mobile number to complete authentication.', { icon: '📱' });
        return;
      }

      if (data?.token && data?.user) {
        setAuth(data.user, data.token);
        if (data.refreshToken) {
          localStorage.setItem('budgi_refresh_token', data.refreshToken);
        }
        toast.success(`Welcome back, ${data.user.name?.split(' ')[0] || 'there'}!`);
        navigate('/app/dashboard');
      }
    } catch (err) {
      setErrorMessage(err.userMessage || 'Google Sign-In failed. Please try again.');
      toast.error(err.userMessage || 'Google Sign-In failed');
    }
  }, [navigate, setAuth]);

  const handleEmailLogin = async (e) => {
    e?.preventDefault();
    setErrorMessage('');
    if (!email || !password) {
      setErrorMessage('Please enter both email and password.');
      return;
    }

    setIsSending(true);
    try {
      const res = await authApi.login({ email, password });
      const data = res?.data || res;

      if (data?.requiresPhoneVerification && data?.preAuthToken) {
        setVerificationModal({
          isOpen: true,
          preAuthToken: data.preAuthToken,
          user: data.user,
        });
        toast('Dual-Anchor Security: Please verify your phone number.', { icon: '🛡️' });
        return;
      }

      const { token, refreshToken, user: authUser } = data;
      if (!token || !authUser) {
        throw new Error('Authentication succeeded but session token is missing.');
      }

      setAuth(authUser, token);
      if (refreshToken) {
        localStorage.setItem('budgi_refresh_token', refreshToken);
      }

      toast.success(`Welcome back, ${authUser.name?.split(' ')[0] || 'there'}!`);
      navigate('/app/dashboard');
    } catch (err) {
      const msg = err.userMessage || err.message || 'Login failed. Please check your credentials.';
      setErrorMessage(msg);
      toast.error(msg);
    } finally {
      setIsSending(false);
    }
  };

  const handleModalVerified = ({ token, refreshToken, user: authUser }) => {
    setVerificationModal({ isOpen: false, preAuthToken: null, user: null });
    setAuth(authUser, token);
    if (refreshToken) {
      localStorage.setItem('budgi_refresh_token', refreshToken);
    }
    toast.success(`Welcome back, ${authUser.name?.split(' ')[0] || 'there'}!`);
    navigate('/app/dashboard');
  };

  useEffect(() => {
    const clientId =
      import.meta.env.VITE_GOOGLE_CLIENT_ID ||
      '257509947874-sbr1d7ev97clthmsh5jdc3ajtaqc3q8b.apps.googleusercontent.com';

    const renderGsi = () => {
      if (window.google?.accounts?.id && googleBtnRef.current) {
        try {
          window.google.accounts.id.initialize({
            client_id: clientId,
            callback: handleGoogleResponse,
          });
          window.google.accounts.id.renderButton(googleBtnRef.current, {
            theme: 'filled_black',
            size: 'large',
            text: 'continue_with',
            shape: 'pill',
            width: 320,
          });
        } catch (e) {
          console.warn('GIS render error:', e);
        }
      }
    };

    if (window.google?.accounts?.id) {
      renderGsi();
    } else {
      const interval = setInterval(() => {
        if (window.google?.accounts?.id) {
          renderGsi();
          clearInterval(interval);
        }
      }, 300);
      return () => clearInterval(interval);
    }
  }, [handleGoogleResponse]);

  const handleSendOtp = async (e) => {
    e?.preventDefault();
    setErrorMessage('');
    const clean = phone.replace(/\D/g, '');
    if (clean.length !== 10) {
      setErrorMessage('Please enter a valid 10-digit mobile number.');
      return;
    }

    const fullPhone = `+91${clean}`;
    setIsSending(true);

    try {
      const res = await authApi.phoneLoginRequest(fullPhone);
      toast.success(res.message || `Verification code sent to ${fullPhone}`);
      setStep('otp');
      setCountdown(60);
      setCanResend(false);
      setTimeout(() => {
        digitRefs.current[0]?.focus();
      }, 100);
    } catch (err) {
      console.error('[Auth] Phone login request error:', err);
      const msg = err.userMessage || err.message || 'Failed to send verification SMS.';
      setErrorMessage(msg);
      toast.error(msg);
    } finally {
      setIsSending(false);
    }
  };

  const handleDigitChange = (index, value) => {
    const char = value.slice(-1);
    if (char && !/^\d+$/.test(char)) return;

    const newDigits = [...digits];
    newDigits[index] = char;
    setDigits(newDigits);
    setErrorMessage('');

    if (char && index < 5) {
      digitRefs.current[index + 1]?.focus();
    }

    if (newDigits.every((d) => d !== '') && char) {
      handleVerifyOtp(newDigits.join(''));
    }
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      digitRefs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (e) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (!pasted) return;

    const newDigits = [...digits];
    for (let i = 0; i < 6; i++) {
      newDigits[i] = pasted[i] || '';
    }
    setDigits(newDigits);
    const nextIdx = Math.min(pasted.length, 5);
    digitRefs.current[nextIdx]?.focus();

    if (pasted.length === 6) {
      handleVerifyOtp(pasted);
    }
  };

  const handleVerifyOtp = async (codeToSubmit) => {
    const otp = codeToSubmit || digits.join('');
    if (otp.length !== 6) {
      setErrorMessage('Please enter the full 6-digit verification code.');
      return;
    }

    setIsVerifying(true);
    setErrorMessage('');

    try {
      const clean = phone.replace(/\D/g, '');
      const fullPhone = `+91${clean}`;
      const res = await authApi.phoneLoginVerify(fullPhone, otp);

      const { token, refreshToken, user: authUser } = res.data || res;
      if (!token || !authUser) {
        throw new Error('Authentication succeeded but session token is missing.');
      }

      setAuth(authUser, token);
      if (refreshToken) {
        localStorage.setItem('budgi_refresh_token', refreshToken);
      }

      toast.success(`Welcome back, ${authUser.name?.split(' ')[0] || 'there'}!`);
      navigate('/app/dashboard');
    } catch (err) {
      setErrorMessage(err.userMessage || err.message || 'Invalid verification code. Please check and retry.');
      toast.error(err.userMessage || 'OTP verification failed');
    } finally {
      setIsVerifying(false);
    }
  };

  return (
    <div className="auth-page">
      {/* Background */}
      <div className="auth-bg">
        <div className="auth-bg-grid" />
        <div className="auth-glow-1" />
        <div className="auth-glow-2" />
        {/* Hidden container for Firebase invisible reCAPTCHA */}
        <div id="recaptcha-container" style={{ position: 'absolute', opacity: 0, pointerEvents: 'none' }} />
      </div>

      {/* Left panel — brand */}
      <div className="auth-left-panel">
        <Link to="/" className="auth-brand-logo">
          <BrandLogo size={32} text="BudGi" />
        </Link>
        <h2 className="auth-tagline">
          Smart money.
          <br />
          <span className="gradient-text">Zero hassle.</span>
        </h2>
        <p className="auth-tagline-sub">
          Instant, unhackable verification. Sign in seamlessly with your verified mobile number or Google account.
        </p>
        <div className="auth-left-features">
          {[
            { icon: Cpu, text: 'BudGi Advisory — real-time treasury intelligence and portfolio health' },
            { icon: TrendingUp, text: 'Predictive Cashflow — 30-day algorithmic expense & runway forecasting' },
            { icon: Shield, text: 'Bank-Grade Vault — Zero fake credentials, OTP cryptographic anchoring' },
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
            <h1 className="auth-title">
              {step === 'phone' ? 'Welcome back' : 'Enter verification code'}
            </h1>
            <p className="auth-sub">
              {step === 'phone'
                ? 'Sign in with your mobile number or Google account.'
                : `Enter the 6-digit code sent to +91 ${phone}`}
            </p>
          </div>

          {errorMessage && (
            <div className="auth-alert-error" role="alert" style={{ marginBottom: '16px' }}>
              <AlertCircle size={15} style={{ flexShrink: 0, marginRight: 6 }} />
              <span>{errorMessage}</span>
            </div>
          )}

          {step === 'phone' ? (
            <>
              {/* Google Sign In Container */}
              <div
                className="auth-google-wrapper"
                style={{ display: 'flex', justifyContent: 'center', marginBottom: '16px', minHeight: '44px' }}
              >
                <div ref={googleBtnRef} />
              </div>

              <div className="auth-divider">
                <div className="auth-divider-line" />
                <span>or choose sign in method</span>
                <div className="auth-divider-line" />
              </div>

              {/* Login Method Tabs */}
              <div
                style={{
                  display: 'flex',
                  background: 'rgba(255, 255, 255, 0.04)',
                  border: '1px solid var(--border)',
                  borderRadius: '12px',
                  padding: '4px',
                  marginBottom: '20px',
                  gap: '4px',
                }}
              >
                <button
                  type="button"
                  onClick={() => { setLoginMode('phone'); setErrorMessage(''); }}
                  style={{
                    flex: 1,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '6px',
                    padding: '9px 12px',
                    borderRadius: '8px',
                    border: 'none',
                    background: loginMode === 'phone' ? 'var(--accent, #7C6AF5)' : 'transparent',
                    color: loginMode === 'phone' ? '#fff' : 'var(--text-secondary)',
                    fontWeight: 600,
                    fontSize: '13px',
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                  }}
                >
                  <Smartphone size={15} />
                  <span>Phone OTP</span>
                </button>
                <button
                  type="button"
                  onClick={() => { setLoginMode('email'); setErrorMessage(''); }}
                  style={{
                    flex: 1,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '6px',
                    padding: '9px 12px',
                    borderRadius: '8px',
                    border: 'none',
                    background: loginMode === 'email' ? 'var(--accent, #7C6AF5)' : 'transparent',
                    color: loginMode === 'email' ? '#fff' : 'var(--text-secondary)',
                    fontWeight: 600,
                    fontSize: '13px',
                    cursor: 'pointer',
                    transition: 'all 0.2s ease',
                  }}
                >
                  <Mail size={15} />
                  <span>Email & Password</span>
                </button>
              </div>

              {loginMode === 'phone' ? (
                <form className="auth-form" onSubmit={handleSendOtp}>
                  <div className="auth-field">
                    <label className="auth-label">Registered Mobile Number</label>
                    <div className="auth-phone-row">
                      <span className="auth-phone-prefix">+91</span>
                      <input
                        className="auth-phone-input"
                        type="tel"
                        placeholder="98765 43210"
                        value={phone}
                        onChange={(e) => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))}
                        autoFocus
                        required
                      />
                    </div>
                  </div>

                  <motion.button
                    type="submit"
                    className={`auth-submit ${isSending ? 'is-loading' : ''}`}
                    disabled={isSending || phone.length < 10}
                    whileTap={!isSending && phone.length >= 10 ? { scale: 0.985 } : {}}
                  >
                    {isSending ? (
                      <div className="auth-btn-loader-wrap">
                        <span className="auth-spinner-ring" />
                        <span className="auth-btn-loader-text">Sending verification code...</span>
                      </div>
                    ) : (
                      <>
                        <span>Continue with Phone</span>
                        <ArrowRight size={16} />
                      </>
                    )}
                  </motion.button>
                </form>
              ) : (
                <form className="auth-form" onSubmit={handleEmailLogin}>
                  <div className="auth-field">
                    <label className="auth-label">Email Address</label>
                    <div className="auth-input-wrap">
                      <input
                        className="auth-input"
                        type="email"
                        placeholder="you@example.com"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        autoFocus
                        required
                      />
                      <Mail size={16} className="auth-input-icon" />
                    </div>
                  </div>

                  <div className="auth-field">
                    <div className="auth-row">
                      <label className="auth-label" style={{ marginBottom: 0 }}>Password</label>
                      <Link to="/forgot-password" className="auth-forgot">
                        Forgot password?
                      </Link>
                    </div>
                    <div className="auth-input-wrap">
                      <input
                        className="auth-input"
                        type="password"
                        placeholder="••••••••"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                      />
                      <Lock size={16} className="auth-input-icon" />
                    </div>
                  </div>

                  <motion.button
                    type="submit"
                    className={`auth-submit ${isSending ? 'is-loading' : ''}`}
                    disabled={isSending || !email || !password}
                    whileTap={!isSending && email && password ? { scale: 0.985 } : {}}
                  >
                    {isSending ? (
                      <div className="auth-btn-loader-wrap">
                        <span className="auth-spinner-ring" />
                        <span className="auth-btn-loader-text">Signing in...</span>
                      </div>
                    ) : (
                      <>
                        <span>Sign In with Email</span>
                        <ArrowRight size={16} />
                      </>
                    )}
                  </motion.button>
                </form>
              )}
            </>
          ) : (
            <div>
              {/* 6-Digit input boxes */}
              <div className="auth-digits-container" onPaste={handlePaste}>
                {digits.map((digit, index) => (
                  <input
                    key={index}
                    ref={(el) => (digitRefs.current[index] = el)}
                    type="text"
                    inputMode="numeric"
                    maxLength={1}
                    value={digit}
                    onChange={(e) => handleDigitChange(index, e.target.value)}
                    onKeyDown={(e) => handleKeyDown(index, e)}
                    className={`auth-digit-box ${digit ? 'filled' : ''}`}
                  />
                ))}
              </div>

              <motion.button
                type="button"
                className={`auth-submit ${isVerifying ? 'is-loading' : ''}`}
                onClick={() => handleVerifyOtp()}
                disabled={isVerifying || digits.some((d) => !d)}
                whileTap={!isVerifying && digits.every((d) => d) ? { scale: 0.985 } : {}}
              >
                {isVerifying ? (
                  <div className="auth-btn-loader-wrap">
                    <span className="auth-spinner-ring" />
                    <span className="auth-btn-loader-text">Verifying security code...</span>
                  </div>
                ) : (
                  <>
                    <span>Verify & Sign In</span>
                    <ArrowRight size={16} />
                  </>
                )}
              </motion.button>

              <div className="auth-otp-meta-row">
                {canResend ? (
                  <button
                    type="button"
                    className="auth-resend-btn"
                    onClick={handleSendOtp}
                    disabled={isSending}
                  >
                    {isSending ? (
                      <>
                        <span className="auth-resend-spinner" />
                        <span>Resending...</span>
                      </>
                    ) : (
                      'Resend Code'
                    )}
                  </button>
                ) : (
                  <span className="auth-resend-timer">Resend in {countdown}s</span>
                )}
                <button
                  type="button"
                  className="auth-change-link"
                  onClick={() => {
                    setStep('phone');
                    setDigits(['', '', '', '', '', '']);
                    setErrorMessage('');
                  }}
                >
                  Change mobile number
                </button>
              </div>
            </div>
          )}

          <div className="auth-divider">
            <div className="auth-divider-line" />
            <span>new to budgi?</span>
            <div className="auth-divider-line" />
          </div>

          <p className="auth-footer-text">
            Don't have an account? <Link to="/register">Create an account</Link>
          </p>
        </motion.div>
      </div>

      <PhoneVerificationModal
        isOpen={verificationModal.isOpen}
        onClose={() => setVerificationModal({ isOpen: false, preAuthToken: null, user: null })}
        preAuthToken={verificationModal.preAuthToken}
        _user={verificationModal.user}
        onVerified={handleModalVerified}
      />
    </div>
  );
}
