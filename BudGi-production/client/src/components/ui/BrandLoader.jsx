import { motion } from 'framer-motion';
import logoImage from '../../logo/budgi-logo-new.png';
import './BrandLoader.css';

export default function BrandLoader({
  message = 'Loading BudGi...',
  fullPage = false,
  minHeight = '50vh',
  size = 'md',
  className = '',
}) {
  return (
    <motion.div
      className={`brand-loader-wrap size-${size} ${fullPage ? 'is-fullpage' : ''} ${className}`.trim()}
      style={!fullPage ? { minHeight } : undefined}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}
    >
      <div className="brand-loader-emblem">
        <img src={logoImage} alt="BudGi" className="brand-loader-img" />
      </div>

      <div className="brand-loader-track">
        <div className="brand-loader-bar" />
      </div>

      {message && <div className="brand-loader-text">{message}</div>}
    </motion.div>
  );
}
