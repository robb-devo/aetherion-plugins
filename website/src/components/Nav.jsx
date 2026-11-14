import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth.jsx'
import { SITE } from '../content.js'
import { useLang } from '../i18n.jsx'
import { Link, useRouter } from '../router.jsx'
import { BrandImg } from './Brand.jsx'
import LangToggle from './LangToggle.jsx'
import { CopyButton, useToast } from './ui.jsx'

const LINKS = [
  { to: '/wiki', key: 'wiki' },
  { to: '/playground', key: 'playground' },
  { to: '/support', key: 'support' },
]

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
      <button type="button" className="btn btn-secondary btn-sm notch gap-2" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
        <span className="grid h-5 w-5 place-items-center rounded-sm bg-bell/20 text-xs font-extrabold text-bell uppercase">{user.username.slice(0, 1)}</span>
        <span className="max-w-28 truncate">{user.username}</span>
      </button>
      {open ? (
        <div role="menu" className="panel absolute right-0 mt-2 w-48 p-1.5">
          <Link to="/servers" role="menuitem" className="block rounded px-3 py-2 text-sm font-semibold text-white no-underline hover:bg-white/6" onClick={() => setOpen(false)}>
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
  const { copy } = useLang()
  const { status, user, logout } = useAuth()
  const { path, navigate } = useRouter()
  const isHome = path === '/'
  const isActive = (to) => path === to || path.startsWith(`${to}/`)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > window.innerHeight * 0.7)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    setOpen(false)
  }, [path])

  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : ''
    return () => {
      document.body.style.overflow = ''
    }
  }, [open])

  const solid = scrolled || !isHome || open
  return (
    <>
      <header className={`site-nav ${solid ? 'site-nav-solid' : ''} ${open ? 'site-nav-open' : ''}`}>
        <div className="site-nav-in">
          <Link to="/" className="site-nav-brand" aria-label="Aetherion">
            <BrandImg className="h-8 w-8" />
            <span className={`site-nav-word ${isHome && !solid ? 'site-nav-word-off' : ''}`} aria-hidden={isHome && !solid ? 'true' : undefined}>
              Aetherion
            </span>
          </Link>

          <nav className="hidden items-center gap-6 md:flex" aria-label={copy.nav.main}>
            {LINKS.map((item) => (
              <Link key={item.to} to={item.to} aria-current={isActive(item.to) ? 'page' : undefined} className="site-nav-link">
                {copy.nav[item.key]}
              </Link>
            ))}
          </nav>

          <div className="flex items-center gap-2">
            <LangToggle className="hidden sm:inline-flex" />
            {status === 'signed-in' && user ? (
              <div className="hidden md:block">
                <AccountMenu />
              </div>
            ) : status === 'loading' ? null : (
              <Link to="/login" className="site-nav-link hidden px-2 lg:inline">
                {copy.nav.signIn}
              </Link>
            )}
            <CopyButton value={SITE.ip} className="btn btn-primary btn-sm notch hidden font-bold sm:inline-flex" copiedLabel={copy.hero.copied} toast={copy.hero.ipCopied} title={copy.hero.copyTitle}>
              {SITE.ip}
            </CopyButton>
            <button
              type="button"
              className="notch inline-flex h-10 w-10 cursor-pointer items-center justify-center bg-[#10183a]/70 md:hidden"
              aria-expanded={open}
              aria-label={open ? copy.nav.menuClose : copy.nav.menuOpen}
              onClick={() => setOpen((value) => !value)}
            >
              <span className="flex flex-col gap-[5px]" aria-hidden="true">
                <span className={`block h-0.5 w-[18px] bg-white transition duration-200 ${open ? 'translate-y-[7px] rotate-45' : ''}`} />
                <span className={`block h-0.5 w-[18px] bg-white transition duration-200 ${open ? 'opacity-0' : ''}`} />
                <span className={`block h-0.5 w-[18px] bg-white transition duration-200 ${open ? '-translate-y-[7px] -rotate-45' : ''}`} />
              </span>
            </button>
          </div>
        </div>
      </header>

      {open ? (
        <div className="scrim-in fixed inset-0 z-40 bg-void pt-16 md:hidden">
          <nav className="mx-auto flex h-full max-w-6xl flex-col gap-1 overflow-y-auto px-4 py-5" aria-label={copy.nav.mobile}>
            {LINKS.map((item) => (
              <Link key={item.to} to={item.to} aria-current={isActive(item.to) ? 'page' : undefined} className="font-display block px-3 py-3 text-2xl font-bold text-white no-underline aria-[current=page]:text-bell" onClick={() => setOpen(false)}>
                {copy.nav[item.key]}
              </Link>
            ))}
            <div className="my-3 border-t hairline" />
            <CopyButton value={SITE.ip} className="btn btn-primary notch w-full justify-between" copiedLabel={copy.hero.copied} toast={copy.hero.ipCopied}>
              <span>{SITE.ip}</span>
              <span>{copy.nav.copyIp}</span>
            </CopyButton>
            <div className="mt-2">
              {status === 'signed-in' && user ? (
                <div className="flex flex-col gap-2">
                  <Link to="/servers" className="btn btn-secondary notch w-full" onClick={() => setOpen(false)}>
                    {copy.nav.myServers}
                  </Link>
                  <button
                    type="button"
                    className="btn btn-ghost w-full"
                    onClick={async () => {
                      setOpen(false)
                      await logout()
                      navigate('/')
                    }}
                  >
                    {copy.nav.signOut} ({user.username})
                  </button>
                </div>
              ) : (
                <Link to="/login" className="btn btn-secondary notch w-full" onClick={() => setOpen(false)}>
                  {copy.nav.signIn}
                </Link>
              )}
            </div>
            <LangToggle className="mt-4 self-start" />
          </nav>
        </div>
      ) : null}
    </>
  )
}
