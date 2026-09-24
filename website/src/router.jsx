import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

/**
 * Minimal history router. The site has a handful of routes, so this avoids a
 * dependency. nginx already falls back to index.html for unknown paths.
 */
const RouterContext = createContext(null)

function scrollToHash(hash) {
  if (!hash) {
    window.scrollTo({ top: 0, behavior: 'instant' })
    return
  }
  requestAnimationFrame(() => {
    document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

export function RouterProvider({ children }) {
  const [location, setLocation] = useState(() => ({
    path: window.location.pathname,
    search: window.location.search,
  }))

  useEffect(() => {
    const onPop = () => setLocation({ path: window.location.pathname, search: window.location.search })
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  useEffect(() => {
    if (window.location.hash) scrollToHash(window.location.hash)
  }, [])

  const navigate = useCallback((to, { replace = false } = {}) => {
    const url = new URL(to, window.location.origin)
    const samePage = url.pathname === window.location.pathname && url.search === window.location.search
    if (replace) window.history.replaceState(null, '', url)
    else window.history.pushState(null, '', url)
    setLocation({ path: url.pathname, search: url.search })
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
  const { navigate } = useRouter()
  return (
    <a
      href={to}
      onClick={(event) => {
        onClick?.(event)
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
          return
        }
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
