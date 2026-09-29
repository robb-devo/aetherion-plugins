function horizontalDistance(a, b) {
  if (!a || !b) return Infinity
  return Math.hypot(a.x - b.x, a.z - b.z)
}

export function readPads(cfg) {
  const raw = cfg?.pads || cfg?.anchors || cfg?.waypoints || []
  return raw
    .map((p, i) => ({
      id: p.id || `pad-${i}`,
      x: Number(p.x),
      y: Number(p.y),
      z: Number(p.z)
    }))
    .filter((p) => Number.isFinite(p.x) && Number.isFinite(p.y) && Number.isFinite(p.z))
}

export function pickNextPad(from, pads, lastId, { minHop = 6, maxHop = Infinity } = {}) {
  if (!pads.length) return null
  const others = pads.filter((pad) => pad.id !== lastId)
  const pool = others.length ? others : pads
  const reachable = pool.filter((pad) => {
    if (!from) return true
    const dist = horizontalDistance(from, pad)
    if (dist < minHop && pool.length > 1) return false
    if (Number.isFinite(maxHop) && dist > maxHop) return false
    return true
  })
  if (!reachable.length) return null
  return reachable[Math.floor(Math.random() * reachable.length)]
}
