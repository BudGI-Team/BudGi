import React, { useState, useMemo } from 'react';
import { fmt } from '../../lib/utils';
import { Plus } from '../../lib/icons';
import './ConcentricGoalsChart.css';

// Signature concentric ring palette matching Image 2
const RING_PALETTE = [
  '#F5A623', // Warm Saffron Gold (Outermost ring)
  '#38BDF8', // BudGi Sky Blue (Second ring)
  '#10B981', // Mint Green (Third ring)
  '#CBD5E1', // Silver Off-white (Innermost ring)
];

export default function ConcentricGoalsChart({
  goals = [],
  totalSaved = 0,
  totalTarget = 0,
  currency = 'INR',
  onSelectGoal,
  onNewGoal,
  className = '',
}) {
  const [hoveredIdx, setHoveredIdx] = useState(null);

  // Take top 4 active goals with calibrated concentric colors matching Image 2
  const ringGoals = useMemo(() => {
    return goals.slice(0, 4).map((g, idx) => {
      const pct = Math.min(
        100,
        Math.max(
          0,
          g.targetAmount > 0
            ? Math.round((g.currentAmount / g.targetAmount) * 100)
            : 0
        )
      );
      return {
        ...g,
        pct,
        color: RING_PALETTE[idx % RING_PALETTE.length],
      };
    });
  }, [goals]);

  // Center hub display state
  const activeRing = hoveredIdx !== null ? ringGoals[hoveredIdx] : null;

  const centerInfo = useMemo(() => {
    if (activeRing) {
      return {
        label: activeRing.title.toUpperCase(),
        value: fmt(activeRing.currentAmount, currency),
        sub: `${activeRing.pct}% of ${fmt(activeRing.targetAmount, currency)}`,
        color: activeRing.color,
      };
    }
    return {
      label: 'SAVED',
      value: fmt(totalSaved, currency),
      sub: totalTarget > 0 ? `Target: ${fmt(totalTarget, currency)}` : 'Active goals',
      color: 'var(--text-primary)',
    };
  }, [activeRing, totalSaved, totalTarget, currency]);

  // Calibrated dimensions to fit perfectly without empty voids
  const size = 280;
  const center = size / 2; // 140
  const ringStroke = 11;
  const radii = [108, 90, 72, 54]; // concentric radii from outer to inner

  return (
    <div className={`concentric-goals-card ${className}`}>
      {/* ── Header with New Goal CTA matching Image 2 ── */}
      <div className="concentric-goals-header">
        <div>
          <h2 className="concentric-goals-title">Goals</h2>
          <p className="concentric-goals-sub">Portfolio progress rings</p>
        </div>

        {onNewGoal && (
          <button
            type="button"
            className="concentric-new-btn"
            onClick={onNewGoal}
          >
            <Plus size={15} />
            <span>New</span>
          </button>
        )}
      </div>

      {/* ── Concentric SVG Viewport ── */}
      <div
        className="concentric-viewport"
        onMouseLeave={() => setHoveredIdx(null)}
      >
        <svg
          viewBox={`0 0 ${size} ${size}`}
          className="concentric-svg"
          aria-label="Concentric Goals Activity Rings"
        >
          {radii.map((radius, idx) => {
            const goal = ringGoals[idx];
            const circumference = 2 * Math.PI * radius;
            const pct = goal ? goal.pct : 0;
            const strokeDashoffset = circumference - (pct / 100) * circumference;
            const isHovered = hoveredIdx === idx;
            const color = RING_PALETTE[idx % RING_PALETTE.length];

            return (
              <g key={`ring-${idx}`}>
                {/* Background groove track circle (matching Image 2) */}
                <circle
                  cx={center}
                  cy={center}
                  r={radius}
                  fill="none"
                  stroke="var(--bg-elevated, #1C1F26)"
                  strokeWidth={ringStroke}
                  opacity="0.85"
                />

                {/* Progress arc with rounded end caps */}
                {goal && pct > 0 && (
                  <circle
                    cx={center}
                    cy={center}
                    r={radius}
                    fill="none"
                    stroke={color}
                    strokeWidth={isHovered ? ringStroke + 3 : ringStroke}
                    strokeLinecap="round"
                    strokeDasharray={`${circumference} ${circumference}`}
                    strokeDashoffset={strokeDashoffset}
                    className="concentric-ring-arc"
                    style={{
                      opacity: hoveredIdx !== null && !isHovered ? 0.35 : 1,
                      transition:
                        'stroke-width 0.25s ease, opacity 0.25s ease, stroke-dashoffset 0.6s cubic-bezier(0.16, 1, 0.3, 1)',
                    }}
                    onMouseEnter={() => setHoveredIdx(idx)}
                    onClick={() => onSelectGoal && onSelectGoal(goal)}
                  />
                )}
              </g>
            );
          })}

          {/* ── Center Hub Disc & Text Group (rotated back upright) ── */}
          <g
            className="concentric-text-group"
            style={{ transformOrigin: `${center}px ${center}px` }}
          >
            {/* Dark circular hub cutout disc */}
            <circle
              cx={center}
              cy={center}
              r={44}
              fill="var(--bg-elevated, #101217)"
              opacity="0.95"
            />

            <text
              x={center}
              y={center - 14}
              textAnchor="middle"
              fill="var(--text-muted)"
              fontSize="9"
              fontWeight="700"
              letterSpacing="0.1em"
              fontFamily="var(--font-display)"
              style={{ textTransform: 'uppercase', transition: 'fill 0.2s' }}
            >
              {centerInfo.label}
            </text>

            <text
              x={center}
              y={center + 7}
              textAnchor="middle"
              fill={centerInfo.color === '#FFFFFF' ? 'var(--text-primary)' : centerInfo.color}
              fontSize="18"
              fontWeight="800"
              letterSpacing="-0.03em"
              fontFamily="var(--font-display)"
              style={{ transition: 'all 0.2s' }}
            >
              {centerInfo.value}
            </text>

            <text
              x={center}
              y={center + 23}
              textAnchor="middle"
              fill="var(--text-secondary)"
              fontSize="9"
              fontWeight="500"
              fontFamily="var(--font-body)"
              style={{ transition: 'fill 0.2s' }}
            >
              {centerInfo.sub}
            </text>
          </g>
        </svg>
      </div>

      {/* ── Legend Items linking to each concentric ring ── */}
      {ringGoals.length > 0 && (
        <div className="concentric-legend">
          {ringGoals.map((g, idx) => {
            const isSelected = hoveredIdx === idx;
            return (
              <div
                key={g._id || idx}
                className={`concentric-legend-item ${isSelected ? 'active' : ''}`}
                onMouseEnter={() => setHoveredIdx(idx)}
                onMouseLeave={() => setHoveredIdx(null)}
                onClick={() => onSelectGoal && onSelectGoal(g)}
              >
                <span
                  className="concentric-legend-swatch"
                  style={{ background: g.color }}
                />
                <div className="concentric-legend-info">
                  <span className="concentric-legend-name">{g.title}</span>
                  <span className="concentric-legend-pct">
                    {g.pct}% • {fmt(g.currentAmount, currency)}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
