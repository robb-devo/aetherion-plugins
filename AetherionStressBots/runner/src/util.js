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
    // Pathfinder owns jump. An extra jump key makes the bot hop in place and float in tunnels.
    if (!bot?.pathfinder && roll < 0.34) {
      bot.setControlState('jump', true)
      setTimeout(() => bot.setControlState('jump', false), jitter(220, 0.3))
      note(bot, 'jump', activity)
    } else if (roll < 0.67) {
      bot.swingArm()
      note(bot, 'swing', activity)
    } else {
      bot.look(Math.random() * Math.PI * 2, (Math.random() - 0.5) * 0.5, true).catch(() => {})
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
  bot.qaErrorAt = Date.now()
  note(bot, `error: ${message}`, 'error')
}

export function inventoryAlmostFull(bot) {
  const empty = bot.inventory.slots.filter((s) => s == null).length
  return empty < 4
}

export function tossJunk(bot) {
  for (const item of bot.inventory.items()) {
    const n = item.name
    if (
      n.includes('cobble') || n.includes('dirt') || n.includes('gravel') || n === 'stone'
      || n.includes('deepslate') || n.includes('netherrack') || n.includes('andesite')
      || n.includes('diorite') || n.includes('granite') || n.includes('tuff')
      || n.includes('rotten_flesh') || n === 'stick' || n.includes('poisonous')
    ) {
      bot.tossStack(item).catch(() => {})
    }
  }
}

export function findMatchingBlock(bot, nameSet, radius, yRange = 6, origin = bot.entity?.position, accept = null) {
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
        if (accept && !accept(block)) continue
        const prefer = block.name.includes('ore') || block.name.includes('log') ? -2 : 0
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
