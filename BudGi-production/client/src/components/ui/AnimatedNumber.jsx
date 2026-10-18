import { motion, useSpring, useTransform } from 'framer-motion';
import { useEffect, useState } from 'react';

export default function AnimatedNumber({ value, formatter }) {
  const [hasMounted, setHasMounted] = useState(false);
  const springValue = useSpring(0, {
    stiffness: 100,
    damping: 30,
    restDelta: 0.001,
  });

  useEffect(() => {
    setHasMounted(true);
    springValue.set(value);
  }, [value, springValue]);

  // useTransform hook calls the formatter continuously as the spring animates
  const display = useTransform(springValue, (current) => (hasMounted ? formatter(current) : formatter(value)));

  return <motion.span>{display}</motion.span>;
}
