import pathfinderPkg from 'mineflayer-pathfinder'
import { equipMatching, jitter } from './util.js'

const { goals } = pathfinderPkg

const PEACEFUL = /villager|iron_golem|armor_stand|item|experience|wandering_trader|allay|snow_golem/

/** Hostile mobs only (never players, never NPC villagers). */
export function isHostile(entity) {
  if (!entity || entity.type === 'player') return false
  if (entity.type !== 'hostile' && entity.type !== 'mob') return false
  return !PEACEFUL.test(String(entity.name || '').toLowerCase())
}

export function nearestHostile(bot, radius) {
  const me = bot.entity?.position
  if (!me) return null
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities || {})) {
    if (!entity?.position || entity === bot.entity || !isHostile(entity)) continue
    const dist = me.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}

/**
 * One step of a fight: pick the closest hostile in {@code radius}, chase it, swing on a human rhythm.
 * {@code state} keeps the last swing time between calls. True while there is something to fight.
 */
export async function fightStep(bot, state, { radius = 16, attackMs = 480 } = {}) {
  const target = nearestHostile(bot, radius)
  if (!target) return false
  const dist = bot.entity.position.distanceTo(target.position)
  bot.lookAt(target.position.offset(0, (target.height || 1.8) * 0.8, 0), true).catch(() => {})
  await equipMatching(bot, (item) => /_(sword|axe)$/.test(item.name))
  if (dist > 2.8) {
    bot.pathfinder?.setGoal(new goals.GoalFollow(target, 1.6), true)
    return true
  }
  bot.pathfinder?.setGoal(null)
  const now = Date.now()
  if (now - (state.lastAttack || 0) >= jitter(attackMs, 0.2)) {
    state.lastAttack = now
    try {
      bot.attack(target)
      bot.qaStats = bot.qaStats || {}
      bot.qaStats.hits = (bot.qaStats.hits || 0) + 1
    } catch {
      /* gone */
    }
  }
  return true
}
