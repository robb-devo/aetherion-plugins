/**
 * Shared locomotion guards. Pathfinder owns jumping; callers must not
 * reset a live goal every tick or hold jump, or the bot hops in place.
 */

const RAILS = new Set(['rail', 'powered_rail', 'detector_rail', 'activator_rail'])

export function sameGoal(a, b, slack = 1.5) {
  if (!a || !b) return false
  if (!Number.isFinite(a.x) || !Number.isFinite(b.x)) return false
  const dx = a.x - b.x
  const dz = a.z - b.z
  const dy = (a.y ?? 0) - (b.y ?? 0)
  return Math.hypot(dx, dz) <= slack && Math.abs(dy) <= 1.6
}

export function alreadyThere(pos, goal, range = 1.6) {
  if (!pos || !goal) return false
  return sameGoal(pos, goal, range)
}

/**
 * Reject a new pathfinder goal when one was just issued or the bot is
 * already walking the same spot. Repeated setGoal is what makes Mineflayer jump in place.
 */
export function shouldIssueGoal({
  moving = false,
  current = null,
  next = null,
  now = 0,
  issuedAt = 0,
  minIntervalMs = 1400
} = {}) {
  if (!next || !Number.isFinite(next.x) || !Number.isFinite(next.z)) return false
  const age = now - (issuedAt || 0)
  if (current && age < 350) return false
  if (moving && current && sameGoal(current, next, 3)) return false
  if (current && sameGoal(current, next, 1.25) && age < minIntervalMs) return false
  return true
}

export function blockName(block) {
  if (!block) return ''
  if (typeof block === 'string') return block
  return block.name || ''
}

export function isRailName(name) {
  const plain = blockName(name).toLowerCase().replace(/^minecraft:/, '')
  return RAILS.has(plain)
}

export function isSolidName(name) {
  const plain = blockName(name).toLowerCase()
  if (!plain || plain === 'air' || plain === 'cave_air' || plain === 'void_air') return false
  if (plain.includes('water') || plain.includes('lava')) return false
  if (isRailName(plain)) return false
  return true
}

/**
 * An ore in a solid corner has no face to stand on. Pathing there is a jump into the wall.
 */
export function hasDigApproach(blockAt, x, y, z) {
  if (typeof blockAt !== 'function') return true
  const faces = [[1, 0], [-1, 0], [0, 1], [0, -1]]
  for (const [dx, dz] of faces) {
    for (const dy of [0, 1, -1]) {
      const feet = blockAt(x + dx, y + dy, z + dz)
      const head = blockAt(x + dx, y + dy + 1, z + dz)
      const below = blockAt(x + dx, y + dy - 1, z + dz)
      if (isSolidName(feet) || isSolidName(head)) continue
      if (!isSolidName(below) && !isRailName(below)) continue
      return true
    }
  }
  return false
}

/** Don't mine the block currently under the bot's feet. */
export function isFooting(pos, block) {
  if (!pos || !block?.position) return false
  const dx = Math.abs(pos.x - (block.position.x + 0.5))
  const dz = Math.abs(pos.z - (block.position.z + 0.5))
  const dy = pos.y - block.position.y
  return dx < 0.95 && dz < 0.95 && dy > 0.2 && dy < 2.4
}

export function assignGoal(bot, goals, point, range = 1.6, { minIntervalMs = 1400, force = false } = {}) {
  if (!bot?.pathfinder || !goals || !point) return false
  const now = Date.now()
  if (!force && alreadyThere(bot.entity?.position, point, range)) return false
  if (!force && !shouldIssueGoal({
    moving: !!bot.pathfinder.isMoving?.(),
    current: bot.qaMoveGoal,
    next: point,
    now,
    issuedAt: bot.qaMoveIssued || 0,
    minIntervalMs
  })) {
    return false
  }
  bot.pathfinder.setGoal(new goals.GoalNear(point.x, point.y, point.z, range))
  bot.qaMoveGoal = { x: point.x, y: point.y, z: point.z }
  bot.qaMoveIssued = now
  return true
}

export function forgetGoal(bot) {
  if (!bot) return
  bot.qaMoveGoal = null
  bot.qaMoveIssued = 0
}

/**
 * A real jump spends most of its time with vertical speed. Hovering with jump held
 * (or standing on a rail the physics thinks is air) should release the key.
 */
export function shouldReleaseJump({
  onGround = true,
  velocityY = 0,
  airMs = 0,
  horizontalSpeed = 0,
  support = ''
} = {}) {
  if (onGround) return false
  if (isRailName(support)) return false
  return airMs > 800 && Math.abs(velocityY) < 0.06 && horizontalSpeed < 0.08
}

export function releaseJump(bot, now = Date.now()) {
  const ent = bot?.entity
  if (!ent) return false
  const vel = ent.velocity || { x: 0, y: 0, z: 0 }
  if (ent.onGround) {
    bot.qaAirSince = 0
    return false
  }
  bot.qaAirSince = bot.qaAirSince || now
  let support = ''
  try {
    const below = bot.blockAt?.(ent.position.offset(0, -0.2, 0).floored?.() || ent.position)
    support = below?.name || (typeof below === 'string' ? below : '')
  } catch {
    support = ''
  }
  const airMs = now - bot.qaAirSince
  const horizontalSpeed = Math.hypot(vel.x || 0, vel.z || 0)
  if (isRailName(support)) {
    try {
      bot.setControlState('jump', false)
      bot.setControlState('sprint', false)
    } catch {
      /* ignore */
    }
    bot.qaAirSince = 0
    return false
  }
  const release = shouldReleaseJump({
    onGround: false,
    velocityY: vel.y || 0,
    airMs,
    horizontalSpeed,
    support
  })
  if (!release) return false
  try {
    bot.setControlState('jump', false)
    bot.setControlState('sprint', false)
  } catch {
    /* ignore */
  }
  bot.qaAirSince = 0
  bot.qaNeedRetarget = true
  return true
}

/**
 * Rails are a floor in places like Elder Rail, and the bot's feet share that block.
 * Marking them avoided makes the cell they are standing in unsafe, so pathfinder
 * never finds a walk and jump-spams instead. Keep rails out of the avoid set.
 */
export function avoidRailBlocks(movements, bot) {
  const byName = bot?.registry?.blocksByName
  if (!movements?.blocksToAvoid || !byName) return movements
  for (const name of RAILS) {
    const id = byName[name]?.id
    if (id != null) movements.blocksToAvoid.delete(id)
  }
  return movements
}

export function insideDisk(pos, home, leash) {
  if (!pos || !home || !Number.isFinite(leash)) return true
  const dx = pos.x - home.x
  const dz = pos.z - home.z
  return Math.hypot(dx, dz) <= leash
}

/** Open neighbor the bot can stand in while digging (feet position, not the bot's float height). */
export function standFeet(blockAt, x, y, z, from = null) {
  if (typeof blockAt !== 'function') return null
  const faces = [[1, 0], [-1, 0], [0, 1], [0, -1]]
  let best = null
  let bestScore = Infinity
  for (const [dx, dz] of faces) {
    for (const dy of [0, 1, -1]) {
      const fx = x + dx
      const fy = y + dy
      const fz = z + dz
      const feet = blockAt(fx, fy, fz)
      const head = blockAt(fx, fy + 1, fz)
      const below = blockAt(fx, fy - 1, fz)
      if (isSolidName(feet) || isSolidName(head)) continue
      if (!isSolidName(below) && !isRailName(below)) continue
      const sx = fx + 0.5
      const sz = fz + 0.5
      let score = Math.abs(dy) * 3
      if (from && Number.isFinite(from.x)) {
        score += Math.hypot(sx - from.x, sz - from.z)
      }
      if (score < bestScore) {
        bestScore = score
        best = { x: sx, y: fy, z: sz }
      }
    }
  }
  return best
}

export function canDigFrom(pos, blockPos, reach = 4.5, eye = 1.62) {
  if (!pos || !blockPos || !Number.isFinite(blockPos.x)) return false
  const dx = pos.x - (blockPos.x + 0.5)
  const dy = (pos.y + eye) - (blockPos.y + 0.5)
  const dz = pos.z - (blockPos.z + 0.5)
  return Math.hypot(dx, dy, dz) <= reach
}

/**
 * dig: break it from here, no path. walk: one approach to a real stand cell.
 * skip: filler across the tunnel, a sealed corner, a stand outside the leash, or a shaft.
 * Pathing at those is what spams "path to deepslate" and hops in place.
 */
export function planDig({
  pos,
  blockPos,
  stand = null,
  home = null,
  leash = 16,
  margin = 1,
  failed = false,
  footing = false,
  primary = true
} = {}) {
  if (!pos || !blockPos || failed || footing || !stand) return 'skip'
  const limit = Math.max(2, leash - margin)
  const center = {
    x: blockPos.x + (Number.isInteger(blockPos.x) ? 0.5 : 0),
    y: blockPos.y,
    z: blockPos.z + (Number.isInteger(blockPos.z) ? 0.5 : 0)
  }
  if (home && !insideDisk(stand, home, limit)) return 'skip'
  if (home && !insideDisk(center, home, limit)) return 'skip'
  if (Math.abs(stand.y - pos.y) > 2.5) return 'skip'
  if (canDigFrom(pos, blockPos) || alreadyThere(pos, stand, 1.35)) return 'dig'
  if (!primary) return 'skip'
  return 'walk'
}

/** Anchor pads sit ~16 blocks above Elder Rail. Goals must use the bot's floor, not the pad. */
export function floorY(botY, anchorY, maxDelta = 3) {
  if (!Number.isFinite(botY)) return anchorY
  if (!Number.isFinite(anchorY)) return botY
  if (Math.abs(botY - anchorY) > maxDelta) return botY
  return anchorY
}

export function pathIsStalled({ moving = false, shifted = 0, stalledMs = 0, limitMs = 900 } = {}) {
  if (!moving) return false
  return stalledMs >= limitMs && shifted < 0.4
}

export function shouldDropRailPath({ support = '', moving = false, stalledMs = 0, horizontalSpeed = 0 } = {}) {
  if (!isRailName(support) || !moving) return false
  return stalledMs > 600 && horizontalSpeed < 0.12
}

const MENU_BLOCK = new Set([
  'pathing', 'mining', 'foraging', 'digging', 'recovering', 'stuck',
  'eating', 'retreating', 'fighting', 'combat', 'catching', 'fishing'
])

export function shouldPeekMenu({ activity = 'idle', digging = false, gathering = false, moving = false } = {}) {
  if (digging || gathering || moving) return false
  if (MENU_BLOCK.has(activity)) return false
  return true
}

export function blockKey(pos) {
  if (!pos) return ''
  return `${Math.floor(pos.x)},${Math.floor(pos.y)},${Math.floor(pos.z)}`
}

export function rememberFailure(bot, pos, now = Date.now(), holdMs = 20_000) {
  if (!bot || !pos) return
  bot.qaFailedBlocks = bot.qaFailedBlocks || {}
  bot.qaFailedBlocks[blockKey(pos)] = now + holdMs
}

export function isFailedBlock(bot, pos, now = Date.now()) {
  const until = bot?.qaFailedBlocks?.[blockKey(pos)]
  return !!until && until > now
}
