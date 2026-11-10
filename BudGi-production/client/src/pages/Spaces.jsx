import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Users,
  Plus,
  Copy,
  Check,
  Shield,
  Target,
  ArrowRight,
  X,
  UserPlus,
  Share2,
  Lock,
  Layers,
} from '../lib/icons';
import { spacesApi } from '../lib/api';
import useStore from '../store/useStore';
import toast from 'react-hot-toast';
import { fmt, formatDate } from '../lib/utils';
import './Spaces.css';

const SPACE_TYPES = [
  { id: 'COUPLE', label: 'Couple / Partners', desc: 'Joint household finances & split goals' },
  { id: 'ROOMMATES', label: 'Roommates / Flat', desc: 'Rent, groceries, utilities & shared chores' },
  { id: 'FAMILY', label: 'Family Pool', desc: 'Household savings & family expense tracking' },
  { id: 'CUSTOM', label: 'Project / Trip', desc: 'Vacations, events, and pooled funds' },
];

export default function Spaces() {
  const qc = useQueryClient();
  const user = useStore((s) => s.user);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showJoinModal, setShowJoinModal] = useState(false);
  const [activeSpaceForGoal, setActiveSpaceForGoal] = useState(null);

  // Form states
  const [newSpaceName, setNewSpaceName] = useState('');
  const [newSpaceType, setNewSpaceType] = useState('COUPLE');
  const [joinCode, setJoinCode] = useState('');
  const [copiedCode, setCopiedCode] = useState(null);

  // Goal form
  const [goalTitle, setGoalTitle] = useState('');
  const [goalTarget, setGoalTarget] = useState('');
  const [goalCategory, setGoalCategory] = useState('Emergency');

  // Query spaces
  const { data, isLoading, isError } = useQuery({
    queryKey: ['spaces'],
    queryFn: spacesApi.list,
  });

  const spaces = data?.data?.spaces || [];

  // Create Space Mutation
  const createMutation = useMutation({
    mutationFn: spacesApi.create,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['spaces'] });
      toast.success('Collaborative space created successfully');
      setShowCreateModal(false);
      setNewSpaceName('');
      setNewSpaceType('COUPLE');
    },
    onError: (err) => {
      toast.error(err?.response?.data?.message || 'Failed to create space');
    },
  });

  // Join Space Mutation
  const joinMutation = useMutation({
    mutationFn: spacesApi.join,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['spaces'] });
      toast.success('Joined space successfully');
      setShowJoinModal(false);
      setJoinCode('');
    },
    onError: (err) => {
      toast.error(err?.response?.data?.message || 'Invalid or expired invite code');
    },
  });

  // Add Goal Mutation
  const addGoalMutation = useMutation({
    mutationFn: ({ spaceId, payload }) => spacesApi.addGoal(spaceId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['spaces'] });
      toast.success('Shared goal added to space');
      setActiveSpaceForGoal(null);
      setGoalTitle('');
      setGoalTarget('');
    },
    onError: (err) => {
      toast.error(err?.response?.data?.message || 'Failed to add shared goal');
    },
  });

  const handleCopyCode = (code) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(code);
    toast.success('Invite code copied to clipboard');
    setTimeout(() => setCopiedCode(null), 2500);
  };

  const handleCreateSubmit = (e) => {
    e.preventDefault();
    if (!newSpaceName.trim()) {
      toast.error('Please enter a space name');
      return;
    }
    createMutation.mutate({ name: newSpaceName.trim(), type: newSpaceType });
  };

  const handleJoinSubmit = (e) => {
    e.preventDefault();
    const clean = joinCode.trim().toUpperCase();
    if (!clean || clean.length < 6) {
      toast.error('Please enter a valid invite code');
      return;
    }
    joinMutation.mutate(clean);
  };

  const handleAddGoalSubmit = (e) => {
    e.preventDefault();
    if (!goalTitle.trim()) {
      toast.error('Please enter a goal title');
      return;
    }
    const targetNum = Number(goalTarget);
    if (!targetNum || targetNum <= 0) {
      toast.error('Please enter a valid target amount');
      return;
    }
    addGoalMutation.mutate({
      spaceId: activeSpaceForGoal._id,
      payload: {
        title: goalTitle.trim(),
        targetAmount: targetNum,
        category: goalCategory,
      },
    });
  };

  return (
    <div className="spaces-page-container">
      {/* ── Top Header ── */}
      <div className="spaces-header">
        <div>
          <div className="spaces-badge">
            <Users size={13} />
            <span>CROSS-LEDGER COLLABORATION</span>
          </div>
          <h1 className="spaces-title">Collaborative Spaces</h1>
          <p className="spaces-subtitle">
            Shared budgets, joint household ledgers, and split savings goals synced in real-time across devices.
          </p>
        </div>

        <div className="spaces-header-actions">
          <button
            className="btn-secondary spaces-btn-join"
            onClick={() => setShowJoinModal(true)}
            id="join-space-trigger"
          >
            <UserPlus size={16} />
            <span>Join with Code</span>
          </button>
          <button
            className="btn-primary spaces-btn-create"
            onClick={() => setShowCreateModal(true)}
            id="create-space-trigger"
          >
            <Plus size={16} />
            <span>Create Space</span>
          </button>
        </div>
      </div>

      {/* ── Spaces Grid / Empty State ── */}
      {isLoading ? (
        <div className="spaces-loading">
          <div className="spaces-spinner" />
          <p>Loading collaborative spaces...</p>
        </div>
      ) : isError ? (
        <div className="spaces-error">
          <p>Unable to load spaces at this time.</p>
        </div>
      ) : spaces.length === 0 ? (
        <div className="spaces-empty-state">
          <div className="spaces-empty-icon-wrap">
            <Users size={36} />
          </div>
          <h3>No Collaborative Spaces Yet</h3>
          <p>
            Create a space for your partner, roommates, or family. Share real-time visibility into pooled budgets and
            track shared financial goals together.
          </p>
          <div className="spaces-empty-actions">
            <button className="btn-primary" onClick={() => setShowCreateModal(true)}>
              <Plus size={16} />
              <span>Create Your First Space</span>
            </button>
            <button className="btn-secondary" onClick={() => setShowJoinModal(true)}>
              <UserPlus size={16} />
              <span>Join with Invite Code</span>
            </button>
          </div>
        </div>
      ) : (
        <div className="spaces-grid">
          {spaces.map((space) => {
            const isAdmin = space.members?.some(
              (m) => (m.userId?._id || m.userId) === (user?._id || user?.id) && m.role === 'ADMIN'
            );

            return (
              <motion.div
                key={space._id}
                className="space-card"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2 }}
              >
                <div className="space-card-top">
                  <div>
                    <span className={`space-type-badge type-${(space.type || 'CUSTOM').toLowerCase()}`}>
                      {space.type}
                    </span>
                    <h3 className="space-name">{space.name}</h3>
                  </div>

                  <div className="space-invite-chip" title="Click to copy invite code">
                    <span className="space-invite-label">INVITE CODE:</span>
                    <span className="space-code-text">{space.inviteCode}</span>
                    <button
                      type="button"
                      className="space-copy-btn"
                      onClick={() => handleCopyCode(space.inviteCode)}
                      aria-label="Copy invite code"
                    >
                      {copiedCode === space.inviteCode ? (
                        <Check size={14} className="copied-icon" />
                      ) : (
                        <Copy size={14} />
                      )}
                    </button>
                  </div>
                </div>

                {/* Members list */}
                <div className="space-members-section">
                  <div className="space-section-header">
                    <span className="section-label">MEMBERS ({space.members?.length || 0})</span>
                  </div>
                  <div className="space-members-pile">
                    {space.members?.map((member, i) => {
                      const mUser = member.userId || {};
                      const memberName =
                        mUser.firstName || mUser.lastName
                          ? `${mUser.firstName || ''} ${mUser.lastName || ''}`.trim()
                          : mUser.email?.split('@')[0] || 'Member';
                      const initial = memberName[0]?.toUpperCase() || '?';

                      return (
                        <div key={i} className="member-avatar-item" title={`${memberName} (${member.role})`}>
                          {mUser.profileImage ? (
                            <img src={mUser.profileImage} alt={memberName} className="member-avatar-img" />
                          ) : (
                            <div className="member-avatar-placeholder">{initial}</div>
                          )}
                          <span className="member-name">{memberName}</span>
                          {member.role === 'ADMIN' && <span className="member-role-badge">ADMIN</span>}
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Shared Goals */}
                <div className="space-goals-section">
                  <div className="space-section-header">
                    <span className="section-label">SHARED GOALS ({space.goals?.length || 0})</span>
                    <button
                      type="button"
                      className="add-goal-btn"
                      onClick={() => setActiveSpaceForGoal(space)}
                    >
                      <Plus size={13} />
                      <span>Add Goal</span>
                    </button>
                  </div>

                  {space.goals?.length === 0 ? (
                    <div className="space-goals-empty">
                      <span>No active shared targets. Add a joint goal above.</span>
                    </div>
                  ) : (
                    <div className="space-goals-list">
                      {space.goals.map((g, idx) => {
                        const pct = Math.min(100, Math.round(((g.currentAmount || 0) / (g.targetAmount || 1)) * 100));
                        return (
                          <div key={idx} className="space-goal-item">
                            <div className="space-goal-meta">
                              <span className="goal-title">{g.title}</span>
                              <span className="goal-amounts">
                                {fmt(g.currentAmount || 0)} / {fmt(g.targetAmount)} ({pct}%)
                              </span>
                            </div>
                            <div className="goal-progress-track">
                              <div className="goal-progress-fill" style={{ width: `${pct}%` }} />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                <div className="space-card-footer">
                  <span className="space-created-at">
                    Created {formatDate(space.createdAt, 'medium')}
                  </span>
                </div>
              </motion.div>
            );
          })}
        </div>
      )}

      {/* ── Modal: Create Space ── */}
      <AnimatePresence>
        {showCreateModal && (
          <div className="modal-backdrop" onClick={() => setShowCreateModal(false)}>
            <motion.div
              className="spaces-modal"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="modal-header">
                <h3>Create Collaborative Space</h3>
                <button className="modal-close-btn" onClick={() => setShowCreateModal(false)}>
                  <X size={18} />
                </button>
              </div>

              <form onSubmit={handleCreateSubmit} className="modal-form">
                <div className="form-group">
                  <label htmlFor="space-name-input">Space Name</label>
                  <input
                    id="space-name-input"
                    type="text"
                    placeholder="e.g., Home Ledger, Europe Trip 2026"
                    value={newSpaceName}
                    onChange={(e) => setNewSpaceName(e.target.value)}
                    autoFocus
                    required
                  />
                </div>

                <div className="form-group">
                  <label>Space Purpose</label>
                  <div className="space-types-selector">
                    {SPACE_TYPES.map((t) => (
                      <div
                        key={t.id}
                        className={`type-option ${newSpaceType === t.id ? 'active' : ''}`}
                        onClick={() => setNewSpaceType(t.id)}
                      >
                        <div className="type-option-title">{t.label}</div>
                        <div className="type-option-desc">{t.desc}</div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="modal-actions">
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => setShowCreateModal(false)}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={createMutation.isPending}
                  >
                    {createMutation.isPending ? 'Creating...' : 'Create Space'}
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* ── Modal: Join Space ── */}
      <AnimatePresence>
        {showJoinModal && (
          <div className="modal-backdrop" onClick={() => setShowJoinModal(false)}>
            <motion.div
              className="spaces-modal"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="modal-header">
                <h3>Join a Space</h3>
                <button className="modal-close-btn" onClick={() => setShowJoinModal(false)}>
                  <X size={18} />
                </button>
              </div>

              <form onSubmit={handleJoinSubmit} className="modal-form">
                <div className="form-group">
                  <label htmlFor="join-code-input">8-Character Invite Code</label>
                  <input
                    id="join-code-input"
                    type="text"
                    maxLength={8}
                    placeholder="e.g. 9AF4B12C"
                    value={joinCode}
                    onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                    style={{ textTransform: 'uppercase', letterSpacing: '2px', fontFamily: 'monospace' }}
                    autoFocus
                    required
                  />
                  <p className="form-hint">
                    Ask the space admin or creator for their 8-character invite code.
                  </p>
                </div>

                <div className="modal-actions">
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => setShowJoinModal(false)}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={joinMutation.isPending}
                  >
                    {joinMutation.isPending ? 'Joining...' : 'Join Space'}
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* ── Modal: Add Goal ── */}
      <AnimatePresence>
        {activeSpaceForGoal && (
          <div className="modal-backdrop" onClick={() => setActiveSpaceForGoal(null)}>
            <motion.div
              className="spaces-modal"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="modal-header">
                <h3>Add Shared Goal to {activeSpaceForGoal.name}</h3>
                <button className="modal-close-btn" onClick={() => setActiveSpaceForGoal(null)}>
                  <X size={18} />
                </button>
              </div>

              <form onSubmit={handleAddGoalSubmit} className="modal-form">
                <div className="form-group">
                  <label htmlFor="goal-title-input">Goal Name</label>
                  <input
                    id="goal-title-input"
                    type="text"
                    placeholder="e.g., Summer Trip Fund, Emergency Cushion"
                    value={goalTitle}
                    onChange={(e) => setGoalTitle(e.target.value)}
                    autoFocus
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="goal-target-input">Target Amount (₹)</label>
                  <input
                    id="goal-target-input"
                    type="number"
                    min="1"
                    step="1"
                    placeholder="e.g. 50000"
                    value={goalTarget}
                    onChange={(e) => setGoalTarget(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="goal-category-select">Category</label>
                  <select
                    id="goal-category-select"
                    value={goalCategory}
                    onChange={(e) => setGoalCategory(e.target.value)}
                  >
                    <option value="Emergency">Emergency Fund</option>
                    <option value="Travel">Vacation & Travel</option>
                    <option value="Home">Home & Rent</option>
                    <option value="Vehicle">Vehicle</option>
                    <option value="Investment">Investment</option>
                    <option value="Other">Other</option>
                  </select>
                </div>

                <div className="modal-actions">
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => setActiveSpaceForGoal(null)}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={addGoalMutation.isPending}
                  >
                    {addGoalMutation.isPending ? 'Saving...' : 'Add Goal'}
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}

