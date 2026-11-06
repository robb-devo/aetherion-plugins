import { useEffect, useRef, useState } from 'react'
import { interpolate, SITE_VARS } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { stage } from '../../world/stage.js'
import { JoinLinks, ServerRow } from './Server.jsx'

/** The first screen: the name, behind the city's towers; under it the row you already know from your server list. */
export default function Hero() {
  const { copy } = useLang()
  const t = copy.home.hero
  const ref = useRef(null)
  const [load, setLoad] = useState({ progress: 0, mode: 'idle' })

  useEffect(() => {
    return stage.subscribe((state) => setLoad({ progress: state.progress ?? 0, mode: stage.mode }))
  }, [])

  useEffect(() => {
    const el = ref.current
    let last = null
    const down = (e) => {
      if (e.target.closest('a, button, input, .mp-list')) return
      last = { x: e.clientX, y: e.clientY }
      stage.setOrbitDrag(true)
      el.setPointerCapture?.(e.pointerId)
    }
    const move = (e) => {
      if (!last) return
      stage.orbitBy(e.clientX - last.x, e.clientY - last.y)
      last = { x: e.clientX, y: e.clientY }
    }
    const up = () => {
      last = null
      stage.setOrbitDrag(false)
    }
    el.addEventListener('pointerdown', down)
    el.addEventListener('pointermove', move)
    el.addEventListener('pointerup', up)
    el.addEventListener('pointercancel', up)
    return () => {
      el.removeEventListener('pointerdown', down)
      el.removeEventListener('pointermove', move)
      el.removeEventListener('pointerup', up)
      el.removeEventListener('pointercancel', up)
    }
  }, [])

  const live = load.mode === 'live'
  return (
    <section id="hero" data-chapter="hero" className="hero" ref={ref}>
      <h1 className="hero-title">Aetherion</h1>
      <div className="hero-foot">
        <p className="hero-lore">{t.lore}</p>
        <ServerRow className="hero-row" />
        <JoinLinks className="hero-links" />
        <p className="hero-note">
          {live && load.progress < 1 ? (
            <span className="font-pixel">
              {t.loading} {Math.round(load.progress * 100)}%
            </span>
          ) : live ? (
            t.note
          ) : (
            t.noteStill
          )}{' '}
          <span className="hero-version">{interpolate(t.versionLine, SITE_VARS)}</span>
        </p>
      </div>
    </section>
  )
}
