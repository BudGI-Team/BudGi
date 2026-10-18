import React, { useState, useMemo } from 'react';
import { fmt } from '../../lib/utils';
import './RadialInsightsChart.css';

// Distinct curated category palette matching Image 1
const RAY_COLORS = [
  '#F5A623', // Saffron / Warm Gold
  '#E07A5F', // Terracotta / Coral
  '#38BDF8', // BudGi Sky / Cyan
  '#1DBF81', // Emerald Green
  '#FFFFFF', // Crisp White (or Slate-900 in light)
  '#4A90E2', // Slate Sky Blue
  '#F472B6', // Warm Rose
  '#A78BFA', // Soft Lavender
  '#34D399', // Mint Teal
  '#FBBF24', // Amber
  '#94A3B8', // Slate
  '#64748B', // Cool Charcoal
];

export default function RadialInsightsChart({
  categories = [],
  totalSpending = 0,
  totalIncome = 0,
  netSavings = 0,
  currency = 'INR',
  monthLabel = 'July 2026',
  className = '',
}) {
  const [activeTab, setActiveTab] = useState('spending'); // 'spending' | 'income' | 'net'
  const [hoveredIdx, setHoveredIdx] = useState(null);

  // Compute total based on active tab
  const displayTotal = useMemo(() => {
    if (activeTab === 'spending') return totalSpending;
    if (activeTab === 'income') return totalIncome;
    return netSavings;
  }, [activeTab, totalSpending, totalIncome, netSavings]);

  const displayLabel = useMemo(() => {
    if (activeTab === 'spending') return 'SPENDING';
    if (activeTab === 'income') return 'INCOME';
    return 'NET SAVINGS';
  }, [activeTab]);

  // Normalize top 12 data slots (clock hours)
  const raySlots = useMemo(() => {
    const sorted = [...categories].sort((a, b) => (b.value || 0) - (a.value || 0));
    const maxValue = sorted[0]?.value || 1;

    // Build 12 slots around the clock dial
    return Array.from({ length: 12 }, (_, i) => {
      const cat = sorted[i];
      if (cat && cat.value > 0) {
        const ratio = Math.min(1, cat.value / maxValue);
        const percent = totalSpending > 0 ? Math.round((cat.value / totalSpending) * 100) : 0;
        return {
          id: cat.name || `cat-${i}`,
          name: cat.name || 'Category',
          value: cat.value,
          percent,
          color: RAY_COLORS[i % RAY_COLORS.length],
          isActive: true,
          // Inner radius 66, base 105, max up to 165
          outerRadius: 105 + ratio * 60,
        };
      }
      return {
        id: `empty-${i}`,
        name: null,
        value: 0,
        percent: 0,
        color: 'var(--border, #222630)',
        isActive: false,
        outerRadius: 102, // baseline length for inactive ray
      };
    });
  }, [categories, totalSpending]);

  const activeRay = hoveredIdx !== null ? raySlots[hoveredIdx] : null;

  const centerData = useMemo(() => {
    if (activeRay && activeRay.isActive) {
      return {
        title: activeRay.name.toUpperCase(),
        amount: fmt(activeRay.value, currency),
        subtitle: `${activeRay.percent}% of total`,
        color: activeRay.color,
      };
    }
    return {
      title: displayLabel,
      amount: fmt(displayTotal, currency),
      subtitle: monthLabel,
      color: 'var(--text-primary)',
    };
  }, [activeRay, displayLabel, displayTotal, currency, monthLabel]);

  // Center coordinates
  const cx = 190;
  const cy = 190;
  const rInner = 64;

  return (
    <div className={`radial-insights-card ${className}`}>
      {/* ── Top Header with Filter Pills matching Image 1 ── */}
      <div className="radial-insights-header">
        <div>
          <h2 className="radial-insights-title">Insights</h2>
          <p className="radial-insights-sub">Interactive category velocity dial</p>
        </div>

        <div className="segmented-pill-group">
          <button
            type="button"
            className={`segmented-pill-item ${activeTab === 'spending' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('spending');
              setHoveredIdx(null);
            }}
          >
            Spending
          </button>
          <button
            type="button"
            className={`segmented-pill-item ${activeTab === 'income' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('income');
              setHoveredIdx(null);
            }}
          >
            Income
          </button>
          <button
            type="button"
            className={`segmented-pill-item ${activeTab === 'net' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('net');
              setHoveredIdx(null);
            }}
          >
            Net
          </button>
        </div>
      </div>

      {/* ── Radial Sunburst Clock Viewport ── */}
      <div className="radial-chart-viewport" onMouseLeave={() => setHoveredIdx(null)}>
        <svg viewBox="0 0 380 380" className="radial-svg" aria-label="Radial Insights Clock Chart">
          <defs>
            {/* Soft backdrop blur filter for center hub */}
            <filter id="hubShadow" x="-20%" y="-20%" width="140%" height="140%">
              <feDropShadow dx="0" dy="2" stdDeviation="6" floodOpacity="0.25" />
            </filter>
          </defs>

          {/* Dotted concentric guide circle tracks matching Image 1 */}
          <circle
            cx={cx}
            cy={cy}
            r="105"
            fill="none"
            stroke="var(--border)"
            strokeWidth="1.2"
            strokeDasharray="3 4"
            opacity="0.6"
          />
          <circle
            cx={cx}
            cy={cy}
            r="145"
            fill="none"
            stroke="var(--border)"
            strokeWidth="1.2"
            strokeDasharray="3 4"
            opacity="0.35"
          />

          {/* 12 Radial Clock Rays */}
          {raySlots.map((ray, i) => {
            // Angle in radians (-90 deg places index 0 at top / 12 o'clock)
            const angleDeg = i * 30 - 90;
            const angleRad = (angleDeg * Math.PI) / 180;

            const x1 = cx + rInner * Math.cos(angleRad);
            const y1 = cy + rInner * Math.sin(angleRad);
            const x2 = cx + ray.outerRadius * Math.cos(angleRad);
            const y2 = cy + ray.outerRadius * Math.sin(angleRad);

            const isHovered = hoveredIdx === i;
            const strokeColor = ray.isActive
              ? ray.color === '#FFFFFF'
                ? 'var(--text-primary)'
                : ray.color
              : 'var(--border-strong, #2A2C35)';

            return (
              <g
                key={ray.id}
                onMouseEnter={() => ray.isActive && setHoveredIdx(i)}
                className="radial-ray-group"
              >
                {/* Hit area */}
                <line
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                  stroke="transparent"
                  strokeWidth="24"
                  strokeLinecap="round"
                  style={{ cursor: ray.isActive ? 'pointer' : 'default' }}
                />
                {/* Visible Pill Ray */}
                <line
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                  stroke={strokeColor}
                  strokeWidth={isHovered ? 17 : 13}
                  strokeLinecap="round"
                  className="radial-ray"
                  style={{
                    opacity: hoveredIdx !== null && !isHovered ? 0.35 : 1,
                    transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
                  }}
                />
              </g>
            );
          })}

          {/* ── Solid Center Hub Circle (matching Image 1) ── */}
          <circle
            cx={cx}
            cy={cy}
            r="55"
            fill="var(--bg-surface)"
            stroke="var(--border)"
            strokeWidth="1.5"
            className="radial-center-hub"
            filter="url(#hubShadow)"
          />

          {/* ── Center Hub Text ── */}
          <text
            x={cx}
            y={cy - 16}
            textAnchor="middle"
            fill="var(--text-muted)"
            fontSize="10"
            fontWeight="700"
            letterSpacing="0.08em"
            fontFamily="var(--font-display)"
            style={{ textTransform: 'uppercase', transition: 'fill 0.2s' }}
          >
            {centerData.title}
          </text>

          <text
            x={cx}
            y={cy + 10}
            textAnchor="middle"
            fill="var(--text-primary)"
            fontSize="21"
            fontWeight="800"
            letterSpacing="-0.03em"
            fontFamily="var(--font-display)"
            style={{ transition: 'all 0.2s' }}
          >
            {centerData.amount}
          </text>

          <text
            x={cx}
            y={cy + 27}
            textAnchor="middle"
            fill="var(--text-secondary)"
            fontSize="10.5"
            fontWeight="500"
            fontFamily="var(--font-body)"
            style={{ transition: 'fill 0.2s' }}
          >
            {centerData.subtitle}
          </text>
        </svg>
      </div>

      {/* ── Quick category legend chips below ── */}
      {categories.length > 0 && (
        <div className="radial-legend-strip">
          {raySlots
            .filter((r) => r.isActive)
            .slice(0, 6)
            .map((ray, i) => {
              const isSelected = hoveredIdx === i;
              return (
                <button
                  type="button"
                  key={ray.id}
                  className={`radial-legend-pill ${isSelected ? 'active' : ''}`}
                  onMouseEnter={() => setHoveredIdx(i)}
                  onMouseLeave={() => setHoveredIdx(null)}
                >
                  <span
                    className="radial-legend-dot"
                    style={{
                      background:
                        ray.color === '#FFFFFF' ? 'var(--text-primary)' : ray.color,
                    }}
                  />
                  <span>{ray.name}</span>
                  <span style={{ color: 'var(--text-primary)', fontWeight: 700 }}>
                    {fmt(ray.value, currency)}
                  </span>
                </button>
              );
            })}
        </div>
      )}
    </div>
  );
}
