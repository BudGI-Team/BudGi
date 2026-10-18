import { Component } from 'react';

/**
 * ErrorBoundary — Catches rendering errors and shows a recovery UI
 * instead of a blank white screen. Wrap around major route components.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('[ErrorBoundary] Caught rendering error:', error, errorInfo);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '60vh',
            padding: '40px 24px',
            textAlign: 'center',
            fontFamily: 'var(--font-body, system-ui)',
            color: 'var(--text-primary, #fff)',
          }}
        >
          <div
            style={{
              width: 72,
              height: 72,
              borderRadius: 20,
              background: 'rgba(245, 97, 106, 0.1)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: 24,
              fontSize: 32,
            }}
          >
            ⚠️
          </div>
          <h2
            style={{
              fontSize: 22,
              fontWeight: 800,
              letterSpacing: '-0.02em',
              marginBottom: 8,
            }}
          >
            Something went wrong
          </h2>
          <p
            style={{
              fontSize: 14,
              color: 'var(--text-secondary, #888)',
              maxWidth: 400,
              lineHeight: 1.6,
              marginBottom: 24,
            }}
          >
            An unexpected error occurred. You can try refreshing the page, or go back to the dashboard.
          </p>
          <div style={{ display: 'flex', gap: 12 }}>
            <button
              onClick={this.handleReset}
              style={{
                padding: '10px 20px',
                borderRadius: 10,
                border: '1px solid rgba(255,255,255,0.1)',
                background: 'transparent',
                color: 'var(--text-primary, #fff)',
                fontWeight: 600,
                fontSize: 14,
                cursor: 'pointer',
              }}
            >
              Try Again
            </button>
            <button
              onClick={() => {
                this.handleReset();
                window.location.href = '/app/dashboard';
              }}
              style={{
                padding: '10px 20px',
                borderRadius: 10,
                border: 'none',
                background: 'var(--accent, #38BDF8)',
                color: '#fff',
                fontWeight: 600,
                fontSize: 14,
                cursor: 'pointer',
              }}
            >
              Go to Dashboard
            </button>
          </div>
          {process.env.NODE_ENV === 'development' && this.state.error && (
            <pre
              style={{
                marginTop: 32,
                padding: 16,
                background: 'rgba(255,255,255,0.03)',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 10,
                fontSize: 12,
                color: 'var(--red, #F5616A)',
                textAlign: 'left',
                maxWidth: 600,
                width: '100%',
                overflow: 'auto',
                maxHeight: 200,
              }}
            >
              {this.state.error.message}
              {'\n'}
              {this.state.error.stack}
            </pre>
          )}
        </div>
      );
    }

    return this.props.children;
  }
}
