import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { createPortal } from 'react-dom';
import {
  Download,
  Github,
  CheckCircle,
  Smartphone,
  ShieldCheck,
  RefreshCw,
  ExternalLink,
  X,
  ChevronRight,
  Sparkles,
} from 'lucide-react';

const LATEST_VERSION = 'v1.1.0';
const APK_DOWNLOAD_URL = 'https://github.com/BudGI-Team/BudGi/releases/latest/download/BudGi.apk';
const RELEASES_PAGE_URL = 'https://github.com/BudGI-Team/BudGi/releases';

export default function DownloadModal({ isOpen, onClose }) {
  const [downloadStarted, setDownloadStarted] = useState(false);

  if (typeof document === 'undefined') return null;

  const handleDownload = () => {
    setDownloadStarted(true);
    // Trigger download
    const link = document.createElement('a');
    link.href = APK_DOWNLOAD_URL;
    link.download = 'BudGi.apk';
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
          transition={{ duration: 0.2 }}
          onClick={(e) => e.target === e.currentTarget && onClose()}
        >
          <motion.div
            className="download-modal-container"
            initial={{ opacity: 0, scale: 0.95, y: 16 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 12 }}
            transition={{ type: 'spring', damping: 25, stiffness: 350 }}
          >
            {/* Ambient Background Glow */}
            <div className="download-modal-glow" />

            {/* Header */}
            <div className="download-modal-header">
              <div className="download-modal-brand">
                <div className="download-app-icon">
                  <Smartphone size={24} className="download-icon-phone" />
                  <span className="download-version-badge">{LATEST_VERSION}</span>
                </div>
                <div>
                  <h3 className="download-modal-title">Get BudGi for Android</h3>
                  <p className="download-modal-sub">Native Android APK • Production Release</p>
                </div>
              </div>
              <button
                className="download-modal-close"
                onClick={onClose}
                aria-label="Close download modal"
              >
                <X size={18} />
              </button>
            </div>

            {/* Body */}
            <div className="download-modal-body">
              {/* Highlight Banner: Seamless In-App Updates */}
              <div className="download-highlight-card">
                <div className="download-highlight-icon">
                  <RefreshCw size={18} className="spin-slow" />
                </div>
                <div className="download-highlight-text">
                  <strong>Seamless Updates Built-in</strong>
                  <span>
                    When we release new updates, BudGi upgrades directly in-place — no need to uninstall the app or lose your data!
                  </span>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="download-action-group">
                <button
                  className="download-btn-primary"
                  onClick={handleDownload}
                  id="direct-apk-download-btn"
                >
                  <Download size={20} />
                  <div className="download-btn-text">
                    <span className="btn-title">
                      {downloadStarted ? 'Downloading BudGi.apk...' : 'Download Android Native (APK)'}
                    </span>
                    <span className="btn-subtitle">Direct Download · Latest {LATEST_VERSION}</span>
                  </div>
                  <ChevronRight size={18} className="download-btn-arrow" />
                </button>

                <a
                  href={RELEASES_PAGE_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="download-btn-secondary"
                  id="github-releases-btn"
                >
                  <Github size={18} />
                  <span>View All Releases on GitHub</span>
                  <ExternalLink size={14} className="external-icon" />
                </a>
              </div>

              {downloadStarted && (
                <motion.div
                  className="download-started-alert"
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                >
                  <CheckCircle size={16} />
                  <span>Download started! Check your browser or device notifications.</span>
                </motion.div>
              )}

              {/* Quick Installation Steps */}
              <div className="download-steps-section">
                <h4 className="download-steps-heading">
                  <ShieldCheck size={16} />
                  <span>How to Install & Update</span>
                </h4>
                <ol className="download-steps-list">
                  <li>
                    <span className="step-num">1</span>
                    <div>
                      <strong>Download the APK</strong>
                      <p>Tap the direct download button above to save <code>BudGi.apk</code>.</p>
                    </div>
                  </li>
                  <li>
                    <span className="step-num">2</span>
                    <div>
                      <strong>Allow Installation</strong>
                      <p>Tap the downloaded file. If prompted, allow <em>Install unknown apps</em> for your browser.</p>
                    </div>
                  </li>
                  <li>
                    <span className="step-num">3</span>
                    <div>
                      <strong>Future Updates Are Automatic</strong>
                      <p>BudGi checks for updates automatically inside the app. New builds install seamlessly over existing ones without uninstallation.</p>
                    </div>
                  </li>
                </ol>
              </div>

              {/* Meta tags */}
              <div className="download-meta-row">
                <span className="meta-pill"><Sparkles size={12} /> Android 8.0+</span>
                <span className="meta-pill"><ShieldCheck size={12} /> Cryptographically Signed</span>
                <span className="meta-pill"><ExternalLink size={12} /> Open Source</span>
              </div>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body
  );
}
