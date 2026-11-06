import { useCallback, useEffect, useRef, useState } from 'react'
import { interpolate } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import Chapter, { Bank } from './Chapter.jsx'

const OBSERVE_ROUNDS = 3
const ROUNDS = 10
const LIVES = 4
const IRIS = { observe: '#f4f1e6', press: '#efa93a', deny: '#a06cf0', prey: '#e0304a' }

/**
 * Reads you the way the boss reads you: it keeps a table of what you did after your last two moves
 * (AetherionGnomon, the Markov read). Bores with a pattern lose. Being random is the only defence,
 * and people are bad at being random.
 */
function predict(history) {
  const counts = { L: 0, R: 0 }
  const a = history[history.length - 1]
  const b = history[history.length - 2]
  for (let i = 2; i < history.length; i += 1) {
    if (history[i - 1] === a && history[i - 2] === b) counts[history[i]] += 3
  }
  for (let i = 1; i < history.length; i += 1) {
    if (history[i - 1] === a) counts[history[i]] += 1
  }
  for (const h of history) counts[h] += 0.25
  if (counts.L === counts.R) return Math.random() < 0.5 ? 'L' : 'R'
  return counts.L > counts.R ? 'L' : 'R'
}

const WAIT = { measure: 820, choose: 1500, strike: 430, result: 760 }

/* ------------------------------------------------------------------ the instrument, drawn in the same pixels as the rest of the game */
const GW = 240
const GH = 150
const LANE = { L: 72, R: 168 }
const FOOT = 122

const px = (x, c, a, b, w = 1, h = 1) => {
  x.fillStyle = c
  x.fillRect(Math.round(a), Math.round(b), Math.round(w), Math.round(h))
}

function needle(x, x0, y0, x1, y1, a = '#d9d3c0', b = '#8d8878') {
  const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))
  for (let i = 0; i <= n; i += 1) {
    const k = i / n
    const w = k < 0.55 ? 2 : 1
    px(x, b, x0 + (x1 - x0) * k + 1, y0 + (y1 - y0) * k, w, 1)
    px(x, a, x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, 1, 1)
  }
}

function dashed(x, x0, y0, x1, y1, offset, c) {
  const n = Math.hypot(x1 - x0, y1 - y0)
  x.fillStyle = c
  for (let d = offset % 7; d < n; d += 7) {
    const k = d / n
    x.fillRect(Math.round(x0 + (x1 - x0) * k), Math.round(y0 + (y1 - y0) * k), 2, 1)
  }
}

/** Five-bar gates, the way anyone counts on a wall. This is the boss's actual notebook. */
function tally(x, a, b, count, color) {
  for (let i = 0; i < count; i += 1) {
    const group = Math.floor(i / 5)
    const inGroup = i % 5
    const gx = a + group * 15
    if (inGroup < 4) px(x, color, gx + inGroup * 3, b, 1, 9)
    else {
      for (let k = 0; k < 11; k += 1) px(x, color, gx - 1 + k, b + 9 - k * 0.9, 1, 1)
    }
  }
}

function scene(x, g, h, view, now) {
  // shake on a hit
  const since = now - (view.hitAt || -9999)
  const shake = since < 300 ? (1 - since / 300) * 3 : 0
  x.save()
  x.translate(Math.round(Math.sin(now / 13) * shake), Math.round(Math.cos(now / 17) * shake))
  x.clearRect(-4, -4, GW + 8, GH + 8)
  const reading = g.phase === 'measure' || g.phase === 'choose'
  const iris = IRIS[g.state] ?? IRIS.observe

  // the room it stands in: a pool of cold light, chalk grid fading out
  const glow = x.createRadialGradient(120, 80, 6, 120, 80, 118)
  glow.addColorStop(0, 'rgba(56,66,128,.5)')
  glow.addColorStop(1, 'rgba(10,13,36,0)')
  x.fillStyle = glow
  x.fillRect(0, 0, GW, GH)
  for (let i = 0; i < 12; i += 1) px(x, 'rgba(239,234,223,.05)', 0, 10 + i * 10, GW, 1)
  for (let i = 0; i < 24; i += 1) px(x, 'rgba(239,234,223,.05)', i * 10, 0, 1, FOOT)

  // the notes, chalked on the wall behind it
  px(x, 'rgba(239,234,223,.55)', 14, 16, 8, 1)
  tally(x, 14, 20, h.L, '#efeadf')
  px(x, 'rgba(239,234,223,.55)', GW - 62, 16, 8, 1)
  tally(x, GW - 62, 20, h.R, '#efeadf')
  // arrows L and R, drawn rather than typed so they need no translation
  for (let i = 0; i < 3; i += 1) px(x, '#efeadf', 14 + i, 16 - i, 1, 1 + i * 2)
  for (let i = 0; i < 3; i += 1) px(x, '#efeadf', GW - 54 - i - 1, 16 - i, 1, 1 + i * 2)

  // the slab
  px(x, '#2a2f55', 22, FOOT + 2, 196, 12)
  px(x, '#3a4170', 22, FOOT, 196, 3)
  px(x, '#14183a', 22, FOOT + 14, 196, 2)
  for (let i = 0; i < 40; i += 1) px(x, '#efeadf', 24 + i * 5, FOOT, 1, i % 5 === 0 ? 4 : 2)
  // the two places you can be
  for (const side of ['L', 'R']) {
    const on = g.side === side
    px(x, on ? '#efbe4a' : 'rgba(239,234,223,.16)', LANE[side] - 26, FOOT + 4, 52, 3)
    if (on) px(x, 'rgba(239,190,74,.22)', LANE[side] - 26, FOOT - 22, 52, 22)
  }

  // measuring lines: from the eye to you, marching while it reads
  const head = { x: 120, y: 42 }
  view.you += ((g.side ? LANE[g.side] : 120) - view.you) * 0.34
  if (reading) {
    const off = now / 60
    dashed(x, head.x, head.y, LANE.L, FOOT, off, g.side === 'L' ? '#ffffff' : 'rgba(239,234,223,.3)')
    dashed(x, head.x, head.y, LANE.R, FOOT, off, g.side === 'R' ? '#ffffff' : 'rgba(239,234,223,.3)')
  }

  // legs: three needles, the feet shift a little as it settles its weight
  const sway = Math.sin(now / 900) * 2
  needle(x, 116, 66, 46 + sway, FOOT)
  needle(x, 124, 66, 194 - sway, FOOT)
  needle(x, 120, 66, 120, FOOT + 3, '#efeadf', '#a8a292')

  // the strike
  if (g.strike) {
    const k = Math.min(1, (now - view.strikeAt) / 160)
    const bx = LANE[g.strike]
    const y0 = head.y
    const width = 16 * (1 - k * 0.2)
    x.globalCompositeOperation = 'lighter'
    const beam = x.createLinearGradient(bx - width, 0, bx + width, 0)
    beam.addColorStop(0, 'rgba(224,48,74,0)')
    beam.addColorStop(0.5, 'rgba(255,90,110,.95)')
    beam.addColorStop(1, 'rgba(224,48,74,0)')
    x.fillStyle = beam
    x.fillRect(bx - width, y0, width * 2, FOOT - y0)
    x.globalCompositeOperation = 'source-over'
    px(x, '#ffd4da', bx - 1, y0, 3, FOOT - y0)
    needle(x, head.x, head.y, bx, y0, '#ff8a98', '#e0304a')
    px(x, '#ffb3bd', bx - 28, FOOT - 1, 56, 3)
  }

  // the body: a brass kite, plates ticking over with each round
  const body = [[120, 8], [138, 32], [131, 60], [124, 68], [116, 68], [109, 60], [102, 32]]
  x.beginPath()
  body.forEach(([a, b], i) => (i ? x.lineTo(a, b) : x.moveTo(a, b)))
  x.closePath()
  x.fillStyle = '#181d44'
  x.fill()
  x.strokeStyle = '#c79a3c'
  x.lineWidth = 2
  x.stroke()
  x.strokeStyle = '#7d5f22'
  x.lineWidth = 1
  x.beginPath()
  x.moveTo(110, 58)
  x.lineTo(130, 58)
  x.moveTo(113, 63)
  x.lineTo(127, 63)
  x.stroke()
  for (let i = 0; i < ROUNDS; i += 1) px(x, i < g.round ? '#efbe4a' : '#3a3f6b', 111 + i * 2, 53, 1, 2)

  // the eye
  const lookX = g.side === 'L' ? -3 : g.side === 'R' ? 3 : Math.sin(now / 1300) * 2
  const blink = (now % 4200) < 110 ? 0.1 : 1
  // a lens, not a cartoon eye: dark glass in a chalk ring, the iris is the only bright thing in it
  x.fillStyle = '#efeadf'
  x.beginPath()
  x.ellipse(120, 34, 14, 12 * blink, 0, 0, 6.3)
  x.fill()
  x.fillStyle = '#0a0d24'
  x.beginPath()
  x.ellipse(120, 34, 12, 10 * blink, 0, 0, 6.3)
  x.fill()
  if (blink > 0.5) {
    x.fillStyle = iris
    x.beginPath()
    x.arc(120 + lookX, 35, 8, 0, 6.3)
    x.fill()
    x.fillStyle = '#080a1e'
    x.beginPath()
    x.arc(120 + lookX * 1.3, 35, g.state === 'prey' ? 2 : 4, 0, 6.3)
    x.fill()
    px(x, '#ffffff', 116 + lookX, 31, 2, 2)
    if (g.state === 'prey' || g.state === 'press') {
      const a = x.createRadialGradient(120, 34, 3, 120, 34, 30)
      a.addColorStop(0, g.state === 'prey' ? 'rgba(224,48,74,.5)' : 'rgba(239,169,58,.35)')
      a.addColorStop(1, 'rgba(0,0,0,0)')
      x.fillStyle = a
      x.fillRect(84, 0, 72, 70)
    }
  }

  // you, a little figure and what it costs to be read
  const hurt = since < 260
  const y = FOOT - 16
  px(x, hurt ? '#ff9aa6' : '#e8b98e', view.you - 3, y, 7, 6)
  px(x, '#6b3f25', view.you - 3, y, 7, 2)
  px(x, '#10183a', view.you - 1, y + 3, 1, 1)
  px(x, '#10183a', view.you + 2, y + 3, 1, 1)
  px(x, hurt ? '#e0304a' : '#56c7aa', view.you - 3, y + 6, 7, 6)
  px(x, '#30435f', view.you - 3, y + 12, 3, 4)
  px(x, '#30435f', view.you + 1, y + 12, 3, 4)

  // sparks: gold when you slipped it, crimson when it had you
  for (const p of view.parts) {
    const age = (now - p.t) / p.life
    if (age < 0 || age > 1) continue
    x.globalAlpha = 1 - age
    px(x, p.c, p.x + p.vx * age * 40, p.y + p.vy * age * 40 + age * age * 24, p.s, p.s)
  }
  x.globalAlpha = 1
  x.restore()
}

function GnomonStage({ game, hist }) {
  const canvas = useRef(null)
  const view = useRef({ you: 120, hitAt: 0, strikeAt: 0, parts: [] })
  const latest = useRef(game)
  latest.current = game
  const last = useRef({ strike: null, outcome: null })

  useEffect(() => {
    const g = game
    const v = view.current
    const now = performance.now()
    if (g.strike && g.strike !== last.current.strike) v.strikeAt = now
    if (g.outcome && g.outcome !== last.current.outcome) {
      const hit = g.outcome === 'hit'
      if (hit) v.hitAt = now
      if (hit || g.outcome === 'dodge') {
        const at = g.strike ? LANE[g.strike] : 120
        for (let i = 0; i < 26; i += 1) {
          v.parts.push({ x: at + (Math.random() - 0.5) * 24, y: FOOT - 2, vx: (Math.random() - 0.5) * 1.4, vy: -0.3 - Math.random() * 1.1, c: hit ? ['#ff8a98', '#e0304a', '#ffd4da'][i % 3] : ['#ffdc85', '#efbe4a', '#ffffff'][i % 3], s: i % 4 ? 1 : 2, t: now, life: 420 + Math.random() * 420 })
        }
        if (v.parts.length > 80) v.parts = v.parts.slice(-60)
      }
    }
    last.current = { strike: g.strike, outcome: g.outcome }
  }, [game])

  useEffect(() => {
    const x = canvas.current.getContext('2d')
    x.imageSmoothingEnabled = false
    let raf = 0
    let on = false
    const loop = (now) => {
      raf = 0
      if (!on) return
      const counts = { L: 0, R: 0 }
      for (const h of hist.current) counts[h] += 1
      scene(x, latest.current, counts, view.current, now)
      raf = requestAnimationFrame(loop)
    }
    const io = new IntersectionObserver(([e]) => {
      on = e.isIntersecting
      if (on && !raf) raf = requestAnimationFrame(loop)
    })
    io.observe(canvas.current)
    return () => {
      io.disconnect()
      on = false
      cancelAnimationFrame(raf)
    }
  }, [hist])

  return <canvas ref={canvas} width={GW} height={GH} className="gn-canvas pixelated" aria-hidden="true" />
}

export default function Gnomon() {
  const { copy } = useLang()
  const t = copy.home.gnomon
  const [game, setGame] = useState({ phase: 'idle', round: 0, hits: 0, side: null, state: 'observe', strike: null, line: null, outcome: null })
  const ref = useRef(game)
  ref.current = game
  const hist = useRef([])
  const guess = useRef(null)
  const dodges = useRef(0)
  const timers = useRef([])
  const clearTimers = () => {
    timers.current.forEach(clearTimeout)
    timers.current = []
  }
  const after = (ms, fn) => timers.current.push(setTimeout(fn, ms))

  useEffect(() => clearTimers, [])

  const nextRound = useCallback((round) => {
    guess.current = hist.current.length >= 2 ? predict(hist.current) : Math.random() < 0.5 ? 'L' : 'R'
    setGame((g) => ({ ...g, phase: 'measure', round, strike: null, outcome: null }))
    after(WAIT.measure, () => {
      setGame((g) => ({ ...g, phase: 'choose' }))
      // the window closes faster the longer it has been reading you
      after(WAIT.choose - Math.min(450, round * 45), () => resolve(round))
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const resolve = (round) => {
    const g = ref.current
    const side = g.side ?? 'L'
    const lastThree = hist.current.slice(-2)
    const repeated = lastThree.length === 2 && lastThree.every((h) => h === side)
    const switched = lastThree.length === 2 && lastThree[1] !== side && lastThree[0] === lastThree[1]
    hist.current.push(side)
    if (round <= OBSERVE_ROUNDS) {
      const line = repeated ? (side === 'L' ? t.lines.left : t.lines.right) : switched ? t.lines.changed : g.line
      setGame((v) => ({ ...v, line, phase: 'result', outcome: 'watched', state: 'observe' }))
      after(WAIT.result * 0.7, () => nextRound(round + 1))
      return
    }
    const strike = guess.current
    const hit = strike === side
    setGame((v) => ({ ...v, phase: 'strike', strike }))
    after(WAIT.strike, () => {
      if (hit) dodges.current = 0
      else dodges.current += 1
      setGame((v) => {
        const hits = v.hits + (hit ? 1 : 0)
        let line = v.line
        if (hit) line = hits >= 2 ? t.lines.knew : t.lines.reach
        else if (dodges.current >= 2) line = t.lines.bait
        else if (switched) line = t.lines.changed
        else if (repeated) line = side === 'L' ? t.lines.left : t.lines.right
        const state = hit ? (hits >= 2 ? 'prey' : 'press') : dodges.current >= 2 ? 'deny' : hits >= 2 ? 'prey' : 'press'
        return { ...v, hits, line, state, phase: 'result', outcome: hit ? 'hit' : 'dodge' }
      })
      after(WAIT.result, () => {
        const now = ref.current
        if (now.hits >= LIVES) {
          setGame((v) => ({ ...v, phase: 'over', won: false, line: t.lines.read, state: 'prey', strike: null }))
        } else if (round >= ROUNDS) {
          setGame((v) => ({ ...v, phase: 'over', won: true, line: t.lines.win, state: 'deny', strike: null }))
        } else nextRound(round + 1)
      })
    })
  }

  const start = () => {
    clearTimers()
    hist.current = []
    dodges.current = 0
    setGame({ phase: 'measure', round: 1, hits: 0, side: null, state: 'observe', strike: null, line: t.lines.open, outcome: null })
    nextRound(1)
  }

  const go = useCallback((side) => {
    setGame((g) => (g.phase === 'measure' || g.phase === 'choose' ? { ...g, side } : g))
  }, [])

  useEffect(() => {
    if (game.phase === 'idle' || game.phase === 'over') return undefined
    const onKey = (e) => {
      if (e.code === 'ArrowLeft') {
        e.preventDefault()
        go('L')
      } else if (e.code === 'ArrowRight') {
        e.preventDefault()
        go('R')
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [game.phase, go])

  const playing = game.phase !== 'idle' && game.phase !== 'over'
  return (
    <Chapter id="gnomon" tone="dark" className="ch-gnomon">
      <Bank title={t.title} side="right">
        <p className="ch-body">{t.body}</p>
        <p className="ch-runin">{t.rule}</p>
      </Bank>
      <div className={`gn ${game.outcome === 'hit' ? 'gn-hit' : ''}`}>
        <div className="gn-top">
          <span className="gn-hearts" role="img" aria-label={`${t.hits}: ${game.hits}`}>
            {Array.from({ length: LIVES }).map((_, i) => (
              <img key={i} src={asset(i < LIVES - game.hits ? 'ui/icon_heart.png' : 'ui/icon_cross.png')} alt="" className="pixelated" />
            ))}
          </span>
          <span className="gn-state font-pixel" style={{ color: IRIS[game.state] }}>
            {t.states[game.state]}
          </span>
          <span className="gn-round font-pixel">{playing ? interpolate(t.round, { n: game.round, max: ROUNDS }) : ''}</span>
        </div>
        <GnomonStage game={{ ...game, strike: game.phase === 'strike' || game.outcome === 'hit' ? game.strike : null }} hist={hist} />
        <p className="gn-line font-pixel" aria-live="polite">
          {game.line ?? ' '}
        </p>
        {playing ? (
          <div className="gn-pads">
            <button type="button" className={`gn-pad ${game.side === 'L' ? 'gn-pad-on' : ''}`} onPointerDown={(e) => { e.preventDefault(); go('L') }} onClick={() => go('L')}>
              {t.left}
            </button>
            <button type="button" className={`gn-pad ${game.side === 'R' ? 'gn-pad-on' : ''}`} onPointerDown={(e) => { e.preventDefault(); go('R') }} onClick={() => go('R')}>
              {t.right}
            </button>
          </div>
        ) : (
          <div className="gn-idle">
            {game.phase === 'over' ? <p className="gn-result">{game.won ? t.resultWin : interpolate(t.resultLose, { n: game.round })}</p> : null}
            <button type="button" className="btn btn-primary btn-lg notch" onClick={start}>
              {game.phase === 'over' ? t.again : t.start}
            </button>
            <p className="gn-keys">{t.keys}</p>
          </div>
        )}
      </div>
    </Chapter>
  )
}
