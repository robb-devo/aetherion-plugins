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

function GnomonFigure({ state, side, strike, game }) {
  const look = side === 'L' ? -5 : side === 'R' ? 5 : 0
  const beamX = strike === 'L' ? 112 : strike === 'R' ? 288 : null
  const px = side === 'L' ? 112 : side === 'R' ? 288 : 200
  const iris = IRIS[state] ?? IRIS.observe
  return (
    <svg viewBox="0 0 400 270" className={`gn-svg ${game === 'choose' ? 'gn-reading' : ''}`} role="img" aria-hidden="true">
      {/* the platform, chalked */}
      <g stroke="#efeadf" strokeOpacity="0.5" fill="none">
        <path d="M30 236 H370" strokeWidth="2" />
        {Array.from({ length: 35 }).map((_, i) => (
          <path key={i} d={`M${30 + i * 10} 236 v${i % 5 === 0 ? 9 : 4}`} strokeWidth="1" />
        ))}
      </g>
      <rect x="66" y="238" width="92" height="9" fill={side === 'L' ? '#efbe4a' : 'rgba(239,234,223,.14)'} />
      <rect x="242" y="238" width="92" height="9" fill={side === 'R' ? '#efbe4a' : 'rgba(239,234,223,.14)'} />

      {/* the measuring lines */}
      <g stroke="#efeadf" strokeWidth="1" strokeDasharray="4 5" className="gn-chalk" opacity={game === 'choose' || game === 'measure' ? 0.85 : 0.2}>
        <path d={`M200 62 L112 226`} />
        <path d={`M200 62 L288 226`} />
      </g>

      {/* three needle legs and the instrument */}
      <g fill="#c9c3b2">
        <path d="M196 96 L78 236 L82 236 L200 104 Z" />
        <path d="M204 96 L322 236 L318 236 L200 104 Z" />
        <path d="M197 100 L199 248 L201 248 L203 100 Z" />
      </g>
      <path d="M200 16 L222 58 L206 104 H194 L178 58 Z" fill="#2a3060" stroke="#efeadf" strokeWidth="2" />
      <path d="M184 76 H216 M188 88 H212" stroke="#efbe4a" strokeWidth="2" />
      <circle cx="200" cy="56" r="17" fill="#0a0d24" stroke="#efeadf" strokeWidth="2" />
      <g style={{ transform: `translate(${look}px, 3px)`, transition: 'transform .18s ease-out' }}>
        <circle cx="200" cy="56" r="10" fill={iris} style={{ transition: 'fill .2s linear' }} />
        <circle cx="200" cy="56" r="4" fill="#0a0d24" />
      </g>

      {/* the strike */}
      {beamX ? (
        <g>
          <rect x={beamX - 7} y="72" width="14" height="164" fill="#e0304a" className="gn-beam" />
          <rect x={beamX - 46} y="232" width="92" height="6" fill="#ff7d8a" className="gn-beam" />
        </g>
      ) : null}

      {/* you */}
      <g style={{ transform: `translateX(${px - 200}px)`, transition: 'transform .16s cubic-bezier(.2,.9,.3,1)' }}>
        <rect x="192" y="212" width="16" height="16" fill="#efbe4a" />
        <rect x="196" y="217" width="3" height="3" fill="#10183a" />
        <rect x="202" y="217" width="3" height="3" fill="#10183a" />
        <rect x="194" y="228" width="12" height="8" fill="#56c7aa" />
      </g>
    </svg>
  )
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
        <GnomonFigure state={game.state} side={game.side} strike={game.phase === 'strike' || game.outcome === 'hit' ? game.strike : null} game={game.phase} />
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
