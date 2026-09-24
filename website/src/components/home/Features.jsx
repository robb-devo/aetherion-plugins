import { FEATURES } from '../../content.js'
import { useLang } from '../../i18n.jsx'
import { ItemSlot, Reveal, Section, SectionHeading } from '../ui.jsx'

export function Facts() {
  const { copy } = useLang()
  return (
    <div className="border-y hairline bg-obsidian/70">
      <div className="mx-auto grid max-w-6xl grid-cols-2 gap-px bg-white/5 lg:grid-cols-4">
        {copy.facts.map((fact) => (
          <div key={fact.label} className="bg-obsidian px-6 py-6">
            <p className="font-display text-3xl font-bold text-white">{fact.value}</p>
            <p className="mt-1 text-xs font-bold tracking-[0.14em] text-ash uppercase">{fact.label}</p>
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
