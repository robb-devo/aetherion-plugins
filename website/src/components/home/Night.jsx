import { useRef, useState } from 'react'
import { useLang } from '../../i18n.jsx'
import { Link } from '../../router.jsx'
import { stage } from '../../world/stage.js'
import Chapter, { Bank } from './Chapter.jsx'

/** A synthesized bell: a bronze bell's inharmonic partials, each decaying at its own speed. */
function strike(ctx, when, root = 196) {
  const partials = [[0.5, 0.5, 5.2], [1, 0.9, 3.6], [1.183, 0.5, 2.6], [1.506, 0.34, 2.1], [2, 0.3, 1.7], [2.514, 0.17, 1.2], [3.011, 0.11, 0.95], [4.166, 0.07, 0.6]]
  const master = ctx.createGain()
  master.gain.value = 0.34
  master.connect(ctx.destination)
  for (const [ratio, amp, decay] of partials) {
    const o = ctx.createOscillator()
    const g = ctx.createGain()
    o.frequency.value = root * ratio
    g.gain.setValueAtTime(0, when)
    g.gain.linearRampToValueAtTime(amp, when + 0.006)
    g.gain.exponentialRampToValueAtTime(0.0008, when + decay)
    o.connect(g)
    g.connect(master)
    o.start(when)
    o.stop(when + decay + 0.1)
  }
}

export default function Night() {
  const { copy } = useLang()
  const t = copy.home.night
  const [ringing, setRinging] = useState(false)
  const ctx = useRef(null)

  const ring = () => {
    if (ringing) return
    const AC = window.AudioContext || window.webkitAudioContext
    if (AC) {
      ctx.current = ctx.current || new AC()
      void ctx.current.resume?.()
    }
    setRinging(true)
    // dusk gets five, as the bellkeeper keeps saying
    const strokes = 5
    const gap = 1700
    for (let i = 0; i < strokes; i += 1) {
      setTimeout(() => {
        if (ctx.current) strike(ctx.current, ctx.current.currentTime + 0.02)
        stage.pulse()
      }, i * gap)
    }
    setTimeout(() => setRinging(false), strokes * gap + 1200)
  }

  return (
    <Chapter id="night" tone="dark" className="ch-night">
      <Bank title={t.title}>
        {t.body.map((line) => (
          <p className="ch-body" key={line}>
            {line}
          </p>
        ))}
        <p className="night-rumour">{t.rumour}</p>
      </Bank>
      <div className="bells bank bank-right">
        <div className="bank-in">
          <p className="bells-line">{t.bells}</p>
          <div className="bells-actions">
            <button type="button" className="btn btn-primary btn-lg notch" onClick={ring} disabled={ringing}>
              {ringing ? t.ringing : t.ring}
            </button>
            <Link to="/wiki/lore" className="btn btn-veil btn-lg notch">
              {t.wiki}
            </Link>
          </div>
          <figure className="bells-quote">
            <blockquote>{t.keeper}</blockquote>
            <figcaption>{t.keeperWho}</figcaption>
          </figure>
        </div>
      </div>
    </Chapter>
  )
}
