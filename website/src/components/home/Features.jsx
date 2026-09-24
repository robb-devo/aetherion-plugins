import { FEATURES } from '../../content.js'
import { useLang } from '../../i18n.jsx'
import { ItemSlot, Reveal, Section, SectionHeading } from '../ui.jsx'

export function Pillars() {
  const { copy } = useLang()
  return (
    <div className="border-y hairline bg-obsidian/70">
      <div className="mx-auto grid max-w-6xl gap-px bg-white/5 sm:grid-cols-3">
        {copy.pillars.map((pillar) => (
          <div key={pillar.title} className="bg-obsidian px-6 py-7">
            <p className="text-sm font-bold text-white">{pillar.title}</p>
            <p className="mt-1.5 text-sm leading-relaxed text-mist/65">{pillar.body}</p>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function Features() {
  const { copy } = useLang()
  return (
    <Section id="features" className="!pb-12">
      <Reveal>
        <SectionHeading kicker={copy.features.kicker} title={copy.features.title} sub={copy.features.sub} />
      </Reveal>
      <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {FEATURES.map((feature, i) => {
          const item = copy.features.items[feature.id]
          return (
            <Reveal key={feature.id} delay={i * 50}>
              <article className="panel group h-full p-6 transition-colors duration-300 hover:border-white/12">
                <ItemSlot item={feature.item} size="h-16 w-16" className="transition-transform duration-300 group-hover:-translate-y-0.5" />
                <h3 className="mt-5 text-lg font-bold text-white">{item.title}</h3>
                <p className="mt-1 text-xs font-bold tracking-wide text-amethyst/90">{item.sub}</p>
                <p className="mt-3 text-sm leading-relaxed text-mist/70">{item.body}</p>
              </article>
            </Reveal>
          )
        })}
      </div>
    </Section>
  )
}
