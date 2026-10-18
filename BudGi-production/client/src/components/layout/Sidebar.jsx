import { useState } from 'react';
import { NavLink, Link, useNavigate } from 'react-router-dom';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LayoutDashboard,
  ArrowLeftRight,
  BarChart3,
  Bot,
  Settings,
  LogOut,
  Wallet,
  Target,
  CircleHelp,
  X,
  Headset,
  BookOpen,
  MessageCircle,
  Mail,
  FileText,
  Flame,
} from '../../lib/icons';
import { useUnreadAlerts } from '../../hooks/useAlerts';
import useStore from '../../store/useStore';
import BrandLogo from '../ui/BrandLogo';
import './Sidebar.css';

const NAV_MAIN = [
  { to: '/app/dashboard', icon: LayoutDashboard, label: 'Overview', shortcut: 'Alt+D' },
  { to: '/app/transactions', icon: ArrowLeftRight, label: 'History', shortcut: 'Alt+T' },
  { to: '/app/budgets', icon: Wallet, label: 'Budgets', shortcut: 'Alt+B' },
  { to: '/app/analytics', icon: BarChart3, label: 'Insights', shortcut: 'Alt+A' },
  { to: '/app/goals', icon: Target, label: 'Goals', shortcut: 'Alt+G' },
];

const NAV_TOOLS = [{ to: '/app/ai', icon: Bot, label: 'Copilot', shortcut: 'Alt+C' }];

export default function Sidebar() {
  const { user, logout, sidebarOpen, setSidebarOpen } = useStore();
  const navigate = useNavigate();
  const [showHelp, setShowHelp] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const { unreadCount } = useUnreadAlerts();

  const initials = user?.name
    ? user.name
        .split(' ')
        .map((w) => w[0])
        .join('')
        .slice(0, 2)
        .toUpperCase()
    : '?';

  const isMobile = useMediaQuery('(max-width: 768px)');

  return (
    <>
      <AnimatePresence>
        {isMobile && sidebarOpen && (
          <motion.div
            className="sidebar-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={() => setSidebarOpen(false)}
            style={{
              position: 'fixed',
              inset: 0,
              background: 'rgba(0, 0, 0, 0.65)',
              backdropFilter: 'blur(4px)',
              WebkitBackdropFilter: 'blur(4px)',
              zIndex: 190,
            }}
          />
        )}
      </AnimatePresence>
      <motion.aside
        className={`sidebar ${sidebarOpen ? 'sidebar-open' : 'sidebar-collapsed'}`}
        initial={false}
        animate={{
          width: isMobile ? 280 : sidebarOpen ? 250 : 76,
          x: isMobile ? (sidebarOpen ? 0 : -280) : 0,
        }}
        transition={{
          type: 'spring',
          stiffness: 280,
          damping: 26,
          mass: 0.5,
        }}
        onMouseEnter={() => !isMobile && setSidebarOpen(true)}
        onMouseLeave={() => !isMobile && setSidebarOpen(false)}
      >
        {/* Logo — click to visit landing page without logging out */}
        <Link
          to="/"
          className="sidebar-logo"
          title="View landing page (session kept active)"
          onClick={() => isMobile && setSidebarOpen(false)}
        >
          <div className="sidebar-logo-mark">
            <BrandLogo size={24} text="" />
          </div>
          <AnimatePresence>
            {sidebarOpen && (
              <motion.span
                className="sidebar-logo-text"
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -10 }}
                transition={{ duration: 0.3 }}
              >
                BudGi
              </motion.span>
            )}
          </AnimatePresence>
        </Link>

        {/* Main Navigation */}
        <nav className="sidebar-nav">
          <AnimatePresence>
            {sidebarOpen && (
              <motion.span
                className="sidebar-group-label"
                initial={{ opacity: 0 }}
                animate={{ opacity: 0.6 }}
                exit={{ opacity: 0 }}
              >
                Personal Finance
              </motion.span>
            )}
          </AnimatePresence>

          {NAV_MAIN.map(({ to, icon: Icon, label, shortcut }, i) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`}
              onClick={(e) => {
                if (isMobile) setSidebarOpen(false);
                e.stopPropagation();
              }}
              title={sidebarOpen ? undefined : `${label} (${shortcut})`}
            >
              <motion.span
                className="sidebar-link-icon"
                whileHover={{ scale: 1.15, rotate: 5 }}
                transition={{ type: 'spring', stiffness: 400, damping: 10 }}
              >
                <Icon size={21} strokeWidth={2.2} />
              </motion.span>
              <AnimatePresence mode="wait">
                {sidebarOpen && (
                  <motion.span
                    className="sidebar-link-label"
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: -5 }}
                    transition={{
                      duration: 0.3,
                      delay: i * 0.03,
                      ease: 'easeOut',
                    }}
                  >
                    {label}
                  </motion.span>
                )}
              </AnimatePresence>
              {label === 'Overview' && unreadCount > 0 && (
                <motion.span className="sidebar-alert-badge" initial={{ scale: 0 }} animate={{ scale: 1 }}>
                  {unreadCount > 9 ? '9+' : unreadCount}
                </motion.span>
              )}
            </NavLink>
          ))}

          {sidebarOpen && <div style={{ height: 16 }} />}
          {NAV_TOOLS.map(({ to, icon: Icon, label, shortcut }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`}
              onClick={(e) => {
                if (isMobile) setSidebarOpen(false);
                e.stopPropagation();
              }}
              title={sidebarOpen ? undefined : `${label} (${shortcut})`}
            >
              <motion.span
                className="sidebar-link-icon"
                whileHover={{ scale: 1.18, rotate: 5 }}
                transition={{ type: 'spring', stiffness: 400, damping: 10 }}
              >
                <Icon size={21} strokeWidth={2.2} />
              </motion.span>
              <AnimatePresence mode="wait">
                {sidebarOpen && (
                  <motion.span
                    className="sidebar-link-label"
                    initial={{ opacity: 0, x: -15 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: -10 }}
                    transition={{ duration: 0.35, ease: 'easeOut' }}
                  >
                    {label}
                  </motion.span>
                )}
              </AnimatePresence>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-spacer" />

        {/* Footer actions */}
        <div className="sidebar-footer">
          <NavLink
            to="/app/settings"
            className="sidebar-link"
            title={sidebarOpen ? undefined : 'Settings'}
            onClick={() => isMobile && setSidebarOpen(false)}
          >
            <span className="sidebar-link-icon">
              <Settings size={18} />
            </span>
            {sidebarOpen && <span className="sidebar-link-label">Settings</span>}
          </NavLink>
          <button className="sidebar-link" title={sidebarOpen ? undefined : 'Help'} onClick={() => setShowHelp(true)}>
            <span className="sidebar-link-icon">
              <CircleHelp size={18} />
            </span>
            {sidebarOpen && <span className="sidebar-link-label">Help Center</span>}
          </button>
        </div>

        {/* User Card */}
        <div className="sidebar-user-section">
          {user?.profileImage ? (
            <img src={user.profileImage} alt={user.name} className="sidebar-user-avatar-img" />
          ) : (
            <div className="sidebar-user-avatar">{initials}</div>
          )}
          <AnimatePresence>
            {sidebarOpen && (
              <motion.div
                className="sidebar-user-details"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                <div className="sidebar-user-name">{user?.name?.split(' ')[0]}</div>
                <div className="sidebar-user-meta" style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                  <span>@{user?.username || 'member'}</span>
                  <span>•</span>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                    <Flame size={12} style={{ color: 'var(--amber)' }} />
                    <span>{user?.streak?.count || 1}d</span>
                  </span>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
          {sidebarOpen && (
            <button className="sidebar-logout-minimal" onClick={handleLogout} title="Sign out">
              <LogOut size={14} />
            </button>
          )}
        </div>
      </motion.aside>

      {/* Mobile backdrop */}
      <AnimatePresence>
        {sidebarOpen && isMobile && (
          <motion.div
            className="sidebar-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setSidebarOpen(false)}
            style={{ zIndex: 190 }}
          />
        )}
      </AnimatePresence>

      {/* Help Center Modal */}
      <AnimatePresence>
        {showHelp && (
          <div className="help-modal-overlay" onClick={() => setShowHelp(false)}>
            <motion.div
              className="help-modal-content"
              initial={{ opacity: 0, scale: 0.92, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.92, y: 20 }}
              transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
              onClick={(e) => e.stopPropagation()}
            >
              <button className="help-modal-close" onClick={() => setShowHelp(false)}>
                <X size={20} />
              </button>

              <div className="help-modal-header">
                <div className="help-modal-icon">
                  <Headset size={24} />
                </div>
                <div>
                  <h2>Help & Support</h2>
                  <p>How can we help you today?</p>
                </div>
              </div>

              <div className="help-modal-grid">
                {[
                  {
                    icon: BookOpen,
                    title: 'Knowledge Base',
                    desc: 'Architecture & Guides',
                    action: () => {
                      setShowHelp(false);
                      window.open('/#features', '_blank');
                    },
                  },
                  {
                    icon: MessageCircle,
                    title: 'BudGi Copilot',
                    desc: 'Real-time financial chat',
                    action: () => {
                      setShowHelp(false);
                      navigate('/app/ai');
                    },
                  },
                  {
                    icon: Mail,
                    title: 'Email Support',
                    desc: 'support@budgi.com',
                    action: () => (window.location.href = 'mailto:support@budgi.com'),
                  },
                  {
                    icon: FileText,
                    title: 'Terms & Privacy',
                    desc: 'Verified Architecture',
                    action: () => {
                      setShowHelp(false);
                      window.open('/#privacy', '_blank');
                    },
                  },
                ].map((item, i) => (
                  <motion.div
                    key={i}
                    className="help-modal-card"
                    onClick={item.action}
                    whileHover={{ scale: 1.02, backgroundColor: 'var(--bg-elevated)' }}
                    whileTap={{ scale: 0.98 }}
                  >
                    <item.icon size={20} className="help-modal-card-icon" />
                    <span className="help-modal-card-title">{item.title}</span>
                    <span className="help-modal-card-desc">{item.desc}</span>
                  </motion.div>
                ))}
              </div>

              <div className="help-modal-footer">
                <div>
                  <div className="help-modal-footer-title">Need instant help?</div>
                  <div className="help-modal-footer-desc">Ask BudGi Copilot</div>
                </div>
                <motion.button
                  className="help-modal-footer-btn"
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  onClick={() => {
                    setShowHelp(false);
                    navigate('/app/ai');
                  }}
                >
                  Ask AI
                </motion.button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </>
  );
}
