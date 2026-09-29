import Vec3 from 'vec3'
import { note } from './util.js'

/**
 * Island leash + void hold for Skyblock-style pads.
 * The Paper plugin teleports bots back; this module stops walking off edges.
 */

export function horizontalDistance(a, b) {
  if (!a || !b) return Infinity
  const dx = a.x - b.x
  const dz = a.z - b.z
  return Math.hypot(dx, dz)
}

export function isBelowFloor(pos, floorY = 40) {
  return !pos || !Number.isFinite(pos.y) || pos.y < floorY
}

export function nearestAnchor(pos, anchors) {
  if (!pos || !anchors || anchors.length === 0) return null
  let best = null
  let bestDist = Infinity
  for (const anchor of anchors) {
    if (!anchor || !Number.isFinite(anchor.x) || !Number.isFinite(anchor.z)) continue
    const dist = horizontalDistance(pos, anchor)
    if (dist < bestDist) {
      best = anchor
      bestDist = dist
    }
  }
  return best
}

export function withinLeash(pos, home, leash) {
  if (!pos || !home || !Number.isFinite(leash)) return true
  return horizontalDistance(pos, home) <= leash
}

export function clampToLeash(from, home, leash) {
  if (!from || !home) return from
  const dist = horizontalDistance(from, home)
  if (dist <= leash || dist < 0.001) {
    return { x: from.x, y: from.y ?? home.y, z: from.z }
  }
  const scale = leash / dist
  return {
    x: home.x + (from.x - home.x) * scale,
    y: from.y ?? home.y,
    z: home.z + (from.z - home.z) * scale
  }
}

export function readAnchors(cfg) {
  const raw = cfg?.anchors || cfg?.waypoints || []
  return raw
    .map((p) => ({
      x: Number(p.x),
      y: Number(p.y),
      z: Number(p.z)
    }))
    .filter((p) => Number.isFinite(p.x) && Number.isFinite(p.y) && Number.isFinite(p.z))
}

export function applyIslandMovements(movements, { canDig = false, maxDrop = 2 } = {}) {
  if (!movements) return movements
  movements.allowSprinting = true
  movements.canDig = canDig
  movements.allow1by1towers = false
  movements.allowParkour = false
  if ('maxDropDown' in movements) {
    movements.maxDropDown = maxDrop
  }
  if ('infiniteLiquidDropdownDistance' in movements) {
    movements.infiniteLiquidDropdownDistance = false
  }
  return movements
}

export function cancelPath(bot) {
  try {
    if (bot.pathfinder) {
      bot.pathfinder.setGoal(null)
    }
  } catch {
    /* ignore */
  }
  try {
    bot.clearControlStates()
  } catch {
    /* ignore */
  }
}

export function setGoal(bot, goal) {
  if (!bot) return
  if (!goal || !Number.isFinite(goal.x) || !Number.isFinite(goal.z)) {
    bot.qaGoal = null
    bot.qaGoalDist = null
    return
  }
  bot.qaGoal = { x: goal.x, y: goal.y, z: goal.z }
  const pos = bot.entity?.position
  bot.qaGoalDist = pos ? horizontalDistance(pos, bot.qaGoal) : null
}

export function goalProgress(pos, goal, previousDist) {
  if (!pos || !goal) return { progressed: false, dist: previousDist }
  const dist = horizontalDistance(pos, goal)
  if (!Number.isFinite(previousDist)) {
    return { progressed: true, dist }
  }
  return { progressed: dist < previousDist - 0.25, dist }
}

/**
 * Dig/path-to-block often looks motionless (tree approach, looking at a log, breaking).
 * Cancel only when there is no movement AND no progress toward the dig target.
 */
export function stuckTimeoutMs({ digging = false, gathering = false, stuckMs = 10_000, digStuckMs = 28_000, gatherStuckMs = 18_000 } = {}) {
  if (digging) return digStuckMs
  if (gathering) return gatherStuckMs
  return stuckMs
}

export function shouldCancelStuck({
  moved = false,
  progressedTowardGoal = false,
  digging = false,
  frozenMs = 0,
  stuckMs = 10_000,
  digStuckMs = 28_000,
  gatherStuckMs = 18_000,
  gathering = false
} = {}) {
  if (moved || progressedTowardGoal) return false
  if (digging && frozenMs < (digStuckMs ?? 28_000)) return false
  const limit = stuckTimeoutMs({ digging, gathering, stuckMs, digStuckMs, gatherStuckMs })
  return frozenMs > limit
}

export function blockIsSolid(block) {
  if (!block || !block.name) return false
  const name = block.name.toLowerCase()
  if (name === 'air' || name === 'cave_air' || name === 'void_air') return false
  if (name.includes('water') || name.includes('lava') || name.includes('bubble')) return false
  if (typeof block.boundingBox === 'string' && block.boundingBox === 'empty') return false
  return true
}

export function standingIsSafe(bot, x, y, z) {
  if (!bot?.blockAt) return false
  const at = (ax, ay, az) => bot.blockAt(new Vec3(Math.floor(ax), Math.floor(ay), Math.floor(az)))
  const feet = at(x, y, z)
  const head = at(x, y + 1, z)
  const below = at(x, y - 1, z)
  if (feet && blockIsSolid(feet)) return false
  if (head && blockIsSolid(head)) return false
  return blockIsSolid(below)
}

export function sampleSolidNear(bot, home, radius, attempts = 12) {
  if (!bot?.entity || !home) return null
  const r = Math.max(2, radius)
  for (let i = 0; i < attempts; i++) {
    const angle = Math.random() * Math.PI * 2
    const dist = Math.sqrt(Math.random()) * r
    const x = home.x + Math.cos(angle) * dist
    const z = home.z + Math.sin(angle) * dist
    const y = home.y
    for (const dy of [0, 1, -1, 2, -2, 3, -3]) {
      if (standingIsSafe(bot, x, y + dy, z)) {
        return { x, y: y + dy, z }
      }
    }
  }
  return { x: home.x, y: home.y, z: home.z }
}

export function wanderOnIsland(bot, home, radius, goals) {
  if (!bot.pathfinder || bot.pathfinder.isMoving()) return false
  const target = sampleSolidNear(bot, home, radius)
  if (!target) return false
  bot.pathfinder.setGoal(new goals.GoalNear(target.x, target.y, target.z, 1))
  return true
}

export function maybeBreakSpin(bot) {
  const entity = bot?.entity
  if (!entity) return false
  const pos = entity.position
  const yaw = entity.yaw
  if (yaw == null || !pos) return false
  const samples = bot.qaYawSamples || (bot.qaYawSamples = [])
  samples.push({ t: Date.now(), yaw, x: pos.x, z: pos.z })
  if (samples.length > 8) samples.shift()
  if (samples.length < 6) return false
  const moved = Math.hypot(pos.x - samples[0].x, pos.z - samples[0].z)
  let yawTravel = 0
  for (let i = 1; i < samples.length; i++) {
    let delta = Math.abs(samples[i].yaw - samples[i - 1].yaw)
    if (delta > Math.PI) delta = Math.PI * 2 - delta
    yawTravel += delta
  }
  if (moved >= 0.5 || yawTravel <= Math.PI * 1.55) return false
  cancelPath(bot)
  bot.qaNeedNewGoal = true
  bot.qaGoal = null
  bot.qaGoalDist = null
  bot.qaYawSamples = []
  note(bot, 'break spin', 'idle')
  return true
}

export function resolveHome(bot, anchors) {
  const pos = bot.entity?.position
  if (!pos) return null
  const nearest = nearestAnchor(pos, anchors)
  if (nearest && horizontalDistance(pos, nearest) < 48) {
    return { x: nearest.x, y: nearest.y, z: nearest.z }
  }
  return { x: pos.x, y: pos.y, z: pos.z }
}

export function attachSafety(bot, opts = {}) {
  const voidFloorY = opts.voidFloorY ?? 40
  const leash = opts.leashRadius ?? 16
  const anchors = opts.anchors || []
  const log = opts.log || (() => {})

  bot.qaHome = bot.qaHome || null
  bot.qaAnchors = anchors
  bot.qaLeash = leash
  bot.qaVoidFloorY = voidFloorY
  bot.qaSuspended = !!bot.qaSuspended
  bot.qaLastSafe = null
  bot.qaLastMoved = null
  bot.qaStuckSince = 0
  bot.qaNeedRetarget = false
  bot.qaGoal = bot.qaGoal || null
  bot.qaGoalDist = null

  const hold = (reason, activity) => {
    cancelPath(bot)
    bot.qaSuspended = true
    note(bot, reason, activity)
  }

  const resumeIfSafe = (pos) => {
    if (!bot.qaSuspended) return
    if (!pos || isBelowFloor(pos, voidFloorY)) return
    if ((bot.health ?? 0) <= 0) return
    bot.qaHome = resolveHome(bot, anchors)
    bot.qaLastSafe = { x: pos.x, y: pos.y, z: pos.z }
    bot.qaSuspended = false
    note(bot, 'resumed after recover', 'idle')
  }

  const tick = () => {
    const pos = bot.entity?.position
    if (!pos) return

    if ((bot.health ?? 1) <= 0) {
      hold('dead — wait for respawn', 'recovering')
      return
    }

    if (isBelowFloor(pos, voidFloorY)) {
      hold('void floor — hold for plugin recover', 'void')
      return
    }

    if (bot.qaLastSafe && (horizontalDistance(pos, bot.qaLastSafe) > 24 || Math.abs(pos.y - bot.qaLastSafe.y) > 8)) {
      cancelPath(bot)
      bot.qaHome = resolveHome(bot, anchors)
      bot.qaLastSafe = { x: pos.x, y: pos.y, z: pos.z }
      note(bot, `re-anchored @ ${pos.x.toFixed(1)} ${pos.y.toFixed(1)} ${pos.z.toFixed(1)}`, 'recovering')
    }

    resumeIfSafe(pos)

    if (!bot.qaHome) {
      bot.qaHome = resolveHome(bot, anchors)
    }

    if (bot.qaHome && !withinLeash(pos, bot.qaHome, leash + 3) && bot.qaActivity !== 'pad_hop') {
      cancelPath(bot)
      bot.qaNeedRetarget = true
      bot.qaGoal = null
      bot.qaGoalDist = null
      note(bot, 'leash pull', 'recovering')
      if (bot.pathfinder && !bot.qaSuspended) {
        const pull = clampToLeash(pos, bot.qaHome, Math.max(2, leash * 0.35))
        try {
          const goals = opts.goals
          if (goals) {
            bot.pathfinder.setGoal(new goals.GoalNear(pull.x, bot.qaHome.y, pull.z, 1))
          }
        } catch {
          /* ignore */
        }
      }
    }

    if (!bot.qaLastSafe || horizontalDistance(pos, bot.qaLastSafe) > 0.8 || Math.abs(pos.y - bot.qaLastSafe.y) > 1.5) {
      bot.qaLastSafe = { x: pos.x, y: pos.y, z: pos.z }
    }

    const moved = !bot.qaLastMoved || pos.distanceTo(bot.qaLastMoved) > 0.35
    const progress = goalProgress(pos, bot.qaGoal, bot.qaGoalDist)
    if (progress.dist != null) bot.qaGoalDist = progress.dist
    const digging = !!bot.qaDigging
    const gathering = !!bot.qaGathering || bot.qaActivity === 'foraging' || bot.qaActivity === 'mining'
      || bot.qaActivity === 'farming' || bot.qaActivity === 'fishing'
    if (bot.qaActivity !== 'pad_hop') {
      maybeBreakSpin(bot)
    }
    const working = digging || gathering || !!bot.targetDigBlock
      || ['mining', 'foraging', 'farming', 'pathing', 'catching', 'fishing', 'ah', 'bazaar', 'quest_dialog', 'minigame', 'pad_hop', 'combat', 'trading', 'fighting']
        .includes(bot.qaActivity)
      || bot.pathfinder?.isMoving?.()
    if (moved || progress.progressed) {
      bot.qaStuckSince = 0
      bot.qaIdleSince = 0
      bot.qaLastMoved = { x: pos.x, y: pos.y, z: pos.z }
    } else if (!bot.qaSuspended) {
      bot.qaStuckSince = bot.qaStuckSince || Date.now()
      const frozenMs = Date.now() - bot.qaStuckSince
      const keepJob = working && ['ah', 'bazaar', 'quest_dialog', 'minigame', 'pad_hop'].includes(bot.qaActivity)
      if (shouldCancelStuck({
        moved: false,
        progressedTowardGoal: false,
        digging,
        gathering,
        frozenMs,
        stuckMs: opts.stuckMs ?? 10_000,
        digStuckMs: opts.digStuckMs ?? 28_000,
        gatherStuckMs: opts.gatherStuckMs ?? 18_000
      })) {
        bot.qaStuckSince = Date.now()
        if (keepJob) {
          note(bot, 'stuck while working — keep job', bot.qaActivity || 'stuck')
        } else {
          cancelPath(bot)
          bot.qaNeedRetarget = true
          bot.qaNeedNewGoal = true
          bot.qaGoal = null
          bot.qaGoalDist = null
          note(bot, working ? 'stuck — cancel and retarget' : 'stuck — cancel path', working ? 'idle' : 'stuck')
          const pad = bot.qaHome
          if (pad && opts.goals && !bot.qaSuspended) {
            wanderOnIsland(bot, pad, Math.min(6, Math.max(3, (opts.leashRadius ?? 16) * 0.35)), opts.goals)
          }
        }
      }
      if (!working) {
        bot.qaIdleSince = bot.qaIdleSince || Date.now()
        if (Date.now() - bot.qaIdleSince > (opts.idleGoalMs ?? 8000)) {
          bot.qaNeedNewGoal = true
          bot.qaIdleSince = Date.now()
          note(bot, 'idle too long — new goal', 'idle')
        }
      } else {
        bot.qaIdleSince = 0
      }
    }
  }

  const handle = setInterval(() => {
    try {
      tick()
    } catch (err) {
      log(bot.stressName || 'bot', `safety: ${err.message}`)
    }
  }, 250)

  bot.on('death', () => {
    hold('died — wait for plugin re-anchor', 'recovering')
  })
  bot.on('spawn', () => {
    hold('spawn — wait for plugin teleport', 'recovering')
    setTimeout(() => {
      const pos = bot.entity?.position
      if (pos && !isBelowFloor(pos, voidFloorY)) {
        bot.qaHome = resolveHome(bot, anchors)
        bot.qaLastSafe = { x: pos.x, y: pos.y, z: pos.z }
        bot.qaSuspended = false
        note(bot, `home @ ${pos.x.toFixed(1)} ${pos.y.toFixed(1)} ${pos.z.toFixed(1)}`, 'idle')
      }
    }, opts.resumeDelayMs ?? 2800)
  })
  bot.once('end', () => clearInterval(handle))

  return { tick, cancel: () => clearInterval(handle) }
}
