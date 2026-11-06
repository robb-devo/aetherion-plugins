import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

/**
 * Minimal history router. The site has a handful of routes, so this avoids a dependency.
 * nginx falls back to index.html for unknown paths.
 *
 * Language lives in the path: /wiki is English, /de/wiki is German. Components only ever
 * see the path without the prefix.
 *
 * In a preview build (VITE_PREVIEW) the page sits at one fixed URL: the route goes into the
 * hash as a plain name (/wiki/faq becomes #wiki-faq).
 */
const RouterContext = createContext(null)
const PREVIEW = import.meta.env.VITE_PREVIEW === '1'

export function splitLang(pathname) {
  if (pathname === '/de' || pathname.startsWith('/de/')) return { lang: 'de', path: pathname.slice(3) || '/' }
  return { lang: 'en', path: pathname }
}

const hashToPath = (hash) => {
  const name = hash.replace(/^#/, '')
  if (!name) return '/'
  if (name.startsWith('r-')) return `/${name.slice(2).replace(/--/g, '/')}`
  return null
}
const pathToHash = (path) => (path === '/' ? '' : `#r-${path.slice(1).replace(/\//g, '--')}`)

function readLocation() {
  if (PREVIEW) {
    const p = hashToPath(window.location.hash)
    return { path: p ?? '/', search: '', hash: p == null ? window.location.hash : '', lang: null }
  }
  const { lang, path } = splitLang(window.location.pathname)
  return { path, lang, search: window.location.search, hash: window.location.hash }
}

function scrollToHash(hash) {
  if (!hash) {
    window.scrollTo({ top: 0, behavior: 'instant' })
    return
  }
  requestAnimationFrame(() => {
    document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

export function RouterProvider({ children, ssrPath = null, ssrLang = 'en' }) {
  const [location, setLocation] = useState(() => {
    if (ssrPath != null) return { path: ssrPath, lang: ssrLang, search: '', hash: '' }
    if (typeof window === 'undefined') return { path: '/', lang: 'en', search: '', hash: '' }
    return readLocation()
  })

  useEffect(() => {
    const onPop = () => setLocation(readLocation())
    window.addEventListener('popstate', onPop)
    if (PREVIEW) window.addEventListener('hashchange', onPop)
    return () => {
      window.removeEventListener('popstate', onPop)
      if (PREVIEW) window.removeEventListener('hashchange', onPop)
    }
  }, [])

  useEffect(() => {
    if (window.location.hash && !hashToPath(window.location.hash)) scrollToHash(window.location.hash)
  }, [])

  const navigate = useCallback((to, { replace = false, lang } = {}) => {
    const url = new URL(to, 'http://x')
    const current = readLocation()
    if (url.pathname === '/' && url.hash && current.path !== '/') {
      // jump to a chapter from another page
    }
    if (PREVIEW) {
      const samePage = url.pathname === current.path
      if (samePage && url.hash) {
        scrollToHash(url.hash)
        return
      }
      window.location.hash = pathToHash(url.pathname)
      setLocation({ path: url.pathname, search: '', hash: url.hash, lang: null })
      scrollToHash(url.hash)
      return
    }
    const nextLang = lang ?? current.lang
    const full = `${nextLang === 'de' ? `/de${url.pathname === '/' ? '/' : url.pathname}` : url.pathname}${url.search}${url.hash}`
    const samePage = url.pathname === current.path && url.search === current.search && nextLang === current.lang
    if (replace) window.history.replaceState(null, '', full)
    else window.history.pushState(null, '', full)
    setLocation({ path: url.pathname, search: url.search, hash: url.hash, lang: nextLang })
    if (lang && lang !== current.lang && url.pathname === current.path) return // same page, other language: stay put
    if (!samePage || url.hash) scrollToHash(url.hash)
  }, [])

  const value = useMemo(() => ({ ...location, navigate }), [location, navigate])
  return <RouterContext.Provider value={value}>{children}</RouterContext.Provider>
}

export function useRouter() {
  const ctx = useContext(RouterContext)
  if (!ctx) throw new Error('useRouter must be used inside RouterProvider')
  return ctx
}

/** `/servers/:id` → `{ id }` or null. */
export function matchPath(pattern, path) {
  const want = pattern.split('/').filter(Boolean)
  const have = path.split('/').filter(Boolean)
  if (want.length !== have.length) return null
  const params = {}
  for (let i = 0; i < want.length; i += 1) {
    if (want[i].startsWith(':')) params[want[i].slice(1)] = decodeURIComponent(have[i])
    else if (want[i] !== have[i]) return null
  }
  return params
}

export function Link({ to, onClick, children, ...props }) {
  const { navigate, lang } = useRouter()
  const external = /^https?:/.test(to)
  let href = to
  if (!external) {
    if (PREVIEW) {
      const u = new URL(to, 'http://x')
      href = u.hash && u.pathname === '/' ? u.hash : pathToHash(u.pathname) || '#'
    } else if (lang === 'de') {
      href = `/de${to === '/' ? '/' : to}`
    }
  }
  return (
    <a
      href={href}
      onClick={(event) => {
        onClick?.(event)
        if (external || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
        if (props.target === '_blank') return
        event.preventDefault()
        navigate(to)
      }}
      {...props}
    >
      {children}
    </a>
  )
}
