import { useState } from 'react'
import { interpolate } from '../../copy.js'
import { WALL } from '../../data/items.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import { Link } from '../../router.jsx'
import Chapter, { Bank } from './Chapter.jsx'

const EMPTY = 18

export default function Loot() {
  const { copy, lang } = useLang()
  const t = copy.home.loot
  const [pick, setPick] = useState(WALL[0].id)
  const item = WALL.find((i) => i.id === pick)
  const slots = [...WALL, ...Array.from({ length: Math.max(0, EMPTY - WALL.length) }).map(() => null)]
  return (
    <Chapter id="loot" tone="dusk" className="ch-loot">
      <Bank title={t.title}>
        <p className="ch-body">{t.body}</p>
      </Bank>
      <div className="loot-module">
        <p className="loot-label">{t.label}</p>
        <div className="loot-body">
          <ul className="inv">
            {slots.map((it, i) => (
              <li key={it ? it.id : `e${i}`}>
                {it ? (
                  <button
                    type="button"
                    className={`inv-slot slot ${pick === it.id ? 'inv-on' : ''}`}
                    aria-label={it.name[lang]}
                    aria-pressed={pick === it.id}
                    onClick={() => setPick(it.id)}
                    onPointerEnter={(e) => {
                      if (e.pointerType === 'mouse') setPick(it.id)
                    }}
                  >
                    <img src={asset(`items/${it.id}.png`)} alt="" draggable="false" />
                  </button>
                ) : (
                  <span className="inv-slot slot" />
                )}
              </li>
            ))}
          </ul>
          <div className={`tip tip-${item.rarity} loot-tip`} aria-live="polite">
            <p className={`tip-name r-${item.rarity}`}>{item.name[lang]}</p>
            <p className="tip-note">{item.note[lang]}</p>
            {item.boss ? <p className="tip-from">{interpolate(t.drop, { who: item.boss })}</p> : null}
            <p className="tip-rarity">
              <span className={`r-${item.rarity}`}>{copy.rarity[item.rarity]}</span>
            </p>
          </div>
        </div>
        <Link to="/wiki/dungeons" className="btn btn-veil notch loot-link">
          {t.link}
        </Link>
      </div>
    </Chapter>
  )
}
