import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { WifiOff, Wifi } from '../../lib/icons';

export default function NetworkStatus() {
  const [isOnline, setIsOnline] = useState(typeof navigator !== 'undefined' ? navigator.onLine : true);
  const [showRestored, setShowRestored] = useState(false);

  useEffect(() => {
    const handleOnline = () => {
      setIsOnline(true);
      setShowRestored(true);
      const timer = setTimeout(() => setShowRestored(false), 3000);
      return () => clearTimeout(timer);
    };

    const handleOffline = () => {
      setIsOnline(false);
      setShowRestored(false);
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return (
    <AnimatePresence>
      {!isOnline && (
        <motion.div
          key="offline-banner"
          initial={{ opacity: 0, y: -40 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -40 }}
          transition={{ duration: 0.25 }}
          style={{
            position: 'fixed',
            top: 12,
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 999999,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '8px 16px',
            background: 'rgba(239, 68, 68, 0.95)',
            backdropFilter: 'blur(8px)',
            color: '#fff',
            borderRadius: '999px',
            fontSize: '13px',
            fontWeight: 500,
            boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
          }}
        >
          <WifiOff size={15} />
          <span>You are offline. Showing cached financial workspace.</span>
        </motion.div>
      )}

      {showRestored && (
        <motion.div
          key="restored-banner"
          initial={{ opacity: 0, y: -40 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -40 }}
          transition={{ duration: 0.25 }}
          style={{
            position: 'fixed',
            top: 12,
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 999999,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '8px 16px',
            background: 'rgba(16, 185, 129, 0.95)',
            backdropFilter: 'blur(8px)',
            color: '#fff',
            borderRadius: '999px',
            fontSize: '13px',
            fontWeight: 500,
            boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
          }}
        >
          <Wifi size={15} />
          <span>Connection restored · BudGi is synced</span>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
