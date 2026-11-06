import { useState } from 'react'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'

/** The game's real boss bars. Tap one to push the fight into its next phase. */
function Bar({ boss }) {
  const [phase, setPhase] = useState(0)
  const n = boss.phases.length
  const left = 1 - phase / n
  return (
    <li className="bossbar">
      <button type="button" onClick={() => setPhase((p) => (p + 1) % n)} aria-label={`${boss.name}: ${boss.phases[phase]}`}>
        <span className="bossbar-name">{boss.name}</span>
        <span className="bossbar-track" style={{ backgroundImage: `url(${asset(`ui/${boss.color}_background.png`)})` }}>
          <span className="bossbar-fill" style={{ width: `${left * 100}%`, backgroundImage: `url(${asset(`ui/${boss.color}_progress.png`)})` }} />
        </span>
        <span className="bossbar-phase">{boss.phases[phase]}</span>
        <span className="bossbar-line">{boss.line}</span>
      </button>
    </li>
  )
}

export default function Bosses() {
  const { copy } = useLang()
  const t = copy.home.gnomon
  return (
    <section data-chapter="bosses" data-mood="dark" className="ch ch-bosses">
      <div className="bosses">
        <h3 className="ch-title bosses-title">{t.others}</h3>
        <ul className="bossbars">
          {t.bars.map((boss) => (
            <Bar key={boss.name} boss={boss} />
          ))}
        </ul>
      </div>
    </section>
  )
}
