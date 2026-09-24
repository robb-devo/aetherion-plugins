import { useState } from 'react'
import { ARSENAL } from '../../content.js'
import { useLang } from '../../i18n.jsx'
import { ItemSlot, Reveal, Section, SectionHeading } from '../ui.jsx'

const RARITY_TEXT = {
  common: 'text-common',
  uncommon: 'text-uncommon',
  rare: 'text-rare',
  epic: 'text-epic',
  legendary: 'text-legendary',
  mythic: 'text-mythic',
}

const RARITY_RING = {
  common: 'ring-common/60',
  uncommon: 'ring-uncommon/60',
  rare: 'ring-rare/60',
  epic: 'ring-epic/60',
  legendary: 'ring-legendary/70',
  mythic: 'ring-mythic/70',
}

// Slots already use box-shadow for their bevel, so selection is drawn with an outline.
const RARITY_OUTLINE = {
  common: 'outline-common/70',
  uncommon: 'outline-uncommon/70',
  rare: 'outline-rare/70',
  epic: 'outline-epic/70',
  legendary: 'outline-legendary/80',
  mythic: 'outline-mythic/80',
}

/** Tooltip styled after the resource pack's rarity frames. */
function Tooltip({ item, copy }) {
  return (
    <div
      className={`pointer-events-none inline-block min-w-52 rounded-[3px] bg-[#100818]/95 px-3 py-2 ring-2 ${RARITY_RING[item.rarity]} shadow-[0_12px_30px_rgba(0,0,0,0.6)]`}
    >
      <p className={`text-sm font-bold ${RARITY_TEXT[item.rarity]}`}>{item.name}</p>
      <p className="mt-1 text-xs text-mist/70">{copy.arsenal.kind[item.kind]}</p>
      <p className={`mt-2 text-[0.68rem] font-extrabold tracking-[0.16em] uppercase ${RARITY_TEXT[item.rarity]}`}>
        {copy.arsenal.rarity[item.rarity]}
      </p>
    </div>
  )
}

export default function Arsenal() {
  const { copy } = useLang()
  const [active, setActive] = useState(ARSENAL[1].id)
  const current = ARSENAL.find((item) => item.id === active) ?? ARSENAL[0]

  return (
    <Section id="arsenal" className="!pt-4 !pb-6">
      <div className="panel grid gap-8 overflow-hidden p-6 sm:p-8 lg:grid-cols-[1fr_auto] lg:items-center">
        <Reveal>
          <SectionHeading kicker={copy.arsenal.kicker} title={copy.arsenal.title} sub={copy.arsenal.sub} />
          <div className="mt-6 hidden lg:block">
            <Tooltip item={current} copy={copy} />
          </div>
        </Reveal>
        <Reveal delay={80}>
          <div className="rounded-md bg-black/30 p-3" role="list">
            <div className="grid grid-cols-4 gap-2 sm:gap-2.5">
              {ARSENAL.map((item) => {
                const selected = item.id === active
                return (
                  <button
                    key={item.id}
                    type="button"
                    role="listitem"
                    aria-label={`${item.name} · ${copy.arsenal.rarity[item.rarity]}`}
                    aria-pressed={selected}
                    className="group relative cursor-pointer"
                    onMouseEnter={() => setActive(item.id)}
                    onFocus={() => setActive(item.id)}
                    onClick={() => setActive(item.id)}
                  >
                    <ItemSlot
                      item={item.id}
                      size="h-16 w-16 sm:h-20 sm:w-20"
                      className={`transition duration-200 ${
                        selected ? `outline-2 outline-offset-2 ${RARITY_OUTLINE[item.rarity]}` : 'group-hover:bg-white/[0.04]'
                      }`}
                    />
                  </button>
                )
              })}
            </div>
          </div>
          <div className="mt-4 lg:hidden">
            <Tooltip item={current} copy={copy} />
          </div>
        </Reveal>
      </div>
    </Section>
  )
}
