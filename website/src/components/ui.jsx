import { createContext, useContext, useEffect, useRef, useState } from 'react'

const ToastContext = createContext((message) => {
  void message
})

export function ToastProvider({ children }) {
  const [message, setMessage] = useState('')
  const timer = useRef(0)

  function showToast(next) {
    setMessage(next)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setMessage(''), 2400)
  }

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return (
    <ToastContext.Provider value={showToast}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className={`pointer-events-none fixed inset-x-0 bottom-6 z-[60] flex justify-center px-4 transition ${
          message ? 'translate-y-0 opacity-100' : 'translate-y-3 opacity-0'
        }`}
      >
        {message ? (
          <p className="rounded-full border border-cyan/35 bg-void/95 px-4 py-2.5 text-sm font-bold text-white shadow-[0_12px_40px_rgba(0,0,0,0.45)] backdrop-blur">
            {message}
          </p>
        ) : null}
      </div>
    </ToastContext.Provider>
  )
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

  function copy() {
    writeClipboard(value)
    setCopied(true)
    showToast(toast)
    window.setTimeout(() => setCopied(false), 2200)
  }

  return (
    <button
      type="button"
      onClick={copy}
      className={`cursor-pointer ${className}`}
      title={title ?? `Kopiert ${value}`}
    >
      {copied ? copiedLabel : children}
    </button>
  )
}

export function Reveal({ children, className = '', delay = 0 }) {
  const ref = useRef(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return undefined
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) {
      el.classList.add('is-visible')
      return undefined
    }
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          el.classList.add('is-visible')
          io.disconnect()
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -8% 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [])

  return (
    <div ref={ref} className={`reveal ${className}`} style={{ transitionDelay: `${delay}ms` }}>
      {children}
    </div>
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
