import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { easeOut, hoverLift, springSoft, tapPress } from '../motion.js'

const ToastContext = createContext((message) => {
  void message
})

export function ToastProvider({ children }) {
  const [message, setMessage] = useState('')
  const timer = useRef(0)
  const reduced = useReducedMotion()

  function showToast(next) {
    setMessage(next)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setMessage(''), 2400)
  }

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return (
    <ToastContext.Provider value={showToast}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-6 z-[60] flex justify-center px-4">
        <AnimatePresence>
          {message ? (
            <motion.p
              key={message}
              role="status"
              aria-live="polite"
              initial={reduced ? { opacity: 0 } : { opacity: 0, y: 18, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={reduced ? { opacity: 0 } : { opacity: 0, y: 12, scale: 0.98 }}
              transition={reduced ? { duration: 0.2 } : springSnappySafe(reduced)}
              className="rounded-full border border-cyan/35 bg-void/95 px-4 py-2.5 text-sm font-bold text-white shadow-[0_12px_40px_rgba(0,0,0,0.45)] backdrop-blur"
            >
              {message}
            </motion.p>
          ) : null}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  )
}

function springSnappySafe(reduced) {
  if (reduced) return { duration: 0.2 }
  return { type: 'spring', stiffness: 460, damping: 32, mass: 0.7 }
}

function writeClipboard(value) {
  if (navigator.clipboard?.writeText) {
    void navigator.clipboard.writeText(value).catch(() => fallbackCopy(value))
    return
  }
  fallbackCopy(value)
}

function fallbackCopy(value) {
  const ta = document.createElement('textarea')
  ta.value = value
  ta.setAttribute('readonly', '')
  ta.style.position = 'fixed'
  ta.style.left = '-9999px'
  document.body.appendChild(ta)
  ta.select()
  try {
    document.execCommand('copy')
  } catch {
    // Clipboard can be blocked in some browsers; the toast still confirms intent.
  }
  ta.remove()
}

export function CopyButton({
  value,
  children,
  copiedLabel = 'Kopiert!',
  toast = 'Kopiert',
  className = '',
  title,
}) {
  const showToast = useContext(ToastContext)
  const [copied, setCopied] = useState(false)
  const reduced = useReducedMotion()

  function copy() {
    writeClipboard(value)
    setCopied(true)
    showToast(toast)
    window.setTimeout(() => setCopied(false), 2200)
  }

  return (
    <motion.button
      type="button"
      onClick={copy}
      className={`cursor-pointer ${className}`}
      title={title ?? `Kopiert ${value}`}
      whileHover={reduced ? undefined : { y: -1, scale: 1.03 }}
      whileTap={reduced ? undefined : tapPress}
      transition={springSoft}
    >
      <span className="inline-grid place-items-center">
        <AnimatePresence mode="popLayout" initial={false}>
          <motion.span
            key={copied ? 'copied' : 'idle'}
            className="col-start-1 row-start-1 inline-flex items-center gap-2"
            initial={reduced ? { opacity: 0 } : { opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={reduced ? { opacity: 0 } : { opacity: 0, y: -8 }}
            transition={{ duration: reduced ? 0.15 : 0.28, ease: easeOut }}
          >
            {copied ? copiedLabel : children}
          </motion.span>
        </AnimatePresence>
      </span>
    </motion.button>
  )
}

export function Reveal({ children, className = '', delay = 0 }) {
  const reduced = useReducedMotion()
  return (
    <motion.div
      className={className}
      initial={reduced ? false : { opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.14, margin: '0px 0px -6% 0px' }}
      transition={{ duration: reduced ? 0 : 0.72, delay: reduced ? 0 : delay / 1000, ease: easeOut }}
    >
      {children}
    </motion.div>
  )
}

export function MotionCard({ as = 'article', children, className = '', hover = true, ...props }) {
  const reduced = useReducedMotion()
  const Card = as === 'figure' ? motion.figure : motion.article
  return (
    <Card
      className={`glass h-full ${className}`}
      whileHover={reduced || !hover ? undefined : hoverLift}
      whileTap={reduced || !hover ? undefined : { y: -1, scale: 0.995 }}
      transition={springSoft}
      {...props}
    >
      {children}
    </Card>
  )
}

export function MotionLink({ children, className = '', ...props }) {
  const reduced = useReducedMotion()
  return (
    <motion.a
      className={className}
      whileHover={reduced ? undefined : { y: -2 }}
      whileTap={reduced ? undefined : tapPress}
      transition={springSoft}
      {...props}
    >
      {children}
    </motion.a>
  )
}

export function Section({ id, children, className = '' }) {
  return (
    <section id={id} className={`relative px-4 py-20 sm:px-6 sm:py-24 lg:py-28 ${className}`}>
      <div className="mx-auto w-full max-w-6xl">{children}</div>
    </section>
  )
}

export function Kicker({ children }) {
  return (
    <p className="mb-3 text-xs font-bold tracking-[0.28em] text-cyan uppercase sm:text-[0.7rem]">
      {children}
    </p>
  )
}

export function SectionTitle({ children, en }) {
  return (
    <div className="max-w-3xl">
      <h2 className="font-display text-3xl font-bold tracking-wide text-white sm:text-4xl lg:text-5xl">
        {children}
      </h2>
      {en ? <p className="mt-2 text-sm text-mist/70">{en}</p> : null}
    </div>
  )
}

