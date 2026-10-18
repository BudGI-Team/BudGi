import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Terminal,
  Cpu,
  Layers,
  ShieldCheck,
  Code2,
  Activity,
  Check,
  Copy,
  Database,
  Scale,
  FileCode,
} from '../../lib/icons';
import './ModernFeatures.css';

const TABS = [
  {
    id: 'pipeline',
    label: 'Ingestion Pipeline',
    tag: 'MODULE 01: PARSER',
    icon: Terminal,
    title: 'Sub-4ms Deterministic Transaction Parser',
    desc: 'Ingests raw SMS, UPI push notifications, and netbanking alerts through compiled regex automata. Extracts merchant, amount, payment rail, and category on-device with zero raw data transmitted.',
    specs: [
      { label: 'Parse Latency', val: '< 3.8ms' },
      { label: 'Bank Coverage', val: '40+ Gateways' },
      { label: 'Privacy Model', val: 'On-device Regex' },
    ],
    codeTitle: 'raw_sms.txt → parsed.json',
    fileType: 'json',
    code: `{
  "event": "transaction.parsed",
  "source": "HDFC_BANK_UPI_SMS",
  "raw": "HDFC: Rs 549.00 debited from a/c **4102 to SWIGGY. Ref: 4298192312.",
  "extracted": {
    "merchant": "Swiggy",
    "amount": 549.00,
    "currency": "INR",
    "payment_rail": "UPI",
    "category": "Food & Dining",
    "reference_id": "4298192312",
    "confidence_score": 0.998
  },
  "execution_time_ms": 3.4
}`,
  },
  {
    id: 'ledger',
    label: 'Double-Entry Core',
    tag: 'MODULE 02: LEDGER_CORE',
    icon: Layers,
    title: 'Mathematical Double-Entry Balancing',
    desc: 'Financial tracking built on formal double-entry accounting. Every debit matches an exact credit with integer paisa precision, eliminating floating-point math drift and phantom discrepancies.',
    specs: [
      { label: 'Balance Drift', val: '0.00% Zero-drift' },
      { label: 'Audit Trail', val: 'Immutable Append' },
      { label: 'Precision', val: 'Integer-paisa' },
    ],
    codeTitle: 'ledger_transaction.sql',
    fileType: 'sql',
    code: `-- Atomic double-entry settlement
BEGIN TRANSACTION;
  INSERT INTO ledger_journal (id, timestamp, note)
  VALUES ('jrn_891a', '2026-10-05T12:00:00Z', 'Swiggy Order #9812');

  -- Debit Expense / Credit Asset (Net sum === 0)
  INSERT INTO ledger_entries (journal_id, account, debit, credit)
  VALUES 
    ('jrn_891a', 'expenses:food_and_dining', 54900, 0),
    ('jrn_891a', 'assets:bank:hdfc_savings', 0, 54900);

  -- Verification constraint: SUM(debit) - SUM(credit) == 0
COMMIT;`,
  },
  {
    id: 'cashflow',
    label: 'Cashflow Engine',
    tag: 'MODULE 03: FORECASTING',
    icon: Activity,
    title: 'Algorithmic Burn Rate & Runway Modeling',
    desc: 'Detects recurring subscription cadences (AWS, Netflix, rent, SIPs) using rolling frequency variance. Calculates safe-to-spend envelopes and 60-day cashflow runway with zero speculative guesswork.',
    specs: [
      { label: 'Recurrence Detection', val: '98.4% Confidence' },
      { label: 'Projection Horizon', val: '90 Days Forward' },
      { label: 'Anomaly Threshold', val: '2.5σ Deviation' },
    ],
    codeTitle: 'burn_projection.json',
    fileType: 'json',
    code: `{
  "period": "2026-10",
  "liquid_balance": 84500.00,
  "daily_burn_velocity": 1397.41,
  "projected_eom_balance": 41200.00,
  "runway_days": 60,
  "recurring_commitments": [
    { "merchant": "AWS Cloud", "cadence": "monthly", "amount": 1420.00 },
    { "merchant": "Airtel Fiber", "cadence": "monthly", "amount": 999.00 }
  ],
  "anomaly_flags": []
}`,
  },
  {
    id: 'api',
    label: 'REST API & Webhooks',
    tag: 'MODULE 04: DEV_INTERFACES',
    icon: Code2,
    title: 'Open REST API, Webhooks & Pipeline Streaming',
    desc: 'Built for engineers who automate their personal stacks. Dispatch HTTP webhooks on transaction events, fetch structured JSON payloads, or stream balance updates directly into Postgres or personal bots.',
    specs: [
      { label: 'Specification', val: 'OpenAPI 3.1' },
      { label: 'Authentication', val: 'Bearer JWT / Token' },
      { label: 'Export Targets', val: 'JSON, CSV, SQLite' },
    ],
    codeTitle: 'dispatch_webhook.sh',
    fileType: 'bash',
    code: `curl -X POST https://api.budgi.app/v1/webhooks \\
  -H "Authorization: Bearer bg_live_49f8a291e0a8" \\
  -H "Content-Type: application/json" \\
  -d '{
    "endpoint": "https://api.yourdomain.com/finances/sync",
    "secret": "whsec_901f4ab82c",
    "subscribed_events": [
      "transaction.created",
      "budget.threshold_reached"
    ]
  }'`,
  },
  {
    id: 'security',
    label: 'Zero-Knowledge Vault',
    tag: 'MODULE 05: CRYPTO_VAULT',
    icon: ShieldCheck,
    title: 'Client-Side PBKDF2 + AES-GCM-256 Encryption',
    desc: 'Local-first zero-knowledge architecture. Encryption keys are generated client-side from your master secret with 100,000 PBKDF2 iterations. Stored locally in encrypted SQLite and IndexedDB.',
    specs: [
      { label: 'Cipher Suite', val: 'AES-256-GCM' },
      { label: 'Key Derivation', val: 'PBKDF2-SHA256' },
      { label: 'Server Access', val: 'Zero plaintext' },
    ],
    codeTitle: 'crypto_vault.js',
    fileType: 'javascript',
    code: `// Client-side key derivation & AES-GCM encryption
const salt = crypto.getRandomValues(new Uint8Array(16));
const derivedKey = await crypto.subtle.deriveKey(
  { name: "PBKDF2", salt, iterations: 100000, hash: "SHA-256" },
  masterKeyMaterial,
  { name: "AES-GCM", length: 256 },
  false,
  ["encrypt", "decrypt"]
);

// Payload encrypted BEFORE transmission or disk storage
const iv = crypto.getRandomValues(new Uint8Array(12));
const ciphertext = await crypto.subtle.encrypt(
  { name: "AES-GCM", iv },
  derivedKey,
  rawPayloadBuffer
);`,
  },
];

const BENTO_PILLARS = [
  {
    icon: Database,
    title: 'Offline-First SQLite Engine',
    desc: 'Works completely offline. Local IndexedDB on web and encrypted SQLite on mobile, synchronizing with conflict-free delta streams.',
    stat: '100% Offline Capable',
  },
  {
    icon: Scale,
    title: 'Deterministic Balance Auditing',
    desc: 'Zero floating-point rounding errors. Accounts are verified against balance invariants with every ledger write.',
    stat: 'Double-Entry Invariant',
  },
  {
    icon: Cpu,
    title: 'Sub-4ms Ingestion Pipeline',
    desc: 'High-speed tokenization without cloud AI latency or unneeded remote LLM dependencies for basic parsing.',
    stat: '< 4ms Parse Latency',
  },
  {
    icon: FileCode,
    title: 'Zero Lock-in Export System',
    desc: 'Full data sovereignty. One-click export to clean JSON, CSV, or raw SQLite database at any time.',
    stat: 'Open JSON / CSV Export',
  },
];

export default function ModernFeatures() {
  const [activeTab, setActiveTab] = useState(TABS[0].id);
  const [copied, setCopied] = useState(false);

  const current = TABS.find((t) => t.id === activeTab) || TABS[0];

  const handleCopy = () => {
    navigator.clipboard?.writeText(current.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="dev-features-container">
      {/* ── Tab Selector ── */}
      <div className="dev-tab-nav" role="tablist">
        {TABS.map((tab) => {
          const Icon = tab.icon;
          const isActive = tab.id === activeTab;
          return (
            <button
              key={tab.id}
              role="tab"
              aria-selected={isActive}
              className={`dev-tab-btn ${isActive ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.id)}
            >
              <Icon size={15} className="dev-tab-icon" />
              <span>{tab.label}</span>
              {isActive && <motion.div layoutId="devTabIndicator" className="dev-tab-indicator" />}
            </button>
          );
        })}
      </div>

      {/* ── Active Module Explorer ── */}
      <div className="dev-console-shell">
        <AnimatePresence mode="wait">
          <motion.div
            key={current.id}
            className="dev-console-grid"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={{ duration: 0.24, ease: 'easeOut' }}
          >
            {/* Left: Spec Details */}
            <div className="dev-spec-col">
              <div className="dev-module-badge">
                <span className="dev-badge-dot" />
                <code>{current.tag}</code>
              </div>

              <h3 className="dev-spec-title">{current.title}</h3>
              <p className="dev-spec-desc">{current.desc}</p>

              <div className="dev-metrics-list">
                {current.specs.map((s, idx) => (
                  <div key={idx} className="dev-metric-item">
                    <span className="dev-metric-label">{s.label}</span>
                    <span className="dev-metric-val">{s.val}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Right: Code Console */}
            <div className="dev-terminal-col">
              <div className="dev-terminal-chrome">
                <div className="dev-terminal-dots">
                  <span className="dot dot-red" />
                  <span className="dot dot-yellow" />
                  <span className="dot dot-green" />
                </div>
                <div className="dev-terminal-filename">
                  <code>{current.codeTitle}</code>
                </div>
                <button
                  type="button"
                  className="dev-copy-btn"
                  onClick={handleCopy}
                  title="Copy code snippet"
                  aria-label="Copy code snippet"
                >
                  {copied ? <Check size={13} className="text-green" /> : <Copy size={13} />}
                  <span>{copied ? 'Copied' : 'Copy'}</span>
                </button>
              </div>

              <div className="dev-terminal-body">
                <pre className="dev-code-block">
                  <code>{current.code}</code>
                </pre>
              </div>

              <div className="dev-terminal-footer">
                <span className="dev-footer-pill live-pill">
                  <span className="live-dot" />
                  RUNTIME: OPTIMIZED
                </span>
                <span className="dev-footer-pill latency-pill">LATENCY: 3.4ms</span>
              </div>
            </div>
          </motion.div>
        </AnimatePresence>
      </div>

      {/* ── 4 Architecture Bento Pillars ── */}
      <div className="dev-bento-grid">
        {BENTO_PILLARS.map((p, idx) => {
          const Icon = p.icon;
          return (
            <motion.div
              key={p.title}
              className="dev-bento-card"
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-40px' }}
              transition={{ delay: idx * 0.08, duration: 0.4 }}
            >
              <div className="dev-bento-icon-wrap">
                <Icon size={18} />
              </div>
              <h4 className="dev-bento-title">{p.title}</h4>
              <p className="dev-bento-desc">{p.desc}</p>
              <div className="dev-bento-tag">
                <code>{p.stat}</code>
              </div>
            </motion.div>
          );
        })}
      </div>
    </div>
  );
}
