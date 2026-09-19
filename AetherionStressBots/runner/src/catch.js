import pathfinderPkg from 'mineflayer-pathfinder'
import { markError, note, wanderNear } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/**
 * Best-effort catch: path to nearby living entities and use the held sphere.
 * Does not play the catch timing minigame — pets may flee / not complete.
 */
export function createCatchLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const searchRadius = cfg.searchRadius ?? 24
  const throwCooldownMs = cfg.throwCooldownMs ?? 2500
  let running = false
  let lastThrow = 0
  let home = null

  async function tick() {
    if (!bot.entity) return
    if (!home) home = bot.entity.position.clone()

    if (!bot.pathfinder.movements) {
      const movements = new Movements(bot)
      movements.allowSprinting = true
      movements.canDig = false
      bot.pathfinder.setMovements(movements)
    }

    equipSphere(bot)

    const target = nearestCatchable(bot, searchRadius)
    if (!target) {
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'idle'
      wanderNear(bot, home, cfg.wanderRadius ?? 16, goals)
      return
    }

    const dist = bot.entity.position.distanceTo(target.position)
    bot.lookAt(target.position.offset(0, target.height * 0.6, 0), true).catch(() => {})
    if (dist > 4.5) {
      note(bot, `approach ${target.name || 'entity'}`, 'pathing')
      bot.pathfinder.setGoal(new goals.GoalFollow(target, 2.2), true)
      return
    }
    bot.pathfinder.setGoal(null)
    const now = Date.now()
    if (now - lastThrow < throwCooldownMs) {
      note(bot, 'catch cooldown', 'catching')
      return
    }
    lastThrow = now
    try {
      note(bot, `throw sphere at ${target.name || 'entity'}`, 'catching')
      await bot.activateItem()
    } catch (err) {
      markError(bot, err)
    }
  }

  return function start() {
    if (running) return
    running = true
    home = bot.entity?.position?.clone() ?? null
    log(bot.stressName, 'catch loop start (best-effort throws; minigame not automated)')
    note(bot, 'catch loop start', 'catching')
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `catch tick: ${err.message}`)
      })
    }, 350)
    bot.once('end', () => clearInterval(handle))
  }
}

function equipSphere(bot) {
  const held = bot.heldItem
  if (held && isSphere(held)) return
  const sphere = bot.inventory.items().find(isSphere)
  if (!sphere) return
  bot.equip(sphere, 'hand').catch(() => {})
}

function isSphere(item) {
  if (!item || !item.name) return false
  const n = item.name.toLowerCase()
  return n.includes('snowball') || n.includes('ender_pearl') || n.includes('nugget') || n.includes('heart_of_the_sea') || n.includes('nether_star')
}

function nearestCatchable(bot, radius) {
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities)) {
    if (!entity || entity === bot.entity) continue
    if (entity.type === 'player' || entity.type === 'orb' || entity.type === 'object') continue
    const name = (entity.name || entity.displayName || '').toLowerCase()
    if (!name) continue
    if (name.includes('item') || name.includes('experience') || name.includes('armor_stand')) continue
    if (name.includes('villager') || name.includes('iron_golem')) continue
    if (entity.position.y < bot.entity.position.y - 8) continue
    const dist = bot.entity.position.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}
