/** Fill keys that exist on {@code example} but are missing from {@code live}. Arrays/scalars already present stay. */
export function mergeMissing(live, example, skip = []) {
  if (example == null || typeof example !== 'object' || Array.isArray(example)) {
    return live
  }
  if (live == null || typeof live !== 'object' || Array.isArray(live)) {
    return live
  }
  const skipSet = new Set(skip)
  const out = { ...live }
  for (const [key, value] of Object.entries(example)) {
    if (skipSet.has(key)) continue
    if (!(key in out) || out[key] == null) {
      out[key] = value
      continue
    }
    if (value && typeof value === 'object' && !Array.isArray(value)
        && out[key] && typeof out[key] === 'object' && !Array.isArray(out[key])) {
      out[key] = mergeMissing(out[key], value, skip)
    }
  }
  return out
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** Human-ish delay: base ± spread fraction. */
export function jitter(base, spread = 0.35) {
  const s = Math.max(0, spread)
  return Math.max(40, Math.round(base * (1 - s + Math.random() * s * 2)))
}

export function fidget(bot, activity) {
  const roll = Math.random()
  try {
    if (roll < 0.34) {
      bot.setControlState('jump', true)
      setTimeout(() => bot.setControlState('jump', false), jitter(220, 0.3))
      note(bot, 'jump', activity)
    } else if (roll < 0.67) {
      bot.swingArm()
      note(bot, 'swing', activity)
    } else {
      const yaw = (bot.entity?.yaw || 0) + (Math.random() - 0.5) * 0.8
      const pitch = Math.max(-0.4, Math.min(0.4, (bot.entity?.pitch || 0) + (Math.random() - 0.5) * 0.3))
      bot.look(yaw, pitch, true).catch(() => {})
      note(bot, 'look around', activity)
    }
  } catch {
    /* ignore */
  }
}

export function waitUntil(predicate, timeoutMs) {
  return new Promise((resolve, reject) => {
    const start = Date.now()
    const id = setInterval(() => {
      if (predicate()) {
        clearInterval(id)
        resolve()
      } else if (Date.now() - start > timeoutMs) {
        clearInterval(id)
        reject(new Error('wait timeout'))
      }
    }, 200)
  })
}

export function note(bot, line, activity) {
  if (activity) bot.qaActivity = activity
  bot.qaLastAction = line
  bot.qaRecent = bot.qaRecent || []
  bot.qaRecent.unshift(String(line || ''))
  if (bot.qaRecent.length > 12) bot.qaRecent.pop()
}

export function markError(bot, err) {
  const message = err && err.message ? err.message : String(err || 'error')
  bot.qaActivity = 'error'
  bot.qaLastError = message
  note(bot, `error: ${message}`, 'error')
}

export function inventoryAlmostFull(bot) {
  const empty = bot.inventory.slots.filter((s) => s == null).length
  return empty < 4
}

export function isMarketResource(name) {
  const n = String(name || '').toLowerCase()
  return n.includes('coal') || n.includes('log') || n.includes('oak') || n.includes('wheat')
    || n.includes('carrot') || n.includes('potato') || n.includes('beet') || n.includes('iron_ingot')
    || n.includes('copper_ingot') || n.includes('gold_ingot') || n.includes('emerald')
    || n === 'cobblestone' || n.includes('raw_iron') || n.includes('raw_gold') || n.includes('raw_copper')
}

export function tossJunk(bot, opts = {}) {
  const keepResources = opts.keepResources
    ?? (bot.role === 'trade' || bot.role === 'farm')
  const items = bot.inventory?.items?.() || []
  const cobble = items.filter((item) => item.name.includes('cobble')).reduce((n, item) => n + item.count, 0)
  for (const item of items) {
    const n = item.name
    if (keepResources && isMarketResource(n) && !(n.includes('cobble') && cobble > 48)) {
      continue
    }
    if (
      n.includes('cobble') || n.includes('dirt') || n.includes('gravel') || n === 'stone'
      || n.includes('deepslate') || n.includes('netherrack') || n.includes('andesite')
      || n.includes('diorite') || n.includes('granite') || n.includes('tuff')
      || n.includes('rotten_flesh') || n === 'stick' || n.includes('poisonous')
      || n.includes('seeds')
    ) {
      bot.tossStack(item).catch(() => {})
    }
  }
}

export function findMatchingBlock(bot, nameSet, radius, yRange = 6, origin = bot.entity?.position, predicate) {
  if (!bot?.entity?.position || !origin) return null
  const originVec = bot.entity.position.offset(0, 0, 0)
  originVec.x = origin.x
  originVec.y = origin.y
  originVec.z = origin.z
  let best = null
  let bestDist = radius
  const r = Math.ceil(radius)
  for (let dx = -r; dx <= r; dx++) {
    for (let dy = -yRange; dy <= yRange; dy++) {
      for (let dz = -r; dz <= r; dz++) {
        const pos = originVec.offset(dx, dy, dz).floored()
        const dist = originVec.distanceTo(pos.offset(0.5, 0.5, 0.5))
        if (dist > radius || dist >= bestDist) continue
        const block = bot.blockAt(pos)
        if (!block || !block.name) continue
        if (!nameSet.has(block.name.toLowerCase())) continue
        if (typeof predicate === 'function' && !predicate(block)) continue
        const prefer = block.name.includes('ore') || block.name.includes('log') || block.name === 'wheat' ? -2 : 0
        const score = dist + prefer
        if (score < bestDist) {
          best = block
          bestDist = score
        }
      }
    }
  }
  return best
}

export function wanderNear(bot, home, radius, goals) {
  if (!bot.pathfinder || bot.pathfinder.isMoving() || !home) return
  const origin = bot.qaHome || home
  const leash = bot.qaLeash ?? Math.max(6, radius)
  const cap = Math.min(Math.max(3, radius), leash)
  for (let i = 0; i < 12; i++) {
    const angle = Math.random() * Math.PI * 2
    const dist = 2 + Math.random() * cap
    const x = origin.x + Math.cos(angle) * dist
    const z = origin.z + Math.sin(angle) * dist
    const y = origin.y
    if (columnHasFloor(bot, x, y, z)) {
      bot.pathfinder.setGoal(new goals.GoalNear(x, y, z, 1))
      return
    }
  }
}

function columnHasFloor(bot, x, y, z) {
  if (!bot.blockAt || !bot.entity?.position?.offset) return false
  const origin = bot.entity.position
  for (const dy of [0, 1, -1, 2, -2]) {
    const feet = origin.offset(0, 0, 0)
    feet.x = x
    feet.y = y + dy
    feet.z = z
    const below = bot.blockAt(feet.offset(0, -1, 0).floored())
    const at = bot.blockAt(feet.floored())
    const head = bot.blockAt(feet.offset(0, 1, 0).floored())
    if (!below || !below.name) continue
    const n = below.name.toLowerCase()
    if (n === 'air' || n.includes('water') || n.includes('lava')) continue
    if (at && at.boundingBox === 'block') continue
    if (head && head.boundingBox === 'block') continue
    return true
  }
  return false
}

export function heldName(bot) {
  const item = bot.heldItem
  return item && item.name ? item.name : '-'
}

export function fmtPos(pos) {
  if (!pos) return '?'
  return `${pos.x.toFixed(1)} ${pos.y.toFixed(1)} ${pos.z.toFixed(1)}`
}
