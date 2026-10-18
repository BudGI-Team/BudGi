import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useEffect, useState, Suspense } from 'react';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import useStore from '../../store/useStore';
import './AppShell.css';

const SHORTCUTS = [
  { key: 'd', path: '/app/dashboard', label: 'Dashboard' },
  { key: 't', path: '/app/transactions', label: 'Transactions' },
  { key: 'b', path: '/app/budgets', label: 'Budgets' },
  { key: 'a', path: '/app/analytics', label: 'Analytics' },
  { key: 'g', path: '/app/goals', label: 'Goals' },
  { key: 'c', path: '/app/ai', label: 'BudGi Copilot' },
  { key: 's', path: '/app/settings', label: 'Settings' },
];

import BrandLoader from '../ui/BrandLoader';

const ContentLoader = () => <BrandLoader minHeight="65vh" message="Loading workspace..." />;

export default function AppShell() {
  const sidebarOpen = useStore((s) => s.sidebarOpen);
  const location = useLocation();
  const navigate = useNavigate();
  const [toast, setToast] = useState(null);

  // Preload sub-screens on idle so transitions are instantaneous and never trigger blank Suspense
  useEffect(() => {
    const preload = () => {
      import('../../pages/Transactions');
      import('../../pages/Budgets');
      import('../../pages/Analytics');
      import('../../pages/Goals');
      import('../../pages/AIChat');
      import('../../pages/Settings');
    };
    if (typeof window !== 'undefined' && 'requestIdleCallback' in window) {
      window.requestIdleCallback(preload);
    } else {
      setTimeout(preload, 400);
    }
  }, []);

  useEffect(() => {
    const onKey = (e) => {
      // Only fire when no input/textarea is focused and modifier key is held (e.g. Alt)
      const tag = document.activeElement?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
      // Use Alt + key to avoid clashing with browser shortcuts
      if (!e.altKey) return;
      const sc = SHORTCUTS.find((s) => s.key === e.key.toLowerCase());
      if (!sc) return;
      e.preventDefault();
      navigate(sc.path);
      setToast(`→ ${sc.label}`);
      setTimeout(() => setToast(null), 1600);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [navigate]);

  // Reset scroll to top on every route change so sub-screens don't stay scrolled down
  useEffect(() => {
    const el = document.querySelector('.shell-content');
    if (el && el.scrollTop > 0) {
      el.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    }
  }, [location.pathname]);

  return (
    <div className={`shell ${sidebarOpen ? 'shell-open' : 'shell-collapsed'}`}>
      <Sidebar />
      <div className="shell-main">
        <TopBar />
        <main className="shell-content">
          <Suspense fallback={<ContentLoader />}>
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0.94 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.1, ease: 'easeOut' }}
              style={{
                minHeight: '100%',
                width: '100%',
                display: 'flex',
                flexDirection: 'column',
                position: 'relative',
              }}
            >
              <Outlet />
            </motion.div>
          </Suspense>
        </main>
      </div>

      {/* Keyboard shortcut toast */}
      <AnimatePresence>
        {toast && (
          <motion.div
            className="kbd-toast"
            initial={{ opacity: 0, y: 12, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.95 }}
            transition={{ duration: 0.22 }}
          >
            {toast}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
