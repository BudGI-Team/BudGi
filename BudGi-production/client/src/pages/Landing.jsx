import { Link } from 'react-router-dom';
import { createPortal } from 'react-dom';
import {
  motion,
  AnimatePresence,
  useMotionValue,
  useSpring,
} from 'framer-motion';
import { useRef, useState, useEffect } from 'react';
import {
  Code2,
  Star,
  Sun,
  Moon,
  ArrowRight,
  ArrowUpRight,
  TrendingUp,
  Bell,
  Check,
  Cpu,
  Zap,
  Target,
  BarChart3,
  Heart,
  Globe,
  Mail,
  Rocket,
  ShieldCheck,
  Download,
  Shield,
  ChevronDown,
  ShoppingCart,
  PieChart,
  Bot,
  Camera,
  Smartphone,
  Layout,
  X,
  Play,
  Pause,
  Maximize2,
  Sparkles,
  Wifi,
  RotateCcw,
} from '../lib/icons';
import ModernFeatures from '../components/ui/ModernFeatures';
import '../components/ui/ModernFeatures.css';
import BrandLogo from '../components/ui/BrandLogo';
import HowItWorksPipeline from '../components/ui/HowItWorksPipeline';
import useStore from '../store/useStore';
import budgiLogo from '../logo/budgi-logo-new.png';
import toast from 'react-hot-toast';
import DownloadModal from '../components/ui/DownloadModal';
import '../components/ui/DownloadModal.css';
import './Landing.css';

const fadeUp = {
  hidden: { opacity: 0, y: 28 },
  show: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.55, ease: [0.16, 1, 0.3, 1] },
  },
};

function MagneticBtn({ className = '', children, to, href, onClick, style, target }) {
  const ref = useRef(null);
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const springX = useSpring(x, { stiffness: 120, damping: 20 });
  const springY = useSpring(y, { stiffness: 120, damping: 20 });
  const handleMouseMove = (e) => {
    if (!ref.current) return;
    const r = ref.current.getBoundingClientRect();
    x.set((e.clientX - (r.left + r.width / 2)) * 0.3);
    y.set((e.clientY - (r.top + r.height / 2)) * 0.3);
    ref.current.style.setProperty('--mx', `${((e.clientX - r.left) / r.width) * 100}%`);
    ref.current.style.setProperty('--my', `${((e.clientY - r.top) / r.height) * 100}%`);
  };
  const handleMouseLeave = () => {
    x.set(0);
    y.set(0);
  };
  const Tag = to ? Link : href ? 'a' : 'button';
  return (
    <motion.div style={{ x: springX, y: springY }} className="magnetic-wrap">
      <Tag
        ref={ref}
        to={to}
        href={href}
        onClick={onClick}
        target={target}
        rel={target === '_blank' ? 'noopener noreferrer' : undefined}
        className={`btn ${className}`}
        style={style}
        onMouseMove={handleMouseMove}
        onMouseLeave={handleMouseLeave}
      >
        {children}
      </Tag>
    </motion.div>
  );
}

const TESTIMONIALS = [
  {
    name: 'Priya Sharma',
    role: 'Product Designer · Bengaluru',
    text: 'I used to wonder where my money went every month. Now BudGi tells me by the 10th — before I even have to ask.',
    avatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=64&h=64&fit=crop',
    saved: '₹11,400/mo',
    tag: 'Top saver',
  },
  {
    name: 'Arjun Mehta',
    role: 'SDE II · Pune',
    text: "BudGi predicted I'd overshoot my food budget — four days in advance. It was exactly right. That's unreal.",
    avatar: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=64&h=64&fit=crop',
    saved: '₹8,200/mo',
    tag: 'Power user',
  },
  {
    name: 'Kavya Nair',
    role: 'Freelancer · Chennai',
    text: 'Finally a finance app that gives real answers, not generic advice. Ask anything, get insights from your actual data.',
    avatar: 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=64&h=64&fit=crop',
    saved: '₹6,800/mo',
    tag: 'AI fan',
  },
  {
    name: 'Rohit Verma',
    role: 'Startup Founder · Delhi',
    text: "Tracking personal + business expenses used to be chaos. BudGi's multi-account feature saved my sanity.",
    avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=64&h=64&fit=crop',
    saved: '₹15,600/mo',
    tag: 'Elite user',
  },
  {
    name: 'Sneha Iyer',
    role: 'CA · Mumbai',
    text: "As a CA, I'm picky about finance tools. BudGi's reporting is genuinely impressive for a consumer app.",
    avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=64&h=64&fit=crop',
    saved: '₹9,300/mo',
    tag: 'Verified',
  },
  {
    name: 'Vikram Joshi',
    role: 'Marketing Lead · Hyderabad',
    text: 'The UPI auto-categorisation is spot on. No manual tagging, just clean double-entry spending data from day one.',
    avatar: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=64&h=64&fit=crop',
    saved: '₹7,100/mo',
    tag: 'Early adopter',
  },
];
const PLANS = [
  {
    name: 'Free',
    price: '₹0',
    period: '/forever',
    desc: 'Get started with no commitment.',
    features: ['100 transactions/mo', 'Basic budgets', 'Spending overview', 'CSV export'],
    cta: 'Start for free',
    highlight: false,
  },
  {
    name: 'Pro',
    price: '₹299',
    period: '/month',
    desc: 'For serious money management.',
    features: [
      'Unlimited transactions',
      'Actionable AI (Create Budgets/Goals)',
      'In-Chat Data Visualization',
      'Smart alerts & Predictions',
      'Priority support',
    ],
    cta: 'Try Pro — free',
    highlight: true,
  },
  {
    name: 'Elite',
    price: '₹799',
    period: '/month',
    desc: 'For families and power users.',
    features: ['Everything in Pro', 'Multi-account', 'Custom categories', 'Advanced reports', 'Dedicated advisor'],
    cta: 'Go Elite',
    highlight: false,
  },
];
const APP_FEATURES = [
  { icon: Cpu, text: 'Real-time telemetry' },
  { icon: Bell, text: 'Instant budget alerts' },
  { icon: Camera, text: 'AI Receipt Scanning' },
  { icon: BarChart3, text: 'Live spending charts' },
  { icon: ShieldCheck, text: 'Biometric secure login' },
  { icon: Zap, text: 'One-tap add transaction' },
];

const AVATARS = [
  'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=64&h=64&fit=crop',
  'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=64&h=64&fit=crop',
  'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=64&h=64&fit=crop',
  'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=64&h=64&fit=crop',
  'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=64&h=64&fit=crop',
];

const LEGAL_DOCS = {
  privacy: {
    title: 'Data Sovereignty & Privacy Policy',
    subtitle: 'Zero data sales. Local-first ledger caching with AES-256 server encryption.',
    sections: [
      {
        heading: '1. Zero Data Monetization',
        body: 'We never sell, rent, or syndicate your financial records, merchant receipts, or cashflow histories to third-party ad brokers or credit bureaus. Your data remains strictly yours.',
      },
      {
        heading: '2. Cryptographic Security & Transit',
        body: 'All client-to-server traffic is mandated over TLS 1.3 with HSTS enabled. Tokens are cryptographically signed with short-lived JWTs and stored securely in protected storage.',
      },
      {
        heading: '3. Data Retention & Full Erasure',
        body: 'You maintain absolute right of erasure. Requesting account deactivation immediately flushes your database records, indexed vectors, and associated telemetry within 24 hours.',
      },
    ],
  },
  terms: {
    title: 'Terms of Service & Operational Guidelines',
    subtitle: 'Clear, transparent rules governing your use of the BudGi ledger application.',
    sections: [
      {
        heading: '1. Ledger Integrity',
        body: 'BudGi provides automated budgeting, categorisation, and predictive cashflow analytics based on user input and imported statement feeds. You are responsible for verifying personal financial decisions.',
      },
      {
        heading: '2. Fair Use & API Quotas',
        body: 'Automated scraping or abusive rate requests beyond nominal UI interactions are rate-limited to preserve system availability and deterministic latency for all users.',
      },
      {
        heading: '3. Availability SLA',
        body: 'We strive for 99.9% uptime across our microservices and WebSocket pipelines. Scheduled maintenance windows are communicated ahead of deployment.',
      },
    ],
  },
  security: {
    title: 'Security Architecture & Hardening',
    subtitle: 'Engineered defense-in-depth across our web client, mobile apps, and backend APIs.',
    sections: [
      {
        heading: '1. Authentication & Token Lifecycle',
        body: 'Stateless JWT tokens with strict HMAC-SHA256 signatures, cryptographic entropy checks, and automatic token expiry mitigation.',
      },
      {
        heading: '2. Isolation & Double-Entry Principles',
        body: 'Transactions are committed atomically via multi-document ACID transactions, preventing balance drift and split-brain ledger states.',
      },
      {
        heading: '3. Threat Modeling & Vulnerability Scans',
        body: 'Regular dependency audits, strict CORS policies, helmet HTTP header sanitization, and parameterized database queries prevent injection attacks.',
      },
    ],
  },
  cookies: {
    title: 'Session & Cookie Policy',
    subtitle: 'Minimal, functional storage strictly required for authentication and state persistence.',
    sections: [
      {
        heading: '1. Essential Storage Only',
        body: 'We do not employ cross-site tracking cookies, third-party marketing beacons, or fingerprinting scripts. Storage is strictly functional (theme preference, authentication session).',
      },
      {
        heading: '2. Local Storage & Cache',
        body: 'Client state is cached in browser localStorage for offline availability, dark/light theme state, and low-latency navigation.',
      },
      {
        heading: '3. Granular Control',
        body: 'Logging out clears all active session tokens and local application state instantly.',
      },
    ],
  },
};

export default function Landing() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  const MOBILE_NAV_ITEMS = [
    { id: 'features', label: 'Architecture', sub: 'Double-entry core & real-time telemetry', icon: Code2 },
    { id: 'mobile-app', label: 'Android App', sub: 'BudGi in your pocket', icon: Smartphone },
    { id: 'how', label: 'How it works', sub: 'End-to-end ledger pipeline', icon: Layout },
    { id: 'pricing', label: 'Pricing', sub: 'Choose your plan', icon: PieChart },
  ];

  // Lock body scroll when menu is open
  useEffect(() => {
    if (isMenuOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }
    return () => {
      document.body.style.overflow = 'unset';
    };
  }, [isMenuOpen]);

  const navVariants = {
    hidden: { opacity: 0, x: 20 },
    show: (i) => ({
      opacity: 1,
      x: 0,
      transition: {
        delay: 0.1 + i * 0.08,
        duration: 0.5,
        ease: [0.16, 1, 0.3, 1],
      },
    }),
    exit: (i) => ({
      opacity: 0,
      x: -10,
      transition: {
        delay: i * 0.05,
        duration: 0.3,
      },
    }),
  };


  const { theme, toggleTheme, token, user } = useStore();
  const isAuthenticated = Boolean(token && user);
  const isDark = theme === 'dark';
  const [legalModal, setLegalModal] = useState(null);
  const [showDownloadModal, setShowDownloadModal] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };
    handleScroll();
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  useEffect(() => {
    const handleHash = () => {
      const hash = window.location.hash.replace('#', '');
      if (hash && LEGAL_DOCS[hash]) {
        setLegalModal(hash);
      }
    };
    handleHash();
    window.addEventListener('hashchange', handleHash);
    return () => window.removeEventListener('hashchange', handleHash);
  }, []);

  return (
    <>
      <div className="landing">
        {/* NAVBAR */}
        <nav className={`landing-nav ${isScrolled ? 'scrolled' : ''}`}>
          <div className="landing-nav-inner">
            <motion.div
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3 }}
            >
              <Link
                to={isAuthenticated ? '/app/dashboard' : '/'}
                style={{ textDecoration: 'none', display: 'flex' }}
                title={isAuthenticated ? 'Return to Dashboard' : 'BudGi Home'}
              >
                <BrandLogo className="landing-logo" />
              </Link>
            </motion.div>
            <div className="landing-nav-links">
              <a href="#features">Features</a>
              <a href="#mobile-app">App</a>
              <a href="#how">How it works</a>
              <a href="#pricing">Pricing</a>
              <a href="#dev-note">Dev Note</a>
            </div>
            <div className="landing-nav-actions">
              <motion.button
                className="nav-theme-btn"
                onClick={toggleTheme}
                aria-label="Toggle theme"
                whileTap={{ scale: 0.85 }}
              >
                <AnimatePresence mode="wait" initial={false}>
                  {isDark ? (
                    <motion.span
                      key="sun"
                      initial={{ rotate: -90, opacity: 0, scale: 0.4 }}
                      animate={{ rotate: 0, opacity: 1, scale: 1 }}
                      exit={{ rotate: 90, opacity: 0, scale: 0.4 }}
                      transition={{ duration: 0.22 }}
                    >
                      <Sun size={15} />
                    </motion.span>
                  ) : (
                    <motion.span
                      key="moon"
                      initial={{ rotate: 90, opacity: 0, scale: 0.4 }}
                      animate={{ rotate: 0, opacity: 1, scale: 1 }}
                      exit={{ rotate: -90, opacity: 0, scale: 0.4 }}
                      transition={{ duration: 0.22 }}
                    >
                      <Moon size={15} />
                    </motion.span>
                  )}
                </AnimatePresence>
              </motion.button>
              {isAuthenticated ? (
                <>
                  <button
                    className="nav-link-btn hide-on-mobile"
                    onClick={() => setShowDownloadModal(true)}
                    title="Download BudGi for Android"
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', background: 'transparent', border: 'none', cursor: 'pointer', color: 'inherit' }}
                  >
                    <Smartphone size={15} />
                    <span>Android APK</span>
                  </button>
                  <MagneticBtn to="/app/dashboard" className="btn-primary nav-cta-btn hide-on-mobile">
                    Dashboard <ArrowRight size={14} />
                  </MagneticBtn>
                </>
              ) : (
                <>
                  <button
                    className="nav-link-btn hide-on-mobile"
                    onClick={() => setShowDownloadModal(true)}
                    title="Download BudGi for Android"
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', background: 'transparent', border: 'none', cursor: 'pointer', color: 'inherit' }}
                  >
                    <Smartphone size={15} />
                    <span>Android APK</span>
                  </button>
                  <Link to="/login" className="nav-link-btn hide-on-mobile">
                    Sign in
                  </Link>
                  <MagneticBtn to="/register" className="btn-primary nav-cta-btn hide-on-mobile">
                    Get started <ArrowRight size={14} />
                  </MagneticBtn>
                </>
              )}
              <motion.button
                className={`mobile-menu-btn ${isMenuOpen ? 'active' : ''}`}
                onClick={() => setIsMenuOpen(!isMenuOpen)}
                whileTap={{ scale: 0.9 }}
                aria-label="Toggle menu"
              >
                <div className="hamburger-box">
                  <div className="hamburger-inner" />
                </div>
              </motion.button>
            </div>
          </div>
        </nav>

        <AnimatePresence>
          {isMenuOpen && (
            <motion.div
              className="mobile-nav-overlay"
              initial={{ opacity: 0, clipPath: 'circle(0% at 90% 5%)' }}
              animate={{ opacity: 1, clipPath: 'circle(150% at 90% 5%)' }}
              exit={{ opacity: 0, clipPath: 'circle(0% at 90% 5%)' }}
              transition={{ duration: 0.75, ease: [0.16, 1, 0.3, 1] }}
            >
              <div className="mobile-nav-mesh" />

              <div className="mobile-nav-links">
                {MOBILE_NAV_ITEMS.map((item, i) => (
                  <motion.a
                    key={item.id}
                    href={`#${item.id}`}
                    onClick={() => setIsMenuOpen(false)}
                    custom={i}
                    variants={navVariants}
                    initial="hidden"
                    animate="show"
                    exit="exit"
                    className="mobile-nav-item"
                  >
                    <div className="mn-item-icon">
                      <item.icon size={20} />
                    </div>
                    <div className="mn-item-content">
                      <span className="mn-item-label">{item.label}</span>
                      <span className="mn-item-sub">{item.sub}</span>
                    </div>
                    <ArrowRight size={16} className="mn-item-arrow" />
                  </motion.a>
                ))}
              </div>

              <motion.div
                className="mobile-nav-footer"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.4 }}
              >
                <div className="mobile-nav-divider" />
                <div className="mobile-nav-badge">
                  <Zap size={14} className="text-accent" />
                  <span>v3.0.0 Stable Build</span>
                </div>
                <div className="mobile-nav-actions">
                  {isAuthenticated ? (
                    <MagneticBtn to="/app/dashboard" className="btn-hero-primary mobile-nav-cta">
                      Go to Dashboard <ArrowUpRight size={18} />
                    </MagneticBtn>
                  ) : (
                    <MagneticBtn to="/register" className="btn-hero-primary mobile-nav-cta">
                      Get Started Free <ArrowUpRight size={18} />
                    </MagneticBtn>
                  )}

                  <div className="mobile-nav-bottom-row">
                    <button className="mobile-theme-toggle" onClick={toggleTheme}>
                      {isDark ? <Sun size={18} /> : <Moon size={18} />}
                      <span>{isDark ? 'Light' : 'Dark'} Mode</span>
                    </button>
                    <div className="mobile-social-row">
                      <a
                        href="https://budgi.app"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="mobile-social-link"
                        aria-label="Official Website"
                      >
                        <Globe size={20} />
                      </a>
                      <a
                        href="mailto:support@budgi.com"
                        className="mobile-social-link"
                        aria-label="Developer Support Email"
                      >
                        <Mail size={20} />
                      </a>
                    </div>
                  </div>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>

        <section className="hero">
          <div className="hero-bg">
            <div className="hero-gradient-orb hero-orb-1" />
            <div className="hero-gradient-orb hero-orb-2" />
            <div className="hero-gradient-orb hero-orb-3" />
            <div className="hero-grid-overlay" />
            <div className="hero-noise" />
          </div>

          <div className="hero-inner">
            {/* LEFT: copy */}
            <div className="hero-left">
              <motion.div
                className="hero-pill"
                initial={{ opacity: 0, scale: 0.8, y: -10 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.05 }}
              >
                <span className="hero-pill-dot" />
                <Zap size={12} />
                <span>Autonomous Ledger Engine · Built for India</span>
                <span className="hero-pill-badge">v3.0</span>
              </motion.div>

              <motion.h1
                className="hero-heading"
                initial={{ opacity: 0, y: 48 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{
                  duration: 0.85,
                  delay: 0.12,
                  ease: [0.16, 1, 0.3, 1],
                }}
              >
                <span className="hero-line-1">Understand exactly where</span>
                <br />
                <span className="gradient-text">your money goes</span>
              </motion.h1>

              <motion.p
                className="hero-sub"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.3 }}
              >
                BudGi reads your SMS, tracks your UPI, and categorizes your spending automatically. No manual entry
                required. <strong> Web + Android. Always on.</strong>
              </motion.p>

              <motion.div
                className="hero-cta-row"
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.45 }}
              >
                <MagneticBtn to={isAuthenticated ? '/app/dashboard' : '/register'} className="btn-hero-primary">
                  {isAuthenticated ? 'Open Dashboard' : 'Start for free'} <ArrowUpRight size={17} />
                </MagneticBtn>
                <MagneticBtn onClick={() => setShowDownloadModal(true)} className="btn-hero-ghost">
                  <AndroidSVG size={18} /> Android app
                </MagneticBtn>
              </motion.div>

              <motion.div
                className="hero-social-proof"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.5, delay: 0.65 }}
              >
                <div className="hero-avatars">
                  {AVATARS.map((url, i) => (
                    <img
                      key={i}
                      src={url}
                      alt="User"
                      className="hero-avatar-img"
                      style={{ marginLeft: i > 0 ? -12 : 0, zIndex: 5 - i }}
                    />
                  ))}
                </div>
                <div className="hero-stars">
                  {[...Array(5)].map((_, i) => (
                    <Star key={i} size={12} fill="var(--amber)" stroke="none" />
                  ))}
                </div>
                <span className="hero-proof-text">2,400+ users already in control</span>
              </motion.div>

              <motion.div
                className="hero-partners"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.8 }}
              >
                <span className="hero-partners-label">Works with your apps</span>
                <div className="hero-partner-logos">
                  {['Swiggy', 'Zomato', 'UPI', 'Paytm', 'Uber'].map((b) => (
                    <span key={b} className="hero-partner-chip">
                      {b}
                    </span>
                  ))}
                </div>
              </motion.div>
            </div>

            {/* RIGHT: visual */}
            <motion.div
              className="hero-right"
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{
                duration: 0.9,
                delay: 0.35,
                ease: [0.22, 1, 0.36, 1],
              }}
            >
              <div className="hero-visual-wrap">
                <div className="hero-visual-glow" />
                <motion.div
                  className="hero-dashboard-card"
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ duration: 0.8, delay: 0.2, ease: 'easeOut' }}
                  style={{}}
                >
                  <HeroDashboard onOpenDownload={() => setShowDownloadModal(true)} />
                </motion.div>
              </div>
            </motion.div>
          </div>
        </section>

        {/* TRUST MARQUEE */}
        <section className="trust-strip">
          <div className="trust-marquee-track">
            <div className="trust-marquee-inner">
              {[
                'Bank-Level Security',
                '256-bit Encryption',
                'ISO 27001 Certified',
                'Privacy First',
                'No Data Sharing',
                '100% Secure',
                'Auto-Sync',
                'Bank-Level Security',
                '256-bit Encryption',
                'ISO 27001 Certified',
                'Privacy First',
                'No Data Sharing',
                '100% Secure',
                'Auto-Sync',
              ].map((t, i) => (
                <div key={i} className="trust-logo-item">
                  <span className="trust-logo-dot" />
                  {t}
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* MODERN FEATURES SECTION */}
        <section className="section features-section" id="features">
          <div className="section-inner">
            <motion.div
              className="section-header"
              initial={{ opacity: 0, y: 40 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-100px' }}
              transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
            >
              <div className="section-pill">
                <Code2 size={12} /> Architecture & Core Engine
              </div>
              <h2 className="section-heading">
                Built like a financial ledger,
                <br />
                <span className="gradient-text">engineered for human speed</span>
              </h2>
              <p className="section-sub">
                No black-box guesses or bloated AI gimmicks. BudGi pairs double-entry accounting primitives with
                deterministic rule engines and zero-knowledge privacy for unbreakable financial clarity.
              </p>
            </motion.div>
          </div>

          <ModernFeatures />
        </section>

        {/* ANDROID APP */}
        <section className="section section-alt" id="mobile-app">
          <div className="section-inner">
            <div className="app-section-grid">
              <motion.div
                className="app-phone-wrap"
                initial={{ opacity: 0, x: -60, rotateY: 15 }}
                whileInView={{ opacity: 1, x: 0, rotateY: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.85, ease: [0.16, 1, 0.3, 1] }}
              >
                <PhoneMockup />
                <div className="app-orbit-badge app-orbit-1">
                  <Star size={12} fill="var(--amber)" stroke="none" /> 4.9 Rating
                </div>
                <div className="app-orbit-badge app-orbit-2">
                  <Download size={11} /> 10K+ Downloads
                </div>
                <div className="app-orbit-badge app-orbit-3">
                  <ShieldCheck size={11} /> Secure
                </div>
              </motion.div>
              <motion.div
                className="app-section-copy"
                variants={fadeUp}
                initial="hidden"
                whileInView="show"
                viewport={{ once: true }}
              >
                <div className="section-pill">
                  <AndroidSVG size={12} /> Android App
                </div>
                <h2 className="section-heading" style={{ textAlign: 'left', marginTop: 16 }}>
                  BudGi is now
                  <br />
                  <span className="gradient-text">in your pocket</span>
                </h2>
                <p className="section-sub" style={{ textAlign: 'left' }}>
                  The same powerful AI. The same real-time alerts. Now in a native Android app built for India's UPI
                  generation. Wherever you are, whenever you need it.
                </p>
                <div className="app-features-grid">
                  {APP_FEATURES.map(({ icon: Icon, text }) => (
                    <div key={text} className="app-feature-chip glass-lite">
                      <Icon size={14} style={{ color: 'var(--accent-bright)', flexShrink: 0 }} />
                      <span>{text}</span>
                    </div>
                  ))}
                </div>
                <div className="app-download-row">
                  <MagneticBtn
                    onClick={() => setShowDownloadModal(true)}
                    className="btn-primary app-download-btn"
                    id="download-android-native-btn"
                  >
                    <PlayStoreSVG />
                    <div className="app-btn-text">
                      <span className="app-btn-sub">Native APK · v1.2.0</span>
                      <span className="app-btn-main">Download Android Native</span>
                    </div>
                  </MagneticBtn>
                  <div className="app-coming-info">
                    <div className="app-rating">
                      {[...Array(5)].map((_, i) => (
                        <Star key={i} size={13} fill="var(--amber)" stroke="none" />
                      ))}
                      <span>4.9 / 5</span>
                    </div>
                    <div className="app-downloads">10,000+ downloads · Free</div>
                  </div>
                </div>
              </motion.div>
            </div>
          </div>
        </section>

        {/* HOW IT WORKS ARCHITECTURE PIPELINE */}
        <HowItWorksPipeline />

        {/* AI COPILOT */}
        <section className="section section-alt">
          <div className="section-inner">
            <div className="ai-highlight">
              <div className="ai-highlight-left">
                <motion.div variants={fadeUp} initial="hidden" whileInView="show" viewport={{ once: true }}>
                  <div className="section-pill">BudGi Copilot</div>
                  <h2 className="section-heading" style={{ textAlign: 'left', marginTop: 16 }}>
                    Your 24/7
                    <br />
                    <span className="gradient-text">personal CFO</span>
                  </h2>
                  <p className="section-sub" style={{ textAlign: 'left' }}>
                    Ask anything in plain English. Get answers from your real data — not generic advice, but insights
                    drawn from your actual spending.
                  </p>
                  <div className="ai-feature-list">
                    {[
                      'Monthly pattern analysis across Swiggy, Zomato & UPI',
                      'Budget overspend prediction — days in advance',
                      'Savings opportunities surfaced automatically',
                      'Month-over-month comparison',
                      'Personal financial coaching',
                    ].map((item) => (
                      <div key={item} className="ai-feature-item">
                        <Check size={14} /> {item}
                      </div>
                    ))}
                  </div>
                  <MagneticBtn to={isAuthenticated ? '/app/ai' : '/register'} className="btn-primary" style={{ marginTop: 28, display: 'inline-flex' }}>
                    {isAuthenticated ? 'Open BudGi Copilot' : 'Try BudGi Copilot'} <ArrowRight size={15} />
                  </MagneticBtn>
                </motion.div>
              </div>
              <motion.div
                className="ai-highlight-right"
                initial={{ opacity: 0, x: 40 }}
                whileInView={{ opacity: 1, x: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.7, delay: 0.2 }}
              >
                <AIChatMockup />
              </motion.div>
            </div>
          </div>
        </section>

        {/* TESTIMONIALS — Premium marquee redesign */}
        <section className="section section-alt" id="testimonials">
          <div className="section-inner">
            <motion.div
              className="section-header"
              variants={fadeUp}
              initial="hidden"
              whileInView="show"
              viewport={{ once: true }}
            >
              <div className="section-pill">
                <Star size={11} fill="var(--amber)" stroke="none" /> Wall of love
              </div>
              <h2 className="section-heading">
                Loved by <span className="gradient-text">thousands</span> across India.
              </h2>
              <p className="section-sub">Real people, real savings. Here's what our community has to say.</p>
            </motion.div>

            {/* Human-Centric Bento Grid */}
            <div className="testi-bento-grid">
              <motion.div
                className="testi-bento-card testi-bento-large glass-hardware"
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6 }}
              >
                <div className="testi-featured-glow" />
                <div className="testi-quote-mark">&ldquo;</div>
                <p className="testi-bento-text">
                  BudGi completely changed how I think about money. The AI doesn&rsquo;t just track — it
                  <strong> actually understands</strong> my spending habits and gives advice that works. I&rsquo;ve
                  saved ₹15,600 this month alone.
                </p>
                <div className="testi-bento-author">
                  <img src={TESTIMONIALS[3].avatar} alt="Rohit Verma" className="testi-bento-avatar-img" />
                  <div>
                    <div className="testi-bento-name">Rohit Verma</div>
                    <div className="testi-bento-role">Startup Founder · Delhi</div>
                  </div>
                  <div className="testi-bento-saved">
                    <TrendingUp size={14} />
                    ₹15,600/mo saved
                  </div>
                </div>
              </motion.div>

              <div className="testi-bento-col">
                {[TESTIMONIALS[0], TESTIMONIALS[1]].map((t, i) => (
                  <motion.div
                    key={i}
                    className="testi-bento-card glass-hardware"
                    initial={{ opacity: 0, x: 20 }}
                    whileInView={{ opacity: 1, x: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.5, delay: 0.2 + i * 0.15 }}
                  >
                    <div className="testi-stars-sm">
                      {[...Array(5)].map((_, s) => (
                        <Star key={s} size={11} fill="var(--amber)" stroke="none" />
                      ))}
                    </div>
                    <p className="testi-bento-text-sm">&ldquo;{t.text}&rdquo;</p>
                    <div className="testi-bento-author-sm">
                      <img src={t.avatar} alt={t.name} className="testi-bento-avatar-sm" />
                      <div>
                        <div className="testi-bento-name-sm">{t.name}</div>
                        <div className="testi-bento-role-sm">{t.role}</div>
                      </div>
                    </div>
                  </motion.div>
                ))}
              </div>
            </div>
          </div>
        </section>

        {/* PRICING */}
        <section className="section section-alt" id="pricing">
          <div className="section-inner">
            <motion.div
              className="section-header"
              variants={fadeUp}
              initial="hidden"
              whileInView="show"
              viewport={{ once: true }}
            >
              <div className="section-pill">Pricing</div>
              <h2 className="section-heading">Simple pricing. No surprises.</h2>
              <p className="section-sub">Start free. Upgrade when you're ready.</p>
            </motion.div>
            <div className="pricing-grid">
              {PLANS.map((plan, i) => (
                <motion.div
                  key={plan.name}
                  className={`pricing-card ${plan.highlight ? 'pricing-card-highlight' : 'glass-lite'}`}
                  initial={{ opacity: 0, y: 28 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.55, delay: i * 0.1 }}
                >
                  {plan.highlight && <div className="pricing-popular">⚡ Most popular</div>}
                  <div className="pricing-name">{plan.name}</div>
                  <div className="pricing-price">
                    {plan.price}
                    <span className="pricing-period">{plan.period}</span>
                  </div>
                  <p className="pricing-desc">{plan.desc}</p>
                  <ul className="pricing-features">
                    {plan.features.map((f) => (
                      <li key={f}>
                        <Check size={14} className="check-icon" /> {f}
                      </li>
                    ))}
                  </ul>
                  <MagneticBtn
                    to={`/register?plan=${plan.name.toLowerCase()}`}
                    className={plan.highlight ? 'btn-primary' : 'btn-ghost'}
                    style={{
                      marginTop: 'auto',
                      width: '100%',
                      justifyContent: 'center',
                    }}
                  >
                    {plan.cta}
                  </MagneticBtn>
                </motion.div>
              ))}
            </div>
          </div>
        </section>

        {/* DEV NOTE */}
        <section className="section section-alt" id="dev-note">
          <div className="section-inner">
            <motion.div
              className="section-header"
              variants={fadeUp}
              initial="hidden"
              whileInView="show"
              viewport={{ once: true }}
            >
              <div className="section-pill">
                <Heart size={11} fill="var(--red)" stroke="none" /> From the team
              </div>
              <h2 className="section-heading">
                Built with conviction.
                <br />
                <span className="gradient-text">Shipped with care.</span>
              </h2>
              <p className="section-sub">
                We're a small team obsessed with helping India's working professionals take control of every rupee.
              </p>
            </motion.div>

            <div className="devnote-grid">
              {/* Left — Origin story card */}
              <motion.div
                className="devnote-story-card"
                initial={{ opacity: 0, y: 32 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6 }}
              >
                <div className="devnote-story-topline" />
                <div className="devnote-story-inner">
                  <div className="devnote-quote-mark" aria-hidden="true">
                    &ldquo;
                  </div>
                  <p className="devnote-quote-text">
                    BudGi started from a personal frustration: salary would arrive, and by month-end, clarity was
                    nowhere to be found. We kept asking ourselves &mdash;{' '}
                    <strong>can this feel simple enough for family and useful enough for working professionals?</strong>
                  </p>
                  <p className="devnote-story-body">
                    Most finance tools are either too corporate or too generic for Indian spending patterns. BudGi
                    understands real categories like rent transfers, UPI splits, food delivery spikes, and recurring
                    subscriptions &mdash; without feeling like a spreadsheet.
                  </p>
                  <div className="devnote-sign">
                    <div className="devnote-sign-avatars">
                      {[
                        'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=64&h=64&fit=crop',
                        'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=64&h=64&fit=crop',
                        'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=64&h=64&fit=crop',
                      ].map((url, i) => (
                        <img
                          key={i}
                          src={url}
                          alt="Founder"
                          className="devnote-sign-avatar-img"
                          style={{ marginLeft: i > 0 ? -12 : 0, zIndex: 3 - i }}
                        />
                      ))}
                    </div>
                    <div>
                      <div className="devnote-sign-name">Team BudGi</div>
                      <div className="devnote-sign-role">Founders &middot; Bengaluru, India</div>
                    </div>
                  </div>
                </div>
              </motion.div>

              {/* Right — Mission pillars */}
              <div className="devnote-pillars">
                {[
                  {
                    icon: Target,
                    title: 'Our promise',
                    desc: "If BudGi helps you make smarter money decisions every single week, we're doing the right thing.",
                  },
                  {
                    icon: Zap,
                    title: 'Ship fast',
                    desc: 'We read every piece of feedback and ship improvements fast. Your voice shapes the product.',
                  },
                  {
                    icon: ShieldCheck,
                    title: 'Privacy first',
                    desc: 'Zero third-party data sharing. Your financial data never leaves our encrypted servers.',
                  },
                ].map(({ icon: Icon, title, desc }, i) => (
                  <motion.div
                    key={title}
                    className="devnote-pillar-card"
                    initial={{ opacity: 0, x: 24 }}
                    whileInView={{ opacity: 1, x: 0 }}
                    viewport={{ once: true }}
                    transition={{ duration: 0.5, delay: i * 0.1 }}
                  >
                    <div className="devnote-pillar-icon">
                      <Icon size={18} />
                    </div>
                    <div>
                      <h3 className="devnote-pillar-title">{title}</h3>
                      <p className="devnote-pillar-desc">{desc}</p>
                    </div>
                  </motion.div>
                ))}
              </div>
            </div>

            {/* Tech stack + links bar */}
            <motion.div
              className="devnote-bottom-bar"
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: 0.2 }}
            >
              <div className="devnote-stack-row">
                <span className="devnote-stack-label">Built with</span>
                {['React 18', 'FastAPI', 'MongoDB', 'Groq AI', 'Framer Motion', 'Node.js'].map((t) => (
                  <span key={t} className="devnote-stack-chip">
                    {t}
                  </span>
                ))}
              </div>
              <div className="devnote-links-row">
                <a href="https://github.com" target="_blank" rel="noopener noreferrer" className="devnote-link-pill">
                  <Globe size={14} /> Website
                </a>
                <a href="mailto:hello@budgi.app" className="devnote-link-pill">
                  <Mail size={14} /> hello@budgi.app
                </a>
                <a href="https://twitter.com" target="_blank" rel="noopener noreferrer" className="devnote-link-pill">
                  <Rocket size={14} /> Updates
                </a>
              </div>
            </motion.div>
          </div>
        </section>

        {/* SAVINGS COUNTER */}
        <section className="section">
          <div className="section-inner">
            <SavingsCounter />
          </div>
        </section>

        {/* FAQ */}
        <section className="section">
          <div className="section-inner">
            <motion.div
              className="section-header"
              initial={{ opacity: 0, y: 32 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6 }}
            >
              <div className="section-pill">
                <ShieldCheck size={11} /> Common questions
              </div>
              <h2 className="section-heading">Everything you need to know</h2>
              <p className="section-sub">Still on the fence? Here are the answers.</p>
            </motion.div>
            <FAQSection />
          </div>
        </section>

        {/* CTA BANNER */}
        <section className="section cta-section">
          <div className="section-inner">
            <motion.div
              className="hardware-cta-banner"
              initial={{ opacity: 0, y: 40 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.7 }}
            >
              <div className="hardware-cta-glow" />
              <div className="hardware-cta-inner">
                <div className="hardware-cta-content">
                  <div className="hardware-cta-pill">
                    <ShieldCheck size={11} /> Production-ready ledger & telemetry
                  </div>
                  <h2 className="hardware-cta-heading">
                    Your money deserves
                    <br />
                    <span className="gradient-text">better than guesswork.</span>
                  </h2>
                  <p className="hardware-cta-sub">
                    Start free with complete data ownership. Real-time bank sync, double-entry audit trails, and zero
                    compromises on privacy or speed.
                  </p>
                  <div className="hardware-cta-trust">
                    <div className="hc-trust-item">
                      <Shield size={14} /> Bank-grade security
                    </div>
                    <div className="hc-trust-item">
                      <Check size={14} /> Setup in 2 mins
                    </div>
                  </div>
                </div>
                <div className="hardware-cta-actions">
                  <div className="hardware-cta-card">
                    <div className="hc-card-chip" />
                    <div className="hc-card-bottom-branding">BudGi</div>
                  </div>
                  <div className="hardware-btn-group">
                    <MagneticBtn to={isAuthenticated ? '/app/dashboard' : '/register'} className="btn-hero-primary hc-main-btn">
                      {isAuthenticated ? 'Open Dashboard' : 'Start for free'} <ArrowUpRight size={17} />
                    </MagneticBtn>
                    <MagneticBtn onClick={() => setShowDownloadModal(true)} className="btn-hero-ghost hc-ghost-btn">
                      <AndroidSVG size={16} /> Get Android app
                    </MagneticBtn>
                  </div>
                </div>
              </div>
            </motion.div>
          </div>
        </section>

        {/* FOOTER */}
        <footer className="landing-footer">
          <div className="footer-top">
            <div className="footer-inner-top">
              <div className="footer-brand">
                <BrandLogo size={26} className="landing-logo" />
                <p className="footer-tagline">
                  Your money, your control.
                  <br />
                  Built for India 🇮🇳
                </p>
                <div className="footer-social">
                  <a
                    href="https://budgi.app"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="footer-social-btn"
                    aria-label="Website"
                  >
                    <Globe size={16} />
                  </a>
                  <a
                    href="https://twitter.com"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="footer-social-btn"
                    aria-label="Updates"
                  >
                    <Rocket size={16} />
                  </a>
                  <a href="#pricing" className="footer-social-btn" aria-label="Pricing">
                    <TrendingUp size={16} />
                  </a>
                  <a href="mailto:hello@budgi.app" className="footer-social-btn" aria-label="Email">
                    <Mail size={16} />
                  </a>
                </div>
              </div>
              <div className="footer-col">
                <div className="footer-col-title">Product</div>
                <a href="#features">Features</a>
                <a href="#pricing">Pricing</a>
                <a href="#mobile-app">Android App</a>
                <Link to="/login">Sign in</Link>
                <Link to="/register">Get started</Link>
              </div>
              <div className="footer-col">
                <div className="footer-col-title">Resources</div>
                <a href="#how">Architecture & Pipeline</a>
                <a href="#features">Deterministic Core</a>
                <a href="#dev-note">Engineering Notes</a>
                <a
                  href="#status"
                  onClick={(e) => {
                    e.preventDefault();
                    toast.success('All BudGi services operational — 99.98% uptime', { icon: '🟢' });
                  }}
                >
                  System Status
                </a>
              </div>
              <div className="footer-col">
                <div className="footer-col-title">Legal & Security</div>
                <button className="footer-link-btn" onClick={() => setLegalModal('privacy')}>
                  Privacy & Data Sovereignty
                </button>
                <button className="footer-link-btn" onClick={() => setLegalModal('terms')}>
                  Terms of Service
                </button>
                <button className="footer-link-btn" onClick={() => setLegalModal('security')}>
                  Security Architecture
                </button>
                <button className="footer-link-btn" onClick={() => setLegalModal('cookies')}>
                  Session & Cookie Policy
                </button>
              </div>
            </div>
          </div>
          <div className="footer-bottom">
            <div className="footer-bottom-inner">
              <span className="footer-copy">© {new Date().getFullYear()} BudGi. Engineered for India 🇮🇳</span>
              <div className="footer-made">
                Crafted with <Heart size={12} fill="var(--red)" stroke="none" /> by Team BudGi
              </div>
              <div className="footer-bottom-right">
                <span className="footer-version">v3.0.0 Stable</span>
                <button className="footer-theme-btn" onClick={toggleTheme} aria-label="Toggle theme">
                  {isDark ? <Sun size={14} /> : <Moon size={14} />} {isDark ? 'Light mode' : 'Dark mode'}
                </button>
              </div>
            </div>
          </div>
        </footer>
      </div>

      {/* LEGAL & ARCHITECTURE MODAL */}
      <AnimatePresence>
        {legalModal && LEGAL_DOCS[legalModal] && (
          <motion.div
            className="legal-modal-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setLegalModal(null)}
          >
            <motion.div
              className="legal-modal"
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="legal-modal-header">
                <div>
                  <span className="legal-modal-tag">LEGAL ARCHITECTURE</span>
                  <h3 className="legal-modal-title">{LEGAL_DOCS[legalModal].title}</h3>
                  <p className="legal-modal-sub">{LEGAL_DOCS[legalModal].subtitle}</p>
                </div>
                <button className="legal-modal-close" onClick={() => setLegalModal(null)} aria-label="Close modal">
                  <X size={18} />
                </button>
              </div>
              <div className="legal-modal-body">
                {LEGAL_DOCS[legalModal].sections.map((sec, idx) => (
                  <div key={idx} className="legal-section-item">
                    <h4 className="legal-section-h">{sec.heading}</h4>
                    <p className="legal-section-p">{sec.body}</p>
                  </div>
                ))}
              </div>
              <div className="legal-modal-footer">
                <span className="legal-updated">Verified Architecture · October 2026</span>
                <button
                  className="btn-primary"
                  style={{ padding: '8px 20px', fontSize: 13, borderRadius: 'var(--radius-md)' }}
                  onClick={() => setLegalModal(null)}
                >
                  Close
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <DownloadModal isOpen={showDownloadModal} onClose={() => setShowDownloadModal(false)} />
    </>
  );
}

const HERO_CHAPTERS = [
  {
    id: 'ingest',
    tab: '01 Inflow',
    icon: Zap,
    badge: 'LIVE LEDGER · 3.4MS',
    title: 'Autonomous Ingestion Pipeline',
    hud: '⚡ Intercepted HDFC UPI alert: ₹340.00 Swiggy debited. Balanced & settled in 3.4ms with 0% drift.',
  },
  {
    id: 'budgets',
    tab: '02 Budgets',
    icon: PieChart,
    badge: 'DOUBLE-ENTRY ENVELOPES',
    title: 'Mathematical Spending Limits',
    hud: '📊 Dining budget at 85% with 9 days left. Recommended safe daily velocity: ₹72/day.',
  },
  {
    id: 'runway',
    tab: '03 Runway',
    icon: TrendingUp,
    badge: '60-DAY BURN FORECAST',
    title: 'Algorithmic Cashflow Runway',
    hud: '🎯 64 days safe runway projected. Monthly burn velocity: ₹1,397/day with surplus fully intact.',
  },
  {
    id: 'copilot',
    tab: '04 Copilot',
    icon: Bot,
    badge: 'GROQ FINANCIAL LLM',
    title: 'In-Ledger Copilot Intelligence',
    hud: '🧠 "You have ₹18,400 discretionary buffer remaining after rent and SIP. 3 subscriptions flagged."',
  },
];

function HeroDashboard({ onOpenDownload: _onOpenDownload }) {
  const [activeIdx, setActiveIdx] = useState(0);
  const [isPlaying, setIsPlaying] = useState(true);
  const [isHovered, setIsHovered] = useState(false);
  const [isPhoneExpanded, setIsPhoneExpanded] = useState(false);
  const [heroDeviceMode, setHeroDeviceMode] = useState('desktop'); // 'desktop' | 'mobile' on <=860px

  // Chapter 01: Inflow interactive state
  const [inflowFilter, setInflowFilter] = useState('all');
  const [hasExtraInflow, setHasExtraInflow] = useState(false);

  // Chapter 02: Budgets interactive state
  const [isRebalanced, setIsRebalanced] = useState(false);

  // Chapter 03: Runway interactive state
  const [runwayScenario, setRunwayScenario] = useState('standard'); // 'standard' | 'lean' | 'scale'
  const [hoveredBarIndex, setHoveredBarIndex] = useState(null);

  // Chapter 04: Copilot interactive state
  const [activePromptIdx, setActivePromptIdx] = useState(0);
  const [actionStaged, setActionStaged] = useState(false);

  // Auto-advance chapters if not paused and not hovered
  useEffect(() => {
    if (!isPlaying || isHovered || isPhoneExpanded) return;
    const timer = setInterval(() => {
      setActiveIdx((prev) => (prev + 1) % HERO_CHAPTERS.length);
    }, 4800);
    return () => clearInterval(timer);
  }, [isPlaying, isHovered, isPhoneExpanded]);

  // Dismiss expanded phone on ESC key
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') setIsPhoneExpanded(false);
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Lock body scroll when phone is popped out
  useEffect(() => {
    if (isPhoneExpanded) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isPhoneExpanded]);

  const current = HERO_CHAPTERS[activeIdx];

  // Dynamic ledger transactions for Chapter 01
  const rawTxns = [
    ...(hasExtraInflow
      ? [
        {
          id: 'extra-1',
          label: 'Zerodha Dividend',
          cat: 'Investment Account',
          amount: '+₹1,450',
          tag: 'IMPS · Settled',
          color: 'var(--green)',
          type: 'income',
          isNew: true,
        },
      ]
      : []),
    {
      id: 'tx-1',
      label: 'Swiggy',
      cat: 'Food & Dining',
      amount: '-₹340',
      tag: 'UPI · Instant',
      color: 'var(--red)',
      type: 'upi',
    },
    {
      id: 'tx-2',
      label: 'Salary Credit',
      cat: 'Income Account',
      amount: '+₹55,000',
      tag: 'HDFC · Settled',
      color: 'var(--accent)',
      type: 'income',
    },
    {
      id: 'tx-3',
      label: 'Uber Premier',
      cat: 'Transport',
      amount: '-₹210',
      tag: 'UPI · Verified',
      color: 'var(--amber)',
      type: 'upi',
    },
    {
      id: 'tx-4',
      label: 'House Rent',
      cat: 'Housing Fixed',
      amount: '-₹18,000',
      tag: 'Auto-Debit',
      color: 'var(--blue)',
      type: 'fixed',
    },
  ];

  const filteredTxns = rawTxns.filter((tx) => {
    if (inflowFilter === 'all') return true;
    return tx.type === inflowFilter;
  });

  // Dynamic runway values
  const runwayData = {
    standard: { days: 64, burn: '₹1,397/d', grade: 'Grade A', bars: [35, 50, 42, 68, 55, 62, 48, 75, 58, 70, 85, 92] },
    lean: { days: 92, burn: '₹940/d', grade: 'Grade A+', bars: [35, 50, 42, 68, 55, 62, 48, 75, 58, 80, 95, 98] },
    scale: { days: 45, burn: '₹1,980/d', grade: 'Grade B+', bars: [35, 50, 42, 68, 55, 62, 48, 75, 58, 62, 70, 78] },
  }[runwayScenario];

  // Copilot conversation presets
  const copilotPresets = [
    {
      q: 'Can I buy the ₹12,000 monitor without missing my SIP goal?',
      reply: 'Yes! After reserving your ₹15,000 SIP and rent commitments, you have an ₹18,400 discretionary buffer remaining this month. Your 60-day safe runway will remain protected.',
      actionLabel: '⚡ Lock SIP ₹15k',
    },
    {
      q: 'Where did my money go this week?',
      reply: '₹4,280 on Dining (85% of monthly allowance), ₹1,450 on Uber commutes, and ₹780 in recurring cloud subscriptions. Dining velocity is pacing 18% high.',
      actionLabel: '📊 Inspect Dining Pace',
    },
    {
      q: 'How can I save ₹5,000 extra before next month?',
      reply: 'You have 3 unused subscriptions costing ₹780/mo. Shifting 2 restaurant orders to home meals recovers ~₹2,400. Pausing non-essential cloud compute hits ₹5,200 easily.',
      actionLabel: '✂️ Pause 3 Unused Subs',
    },
  ];

  const activeCopilot = copilotPresets[activePromptIdx];

  return (
    <div
      className="hero-dual-stage"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      {/* ── Mobile View Switcher (Visible on Screens <= 860px) ── */}
      <div className="hero-device-toggle-bar" role="group" aria-label="Device Preview Switcher">
        <button
          type="button"
          className={`hero-device-toggle-btn ${heroDeviceMode === 'desktop' ? 'active' : ''}`}
          onClick={() => setHeroDeviceMode('desktop')}
          aria-label="Switch to Web Dashboard view"
        >
          <Layout size={12} />
          <span>Web Dashboard</span>
        </button>
        <button
          type="button"
          className={`hero-device-toggle-btn ${heroDeviceMode === 'mobile' ? 'active' : ''}`}
          onClick={() => setHeroDeviceMode('mobile')}
          aria-label="Switch to Mobile App view"
        >
          <Smartphone size={12} />
          <span>Mobile App</span>
          <span className="hero-toggle-live-dot" />
        </button>
      </div>

      {/* ── Desktop Dashboard Screen ── */}
      <div className={`hero-mockup-frame ${heroDeviceMode === 'desktop' ? 'mobile-visible' : 'mobile-hidden'}`}>
        {/* Top chrome with interactive chapters and play toggle */}
        <div className="hero-mockup-topbar">
          <div className="hero-mockup-topbar-left">
            <div className="mockup-dot red" />
            <div className="mockup-dot yellow" />
            <div className="mockup-dot green" />
          </div>

          {/* Interactive Tour Chapters */}
          <div className="hero-tour-tabs" role="tablist">
            {HERO_CHAPTERS.map((ch, idx) => {
              const isActive = idx === activeIdx;
              return (
                <button
                  key={ch.id}
                  role="tab"
                  aria-selected={isActive}
                  className={`hero-tour-tab ${isActive ? 'active' : ''}`}
                  onClick={() => {
                    setActiveIdx(idx);
                  }}
                >
                  <span className="hero-tab-long">{ch.tab}</span>
                  <span className="hero-tab-short">{ch.tab.replace(/^\d+\s*/, '')}</span>
                  {isActive && (
                    <motion.div
                      layoutId="heroTabIndicator"
                      className="hero-tour-tab-indicator"
                    />
                  )}
                  {isActive && isPlaying && !isHovered && !isPhoneExpanded && (
                    <motion.div
                      key={`prog-${idx}`}
                      className="hero-tour-progress-line"
                      initial={{ width: '0%' }}
                      animate={{ width: '100%' }}
                      transition={{ duration: 4.8, ease: 'linear' }}
                    />
                  )}
                </button>
              );
            })}
          </div>

          {/* Play / Pause toggle button */}
          <button
            className="hero-tour-playbtn"
            onClick={() => setIsPlaying((p) => !p)}
            title={isPlaying ? 'Pause live tour' : 'Play live tour'}
            aria-label={isPlaying ? 'Pause live tour' : 'Play live tour'}
          >
            {isPlaying ? <Pause size={12} /> : <Play size={12} />}
          </button>
        </div>

        {/* Main interactive screen stage */}
        <div className="hero-mockup-body">
          {/* Chapter Header Badge */}
          <div className="hero-tour-chapter-header">
            <div className="hero-tour-badge">
              <span className="hero-tour-dot" />
              <code>{current.badge}</code>
            </div>
            <span className="hero-tour-pause-hint">
              {isHovered ? 'PAUSED · HOVERING' : isPlaying ? '● AUTO-PLAYING' : 'PAUSED'}
            </span>
          </div>

          <AnimatePresence mode="wait">
            {/* CHAPTER 0: INFLOW & LIVE INGESTION */}
            {activeIdx === 0 && (
              <motion.div
                key="step-ingest"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2, ease: 'easeOut' }}
                className="hero-tour-stage"
              >
                <div className="mockup-stats compact-2col">
                  <div className="mockup-stat">
                    <div className="mockup-stat-label">Total Balance</div>
                    <div className="mockup-stat-value text-accent">
                      {hasExtraInflow ? '₹85,650' : '₹84,200'}
                    </div>
                    <div className="mockup-stat-sub">
                      {hasExtraInflow ? '↑ ₹4,650 this month' : '↑ ₹3,200 this month'}
                    </div>
                  </div>
                  <div className="mockup-stat">
                    <div className="mockup-stat-label">Monthly Outflow</div>
                    <div className="mockup-stat-value text-red">₹23,450</div>
                    <div className="mockup-stat-sub">40% of income · On track</div>
                  </div>
                </div>

                {/* Interactive Filter Row + Inflow Simulator */}
                <div className="mockup-filter-row">
                  <div className="mockup-filter-pills">
                    {[
                      { id: 'all', label: 'All (4)' },
                      { id: 'upi', label: 'UPI (2)' },
                      { id: 'income', label: 'Income' },
                    ].map((f) => (
                      <button
                        key={f.id}
                        className={`mockup-filter-pill ${inflowFilter === f.id ? 'active' : ''}`}
                        onClick={() => setInflowFilter(f.id)}
                      >
                        {f.label}
                      </button>
                    ))}
                  </div>

                  <button
                    className={`mockup-simulate-btn ${hasExtraInflow ? 'active' : ''}`}
                    onClick={() => setHasExtraInflow((prev) => !prev)}
                    title="Simulate live incoming UPI SMS ingestion"
                  >
                    <Zap size={10} />
                    <span>{hasExtraInflow ? 'Reset SMS' : '+ Simulate UPI'}</span>
                  </button>
                </div>

                {/* Double-entry transaction ledger rows */}
                <div className="mockup-txns compact-list">
                  {filteredTxns.map((tx) => (
                    <motion.div
                      key={tx.id}
                      layout
                      initial={{ opacity: 0, scale: 0.98 }}
                      animate={{ opacity: 1, scale: 1 }}
                      className={`mockup-txn ${tx.isNew ? 'new-inflow-flash' : ''}`}
                    >
                      <div className="mockup-txn-dot" style={{ background: tx.color }} />
                      <div className="mockup-txn-info">
                        <span className="mockup-txn-label">{tx.label}</span>
                        <span className="mockup-txn-cat">{tx.cat}</span>
                      </div>
                      <span className="mockup-txn-tag">{tx.tag}</span>
                      <span className="mockup-txn-amount" style={{ color: tx.color }}>
                        {tx.amount}
                      </span>
                    </motion.div>
                  ))}
                </div>
              </motion.div>
            )}

            {/* CHAPTER 1: SMART BUDGETS */}
            {activeIdx === 1 && (
              <motion.div
                key="step-budgets"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2, ease: 'easeOut' }}
                className="hero-tour-stage"
              >
                {/* Interactive Envelope Rebalance Control */}
                <div className="mockup-envelope-toolbar">
                  <span className="mockup-toolbar-label">
                    {isRebalanced ? '✓ ₹500 transferred from Buffer' : 'Dining envelope pacing high'}
                  </span>
                  <button
                    className={`mockup-rebalance-btn ${isRebalanced ? 'active' : ''}`}
                    onClick={() => setIsRebalanced((prev) => !prev)}
                  >
                    <Check size={10} />
                    <span>{isRebalanced ? 'Reset Buffer' : '+ Transfer ₹500'}</span>
                  </button>
                </div>

                <div className="mockup-envelopes-grid compact-grid">
                  {[
                    {
                      name: 'Dining & Swiggy',
                      spent: '₹4,280',
                      limit: isRebalanced ? '₹5,500' : '₹5,000',
                      pct: isRebalanced ? 77 : 85,
                      color: isRebalanced ? 'var(--green)' : 'var(--amber)',
                      status: isRebalanced ? 'Pace healthy (77%)' : 'Pacing high (85%)',
                    },
                    {
                      name: 'Cloud & Dev Tools',
                      spent: '₹2,419',
                      limit: '₹2,500',
                      pct: 96,
                      color: 'var(--red)',
                      status: 'Near ceiling (96%)',
                    },
                    {
                      name: 'Transport & Uber',
                      spent: '₹1,450',
                      limit: '₹3,000',
                      pct: 48,
                      color: 'var(--green)',
                      status: 'Optimal pace (48%)',
                    },
                    {
                      name: 'Emergency Reserve',
                      spent: isRebalanced ? '₹12,300' : '₹12,800',
                      limit: '₹15,000',
                      pct: isRebalanced ? 82 : 85,
                      color: 'var(--accent)',
                      status: 'Safe surplus buffer',
                    },
                  ].map((b) => (
                    <div key={b.name} className="mockup-envelope-item">
                      <div className="mockup-envelope-header">
                        <span className="mockup-envelope-name">{b.name}</span>
                        <span className="mockup-envelope-vals">
                          <strong>{b.spent}</strong> / {b.limit}
                        </span>
                      </div>
                      <div className="mockup-envelope-track">
                        <motion.div
                          className="mockup-envelope-fill"
                          style={{ background: b.color }}
                          initial={{ width: '0%' }}
                          animate={{ width: `${b.pct}%` }}
                          transition={{ duration: 0.5, ease: 'easeOut' }}
                        />
                      </div>
                      <div className="mockup-envelope-footer">
                        <span className="mockup-envelope-status" style={{ color: b.color }}>
                          ● {b.status}
                        </span>
                        <span className="mockup-envelope-pct">{b.pct}%</span>
                      </div>
                    </div>
                  ))}
                </div>
              </motion.div>
            )}

            {/* CHAPTER 2: CASHFLOW RUNWAY */}
            {activeIdx === 2 && (
              <motion.div
                key="step-runway"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2, ease: 'easeOut' }}
                className="hero-tour-stage"
              >
                {/* Scenario switcher toolbar */}
                <div className="mockup-scenario-bar">
                  <span className="mockup-scenario-label">Burn Scenario:</span>
                  <div className="mockup-scenario-pills">
                    {[
                      { id: 'standard', label: 'Standard (64d)' },
                      { id: 'lean', label: 'Lean Mode (92d)' },
                      { id: 'scale', label: 'Growth (45d)' },
                    ].map((s) => (
                      <button
                        key={s.id}
                        className={`mockup-scenario-btn ${runwayScenario === s.id ? 'active' : ''}`}
                        onClick={() => setRunwayScenario(s.id)}
                      >
                        {s.label}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="mockup-stats compact-3col">
                  <div className="mockup-stat">
                    <div className="mockup-stat-label">Safe Runway</div>
                    <div className="mockup-stat-value text-accent">{runwayData.days} Days</div>
                    <div className="mockup-stat-sub">Zero debt buffer</div>
                  </div>
                  <div className="mockup-stat">
                    <div className="mockup-stat-label">Daily Burn</div>
                    <div className="mockup-stat-value text-amber">{runwayData.burn}</div>
                    <div className="mockup-stat-sub">Pacing calibrated</div>
                  </div>
                  <div className="mockup-stat">
                    <div className="mockup-stat-label">Health Score</div>
                    <div className="mockup-stat-value text-green">{runwayData.grade}</div>
                    <div className="mockup-stat-sub">Surplus stable</div>
                  </div>
                </div>

                <div className="mockup-chart-wrap compact-chart">
                  <div className="mockup-chart">
                    {runwayData.bars.map((h, i) => {
                      const isProjected = i >= 10;
                      const months = ['Nov', 'Dec', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct (Now)'];
                      return (
                        <motion.div
                          key={`${runwayScenario}-${i}`}
                          className={`mockup-bar ${isProjected ? 'projected-bar' : ''} ${hoveredBarIndex === i ? 'hovered' : ''}`}
                          initial={{ scaleY: 0 }}
                          animate={{ scaleY: 1 }}
                          transition={{ duration: 0.35, delay: i * 0.02, ease: [0.16, 1, 0.3, 1] }}
                          style={{ height: `${h}%` }}
                          onMouseEnter={() => setHoveredBarIndex(i)}
                          onMouseLeave={() => setHoveredBarIndex(null)}
                          title={`${months[i] || 'Projected'}: ${h}% capacity`}
                        />
                      );
                    })}
                  </div>
                  <div className="mockup-chart-axis">
                    <span>Nov</span>
                    <span>Jan</span>
                    <span>Mar</span>
                    <span>May</span>
                    <span>Jul</span>
                    <span>Sep</span>
                    <span className="text-accent font-bold">Oct (Now)</span>
                    <span className="text-muted">Nov (Est)</span>
                  </div>
                </div>
              </motion.div>
            )}

            {/* CHAPTER 3: GROQ COPILOT */}
            {activeIdx === 3 && (
              <motion.div
                key="step-copilot"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2, ease: 'easeOut' }}
                className="hero-tour-stage"
              >
                {/* Prompt selector chips */}
                <div className="mockup-prompts-row">
                  {copilotPresets.map((p, idx) => (
                    <button
                      key={idx}
                      className={`mockup-prompt-chip ${activePromptIdx === idx ? 'active' : ''}`}
                      onClick={() => {
                        setActivePromptIdx(idx);
                        setActionStaged(false);
                      }}
                    >
                      {idx === 0 ? 'Buy monitor ₹12k?' : idx === 1 ? 'Where spent this week?' : 'Save ₹5k for Diwali?'}
                    </button>
                  ))}
                </div>

                <div className="mockup-copilot-thread compact-thread">
                  <div className="mockup-chat-msg user-msg">
                    <span className="mockup-chat-text">{activeCopilot.q}</span>
                  </div>

                  <div className="mockup-chat-msg copilot-msg">
                    <div className="mockup-copilot-avatar">
                      <Bot size={13} />
                    </div>
                    <div className="mockup-copilot-content">
                      <span className="mockup-copilot-name">BudGi Copilot · Groq LLaMA 3.3</span>
                      <p className="mockup-copilot-reply">
                        {activeCopilot.reply}
                      </p>
                      <div className="mockup-copilot-actions">
                        <button
                          className={`mockup-action-pill interactive ${actionStaged ? 'staged' : ''}`}
                          onClick={() => setActionStaged((prev) => !prev)}
                        >
                          {actionStaged ? '✓ Action Staged in Ledger' : activeCopilot.actionLabel}
                        </button>
                        <span className="mockup-action-pill">📊 Inspect Burn Runway</span>
                      </div>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Live HUD Engineering Status ("Talking to the User") */}
          <div className="hero-hud-bar">
            <div className="hero-hud-badge">
              <span className="hero-hud-beacon" />
              <code>LIVE TELEMETRY</code>
            </div>
            <AnimatePresence mode="wait">
              <motion.span
                key={current.id}
                className="hero-hud-text"
                initial={{ opacity: 0, x: 8 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -8 }}
                transition={{ duration: 0.2 }}
              >
                {current.hud}
              </motion.span>
            </AnimatePresence>
          </div>
        </div>
      </div>

      {/* ── Synchronized Dynamic Mobile Phone Mockup ── */}
      <motion.div
        className={`hero-phone-mockup ${heroDeviceMode === 'mobile' ? 'mobile-visible' : 'mobile-hidden'}`}
        whileHover={{ scale: 1.02, y: -4 }}
        whileTap={{ scale: 0.99 }}
        onClick={() => setIsPhoneExpanded(true)}
        title="BudGi Android App · Tap to pop out full live interactive app"
      >
        {/* Floating Pop-Out Pill Trigger */}
        <button
          type="button"
          className="hp-expand-hint-badge"
          onClick={(e) => {
            e.stopPropagation();
            setIsPhoneExpanded(true);
          }}
          aria-label="Pop out interactive mobile app walkthrough"
        >
          <Maximize2 size={9} />
          <span>Live App · Tap to Pop Out</span>
        </button>

        <div className="hero-phone-frame">
          {/* Dynamic Island Notch */}
          <div className="hero-phone-notch">
            <span className="hp-notch-camera" />
            <span className="hp-notch-sensor" />
          </div>

          {/* Phone Status bar */}
          <div className="hp-statusbar">
            <span className="hp-time">9:41</span>
            <div className="hp-statusbar-right">
              <div className="hp-signal">
                {[3, 5, 7, 10].map((h) => (
                  <span key={h} className="hp-signal-bar" style={{ height: h }} />
                ))}
              </div>
              <span className="hp-wifi">5G</span>
              <div className="hp-battery">
                <div className="hp-battery-fill" />
              </div>
            </div>
          </div>

          {/* Screen Content */}
          <div className="hero-phone-screen">
            {/* Native Mobile App Header with Official BudGi Logo */}
            <div className="hp-app-header">
              <img src={budgiLogo} alt="BudGi Logo" className="hp-app-logo-img" />
              <div className="hp-app-info">
                <span className="hp-app-name">BudGi</span>
                <span className="hp-app-sync">
                  <span className="hp-sync-dot" /> LIVE SYNC
                </span>
              </div>
            </div>

            {/* Synchronized dynamic screen body */}
            <div className="hp-screen-scroll-area">
              <AnimatePresence mode="wait">
                {activeIdx === 0 && (
                  <motion.div
                    key="m-inflow"
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.2 }}
                    className="hp-screen-body"
                  >
                    {/* Realistic Bank Push Notification */}
                    <div
                      className={`hp-push-card ${hasExtraInflow ? 'flash-green' : ''}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        setHasExtraInflow((p) => !p);
                      }}
                      title="Tap to simulate incoming UPI credit"
                    >
                      <div className="hp-push-head">
                        <span className="hp-push-bank">HDFC Bank · UPI</span>
                        <span className="hp-push-time">Just now</span>
                      </div>
                      <div className="hp-push-text">
                        {hasExtraInflow ? (
                          <>Credited <strong>₹1,450.00</strong> via UPI Ref 994812. Available: ₹85,650.</>
                        ) : (
                          <>Debited <strong>₹340.00</strong> to Swiggy. Available: ₹84,200.</>
                        )}
                      </div>
                    </div>

                    <div className="hp-balance">
                      <span className="hp-balance-label">Total Balance</span>
                      <span className="hp-balance-amount">
                        {hasExtraInflow ? '₹85,650' : '₹84,200'}
                      </span>
                      <span className="hp-balance-change">
                        {hasExtraInflow ? '↑ ₹4,650 this mo' : '↑ ₹3,200 this mo'}
                      </span>
                    </div>

                    {/* Quick mobile action bar */}
                    <div className="hp-quick-actions">
                      <span className="hp-quick-chip">Scan QR</span>
                      <span className="hp-quick-chip">Send UPI</span>
                      <span className="hp-quick-chip">Ledger</span>
                    </div>

                    <div className="hp-mini-txn">
                      <div className="hp-mini-txn-icon text-accent">
                        <ShoppingCart size={11} />
                      </div>
                      <div className="hp-mini-txn-info">
                        <span className="hp-mini-txn-title">
                          {hasExtraInflow ? 'Zerodha Div' : 'Swiggy'}
                        </span>
                        <span className="hp-mini-txn-sub">
                          {hasExtraInflow ? 'Investment' : 'Food & Dining'}
                        </span>
                      </div>
                      <span className={`hp-mini-txn-amt ${hasExtraInflow ? 'text-green' : ''}`}>
                        {hasExtraInflow ? '+₹1,450' : '-₹340'}
                      </span>
                    </div>
                  </motion.div>
                )}

                {activeIdx === 1 && (
                  <motion.div
                    key="m-budgets"
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.2 }}
                    className="hp-screen-body"
                  >
                    <div className="hp-screen-tag">BUDGET ENVELOPE</div>
                    <div className="hp-envelope-card">
                      <div className="hp-env-top">
                        <span className="hp-env-name">Dining & Food</span>
                        <span className="hp-env-val">{isRebalanced ? '77%' : '85%'}</span>
                      </div>
                      <div className="hp-env-bar">
                        <div
                          className={`hp-env-fill ${isRebalanced ? 'green' : 'amber'}`}
                          style={{ width: isRebalanced ? '77%' : '85%' }}
                        />
                      </div>
                      <div className="hp-env-bot">
                        <span>₹4,280 spent</span>
                        <span className={isRebalanced ? 'text-green' : 'text-amber'}>
                          {isRebalanced ? '₹1,220 left' : '₹720 left'}
                        </span>
                      </div>
                    </div>

                    <div className="hp-quick-alert">
                      <Bell size={10} className={isRebalanced ? 'text-green' : 'text-amber'} />
                      <span>{isRebalanced ? 'Pace optimal: ₹122/day left' : 'Pacing high: ₹72/day left'}</span>
                    </div>

                    <button
                      className="hp-action-btn"
                      onClick={(e) => {
                        e.stopPropagation();
                        setIsRebalanced((p) => !p);
                      }}
                    >
                      <span>{isRebalanced ? '✓ ₹500 Added from Buffer' : '+ Add ₹500 from Buffer'}</span>
                    </button>
                  </motion.div>
                )}

                {activeIdx === 2 && (
                  <motion.div
                    key="m-runway"
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.2 }}
                    className="hp-screen-body"
                  >
                    <div className="hp-screen-tag">CASHFLOW RUNWAY</div>
                    <div className="hp-runway-hero">
                      <span className="hp-runway-num text-accent">{runwayData.days}</span>
                      <span className="hp-runway-unit">DAYS SAFE</span>
                    </div>

                    <div className="hp-metric-row">
                      <div className="hp-metric-col">
                        <span className="hp-m-lbl">Daily Burn</span>
                        <span className="hp-m-val text-amber">{runwayData.burn}</span>
                      </div>
                      <div className="hp-metric-col">
                        <span className="hp-m-lbl">Health</span>
                        <span className="hp-m-val text-green">{runwayData.grade}</span>
                      </div>
                    </div>

                    <div className="hp-sparkline">
                      {runwayData.bars.slice(4).map((v, i) => (
                        <span key={i} className="hp-spark-bar" style={{ height: `${v}%` }} />
                      ))}
                    </div>
                  </motion.div>
                )}

                {activeIdx === 3 && (
                  <motion.div
                    key="m-copilot"
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.2 }}
                    className="hp-screen-body"
                  >
                    <div className="hp-screen-tag">GROQ COPILOT</div>
                    <div className="hp-chat-bubble user">
                      <span>{activeCopilot.q.slice(0, 24)}...</span>
                    </div>

                    <div className="hp-chat-bubble copilot">
                      <Bot size={11} className="text-accent" />
                      <span>{activeCopilot.reply.slice(0, 68)}...</span>
                    </div>

                    <button
                      className="hp-action-btn copilot-btn"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActionStaged((p) => !p);
                      }}
                    >
                      <Zap size={10} />
                      <span>{actionStaged ? '✓ Staged' : activeCopilot.actionLabel}</span>
                    </button>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            {/* Interactive Mobile Bottom Navigation (Syncs with Desktop!) */}
            <div className="hp-bottom-nav">
              {[
                { idx: 0, label: 'Inflow', icon: Zap },
                { idx: 1, label: 'Budgets', icon: PieChart },
                { idx: 2, label: 'Runway', icon: TrendingUp },
                { idx: 3, label: 'Copilot', icon: Bot },
              ].map((tab) => {
                const isTabActive = tab.idx === activeIdx;
                const IconComponent = tab.icon;
                return (
                  <button
                    key={tab.idx}
                    className={`hp-nav-btn ${isTabActive ? 'active' : ''}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      setActiveIdx(tab.idx);
                    }}
                    title={`Switch to ${tab.label}`}
                  >
                    <IconComponent size={11} />
                    <span>{tab.label}</span>
                  </button>
                );
              })}
            </div>

            {/* Bottom touch indicator bar */}
            <div className="hp-home-indicator" />
          </div>

          <div className="hp-scanline" />
        </div>
      </motion.div>

      {/* ── Pop-Out Dynamic Mobile App Walkthrough Modal ── */}
      {typeof document !== 'undefined' &&
        createPortal(
          <AnimatePresence>
            {isPhoneExpanded && (
              <motion.div
                className="phone-walkthrough-backdrop"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setIsPhoneExpanded(false)}
              >
                <motion.div
                  className="phone-walkthrough-modal"
                  initial={{ scale: 0.9, opacity: 0, y: 30 }}
                  animate={{ scale: 1, opacity: 1, y: 0 }}
                  exit={{ scale: 0.92, opacity: 0, y: 20 }}
                  transition={{ type: 'spring', damping: 26, stiffness: 300 }}
                  onClick={(e) => e.stopPropagation()}
                >
                  {/* Header Toolbar */}
                  <div className="phone-walkthrough-header">
                    <div className="pwh-info">
                      <span className="pwh-badge">
                        <span className="pwh-badge-dot" /> LIVE INTERACTIVE APP
                      </span>
                      <span className="pwh-hint">Scroll the ledger & tap tabs to experience BudGi on Android</span>
                    </div>
                    <button
                      type="button"
                      className="pwh-close-btn"
                      onClick={() => setIsPhoneExpanded(false)}
                      aria-label="Close app walkthrough"
                      title="Close walkthrough (Esc)"
                    >
                      <X size={15} />
                      <span>Close Preview</span>
                    </button>
                  </div>

                  {/* Realistic Flagship Smartphone Chassis */}
                  <div className="phone-walkthrough-device">
                    {/* Dynamic Island */}
                    <div className="pw-island">
                      <span className="pw-cam" />
                      <span className="pw-sensor" />
                    </div>

                    {/* Status Bar */}
                    <div className="pw-statusbar">
                      <span className="pw-time">9:41</span>
                      <div className="pw-status-icons">
                        <div className="pw-signal">
                          {[4, 7, 10, 13].map((h) => (
                            <span key={h} className="pw-signal-bar" style={{ height: h }} />
                          ))}
                        </div>
                        <span className="pw-net">5G</span>
                        <div className="pw-battery">
                          <div className="pw-battery-fill" />
                        </div>
                      </div>
                    </div>

                    {/* App Navigation Bar with Official BudGi Logo */}
                    <div className="pw-app-nav">
                      <div className="pw-brand">
                        <img src={budgiLogo} alt="BudGi Logo" className="pw-logo-img" />
                        <div className="pw-brand-text">
                          <span className="pw-brand-name">BudGi</span>
                          <span className="pw-brand-sub">HDFC & Zerodha Live Sync</span>
                        </div>
                      </div>
                      <div className="pw-nav-actions">
                        <span className="pw-status-pill">
                          <span className="pw-live-dot" /> SYNCED
                        </span>
                      </div>
                    </div>

                    {/* Realistic Scrollable Screen Area */}
                    <div className="pw-scroll-body">
                      {/* CHAPTER 0: INFLOW & DOUBLE-ENTRY LEDGER */}
                      {activeIdx === 0 && (
                        <motion.div
                          key="pw-inflow"
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          exit={{ opacity: 0, y: -10 }}
                          className="pw-screen-content"
                        >
                          {/* Live HDFC Push Alert */}
                          <div
                            className={`pw-push-card ${hasExtraInflow ? 'flash-green' : ''}`}
                            onClick={() => setHasExtraInflow((p) => !p)}
                            title="Tap to toggle live UPI credit simulation"
                          >
                            <div className="pw-push-head">
                              <span className="pw-push-bank">HDFC Bank · UPI Credit</span>
                              <span className="pw-push-time">Just now</span>
                            </div>
                            <div className="pw-push-text">
                              {hasExtraInflow ? (
                                <>Credited <strong>₹1,450.00</strong> via UPI Ref 994812 from Zerodha Div. Balance: ₹85,650.</>
                              ) : (
                                <>Debited <strong>₹340.00</strong> to Swiggy UPI. Available: ₹84,200.</>
                              )}
                            </div>
                            <div className="pw-push-hint">Tap card to {hasExtraInflow ? 'reset' : 'simulate instant UPI SMS credit'}</div>
                          </div>

                          {/* Total Balance Card */}
                          <div className="pw-balance-card">
                            <div className="pw-balance-top">
                              <span className="pw-balance-title">TOTAL LIQUID BALANCE</span>
                              <span className="pw-balance-badge">{hasExtraInflow ? '+₹4,650 this mo' : '+₹3,200 this mo'}</span>
                            </div>
                            <div className="pw-balance-num">
                              {hasExtraInflow ? '₹85,650' : '₹84,200'}
                            </div>
                            <div className="pw-accounts-strip">
                              <div className="pw-acc-item">
                                <span className="pw-acc-name">HDFC Salary A/c</span>
                                <span className="pw-acc-val">₹62,400</span>
                              </div>
                              <div className="pw-acc-sep" />
                              <div className="pw-acc-item">
                                <span className="pw-acc-name">Zerodha Trading</span>
                                <span className="pw-acc-val">{hasExtraInflow ? '₹23,250' : '₹21,800'}</span>
                              </div>
                            </div>
                          </div>

                          {/* Filter Row + Simulate Inflow button */}
                          <div className="pw-controls-row">
                            <div className="pw-filter-pills">
                              {[
                                { id: 'all', label: 'All' },
                                { id: 'upi', label: 'UPI' },
                                { id: 'income', label: 'Income' },
                              ].map((f) => (
                                <button
                                  key={f.id}
                                  className={`pw-filter-pill ${inflowFilter === f.id ? 'active' : ''}`}
                                  onClick={() => setInflowFilter(f.id)}
                                >
                                  {f.label}
                                </button>
                              ))}
                            </div>
                            <button
                              className={`pw-action-pill ${hasExtraInflow ? 'active' : ''}`}
                              onClick={() => setHasExtraInflow((p) => !p)}
                            >
                              <Zap size={11} />
                              <span>{hasExtraInflow ? 'Reset SMS' : '+ Simulate UPI'}</span>
                            </button>
                          </div>

                          {/* Detailed Double-Entry Ledger List */}
                          <div className="pw-section-header">
                            <span>OCTOBER TRANSACTIONS</span>
                            <span>{filteredTxns.length} entries</span>
                          </div>

                          <div className="pw-ledger-list">
                            {filteredTxns.map((tx) => (
                              <div key={tx.id} className={`pw-txn-card ${tx.isNew ? 'flash' : ''}`}>
                                <div className="pw-txn-dot" style={{ background: tx.color }} />
                                <div className="pw-txn-meta">
                                  <span className="pw-txn-label">{tx.label}</span>
                                  <span className="pw-txn-cat">{tx.cat}</span>
                                </div>
                                <span className="pw-txn-tag">{tx.tag}</span>
                                <span className="pw-txn-amt" style={{ color: tx.color }}>
                                  {tx.amount}
                                </span>
                              </div>
                            ))}

                            {/* Additional Realistic Indian Transactions */}
                            <div className="pw-txn-card">
                              <div className="pw-txn-dot" style={{ background: 'var(--amber)' }} />
                              <div className="pw-txn-meta">
                                <span className="pw-txn-label">Blinkit Grocery</span>
                                <span className="pw-txn-cat">Household & Groceries</span>
                              </div>
                              <span className="pw-txn-tag">UPI · Instant</span>
                              <span className="pw-txn-amt" style={{ color: 'var(--amber)' }}>-₹480</span>
                            </div>

                            <div className="pw-txn-card">
                              <div className="pw-txn-dot" style={{ background: 'var(--accent)' }} />
                              <div className="pw-txn-meta">
                                <span className="pw-txn-label">Zerodha Mutual Fund SIP</span>
                                <span className="pw-txn-cat">Nifty 50 Index Fund</span>
                              </div>
                              <span className="pw-txn-tag">SIP · Auto</span>
                              <span className="pw-txn-amt" style={{ color: 'var(--accent)' }}>-₹15,000</span>
                            </div>
                          </div>
                        </motion.div>
                      )}

                      {/* CHAPTER 1: SMART BUDGET ENVELOPES */}
                      {activeIdx === 1 && (
                        <motion.div
                          key="pw-budgets"
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          exit={{ opacity: 0, y: -10 }}
                          className="pw-screen-content"
                        >
                          {/* Interactive Rebalance Action Banner */}
                          <div className="pw-envelope-banner">
                            <div className="pw-ebanner-text">
                              <span className="pw-ebanner-title">
                                {isRebalanced ? '✓ ₹500 Allocated from Buffer' : 'Dining Pacing at 85% of Limit'}
                              </span>
                              <span className="pw-ebanner-sub">
                                {isRebalanced ? 'Envelope pace restored to optimal 77%' : 'Projected to exhaust allowance in 4 days'}
                              </span>
                            </div>
                            <button
                              className={`pw-rebalance-btn ${isRebalanced ? 'active' : ''}`}
                              onClick={() => setIsRebalanced((p) => !p)}
                            >
                              <Check size={12} />
                              <span>{isRebalanced ? 'Reset Buffer' : '+ Transfer ₹500'}</span>
                            </button>
                          </div>

                          {/* Envelope Cards */}
                          <div className="pw-envelope-cards">
                            {[
                              {
                                name: 'Dining & Swiggy',
                                spent: '₹4,280',
                                limit: isRebalanced ? '₹5,500' : '₹5,000',
                                pct: isRebalanced ? 77 : 85,
                                color: isRebalanced ? 'var(--green)' : 'var(--amber)',
                                status: isRebalanced ? 'Optimal pace (77%)' : 'Pacing high (85%)',
                                burn: isRebalanced ? '₹122/day remaining' : '₹72/day remaining',
                              },
                              {
                                name: 'Cloud Compute & APIs',
                                spent: '₹2,419',
                                limit: '₹2,500',
                                pct: 96,
                                color: 'var(--red)',
                                status: 'Near ceiling (96%)',
                                burn: '₹81 remaining',
                              },
                              {
                                name: 'Transport & Uber',
                                spent: '₹1,450',
                                limit: '₹3,000',
                                pct: 48,
                                color: 'var(--green)',
                                status: 'Healthy (48%)',
                                burn: '₹155/day remaining',
                              },
                              {
                                name: 'Emergency Buffer',
                                spent: isRebalanced ? '₹12,300' : '₹12,800',
                                limit: '₹15,000',
                                pct: isRebalanced ? 82 : 85,
                                color: 'var(--accent)',
                                status: 'Safe cushion',
                                burn: 'Surplus preserved',
                              },
                            ].map((env) => (
                              <div key={env.name} className="pw-env-item">
                                <div className="pw-env-header">
                                  <span className="pw-env-name">{env.name}</span>
                                  <span className="pw-env-vals">
                                    <strong>{env.spent}</strong> / {env.limit}
                                  </span>
                                </div>
                                <div className="pw-env-track">
                                  <motion.div
                                    className="pw-env-fill"
                                    style={{ background: env.color }}
                                    initial={{ width: '0%' }}
                                    animate={{ width: `${env.pct}%` }}
                                    transition={{ duration: 0.4 }}
                                  />
                                </div>
                                <div className="pw-env-footer">
                                  <span style={{ color: env.color }}>● {env.status}</span>
                                  <span>{env.burn}</span>
                                </div>
                              </div>
                            ))}
                          </div>

                          {/* Recommendation Tip */}
                          <div className="pw-tip-box">
                            <span className="pw-tip-icon">💡</span>
                            <div className="pw-tip-content">
                              <span className="pw-tip-title">Automated Rule Engine</span>
                              <span className="pw-tip-desc">
                                Shifting 2 restaurant orders to home cooking recovers ~₹2,400 monthly discretionary surplus.
                              </span>
                            </div>
                          </div>
                        </motion.div>
                      )}

                      {/* CHAPTER 2: RUNWAY & PROJECTIONS */}
                      {activeIdx === 2 && (
                        <motion.div
                          key="pw-runway"
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          exit={{ opacity: 0, y: -10 }}
                          className="pw-screen-content"
                        >
                          {/* Scenario Switcher */}
                          <div className="pw-scenario-wrap">
                            <span className="pw-scenario-title">CALIBRATE SCENARIO:</span>
                            <div className="pw-scenario-btns">
                              {[
                                { id: 'standard', label: 'Standard (64d)' },
                                { id: 'lean', label: 'Lean (92d)' },
                                { id: 'scale', label: 'Growth (45d)' },
                              ].map((sc) => (
                                <button
                                  key={sc.id}
                                  className={`pw-scenario-btn ${runwayScenario === sc.id ? 'active' : ''}`}
                                  onClick={() => setRunwayScenario(sc.id)}
                                >
                                  {sc.label}
                                </button>
                              ))}
                            </div>
                          </div>

                          {/* Big Runway Hero */}
                          <div className="pw-runway-hero">
                            <span className="pw-runway-count text-accent">{runwayData.days}</span>
                            <span className="pw-runway-unit">DAYS SAFE CASHFLOW RUNWAY</span>
                            <span className="pw-runway-desc">Surplus sustains all fixed costs & SIP with zero debt.</span>
                          </div>

                          {/* 3 Metric Tiles */}
                          <div className="pw-metrics-grid">
                            <div className="pw-metric-card">
                              <span className="pw-m-lbl">Daily Burn</span>
                              <span className="pw-m-val text-amber">{runwayData.burn}</span>
                            </div>
                            <div className="pw-metric-card">
                              <span className="pw-m-lbl">Health Grade</span>
                              <span className="pw-m-val text-green">{runwayData.grade}</span>
                            </div>
                            <div className="pw-metric-card">
                              <span className="pw-m-lbl">SIP Buffer</span>
                              <span className="pw-m-val text-accent">₹15,000</span>
                            </div>
                          </div>

                          {/* 12-Month Interactive Bar Chart */}
                          <div className="pw-chart-container">
                            <div className="pw-chart-header">
                              <span>12-MONTH CASHFLOW SURPLUS</span>
                              <span className="text-accent font-bold">Oct (Now)</span>
                            </div>
                            <div className="pw-chart-bars">
                              {runwayData.bars.map((bar, i) => {
                                const isProjected = i >= 10;
                                return (
                                  <div key={`${runwayScenario}-${i}`} className="pw-bar-col">
                                    <motion.div
                                      className={`pw-chart-bar ${isProjected ? 'projected' : ''}`}
                                      initial={{ scaleY: 0 }}
                                      animate={{ scaleY: 1 }}
                                      transition={{ duration: 0.3, delay: i * 0.02 }}
                                      style={{ height: `${bar}%` }}
                                    />
                                    <span className="pw-bar-label">{['N', 'D', 'J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O'][i]}</span>
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        </motion.div>
                      )}

                      {/* CHAPTER 3: GROQ COPILOT INTELLIGENCE */}
                      {activeIdx === 3 && (
                        <motion.div
                          key="pw-copilot"
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          exit={{ opacity: 0, y: -10 }}
                          className="pw-screen-content"
                        >
                          {/* Tap to ask prompt selector chips */}
                          <div className="pw-copilot-prompts">
                            <span className="pw-prompts-label">SELECT A REALISTIC FINANCIAL PROMPT:</span>
                            <div className="pw-prompts-list">
                              {copilotPresets.map((p, idx) => (
                                <button
                                  key={idx}
                                  className={`pw-prompt-chip ${activePromptIdx === idx ? 'active' : ''}`}
                                  onClick={() => {
                                    setActivePromptIdx(idx);
                                    setActionStaged(false);
                                  }}
                                >
                                  <Sparkles size={11} />
                                  <span>{idx === 0 ? 'Buy ₹12k monitor?' : idx === 1 ? 'Where spent this week?' : 'Save ₹5k for Diwali?'}</span>
                                </button>
                              ))}
                            </div>
                          </div>

                          {/* Copilot Chat Thread */}
                          <div className="pw-copilot-stream">
                            {/* User Message */}
                            <div className="pw-chat-row user">
                              <div className="pw-bubble user">
                                {activeCopilot.q}
                              </div>
                            </div>

                            {/* Copilot AI Message */}
                            <div className="pw-chat-row copilot">
                              <div className="pw-copilot-avatar">
                                <Bot size={14} />
                              </div>
                              <div className="pw-bubble copilot">
                                <div className="pw-copilot-model-name">
                                  <span>BudGi Copilot</span>
                                  <code>Groq LLaMA 3.3</code>
                                </div>
                                <p className="pw-copilot-text">
                                  {activeCopilot.reply}
                                </p>
                                <div className="pw-copilot-actions">
                                  <button
                                    className={`pw-copilot-act-btn ${actionStaged ? 'staged' : ''}`}
                                    onClick={() => setActionStaged((p) => !p)}
                                  >
                                    {actionStaged ? '✓ Staged in Ledger' : activeCopilot.actionLabel}
                                  </button>
                                </div>
                              </div>
                            </div>
                          </div>
                        </motion.div>
                      )}
                    </div>

                    {/* Interactive Mobile Bottom Navigation */}
                    <div className="pw-bottom-bar">
                      {HERO_CHAPTERS.map((ch, idx) => {
                        const Icon = ch.icon;
                        const isActive = activeIdx === idx;
                        return (
                          <button
                            key={ch.id}
                            className={`pw-tab-btn ${isActive ? 'active' : ''}`}
                            onClick={() => setActiveIdx(idx)}
                          >
                            <Icon size={16} />
                            <span>{ch.tab.split(' ')[1] || ch.tab}</span>
                            {isActive && <motion.div layoutId="pwActiveTab" className="pw-tab-indicator" />}
                          </button>
                        );
                      })}
                    </div>

                    {/* Home indicator bar */}
                    <div className="pw-home-bar" />
                  </div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>,
          document.body
        )}
    </div>
  );
}

// BentoFeatures has been replaced by ScrollyFeatures

function AIChatMockup() {
  return (
    <div className="ai-mockup glass">
      <div className="ai-mockup-header">
        <div className="ai-mockup-avatar" style={{ overflow: 'hidden', padding: 0, background: 'transparent' }}>
          <img
            src={budgiLogo}
            alt="BudGi"
            style={{
              width: '100%',
              height: '100%',
              objectFit: 'cover',
              borderRadius: '50%',
            }}
          />
          <span className="ai-mockup-status-dot" />
        </div>
        <div>
          <div className="ai-mockup-name">BudGi Copilot</div>
          <div className="ai-mockup-online">Online · Knows your data</div>
        </div>
      </div>
      <div className="ai-mockup-body">
        {[
          {
            role: 'user',
            text: 'How much did I spend on Swiggy this month?',
            time: '10:42',
          },
          {
            role: 'ai',
            text: "₹4,280 — that's 85% of your food budget. Try cooking at home this week.",
            time: '10:42',
          },
          { role: 'user', text: 'Where else can I save?', time: '10:43' },
          {
            role: 'ai',
            text: "You're paying ₹780 extra on subscriptions. Cancel 3 unused ones — that's ₹1,200/month back. Want the list?",
            time: '10:43',
          },
        ].map((msg, i) => (
          <motion.div
            key={i}
            className={`ai-mock-bubble-row ${msg.role === 'user' ? 'ai-mock-user-row' : ''}`}
            initial={{ opacity: 0, y: 10, scale: 0.95 }}
            whileInView={{ opacity: 1, y: 0, scale: 1 }}
            viewport={{ once: true }}
            transition={{ duration: 0.38, delay: 0.4 + i * 0.18 }}
          >
            {msg.role === 'ai' && (
              <div
                className="ai-mock-ai-avatar"
                style={{
                  overflow: 'hidden',
                  padding: 0,
                  background: 'transparent',
                }}
              >
                <img
                  src={budgiLogo}
                  alt="BudGi"
                  style={{
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover',
                    borderRadius: '50%',
                  }}
                />
              </div>
            )}
            <div className={`ai-mock-bubble ${msg.role === 'user' ? 'ai-mock-bubble-user' : 'ai-mock-bubble-ai'}`}>
              {msg.text}
              <span className="ai-mock-time">{msg.time}</span>
            </div>
          </motion.div>
        ))}
        <div className="ai-mock-bubble-row">
          <div
            className="ai-mock-ai-avatar"
            style={{
              overflow: 'hidden',
              padding: 0,
              background: 'transparent',
            }}
          >
            <img
              src={budgiLogo}
              alt="BudGi"
              style={{
                width: '100%',
                height: '100%',
                objectFit: 'cover',
                borderRadius: '50%',
              }}
            />
          </div>
          <div className="ai-mock-bubble ai-mock-bubble-ai ai-mock-typing">
            <span />
            <span />
            <span />
          </div>
        </div>
      </div>
      <div className="ai-mockup-input">
        <span className="ai-mockup-input-placeholder">Ask anything about your finances...</span>
        <div className="ai-mockup-send">
          <ArrowRight size={12} />
        </div>
      </div>
    </div>
  );
}


/* ═══════════════════════════════════════════════════════════
   ANDROID PHONE MOCKUP (Interactive Test-Drive Simulator)
   Native Flagship Smartphone Chassis · Touch Nav · Repeat
   ═══════════════════════════════════════════════════════════ */
const PHONE_SCREENS = {
  dashboard: {
    id: 'dashboard',
    label: 'Dashboard',
    dark: '/mockup/dark/dashboard.jpg',
    light: '/mockup/light/dashboard.jpg',
  },
  activity: {
    id: 'activity',
    label: 'Activity',
    dark: '/mockup/dark/activity.jpg',
    light: '/mockup/light/activity.jpg',
  },
  goals: {
    id: 'goals',
    label: 'Goals',
    dark: '/mockup/dark/goals.jpg',
    light: '/mockup/light/goals.jpg',
  },
  planner: {
    id: 'planner',
    label: 'Planner',
    dark: '/mockup/dark/planner.jpg',
    light: '/mockup/light/planner.jpg',
  },
  copilot: {
    id: 'copilot',
    label: 'Copilot',
    dark: '/mockup/dark/copilot.jpg',
    light: '/mockup/light/copilot.jpg',
  },
};

const PHONE_SPLASH = {
  dark: '/mockup/dark/splash.jpg',
  light: '/mockup/light/splash.jpg',
};

const PHONE_TAB_KEYS = ['dashboard', 'activity', 'goals', 'planner', 'copilot'];

function PhoneMockup() {
  const globalTheme = useStore((s) => s.theme) || 'dark';
  const [phase, setPhase] = useState('standby'); // 'standby' | 'splash' | 'app'
  const [activeTab, setActiveTab] = useState('dashboard');
  const [ripples, setRipples] = useState([]);
  const [currentTime, setCurrentTime] = useState('');
  const splashTimerRef = useRef(null);

  // Preload all 12 assets on mount for zero-latency screen switching
  useEffect(() => {
    const urls = [
      '/mockup/dark/splash.jpg',
      '/mockup/dark/dashboard.jpg',
      '/mockup/dark/activity.jpg',
      '/mockup/dark/goals.jpg',
      '/mockup/dark/planner.jpg',
      '/mockup/dark/copilot.jpg',
      '/mockup/light/splash.jpg',
      '/mockup/light/dashboard.jpg',
      '/mockup/light/activity.jpg',
      '/mockup/light/goals.jpg',
      '/mockup/light/planner.jpg',
      '/mockup/light/copilot.jpg',
    ];
    urls.forEach((url) => {
      const img = new Image();
      img.src = url;
    });
  }, []);

  // Update real-time clock for status bar
  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      let hours = now.getHours();
      const minutes = String(now.getMinutes()).padStart(2, '0');
      hours = hours % 12 || 12;
      setCurrentTime(`${hours}:${minutes}`);
    };
    updateTime();
    const interval = setInterval(updateTime, 30000);
    return () => clearInterval(interval);
  }, []);

  // Launch sequence: standby -> splash (1.2s) -> app
  const launchApp = (targetTab = 'dashboard') => {
    if (splashTimerRef.current) clearTimeout(splashTimerRef.current);
    setActiveTab(targetTab);
    setPhase('splash');

    splashTimerRef.current = setTimeout(() => {
      setPhase('app');
    }, 1200);
  };

  const handleDeviceClick = () => {
    if (phase === 'standby') {
      launchApp('dashboard');
      return;
    }
    if (phase === 'splash') {
      if (splashTimerRef.current) clearTimeout(splashTimerRef.current);
      setPhase('app');
    }
  };

  const switchTab = (tabKey, e) => {
    if (e) {
      e.stopPropagation();
      const rect = e.currentTarget.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      const rippleId = Date.now();
      setRipples((prev) => [...prev, { id: rippleId, x, y }]);
      setTimeout(() => {
        setRipples((prev) => prev.filter((r) => r.id !== rippleId));
      }, 400);
    }

    if (phase !== 'app') {
      launchApp(tabKey);
    } else {
      setActiveTab(tabKey);
    }
  };

  const restartMockup = (e) => {
    if (e) e.stopPropagation();
    if (splashTimerRef.current) clearTimeout(splashTimerRef.current);
    setPhase('standby');
    setActiveTab('dashboard');
  };

  const currentImageSrc = PHONE_SCREENS[activeTab][globalTheme];
  const currentSplashSrc = PHONE_SPLASH[globalTheme];

  return (
    <div className="interactive-phone-container">
      {/* Physical Android Flagship Chassis */}
      <div
        className={`android-device-chassis theme-${globalTheme}`}
        onClick={handleDeviceClick}
        title={phase === 'standby' ? 'Click to open BudGi' : undefined}
      >
        {/* Hardware details: Side buttons & speaker slit */}
        <div className="device-btn-volume" />
        <div
          className="device-btn-power"
          onClick={restartMockup}
          title={phase === 'app' ? 'Power button (Restart demo)' : undefined}
        />
        <div className="device-earpiece-slit" />

        {/* Display Bezel & Screen */}
        <div className="android-device-screen">
          {/* Subtle realistic glass reflection sheen */}
          <div className="device-glass-sheen" />

          {/* Precision Punchhole Camera */}
          <div className="punchhole-camera" />

          {/* Status Bar only shown in Standby to avoid double-clocks over real app headers */}
          {phase === 'standby' && (
            <div className="android-status-bar">
              <span className="status-time">{currentTime || '5:28'}</span>
              <div className="status-icons">
                <Wifi size={11} />
                <span>5G</span>
                <div className="status-battery">
                  <div className="status-battery-level" />
                </div>
              </div>
            </div>
          )}

          {/* Screen Content Layers */}
          <AnimatePresence mode="wait">
            {phase === 'standby' && (
              <motion.div
                key={`standby-${globalTheme}`}
                className="mockup-standby-view"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.2 }}
              >
                <div className="standby-app-icon-wrap">
                  <img src={budgiLogo} alt="BudGi" className="standby-app-icon" />
                </div>
                <div className="standby-app-name">BudGi</div>
                <div className="standby-app-tag">Native Android App</div>
                <div className="standby-tap-prompt">
                  <Play size={10} fill="currentColor" />
                  <span>Tap to open app</span>
                </div>
              </motion.div>
            )}

            {phase === 'splash' && (
              <motion.div
                key={`splash-${globalTheme}`}
                className="mockup-splash-view"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.25 }}
              >
                <img
                  src={currentSplashSrc}
                  alt="BudGi Splash Screen"
                  className="mockup-screenshot-img"
                />
              </motion.div>
            )}

            {phase === 'app' && (
              <motion.div
                key={`${activeTab}-${globalTheme}`}
                className="mockup-screen-viewport"
                initial={{ opacity: 0.9 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0.9 }}
                transition={{ duration: 0.15 }}
              >
                <img
                  src={currentImageSrc}
                  alt={`BudGi ${PHONE_SCREENS[activeTab].label}`}
                  className="mockup-screenshot-img"
                  draggable={false}
                />

                {/* Copilot Back Button Hotspot */}
                {activeTab === 'copilot' && (
                  <button
                    type="button"
                    className="mockup-top-back-hotspot"
                    onClick={(e) => switchTab('dashboard', e)}
                    aria-label="Back to Dashboard"
                    title="Back to Dashboard"
                  />
                )}

                {/* Bottom Navigation Bar Touch Hotspots (5 equal segments) */}
                <div className="mockup-bottom-hotspots">
                  {PHONE_TAB_KEYS.map((key) => (
                    <button
                      key={key}
                      type="button"
                      className="mockup-nav-hotspot"
                      onClick={(e) => switchTab(key, e)}
                      aria-label={PHONE_SCREENS[key].label}
                      title={PHONE_SCREENS[key].label}
                    />
                  ))}
                </div>

                {/* Touch ripples */}
                {ripples.map((ripple) => (
                  <span
                    key={ripple.id}
                    className="mockup-touch-ripple"
                    style={{ left: ripple.x, top: ripple.y }}
                  />
                ))}
              </motion.div>
            )}
          </AnimatePresence>

          {/* Android Gesture Bar only on standby */}
          {phase === 'standby' && <div className="android-gesture-pill" />}
        </div>
      </div>

      {/* Repeat / Restart Demo Control */}
      {phase !== 'standby' && (
        <motion.button
          type="button"
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 6 }}
          className="mockup-replay-btn"
          onClick={restartMockup}
          title="Restart app demo"
        >
          <RotateCcw size={12} />
          <span>Restart Demo</span>
        </motion.button>
      )}
    </div>
  );
}

function AndroidSVG({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className}>
      <path d="M17.6 9.48l1.84-3.18c.16-.31.04-.69-.26-.85-.29-.15-.65-.06-.83.22l-1.88 3.24c-2.86-1.21-6.08-1.21-8.94 0L5.65 5.67c-.19-.29-.58-.38-.87-.22-.28.15-.4.54-.22.85L6.4 9.48C3.3 11.25 1.28 14.44 1 18.25h22c-.28-3.81-2.3-7-5.4-8.77zM7 15.25c-.69 0-1.25-.56-1.25-1.25s.56-1.25 1.25-1.25 1.25.56 1.25 1.25-.56 1.25-1.25 1.25zm10 0c-.69 0-1.25-.56-1.25-1.25s.56-1.25 1.25-1.25 1.25.56 1.25 1.25-.56 1.25-1.25 1.25z" />
    </svg>
  );
}

function PlayStoreSVG() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
      <path
        d="M3.18 23.76c.28.15.6.19.91.11L14.6 12 3.09.13A1.1 1.1 0 0 0 3 .59v22.82c0 .12.06.23.18.35z"
        fill="#EA4335"
      />
      <path d="M18.71 15.5l-3.1-3.5 3.1-3.5 3.5 2.02a1.1 1.1 0 0 1 0 2.96L18.71 15.5z" fill="#FBBC04" />
      <path d="M14.6 12 3.09 23.87c.33.09.68.05.97-.11l13.17-7.59L14.6 12z" fill="#34A853" />
      <path d="M14.6 12 17.23 9.37 4.06.11A1.1 1.1 0 0 0 3.09.13L14.6 12z" fill="#4285F4" />
    </svg>
  );
}



/* ═══════════════════════════════════════
   SAVINGS COUNTER — animated social proof
   ═══════════════════════════════════════ */
function SavingsCounter() {
  const [count, setCount] = useState(0);
  const targetRef = useRef(null);
  const hasRun = useRef(false);

  useEffect(() => {
    const el = targetRef.current;
    if (!el) return;
    let rafId;
    const obs = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !hasRun.current) {
          hasRun.current = true;
          const target = 24_300_000;
          const duration = 2200;
          const start = performance.now();
          const raf = (now) => {
            const progress = Math.min((now - start) / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3);
            setCount(Math.round(eased * target));
            if (progress < 1) {
              rafId = requestAnimationFrame(raf);
            }
          };
          rafId = requestAnimationFrame(raf);
        }
      },
      { threshold: 0.5 },
    );
    obs.observe(el);
    return () => {
      obs.disconnect();
      if (rafId) cancelAnimationFrame(rafId);
    };
  }, []);

  const fmt = (n) => {
    if (n >= 10_000_000) return `₹${(n / 10_000_000).toFixed(1)} Cr`;
    if (n >= 100_000) return `₹${(n / 100_000).toFixed(1)} L`;
    return `₹${n.toLocaleString('en-IN')}`;
  };

  const STATS = [
    { value: '2,400+', label: 'Active users' },
    { value: fmt(count), label: 'Saved collectively', live: true },
    { value: '91%', label: 'ML forecast accuracy' },
    { value: '4.8 ★', label: 'Average user rating' },
  ];

  return (
    <motion.div
      ref={targetRef}
      className="savings-counter-row"
      initial={{ opacity: 0, y: 32 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.6 }}
    >
      {STATS.map((s, i) => (
        <motion.div
          key={s.label}
          className="savings-stat"
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5, delay: i * 0.1 }}
        >
          <div className={`savings-stat-value ${s.live ? 'savings-stat-live' : ''}`}>{s.value}</div>
          <div className="savings-stat-label">{s.label}</div>
          {s.live && <span className="savings-live-dot" />}
        </motion.div>
      ))}
    </motion.div>
  );
}

/* ═══════════════════════════════════════
   FAQ — accordion
   ═══════════════════════════════════════ */
const FAQ_ITEMS = [
  {
    q: 'Is my financial data safe?',
    a: 'Yes. BudGi uses JWT authentication, encrypted storage, and rate limiting on all endpoints. We never sell or share your data with third parties — ever. Your data is yours.',
  },
  {
    q: 'Do I need to connect my bank account?',
    a: 'No. BudGi works entirely with manual entries and CSV imports. No bank connection, no open banking API, no credentials shared. You stay in full control.',
  },
  {
    q: 'What is BudGi Copilot exactly?',
    a: 'BudGi Copilot is an AI assistant powered by Groq\'s LLaMA 3.3 model. It reads your actual transaction data and budget limits to answer questions like "How much did I spend on Swiggy this month?" or "Where can I save more?" It gives personalised answers, not generic advice.',
  },
  {
    q: 'Can I export my data?',
    a: 'Yes — the Free plan includes CSV export of all your transactions. Pro and Elite plans include additional report formats and full data portability.',
  },
  {
    q: 'What happens if I exceed the free plan limits?',
    a: "You'll be prompted to upgrade when you hit the 100 transaction limit. None of your existing data is deleted — you just can't add new entries until you upgrade or remove old transactions.",
  },
  {
    q: 'How accurate are the ML predictions?',
    a: 'Our spending forecast model achieves 91% accuracy on average after 2 months of data. The more transactions you add, the sharper the predictions get.',
  },
];

function FAQSection() {
  const [open, setOpen] = useState(null);
  return (
    <div className="faq-list">
      {FAQ_ITEMS.map((item, i) => (
        <motion.div
          key={i}
          className={`faq-item ${open === i ? 'faq-item-open' : ''}`}
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.4, delay: i * 0.06 }}
        >
          <button
            className="faq-question"
            onClick={() => setOpen(open === i ? null : i)}
            aria-expanded={open === i}
            aria-controls={`faq-answer-${i}`}
          >
            <span>{item.q}</span>
            <motion.div
              className="faq-chevron"
              animate={{ rotate: open === i ? 180 : 0 }}
              transition={{ duration: 0.25 }}
            >
              <ChevronDown size={16} />
            </motion.div>
          </button>
          <AnimatePresence>
            {open === i && (
              <motion.div
                id={`faq-answer-${i}`}
                className="faq-answer"
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
              >
                <p>{item.a}</p>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      ))}
    </div>
  );
}
