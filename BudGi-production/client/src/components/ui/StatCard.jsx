import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown } from '../../lib/icons';
import AnimatedNumber from './AnimatedNumber';
import './StatCard.css';

export default function StatCard({
  title,
  value,
  rawValue,
  formatter,
  subvalue,
  trend,
  trendLabel,
  icon: Icon,
  color = 'accent',
  loading = false,
  index = 0,
}) {
  const isPositive = trend > 0;
  const isNegative = trend < 0;

  return (
    <motion.div
      className={`stat-card stat-card-${color}`}
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{
        duration: 0.4,
        delay: index * 0.08,
        ease: [0.16, 1, 0.3, 1],
      }}
    >
      {loading ? (
        <StatCardSkeleton />
      ) : (
        <>
          <div className="stat-top">
            <span className="stat-title">{title}</span>
            {Icon && (
              <div className={`stat-icon-wrap stat-icon-${color}`}>
                <Icon size={16} />
              </div>
            )}
          </div>
          <motion.div
            className="stat-value"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: index * 0.08 + 0.15 }}
          >
            {rawValue !== undefined && formatter ? <AnimatedNumber value={rawValue} formatter={formatter} /> : value}
          </motion.div>
          <div className="stat-bottom">
            {subvalue && <span className="stat-subvalue">{subvalue}</span>}
            {trend !== undefined && trend !== null && (
              <span className={`stat-trend ${isPositive ? 'trend-up' : isNegative ? 'trend-down' : 'trend-flat'}`}>
                {isPositive ? <TrendingUp size={12} /> : isNegative ? <TrendingDown size={12} /> : null}
                {Math.abs(trend).toFixed(1)}%{trendLabel && <span className="trend-label">{trendLabel}</span>}
              </span>
            )}
          </div>
        </>
      )}
    </motion.div>
  );
}

function StatCardSkeleton() {
  return (
    <div className="stat-skeleton">
      <div className="shimmer" style={{ height: 12, width: '60%', borderRadius: 6 }} />
      <div className="shimmer" style={{ height: 28, width: '80%', borderRadius: 8, marginTop: 12 }} />
      <div className="shimmer" style={{ height: 10, width: '40%', borderRadius: 6, marginTop: 10 }} />
    </div>
  );
}
