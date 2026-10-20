// src/components/auth/PhoneVerificationModal.jsx
// Production-grade Dual-Anchor Security Verification Modal
import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { createPortal } from 'react-dom';
import { ArrowRight, X, AlertCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import { authApi } from '../../lib/api';
import useStore from '../../store/useStore';
import './PhoneVerificationModal.css';

export default function PhoneVerificationModal({ isOpen, onClose, preAuthToken, _user, onVerified }) {
  const { setAuth } = useStore();
  const [step, setStep] = useState('phone'); // 'phone' | 'otp'
  const [phone, setPhone] = useState('');
  const [digits, setDigits] = useState(['', '', '', '', '', '']);
  const [countdown, setCountdown] = useState(60);
  const [canResend, setCanResend] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  
  const digitRefs = useRef([]);

  // Countdown timer for resend
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

  if (!isOpen || typeof document === 'undefined') return null;

  const handlePhoneSubmit = async (e) => {
    e?.preventDefault();
    setErrorMsg('');
    const cleanDigits = phone.replace(/\D/g, '');
    if (cleanDigits.length !== 10) {
      setErrorMsg('Please enter a valid 10-digit mobile number.');
      return;
    }

    const fullPhone = `+91${cleanDigits}`;
    setIsSending(true);
    try {
      const res = await authApi.sendPhoneOtp(fullPhone, preAuthToken);
      toast.success(res.message || 'OTP sent successfully!');
      setStep('otp');
      setCountdown(60);
      setCanResend(false);
      // Focus first digit after render
      setTimeout(() => {
        digitRefs.current[0]?.focus();
      }, 100);
    } catch (err) {
      setErrorMsg(err.userMessage || 'Failed to send OTP. Please try again.');
      toast.error(err.userMessage || 'Failed to send OTP');
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
    setErrorMsg('');

    // Advance to next box
    if (char && index < 5) {
      digitRefs.current[index + 1]?.focus();
    }

    // Auto submit if all filled
    if (newDigits.every((d) => d !== '') && char) {
      submitOtp(newDigits.join(''));
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
      submitOtp(pasted);
    }
  };

  const submitOtp = async (codeToSubmit) => {
    const otp = codeToSubmit || digits.join('');
    if (otp.length !== 6) {
      setErrorMsg('Please enter the complete 6-digit verification code.');
      return;
    }

    const cleanDigits = phone.replace(/\D/g, '');
    const fullPhone = `+91${cleanDigits}`;

    setIsVerifying(true);
    setErrorMsg('');
    try {
      const res = await authApi.verifyPhoneOtp(fullPhone, otp, preAuthToken);
      const { token, refreshToken, user: authUser } = res.data || {};
      if (!token || !authUser) {
        throw new Error('Verification completed but credentials missing.');
      }

      setAuth(authUser, token);
      if (refreshToken) {
        localStorage.setItem('budgi_refresh_token', refreshToken);
      }

      toast.success('Security verification complete! Welcome to BudGi.');
      if (onVerified) {
        onVerified({ token, refreshToken, user: authUser });
      } else {
        onClose();
        window.location.href = '/app/dashboard';
      }
    } catch (err) {
      setErrorMsg(err.userMessage || 'Invalid OTP code. Please verify and retry.');
      toast.error(err.userMessage || 'OTP verification failed');
    } finally {
      setIsVerifying(false);
    }
  };

  return createPortal(
    <AnimatePresence>
      <motion.div
        className="phone-modal-backdrop"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.2 }}
        onClick={(e) => e.target === e.currentTarget && onClose()}
      >
        <motion.div
          className="phone-modal-container"
          initial={{ opacity: 0, scale: 0.95, y: 16 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 12 }}
          transition={{ type: 'spring', damping: 25, stiffness: 350 }}
        >
          {/* Header */}
          <div className="phone-modal-header">
            <div className="phone-modal-title-wrap">
              <h3 className="phone-modal-title">
                {step === 'phone' ? 'Phone Verification' : 'Enter Verification Code'}
              </h3>
              <p className="phone-modal-subtitle">
                {step === 'phone'
                  ? 'Link your mobile number to secure your BudGi account.'
                  : `Enter the 6-digit code sent to +91 ${phone}.`}
              </p>
            </div>
            <button className="phone-modal-close" onClick={onClose} aria-label="Close">
              <X size={18} />
            </button>
          </div>

          {/* Body */}
          <div className="phone-modal-body">
            {step === 'phone' ? (
              <form onSubmit={handlePhoneSubmit}>
                {errorMsg && (
                  <div className="phone-modal-error">
                    <AlertCircle size={16} />
                    <span>{errorMsg}</span>
                  </div>
                )}

                <div className="phone-input-group">
                  <label>Mobile Number (India +91)</label>
                  <div className="phone-input-row">
                    <span className="phone-country-code">+91</span>
                    <input
                      type="tel"
                      placeholder="98765 43210"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value.replace(/\D/g, '').slice(0, 10))}
                      autoFocus
                      required
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  className={`phone-submit-btn ${isSending ? 'is-loading' : ''}`}
                  disabled={isSending || phone.length < 10}
                >
                  {isSending ? (
                    <div className="auth-btn-loader-wrap">
                      <span className="auth-spinner-ring" />
                      <span className="auth-btn-loader-text">Sending verification code...</span>
                    </div>
                  ) : (
                    <>
                      <span>Send 6-Digit OTP</span>
                      <ArrowRight size={18} />
                    </>
                  )}
                </button>
              </form>
            ) : (
              <div>
                {errorMsg && (
                  <div className="phone-modal-error">
                    <AlertCircle size={16} />
                    <span>{errorMsg}</span>
                  </div>
                )}

                {/* 6 Digit Input Row */}
                <div className="digit-boxes-row" onPaste={handlePaste}>
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
                      className={`digit-box ${digit ? 'filled' : ''}`}
                    />
                  ))}
                </div>

                <button
                  type="button"
                  className={`phone-submit-btn ${isVerifying ? 'is-loading' : ''}`}
                  onClick={() => submitOtp()}
                  disabled={isVerifying || digits.some((d) => !d)}
                >
                  {isVerifying ? (
                    <div className="auth-btn-loader-wrap">
                      <span className="auth-spinner-ring" />
                      <span className="auth-btn-loader-text">Verifying security code...</span>
                    </div>
                  ) : (
                    <>
                      <span>Verify & Access Account</span>
                      <ArrowRight size={18} />
                    </>
                  )}
                </button>

                <div className="phone-resend-row">
                  {canResend ? (
                    <button
                      type="button"
                      className="resend-active-btn"
                      onClick={handlePhoneSubmit}
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
                    <span className="resend-timer-text">Resend code in {countdown}s</span>
                  )}
                  <button
                    type="button"
                    className="change-phone-link"
                    onClick={() => {
                      setStep('phone');
                      setDigits(['', '', '', '', '', '']);
                      setErrorMsg('');
                    }}
                  >
                    Change Number
                  </button>
                </div>
              </div>
            )}
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>,
    document.body
  );
}
