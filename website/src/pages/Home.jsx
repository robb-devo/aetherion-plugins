import { useEffect } from 'react'
import { About, Dungeons, Exile, Isle, Jobs, Pets, World } from '../components/home/Beats.jsx'
import Hero from '../components/home/Hero.jsx'
import Join, { JoinDock } from '../components/home/Join.jsx'
import { useLang } from '../i18n.jsx'

/**
 * One day over Aetherion in a single camera take. Each section is one frame of that take with one
 * line and a few sentences on it; world/route.js pins the camera and the clock to them. Anything
 * deeper belongs in the wiki, not here.
 */
export default function Home() {
  const { copy } = useLang()
  useEffect(() => {
    document.title = copy.meta.title
  }, [copy.meta.title])

  return (
    <main className="home">
      <Hero />
      <About />
      <Jobs />
      <World />
      <Isle />
      <Pets />
      <Dungeons />
      <Exile />
      <Join />
      <JoinDock />
    </main>
  )
}
