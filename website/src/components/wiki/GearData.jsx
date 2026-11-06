import { WALL } from '../../data/items.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import { interpolate } from '../../copy.js'

const LADDERS = [
  { id: 'forage', icons: ['foraging_axe', 'foraging_axe_2', 'foraging_axe_3', 'foraging_axe_4', 'foraging_axe_5'] },
  { id: 'farm', icons: ['farming_hoe', 'farming_hoe_2', 'farming_hoe_3', 'farming_hoe_4', 'farming_hoe_5'] },
  { id: 'fish', icons: ['fishing_rod', 'fishing_rod_2', 'fishing_rod_3', 'fishing_rod_4', 'fishing_rod_5'] },
  { id: 'mine', icons: ['mining_pickaxe', 'mining_pickaxe_2', 'mining_pickaxe_3', 'mining_pickaxe_4', 'mining_pickaxe_5'] },
  { id: 'combat', icons: ['combat_sword', 'combat_sword_2', 'combat_sword_3', 'combat_sword_4', 'combat_sword_5'] },
]
const TIER = ['common', 'rare', 'epic', 'legendary', 'mythic']

/** Five tiers of every trade, then what only drops from a boss. */
export default function GearData() {
  const { copy, lang } = useLang()
  const games = copy.home.skills.games
  return (
    <>
      <h2 className="wiki-h2">{copy.wiki.gearTiers}</h2>
      <div className="wiki-ladders">
        {LADDERS.map((l) => (
          <section key={l.id}>
            <h3>{games[l.id].slot}</h3>
            <ol>
              {l.icons.map((icon, i) => (
                <li key={icon}>
                  <span className="slot tiers-slot">
                    <img src={asset(`items/${icon}.png`)} alt="" />
                  </span>
                  <span className={`tiers-name r-${TIER[i]}`}>{games[l.id].tools[i]}</span>
                </li>
              ))}
            </ol>
          </section>
        ))}
      </div>
      <h2 className="wiki-h2">{copy.wiki.gearDrops}</h2>
      <ul className="wiki-gear">
        {WALL.map((it) => (
          <li key={it.id} className={`tip tip-${it.rarity}`}>
            <img src={asset(`items/${it.id}.png`)} alt="" className="pixelated" />
            <div>
              <p className={`tip-name r-${it.rarity}`}>{it.name[lang]}</p>
              <p className="tip-note">{it.note[lang]}</p>
              {it.boss ? <p className="tip-from">{interpolate(copy.home.loot.drop, { who: it.boss })}</p> : null}
            </div>
          </li>
        ))}
      </ul>
    </>
  )
}
