import { useEffect } from 'react'
import Bosses from '../components/home/Bosses.jsx'
import Chart from '../components/home/Chart.jsx'
import Gnomon from '../components/home/Gnomon.jsx'
import Hero from '../components/home/Hero.jsx'
import Isles from '../components/home/Isles.jsx'
import Join, { JoinDock } from '../components/home/Join.jsx'
import Loot from '../components/home/Loot.jsx'
import Pets from '../components/home/Pets.jsx'
import Prison from '../components/home/Prison.jsx'
import Night from '../components/home/Night.jsx'
import Skills from '../components/home/Skills.jsx'
import Talk from '../components/home/Talk.jsx'
import What from '../components/home/What.jsx'
import { useLang } from '../i18n.jsx'

/**
 * One day over Aetherion, from first light to the next. Each chapter owns a stretch of the scroll;
 * world/route.js pins the camera and the clock to them. Gaps between chapters are on purpose: the
 * camera needs room to fly, and that flight is the point.
 */
export default function Home() {
  const { copy } = useLang()
  useEffect(() => {
    document.title = copy.meta.title
  }, [copy.meta.title])

  return (
    <main className="home">
      <Hero />
      <What />
      <Talk />
      <Skills />
      <Chart />
      <Isles />
      <Pets />
      <Loot />
      <section data-chapter="dive" className="ch-flight" aria-hidden="true" />
      <Prison />
      <Gnomon />
      <Bosses />
      <section data-chapter="rise" className="ch-flight" aria-hidden="true" />
      <Night />
      <Join />
      <JoinDock />
    </main>
  )
}
