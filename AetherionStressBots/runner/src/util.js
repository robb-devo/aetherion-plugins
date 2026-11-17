export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * Run an async tick over and over, never two at once: the next tick is scheduled only after the
 * previous one settled. Stops when the bot disconnects. Returns a stop function.
 */
export function runEvery(bot, intervalMs, tick, onError) {
  let stopped = false
  let timer = null
  const loop = async () => {
    if (stopped) return
    try {
      await tick()
    } catch (err) {
      if (onError) onError(err)
    }
    if (!stopped) timer = setTimeout(loop, jitter(intervalMs, 0.15))
  }
  const stop = () => {
    stopped = true
    if (timer) clearTimeout(timer)
  }
  bot.once('end', stop)
  timer = setTimeout(loop, jitter(intervalMs, 0.5))
  return stop
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
  note(bot, `error: ${message}`, 'error')
}

export function inventoryAlmostFull(bot) {
  const inv = bot.inventory
  if (!inv) return false
  if (typeof inv.emptySlotCount === 'function') return inv.emptySlotCount() < 4
  const empty = inv.slots.slice(inv.inventoryStart ?? 9, inv.inventoryEnd ?? 45).filter((s) => s == null).length
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

/** Hold the first inventory item matching {@code predicate} (pick, axe, rod, sphere, sword). */
export async function equipMatching(bot, predicate) {
  const held = bot.heldItem
  if (held && predicate(held)) return true
  const item = (bot.inventory?.items?.() || []).find(predicate)
  if (!item) return false
  try {
    await bot.equip(item, 'hand')
    return true
  } catch {
    return false
  }
}

/**
 * Closest block whose name is in {@code nameSet} around {@code origin} (ores and logs win ties).
 * Uses mineflayer's palette scan instead of a block-by-block cube, so 40 bots stay cheap.
 * {@code skip(block)} drops candidates the caller already gave up on.
 */
export function findMatchingBlock(bot, nameSet, radius, yRange = 6, origin = bot.entity?.position, skip = null) {
  if (!bot?.entity?.position || !origin || !nameSet || nameSet.size === 0) return null
  const ids = []
  for (const name of nameSet) {
    const def = bot.registry?.blocksByName?.[name]
    if (def) ids.push(def.id)
  }
  if (ids.length === 0) return null
  const point = bot.entity.position.offset(0, 0, 0)
  point.x = origin.x
  point.y = origin.y
  point.z = origin.z
  let positions = []
  try {
    positions = bot.findBlocks({ matching: ids, maxDistance: Math.ceil(radius), count: 96, point })
  } catch {
    return null
  }
  let best = null
  let bestScore = Infinity
  for (const pos of positions) {
    if (Math.abs(pos.y - origin.y) > yRange) continue
    const dist = point.distanceTo(pos.offset(0.5, 0.5, 0.5))
    if (dist > radius) continue
    const block = bot.blockAt(pos)
    if (!block || !block.name) continue
    if (skip && skip(block)) continue
    const prefer = block.name.includes('ore') || block.name.includes('log') ? -2 : 0
    const score = dist + prefer
    if (score < bestScore) {
      best = block
      bestScore = score
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
