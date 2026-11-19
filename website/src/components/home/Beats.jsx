import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import { Link } from '../../router.jsx'

/**
 * One frame of the take: a line, a few sentences, at most one small thing quoted from the game's
 * own interface. `side` is where the words stand on a wide screen; the camera keeps its subject on
 * the other side (world/route.js).
 */
function Beat({ id, tone = 'day', side = 'left', title, children }) {
  return (
    <section id={id} data-chapter={id} data-mood={tone} className={`beat beat-${side}`}>
      <div className="beat-in">
        <h2 className="beat-title">{title}</h2>
        {children}
      </div>
    </section>
  )
}

function More({ to, children }) {
  return (
    <p className="beat-more">
      <Link to={to}>{children}</Link>
    </p>
  )
}

export function About() {
  const t = useLang().copy.home.about
  return (
    <Beat id="about" title={t.title}>
      <p className="beat-body">{t.body}</p>
      <p className="beat-runin">{t.runIn}</p>
    </Beat>
  )
}

const TRADES = [
  ['forage', 'foraging_axe_5'],
  ['farm', 'farming_hoe_5'],
  ['fish', 'fishing_rod_5'],
  ['mine', 'mining_pickaxe_5'],
  ['combat', 'combat_sword_5'],
]

export function Jobs() {
  const { copy } = useLang()
  const t = copy.home.jobs
  return (
    <Beat id="jobs" side="right" title={t.title}>
      <p className="beat-body">{t.body}</p>
      <div className="trades">
        <ul className="hotbar" aria-hidden="true">
          {TRADES.map(([id, icon]) => (
            <li key={id} className="hotbar-slot">
              <img src={asset(`items/${icon}.png`)} alt="" width="32" height="32" draggable="false" />
            </li>
          ))}
        </ul>
        <p className="trades-names">{TRADES.map(([id]) => copy.game.trades[id].name).join(', ')}</p>
      </div>
      <More to="/wiki/skills">{t.more}</More>
    </Beat>
  )
}

export function World() {
  const t = useLang().copy.home.world
  return (
    <Beat id="world" title={t.title}>
      <p className="beat-body">{t.body}</p>
      <More to="/wiki/island">{t.more}</More>
      <Link to="/wiki/island" className="plate" tabIndex={-1} aria-hidden="true">
        <img src={asset('world/chart-1000.webp')} alt="" width="1000" height="1000" loading="lazy" decoding="async" draggable="false" />
      </Link>
    </Beat>
  )
}

export function Isle() {
  const t = useLang().copy.home.isle
  return (
    <Beat id="isle" side="right" title={t.title}>
      <p className="beat-body">{t.body}</p>
    </Beat>
  )
}

export function Pets() {
  const { copy } = useLang()
  const t = copy.home.pets
  return (
    <Beat id="pets" tone="dusk" title={t.title}>
      <p className="beat-body">{t.body}</p>
      <div className="catch">
        <img className="catch-sphere" src={asset('items/catch_sphere.png')} alt="" width="48" height="48" draggable="false" />
        <div className={`tip tip-${t.card.rarity}`}>
          <p className={`tip-name r-${t.card.rarity}`}>{t.card.name}</p>
          <p className="tip-note">{t.card.note}</p>
          <p className={`tip-rarity r-${t.card.rarity}`}>{copy.rarity[t.card.rarity]}</p>
        </div>
      </div>
      <More to="/wiki/pets">{t.more}</More>
    </Beat>
  )
}

export function Dungeons() {
  const { copy } = useLang()
  const t = copy.home.dungeons
  const boss = copy.game.bosses.find((b) => b.name === 'The Frostbound')
  return (
    <Beat id="dungeons" tone="dark" side="right" title={t.title}>
      <p className="beat-body">{t.body}</p>
      {boss ? (
        <div className="bossbar">
          <p className="bossbar-name">{boss.name}</p>
          <span className="bossbar-track" style={{ backgroundImage: `url(${asset(`ui/${boss.color}_background.png`)})` }}>
            <span className="bossbar-fill" style={{ backgroundImage: `url(${asset(`ui/${boss.color}_progress.png`)})` }} />
          </span>
          <p className="bossbar-line">{boss.line}</p>
        </div>
      ) : null}
      <More to="/wiki/dungeons">{t.more}</More>
    </Beat>
  )
}

export function Exile() {
  const t = useLang().copy.home.exile
  return (
    <Beat id="exile" tone="dark" title={t.title}>
      <p className="beat-body">{t.body}</p>
      <More to="/wiki/lore">{t.more}</More>
    </Beat>
  )
}
