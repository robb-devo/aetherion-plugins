import { useEffect, useState } from 'react'
import { NAV, SITE } from '../content.js'
import { CopyButton } from './ui.jsx'

export default function Nav() {
  const [open, setOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : ''
    return () => {
      document.body.style.overflow = ''
    }
  }, [open])

  return (
    <header
      className={`fixed inset-x-0 top-0 z-40 transition-colors ${
        scrolled || open ? 'bg-void/80 backdrop-blur-xl' : 'bg-transparent'
      }`}
    >
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
        <a href="#top" className="flex items-center gap-2.5 no-underline">
          <img src="/favicon.svg" alt="" className="h-8 w-8" />
          <span className="font-display text-sm font-bold tracking-[0.22em] text-white">
            AETHERION
          </span>
        </a>

        <nav className="hidden items-center gap-7 md:flex" aria-label="Hauptnavigation">
          {NAV.map((item) => (
            <a
              key={item.href}
              href={item.href}
              className="text-sm font-semibold text-mist/80 no-underline transition-colors hover:text-white"
            >
              {item.label}
            </a>
          ))}
        </nav>

        <div className="hidden items-center gap-2 md:flex">
          <CopyButton
            value={SITE.ip}
            className="btn btn-ghost !px-3.5 !py-2 text-xs"
            copiedLabel="IP kopiert"
            toast="IP kopiert — in Minecraft einfügen"
          >
            {SITE.ip}
          </CopyButton>
          <a href="#support" className="btn btn-primary !px-4 !py-2 text-xs">
            Support
          </a>
        </div>

        <button
          type="button"
          className="inline-flex h-10 w-10 cursor-pointer items-center justify-center rounded-full border border-white/15 bg-white/5 md:hidden"
          aria-expanded={open}
          aria-label={open ? 'Menü schließen' : 'Menü öffnen'}
          onClick={() => setOpen((v) => !v)}
        >
          <span className="sr-only">Menü</span>
          <span className="flex flex-col gap-1.5">
            <span className={`block h-0.5 w-4 bg-white transition ${open ? 'translate-y-2 rotate-45' : ''}`} />
            <span className={`block h-0.5 w-4 bg-white transition ${open ? 'opacity-0' : ''}`} />
            <span className={`block h-0.5 w-4 bg-white transition ${open ? '-translate-y-2 -rotate-45' : ''}`} />
          </span>
        </button>
      </div>

      {open ? (
        <div className="fixed inset-x-0 top-[57px] bottom-0 z-40 overflow-y-auto border-t border-white/10 bg-void px-4 py-5 md:hidden">
          <nav className="mx-auto flex max-w-6xl flex-col gap-3" aria-label="Mobilnavigation">
            {NAV.map((item) => (
              <a
                key={item.href}
                href={item.href}
                className="rounded-xl px-3 py-3 text-base font-semibold text-white no-underline hover:bg-white/5"
                onClick={() => setOpen(false)}
              >
                {item.label}
              </a>
            ))}
            <CopyButton
              value={SITE.ip}
              className="btn btn-ghost mt-2 w-full"
              copiedLabel="IP kopiert"
              toast="IP kopiert — in Minecraft einfügen"
            >
              IP kopieren · {SITE.ip}
            </CopyButton>
          </nav>
        </div>
      ) : null}
    </header>
  )
}
