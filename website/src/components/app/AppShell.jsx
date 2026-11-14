import { useEffect } from 'react'
import { useAuth } from '../../auth.jsx'
import { useLang } from '../../i18n.jsx'
import { Link, useRouter } from '../../router.jsx'
import { Skeleton } from '../ui.jsx'

/** Platform page frame: a faint slice of the world behind the panels. */
export function AppShell({ children, offline = false, narrow = false }) {
  const { copy } = useLang()
  // Inside the auth page's split layout the shell is a column, not the page.
  const Tag = narrow ? 'div' : 'main'
  return (
    <Tag className="relative isolate min-h-screen px-4 pt-24 pb-20 sm:px-6">
      {offline ? (
        <div className="fixed inset-x-0 top-16 z-40 flex justify-center px-4">
          <p className="notch mt-2 bg-gold px-3 py-1.5 text-xs font-extrabold text-black" role="status">
            {copy.ui.offline}
          </p>
        </div>
      ) : null}
      <div className={`mx-auto w-full ${narrow ? 'max-w-md' : 'max-w-6xl'}`}>{children}</div>
    </Tag>
  )
}

/** Redirects to the sign-in page, then back here after signing in. */
export function RequireAuth({ children }) {
  const { status } = useAuth()
  const { path, search, navigate } = useRouter()

  useEffect(() => {
    if (status === 'signed-out') {
      navigate(`/login?next=${encodeURIComponent(path + search)}`, { replace: true })
    }
  }, [status, path, search, navigate])

  if (status === 'signed-in') return children
  return (
    <AppShell>
      <Skeleton className="h-10 w-56" />
      <Skeleton className="mt-4 h-5 w-80" />
      <div className="mt-10 grid gap-4 md:grid-cols-2">
        <Skeleton className="h-44" />
        <Skeleton className="h-44" />
      </div>
    </AppShell>
  )
}

export function BackLink({ to, children }) {
  return (
    <Link to={to} className="mb-3 inline-flex items-center gap-1.5 text-sm font-semibold text-ash no-underline hover:text-white">
      <span aria-hidden="true">←</span> {children}
    </Link>
  )
}

export function PageHeader({ back, title, sub, actions }) {
  return (
    <div className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {back}
        <h1 className="font-display text-3xl font-bold tracking-wide text-white sm:text-4xl">{title}</h1>
        {sub ? <p className="mt-2 text-sm text-mist/70 sm:text-base">{sub}</p> : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap gap-2">{actions}</div> : null}
    </div>
  )
}
