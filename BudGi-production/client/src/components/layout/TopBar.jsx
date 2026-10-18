import { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useLocation, Link } from 'react-router-dom';
import { Menu, Moon, Sun, Bell, X, Check, AlertTriangle, AlertCircle, Keyboard, Flame, Info, Trash2 } from '../../lib/icons';
import { motion, AnimatePresence } from 'framer-motion';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { alertsApi } from '../../lib/api';
import { useUnreadAlerts } from '../../hooks/useAlerts';
import useStore from '../../store/useStore';
import toast from 'react-hot-toast';
import './TopBar.css';

const PAGE_TITLES = {
  '/app/dashboard': { title: 'Dashboard', subtitle: 'Your financial overview' },
  '/app/transactions': { title: 'Transactions', subtitle: 'Track every rupee' },
  '/app/budgets': { title: 'Budgets', subtitle: 'Manage your limits' },
  '/app/analytics': {
    title: 'Analytics',
    subtitle: 'Spending insights & trends',
  },
  '/app/ai': { title: 'BudGi Copilot', subtitle: 'Your smart assistant' },
  '/app/goals': { title: 'Goals', subtitle: 'Savings targets & progress' },
  '/app/settings': { title: 'Settings', subtitle: 'Account & preferences' },
};

export default function TopBar() {
  const location = useLocation();
  const { toggleSidebar, toggleTheme, theme, user } = useStore();
  const page = PAGE_TITLES[location.pathname] || {
    title: 'BudGi',
    subtitle: '',
  };
  const isDark = theme === 'dark';
  const [bellOpen, setBellOpen] = useState(false);
  const [kbdOpen, setKbdOpen] = useState(false);
  const bellRef = useRef(null);
  const topbarRef = useRef(null);

  // Scroll-based auto-hide
  const [isHidden, setIsHidden] = useState(false);
  const lastScrollY = useRef(0);

  // Always show TopBar and reset scroll state when switching sub-screens
  useEffect(() => {
    setIsHidden(false);
    lastScrollY.current = 0;
  }, [location.pathname]);

  useEffect(() => {
    const contentEl = document.querySelector('.shell-content');
    if (!contentEl) return;

    const handleScroll = () => {
      if (bellOpen || kbdOpen) return;
      const currentY = contentEl.scrollTop;
      const delta = currentY - lastScrollY.current;

      // Auto-hide: hide on scroll-down, show on scroll-up
      if (delta > 14 && currentY > 120) {
        setIsHidden(true);
      } else if (delta < -8) {
        setIsHidden(false);
      }

      lastScrollY.current = currentY;
    };

    contentEl.addEventListener('scroll', handleScroll, { passive: true });
    return () => contentEl.removeEventListener('scroll', handleScroll);
  }, [bellOpen, kbdOpen]);

  const [dropdownPos, setDropdownPos] = useState({ top: 76, right: 24 });

  useEffect(() => {
    if (bellOpen && bellRef.current) {
      const rect = bellRef.current.getBoundingClientRect();
      setDropdownPos({
        top: Math.round(rect.bottom + 8),
        right: Math.max(16, Math.round(window.innerWidth - rect.right)),
      });
    }
  }, [bellOpen]);

  // '?' key opens keyboard shortcuts
  useEffect(() => {
    const handler = (e) => {
      const tag = document.activeElement?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
      if (e.key === '?') {
        e.preventDefault();
        setKbdOpen((v) => !v);
      }
      if (e.key === 'Escape') {
        setKbdOpen(false);
        setBellOpen(false);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  // Live unread count
  const { unreadCount } = useUnreadAlerts();

  // Alerts list (pre-fetched so it appears immediately)
  const { data: alertsEnv, isLoading: alertsLoading } = useQuery({
    queryKey: ['alerts-topbar'],
    queryFn: () => alertsApi.list({ limit: 15, unreadOnly: false }),
    staleTime: 30_000,
    refetchInterval: 30_000,
  });
  const alerts = alertsEnv?.data?.alerts ?? [];

  // Mark as read
  const qc = useQueryClient();
  const markRead = useMutation({
    mutationFn: (ids) => alertsApi.markRead(ids),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['alerts-count'] });
      qc.invalidateQueries({ queryKey: ['alerts-topbar'] });
      qc.invalidateQueries({ queryKey: ['alerts'] });
    },
  });

  const clearAllMutation = useMutation({
    mutationFn: () => alertsApi.clearAll(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['alerts-count'] });
      qc.invalidateQueries({ queryKey: ['alerts-topbar'] });
      qc.invalidateQueries({ queryKey: ['alerts'] });
      toast.success('All notifications cleared');
    },
    onError: () => {
      toast.error('Failed to clear notifications');
    },
  });

  const handleMarkAllRead = () => {
    const unreadIds = alerts.filter((a) => !a.isRead).map((a) => a._id);
    if (unreadIds.length > 0) {
      markRead.mutate(unreadIds);
      toast.success('All alerts marked as read');
    }
  };

  const handleClearAll = () => {
    if (alerts.length === 0) return;
    clearAllMutation.mutate();
  };

  const handleMarkOne = (id) => {
    markRead.mutate([id]);
  };

  const severityIcon = (s) => {
    if (s === 'critical') return <AlertCircle size={13} style={{ color: 'var(--red)' }} />;
    if (s === 'warning') return <AlertTriangle size={13} style={{ color: 'var(--amber)' }} />;
    return <Info size={13} style={{ color: 'var(--accent-bright)' }} />;
  };

  return (
    <header ref={topbarRef} className={`topbar ${isHidden ? 'topbar-hidden' : ''}`}>
      <div className="topbar-left">
        <button className="topbar-menu-btn" onClick={toggleSidebar} aria-label="Toggle menu">
          <Menu size={18} />
        </button>
        <div className="topbar-title-group">
          <motion.h1
            key={page.title}
            className="topbar-title"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.2 }}
          >
            {page.title}
          </motion.h1>
          <motion.span
            key={page.subtitle}
            className="topbar-subtitle"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.2, delay: 0.05 }}
          >
            {page.subtitle}
          </motion.span>
        </div>
      </div>

      <div className="topbar-right">
        <div className="topbar-greeting">
          <span>
            {getGreeting()}, {user?.name?.split(' ')[0] || 'there'}
          </span>
          {user?.username && (
            <span style={{ fontSize: '12px', color: 'var(--accent-bright)', fontWeight: 600, marginLeft: '6px' }}>
              @{user.username}
            </span>
          )}
        </div>
        <div
          className="topbar-streak-badge"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '5px',
            background: 'var(--amber-dim)',
            color: 'var(--amber)',
            border: '1px solid var(--amber)',
            borderRadius: '999px',
            padding: '4px 10px',
            fontSize: '12px',
            fontWeight: 700,
          }}
        >
          <Flame size={13} style={{ color: 'var(--amber)', flexShrink: 0 }} />
          <span>{user?.streak?.count || 1}d</span>
        </div>
        <div className="topbar-currency-badge">
          <span>{user?.currency || 'INR'}</span>
        </div>

        {/* Notification Bell */}
        <div className="topbar-bell-wrap" ref={bellRef}>
          <motion.button
            className="topbar-bell-btn"
            onClick={() => setBellOpen((v) => !v)}
            whileTap={{ scale: 0.88 }}
            aria-label="Notifications"
            title="Notifications"
          >
            <Bell size={17} />
            <AnimatePresence>
              {unreadCount > 0 && (
                <motion.span
                  className="topbar-bell-badge"
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  exit={{ scale: 0 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 20 }}
                >
                  {unreadCount > 9 ? '9+' : unreadCount}
                </motion.span>
              )}
            </AnimatePresence>
          </motion.button>
        </div>

        {/* Portal for Notifications Dropdown */}
        {typeof document !== 'undefined' &&
          createPortal(
            <AnimatePresence>
              {bellOpen && (
                <>
                  <div
                    className="topbar-dropdown-backdrop"
                    onClick={() => setBellOpen(false)}
                  />
                  <motion.div
                    className="topbar-bell-dropdown"
                    style={{
                      position: 'fixed',
                      top: dropdownPos.top,
                      right: dropdownPos.right,
                      zIndex: 99999,
                    }}
                    initial={{ opacity: 0, y: -8, scale: 0.96 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, y: -8, scale: 0.96 }}
                    transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
                  >
                    <div className="bell-dd-header">
                      <span className="bell-dd-title">Notifications</span>
                      <div className="bell-dd-actions">
                        {unreadCount > 0 && (
                          <button className="bell-dd-mark-all" onClick={handleMarkAllRead} title="Mark all as read">
                            <Check size={12} /> Mark read
                          </button>
                        )}
                        {alerts.length > 0 && (
                          <button
                            className="bell-dd-clear-all"
                            onClick={handleClearAll}
                            disabled={clearAllMutation.isPending}
                            title="Clear all notifications"
                          >
                            <Trash2 size={12} /> Clear all
                          </button>
                        )}
                      </div>
                    </div>
                    <div className="bell-dd-list">
                      {alertsLoading ? (
                        <div style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: 10 }}>
                          {Array.from({ length: 3 }).map((_, i) => (
                            <div key={i} className="shimmer" style={{ height: 48, borderRadius: 8, opacity: 0.3 }} />
                          ))}
                        </div>
                      ) : alerts.length === 0 ? (
                        <div className="bell-dd-empty">
                          <Bell size={20} style={{ opacity: 0.25, marginBottom: 4 }} />
                          <span>No notifications yet</span>
                        </div>
                      ) : (
                        alerts.map((a) => (
                          <div
                            key={a._id}
                            className={`bell-dd-item ${!a.isRead ? 'bell-dd-unread' : ''}`}
                            onClick={() => !a.isRead && handleMarkOne(a._id)}
                          >
                            <div className="bell-dd-item-icon">{severityIcon(a.severity)}</div>
                            <div className="bell-dd-item-body">
                              <span className="bell-dd-item-msg">{a.message}</span>
                              <span className="bell-dd-item-time">{timeAgo(a.createdAt)}</span>
                            </div>
                            {!a.isRead && <span className="bell-dd-dot" />}
                          </div>
                        ))
                      )}
                    </div>
                    <Link to="/app/ai" className="bell-dd-footer" onClick={() => setBellOpen(false)}>
                      View all in BudGi Copilot →
                    </Link>
                  </motion.div>
                </>
              )}
            </AnimatePresence>,
            document.body,
          )}

        {/* Theme toggle */}
        <motion.button
          className="topbar-theme-btn"
          onClick={toggleTheme}
          aria-label="Toggle theme"
          whileTap={{ scale: 0.88 }}
          title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
        >
          <AnimatePresence mode="wait" initial={false}>
            {isDark ? (
              <motion.span
                key="moon"
                initial={{ rotate: -90, opacity: 0, scale: 0.5 }}
                animate={{ rotate: 0, opacity: 1, scale: 1 }}
                exit={{ rotate: 90, opacity: 0, scale: 0.5 }}
                transition={{ duration: 0.25 }}
              >
                <Sun size={16} />
              </motion.span>
            ) : (
              <motion.span
                key="sun"
                initial={{ rotate: 90, opacity: 0, scale: 0.5 }}
                animate={{ rotate: 0, opacity: 1, scale: 1 }}
                exit={{ rotate: -90, opacity: 0, scale: 0.5 }}
                transition={{ duration: 0.25 }}
              >
                <Moon size={16} />
              </motion.span>
            )}
          </AnimatePresence>
        </motion.button>

        {/* Keyboard shortcuts help */}
        <motion.button
          className="topbar-theme-btn"
          onClick={() => setKbdOpen(true)}
          aria-label="Keyboard shortcuts"
          whileTap={{ scale: 0.88 }}
          title="Keyboard shortcuts (?)"
        >
          <Keyboard size={16} />
        </motion.button>
      </div>

      {/* Keyboard Shortcuts Modal */}
      {typeof document !== 'undefined' &&
        createPortal(
          <AnimatePresence>
            {kbdOpen && (
              <motion.div
                className="kbd-modal-overlay"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setKbdOpen(false)}
              >
                <motion.div
                  className="kbd-modal"
                  initial={{ opacity: 0, scale: 0.95, y: 16 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95, y: 16 }}
                  transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="kbd-modal-header">
                    <h3>⌨️ Keyboard Shortcuts</h3>
                    <button onClick={() => setKbdOpen(false)} aria-label="Close shortcuts">
                      <X size={16} />
                    </button>
                  </div>
                  <div className="kbd-modal-grid">
                    {[
                      ['Alt + D', 'Dashboard'],
                      ['Alt + T', 'Transactions'],
                      ['Alt + B', 'Budgets'],
                      ['Alt + A', 'Analytics'],
                      ['Alt + G', 'Goals'],
                      ['Alt + C', 'BudGi Copilot'],
                      ['Alt + S', 'Settings'],
                    ].map(([key, label]) => (
                      <div key={key} className="kbd-row">
                        <kbd className="kbd-key">{key}</kbd>
                        <span className="kbd-label">{label}</span>
                      </div>
                    ))}
                  </div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>,
          document.body,
        )}
    </header>
  );
}

function getGreeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

function timeAgo(dateStr) {
  if (!dateStr) return '';
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'Just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(dateStr).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}
