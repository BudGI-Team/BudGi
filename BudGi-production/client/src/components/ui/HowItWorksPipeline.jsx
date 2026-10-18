import React, { useState } from 'react';
import {
  Smartphone,
  Receipt,
  Activity,
  Bot,
  CheckCircle2,
  ArrowRight,
  ArrowLeft,
  ShieldCheck,
  Zap,
} from '../../lib/icons';
import budgiLogo from '../../logo/budgi-logo-new.png';
import './HowItWorksPipeline.css';

const STEPS = [
  {
    id: 'tracking',
    num: '01',
    navTitle: 'Auto-Tracking',
    icon: Smartphone,
    pill: 'STEP 01 · REAL-TIME INGESTION',
    title: 'You spend. BudGi logs it instantly.',
    description:
      'Incoming UPI and bank SMS alerts are parsed on-device in milliseconds as you pay. No manual typing, no forgotten coffee receipts, and zero cloud delay.',
    highlights: [
      'Sub-4ms local regex parser running on Android',
      'Auto-detects HDFC, SBI, ICICI, Axis, Swiggy, Blinkit, Zerodha',
      'Zero raw message content transmitted to external servers',
    ],
    screenTopTitle: 'ANDROID NOTIFICATION LISTENER · 9:41 AM',
    screenStatus: 'LIVE CAPTURE',
  },
  {
    id: 'receipts',
    num: '02',
    navTitle: 'Bill & Receipt OCR',
    icon: Receipt,
    pill: 'STEP 02 · AUTONOMOUS OCR',
    title: 'Bills and receipts converted to clean data.',
    description:
      'Snap a photo or upload a statement. BudGi extracts line items, detects merchant GSTINs, and categorizes split totals with integer-paisa double-entry precision.',
    highlights: [
      'Itemized line-by-line extraction with tax breakdowns',
      'Eliminates floating-point calculation drift via integer math',
      'Automatic envelope debit allocation in under 2 seconds',
    ],
    screenTopTitle: 'OPTICAL RECEIPT PARSER · INVOICE #BK-9412',
    screenStatus: 'OCR EXTRACTED',
  },
  {
    id: 'guardrails',
    num: '03',
    navTitle: 'Smart Envelopes',
    icon: Activity,
    pill: 'STEP 03 · PREDICTIVE GUARDRAILS',
    title: 'Predictive envelopes stop overspending.',
    description:
      'BudGi analyzes your daily burn velocity against 60-day historical trajectories. If dining or shopping trends pace too fast, you get an early alert before money runs out.',
    highlights: [
      'Real-time "Safe to Spend" daily allowance calculation',
      'Proactive velocity warnings before weekend overspending',
      'One-tap rebalance transfer from emergency surplus buffer',
    ],
    screenTopTitle: 'ENVELOPE VELOCITY ENGINE · 64-DAY RUNWAY',
    screenStatus: 'PACING HEALTHY',
  },
  {
    id: 'copilot',
    num: '04',
    navTitle: 'Private Copilot',
    icon: Bot,
    pill: 'STEP 04 · FINANCIAL INTELLIGENCE',
    title: 'A private CFO that knows your actual ledger.',
    description:
      'Ask questions in plain English. Get instant, mathematically accurate answers drawn directly from your double-entry ledger — without sharing financial data with ad networks.',
    highlights: [
      'Sub-100ms Groq LLaMA 3.3 70B financial inference',
      'Answers drawn from verified mathematical accounts, not guesses',
      'Strict zero-ad monetization or broker telemetry guarantee',
    ],
    screenTopTitle: 'BUDGI COPILOT INTELLIGENCE · GROQ INFERENCE',
    screenStatus: 'ACTIVE QUERY',
  },
];

export default function HowItWorksPipeline() {
  const [activeIdx, setActiveIdx] = useState(0);
  const currentStep = STEPS[activeIdx];

  const goNext = () => setActiveIdx((prev) => Math.min(STEPS.length - 1, prev + 1));
  const goPrev = () => setActiveIdx((prev) => Math.max(0, prev - 1));

  return (
    <section className="how-works-section" id="how" aria-label="How BudGi Works Visual Product Walkthrough">
      <div className="how-works-container">
        {/* Concise Section Header */}
        <div className="how-works-header">
          <div className="how-works-badge">
            <span className="how-works-badge-dot" />
            <span>Interactive Experience</span>
          </div>
          <h2 className="how-works-title">
            See exactly how BudGi
            <br />
            manages your money.
          </h2>
          <p className="how-works-subtitle">
            Experience the actual product workflow from incoming UPI payment to intelligent envelope guardrails.
          </p>
        </div>

        {/* 4 Interactive Step Selector Tabs */}
        <div className="how-works-tabs" role="tablist">
          {STEPS.map((step, idx) => {
            const Icon = step.icon;
            const isActive = activeIdx === idx;
            return (
              <button
                key={step.id}
                type="button"
                role="tab"
                aria-selected={isActive}
                className={`how-works-tab-btn ${isActive ? 'active' : ''}`}
                onClick={() => setActiveIdx(idx)}
              >
                <div className="hwt-icon-box">
                  <Icon size={18} strokeWidth={2} />
                </div>
                <div className="hwt-info">
                  <span className="hwt-step-label">Step {step.num}</span>
                  <span className="hwt-step-title">{step.navTitle}</span>
                </div>
              </button>
            );
          })}
        </div>

        {/* Active Step Showcase: Split View */}
        <div className="how-works-showcase">
          {/* Left: Concise Human Explanation */}
          <div className="showcase-info">
            <span className="showcase-step-pill">{currentStep.pill}</span>
            <h3 className="showcase-title">{currentStep.title}</h3>
            <p className="showcase-desc">{currentStep.description}</p>

            <div className="showcase-highlights">
              {currentStep.highlights.map((hText, hIdx) => (
                <div key={hIdx} className="showcase-hl-item">
                  <CheckCircle2 size={16} strokeWidth={2} className="showcase-hl-icon" />
                  <span>{hText}</span>
                </div>
              ))}
            </div>

            {/* Step Controls */}
            <div className="showcase-nav-row">
              <button
                type="button"
                className="showcase-nav-btn"
                onClick={goPrev}
                disabled={activeIdx === 0}
                aria-label="Previous step"
              >
                <ArrowLeft size={14} /> <span>Previous</span>
              </button>
              <button
                type="button"
                className="showcase-nav-btn"
                onClick={goNext}
                disabled={activeIdx === STEPS.length - 1}
                aria-label="Next step"
              >
                <span>Next</span> <ArrowRight size={14} />
              </button>

              <div className="showcase-step-dots">
                {STEPS.map((_, dotIdx) => (
                  <span
                    key={dotIdx}
                    className={`showcase-dot ${activeIdx === dotIdx ? 'active' : ''}`}
                    onClick={() => setActiveIdx(dotIdx)}
                  />
                ))}
              </div>
            </div>
          </div>

          {/* Right: Actual Product Interface Preview */}
          <div className="showcase-screen-wrap">
            <div className="screen-frame">
              {/* Window Controls Topbar */}
              <div className="screen-topbar">
                <div className="screen-topbar-left">
                  <span className="screen-dot red" />
                  <span className="screen-dot yellow" />
                  <span className="screen-dot green" />
                  <span className="screen-topbar-title">{currentStep.screenTopTitle}</span>
                </div>
                <span className="screen-topbar-badge">{currentStep.screenStatus}</span>
              </div>

              {/* Dynamic Screen Visual Body */}
              <div className="screen-content-area">
                {/* Visual Screen 1: Auto Ingestion */}
                {activeIdx === 0 && (
                  <div className="screen1-flow">
                    <div className="phone-push-card">
                      <div className="pp-head">
                        <span className="pp-bank-badge">
                          <Zap size={13} /> HDFC Bank · UPI Instant
                        </span>
                        <span className="pp-time">Just now</span>
                      </div>
                      <div className="pp-text">
                        Debited <strong>₹480.00</strong> to <strong>Blinkit Grocery</strong> via UPI Ref 409218.
                        Available Balance: ₹84,200.
                      </div>
                    </div>

                    <div className="screen-arrow-connect">
                      <div className="screen-arrow-line" />
                      <span>Parsed on-device in 3.4ms</span>
                      <div className="screen-arrow-line" />
                    </div>

                    <div className="app-live-txn-card">
                      <div className="alt-logo-box">BK</div>
                      <div className="alt-meta">
                        <span className="alt-merchant">Blinkit Grocery & Kitchen</span>
                        <span className="alt-category">
                          <span>Household Groceries</span>
                          <span>·</span>
                          <span className="alt-auto-tag">✓ Auto-Categorized</span>
                        </span>
                      </div>
                      <span className="alt-amount">-₹480.00</span>
                    </div>
                  </div>
                )}

                {/* Visual Screen 2: Receipt OCR */}
                {activeIdx === 1 && (
                  <div className="screen2-flow">
                    <div className="receipt-slip">
                      <div className="receipt-header">
                        <span className="receipt-store">BLINKIT COMMERCE LTD</span>
                        <span className="receipt-sub">14 Oct 2026 · Invoice #BK-9412</span>
                      </div>
                      <div className="receipt-items">
                        <div className="receipt-row">
                          <span>1x Organic Milk (1L)</span>
                          <span>₹64.00</span>
                        </div>
                        <div className="receipt-row">
                          <span>1x Sourdough Bread</span>
                          <span>₹55.00</span>
                        </div>
                        <div className="receipt-row">
                          <span>1x Farm Butter 100g</span>
                          <span>₹62.00</span>
                        </div>
                      </div>
                      <div className="receipt-total">
                        <span>TOTAL PAID</span>
                        <span>₹181.00</span>
                      </div>
                    </div>

                    <div className="ocr-parsed-result">
                      <div className="ocr-item-badge">
                        <div className="oib-label">Merchant & GSTIN</div>
                        <div className="oib-val">Blinkit Commerce (07AABCB2212E1ZU)</div>
                      </div>
                      <div className="ocr-item-badge">
                        <div className="oib-label">Allocated Envelope</div>
                        <div className="oib-val" style={{ color: 'var(--green)' }}>
                          Groceries & Essentials (+₹181.00)
                        </div>
                      </div>
                      <div className="ocr-item-badge">
                        <div className="oib-label">Double-Entry Status</div>
                        <div className="oib-val" style={{ color: 'var(--accent-bright)' }}>
                          ✓ Balanced · 0.00 Paisa Drift
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Visual Screen 3: Envelopes */}
                {activeIdx === 2 && (
                  <div className="screen3-flow">
                    <div className="s3-top-metric">
                      <div className="s3-metric-left">
                        <span className="s3-m-lbl">Safe to Spend Today</span>
                        <span className="s3-m-val">₹650 / day</span>
                      </div>
                      <span className="s3-runway-chip">64 Days Safe Runway</span>
                    </div>

                    <div className="s3-envelopes-list">
                      <div className="s3-env-card">
                        <div className="s3-env-header">
                          <span>Dining & Swiggy</span>
                          <span style={{ color: 'var(--amber)' }}>₹4,280 / ₹5,000 (85%)</span>
                        </div>
                        <div className="s3-env-track">
                          <div className="s3-env-fill" style={{ width: '85%', background: 'var(--amber)' }} />
                        </div>
                        <div className="s3-env-footer">
                          <span>Burn velocity high (₹72/day left)</span>
                          <span style={{ color: 'var(--accent-bright)', fontWeight: 600 }}>Suggested: +₹500 buffer</span>
                        </div>
                      </div>

                      <div className="s3-env-card">
                        <div className="s3-env-header">
                          <span>Groceries & Supermarket</span>
                          <span style={{ color: 'var(--green)' }}>₹3,100 / ₹6,000 (51%)</span>
                        </div>
                        <div className="s3-env-track">
                          <div className="s3-env-fill" style={{ width: '51%', background: 'var(--green)' }} />
                        </div>
                        <div className="s3-env-footer">
                          <span>Healthy trajectory (₹170/day left)</span>
                          <span style={{ color: 'var(--green)' }}>● Optimal Pace</span>
                        </div>
                      </div>

                      <div className="s3-env-card">
                        <div className="s3-env-header">
                          <span>Cloud & Subscriptions</span>
                          <span style={{ color: 'var(--blue)' }}>₹1,499 / ₹2,000 (75%)</span>
                        </div>
                        <div className="s3-env-track">
                          <div className="s3-env-fill" style={{ width: '75%', background: 'var(--blue)' }} />
                        </div>
                        <div className="s3-env-footer">
                          <span>Monthly renewals verified</span>
                          <span>Zero overspend risk</span>
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Visual Screen 4: Copilot */}
                {activeIdx === 3 && (
                  <div className="screen4-flow">
                    <div className="copilot-bubble-user">
                      Can I afford the ₹12,000 weekend trip to Goa?
                    </div>

                    <div className="copilot-bubble-ai">
                      <div className="ai-author-row">
                        <img src={budgiLogo} alt="BudGi Copilot" className="ai-logo-tiny" />
                        <span>BudGi Treasury Copilot</span>
                      </div>
                      <div>
                        Yes, you're fully covered. After reserving <strong>₹28,500</strong> for upcoming rent and your
                        scheduled <strong>₹15,000</strong> SIP, you still have <strong>₹18,400</strong> discretionary
                        buffer in your primary account.
                      </div>
                      <div className="ai-metric-pill-box">
                        <span className="ai-mpill">
                          <ShieldCheck size={12} /> ₹6,400 Emergency Buffer Intact
                        </span>
                        <span className="ai-mpill" style={{ color: 'var(--accent-bright)', borderColor: 'var(--border-accent)', background: 'var(--accent-dim)' }}>
                          Runway: 64 Days Preserved
                        </span>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
