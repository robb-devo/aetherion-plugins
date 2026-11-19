import { useMemo, useState } from 'react'
import { PETS } from '../../data/pets.js'
import { useLang } from '../../i18n.jsx'

const ORDER = ['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic']

/** The Aetherlex: every catchable creature and its note. */
export default function PetData() {
  const { copy, lang } = useLang()
  const t = copy.wiki
  const [q, setQ] = useState('')
  const [rarity, setRarity] = useState('')
  const list = useMemo(() => {
    const s = q.trim().toLowerCase()
    return PETS.filter((p) => {
      if (s && !p.name[lang].toLowerCase().includes(s) && !p.note[lang].toLowerCase().includes(s)) return false
      if (rarity) {
        const i = ORDER.indexOf(rarity)
        if (i < ORDER.indexOf(p.low) || i > ORDER.indexOf(p.top)) return false
      }
      return true
    })
  }, [q, rarity, lang])
  return (
    <>
      <div className="wiki-filter">
        <input className="field" type="search" value={q} placeholder={t.petSearch} aria-label={t.petSearch} onChange={(e) => setQ(e.target.value)} />
        <select className="field" value={rarity} aria-label={t.petRarity} onChange={(e) => setRarity(e.target.value)}>
          <option value="">{t.petAny}</option>
          {ORDER.map((r) => (
            <option key={r} value={r}>
              {copy.rarity[r]}
            </option>
          ))}
        </select>
      </div>
      <ul className="wiki-pets">
        {list.map((p) => (
          <li key={p.id}>
            <p className="wiki-pet-name">{p.name[lang]}</p>
            <p className="wiki-pet-note">{p.note[lang]}</p>
            <p className="wiki-pet-range">
              <span className={`r-${p.low}`}>{copy.rarity[p.low]}</span>
              {p.low !== p.top ? (
                <>
                  {' '}
                  … <span className={`r-${p.top}`}>{copy.rarity[p.top]}</span>
                </>
              ) : null}
            </p>
          </li>
        ))}
      </ul>
    </>
  )
}
