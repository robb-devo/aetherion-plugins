import { WORLD } from '../../content.js'
import { useLang } from '../../i18n.jsx'
import { ItemSlot, Reveal, Section, SectionHeading } from '../ui.jsx'

function Place({ place, large = false }) {
  const { copy } = useLang()
  const text = copy.world.items[place.id]
  return (
    <figure className={`panel group relative isolate h-full overflow-hidden ${large ? 'min-h-[26rem]' : 'min-h-[13rem]'}`}>
      {place.image ? (
        <>
          <img
            src={place.image}
            alt={text.title}
            loading="lazy"
            className="absolute inset-0 -z-20 h-full w-full object-cover transition-transform duration-[1.4s] ease-out group-hover:scale-[1.03]"
          />
          <div className="absolute inset-0 -z-10 bg-gradient-to-t from-void via-void/40 to-transparent" aria-hidden="true" />
        </>
      ) : (
        <div className="absolute inset-0 -z-10 bg-[radial-gradient(circle_at_85%_15%,rgba(167,139,250,0.10),transparent_55%)]">
          <div className="absolute top-5 left-5 flex items-center gap-3 sm:top-6 sm:left-6">
            <ItemSlot item={place.item} size="h-12 w-12" className="opacity-85" />
            <span className="text-[0.66rem] font-extrabold tracking-[0.18em] text-ash uppercase">{copy.world.soon}</span>
          </div>
        </div>
      )}
      <figcaption className="absolute inset-x-0 bottom-0 p-5 sm:p-6">
        <p className="text-[0.68rem] font-extrabold tracking-[0.18em] text-amethyst uppercase">{text.sub}</p>
        <h3 className={`font-display mt-1 font-bold tracking-wide text-white ${large ? 'text-3xl' : 'text-xl'}`}>{text.title}</h3>
        <p className={`mt-2 max-w-md text-sm leading-relaxed text-mist/75 ${large ? '' : 'line-clamp-2'}`}>{text.caption}</p>
      </figcaption>
    </figure>
  )
}

export default function World() {
  const { copy } = useLang()
  const [first, ...rest] = WORLD
  return (
    <Section id="world">
      <Reveal>
        <SectionHeading kicker={copy.world.kicker} title={copy.world.title} sub={copy.world.sub} />
      </Reveal>
      <div className="mt-12 grid gap-4 lg:grid-cols-[1.6fr_1fr]">
        <Reveal className="lg:row-span-3">
          <Place place={first} large />
        </Reveal>
        {rest.map((place, i) => (
          <Reveal key={place.id} delay={80 + i * 60}>
            <Place place={place} />
          </Reveal>
        ))}
      </div>
    </Section>
  )
}
