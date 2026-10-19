import { useEffect, useRef, useState, createContext, useContext } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import useStore from '../store/useStore';

const WebSocketContext = createContext(null);

export const useWebSocket = () => useContext(WebSocketContext);

export function WebSocketProvider({ children }) {
  const token = useStore((s) => s.token);
  const qc = useQueryClient();
  const ws = useRef(null);
  const [isConnected, setIsConnected] = useState(false);
  const reconnectAttempt = useRef(0);

  useEffect(() => {
    if (!token) {
      if (ws.current) {
        ws.current.close(1000, 'logout');
        ws.current = null;
      }
      setIsConnected(false);
      reconnectAttempt.current = 0;
      return;
    }

    let reconnectTimer;
    let pingTimer;
    let isUnmounting = false;

    const connect = () => {
      // Prevent duplicate connections
      if (ws.current?.readyState === WebSocket.OPEN || ws.current?.readyState === WebSocket.CONNECTING) return;

      const baseUrl = import.meta.env.VITE_API_URL
        ? import.meta.env.VITE_API_URL
        : typeof window !== 'undefined' &&
            (window.location.hostname.includes('vercel.app') || window.location.hostname.includes('budgi'))
          ? 'https://budgi-175t.onrender.com'
          : typeof window !== 'undefined' &&
              window.location.hostname &&
              window.location.hostname !== 'localhost' &&
              window.location.hostname !== '127.0.0.1'
            ? `http://${window.location.hostname}:5000`
            : 'http://localhost:5000';
      const wsUrl = baseUrl.replace(/^http/, 'ws').replace(/\/api$/, '') + '/ws';

      const socket = new WebSocket(wsUrl);
      ws.current = socket;

      socket.onopen = () => {
        socket.send(JSON.stringify({ type: 'AUTH', token }));
        setIsConnected(true);
        reconnectAttempt.current = 0; // Reset backoff on successful connect
        if (import.meta.env.DEV) console.log('[WS] Connected');

        // NOTE: Do NOT call qc.invalidateQueries() here — it causes a thundering herd
        // of requests that triggers the rate limiter. Data is already fresh from initial page load.

        pingTimer = setInterval(() => {
          if (socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({ type: 'PING' }));
          }
        }, 30000);
      };

      socket.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);

          if (msg.type === 'AUTH_OK') {
            if (import.meta.env.DEV) console.log('[WS] Authenticated as', msg.userId);
            return;
          }

          if (msg.type === 'PONG') return; // Ignore pong responses

          // ── Financial data change events ──
          if (msg.type === 'TRANSACTION_ADDED' || msg.type === 'TRANSACTION_DELETED') {
            qc.invalidateQueries({ queryKey: ['transactions'] });
            qc.invalidateQueries({ queryKey: ['analytics'] });
            qc.invalidateQueries({ queryKey: ['ai'] });
          }

          if (msg.type === 'IMPORT_DONE') {
            qc.invalidateQueries({ queryKey: ['transactions'] });
            qc.invalidateQueries({ queryKey: ['analytics'] });
            qc.invalidateQueries({ queryKey: ['ai'] });
            toast.success(`✅ Import completed: ${msg.data?.imported || 0} transactions added`);
          }

          if (msg.type === 'BUDGET_ADDED' || msg.type === 'BUDGET_UPDATED' || msg.type === 'BUDGET_DELETED') {
            qc.invalidateQueries({ queryKey: ['budgets'] });
            qc.invalidateQueries({ queryKey: ['analytics'] });
          }

          if (msg.type === 'GOAL_ADDED' || msg.type === 'GOAL_UPDATED' || msg.type === 'GOAL_DELETED') {
            qc.invalidateQueries({ queryKey: ['goals'] });
          }

          // ── Alert & notification events ──
          if (msg.type === 'ALERT_NEW') {
            qc.invalidateQueries({ queryKey: ['alerts'] });
            qc.invalidateQueries({ queryKey: ['alerts-count'] });
            qc.invalidateQueries({ queryKey: ['alerts-topbar'] });
            const alert = msg.data;
            if (alert?.severity === 'critical') {
              toast.error(alert.message || 'Budget exceeded!', { duration: 5000 });
            } else if (alert?.severity === 'warning') {
              toast(alert.message || 'Spending alert', { icon: '⚠️', duration: 4000 });
            } else if (alert?.type === 'ai_nightly_briefing') {
              toast(alert.message?.slice(0, 100) || 'New AI insight available', { icon: '🧠', duration: 5000 });
            }
          }

          if (msg.type === 'HEALTH_SCORE') {
            qc.invalidateQueries({ queryKey: ['ai', 'health-score'] });
          }

          if (msg.type === 'BUDGET_EXCEEDED') {
            qc.invalidateQueries({ queryKey: ['budgets'] });
            qc.invalidateQueries({ queryKey: ['alerts'] });
            qc.invalidateQueries({ queryKey: ['alerts-count'] });
            toast.error(`⚠️ Budget exceeded for ${msg.data?.category || 'a category'}!`, { duration: 5000 });
          }

          if (msg.type === 'WEEKLY_SUMMARY') {
            qc.invalidateQueries({ queryKey: ['alerts'] });
            qc.invalidateQueries({ queryKey: ['alerts-count'] });
            toast('📊 Your weekly spending summary is ready!', { icon: '📊', duration: 4000 });
          }
        } catch (err) {
          console.error('[WS] Error parsing message', err);
        }
      };

      socket.onclose = (e) => {
        setIsConnected(false);
        clearInterval(pingTimer);
        ws.current = null;

        // Only reconnect if not intentionally closed and component still mounted
        if (e.code !== 1000 && token && !isUnmounting) {
          // Exponential backoff: 1s, 2s, 4s, 8s, 16s, max 30s
          const backoff = Math.min(1000 * Math.pow(2, reconnectAttempt.current), 30000);
          reconnectAttempt.current += 1;
          reconnectTimer = setTimeout(connect, backoff);
        }
      };

      socket.onerror = () => {
        // Suppress console noise — onclose will handle reconnection
        if (socket.readyState !== WebSocket.CLOSED && socket.readyState !== WebSocket.CLOSING) {
          socket.close();
        }
      };
    };

    connect();

    return () => {
      isUnmounting = true;
      clearTimeout(reconnectTimer);
      clearInterval(pingTimer);
      if (ws.current) {
        const socket = ws.current;
        if (socket.readyState === WebSocket.CONNECTING) {
          socket.onopen = () => socket.close(1000, 'unmount');
        } else {
          socket.close(1000, 'unmount');
        }
        ws.current = null;
      }
    };
  }, [token, qc]);

  return <WebSocketContext.Provider value={{ isConnected }}>{children}</WebSocketContext.Provider>;
}
