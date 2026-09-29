import Features from './components/Features.jsx'
import Footer from './components/Footer.jsx'
import Hero from './components/Hero.jsx'
import MapGallery from './components/MapGallery.jsx'
import Nav from './components/Nav.jsx'
import Support from './components/Support.jsx'
import Vision from './components/Vision.jsx'
import { ToastProvider } from './components/ui.jsx'
import { LanguageProvider } from './i18n.jsx'

export default function App() {
  return (
    <LanguageProvider>
      <ToastProvider>
        <div className="min-h-screen bg-void text-white">
          <Nav />
          <main>
            <Hero />
            <Vision />
            <Features />
            <MapGallery />
            <Support />
          </main>
          <Footer />
        </div>
      </ToastProvider>
    </LanguageProvider>
  )
}
