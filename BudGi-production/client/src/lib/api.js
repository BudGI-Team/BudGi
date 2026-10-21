// src/lib/api.js
// Centralised HTTP client — single source of truth for ALL backend calls.

import axios from 'axios';
import toast from 'react-hot-toast';
import useStore from '../store/useStore';

const getBaseURL = () => {
  if (import.meta.env.VITE_API_URL) {
    return import.meta.env.VITE_API_URL;
  }
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname || '';
    if (hostname.includes('vercel.app') || hostname.includes('budgi')) {
      return 'https://budgi-175t.onrender.com';
    }
    if (hostname && hostname !== 'localhost' && hostname !== '127.0.0.1') {
      return `http://${hostname}:5000`;
    }
  }
  return 'http://localhost:5000';
};

const api = axios.create({
  baseURL: getBaseURL(),
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT on every request
api.interceptors.request.use((config) => {
  const token = useStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Global response handler with queued token refresh
let lastRateLimitToast = 0;
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(token);
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (res) => res,
  async (err) => {
    const originalRequest = err.config;
    const status = err.response?.status;
    const envelope = err.response?.data;
    const message = envelope?.errors?.[0]?.message || envelope?.message || err.message || 'Something went wrong';

    if (status === 401 && originalRequest && !originalRequest._retry) {
      const url = originalRequest.url || '';
      const isAuthEndpoint =
        url.includes('/auth/login') ||
        url.includes('/auth/register') ||
        url.includes('/auth/refresh') ||
        url.includes('/auth/phone/login-') ||
        url.includes('/auth/phone/register-') ||
        url.includes('/auth/forgot-password') ||
        url.includes('/auth/reset-password') ||
        url.includes('/auth/check-username');

      if (isAuthEndpoint) {
        return Promise.reject({ ...err, userMessage: message });
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((newToken) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
            return api(originalRequest);
          })
          .catch((refreshErr) => Promise.reject(refreshErr));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const storedRefresh = localStorage.getItem('budgi_refresh_token');
        if (!storedRefresh) {
          throw new Error('No refresh token available');
        }

        // Direct axios post to avoid recursive 401 interceptor
        const res = await axios.post(`${api.defaults.baseURL}/api/v1/auth/refresh`, {
          refreshToken: storedRefresh,
          platform: 'web',
        });

        const data = res.data?.data || res.data;
        const newToken = data.token;
        const newRefreshToken = data.refreshToken;
        const user = data.user;

        if (newToken) {
          useStore.getState().setAuth(user || useStore.getState().user, newToken);
          if (newRefreshToken) {
            localStorage.setItem('budgi_refresh_token', newRefreshToken);
          }
          processQueue(null, newToken);
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
          return api(originalRequest);
        } else {
          throw new Error('Token refresh failed to return access token');
        }
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        useStore.getState().logout();
        localStorage.removeItem('budgi_refresh_token');
        localStorage.removeItem('budgi_token');
        localStorage.removeItem('budgi_user');
        localStorage.removeItem('budgi-store');
        if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
        return Promise.reject({ ...err, userMessage: 'Session expired. Please sign in again.' });
      } finally {
        isRefreshing = false;
      }
    }

    if (status === 401) {
      return Promise.reject({ ...err, userMessage: message });
    }

    // Rate limit — show a single toast, don't spam the user
    if (status === 429) {
      const now = Date.now();
      if (now - lastRateLimitToast > 10000) {
        lastRateLimitToast = now;
        toast.error('Slow down — too many requests. Please wait a moment.', { id: 'rate-limit', duration: 5000 });
      }
      return Promise.reject({ ...err, userMessage: 'Rate limited — please wait', silent: true });
    }

    if (status >= 500) {
      toast.error('Server error — please try again');
    }

    return Promise.reject({ ...err, userMessage: message });
  },
);

// ── Auth ──────────────────────────────────────────────────────────────────────
export const authApi = {
  register: (data) => api.post('/api/v1/auth/register', data).then((r) => r.data),
  login: (data) => api.post('/api/v1/auth/login', { ...data, platform: 'web' }).then((r) => r.data),
  googleLogin: (idToken) => api.post('/api/v1/auth/google', { idToken, platform: 'web' }).then((r) => r.data),
  phoneLoginRequest: (phone) => api.post('/api/v1/auth/phone/login-request', { phone }).then((r) => r.data),
  phoneLoginVerify: (phone, otp) => api.post('/api/v1/auth/phone/login-verify', { phone, otp, platform: 'web' }).then((r) => r.data),
  firebasePhoneLogin: (idToken) => api.post('/api/v1/auth/firebase/phone-login', { idToken, platform: 'web' }).then((r) => r.data),
  firebasePhoneRegister: (data) => api.post('/api/v1/auth/firebase/phone-register', { ...data, platform: 'web' }).then((r) => r.data),
  phoneRegisterRequest: (data) => api.post('/api/v1/auth/phone/register-request', data).then((r) => r.data),
  phoneRegisterVerify: (data) => api.post('/api/v1/auth/phone/register-verify', { ...data, platform: 'web' }).then((r) => r.data),
  sendPhoneOtp: (phone, preAuthToken) => api.post('/api/v1/auth/phone/send-otp', { phone, preAuthToken }).then((r) => r.data),
  verifyPhoneOtp: (phone, otp, preAuthToken) => api.post('/api/v1/auth/phone/verify-otp', { phone, otp, preAuthToken, platform: 'web' }).then((r) => r.data),
  refresh: (refreshToken) => api.post('/api/v1/auth/refresh', { refreshToken, platform: 'web' }).then((r) => r.data),
  logout: () => {
    const refreshToken = typeof localStorage !== 'undefined' ? localStorage.getItem('budgi_refresh_token') : null;
    return api.post('/api/v1/auth/logout', { refreshToken }).then((r) => r.data);
  },
  me: () => api.get('/api/v1/auth/me').then((r) => r.data),
  checkUsername: (username) => api.get('/api/v1/auth/check-username', { params: { username } }).then((r) => r.data),
  updateProfile: (data) => api.put('/api/v1/auth/profile', data).then((r) => r.data),
  forgotPassword: (email) => api.post('/api/v1/auth/forgot-password', { email }).then((r) => r.data),
  resetPassword: (token, password) => api.post('/api/v1/auth/reset-password', { token, password }).then((r) => r.data),
  verifyEmail: (token) => api.post('/api/v1/auth/verify-email', { token }).then((r) => r.data),
  resendVerification: (email) => api.post('/api/v1/auth/resend-verification', { email }).then((r) => r.data),
  deleteAccount: () => api.delete('/api/v1/auth/account').then((r) => r.data),
};

// ── Transactions ──────────────────────────────────────────────────────────────
export const transactionsApi = {
  list: (params) => api.get('/api/v1/transactions', { params }).then((r) => r.data),

  create: (data) => api.post('/api/v1/transactions', data).then((r) => r.data),

  bulkCreate: (transactions) => api.post('/api/v1/transactions', transactions).then((r) => r.data),

  update: (id, data) => api.put(`/api/v1/transactions/${id}`, data).then((r) => r.data),

  remove: (id) => api.delete(`/api/v1/transactions/${id}`).then((r) => r.data),

  clear: (params) => api.delete('/api/v1/transactions/clear', { params }).then((r) => r.data),

  summary: (params) => api.get('/api/v1/transactions/summary', { params }).then((r) => r.data),

  importCSV: (formData) =>
    api
      .post('/api/v1/transactions/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data),

  scanReceipt: (formData) =>
    api
      .post('/api/v1/transactions/scan', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data),

  // Export transactions as JSON (for data download)
  exportAll: (params) => api.get('/api/v1/transactions', { params: { ...params, limit: 10000 } }).then((r) => r.data),
};

// ── Budget ────────────────────────────────────────────────────────────────────
export const budgetApi = {
  list: () => api.get('/api/v1/budgets').then((r) => r.data),
  create: (data) => api.post('/api/v1/budgets', data).then((r) => r.data),
  update: (id, data) => api.put(`/api/v1/budgets/${id}`, data).then((r) => r.data),
  remove: (id) => api.delete(`/api/v1/budgets/${id}`).then((r) => r.data),
  upsert: (data) =>
    data._id
      ? api.put(`/api/v1/budgets/${data._id}`, data).then((r) => r.data)
      : api.post('/api/v1/budgets', data).then((r) => r.data),
};

// ── AI ────────────────────────────────────────────────────────────────────────
export const aiApi = {
  analytics: () => api.get('/api/v1/analytics/summary').then((r) => r.data),

  predictCategory: (description) =>
    api.post('/api/v1/ai/analyze', { userMessage: `Categorize this transaction: ${description}` }).then((r) => r.data),

  status: () => api.get('/health').then((r) => r.data),

  insights: () => api.get('/api/v1/ai/insights').then((r) => r.data),
  suggestions: () => api.get('/api/v1/ai/suggestions').then((r) => r.data),
  predict: () => api.get('/api/v1/ai/predict').then((r) => r.data),
  trends: (months = 6) => api.get('/api/v1/ai/trends', { params: { months } }).then((r) => r.data),
  healthScore: () => api.get('/api/v1/ai/health-score').then((r) => r.data),
  analyze: (userMessage) => api.post('/api/v1/ai/analyze', { userMessage }).then((r) => r.data),
  chat: (messages) => api.post('/api/v1/ai/chat', { messages }).then((r) => r.data),
  getHistory: () => api.get('/api/v1/ai/chat/history').then((r) => r.data),
  clearHistory: () => api.delete('/api/v1/ai/chat/history').then((r) => r.data),
  smartBudgets: () => api.get('/api/v1/ai/smart-budgets').then((r) => r.data),
};

// ── Alerts ────────────────────────────────────────────────────────────────────
export const alertsApi = {
  list: (params) => api.get('/api/v1/alerts', { params }).then((r) => r.data),
  unreadCount: () => api.get('/api/v1/alerts/count').then((r) => r.data),
  markRead: (alertIds) => api.put('/api/v1/alerts/read', { alertIds }).then((r) => r.data),
  markAllRead: () => api.put('/api/v1/alerts/read', { all: true }).then((r) => r.data),
  clearAll: () => api.delete('/api/v1/alerts').then((r) => r.data),
};

// ── Analytics ─────────────────────────────────────────────────────────────────
export const analyticsApi = {
  summary: (params) => api.get('/api/v1/analytics/summary', { params }).then((r) => r.data),
  predict: (params) => api.get('/api/v1/analytics/predict', { params }).then((r) => r.data),
  dashboard: () => api.get('/api/v1/analytics/dashboard').then((r) => r.data),
  report: (params) => api.get('/api/v1/analytics/report', { params }).then((r) => r.data),
  downloadPdf: (params) =>
    api.get('/api/v1/analytics/report/pdf', { params, responseType: 'blob' }).then((r) => {
      const blob = new Blob([r.data], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `BudGi-Report.pdf`;
      a.click();
      URL.revokeObjectURL(url);
      return { success: true };
    }),
};

// ── Mobile / Config ───────────────────────────────────────────────────────────
export const mobileApi = {
  config: () => api.get('/api/v1/mobile/config').then((r) => r.data),
};

// ── Goals ─────────────────────────────────────────────────────────────────────
export const goalsApi = {
  list: () => api.get('/api/v1/goals').then((r) => r.data),
  create: (data) => api.post('/api/v1/goals', data).then((r) => r.data),
  update: (id, data) => api.put(`/api/v1/goals/${id}`, data).then((r) => r.data),
  addSavings: (id, amount) => api.put(`/api/v1/goals/${id}`, { addAmount: amount }).then((r) => r.data),
  remove: (id) => api.delete(`/api/v1/goals/${id}`).then((r) => r.data),
};

export default api;
