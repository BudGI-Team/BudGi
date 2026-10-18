// ── Currency formatting ───────────────────────────────────────
export function fmt(n, currency = 'INR', compact = false) {
  if (n === null || n === undefined || isNaN(n)) return '';
  const num = Number(n);
  const isNeg = num < 0;
  const absNum = Math.abs(num);
  const sym = currency === 'INR' ? '₹' : currency === 'USD' ? '$' : currency;

  if (compact) {
    let formatted = '';
    if (absNum >= 10000000) formatted = `${(absNum / 10000000).toFixed(2)}Cr`;
    else if (absNum >= 100000) formatted = `${(absNum / 100000).toFixed(2)}L`;
    else if (absNum >= 1000) formatted = `${(absNum / 1000).toFixed(1)}K`;
    else formatted = `${Math.round(absNum)}`;
    return `${isNeg ? '-' : ''}${sym}${formatted}`;
  }

  const formattedNum = absNum.toLocaleString(currency === 'INR' ? 'en-IN' : 'en-US', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  });

  return `${isNeg ? '-' : ''}${sym}${formattedNum}`;
}

export function formatCompact(amount) {
  if (amount == null || isNaN(amount)) return '₹0';
  const num = Number(amount);
  const isNeg = num < 0;
  const abs = Math.abs(num);
  let val = '';
  if (abs >= 100000) val = `${(abs / 100000).toFixed(1)}L`;
  else if (abs >= 1000) val = `${(abs / 1000).toFixed(1)}K`;
  else val = `${Math.round(abs)}`;
  return `${isNeg ? '-' : ''}₹${val}`;
}

// ── Date formatting ───────────────────────────────────────────
export function formatDate(date, style = 'medium') {
  if (!date) return '';
  const d = typeof date === 'string' ? new Date(date) : date;
  if (isNaN(d)) return '';
  const opts = {
    short: { day: 'numeric', month: 'short' },
    medium: { day: 'numeric', month: 'short', year: 'numeric' },
    long: { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' },
    month: { month: 'short', year: 'numeric' },
  };
  return d.toLocaleDateString('en-IN', opts[style] || opts.medium);
}

export function relativeTime(date) {
  const d = typeof date === 'string' ? new Date(date) : date;
  const diff = (Date.now() - d.getTime()) / 1000;
  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  if (diff < 604800) return `${Math.floor(diff / 86400)}d ago`;
  return formatDate(d, 'short');
}

export function currentMonthRange() {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1);
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);
  return { start, end };
}

// ── Hex to RGBA helper ────────────────────────────────────────
export function hexToRgba(hex, alpha = 0.12) {
  if (!hex || typeof hex !== 'string' || !hex.startsWith('#')) return `rgba(126, 139, 155, ${alpha})`;
  let c = hex.substring(1);
  if (c.length === 3) c = c.split('').map((x) => x + x).join('');
  const num = parseInt(c, 16);
  if (isNaN(num)) return `rgba(126, 139, 155, ${alpha})`;
  const r = (num >> 16) & 255;
  const g = (num >> 8) & 255;
  const b = num & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

// ── Category helpers (BudGi Signature Calm Palette) ───────────
const CATEGORY_META = {
  'Food & Dining': { icon: '🍽', color: '#E07A5F', badge: 'badge-food' },
  Food: { icon: '🍽', color: '#E07A5F', badge: 'badge-food' },
  Groceries: { icon: '🛒', color: '#38A169', badge: 'badge-groceries' },
  Shopping: { icon: '🛍', color: '#8E7CC3', badge: 'badge-shopping' },
  Transport: { icon: '🚗', color: '#4A90E2', badge: 'badge-transport' },
  Subscriptions: { icon: '🔄', color: '#7E69AB', badge: 'badge-subscriptions' },
  Rent: { icon: '🏠', color: '#D97706', badge: 'badge-rent' },
  'Utilities & Bills': { icon: '⚡', color: '#5C768D', badge: 'badge-bills' },
  Utilities: { icon: '⚡', color: '#5C768D', badge: 'badge-bills' },
  Bills: { icon: '📄', color: '#5C768D', badge: 'badge-bills' },
  Insurance: { icon: '🛡', color: '#4F6FD4', badge: 'badge-insurance' },
  'Health & Wellness': { icon: '💊', color: '#2A9D8F', badge: 'badge-health' },
  Health: { icon: '💊', color: '#2A9D8F', badge: 'badge-health' },
  Entertainment: { icon: '🎬', color: '#D95D74', badge: 'badge-entertainment' },
  Investment: { icon: '📈', color: '#2E7D5E', badge: 'badge-investment' },
  Education: { icon: '📚', color: '#5C6BC0', badge: 'badge-education' },
  Recharge: { icon: '📱', color: '#E67E52', badge: 'badge-recharge' },
  Transfer: { icon: '🔁', color: '#5A78A5', badge: 'badge-transfer' },
  Income: { icon: '💵', color: '#34A853', badge: 'badge-income' },
  Salary: { icon: '💼', color: '#34A853', badge: 'badge-income' },
  Freelance: { icon: '💻', color: '#25A18E', badge: 'badge-income' },
  Travel: { icon: '✈️', color: '#3A9AB7', badge: 'badge-transport' },
  Other: { icon: '📌', color: '#7E8B9B', badge: 'badge-other' },
  Others: { icon: '📌', color: '#7E8B9B', badge: 'badge-other' },
};

export function getCategoryMeta(category) {
  if (!category) {
    return {
      icon: '📌',
      color: '#7E8B9B',
      badge: 'badge-other',
      bg: 'rgba(126, 139, 155, 0.12)',
      border: 'rgba(126, 139, 155, 0.22)',
    };
  }
  const norm = String(category).toLowerCase().trim();
  const foundKey = Object.keys(CATEGORY_META).find(
    (k) => k.toLowerCase() === norm || norm.includes(k.toLowerCase()) || k.toLowerCase().includes(norm),
  );
  const base = foundKey
    ? CATEGORY_META[foundKey]
    : {
        icon: '📌',
        color: '#7E8B9B',
        badge: 'badge-other',
      };
  return {
    ...base,
    bg: hexToRgba(base.color, 0.12),
    border: hexToRgba(base.color, 0.22),
  };
}

export const CATEGORIES = [
  'Food & Dining',
  'Groceries',
  'Shopping',
  'Transport',
  'Subscriptions',
  'Rent',
  'Utilities & Bills',
  'Insurance',
  'Health & Wellness',
  'Entertainment',
  'Investment',
  'Education',
  'Recharge',
  'Transfer',
  'Salary',
  'Freelance',
  'Income',
  'Others',
];

// ── Percentage ────────────────────────────────────────────────
export function pct(value, total) {
  if (!total) return 0;
  return Math.round((value / total) * 100);
}

export function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

// ── Spending personality detection ────────────────────────────
export function detectSpendingPersonality(transactions) {
  if (!transactions || transactions.length < 5) return 'Emerging saver';

  const expenses = transactions.filter((t) => t.type === 'expense');
  const totalSpend = expenses.reduce((s, t) => s + t.amount, 0);
  const avgTx = totalSpend / expenses.length;

  const byDow = Array(7).fill(0);
  expenses.forEach((t) => {
    const d = new Date(t.date).getDay();
    byDow[d] += t.amount;
  });
  const weekendSpend = byDow[0] + byDow[6];
  const weekdaySpend = byDow.slice(1, 6).reduce((a, b) => a + b, 0);
  const weekendRatio = weekdaySpend > 0 ? weekendSpend / (weekdaySpend / 5) : 1;

  const shoppingPct = pct(
    expenses.filter((t) => t.category === 'Shopping').reduce((s, t) => s + t.amount, 0),
    totalSpend,
  );

  if (shoppingPct > 30) return 'Impulse buyer';
  if (weekendRatio > 2.5) return 'Weekend spender';
  if (avgTx > 5000) return 'Big ticket buyer';
  return 'Mindful spender';
}

// ── Chart colors (BudGi Harmonious Visual Analytics Tone) ─────
export const CHART_COLORS = [
  '#5E72E4',
  '#38A169',
  '#E07A5F',
  '#4A90E2',
  '#8E7CC3',
  '#D97706',
  '#2A9D8F',
  '#D95D74',
  '#3A9AB7',
  '#5C768D',
];

// ── Local storage helpers ─────────────────────────────────────
export const storage = {
  get: (key) => {
    try {
      return JSON.parse(localStorage.getItem(key));
    } catch {
      return null;
    }
  },
  set: (key, value) => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {}
  },
  remove: (key) => localStorage.removeItem(key),
};

// ── Debounce ──────────────────────────────────────────────────
export function debounce(fn, ms) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

// ── Class names helper ────────────────────────────────────────
export function cx(...classes) {
  return classes.filter(Boolean).join(' ');
}
