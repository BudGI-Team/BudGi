import { useEffect, Suspense, lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import useStore from './store/useStore';
import AppShell from './components/layout/AppShell';
import { WebSocketProvider } from './hooks/useWebSocket';
import ErrorBoundary from './components/ui/ErrorBoundary';

// Pre-load essential public routes synchronously
import Landing from './pages/Landing';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import VerifyEmail from './pages/VerifyEmail';
import NotFound from './pages/NotFound';

// Lazy-load internal application modules to drop initial bundle size
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Budgets = lazy(() => import('./pages/Budgets'));
const Analytics = lazy(() => import('./pages/Analytics'));
const AIChat = lazy(() => import('./pages/AIChat'));
const Goals = lazy(() => import('./pages/Goals'));
const Onboarding = lazy(() => import('./pages/Onboarding'));
const Transactions = lazy(() => import('./pages/Transactions'));
const Settings = lazy(() => import('./pages/Settings'));

function PrivateRoute({ children, requireOnboarded = false }) {
  const user = useStore((s) => s.user);
  const token = useStore((s) => s.token);

  if (!token || !user) return <Navigate to="/login" replace />;
  if (requireOnboarded && user.onboarded !== true) return <Navigate to="/onboarding" replace />;

  return children;
}

function PublicRoute({ children }) {
  const user = useStore((s) => s.user);
  const token = useStore((s) => s.token);
  return token && user ? <Navigate to="/app/dashboard" replace /> : children;
}

import BrandLoader from './components/ui/BrandLoader';
import NetworkStatus from './components/ui/NetworkStatus';

// Sleek fallback loader for Suspense
const PageLoader = () => <BrandLoader fullPage message="Initializing BudGi..." />;

export default function App() {
  const { theme } = useStore();

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme || 'dark');
  }, [theme]);

  return (
    <ErrorBoundary>
      <NetworkStatus />
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route
            path="/login"
            element={
              <PublicRoute>
                <Login />
              </PublicRoute>
            }
          />
          <Route
            path="/register"
            element={
              <PublicRoute>
                <Register />
              </PublicRoute>
            }
          />
          <Route
            path="/forgot-password"
            element={
              <PublicRoute>
                <ForgotPassword />
              </PublicRoute>
            }
          />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/verify-email" element={<VerifyEmail />} />
          <Route
            path="/onboarding"
            element={
              <PrivateRoute>
                <Onboarding />
              </PrivateRoute>
            }
          />
          <Route
            path="/app"
            element={
              <PrivateRoute requireOnboarded>
                <WebSocketProvider>
                  <AppShell />
                </WebSocketProvider>
              </PrivateRoute>
            }
          >
            <Route index element={<Dashboard />} />
            <Route path="dashboard" element={<Dashboard />} />
            <Route path="transactions" element={<Transactions />} />
            <Route path="budgets" element={<Budgets />} />
            <Route path="analytics" element={<Analytics />} />
            <Route path="goals" element={<Goals />} />
            <Route path="ai" element={<AIChat />} />
            <Route path="settings" element={<Settings />} />
          </Route>
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}
