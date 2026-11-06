import { useCallback, useEffect, useRef, useState } from 'react'
import { interpolate } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import Chapter, { Bank } from './Chapter.jsx'
import { useInView } from './useInView.js'

const SLOTS = [
  { id: 'forage', item: 'foraging_axe_5', icons: ['foraging_axe', 'foraging_axe_2', 'foraging_axe_3', 'foraging_axe_4', 'foraging_axe_5'], skills: 10 },
  { id: 'farm', item: 'farming_hoe_5', icons: ['farming_hoe', 'farming_hoe_2', 'farming_hoe_3', 'farming_hoe_4', 'farming_hoe_5'], skills: 8 },
  { id: 'fish', item: 'fishing_rod_5', icons: ['fishing_rod', 'fishing_rod_2', 'fishing_rod_3', 'fishing_rod_4', 'fishing_rod_5'], skills: 7 },
  { id: 'mine', item: 'mining_pickaxe_5', icons: ['mining_pickaxe', 'mining_pickaxe_2', 'mining_pickaxe_3', 'mining_pickaxe_4', 'mining_pickaxe_5'], skills: 12 },
  { id: 'combat', item: 'combat_sword_5', icons: ['combat_sword', 'combat_sword_2', 'combat_sword_3', 'combat_sword_4', 'combat_sword_5'], skills: 11 },
]
const TIER_RARITY = ['common', 'rare', 'epic', 'legendary', 'mythic']

/* ------------------------------------------------------------- pixel art, drawn here so no game texture ships */
const W = 160
const H = 92
const T = 12 // tile

function noiseTile(base, spots, seed, fn) {
  const c = document.createElement('canvas')
  c.width = T
  c.height = T
  const x = c.getContext('2d')
  x.fillStyle = base
  x.fillRect(0, 0, T, T)
  let s = seed
  const R = () => ((s = (s * 16807) % 2147483647) / 2147483647)
  for (let i = 0; i < 28; i += 1) {
    x.fillStyle = spots[Math.floor(R() * spots.length)]
    x.fillRect(Math.floor(R() * T), Math.floor(R() * T), 1 + (R() < 0.3 ? 1 : 0), 1)
  }
  fn?.(x, R)
  return c
}

let TILES = null
function tiles() {
  if (TILES) return TILES
  TILES = {
    bark: noiseTile('#6b4a2a', ['#5a3c20', '#7a5632', '#4c3119'], 11, (x) => {
      x.fillStyle = '#4c3119'
      for (let i = 1; i < T; i += 4) x.fillRect(i, 0, 1, T)
    }),
    cut: noiseTile('#b08a56', ['#c29a64', '#9a7646'], 12, (x) => {
      x.fillStyle = '#8a6a3e'
      x.strokeStyle = '#8a6a3e'
      x.strokeRect(2.5, 2.5, 7, 7)
      x.fillRect(5, 5, 2, 2)
    }),
    leaf: noiseTile('#3f7a35', ['#356c2d', '#4c8c3f', '#2c5c26', '#5a9c48'], 13),
    goldleaf: noiseTile('#d9a92f', ['#efbe4a', '#ffdc85', '#b08a24'], 18),
    grass: noiseTile('#6a4a30', ['#5a3c26', '#7a583a'], 14, (x, R) => {
      x.fillStyle = '#5c9a42'
      x.fillRect(0, 0, T, 3)
      x.fillStyle = '#4c8a38'
      for (let i = 0; i < T; i += 1) if (R() < 0.5) x.fillRect(i, 3, 1, 1)
    }),
    soil: noiseTile('#4a321e', ['#3c2816', '#5a3e26'], 15, (x) => {
      x.fillStyle = '#35220f'
      x.fillRect(0, 3, T, 1)
      x.fillRect(0, 8, T, 1)
    }),
    stone: noiseTile('#7d8088', ['#6e7178', '#8c8f97', '#62656c'], 16),
    ore: noiseTile('#7d8088', ['#6e7178', '#8c8f97'], 17, (x) => {
      x.fillStyle = '#b48cf0'
      for (const [a, b] of [[2, 3], [7, 2], [4, 7], [9, 8]]) x.fillRect(a, b, 2, 2)
      x.fillStyle = '#e3ccff'
      for (const [a, b] of [[2, 3], [7, 2], [4, 7], [9, 8]]) x.fillRect(a, b, 1, 1)
    }),
  }
  return TILES
}

const hexA = (h, a) => `rgba(${parseInt(h.slice(1, 3), 16)},${parseInt(h.slice(3, 5), 16)},${parseInt(h.slice(5, 7), 16)},${a})`

function sky(x, top, bottom) {
  const g = x.createLinearGradient(0, 0, 0, H)
  g.addColorStop(0, top)
  g.addColorStop(1, bottom)
  x.fillStyle = g
  x.fillRect(0, 0, W, H)
}

function sparks(x, list, now) {
  for (const p of list) {
    const age = (now - p.t) / p.life
    if (age < 0 || age > 1) continue
    x.fillStyle = hexA(p.c, 1 - age * age)
    const px = p.x + p.vx * age * p.life * 0.04
    const py = p.y + p.vy * age * p.life * 0.04 + age * age * 14
    x.fillRect(Math.round(px), Math.round(py), p.s, p.s)
  }
}

function burst(list, x, y, colors, n, now) {
  for (let i = 0; i < n; i += 1) {
    list.push({ x, y, vx: (Math.random() - 0.5) * 1.1, vy: -0.2 - Math.random() * 0.8, c: colors[Math.floor(Math.random() * colors.length)], s: Math.random() < 0.3 ? 2 : 1, t: now, life: 500 + Math.random() * 450 })
  }
}

const MINE = { cols: 14, rows: 8, ox: -4, oy: 0, order: [34, 49, 35, 48, 62] }

/* The four games share one heart: a marker sweeps a bar, you press on the green, gold is perfect. */
const GAMES = {
  forage: {
    speed: 1.05, green: 0.2, gold: 0.06, goal: 3,
    draw(x, g, now) {
      const t = tiles()
      sky(x, '#8fc2e8', '#dff0f7')
      for (let i = 0; i < W; i += T) x.drawImage(t.grass, i, H - T)
      // a real trunk, three blocks wide at the crown: you chop from the bottom, the tree drops one block
      const logs = 4 - g.progress
      const baseY = H - T
      const cx = 74
      const fall = g.fallAt ? Math.min(1, (now - g.fallAt) / 140) : 1
      const drop = (1 - fall) * -T
      for (let i = 0; i < logs; i += 1) x.drawImage(t.bark, cx, baseY - (i + 1) * T + drop)
      const top = baseY - logs * T + drop
      const crown = g.won ? t.goldleaf : t.leaf
      for (const [a, b] of [[-3, -1], [-2, -1], [-1, -1], [0, -1], [1, -1], [2, -1], [3, -1], [-2, -2], [-1, -2], [0, -2], [1, -2], [2, -2], [-1, -3], [0, -3], [1, -3]]) x.drawImage(crown, cx + a * T, top + b * T + 8)
      if (g.logFly) {
        const k = (now - g.logFly.t) / 420
        if (k < 1) x.drawImage(t.cut, cx + 14 + k * 40, baseY - T - Math.sin(k * 3.14) * 22 + k * 8)
      }
    },
    onHit(g, now, list) {
      g.fallAt = now
      g.logFly = { t: now }
      burst(list, 80, H - T - 6, ['#b08a56', '#6b4a2a', '#c29a64'], 10, now)
    },
    onWin(g, now, list) {
      burst(list, 80, 30, ['#ffdc85', '#efbe4a', '#fff'], 44, now)
    },
  },
  farm: {
    speed: 1.18, green: 0.24, gold: 0.08, goal: 5,
    draw(x, g, now) {
      const t = tiles()
      sky(x, '#a7d4ef', '#f7efd8')
      for (let i = 0; i < W; i += T) x.drawImage(t.soil, i, H - T)
      for (let i = 0; i < 9; i += 1) {
        const cut = i < g.progress
        const px = 26 + i * 12
        const sway = Math.sin(now / 420 + i) > 0.5 ? 1 : 0
        if (cut) {
          x.fillStyle = '#a58a3a'
          x.fillRect(px + 3, H - T - 3, 1, 3)
          x.fillRect(px + 6, H - T - 2, 1, 2)
          continue
        }
        const gold = g.won
        x.fillStyle = gold ? '#ffcf4e' : '#c9a83a'
        for (const o of [1, 3, 5, 7]) {
          x.fillRect(px + o, H - T - 20, 1, 20)
          x.fillStyle = gold ? '#ffe9a0' : '#e3c95a'
          x.fillRect(px + o + sway, H - T - 25, 1, 6)
          x.fillStyle = gold ? '#ffcf4e' : '#c9a83a'
        }
      }
    },
    onHit(g, now, list) {
      burst(list, 26 + (g.progress - 1) * 12 + 4, H - T - 18, ['#e3c95a', '#c9a83a', '#fff3b0'], 9, now)
    },
    onWin(g, now, list) {
      burst(list, 80, 40, ['#ffdc85', '#efbe4a', '#fff'], 40, now)
    },
    total: 9,
  },
  fish: {
    speed: 1.5, green: 0.2, gold: 0.06, goal: 3, patience: true,
    draw(x, g, now) {
      const warm = g.progress / 3
      sky(x, '#bcdcf0', '#eaf4f4')
      const y = 46
      const a = [40 + warm * 60, 110 - warm * 30, 150 - warm * 80]
      const grad = x.createLinearGradient(0, y, 0, H)
      grad.addColorStop(0, `rgb(${a[0] + 30},${a[1] + 50},${a[2] + 40})`)
      grad.addColorStop(1, `rgb(${a[0] - 20},${a[1] - 30},${a[2] - 30})`)
      x.fillStyle = grad
      x.fillRect(0, y, W, H - y)
      x.fillStyle = 'rgba(255,255,255,.35)'
      for (let i = 0; i < 9; i += 1) {
        const ox = (i * 31 + now / (90 + i * 13)) % W
        x.fillRect(Math.round(ox), y + 3 + (i % 4) * 9, 6 + (i % 3) * 3, 1)
      }
      const bite = g.mode === 'reel'
      const bob = bite ? 3 + Math.sin(now / 50) * 2 : Math.sin(now / 480) * 1
      const fx = 92
      const fy = y - 2 + bob
      x.strokeStyle = 'rgba(40,40,60,.75)'
      x.beginPath()
      x.moveTo(0, 8)
      x.quadraticCurveTo(50, bite ? 20 : 4, fx + 1, fy)
      x.stroke()
      x.fillStyle = '#e04848'
      x.fillRect(fx - 1, fy - 2, 4, 3)
      x.fillStyle = '#fff'
      x.fillRect(fx - 1, fy + 1, 4, 2)
      if (g.mode === 'wait' && g.nibble > 0.6) {
        x.fillStyle = 'rgba(255,255,255,.6)'
        x.fillRect(fx - 5, y + 2, 11, 1)
      }
      if (g.fish) {
        const k = (now - g.fish.t) / 620
        if (k < 1) {
          const px = fx - k * 70
          const py = y - Math.sin(k * 3.14) * 34
          x.fillStyle = g.fish.gold ? '#ffcf4e' : '#a6c8d8'
          x.fillRect(px, py, 11, 5)
          x.fillRect(px + 11, py - 1, 3, 7)
          x.fillStyle = '#10183a'
          x.fillRect(px + 2, py + 1, 1, 1)
        }
      }
    },
    onHit(g, now, list, gold) {
      g.fish = { t: now, gold }
      burst(list, 92, 44, ['#ffffff', '#cfe9f4'], 16, now)
    },
    onWin(g, now, list) {
      burst(list, 80, 50, ['#ffdc85', '#ff9d5c', '#fff'], 34, now)
    },
  },
  mine: {
    speed: 1.28, green: 0.2, gold: 0.07, goal: 5,
    draw(x, g, now) {
      const t = tiles()
      x.fillStyle = '#1a1626'
      x.fillRect(0, 0, W, H)
      const cols = MINE.cols
      const rows = MINE.rows
      const ox = MINE.ox
      const oy = MINE.oy
      const order = MINE.order
      const ORE = new Set(order)
      const broken = new Set(order.slice(0, g.progress))
      for (let r = 0; r < rows; r += 1) {
        for (let c = 0; c < cols; c += 1) {
          const i = r * cols + c
          const px = ox + c * T
          const py = oy + r * T
          if (broken.has(i)) {
            x.fillStyle = '#0d0a16'
            x.fillRect(px, py, T, T)
            continue
          }
          x.drawImage(ORE.has(i) ? t.ore : t.stone, px, py)
        }
      }
      if (g.won) {
        const pulse = 0.6 + Math.sin(now / 160) * 0.4
        x.fillStyle = `rgba(180,140,240,${0.34 * pulse})`
        x.fillRect(ox + 5 * T, oy + 2 * T, 4 * T, 3 * T)
        x.fillStyle = '#e3ccff'
        for (const [a, b] of [[6, 3], [7, 2], [6, 2], [7, 3]]) {
          x.fillRect(ox + a * T + 4, oy + b * T + 2, 3, 8)
          x.fillRect(ox + a * T + 2, oy + b * T + 6, 2, 4)
        }
      }
      const nx = order[Math.min(g.progress, order.length - 1)]
      if (!g.won) {
        const px = ox + (nx % cols) * T
        const py = oy + Math.floor(nx / cols) * T
        x.strokeStyle = now % 700 < 350 ? '#ffdc85' : 'rgba(255,220,133,.35)'
        x.strokeRect(px + 0.5, py + 0.5, T - 1, T - 1)
      }
    },
    onHit(g, now, list) {
      const i = MINE.order[g.progress - 1]
      burst(list, MINE.ox + (i % MINE.cols) * T + 6, MINE.oy + Math.floor(i / MINE.cols) * T + 6, ['#b48cf0', '#e3ccff', '#8c8f97'], 16, now)
    },
    onWin(g, now, list) {
      burst(list, 82, 40, ['#e3ccff', '#b48cf0', '#fff'], 40, now)
    },
  },
}

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
