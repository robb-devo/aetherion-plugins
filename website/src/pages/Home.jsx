import { useEffect } from 'react'
import Features, { Facts } from '../components/home/Features.jsx'
import Finale from '../components/home/Finale.jsx'
import Hero from '../components/home/Hero.jsx'
import Playground from '../components/home/Playground.jsx'
import Support from '../components/home/Support.jsx'
import World from '../components/home/World.jsx'
import { useLang } from '../i18n.jsx'

export default function Home() {
  const { copy } = useLang()
  useEffect(() => {
    document.title = copy.meta.title
  }, [copy.meta.title])

  return (
    <main>
      <Hero />
      <Facts />
      <Features />
      <World />
      <Playground />
      <Support />
      <Finale />
    </main>
  )
}
