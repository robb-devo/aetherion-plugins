import { motion, useReducedMotion } from 'framer-motion'
import { MAPS } from '../content.js'
import { useLang } from '../i18n.jsx'
import { FadeLang } from './LangToggle.jsx'
import { Kicker, MotionCard, Reveal, Section, SectionTitle } from './ui.jsx'

export default function MapGallery() {
  const reduced = useReducedMotion()
  const { copy } = useLang()

  return (
    <Section id="karten">
      <Reveal>
        <FadeLang>
          <Kicker>{copy.maps.kicker}</Kicker>
          <SectionTitle sub={copy.maps.sub}>{copy.maps.title}</SectionTitle>
          <p className="mt-4 max-w-2xl text-sm leading-relaxed text-mist/70">{copy.maps.intro}</p>
        </FadeLang>
      </Reveal>

      <div className="mt-10 grid gap-5 md:grid-cols-3">
        {MAPS.map((map, i) => {
          const item = copy.maps.items[map.id]
          return (
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
                      <FadeLang as="span">{copy.maps.placeholder}</FadeLang>
                    </span>
                  ) : null}
                </div>
                <figcaption className="p-5">
                  <h3 className="font-display text-xl font-bold tracking-wide text-white">{map.title}</h3>
                  <FadeLang>
                    <p className="mt-1 text-xs font-semibold tracking-wide text-cyan/80 uppercase">{item.sub}</p>
                    <p className="mt-2 text-sm leading-relaxed text-mist/75">{item.caption}</p>
                  </FadeLang>
                </figcaption>
              </MotionCard>
            </Reveal>
          )
        })}
      </div>
    </Section>
  )
}
