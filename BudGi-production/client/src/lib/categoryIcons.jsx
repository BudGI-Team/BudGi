import {
  ShoppingBag,
  Car,
  Home,
  HeartPulse,
  FileText,
  Smartphone,
  DollarSign,
  Wallet,
  Plane,
  Tv,
  Shield,
  Scissors,
  Users,
  GraduationCap,
  Briefcase,
  Activity,
  Utensils,
} from './icons';

// Map your database category strings to premium Lucide icons
export function getCategoryIcon(categoryName) {
  if (!categoryName) {
    return Wallet;
  }

  const normalized = categoryName.toLowerCase();

  if (
    normalized.includes('food') ||
    normalized.includes('dining') ||
    normalized.includes('swiggy') ||
    normalized.includes('zomato')
  ) {
    return Utensils;
  }
  if (normalized.includes('shopping') || normalized.includes('amazon') || normalized.includes('flipkart')) {
    return ShoppingBag;
  }
  if (
    normalized.includes('transport') ||
    normalized.includes('uber') ||
    normalized.includes('cab') ||
    normalized.includes('ola')
  ) {
    return Car;
  }
  if (normalized.includes('home') || normalized.includes('rent')) {
    return Home;
  }
  if (normalized.includes('health') || normalized.includes('medical') || normalized.includes('pharmacy')) {
    return HeartPulse;
  }
  if (normalized.includes('bills') || normalized.includes('utilities') || normalized.includes('electricity')) {
    return FileText;
  }
  if (normalized.includes('electronics')) {
    return Smartphone;
  }
  if (normalized.includes('income') || normalized.includes('salary') || normalized.includes('freelance')) {
    return DollarSign;
  }
  if (normalized.includes('travel') || normalized.includes('flight')) {
    return Plane;
  }
  if (normalized.includes('entertainment') || normalized.includes('movie') || normalized.includes('netflix')) {
    return Tv;
  }
  if (normalized.includes('insurance')) {
    return Shield;
  }
  if (normalized.includes('personal') || normalized.includes('salon')) {
    return Scissors;
  }
  if (normalized.includes('education') || normalized.includes('college')) {
    return GraduationCap;
  }
  if (normalized.includes('family') || normalized.includes('kids')) {
    return Users;
  }
  if (normalized.includes('business') || normalized.includes('work')) {
    return Briefcase;
  }
  if (normalized.includes('fitness') || normalized.includes('gym')) {
    return Activity;
  }

  return Wallet;
}

import { getCategoryMeta } from './utils';

// Sleek, minimal BudGi category pill representation with soft tonal background & icon
export function CategoryPill({ category, size = 'sm', showIcon = true, className = '', style = {} }) {
  const meta = getCategoryMeta(category);
  const Icon = getCategoryIcon(category);
  const iconSize = size === 'xs' ? 10 : size === 'sm' ? 12 : 14;

  return (
    <span
      className={`cat-pill cat-pill-${size} ${className}`}
      style={{
        color: meta.color,
        backgroundColor: meta.bg,
        borderColor: meta.border,
        ...style,
      }}
    >
      {showIcon && <Icon size={iconSize} strokeWidth={2.2} style={{ flexShrink: 0 }} />}
      <span className="cat-pill-text">{category || 'Other'}</span>
    </span>
  );
}

// Sleek, calm circular/squircle avatar with muted tinted background for transaction lists
export function CategoryAvatar({ category, size = 34, className = '', style = {} }) {
  const meta = getCategoryMeta(category);
  const Icon = getCategoryIcon(category);
  const iconSize = Math.max(14, Math.round(size * 0.46));

  return (
    <div
      className={`cat-avatar ${className}`}
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: meta.bg,
        color: meta.color,
        border: `1px solid ${meta.border}`,
        flexShrink: 0,
        ...style,
      }}
      title={category || 'Category'}
    >
      <Icon size={iconSize} strokeWidth={2.2} />
    </div>
  );
}
