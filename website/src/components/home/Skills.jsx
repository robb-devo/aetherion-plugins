import { useCallback, useEffect, useRef, useState } from 'react'
import { interpolate } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import Chapter, { Bank } from './Chapter.jsx'
import { GAMES, H, sparks, W } from './scenes.js'
import { useInView } from './useInView.js'

const SLOTS = [
  { id: 'forage', item: 'foraging_axe_5', icons: ['foraging_axe', 'foraging_axe_2', 'foraging_axe_3', 'foraging_axe_4', 'foraging_axe_5'], skills: 10 },
  { id: 'farm', item: 'farming_hoe_5', icons: ['farming_hoe', 'farming_hoe_2', 'farming_hoe_3', 'farming_hoe_4', 'farming_hoe_5'], skills: 8 },
  { id: 'fish', item: 'fishing_rod_5', icons: ['fishing_rod', 'fishing_rod_2', 'fishing_rod_3', 'fishing_rod_4', 'fishing_rod_5'], skills: 7 },
  { id: 'mine', item: 'mining_pickaxe_5', icons: ['mining_pickaxe', 'mining_pickaxe_2', 'mining_pickaxe_3', 'mining_pickaxe_4', 'mining_pickaxe_5'], skills: 12 },
  { id: 'combat', item: 'combat_sword_5', icons: ['combat_sword', 'combat_sword_2', 'combat_sword_3', 'combat_sword_4', 'combat_sword_5'], skills: 11 },
]
const TIER_RARITY = ['common', 'rare', 'epic', 'legendary', 'mythic']

function Game({ id, t, you, active }) {
  const spec = GAMES[id]
  const canvas = useRef(null)
  const bar = useRef(null)
  const state = useRef(null)
  const parts = useRef([])
  const [ui, setUi] = useState({ streak: 0, won: false, flash: null, mode: 'run' })
  const fill = (s) => (s ? s.replaceAll('{player}', you) : s)

  const reset = useCallback(() => {
    state.current = { progress: 0, won: false, mode: spec.patience ? 'wait' : 'run', phase: 0, bite: 0, fish: null, nibble: 0, fallAt: 0, logFly: null, start: performance.now() }
    if (spec.patience) state.current.bite = performance.now() + 1100 + Math.random() * 1700
    parts.current = []
    setUi({ streak: 0, won: false, flash: null, mode: state.current.mode })
  }, [spec])

  useEffect(() => {
    reset()
  }, [reset])

  const press = useCallback(() => {
    const g = state.current
    if (!g) return
    const now = performance.now()
    if (g.won) {
      reset()
      return
    }
    if (g.mode === 'wait') {
      // pulled too early
      setUi((u) => ({ ...u, flash: 'early' }))
      g.bite = now + 900 + Math.random() * 1500
      return
    }
    const pos = Math.abs(Math.sin(g.phase))
    const d = Math.abs(pos - 0.5)
    const inGreen = d < spec.green / 2
    const gold = d < spec.gold / 2
    if (inGreen) {
      g.progress += 1
      spec.onHit(g, now, parts.current, gold)
      if (g.progress >= (spec.goal ?? 3)) {
        g.won = true
        spec.onWin(g, now, parts.current)
        setUi((u) => ({ ...u, streak: g.progress, won: true, flash: null }))
      } else {
        setUi((u) => ({ ...u, streak: g.progress, flash: gold ? 'gold' : 'green' }))
      }
      if (spec.patience && !g.won) {
        g.mode = 'wait'
        g.bite = now + 900 + Math.random() * 1500
        setUi((u) => ({ ...u, mode: 'wait' }))
      }
    } else {
      g.progress = 0
      if (spec.patience) {
        g.mode = 'wait'
        g.bite = now + 900 + Math.random() * 1500
      }
      setUi((u) => ({ ...u, streak: 0, flash: 'miss', mode: g.mode }))
    }
  }, [reset, spec])

  useEffect(() => {
    if (!active) return undefined
    const x = canvas.current.getContext('2d')
    x.imageSmoothingEnabled = false
    let raf = 0
    let last = performance.now()
    const loop = (now) => {
      const dt = now - last
      last = now
      const g = state.current
      if (g) {
        if (g.mode === 'wait') {
          g.nibble = Math.abs(Math.sin(now / 340))
          if (now >= g.bite) {
            g.mode = 'reel'
            g.phase = Math.PI * Math.random()
            setUi((u) => ({ ...u, mode: 'reel' }))
          }
        } else if (!g.won) {
          g.phase += (dt / 1000) * spec.speed * Math.PI * 0.72
        }
        spec.draw(x, g, now)
        sparks(x, parts.current, now)
        if (parts.current.length > 120) parts.current = parts.current.filter((p) => now - p.t < p.life)
        if (bar.current) {
          bar.current.style.setProperty('--m', g.mode === 'wait' ? '50' : (Math.abs(Math.sin(g.phase)) * 100).toFixed(2))
          bar.current.dataset.hidden = g.mode === 'wait' ? 'true' : 'false'
        }
      }
      raf = requestAnimationFrame(loop)
    }
    raf = requestAnimationFrame(loop)
    return () => cancelAnimationFrame(raf)
  }, [active, spec])

  useEffect(() => {
    if (!active) return undefined
    const onKey = (e) => {
      if (e.code !== 'Space' || e.target.closest?.('input, textarea, button, a')) return
      const r = canvas.current.getBoundingClientRect()
      if (r.bottom < 0 || r.top > window.innerHeight) return
      e.preventDefault()
      press()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [active, press])

  useEffect(() => {
    if (!ui.flash) return undefined
    const id = setTimeout(() => setUi((u) => ({ ...u, flash: null })), 700)
    return () => clearTimeout(id)
  }, [ui.flash])

  const goal = spec.goal ?? 3
  const status = ui.won ? t.win : ui.flash === 'miss' ? null : ui.mode === 'wait' ? t.wait : ui.mode === 'reel' && spec.patience ? t.bite : null
  return (
    <div className="game">
      <button type="button" className={`game-stage ${ui.flash ? `game-${ui.flash}` : ''}`} onPointerDown={(e) => { e.preventDefault(); press() }} onKeyDown={(e) => { if (e.code === 'Enter') press() }}>
        <canvas ref={canvas} width={W} height={H} className="game-canvas pixelated" aria-hidden="true" />
        <span ref={bar} className="game-bar" aria-hidden="true" data-hidden="false" style={{ '--g': spec.green * 100, '--au': spec.gold * 100 }}>
          <i className="game-bar-green" />
          <i className="game-bar-gold" />
          <i className="game-bar-marker" />
        </span>
        <span className="game-pips" aria-hidden="true">
          {Array.from({ length: goal }).map((_, i) => (
            <i key={i} className={i < ui.streak ? 'on' : ''} />
          ))}
        </span>
        {status ? <span className={`game-status ${ui.won ? 'game-status-win' : ''}`}>{status}</span> : null}
        {ui.flash === 'miss' ? <span className="game-status game-status-miss">{t.missText}</span> : null}
        <span className="sr-only">{t.tapLabel}</span>
      </button>
      <p className="game-line">
        <span className="game-who">{t.teacher}</span> {fill(ui.won ? t.winBody : t.line)}
      </p>
      <p className="game-goal">{ui.won ? t.againText : t.goal}</p>
    </div>
  )
}

export default function Skills() {
  const { copy } = useLang()
  const t = copy.home.skills
  const [ref, inView] = useInView({ margin: '10% 0px' })
  const [slot, setSlot] = useState('forage')
  const you = copy.home.talk.you
  const current = SLOTS.find((s) => s.id === slot)
  const g = t.games[slot]

  useEffect(() => {
    const onKey = (e) => {
      if (!inView || e.target.closest?.('input, textarea')) return
      const n = Number(e.key)
      if (n >= 1 && n <= SLOTS.length) setSlot(SLOTS[n - 1].id)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [inView])

  return (
    <Chapter id="skills" tone="day" className="ch-skills">
      <Bank title={t.title} side="right">
        <p className="ch-body">{t.body}</p>
      </Bank>
      <div ref={ref} className="skills-module">
        <div className="hotbar" role="tablist" aria-label={t.label}>
          {SLOTS.map((s) => (
            <button type="button" role="tab" key={s.id} aria-selected={slot === s.id} className={`hotbar-slot ${slot === s.id ? 'hotbar-on' : ''}`} onClick={() => setSlot(s.id)}>
              <img src={asset(`items/${s.item}.png`)} alt="" draggable="false" />
              <span>{t.games[s.id].slot}</span>
            </button>
          ))}
        </div>
        <div className="skills-panel">
          {slot === 'combat' ? (
            <div className="game">
              <div className="game-vex">
                <p className="game-line">
                  <span className="game-who">{g.teacher}</span> {g.line}
                </p>
                <p className="game-goal">{g.goal}</p>
                <a
                  className="btn btn-primary notch"
                  href="#gnomon"
                  onClick={(e) => {
                    e.preventDefault()
                    document.getElementById('gnomon')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
                  }}
                >
                  {g.jump}
                </a>
              </div>
            </div>
          ) : (
            <Game key={slot} id={slot} active={inView} you={you} t={{ ...g, missText: t.miss, tapLabel: t.tap, againText: t.restart }} />
          )}
          <div className="tiers">
            <p className="tiers-title">{interpolate(t.of, { n: current.skills })}</p>
            <ol className="tiers-list" aria-label={t.tiers}>
              {current.icons.map((icon, i) => (
                <li key={icon}>
                  <span className="slot tiers-slot">
                    <img src={asset(`items/${icon}.png`)} alt="" draggable="false" />
                  </span>
                  <span className={`tiers-name r-${TIER_RARITY[i]}`}>{g.tools[i]}</span>
                </li>
              ))}
            </ol>
            {g.extra ? <p className="tiers-extra">{g.extra}</p> : null}
          </div>
        </div>
      </div>
    </Chapter>
  )
}
