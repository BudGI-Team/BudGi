import logoImage from '../../logo/budgi-logo-new.png';

export default function BrandLogo({ size = 28, text = 'BudGi', className = '' }) {
  const fontSize = Math.round(size * 0.8);
  // Optical vertical offset: shifts the text down so cap-height center aligns with the toucan's visual center
  const opticalY = Math.max(2.5, Math.round(size * 0.1));

  return (
    <div
      className={`brand-logo ${className}`.trim()}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: Math.max(8, Math.round(size * 0.3)),
        lineHeight: 1,
      }}
    >
      <img
        src={logoImage}
        alt="BudGi logo"
        width={size}
        height={size}
        loading="eager"
        decoding="async"
        className="brand-logo-image"
        style={{
          objectFit: 'contain',
          display: 'block',
          flexShrink: 0,
        }}
      />
      {text ? (
        <span
          className="brand-logo-text"
          style={{
            fontFamily: "'Plus Jakarta Sans', 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
            fontSize: `${fontSize}px`,
            fontWeight: 800,
            letterSpacing: '-0.038em',
            lineHeight: 1,
            display: 'inline-flex',
            alignItems: 'center',
            color: 'var(--text-primary)',
            transform: `translateY(${opticalY}px)`,
          }}
        >
          {text}
        </span>
      ) : null}
    </div>
  );
}
