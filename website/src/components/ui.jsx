import { createContext, useCallback, useContext, useEffect, useId, useRef, useState } from 'react'
import { useLang } from '../i18n.jsx'
import { asset } from '../lib/asset.js'

// Toasts --------------------------------------------------------------------

const ToastContext = createContext(() => {})

const TONES = {
  info: 'border-white/12 text-white',
  success: 'border-emerald/40 text-white',
  error: 'border-redstone/50 text-white',
}

const TONE_DOT = {
  info: 'text-bell',
  success: 'text-emerald',
  error: 'text-redstone',
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const seq = useRef(0)

  const show = useCallback((message, tone = 'info') => {
    if (!message) return
    seq.current += 1
    const id = seq.current
    setToasts((list) => [...list.filter((toast) => toast.message !== message), { id, message, tone }].slice(-3))
    window.setTimeout(() => setToasts((list) => list.filter((toast) => toast.id !== id)), tone === 'error' ? 5200 : 2800)
  }, [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-24 z-[80] flex flex-col items-center gap-2 px-4 md:bottom-5" role="status" aria-live="polite">
        {toasts.map((toast) => (
          <p key={toast.id} className={`panel toast-in pointer-events-auto flex max-w-md items-center gap-2.5 px-4 py-2.5 text-sm font-semibold ${TONES[toast.tone]}`}>
            <span className={`dot shrink-0 ${TONE_DOT[toast.tone]}`} />
            {toast.message}
          </p>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  return useContext(ToastContext)
}

// Clipboard -----------------------------------------------------------------

function fallbackCopy(value) {
  const area = document.createElement('textarea')
  area.value = value
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.left = '-9999px'
  document.body.appendChild(area)
  area.select()
  try {
    document.execCommand('copy')
  } catch {
    // Clipboard can be blocked; the toast still confirms intent.
  }
  area.remove()
}

export function writeClipboard(value) {
  if (navigator.clipboard?.writeText) {
    void navigator.clipboard.writeText(value).catch(() => fallbackCopy(value))
    return
  }
  fallbackCopy(value)
}

export function CopyButton({ value, children, copiedLabel, toast, className = '', title }) {
  const showToast = useToast()
  const { copy } = useLang()
  const [copied, setCopied] = useState(false)
  const timer = useRef(0)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return (
    <button
      type="button"
      className={className}
      title={title ?? `${copy.ui.copy} ${value}`}
      onClick={() => {
        writeClipboard(value)
        setCopied(true)
        showToast(toast ?? copy.ui.copied, 'success')
        window.clearTimeout(timer.current)
        timer.current = window.setTimeout(() => setCopied(false), 1800)
      }}
    >
      {copied ? (copiedLabel ?? copy.ui.copied) : children}
    </button>
  )
}

// Layout --------------------------------------------------------------------

export function Section({ id, children, className = '' }) {
  return (
    <section id={id} className={`relative px-4 py-20 sm:px-6 sm:py-24 ${className}`}>
      <div className="mx-auto w-full max-w-6xl">{children}</div>
    </section>
  )
}

export function SectionHeading({ kicker, title, sub, align = 'left' }) {
  return (
    <div className={align === 'center' ? 'mx-auto max-w-2xl text-center' : 'max-w-2xl'}>
      {kicker ? <p className="kicker mb-3">{kicker}</p> : null}
      <h2 className="font-display text-3xl font-extrabold text-white sm:text-[2.6rem] sm:leading-[1.08]">{title}</h2>
      {sub ? <p className="mt-3 max-w-[52ch] text-base leading-relaxed text-mist/70">{sub}</p> : null}
    </div>
  )
}

// Minecraft-flavoured pieces -----------------------------------------------

/** An item texture from the Aetherion resource pack in an inventory slot. */
export function ItemSlot({ item, size = 'h-14 w-14', className = '', alt = '' }) {
  return (
    <span className={`slot shrink-0 ${size} ${className}`}>
      {item ? <img src={asset(`items/${item}.png`)} alt={alt} draggable="false" /> : null}
    </span>
  )
}

const STATUS_STYLE = {
  online: { text: 'text-emerald', live: true },
  starting: { text: 'text-gold', live: true },
  restarting: { text: 'text-gold', live: true },
  preparing: { text: 'text-lapis', live: true },
  stopping: { text: 'text-gold', live: true },
  offline: { text: 'text-ash', live: false },
  failed: { text: 'text-redstone', live: false },
}

export function StatusPill({ status, size = 'md' }) {
  const { copy } = useLang()
  const style = STATUS_STYLE[status] ?? STATUS_STYLE.offline
  const label = copy.app.status[status] ?? status
  const pad = size === 'lg' ? 'px-3 py-1.5 text-sm' : 'px-2.5 py-1 text-xs'
  return (
    <span
      className={`notch inline-flex items-center gap-2 bg-white/[0.06] font-bold ${pad} ${style.text}`}
    >
      <span className={`dot ${style.live ? 'dot-live' : ''}`} aria-hidden="true" />
      {label}
    </span>
  )
}

export function Spinner({ className = '' }) {
  return <span className={`spinner inline-block ${className}`} aria-hidden="true" />
}

export function Skeleton({ className = '' }) {
  return <span className={`skeleton block ${className}`} aria-hidden="true" />
}

export function Notice({ tone = 'info', children, action }) {
  const tones = {
    info: 'border-lapis/30 bg-lapis/[0.07]',
    warn: 'border-gold/35 bg-gold/[0.07]',
    error: 'border-redstone/40 bg-redstone/[0.08]',
    success: 'border-emerald/35 bg-emerald/[0.07]',
  }
  const dots = { info: 'text-lapis', warn: 'text-gold', error: 'text-redstone', success: 'text-emerald' }
  return (
    <div className={`flex flex-wrap items-center gap-3 rounded-md border px-4 py-3 text-sm text-white/90 ${tones[tone]}`}>
      <span className={`dot shrink-0 ${dots[tone]}`} aria-hidden="true" />
      <div className="min-w-0 flex-1">{children}</div>
      {action}
    </div>
  )
}

// Dialog --------------------------------------------------------------------

export function Dialog({ open, onClose, title, children, footer }) {
  const titleId = useId()
  const panel = useRef(null)
  const { copy } = useLang()

  useEffect(() => {
    if (!open) return undefined
    const previous = document.activeElement
    const onKey = (event) => {
      if (event.key === 'Escape') onClose()
      if (event.key === 'Tab' && panel.current) {
        const focusable = panel.current.querySelectorAll('button, [href], input, select, textarea')
        const first = focusable[0]
        const last = focusable[focusable.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last?.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first?.focus()
        }
      }
    }
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    window.setTimeout(() => panel.current?.querySelector('input, button')?.focus(), 30)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
      previous?.focus?.()
    }
  }, [open, onClose])

  if (!open) return null
  return (
    <div
      className="scrim-in fixed inset-0 z-[70] flex items-end justify-center bg-black/70 p-4 sm:items-center"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div ref={panel} role="dialog" aria-modal="true" aria-labelledby={titleId} className="panel dialog-in w-full max-w-md p-6">
        <div className="flex items-start justify-between gap-4">
          <h2 id={titleId} className="text-lg font-bold text-white">
            {title}
          </h2>
          <button type="button" className="btn btn-ghost btn-sm -mt-1 -mr-2 !px-2" onClick={onClose} aria-label={copy.ui.close}>
            ✕
          </button>
        </div>
        <div className="mt-3 text-sm leading-relaxed text-mist/80">{children}</div>
        {footer ? <div className="mt-6 flex flex-wrap justify-end gap-2">{footer}</div> : null}
      </div>
    </div>
  )
}
