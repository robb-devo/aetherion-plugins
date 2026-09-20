import { MAPS } from '../content.js'
import { Kicker, Reveal, Section, SectionTitle } from './ui.jsx'

export default function MapGallery() {
  return (
    <Section id="karten">
      <Reveal>
        <Kicker>Karten</Kicker>
        <SectionTitle en="Placeholder frames — drop real screenshots into public/maps/ later.">
          Drei Orte, die du kennen wirst
        </SectionTitle>
        <p className="mt-4 max-w-2xl text-sm leading-relaxed text-mist/70">
          Die Galerie ist vorbereitet. Aktuell liegen stilisierte Platzhalter in{' '}
          <code className="rounded bg-white/10 px-1.5 py-0.5 text-cyan">website/public/maps/</code> —
          gleiche Dateinamen, echte Screenshots, fertig.
        </p>
      </Reveal>

      <div className="mt-10 grid gap-5 md:grid-cols-3">
        {MAPS.map((map, i) => (
          <Reveal key={map.id} delay={i * 90}>
            <figure className="glass glass-hover overflow-hidden rounded-2xl">
              <div className="relative aspect-[16/10] overflow-hidden bg-ink">
                <img
                  src={map.image}
                  alt={map.title}
                  className="h-full w-full object-cover"
                  width={1200}
                  height={750}
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
            </figure>
          </Reveal>
        ))}
      </div>
    </Section>
  )
}
