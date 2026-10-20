import { useState, useRef, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { User, ArrowRight, Cpu, Shield, TrendingUp, AlertCircle } from '../lib/icons';
import BrandLogo from '../components/ui/BrandLogo';
import { authApi } from '../lib/api';
import useStore from '../store/useStore';
import toast from 'react-hot-toast';
import './Auth.css';

export default function Register() {
  const navigate = useNavigate();
  const { setAuth } = useStore();
  
  const [step, setStep] = useState('phone'); // 'phone' | 'otp'
  const [form, setForm] = useState({ name: '', phone: '', username: '' });
  const [digits, setDigits] = useState(['', '', '', '', '', '']);
  const [countdown, setCountdown] = useState(60);
  const [canResend, setCanResend] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

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

  // Seamless Google Registration — Direct access without phone interruptions
  const handleGoogleResponse = useCallback(async (response) => {
    if (!response.credential) return;
    setErrorMessage('');
    try {
      const res = await authApi.googleLogin(response.credential);
      const data = res?.data || res;
      if (data?.token && data?.user) {
        setAuth(data.user, data.token);
        if (data.refreshToken) {
          localStorage.setItem('budgi_refresh_token', data.refreshToken);
        }
        toast.success(`Welcome to BudGi, ${data.user.name?.split(' ')[0] || 'there'}!`);
        navigate('/onboarding');
      }
    } catch (err) {
      setErrorMessage(err.userMessage || 'Google Sign-In failed. Please try again.');
      toast.error(err.userMessage || 'Google Sign-In failed');
    }
  }, [navigate, setAuth]);

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
            text: 'signup_with',
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

  const handleInputChange = (e) => {
    setErrorMessage('');
    const { name, value } = e.target;
    if (name === 'phone') {
      setForm((p) => ({ ...p, phone: value.replace(/\D/g, '').slice(0, 10) }));
    } else {
      setForm((p) => ({ ...p, [name]: value }));
    }
  };

  const handleSendOtp = async (e) => {
    e?.preventDefault();
    setErrorMessage('');

    if (!form.name || form.name.trim().length < 2) {
      setErrorMessage('Please enter your full name.');
      return;
    }
    const cleanPhone = form.phone.replace(/\D/g, '');
    if (cleanPhone.length !== 10) {
      setErrorMessage('Please enter a valid 10-digit mobile number.');
      return;
    }

    const fullPhone = `+91${cleanPhone}`;
    setIsSending(true);

    try {
      const res = await authApi.phoneRegisterRequest({
        phone: fullPhone,
        name: form.name.trim(),
        username: form.username ? form.username.trim() : undefined,
      });

      toast.success(res.message || `Verification code sent to ${fullPhone}`);
      setStep('otp');
      setCountdown(60);
      setCanResend(false);
      setTimeout(() => {
        digitRefs.current[0]?.focus();
      }, 100);
    } catch (err) {
      console.error('[Auth] Phone register request error:', err);
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
      const cleanPhone = form.phone.replace(/\D/g, '');
      const fullPhone = `+91${cleanPhone}`;
      const res = await authApi.phoneRegisterVerify({
        phone: fullPhone,
        otp,
        name: form.name.trim(),
        username: form.username ? form.username.trim() : undefined,
      });

      const { token, refreshToken, user: authUser } = res.data || res;
      if (!token || !authUser) {
        throw new Error('Registration completed but session token missing.');
      }

      setAuth(authUser, token);
      if (refreshToken) {
        localStorage.setItem('budgi_refresh_token', refreshToken);
      }

      toast.success(`Account created! Welcome to BudGi, ${authUser.name?.split(' ')[0] || 'there'}!`);
      navigate('/onboarding');
    } catch (err) {
      setErrorMessage(err.userMessage || err.message || 'Verification failed. Please check the code and retry.');
      toast.error(err.userMessage || 'Verification failed');
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
          Your money,
          <br />
          <span className="gradient-text">your control.</span>
        </h2>
        <p className="auth-tagline-sub">
          Instant, unhackable verification. Sign up seamlessly with your mobile number or Google account.
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
              {step === 'phone' ? 'Create your account' : 'Enter verification code'}
            </h1>
            <p className="auth-sub">
              {step === 'phone'
                ? 'Sign up with your mobile number or Google account.'
                : `Enter the 6-digit code sent to +91 ${form.phone}`}
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
                <span>or register with mobile number</span>
                <div className="auth-divider-line" />
              </div>

              <form className="auth-form" onSubmit={handleSendOtp}>
                <div className="auth-field">
                  <label className="auth-label">Full Name</label>
                  <div className="auth-input-wrap">
                    <User size={15} className="auth-input-icon" />
                    <input
                      className="auth-input"
                      type="text"
                      name="name"
                      value={form.name}
                      onChange={handleInputChange}
                      placeholder="Rahul Sharma"
                      required
                      autoComplete="name"
                      autoFocus
                    />
                  </div>
                </div>

                <div className="auth-field">
                  <label className="auth-label">Mobile Number</label>
                  <div className="auth-phone-row">
                    <span className="auth-phone-prefix">+91</span>
                    <input
                      className="auth-phone-input"
                      type="tel"
                      name="phone"
                      placeholder="98765 43210"
                      value={form.phone}
                      onChange={handleInputChange}
                      required
                    />
                  </div>
                </div>

                <div className="auth-field">
                  <label className="auth-label">
                    Username <span style={{ opacity: 0.6, fontSize: '11px', fontWeight: 400 }}>(optional)</span>
                  </label>
                  <div className="auth-input-wrap">
                    <span style={{ position: 'absolute', left: 14, color: 'var(--text-muted)', fontSize: 14 }}>@</span>
                    <input
                      className="auth-input"
                      style={{ paddingLeft: 34 }}
                      type="text"
                      name="username"
                      value={form.username}
                      onChange={handleInputChange}
                      placeholder="rahul_sharma"
                      autoComplete="username"
                    />
                  </div>
                </div>

                <motion.button
                  type="submit"
                  className={`auth-submit ${isSending ? 'is-loading' : ''}`}
                  disabled={isSending || form.phone.length < 10 || !form.name.trim()}
                  whileTap={!isSending && form.name.trim() && form.phone.length >= 10 ? { scale: 0.985 } : {}}
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
                    <span className="auth-btn-loader-text">Creating your account...</span>
                  </div>
                ) : (
                  <>
                    <span>Verify & Create Account</span>
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

          <p className="auth-terms" style={{ marginTop: '18px' }}>
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
            <span>already registered?</span>
            <div className="auth-divider-line" />
          </div>

          <p className="auth-footer-text">
            Already have an account? <Link to="/login">Sign in instead</Link>
          </p>
        </motion.div>
      </div>
    </div>
  );
}
