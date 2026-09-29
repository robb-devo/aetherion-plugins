export const easeOut = [0.22, 1, 0.36, 1]

export const springSoft = { type: 'spring', stiffness: 380, damping: 32, mass: 0.85 }

export const tapPress = { scale: 0.98, y: 0 }

export const hoverLift = { y: -5 }

export const fadeUp = {
  hidden: { opacity: 0, y: 22 },
  show: { opacity: 1, y: 0, transition: { duration: 0.7, ease: easeOut } },
}

export const heroStagger = {
  hidden: {},
  show: {
    transition: { staggerChildren: 0.09, delayChildren: 0.08 },
  },
}

export const listStagger = {
  hidden: {},
  show: {
    transition: { staggerChildren: 0.07, delayChildren: 0.04 },
  },
}
