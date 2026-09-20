import { applyIslandMovements, horizontalDistance, sampleSolidNear } from './safety.js'
import { jitter, note } from './util.js'

/**
 * Player-like pathing: longer think time, look where you walk, no in-place spin,
 * and do not slam a new GoalNear every tick for the same destination.
 */

export function tunePathfinder(bot, movements, opts = {}) {
  applyIslandMovements(movements, {
    canDig: opts.canDig ?? false,
    maxDrop: opts.maxDrop ?? 2
  })
  if (movements) {
    movements.allowSprinting = opts.sprint ?? (bot.qaSprint ?? false)
    movements.allowParkour = false
    movements.allow1by1towers = false
  }
  if (bot.pathfinder) {
    bot.pathfinder.thinkTimeout = opts.thinkTimeout ?? 2200
    if (Number.isFinite(bot.pathfinder.tickTimeout)) {
      bot.pathfinder.tickTimeout = Math.max(bot.pathfinder.tickTimeout, opts.tickTimeout ?? 50)
    } else {
      bot.pathfinder.tickTimeout = opts.tickTimeout ?? 50
    }
  }
  return movements
}

export function assignGait(bot) {
  bot.qaSprint = Math.random() < 0.42
}

export function sameGoal(a, b, slop = 1.5) {
  if (!a || !b) return false
  return horizontalDistance(a, b) < slop && Math.abs((a.y ?? 0) - (b.y ?? 0)) < 2.2
}

export function setNearGoal(bot, goals, dest, range = 1.4) {
  if (!bot?.pathfinder || !dest || !Number.isFinite(dest.x) || !Number.isFinite(dest.z)) return false
  const moving = typeof bot.pathfinder.isMoving === 'function' ? bot.pathfinder.isMoving() : false
  if (moving && sameGoal(bot.qaGoal, dest, Math.max(1.25, range))) {
    lookToward(bot, dest)
    return false
  }
  bot.qaGoal = { x: dest.x, y: dest.y ?? bot.entity?.position?.y, z: dest.z }
  const pos = bot.entity?.position
  bot.qaGoalDist = pos ? horizontalDistance(pos, bot.qaGoal) : null
  bot.pathfinder.setGoal(new goals.GoalNear(dest.x, dest.y ?? bot.qaGoal.y, dest.z, range))
  lookToward(bot, dest)
  return true
}

export function lookToward(bot, dest) {
  if (!bot?.entity || !dest) return
  const pos = bot.entity.position
  const dx = dest.x - pos.x
  const dz = dest.z - pos.z
  const flat = Math.hypot(dx, dz)
  if (flat < 0.15) return
  const yaw = Math.atan2(-dx, dz)
  const pitch = Math.max(-0.45, Math.min(0.6, Math.atan2((pos.y + 1.4) - ((dest.y ?? pos.y) + 0.4), flat)))
  bot.look(yaw, pitch, true).catch(() => {})
}

/** Small idle tells. Never full 360 looks while pathing (that reads as spinning). */
export function idleFidget(bot, activity = 'idle') {
  if (!bot?.entity) return
  if (typeof bot.pathfinder?.isMoving === 'function' && bot.pathfinder.isMoving()) return
  const roll = Math.random()
  try {
    if (roll < 0.28) {
      bot.setControlState('jump', true)
      setTimeout(() => bot.setControlState('jump', false), jitter(180, 0.3))
      note(bot, 'idle hop', activity)
    } else if (roll < 0.55) {
      bot.swingArm()
      note(bot, 'idle swing', activity)
    } else {
      const yaw = (bot.entity.yaw || 0) + (Math.random() - 0.5) * 0.7
      const pitch = Math.max(-0.35, Math.min(0.35, (bot.entity.pitch || 0) + (Math.random() - 0.5) * 0.25))
      bot.look(yaw, pitch, true).catch(() => {})
      note(bot, 'glance', activity)
    }
  } catch {
    /* ignore */
  }
}

export function isSpinning(bot) {
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
  return moved < 0.5 && yawTravel > Math.PI * 1.55
}

export function breakSpin(bot) {
  if (!isSpinning(bot)) return false
  try {
    if (bot.pathfinder) bot.pathfinder.setGoal(null)
  } catch { /* ignore */ }
  try { bot.clearControlStates() } catch { /* ignore */ }
  bot.qaNeedNewGoal = true
  bot.qaGoal = null
  bot.qaGoalDist = null
  bot.qaYawSamples = []
  note(bot, 'break spin', 'idle')
  return true
}

export function wanderIfIdle(bot, home, radius, goals) {
  if (!bot?.pathfinder || !home || !goals) return false
  if (typeof bot.pathfinder.isMoving === 'function' && bot.pathfinder.isMoving()) return false
  const target = sampleSolidNear(bot, home, Math.max(2, radius))
  if (!target) return false
  return setNearGoal(bot, goals, target, 1)
}

export function followIfNeeded(bot, goals, entity, range = 1.8) {
  if (!bot?.pathfinder || !entity?.position || !goals) return false
  const id = entity.id
  const moving = typeof bot.pathfinder.isMoving === 'function' ? bot.pathfinder.isMoving() : false
  if (moving && bot.qaFollowId === id) {
    lookToward(bot, entity.position)
    return false
  }
  bot.qaFollowId = id
  bot.qaGoal = { x: entity.position.x, y: entity.position.y, z: entity.position.z }
  bot.pathfinder.setGoal(new goals.GoalFollow(entity, range), true)
  lookToward(bot, entity.position)
  return true
}
