import { motion, useReducedMotion } from 'framer-motion'
import { MAPS } from '../content.js'
import { Kicker, MotionCard, Reveal, Section, SectionTitle } from './ui.jsx'

export default function MapGallery() {
  const reduced = useReducedMotion()

  return (
    <Section id="karten">
      <Reveal>
        <Kicker>Karten</Kicker>
        <SectionTitle en="Eldervale · Crystal Hollows · Harbour">
          Drei Orte, die du kennen wirst
        </SectionTitle>
        <p className="mt-4 max-w-2xl text-sm leading-relaxed text-mist/70">
          Drei Orte aus der Welt. Die Rahmen stehen — echte Ingame-Screenshots kommen nach.
        </p>
      </Reveal>

      <div className="mt-10 grid gap-5 md:grid-cols-3">
        {MAPS.map((map, i) => (
          <Reveal key={map.id} delay={i * 90}>
            <MotionCard as="figure" className="overflow-hidden rounded-2xl">
              <div className="relative aspect-[16/10] overflow-hidden bg-ink">
                <motion.img
                  src={map.image}
                  alt={map.title}
                  className="h-full w-full object-cover"
                  width={1200}
                  height={750}
                  whileHover={reduced ? undefined : { scale: 1.06 }}
                  transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
                />
                {map.placeholder ? (
                  <span className="absolute top-3 left-3 rounded-full border border-white/15 bg-void/70 px-2.5 py-1 text-[0.65rem] font-bold tracking-wide text-white/90 uppercase backdrop-blur">
                    Platzhalter
                  </span>
                ) : null}
              </div>
              <figcaption className="p-5">
                <h3 className="font-display text-xl font-bold tracking-wide text-white">{map.title}</h3>
                <p className="mt-1 text-xs font-semibold tracking-wide text-cyan/80 uppercase">{map.en}</p>
                <p className="mt-2 text-sm leading-relaxed text-mist/75">{map.caption}</p>
              </figcaption>
            </MotionCard>
          </Reveal>
        ))}
      </div>
    </Section>
  )
}
