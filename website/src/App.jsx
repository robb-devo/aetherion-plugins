import { lazy, Suspense } from 'react'
import { AuthProvider } from './auth.jsx'
import Footer from './components/Footer.jsx'
import Nav from './components/Nav.jsx'
import { ToastProvider } from './components/ui.jsx'
import { WIKI_SECTIONS } from './content.js'
import { LanguageProvider } from './i18n.jsx'
import Home from './pages/Home.jsx'
import { matchPath, RouterProvider, useRouter } from './router.jsx'
import WorldStage from './world/WorldStage.jsx'

// The account pages are a separate application for a smaller audience: split off, so a first-time
// visitor's download is the world and not a server dashboard.
const Wiki = lazy(() => import('./pages/Wiki.jsx'))
const WikiPage = lazy(() => import('./pages/WikiPage.jsx'))
const Playground = lazy(() => import('./pages/Playground.jsx'))
const Support = lazy(() => import('./pages/Support.jsx'))
const NotFound = lazy(() => import('./pages/NotFound.jsx'))
const AuthPage = lazy(() => import('./pages/AuthPage.jsx'))
const CreateServer = lazy(() => import('./pages/CreateServer.jsx'))
const ServerDashboard = lazy(() => import('./pages/ServerDashboard.jsx'))
const Servers = lazy(() => import('./pages/Servers.jsx'))
const RequireAuth = lazy(() => import('./components/app/AppShell.jsx').then((m) => ({ default: m.RequireAuth })))

function Page({ clean }) {
  if (clean === '/') return <Home />
  if (clean === '/wiki') return <Wiki />
  const wiki = matchPath('/wiki/:slug', clean)
  if (wiki && WIKI_SECTIONS.some((section) => section.id === wiki.slug)) return <WikiPage key={wiki.slug} slug={wiki.slug} />
  if (clean === '/playground') return <Playground />
  if (clean === '/support') return <Support />
  if (clean === '/login') return <AuthPage mode="login" />
  if (clean === '/register') return <AuthPage mode="register" />
  if (clean === '/servers') {
    return (
      <RequireAuth>
        <Servers />
      </RequireAuth>
    )
  }
  if (clean === '/servers/new') {
    return (
      <RequireAuth>
        <CreateServer />
      </RequireAuth>
    )
  }
  const server = matchPath('/servers/:id', clean)
  if (server) {
    return (
      <RequireAuth>
        <ServerDashboard key={server.id} id={server.id} />
      </RequireAuth>
    )
  }
  return <NotFound />
}

function Routes() {
  const { path } = useRouter()
  const clean = path.length > 1 ? path.replace(/\/+$/, '') : path
  return (
    <div key={clean} className={clean === '/' ? '' : 'page-in'}>
      <Suspense fallback={<div className="inner" />}>
        <Page clean={clean} />
      </Suspense>
    </div>
  )
}

export default function App({ ssrPath = null, ssrLang = 'en' }) {
  return (
    <RouterProvider ssrPath={ssrPath} ssrLang={ssrLang}>
      <LanguageProvider>
        <AuthProvider>
          <ToastProvider>
            <WorldStage />
            <Nav />
            <Routes />
            <Footer />
          </ToastProvider>
        </AuthProvider>
      </LanguageProvider>
    </RouterProvider>
  )
}
