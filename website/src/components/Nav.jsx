import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { NAV, SITE } from '../content.js'
import { easeOut, listStagger } from '../motion.js'
import { CopyButton, MotionLink } from './ui.jsx'

export default function Nav() {
  const [open, setOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)
  const [hovered, setHovered] = useState('')
  const reduced = useReducedMotion()

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
    <>
      <motion.header
        className={`fixed inset-x-0 top-0 z-50 ${open || scrolled ? 'backdrop-blur-xl' : ''}`}
        animate={{
          backgroundColor: open
            ? 'rgba(7,6,15,1)'
            : scrolled
              ? 'rgba(7,6,15,0.82)'
              : 'rgba(7,6,15,0)',
        }}
        transition={{ duration: 0.35, ease: easeOut }}
      >
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
          <a href="#top" className="flex items-center gap-2.5 no-underline" onClick={() => setOpen(false)}>
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
                className="relative text-sm font-semibold text-mist/80 no-underline transition-colors duration-300 hover:text-white"
                onMouseEnter={() => setHovered(item.href)}
                onMouseLeave={() => setHovered('')}
              >
                {item.label}
                {hovered === item.href ? (
                  <motion.span
                    layoutId="nav-ink"
                    className="absolute -bottom-1 left-0 h-px w-full bg-cyan"
                    transition={reduced ? { duration: 0 } : { type: 'spring', stiffness: 420, damping: 32 }}
                  />
                ) : null}
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
            <MotionLink href="#support" className="btn btn-primary !px-4 !py-2 text-xs">
              Support
            </MotionLink>
          </div>

          <motion.button
            type="button"
            className="inline-flex h-10 w-10 cursor-pointer items-center justify-center rounded-full border border-white/15 bg-white/5 md:hidden"
            aria-expanded={open}
            aria-label={open ? 'Menü schließen' : 'Menü öffnen'}
            onClick={() => setOpen((v) => !v)}
            whileTap={reduced ? undefined : { scale: 0.94 }}
          >
            <span className="sr-only">Menü</span>
            <span className="flex flex-col gap-1.5">
              <span className={`block h-0.5 w-4 bg-white transition duration-300 ${open ? 'translate-y-2 rotate-45' : ''}`} />
              <span className={`block h-0.5 w-4 bg-white transition duration-300 ${open ? 'opacity-0' : ''}`} />
              <span className={`block h-0.5 w-4 bg-white transition duration-300 ${open ? '-translate-y-2 -rotate-45' : ''}`} />
            </span>
          </motion.button>
        </div>
      </motion.header>

      <AnimatePresence>
        {open ? (
          <motion.div
            className="fixed inset-0 z-40 bg-void pt-16 md:hidden"
            initial={reduced ? { opacity: 0 } : { opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.28, ease: easeOut }}
          >
            <motion.nav
              className="mx-auto flex h-full max-w-6xl flex-col gap-3 overflow-y-auto px-4 py-5"
              aria-label="Mobilnavigation"
              variants={reduced ? undefined : listStagger}
              initial={reduced ? false : 'hidden'}
              animate="show"
            >
              {NAV.map((item) => (
                <motion.a
                  key={item.href}
                  href={item.href}
                  className="rounded-xl px-3 py-3 text-base font-semibold text-white no-underline hover:bg-white/5"
                  variants={reduced ? undefined : fadeUpSafe}
                  onClick={() => setOpen(false)}
                >
                  {item.label}
                </motion.a>
              ))}
              <CopyButton
                value={SITE.ip}
                className="btn btn-ghost mt-2 w-full"
                copiedLabel="IP kopiert"
                toast="IP kopiert — in Minecraft einfügen"
              >
                IP kopieren · {SITE.ip}
              </CopyButton>
            </motion.nav>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </>
  )
}

const fadeUpSafe = {
  hidden: { opacity: 0, x: -12 },
  show: { opacity: 1, x: 0, transition: { duration: 0.45, ease: easeOut } },
}
