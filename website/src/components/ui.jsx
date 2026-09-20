import { useEffect, useRef, useState } from 'react'

export function CopyButton({
  value,
  children,
  copiedLabel = 'Kopiert!',
  className = '',
  title,
}) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(value)
    } catch {
      const ta = document.createElement('textarea')
      ta.value = value
      ta.setAttribute('readonly', '')
      ta.style.position = 'fixed'
      ta.style.left = '-9999px'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      ta.remove()
    }
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1800)
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
