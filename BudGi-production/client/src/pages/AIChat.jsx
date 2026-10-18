import { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Send, RefreshCw, Zap, ChevronDown, Mic, MicOff, Volume2, VolumeX } from '../lib/icons';
import { useCopilotChat, useChatHistory, useClearChatHistory } from '../hooks/useAI';
import { useQueryClient } from '@tanstack/react-query';
import useStore from '../store/useStore';
import budgiLogo from '../logo/budgi-logo-new.png';
import { ResponsiveContainer, PieChart, Pie, BarChart, Bar, XAxis, YAxis, Tooltip, Cell } from 'recharts';
import './AIChat.css';

const PROMPTS = [
  'How much did I spend on Swiggy this month?',
  'Where is my money going?',
  'Predict my spending for next month',
  'Where can I save more?',
  'What is my biggest expense category?',
  'How long will it take to reach my financial goal?',
];

const FOLLOWUPS = {
  swiggy: ['Show me weekly Swiggy trend', "What's my food budget status?", 'How do I reduce food spend?'],
  zomato: ['Compare Swiggy vs Zomato spend', "What's my food budget status?", 'Give me a weekly breakdown'],
  food: ['Show top 3 food merchants', "What's my food budget this month?", 'How can I cut food costs?'],
  budget: ['Which budgets am I over?', 'Set a stricter food limit', 'Show all budget health'],
  save: ['Where exactly can I cut?', "What's my savings rate?", 'Help me set a savings goal'],
  spend: ['Break this down by week', 'Compare to last month', 'Which category is highest?'],
  subscription: ['List my subscriptions', 'Which subs can I cancel?', 'How much do subs cost monthly?'],
  invest: ["What's my savings rate?", 'How much can I invest monthly?', 'Give me an investment tip'],
  predict: ["How accurate were last month's predictions?", 'What drives my spending?', 'How can I lower next month?'],
  default: ['Tell me more', 'Break this down further', 'What should I do next?'],
};

function getFollowUps(text) {
  if (!text) {
    return FOLLOWUPS.default;
  }
  const t = text.toLowerCase();
  if (t.includes('swiggy') || t.includes('food')) {
    return FOLLOWUPS.food;
  }
  if (t.includes('budget')) {
    return FOLLOWUPS.budget;
  }
  if (t.includes('save') || t.includes('invest')) {
    return FOLLOWUPS.save;
  }
  if (t.includes('predict') || t.includes('forecast')) {
    return FOLLOWUPS.predict;
  }
  if (t.includes('spend') || t.includes('spent') || t.includes('expense') || t.includes('transaction')) {
    return FOLLOWUPS.spend;
  }
  if (t.includes('subscri')) {
    return FOLLOWUPS.subscription;
  }
  return FOLLOWUPS.default;
}

function fmtTime(date) {
  return new Date(date).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: true });
}

export default function AIChat() {
  const qc = useQueryClient();
  const { user } = useStore();
  const historyQuery = useChatHistory();
  const clearHistoryMutation = useClearChatHistory();
  const copilotChat = useCopilotChat();

  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [showScrollBtn, setShowScrollBtn] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [isTtsEnabled, setIsTtsEnabled] = useState(false);

  const bottomRef = useRef(null);
  const inputRef = useRef(null);
  const messagesRef = useRef(null);
  const recognitionRef = useRef(null);

  // Sync state with query data
  useEffect(() => {
    if (historyQuery.data) {
      setMessages(historyQuery.data);
    }
  }, [historyQuery.data]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Persist on change — removed, backend handles it

  const handleScroll = () => {
    if (!messagesRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = messagesRef.current;
    setShowScrollBtn(scrollHeight - scrollTop - clientHeight > 120);
  };

  const scrollToBottom = () => bottomRef.current?.scrollIntoView({ behavior: 'smooth' });

  // Voice input via Web Speech API
  const toggleVoice = () => {
    if (!('webkitSpeechRecognition' in window || 'SpeechRecognition' in window)) {
      return;
    }
    if (isListening) {
      recognitionRef.current?.stop();
      setIsListening(false);
      return;
    }
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    const rec = new SR();
    rec.lang = 'en-IN';
    rec.continuous = false;
    rec.interimResults = false;
    rec.onresult = (e) => {
      const transcript = e.results[0][0].transcript;
      setInput((prev) => prev + transcript);
      setIsListening(false);
    };
    rec.onerror = () => setIsListening(false);
    rec.onend = () => setIsListening(false);
    recognitionRef.current = rec;
    rec.start();
    setIsListening(true);
  };

  const send = async (text) => {
    const msg = (text || input).trim();
    if (!msg) return;
    setInput('');
    inputRef.current?.focus();

    const userMsg = { role: 'user', content: msg, time: new Date().toISOString() };
    const history = [...messages, userMsg];
    setMessages(history);

    try {
      const apiMessages = history.map((m) => ({
        role: m.role === 'ai' ? 'assistant' : 'user',
        content: m.content,
      }));

      setMessages((prev) => [...prev, { role: 'ai', content: '', thinking: true, time: new Date().toISOString() }]);

      const { reply, executedTools } = await copilotChat.mutateAsync(apiMessages);

      if (isTtsEnabled && 'speechSynthesis' in window) {
        // Strip markdown before speaking
        const cleanReply = reply
          .replace(/\*\*(.*?)\*\*/g, '$1')
          .replace(/\{.*?\}/g, '')
          .replace(/[\#\[\]\-\*]/g, '');
        const utterance = new SpeechSynthesisUtterance(cleanReply);
        utterance.lang = 'en-IN';
        utterance.rate = 1.05;
        window.speechSynthesis.speak(utterance);
      }

      // Immediately replace thinking placeholder with real response
      setMessages((prev) => {
        const withoutThinking = prev.filter((m) => !m.thinking);
        return [
          ...withoutThinking,
          {
            role: 'ai',
            content: reply,
            time: new Date().toISOString(),
          },
        ];
      });

      // Detect tool-use in the AI reply and invalidate relevant caches
      if (
        executedTools.includes('create_budget') ||
        executedTools.includes('update_budget') ||
        executedTools.includes('delete_budget')
      ) {
        qc.invalidateQueries({ queryKey: ['budgets'] });
        qc.invalidateQueries({ queryKey: ['analytics'] });
      }
      if (executedTools.includes('create_goal')) {
        qc.invalidateQueries({ queryKey: ['goals'] });
      }
      if (executedTools.includes('add_transaction') || executedTools.includes('bulk_categorize_transactions')) {
        qc.invalidateQueries({ queryKey: ['transactions'] });
        qc.invalidateQueries({ queryKey: ['analytics'] });
      }

      // Also invalidate history query to stay in sync with backend
      qc.invalidateQueries({ queryKey: ['ai', 'chat', 'history'] });
    } catch (err) {
      setMessages((prev) => {
        const withoutThinking = prev.filter((m) => !m.thinking);
        return [
          ...withoutThinking,
          {
            role: 'ai',
            content:
              err.userMessage ||
              'Unable to connect to BudGi Copilot. Please check your internet connection or try again later.',
            isError: true,
            time: new Date().toISOString(),
          },
        ];
      });
    }
  };

  const clearChat = async () => {
    try {
      await clearHistoryMutation.mutateAsync();
      setMessages([]);
      qc.setQueryData(['ai', 'chat', 'history'], []);
    } catch (err) {
      console.error('Failed to clear chat:', err);
    }
  };

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  const handleInputChange = (e) => {
    setInput(e.target.value);
    const el = e.target;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 120) + 'px';
  };

  const isPending = copilotChat.isPending;

  // Find last AI message for follow-ups
  const lastAI = [...messages]
    .reverse()
    .find((m) => (m.role === 'ai' || m.role === 'assistant') && !m.thinking && !m.isError);
  const followUps = lastAI ? getFollowUps(lastAI.content) : [];

  return (
    <div className="ai-page">
      {/* Header */}
      <div className="ai-header glass">
        <div className="ai-header-avatar-wrap">
          <div className="ai-header-avatar ai-header-avatar-logo">
            <img src={budgiLogo} alt="BudGi" />
          </div>
          <span className="ai-online-dot" />
        </div>
        <div className="ai-header-text desktop-only">
          <div className="ai-header-title">BudGi Copilot</div>
          <div className="ai-header-sub">
            <span className="ai-status-green">●</span> Autonomous
          </div>
        </div>
        <div className="ai-header-text mobile-only">
          <div className="ai-header-title" style={{ fontSize: '13px' }}>
            Copilot
          </div>
          <div className="ai-header-sub" style={{ fontSize: '10px' }}>
            Autonomous
          </div>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <motion.button
            className="ai-clear-btn"
            onClick={() => {
              if (isTtsEnabled) window.speechSynthesis.cancel();
              setIsTtsEnabled(!isTtsEnabled);
            }}
            whileTap={{ scale: 0.95 }}
            title={isTtsEnabled ? 'Mute voice responses' : 'Enable voice responses'}
          >
            {isTtsEnabled ? <Volume2 size={14} /> : <VolumeX size={14} />}
          </motion.button>
          {messages.length > 0 && (
            <motion.button className="ai-clear-btn" onClick={clearChat} whileTap={{ scale: 0.95 }}>
              <RefreshCw size={14} /> New chat
            </motion.button>
          )}
        </div>
      </div>

      {/* Body */}
      <div className="ai-body">
        <div className="ai-messages" ref={messagesRef} onScroll={handleScroll}>
          {messages.length === 0 ? (
            <WelcomeScreen onPrompt={send} />
          ) : (
            <>
              <div className="ai-date-divider">
                <span>Today</span>
              </div>
              <AnimatePresence initial={false}>
                {messages.map((msg, i) => {
                  const isUser = msg.role === 'user';
                  const isAI = msg.role === 'ai' || msg.role === 'assistant';
                  const isLastAI =
                    isAI &&
                    !msg.thinking &&
                    !msg.isError &&
                    messages.slice(i + 1).every((m) => (m.role !== 'ai' && m.role !== 'assistant') || m.thinking);

                  return (
                    <motion.div
                      key={msg.id || i}
                      className={`ai-message-row ${isUser ? 'ai-message-user' : 'ai-message-ai'}`}
                      initial={{ opacity: 0, y: 15 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ type: 'spring', stiffness: 300, damping: 25, mass: 0.8 }}
                    >
                      <div className="ai-message-avatar">
                        {isAI ? (
                          <div className="ai-avatar-logo">
                            <img src={budgiLogo} alt="BudGi" />
                          </div>
                        ) : (
                          <div className="ai-avatar-user">
                            {user?.profileImage ? (
                              <img src={user.profileImage} alt={user.name} />
                            ) : (
                              <span>{(user?.name || 'U')[0].toUpperCase()}</span>
                            )}
                          </div>
                        )}
                      </div>

                      <div className="ai-message-content-wrapper">
                        <div className={isUser ? 'ai-user-name' : 'ai-ai-name'}>
                          <span>{isUser ? user?.name || 'You' : 'BudGi Copilot'}</span>
                          <span className="ai-message-time">{fmtTime(msg.time)}</span>
                        </div>

                        <div className="ai-message-bubble">
                          <div className="ai-message-content">
                            {msg.thinking ? (
                              <div className="ai-message-thinking">
                                <ThinkingDots />
                              </div>
                            ) : msg.isError ? (
                              <div className="ai-message-error">{msg.content}</div>
                            ) : (
                              <MessageContent content={msg.content} />
                            )}
                          </div>
                        </div>

                        {isLastAI && followUps.length > 0 && (
                          <motion.div
                            className="ai-followup-chips"
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: 0.3 }}
                          >
                            {followUps.map((f) => (
                              <motion.button
                                key={f}
                                className="ai-followup-chip"
                                onClick={() => send(f)}
                                whileHover={{ scale: 1.03 }}
                                whileTap={{ scale: 0.97 }}
                                disabled={isPending}
                              >
                                <Zap size={13} /> {f}
                              </motion.button>
                            ))}
                          </motion.div>
                        )}
                      </div>
                    </motion.div>
                  );
                })}
              </AnimatePresence>
              <div ref={bottomRef} style={{ height: 8 }} />
            </>
          )}
        </div>

        <AnimatePresence>
          {showScrollBtn && (
            <motion.button
              className="ai-scroll-btn"
              onClick={scrollToBottom}
              initial={{ opacity: 0, scale: 0.8, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.8, y: 10 }}
              transition={{ duration: 0.2 }}
            >
              <ChevronDown size={16} />
            </motion.button>
          )}
        </AnimatePresence>

        {/* Input area - Modern Floating Pill */}
        <div className="ai-input-floating-wrapper">
          {messages.length === 0 && (
            <motion.div
              className="ai-quick-prompts"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.5, duration: 0.5 }}
            >
              {PROMPTS.slice(0, 3).map((p) => (
                <motion.button
                  key={p}
                  className="ai-quick-btn"
                  onClick={() => send(p)}
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                >
                  <Zap size={12} /> {p}
                </motion.button>
              ))}
            </motion.div>
          )}

          <div className="ai-input-area glass">
            <textarea
              ref={inputRef}
              className="ai-input"
              placeholder="Ask anything about your finances..."
              value={input}
              onChange={handleInputChange}
              onKeyDown={handleKey}
              rows={1}
              disabled={isPending}
            />

            {/* Voice button */}
            <motion.button
              className={`ai-voice-btn ${isListening ? 'ai-voice-active' : ''}`}
              onClick={toggleVoice}
              whileTap={{ scale: 0.9 }}
              title={isListening ? 'Stop listening' : 'Voice input'}
            >
              {isListening ? <MicOff size={15} /> : <Mic size={15} />}
            </motion.button>

            <motion.button
              className={`ai-send-btn ${!input.trim() || isPending ? 'ai-send-disabled' : 'ai-send-active'}`}
              onClick={() => send()}
              disabled={!input.trim() || isPending}
              whileHover={input.trim() && !isPending ? { scale: 1.08 } : {}}
              whileTap={input.trim() && !isPending ? { scale: 0.9 } : {}}
            >
              <Send size={16} />
            </motion.button>
          </div>

          <p className="ai-disclaimer">BudGi AI can make mistakes. Verify important financial decisions.</p>
        </div>
      </div>
    </div>
  );
}

function WelcomeScreen({ onPrompt }) {
  const getGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  };

  return (
    <motion.div
      className="ai-welcome-modern"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
    >
      <motion.div
        className="ai-hero-logo"
        initial={{ scale: 0.9, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.4 }}
      >
        <img src={budgiLogo} alt="BudGi" />
      </motion.div>

      <motion.h1
        className="ai-hero-title"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1, duration: 0.4 }}
      >
        {getGreeting()}, I'm BudGi.
      </motion.h1>

      <motion.p
        className="ai-hero-subtitle"
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.18, duration: 0.4 }}
      >
        Your intelligent financial copilot. Ask anything about your expenses, budgets, cashflow, and goals.
      </motion.p>

      <div className="ai-hero-grid">
        {PROMPTS.slice(0, 4).map((p, i) => (
          <motion.button
            key={p}
            className="ai-hero-card glass-card"
            onClick={() => onPrompt(p)}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.4 + i * 0.1, ease: [0.16, 1, 0.3, 1] }}
            whileHover={{
              y: -6,
              scale: 1.02,
              boxShadow: '0 20px 40px rgba(124,106,245,0.15), inset 0 1px 0 rgba(255,255,255,0.1)',
              borderColor: 'rgba(124,106,245,0.4)',
            }}
            whileTap={{ scale: 0.97 }}
          >
            <div className="ai-hero-card-icon">
              <Zap size={16} />
            </div>
            <span className="ai-hero-card-text">{p}</span>
          </motion.button>
        ))}
      </div>
    </motion.div>
  );
}

function ThinkingDots() {
  return (
    <div className="thinking-dots">
      {[0, 1, 2].map((i) => (
        <motion.span
          key={i}
          animate={{ scale: [0.7, 1, 0.7], opacity: [0.4, 1, 0.4] }}
          transition={{ duration: 1.1, repeat: Infinity, delay: i * 0.22 }}
        />
      ))}
    </div>
  );
}

function MessageContent({ content }) {
  let chartData = null;
  let actionIntent = null;
  let textContent = content;

  // Extract action_intent JSON if present
  const intentMatch = textContent.match(/\{[\s\S]*?"type"\s*:\s*"action_intent"[\s\S]*?\}/);
  if (intentMatch) {
    try {
      actionIntent = JSON.parse(intentMatch[0]);
      textContent = textContent.replace(intentMatch[0], '').trim();
    } catch (_e) {}
  }

  const chartStartIdx = textContent.indexOf('{"type":"chart"');
  const chartStartIdx2 = textContent.indexOf('{"type": "chart"');
  const startIdx = chartStartIdx >= 0 ? chartStartIdx : chartStartIdx2;

  if (startIdx >= 0) {
    let depth = 0;
    let endIdx = -1;
    for (let i = startIdx; i < textContent.length; i++) {
      if (textContent[i] === '{') depth++;
      else if (textContent[i] === '}') {
        depth--;
        if (depth === 0) {
          endIdx = i + 1;
          break;
        }
      }
    }
    if (endIdx > startIdx) {
      try {
        const jsonStr = textContent.substring(startIdx, endIdx);
        const parsed = JSON.parse(jsonStr);
        if (parsed.data && Array.isArray(parsed.data)) {
          chartData = parsed;
          textContent = (textContent.substring(0, startIdx) + textContent.substring(endIdx)).trim();
        }
      } catch {
        // Silently ignore malformed chart JSON — just render as text
      }
    }
  }

  const lines = textContent.split('\n');
  const COLORS = ['#5E72E4', '#38A169', '#E07A5F', '#4A90E2', '#8E7CC3', '#D97706', '#2A9D8F', '#D95D74'];

  const formatText = (text) => {
    return text
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2" class="msg-link">$1</a>');
  };

  return (
    <div className="msg-content">
      {lines.map((line, i) => {
        if (!line) return <span key={i} className="msg-br" />;

        // Headers
        if (line.startsWith('### ')) {
          return (
            <h3 key={i} className="msg-header">
              {line.replace('### ', '')}
            </h3>
          );
        }

        // Lists
        if (line.match(/^[-•]\s/)) {
          return (
            <div key={i} className="msg-list-item">
              <span className="msg-bullet">•</span>
              <span dangerouslySetInnerHTML={{ __html: formatText(line.replace(/^[-•]\s/, '')) }} />
            </div>
          );
        }

        if (line.match(/^\d+\.\s/)) {
          const num = line.match(/^(\d+)\./)[1];
          return (
            <div key={i} className="msg-list-item">
              <span className="msg-num">{num}.</span>
              <span dangerouslySetInnerHTML={{ __html: formatText(line.replace(/^\d+\.\s/, '')) }} />
            </div>
          );
        }

        return <p key={i} dangerouslySetInnerHTML={{ __html: formatText(line) }} />;
      })}

      {chartData && (
        <motion.div
          className="chat-chart-wrap"
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.4 }}
          style={{
            marginTop: 16,
            padding: 12,
            background: 'rgba(255,255,255,0.03)',
            borderRadius: 12,
            border: '1px solid rgba(255,255,255,0.1)',
            height: 220,
            width: '100%',
            maxWidth: 400,
          }}
        >
          <ResponsiveContainer width="100%" height="100%">
            {chartData.chartType === 'pie' ? (
              <PieChart>
                <Pie
                  data={chartData.data}
                  cx="50%"
                  cy="50%"
                  innerRadius={40}
                  outerRadius={60}
                  paddingAngle={5}
                  dataKey="value"
                >
                  {chartData.data.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ background: '#1a1a1a', border: '1px solid #333', borderRadius: 8, fontSize: 12 }}
                  itemStyle={{ color: '#fff' }}
                />
              </PieChart>
            ) : (
              <BarChart data={chartData.data} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <XAxis dataKey="name" tick={{ fill: '#888', fontSize: 10 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: '#888', fontSize: 10 }} axisLine={false} tickLine={false} />
                <Tooltip
                  contentStyle={{ background: '#1a1a1a', border: '1px solid #333', borderRadius: 8, fontSize: 12 }}
                  itemStyle={{ color: '#fff' }}
                  cursor={{ fill: 'rgba(255,255,255,0.05)' }}
                />
                <Bar dataKey="value" fill="var(--accent, #38BDF8)" radius={[4, 4, 0, 0]} />
              </BarChart>
            )}
          </ResponsiveContainer>
        </motion.div>
      )}

      {actionIntent && (
        <div
          className="copilot-action-card-web"
          style={{
            marginTop: 16,
            padding: 16,
            background: 'rgba(255, 255, 255, 0.03)',
            border: '1px solid rgba(255, 255, 255, 0.12)',
            borderRadius: 12,
            maxWidth: 420,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#fff' }}>{actionIntent.title}</span>
            <span
              style={{
                fontSize: 9,
                fontWeight: 700,
                padding: '2px 6px',
                background: 'rgba(56, 189, 248, 0.15)',
                color: '#38bdf8',
                borderRadius: 4,
                letterSpacing: '0.5px',
              }}
            >
              PROPOSAL
            </span>
          </div>
          <p style={{ fontSize: 12, color: '#94a3b8', margin: '0 0 10px 0', lineHeight: 1.4 }}>
            {actionIntent.reason}
          </p>
          <div
            style={{
              background: 'rgba(0, 0, 0, 0.4)',
              padding: '8px 12px',
              borderRadius: 8,
              fontSize: 12,
              border: '1px solid rgba(255, 255, 255, 0.06)',
            }}
          >
            {actionIntent.intentType === 'ADJUST_BUDGET' && (
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#fff', fontWeight: 600 }}>{actionIntent.category}</span>
                <span style={{ color: '#38bdf8', fontWeight: 700 }}>
                  ₹{actionIntent.proposedLimit?.toLocaleString()}/mo
                </span>
              </div>
            )}
            {actionIntent.intentType === 'REBALANCE_BUDGET' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-secondary)' }}>{actionIntent.fromCategory}</span>
                  <span style={{ color: 'var(--red)', fontWeight: 700 }}>-₹{actionIntent.amount?.toLocaleString()}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-primary)' }}>{actionIntent.toCategory}</span>
                  <span style={{ color: 'var(--green)', fontWeight: 700 }}>+₹{actionIntent.amount?.toLocaleString()}</span>
                </div>
              </div>
            )}
            {actionIntent.intentType === 'AFFORDABILITY_VERDICT' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-primary)', fontWeight: 600 }}>
                    {actionIntent.itemName} (₹{actionIntent.itemAmount?.toLocaleString()})
                  </span>
                  <span
                    style={{
                      color:
                        actionIntent.verdict === 'SAFE'
                          ? 'var(--green)'
                          : actionIntent.verdict === 'CAUTION'
                            ? 'var(--amber)'
                            : 'var(--red)',
                      fontWeight: 700,
                    }}
                  >
                    {actionIntent.verdict}
                  </span>
                </div>
                <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                  Safe run-rate: ₹{Math.round(actionIntent.safeDailySpendAfter || 0)}/day for {actionIntent.daysUntilPayday} days
                </span>
              </div>
            )}
            {actionIntent.intentType === 'CREATE_GOAL' && (
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#fff', fontWeight: 600 }}>{actionIntent.goalTitle}</span>
                <span style={{ color: '#38bdf8', fontWeight: 700 }}>
                  Target: ₹{actionIntent.targetAmount?.toLocaleString()}
                </span>
              </div>
            )}
            {actionIntent.intentType === 'BULK_CATEGORIZE' && (
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#94a3b8' }}>
                  "{actionIntent.merchantKeyword}" ({actionIntent.count} txs)
                </span>
                <span style={{ color: '#38bdf8', fontWeight: 700 }}>➔ {actionIntent.targetCategory}</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
