import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';

const COLORS = ['#5E72E4', '#38A169', '#4A90E2', '#E07A5F', '#8E7CC3', '#D97706'];

export default function Confetti({ active }) {
  const [pieces, setPieces] = useState([]);

  useEffect(() => {
    if (active) {
      // Generate 50 confetti pieces
      const newPieces = Array.from({ length: 50 }).map((_, i) => ({
        id: i,
        x: Math.random() * 100, // starting X percentage
        delay: Math.random() * 0.2,
        duration: 1 + Math.random() * 2,
        color: COLORS[Math.floor(Math.random() * COLORS.length)],
        rotation: Math.random() * 360,
        scale: 0.5 + Math.random() * 0.5,
      }));
      setPieces(newPieces);

      // Clear after 3 seconds
      const t = setTimeout(() => setPieces([]), 3000);
      return () => clearTimeout(t);
    }
  }, [active]);

  if (!active || pieces.length === 0) return null;

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        pointerEvents: 'none',
        zIndex: 9999,
        overflow: 'hidden',
      }}
    >
      {pieces.map((p) => (
        <motion.div
          key={p.id}
          initial={{
            y: '-10vh',
            x: `${p.x}vw`,
            rotate: p.rotation,
            scale: p.scale,
          }}
          animate={{
            y: '110vh',
            x: `${p.x + (Math.random() * 20 - 10)}vw`,
            rotate: p.rotation + 360 * 2,
          }}
          transition={{
            duration: p.duration,
            delay: p.delay,
            ease: 'easeOut',
          }}
          style={{
            position: 'absolute',
            width: '10px',
            height: '16px',
            backgroundColor: p.color,
            borderRadius: '2px',
          }}
        />
      ))}
    </div>
  );
}
