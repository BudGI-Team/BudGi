import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { createPortal } from 'react-dom';
import {
  Download,
  Github,
  CheckCircle,
  Smartphone,
  ExternalLink,
  X,
  QrCode,
  ShieldCheck,
} from 'lucide-react';
import './DownloadModal.css';

const LATEST_VERSION = 'v1.2.1';
const APK_DOWNLOAD_URL = 'https://github.com/BudGI-Team/BudGi/releases/latest/download/BudGi.apk';
const RELEASES_PAGE_URL = 'https://github.com/BudGI-Team/BudGi/releases';

export default function DownloadModal({ isOpen, onClose }) {
  const [downloadStarted, setDownloadStarted] = useState(false);
  const [showQr, setShowQr] = useState(false);

  if (typeof document === 'undefined') return null;

  const handleDownload = () => {
    setDownloadStarted(true);
    const link = document.createElement('a');
    link.href = APK_DOWNLOAD_URL;
    link.download = 'BudGi-v1.2.1.apk';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <motion.div
          className="download-modal-backdrop"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.15 }}
          onClick={(e) => e.target === e.currentTarget && onClose()}
        >
          <motion.div
            className="download-modal-container"
            initial={{ opacity: 0, scale: 0.96, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 8 }}
            transition={{ type: 'spring', damping: 28, stiffness: 380 }}
          >
            {/* Header */}
            <div className="download-modal-header">
              <div className="download-modal-brand">
                <div className="download-app-icon">
                  <Smartphone size={20} />
                </div>
                <div>
                  <div className="download-title-row">
                    <h3 className="download-modal-title">BudGi for Android</h3>
                    <span className="download-version-badge">{LATEST_VERSION}</span>
                  </div>
                  <p className="download-modal-sub">Official Native Release</p>
                </div>
              </div>
              <button
                className="download-modal-close"
                onClick={onClose}
                aria-label="Close download modal"
              >
                <X size={16} />
              </button>
            </div>

            {/* Body */}
            <div className="download-modal-body">
              {/* Feature Note */}
              <div className="download-feature-note">
                <ShieldCheck size={15} className="download-note-icon" />
                <span>
                  <strong>In-App Updates:</strong> Future releases upgrade in-place automatically without losing your data.
                </span>
              </div>

              {/* Primary Download Button */}
              <button
                className="download-btn-primary"
                onClick={handleDownload}
                id="direct-apk-download-btn"
              >
                <Download size={18} />
                <span>{downloadStarted ? 'Downloading BudGi.apk...' : 'Download APK'}</span>
              </button>

              {downloadStarted && (
                <motion.div
                  className="download-started-alert"
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                >
                  <CheckCircle size={15} />
                  <span>Download started! Check your device notifications to install.</span>
                </motion.div>
              )}

              {/* Google Play Protect & Installation Trust Guide */}
              <div
                style={{
                  fontSize: '11.5px',
                  color: 'var(--text-muted, #94a3b8)',
                  background: 'rgba(16, 185, 129, 0.06)',
                  border: '1px solid rgba(16, 185, 129, 0.22)',
                  borderRadius: '10px',
                  padding: '10px 13px',
                  lineHeight: 1.55,
                  marginTop: '12px',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px', color: '#10B981', fontWeight: 600, fontSize: '12px' }}>
                  <ShieldCheck size={15} />
                  <span>100% Verified Safe • On-Device Financial Sandbox</span>
                </div>
                <div>
                  <strong>Play Protect Tip:</strong> Because BudGi parses bank SMS locally on-device without cloud upload, Google Play Protect displays a standard verification notice for direct APKs. Simply tap <strong>"More details" → "Install anyway"</strong>. You do <em>not</em> need to disable Play Protect.
                </div>
                <div style={{ marginTop: '5px', opacity: 0.85 }}>
                  If prompted by your browser, toggle <strong>"Allow from this source"</strong>.
                </div>
              </div>

              {/* Secondary Options */}
              <div className="download-secondary-row">
                <a
                  href={RELEASES_PAGE_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="download-btn-secondary"
                  id="github-releases-btn"
                >
                  <Github size={15} />
                  <span>GitHub Releases</span>
                  <ExternalLink size={12} />
                </a>

                <button
                  type="button"
                  className="download-btn-secondary"
                  onClick={() => setShowQr((s) => !s)}
                >
                  <QrCode size={15} />
                  <span>{showQr ? 'Hide QR' : 'Scan QR'}</span>
                </button>
              </div>

              {/* QR Code dropdown */}
              {showQr && (
                <motion.div
                  className="download-qr-box"
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                >
                  <img
                    src={`https://api.qrserver.com/v1/create-qr-code/?size=140x140&data=${encodeURIComponent(
                      APK_DOWNLOAD_URL
                    )}&color=255-255-255&bgcolor=19-22-28`}
                    alt="Scan to Download BudGi APK"
                    className="download-qr-img"
                  />
                  <span className="download-qr-label">
                    Scan with your phone camera to download directly
                  </span>
                </motion.div>
              )}

              {/* Minimal Footer Meta */}
              <div className="download-footer-meta">
                <span>Android 8.0+</span>
                <span>•</span>
                <span>Free & Open Source</span>
                <span>•</span>
                <span>Verified Clean</span>
              </div>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body
  );
}
