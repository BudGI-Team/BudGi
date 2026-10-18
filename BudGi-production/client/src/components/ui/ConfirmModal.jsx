import React from 'react';
import Modal from './Modal';
import { motion } from 'framer-motion';

export default function ConfirmModal({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmText = 'Delete',
  cancelText = 'Cancel',
  isDanger = true,
}) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <div style={{ padding: '16px 0', color: 'var(--text-secondary)' }}>
        <p style={{ margin: '0 0 24px', fontSize: 15, lineHeight: 1.5 }}>{message}</p>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={onClose}
            style={{
              padding: '10px 20px',
              borderRadius: 10,
              border: '1px solid var(--border-strong)',
              background: 'transparent',
              color: 'var(--text-primary)',
              cursor: 'pointer',
              fontWeight: 600,
            }}
          >
            {cancelText}
          </motion.button>
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={() => {
              onConfirm();
              onClose();
            }}
            style={{
              padding: '10px 20px',
              borderRadius: 10,
              border: 'none',
              background: isDanger ? 'var(--red, #FF453A)' : 'var(--btn-primary-bg)',
              color: isDanger ? '#fff' : 'var(--btn-primary-text)',
              cursor: 'pointer',
              fontWeight: 700,
            }}
          >
            {confirmText}
          </motion.button>
        </div>
      </div>
    </Modal>
  );
}
