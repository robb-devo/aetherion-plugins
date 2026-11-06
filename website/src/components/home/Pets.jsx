import { useCallback, useRef, useState } from 'react'
import { interpolate } from '../../copy.js'
import { PETS } from '../../data/pets.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import Chapter, { Bank } from './Chapter.jsx'

const ORDER = ['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic']
const WEIGHT = { common: 10, uncommon: 7, rare: 4, epic: 2, legendary: 1.5, mythic: 0.8 }
const SPHERE = { common: 'catch_sphere_common', uncommon: 'catch_sphere_common', rare: 'catch_sphere_rare', epic: 'catch_sphere_epic', legendary: 'catch_sphere_legendary', mythic: 'catch_sphere_legendary' }

function roll() {
  const total = PETS.reduce((a, p) => a + WEIGHT[p.low], 0)
  let r = Math.random() * total
  let pet = PETS[0]
  for (const p of PETS) {
    r -= WEIGHT[p.low]
    if (r <= 0) {
      pet = p
      break
    }
  }
  let i = ORDER.indexOf(pet.low)
  const top = ORDER.indexOf(pet.top)
  while (i < top && Math.random() < 0.18) i += 1
  return { pet, rarity: ORDER[i], shiny: Math.random() < 1 / 4096 }
}

/** A real catch from the real list. The first one is always the potatoes. It sets the tone. */
export default function Pets() {
  const { copy, lang } = useLang()
  const t = copy.home.pets
  const [phase, setPhase] = useState('idle')
  const [catchNow, setCatch] = useState(null)
  const [count, setCount] = useState({ n: 0, s: 0 })
  const [shelf, setShelf] = useState([])
  const first = useRef(true)

  const toss = useCallback(() => {
    if (phase === 'toss') return
    const next = first.current
      ? { pet: PETS.find((p) => p.id === 'sack_of_potatoes'), rarity: 'common', shiny: false }
      : roll()
    first.current = false
    setCatch(null)
    setPhase('toss')
    window.setTimeout(() => {
      setCatch(next)
      setPhase('done')
      setCount((c) => ({ n: c.n + 1, s: c.s + (next.shiny ? 1 : 0) }))
      setShelf((list) => [next, ...list.filter((x) => x.pet.id !== next.pet.id)].slice(0, 8))
    }, 1250)
  }, [phase])

  const sphere = catchNow ? SPHERE[catchNow.rarity] : 'catch_sphere'
  return (
    <Chapter id="pets" tone="dusk" className="ch-pets">
      <Bank title={t.title} side="right">
        <p className="ch-body">{t.body}</p>
      </Bank>
      <div className="pet-module">
        <div className="pet-toy">
          <div className={`pet-sphere ${phase === 'toss' ? 'pet-sphere-toss' : ''} ${phase === 'done' ? 'pet-sphere-done' : ''}`} aria-hidden="true">
            <img src={asset(`items/${sphere}.png`)} alt="" className="pixelated" />
          </div>
          <div className="pet-result" aria-live="polite">
            {phase === 'toss' ? <p className="pet-tossing">{t.tossing}</p> : null}
            {catchNow ? (
              <div className={`tip tip-${catchNow.rarity} pet-card`} key={`${catchNow.pet.id}${count.n}`}>
                <p className={`tip-name r-${catchNow.rarity}`}>
                  {catchNow.shiny ? <span className="pet-shiny">{t.shiny}</span> : null}
                  {catchNow.pet.name[lang]}
                </p>
                <p className="tip-note">{catchNow.pet.note[lang]}</p>
                <p className={`tip-rarity r-${catchNow.rarity}`}>{copy.rarity[catchNow.rarity]}</p>
              </div>
            ) : phase === 'idle' ? (
              <p className="pet-hint">{t.chance}</p>
            ) : null}
          </div>
          <button type="button" className="btn btn-primary btn-lg notch pet-throw" onClick={toss} disabled={phase === 'toss'}>
            {count.n ? t.again : t.throw}
          </button>
          {count.n ? <p className="pet-count font-pixel">{interpolate(t.count, count)}</p> : null}
        </div>
        {shelf.length > 1 ? (
          <ul className="pet-shelf">
            {shelf.slice(1).map((c) => (
              <li key={c.pet.id} className={`r-${c.rarity}`}>
                {c.pet.name[lang]}
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    </Chapter>
  )
}
