import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  ArrowRight,
  Check,
  Target,
  Wallet,
  CheckCircle2,
  Shield,
  TrendingDown,
  TrendingUp,
  Search,
  Plane,
  Flame,
} from '../lib/icons';
import BrandLogo from '../components/ui/BrandLogo';
import { useUpdateProfile } from '../hooks/useAuth';
import useStore from '../store/useStore';
import './Onboarding.css';

const STEPS = [
  {
    id: 'income',
    title: "What's your monthly income?",
    subtitle: 'BudGi uses this to calculate your savings rate and set smart budget targets.',
    icon: Wallet,
  },
  {
    id: 'goal',
    title: "What's your financial goal?",
    subtitle: "This helps BudGi Copilot give you advice that's actually relevant to you.",
    icon: Target,
  },
  {
    id: 'ready',
    title: "You're All Set!",
    subtitle: 'Your personalized profile is configured and ready for tracking.',
    icon: CheckCircle2,
  },
];

const GOAL_OPTIONS = [
  { value: 'Save ₹1 lakh this year', icon: Target },
  { value: 'Build a 6-month emergency fund', icon: Shield },
  { value: 'Manage daily expenses', icon: TrendingDown },
  { value: 'Invest more consistently', icon: TrendingUp },
  { value: 'Track every rupee I spend', icon: Search },
  { value: 'Retire early', icon: Plane },
];

const INCOME_RANGES = [
  'Under ₹30,000',
  '₹30,000 – ₹60,000',
  '₹60,000 – ₹1,00,000',
  '₹1,00,000 – ₹2,00,000',
  'Above ₹2,00,000',
];

export default function Onboarding() {
  const [step, setStep] = useState(0);
  const [income, setIncome] = useState('');
  const [goal, setGoal] = useState('');
  const navigate = useNavigate();
  const { user, updateUser } = useStore();
  const updateProfile = useUpdateProfile();

  // Parse income range string to a numeric value for the backend
  const parseIncomeToNumber = (range) => {
    if (range.includes('Under')) return 25000;
    if (range.includes('30,000 – ₹60,000')) return 45000;
    if (range.includes('60,000 – ₹1,00,000')) return 80000;
    if (range.includes('1,00,000 – ₹2,00,000')) return 150000;
    if (range.includes('Above')) return 250000;
    return 0;
  };

  const advance = () => {
    if (step === 0 && !income) return;
    if (step === 1 && !goal) return;
    if (step === STEPS.length - 1) {
      // Save income + goal to backend via API
      const numericIncome = parseIncomeToNumber(income);
      updateProfile.mutate({
        monthlyIncome: numericIncome,
        financialGoal: goal,
        onboarded: true,
      });
      // Save locally for immediate UI update
      updateUser({ ...user, onboarded: true, financialGoal: goal, monthlyIncome: numericIncome });
      localStorage.setItem('budgi_onboarded', 'true');
      navigate('/app/dashboard');
      return;
    }
    setStep((s) => s + 1);
  };

  const canAdvance = (step === 0 && income) || (step === 1 && goal) || step === STEPS.length - 1;

  const StepIcon = STEPS[step].icon;

  return (
    <div className="onboarding-page">
      <div className="onboarding-bg">
        <div className="onboarding-orb onboarding-orb-1" />
        <div className="onboarding-orb onboarding-orb-2" />
        <div className="onboarding-grid" />
      </div>

      {/* Logo top-left */}
      <div className="onboarding-logo">
        <BrandLogo size={28} text="BudGi" />
      </div>

      {/* Progress dots */}
      <div className="onboarding-progress">
        {STEPS.map((s, i) => (
          <div
            key={s.id}
            className={`onboarding-dot ${i <= step ? 'onboarding-dot-active' : ''} ${i < step ? 'onboarding-dot-done' : ''}`}
          >
            {i < step && <Check size={9} />}
          </div>
        ))}
      </div>

      {/* Card */}
      <div className="onboarding-center">
        <AnimatePresence mode="wait">
          <motion.div
            key={step}
            className="onboarding-card glass"
            initial={{ opacity: 0, y: 32, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -20, scale: 0.97 }}
            transition={{ duration: 0.42, ease: [0.16, 1, 0.3, 1] }}
          >
            {/* Step badge */}
            <div className="onboarding-step-badge">
              Step {step + 1} of {STEPS.length}
            </div>

            {/* Icon */}
            <motion.div
              className="onboarding-icon"
              initial={{ scale: 0.6, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ duration: 0.4, delay: 0.1 }}
            >
              <StepIcon size={26} />
            </motion.div>

            <h1 className="onboarding-title">{STEPS[step].title}</h1>
            <p className="onboarding-sub">{STEPS[step].subtitle}</p>

            {/* Step 0 — Income */}
            {step === 0 && (
              <div className="onboarding-options">
                {INCOME_RANGES.map((range) => (
                  <motion.button
                    key={range}
                    className={`onboarding-option ${income === range ? 'onboarding-option-selected' : ''}`}
                    onClick={() => setIncome(range)}
                    whileTap={{ scale: 0.98 }}
                  >
                    <span>{range}</span>
                    {income === range && (
                      <div className="onboarding-check">
                        <Check size={12} />
                      </div>
                    )}
                  </motion.button>
                ))}
              </div>
            )}

            {/* Step 1 — Goal */}
            {step === 1 && (
              <div className="onboarding-goals-grid">
                {GOAL_OPTIONS.map((g) => (
                  <motion.button
                    key={g.value}
                    className={`onboarding-goal-card ${goal === g.value ? 'onboarding-goal-selected' : ''}`}
                    onClick={() => setGoal(g.value)}
                    whileTap={{ scale: 0.96 }}
                  >
                    <span className="onboarding-goal-icon">
                      <g.icon size={20} strokeWidth={2} />
                    </span>
                    <span className="onboarding-goal-text">{g.value}</span>
                    {goal === g.value && (
                      <div className="onboarding-goal-check">
                        <Check size={11} />
                      </div>
                    )}
                  </motion.button>
                ))}
              </div>
            )}

            {/* Step 2 — Ready & Treasury Passport Grand Finale */}
            {step === 2 && (
              <div
                className="onboarding-passport-finale"
                style={{ display: 'flex', flexDirection: 'column', gap: '16px', alignItems: 'center', width: '100%' }}
              >
                {/* Golden Laurel Streak Pill */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    background: 'var(--amber-dim)',
                    border: '1px solid var(--amber)',
                    borderRadius: '999px',
                    padding: '6px 16px',
                    color: 'var(--amber)',
                    fontSize: '13px',
                    fontWeight: 700,
                    boxShadow: '0 4px 20px var(--amber-dim)',
                  }}
                >
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <Flame size={14} style={{ color: 'var(--amber)' }} />
                    Day 1 Streak Activated • +50 Karma Points
                  </span>
                </div>

                {/* Titanium Passport Card */}
                <div
                  style={{
                    width: '100%',
                    borderRadius: '24px',
                    background: 'var(--bg-card)',
                    border: '1.5px solid var(--border)',
                    boxShadow: 'var(--shadow-card)',
                    padding: '24px',
                    position: 'relative',
                    overflow: 'hidden',
                    textAlign: 'left',
                  }}
                >
                  <div
                    style={{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      right: 0,
                      height: '60%',
                      background: 'linear-gradient(180deg, var(--accent-dim) 0%, transparent 100%)',
                      pointerEvents: 'none',
                    }}
                  />

                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '20px',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <BrandLogo size={22} text="" />
                      <span style={{ fontSize: '13px', fontWeight: 800, letterSpacing: '2px', color: 'var(--accent)' }}>
                        BUDGI PROFILE
                      </span>
                    </div>
                    <span
                      style={{
                        fontSize: '11px',
                        fontWeight: 700,
                        background: 'var(--accent-dim)',
                        color: 'var(--accent)',
                        padding: '3px 10px',
                        borderRadius: '8px',
                        border: '0.5px solid var(--accent)',
                      }}
                    >
                      MEMBER #{(user?._id || user?.id || '84920').toString().slice(-6).toUpperCase()}
                    </span>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '20px' }}>
                    <div
                      style={{
                        width: '56px',
                        height: '56px',
                        borderRadius: '50%',
                        background: 'var(--accent)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '20px',
                        fontWeight: 800,
                        color: 'var(--bg-base)',
                        border: '2px solid var(--border)',
                      }}
                    >
                      {user?.name ? user.name.slice(0, 2).toUpperCase() : 'BG'}
                    </div>
                    <div>
                      <div style={{ fontSize: '17px', fontWeight: 800, color: 'var(--text-primary)' }}>
                        {user?.name || 'BudGi Member'}
                      </div>
                      <div style={{ fontSize: '13px', color: 'var(--text-secondary)', fontWeight: 500 }}>
                        @{user?.username || (user?.email ? user.email.split('@')[0] : 'explorer')}
                      </div>
                    </div>
                  </div>

                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(3, 1fr)',
                      gap: '12px',
                      borderTop: '1px solid var(--border)',
                      paddingTop: '16px',
                    }}
                  >
                    <div>
                      <div style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 700, letterSpacing: '0.8px' }}>
                        CURRENCY
                      </div>
                      <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text-primary)', marginTop: '2px' }}>
                        {user?.currency || 'INR'}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 700, letterSpacing: '0.8px' }}>
                        MONTHLY RANGE
                      </div>
                      <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--text-primary)', marginTop: '2px' }}>
                        {income || 'Standard'}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontSize: '10px', color: 'var(--text-muted)', fontWeight: 700, letterSpacing: '0.8px' }}>
                        STATUS
                      </div>
                      <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--green)', marginTop: '2px' }}>
                        Verified Member
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* CTA */}
            <motion.button
              className={`onboarding-cta ${canAdvance ? 'onboarding-cta-active' : 'onboarding-cta-disabled'}`}
              onClick={advance}
              disabled={!canAdvance}
              whileHover={canAdvance ? { scale: 1.02 } : {}}
              whileTap={canAdvance ? { scale: 0.98 } : {}}
            >
              {step === STEPS.length - 1 ? 'Go to Dashboard' : 'Continue'}
              <ArrowRight size={16} />
            </motion.button>
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  );
}
