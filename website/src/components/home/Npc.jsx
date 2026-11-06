import { useEffect, useRef } from 'react'
import { asset } from '../../lib/asset.js'

/**
 * A Minecraft figure drawn with CSS 3D from its real 64x64 skin: six boxes, each with an outer layer.
 * The skins are the game's own NPC art (public/npc).
 */

// [w, h, d, u, v of base, u, v of overlay]: standard skin layout
const PARTS = {
  head: [8, 8, 8, 0, 0, 32, 0],
  body: [8, 12, 4, 16, 16, 16, 32],
  armR: [4, 12, 4, 40, 16, 40, 32],
  armL: [4, 12, 4, 32, 48, 48, 48],
  legR: [4, 12, 4, 0, 16, 0, 32],
  legL: [4, 12, 4, 16, 48, 0, 48],
}

function Faces({ w, h, d, u, v, px, skin, outer }) {
  const W = w * px
  const H = h * px
  const D = d * px
  const face = (name, fw, fh, fu, fv, transform) => (
    <i
      key={name}
      className="vx-f"
      style={{
        width: fw,
        height: fh,
        marginLeft: -fw / 2,
        marginTop: -fh / 2,
        transform,
        backgroundImage: `url(${skin})`,
        backgroundSize: `${64 * px}px ${64 * px}px`,
        backgroundPosition: `${-fu * px}px ${-fv * px}px`,
      }}
    />
  )
  return (
    <span className={outer ? 'vx-box vx-outer' : 'vx-box'} style={outer ? { transform: `scale3d(${(w + 1) / w},${(h + 1) / h},${(d + 1) / d})` } : undefined}>
      {face('front', W, H, u + d, v + d, `translateZ(${D / 2}px)`)}
      {face('back', W, H, u + d * 2 + w, v + d, `rotateY(180deg) translateZ(${D / 2}px)`)}
      {face('right', D, H, u, v + d, `rotateY(-90deg) translateZ(${W / 2}px)`)}
      {face('left', D, H, u + d + w, v + d, `rotateY(90deg) translateZ(${W / 2}px)`)}
      {face('top', W, D, u + d, v, `rotateX(90deg) translateZ(${D / 2}px)`)}
      {face('bottom', W, D, u + d + w, v, `rotateX(-90deg) translateZ(${H - D / 2}px)`)}
    </span>
  )
}

function Part({ part, px, skin, x, y, className = '', origin = '50% 0%' }) {
  const [w, h, d, u, v, ou, ov] = PARTS[part]
  return (
    <span
      className={`vx-part vx-${part} ${className}`}
      style={{ left: x * px, top: y * px, width: 0, height: 0, transformOrigin: origin, '--h': `${(h * px) / 2}px` }}
    >
      <span className="vx-center" style={{ transform: `translate3d(0, ${(h * px) / 2}px, 0)` }}>
        <Faces w={w} h={h} d={d} u={u} v={v} px={px} skin={skin} />
        <Faces w={w} h={h} d={d} u={ou} v={ov} px={px} skin={skin} outer />
      </span>
    </span>
  )
}

export function NpcFigure({ id, px = 6, talking = false, follow = true, className = '' }) {
  const skin = asset(`npc/${id}.png`)
  const root = useRef(null)

  useEffect(() => {
    if (!follow) return undefined
    const el = root.current
    const head = el.querySelector('.vx-head')
    let raf = 0
    const onMove = (e) => {
      if (raf) return
      raf = requestAnimationFrame(() => {
        raf = 0
        const r = el.getBoundingClientRect()
        const dx = (e.clientX - (r.left + r.width / 2)) / Math.max(320, window.innerWidth * 0.5)
        const dy = (e.clientY - (r.top + r.height * 0.14)) / Math.max(320, window.innerHeight * 0.6)
        const ry = Math.max(-1, Math.min(1, dx)) * 38
        const rx = Math.max(-1, Math.min(1, dy)) * -22
        head.style.setProperty('--ry', `${ry.toFixed(1)}deg`)
        head.style.setProperty('--rx', `${rx.toFixed(1)}deg`)
      })
    }
    window.addEventListener('pointermove', onMove, { passive: true })
    return () => {
      window.removeEventListener('pointermove', onMove)
      cancelAnimationFrame(raf)
    }
  }, [follow])

  return (
    <span ref={root} className={`vx ${talking ? 'vx-talking' : ''} ${className}`} style={{ width: 16 * px, height: 32 * px }} aria-hidden="true">
      <span className="vx-rig" style={{ left: 8 * px, top: 0 }}>
        <Part part="legR" px={px} skin={skin} x={-2} y={20} className="vx-swingR" />
        <Part part="legL" px={px} skin={skin} x={2} y={20} className="vx-swingL" />
        <Part part="body" px={px} skin={skin} x={0} y={8} />
        <Part part="armR" px={px} skin={skin} x={-6} y={8} className="vx-armR-idle" />
        <Part part="armL" px={px} skin={skin} x={6} y={8} className="vx-armL-idle" />
        <Part part="head" px={px} skin={skin} x={0} y={0} origin="0 100%" />
      </span>
    </span>
  )
}

/** Just the face: the skin's front head square with its hat layer on top, as the tab list draws it. */
export function NpcFace({ id, size = 40, className = '' }) {
  const skin = asset(`npc/${id}.png`)
  const k = size / 8
  return (
    <span className={`npc-face ${className}`} style={{ width: size, height: size }} aria-hidden="true">
      <i style={{ backgroundImage: `url(${skin})`, backgroundSize: `${64 * k}px ${64 * k}px`, backgroundPosition: `${-8 * k}px ${-8 * k}px` }} />
      <i style={{ backgroundImage: `url(${skin})`, backgroundSize: `${64 * k}px ${64 * k}px`, backgroundPosition: `${-40 * k}px ${-8 * k}px` }} />
    </span>
  )
}
