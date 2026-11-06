import { WALL } from '../../data/items.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import { interpolate } from '../../copy.js'

const DROPS = { 'The Ashen Sheath': 'ashen_katana', Pathwarden: 'gravwell_cleaver', 'Bridge Troll': 'bridged_axe', Brückentroll: 'bridged_axe', Skuldugery: 'skuldugery_shortbow', 'Hollow Lurker': 'warped_blade' }

export default function BossData() {
  const { copy, lang } = useLang()
  const bars = copy.home.gnomon.bars
  return (
    <ul className="wiki-bosses">
      {bars.map((boss) => {
        const drop = WALL.find((i) => i.id === DROPS[boss.name])
        return (
          <li key={boss.name} className="wiki-boss">
            <p className="bossbar-name">{boss.name}</p>
            <span className="bossbar-track" style={{ backgroundImage: `url(${asset(`ui/${boss.color}_progress.png`)})` }} />
            <p className="wiki-boss-line">{boss.line}</p>
            <ol className="wiki-boss-phases">
              {boss.phases.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ol>
            {drop ? (
              <p className="wiki-boss-drop">
                <img src={asset(`items/${drop.id}.png`)} alt="" className="pixelated" />
                <span>
                  {interpolate(copy.wiki.dropsLabel, { name: '' })}
                  <span className={`r-${drop.rarity}`}>{drop.name[lang]}</span>
                </span>
              </p>
            ) : null}
          </li>
        )
      })}
    </ul>
  )
}
