import { AuthProvider } from './auth.jsx'
import { RequireAuth } from './components/app/AppShell.jsx'
import Footer from './components/Footer.jsx'
import Nav from './components/Nav.jsx'
import { ToastProvider } from './components/ui.jsx'
import { LanguageProvider } from './i18n.jsx'
import AuthPage from './pages/AuthPage.jsx'
import CreateServer from './pages/CreateServer.jsx'
import Home from './pages/Home.jsx'
import NotFound from './pages/NotFound.jsx'
import ServerDashboard from './pages/ServerDashboard.jsx'
import Servers from './pages/Servers.jsx'
import { matchPath, RouterProvider, useRouter } from './router.jsx'

function Routes() {
  const { path } = useRouter()
  const clean = path.length > 1 ? path.replace(/\/+$/, '') : path

  if (clean === '/') return <Home />
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

export default function App() {
  return (
    <LanguageProvider>
      <RouterProvider>
        <AuthProvider>
          <ToastProvider>
            <div className="min-h-screen bg-void text-white">
              <Nav />
              <Routes />
              <Footer />
            </div>
          </ToastProvider>
        </AuthProvider>
      </RouterProvider>
    </LanguageProvider>
  )
}
