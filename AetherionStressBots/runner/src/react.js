import pathfinderPkg from 'mineflayer-pathfinder'
import { cancelPath, wanderOnIsland } from './safety.js'
import { inventoryAlmostFull, note, tossJunk } from './util.js'

const { goals } = pathfinderPkg

const HOSTILE = ['zombie', 'husk', 'skeleton', 'stray', 'creeper', 'spider', 'drowned', 'witch', 'pillager', 'phantom', 'vex', 'pillager']
const BUSY = new Set(['ah', 'bazaar', 'quest_dialog', 'minigame', 'trading', 'fishing'])

/**
 * Shared survival layer: collect drops, flee or fight nearby hostiles,
 * dump junk when full. Returns true when the role tick should wait.
 */
export function tickSurvival(bot) {
  if (!bot.entity || bot.qaSuspended) return false
  const personality = bot.qaPersonality
  const activity = bot.qaActivity || 'idle'
  if (BUSY.has(activity) || bot.qaDigging || bot.currentWindow) return false

  if (inventoryAlmostFull(bot)) {
    tossJunk(bot)
    note(bot, 'inventory full — toss', activity)
  }

  const drop = nearestDrop(bot, 3.2)
  if (drop && !bot.pathfinder?.isMoving?.()) {
    bot.qaTarget = drop.name || 'drop'
    note(bot, `collect ${drop.name || 'item'}`, 'pathing')
    try {
      bot.pathfinder?.setGoal(new goals.GoalNear(drop.position.x, drop.position.y, drop.position.z, 1))
    } catch {
      /* ignore */
    }
    return true
  }

  const hostile = nearestHostile(bot, personality?.aggression > 0.65 ? 10 : 7)
  if (!hostile) {
    if (bot.qaTarget && !String(bot.qaLastAction || '').includes(bot.qaTarget)) {
      bot.qaTarget = ''
    }
    return false
  }

  bot.qaTarget = hostile.name || 'mob'
  const fight = (personality?.aggression ?? 0.4) > 0.55 || activity === 'combat' || activity === 'fighting'
  const dist = bot.entity.position.distanceTo(hostile.position)
  if (fight && activity !== 'catching') {
    note(bot, `react fight ${hostile.name || 'mob'}`, 'combat')
    bot.lookAt(hostile.position.offset(0, hostile.height * 0.8, 0), true).catch(() => {})
    if (dist <= 3) {
      try { bot.attack(hostile) } catch { /* ignore */ }
    } else if (bot.pathfinder) {
      try { bot.pathfinder.setGoal(new goals.GoalFollow(hostile, 1.6), true) } catch { /* ignore */ }
    }
    return true
  }

  note(bot, `react flee ${hostile.name || 'mob'}`, 'recovering')
  cancelPath(bot)
  wanderOnIsland(bot, bot.qaHome || bot.entity.position, 4, goals)
  return true
}

export function nearestHostile(bot, radius) {
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities || {})) {
    if (!entity || entity === bot.entity) continue
    const name = (entity.name || entity.displayName || '').toLowerCase()
    if (!name) continue
    if (!HOSTILE.some((key) => name.includes(key))) continue
    const dist = bot.entity.position.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}

function nearestDrop(bot, radius) {
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities || {})) {
    if (!entity || entity === bot.entity) continue
    if (entity.name !== 'item' && entity.type !== 'object') continue
    if (!entity.position) continue
    const dist = bot.entity.position.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}

export function equipForActivity(bot, activity) {
  const want = {
    mine: ['pickaxe'],
    forage: ['axe'],
    combat: ['sword'],
    fish: ['fishing_rod'],
    catch: ['snowball', 'ender_pearl', 'nugget', 'heart_of_the_sea', 'nether_star'],
    trade: ['sword', 'pickaxe', 'log', 'coal', 'cobble'],
    roam: ['sword'],
    quest: ['sword'],
    pad: ['sword']
  }[activity] || []
  if (want.length === 0) return false
  const held = bot.heldItem?.name?.toLowerCase() || ''
  if (want.some((key) => held.includes(key))) return true
  const item = (bot.inventory.items() || []).find((it) => {
    const n = (it.name || '').toLowerCase()
    return want.some((key) => n.includes(key))
  })
  if (!item) return false
  bot.equip(item, 'hand').catch(() => {})
  return true
}
