import { useEffect, useRef, useState } from 'react'
import { useLang } from '../i18n.jsx'
import { useRouter } from '../router.jsx'
import { CHAPTERS } from '../world/route.js'
import { stage } from '../world/stage.js'

/** The day on the page against the hours on a dial. */
const HOURS = [[0, 5.7], [0.12, 8], [0.26, 12], [0.38, 15.5], [0.48, 18.5], [0.56, 20], [0.63, 21], [0.78, 0 + 24], [0.88, 3.5 + 24], [1, 5.7 + 24]]

export function clockText(t) {
  let h = HOURS[0][1]
  for (let i = 0; i < HOURS.length - 1; i += 1) {
    if (t >= HOURS[i][0] && t <= HOURS[i + 1][0]) {
      h = HOURS[i][1] + ((t - HOURS[i][0]) / (HOURS[i + 1][0] - HOURS[i][0])) * (HOURS[i + 1][1] - HOURS[i][1])
      break
    }
  }
  const whole = Math.floor(h) % 24
  const min = Math.floor(((h % 1) * 60) / 10) * 10
  return `${String(whole).padStart(2, '0')}:${String(min).padStart(2, '0')}`
}

const DIAL = 16
const RIM = ['#7a5216', '#efbe4a', '#ffdc85']

/** A little pixel dial: a window on the sky turning behind a gilt rim, in 64 steps, the way a game clock does. */
function drawDial(ctx, t) {
  const step = Math.round(t * 64) / 64
  const rot = step * Math.PI * 2
  ctx.clearRect(0, 0, DIAL, DIAL)
  for (let y = 0; y < DIAL; y += 1) {
    for (let x = 0; x < DIAL; x += 1) {
      const dx = x + 0.5 - 8
      const dy = y + 0.5 - 8
      const r = Math.hypot(dx, dy)
      if (r > 7.9) continue
      if (r > 6.6) {
        const lit = dx + dy < -1.5 ? 2 : dx + dy > 3 ? 0 : 1
        ctx.fillStyle = RIM[lit]
      } else {
        const a = Math.atan2(dy, dx) + rot + Math.PI / 2
        const h = Math.sin(a + Math.PI / 2)
        ctx.fillStyle = h > 0.14 ? '#6bb6ef' : h > -0.14 ? '#ff9d5c' : '#10183a'
      }
      ctx.fillRect(x, y, 1, 1)
    }
  }
  const dot = (ang, rad, col, s) => {
    const a = ang - rot - Math.PI / 2
    const px = Math.round(8 + Math.cos(a) * rad - s / 2)
    const py = Math.round(8 + Math.sin(a) * rad - s / 2)
    ctx.fillStyle = col
    ctx.fillRect(px, py, s, s)
  }
  dot(0, 3.4, '#fff3b0', 3)
  dot(Math.PI, 3.4, '#e8ecf8', 2)
  // the mark the sky turns past
  ctx.fillStyle = '#ffdc85'
  ctx.fillRect(7, 2, 2, 2)
}

export default function Clock() {
  const { copy } = useLang()
  const { path } = useRouter()
  const c = copy.home.clock
  const canvas = useRef(null)
  const text = useRef(null)
  const [open, setOpen] = useState(false)
  const [past, setPast] = useState(false)
  const [end, setEnd] = useState(false)
  const wrap = useRef(null)
  const onHome = path === '/'

  useEffect(() => {
    const onScroll = () => {
      setPast(window.scrollY > window.innerHeight * 0.6)
      // the day is over at the footer; the clock would only sit on its small print
      const footer = document.querySelector('footer')
      setEnd(footer ? footer.getBoundingClientRect().top < window.innerHeight - 40 : false)
    }
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    const ctx = canvas.current?.getContext('2d')
    if (!ctx) return undefined
    let lastStep = -1
    let lastText = ''
    return stage.subscribe((state) => {
      const step = Math.round((state.t ?? 0) * 64)
      if (step !== lastStep) {
        lastStep = step
        drawDial(ctx, state.t ?? 0)
      }
      const value = clockText(state.t ?? 0)
      if (value !== lastText && text.current) {
        lastText = value
        text.current.textContent = value
      }
    })
  }, [])

  useEffect(() => {
    if (!open) return undefined
    const onDown = (event) => {
      if (!wrap.current?.contains(event.target)) setOpen(false)
    }
    const onKey = (event) => event.key === 'Escape' && setOpen(false)
    document.addEventListener('pointerdown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (!onHome) return null

  return (
    <div className={`clock ${past ? 'clock-past' : ''} ${end ? 'clock-end' : ''}`} ref={wrap}>
      <button type="button" className="clock-btn" aria-expanded={open} aria-label={c.open} onClick={() => setOpen((v) => !v)}>
        <canvas ref={canvas} width={DIAL} height={DIAL} className="clock-dial pixelated" aria-hidden="true" />
        <span ref={text} className="clock-time font-pixel" aria-hidden="true">
          05:40
        </span>
      </button>
      {open ? (
        <nav className="clock-menu" aria-label={c.label}>
          <ol>
            {CHAPTERS.map((ch) => (
              <li key={ch.id}>
                <a
                  href={`#${ch.id}`}
                  onClick={(e) => {
                    e.preventDefault()
                    setOpen(false)
                    document.getElementById(ch.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
                  }}
                >
                  <span className="font-pixel clock-at">{clockText(ch.at)}</span>
                  <span>{c.chapters[ch.id]}</span>
                </a>
              </li>
            ))}
          </ol>
        </nav>
      ) : null}
    </div>
  )
}
