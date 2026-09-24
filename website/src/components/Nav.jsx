import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth.jsx'
import { NAV_LINKS, SITE } from '../content.js'
import { useLang } from '../i18n.jsx'
import { easeOut } from '../motion.js'
import { Link, useRouter } from '../router.jsx'
import LangToggle from './LangToggle.jsx'
import { CopyButton, useToast } from './ui.jsx'

function Wordmark({ onClick }) {
  return (
    <Link to="/" className="flex items-center gap-2.5 no-underline" onClick={onClick}>
      <img src="/favicon.svg" alt="" className="h-8 w-8" />
      <span className="font-display text-sm font-bold tracking-[0.24em] text-white">AETHERION</span>
    </Link>
  )
}

function AccountMenu() {
  const { user, logout } = useAuth()
  const { copy } = useLang()
  const { navigate } = useRouter()
  const showToast = useToast()
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const onDown = (event) => {
      if (!ref.current?.contains(event.target)) setOpen(false)
    }
    const onKey = (event) => event.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        className="btn btn-secondary btn-sm notch gap-2"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <span className="grid h-5 w-5 place-items-center rounded-sm bg-amethyst/25 text-[0.65rem] font-black text-amethyst uppercase">
          {user.username.slice(0, 1)}
        </span>
        <span className="max-w-28 truncate">{user.username}</span>
      </button>
      {open ? (
        <div role="menu" className="panel absolute right-0 mt-2 w-48 p-1.5">
          <Link
            to="/servers"
            role="menuitem"
            className="block rounded px-3 py-2 text-sm font-semibold text-white no-underline hover:bg-white/6"
            onClick={() => setOpen(false)}
          >
            {copy.nav.myServers}
          </Link>
          <button
            type="button"
            role="menuitem"
            className="block w-full cursor-pointer rounded px-3 py-2 text-left text-sm font-semibold text-mist/80 hover:bg-white/6 hover:text-white"
            onClick={async () => {
              setOpen(false)
              await logout()
              showToast(copy.app.auth.signedOut)
              navigate('/')
            }}
          >
            {copy.nav.signOut}
          </button>
        </div>
      ) : null}
    </div>
  )
}

export default function Nav() {
  const [open, setOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)
  const reduced = useReducedMotion()
  const { copy } = useLang()
  const { status, user } = useAuth()
  const { path } = useRouter()
  const onHome = path === '/'

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

  const solid = open || scrolled || !onHome

  return (
    <>
      <header
        className={`fixed inset-x-0 top-0 z-50 border-b transition-colors duration-300 ${
          solid ? 'border-white/6 bg-void/88 backdrop-blur-xl' : 'border-transparent bg-transparent'
        }`}
      >
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
          <Wordmark onClick={() => setOpen(false)} />

          <nav className="hidden items-center gap-7 lg:flex" aria-label={copy.nav.main}>
            {NAV_LINKS.map((item) => (
              <Link
                key={item.href}
                to={item.href}
                className="text-sm font-semibold text-mist/75 no-underline transition-colors hover:text-white"
              >
                {copy.nav[item.key]}
              </Link>
            ))}
          </nav>

          <div className="flex items-center gap-2">
            <LangToggle className="hidden sm:inline-flex" />
            <CopyButton
              value={SITE.ip}
              className="btn btn-ghost btn-sm notch hidden font-mono md:inline-flex"
              copiedLabel={copy.hero.copied}
              toast={copy.hero.ipCopied}
            >
              {SITE.ip}
            </CopyButton>
            {status === 'signed-in' && user ? (
              <div className="hidden md:block">
                <AccountMenu />
              </div>
            ) : status === 'loading' ? (
              <span className="hidden h-9 w-24 md:block" />
            ) : (
              <Link to="/login" className="btn btn-primary btn-sm notch hidden md:inline-flex">
                {copy.nav.signIn}
              </Link>
            )}
            <button
              type="button"
              className="notch inline-flex h-10 w-10 cursor-pointer items-center justify-center bg-white/6 md:hidden"
              aria-expanded={open}
              aria-label={open ? copy.nav.menuClose : copy.nav.menuOpen}
              onClick={() => setOpen((value) => !value)}
            >
              <span className="flex flex-col gap-1.5" aria-hidden="true">
                <span className={`block h-0.5 w-4 bg-white transition duration-300 ${open ? 'translate-y-2 rotate-45' : ''}`} />
                <span className={`block h-0.5 w-4 bg-white transition duration-300 ${open ? 'opacity-0' : ''}`} />
                <span className={`block h-0.5 w-4 bg-white transition duration-300 ${open ? '-translate-y-2 -rotate-45' : ''}`} />
              </span>
            </button>
          </div>
        </div>
      </header>

      <AnimatePresence>
        {open ? (
          <motion.div
            className="fixed inset-0 z-40 bg-void pt-16 md:hidden"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: reduced ? 0 : 0.22, ease: easeOut }}
          >
            <nav className="mx-auto flex h-full max-w-6xl flex-col gap-1 overflow-y-auto px-4 py-5" aria-label={copy.nav.mobile}>
              {NAV_LINKS.map((item) => (
                <Link
                  key={item.href}
                  to={item.href}
                  className="rounded-md px-3 py-3 text-base font-semibold text-white no-underline hover:bg-white/5"
                  onClick={() => setOpen(false)}
                >
                  {copy.nav[item.key]}
                </Link>
              ))}
              <div className="my-3 border-t hairline" />
              {status === 'signed-in' && user ? (
                <MobileAccount onDone={() => setOpen(false)} />
              ) : (
                <Link to="/login" className="btn btn-primary notch w-full" onClick={() => setOpen(false)}>
                  {copy.nav.signIn}
                </Link>
              )}
              <CopyButton
                value={SITE.ip}
                className="btn btn-secondary notch mt-2 w-full"
                copiedLabel={copy.hero.copied}
                toast={copy.hero.ipCopied}
              >
                {copy.nav.copyIp} · {SITE.ip}
              </CopyButton>
              <LangToggle className="mt-4 self-start" />
            </nav>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </>
  )
}

function MobileAccount({ onDone }) {
  const { user, logout } = useAuth()
  const { copy } = useLang()
  const { navigate } = useRouter()
  return (
    <div className="flex flex-col gap-2">
      <Link to="/servers" className="btn btn-primary notch w-full" onClick={onDone}>
        {copy.nav.myServers}
      </Link>
      <button
        type="button"
        className="btn btn-ghost w-full"
        onClick={async () => {
          onDone()
          await logout()
          navigate('/')
        }}
      >
        {copy.nav.signOut} · {user.username}
      </button>
    </div>
  )
}
