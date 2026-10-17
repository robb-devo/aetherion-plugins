import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, cancelPath, standingIsSafe, withinLeash, wanderOnIsland } from './safety.js'
import { fidget, markError, note } from './util.js'
import { maybeSkip } from './playstyle.js'
import { shouldYield } from './mind.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/**
 * Best-effort catch: throw spheres from a solid habitat pad.
 * Does not chase pets off the island or play the catch timing minigame.
 */
export function createCatchLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const searchRadius = cfg.searchRadius ?? 10
  const throwCooldownMs = cfg.throwCooldownMs ?? 1800
  const wanderRadius = cfg.wanderRadius ?? 5
  const leash = cfg.leashRadius ?? bot.qaLeash ?? 12
  let running = false
  let lastThrow = 0

  function home() {
    return bot.qaHome || bot.entity?.position
  }

  function onPad() {
    const pos = bot.entity.position
    return standingIsSafe(bot, pos.x, pos.y, pos.z)
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
    if (shouldYield(bot)) return
    if (!home()) {
      bot.qaHome = { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
    }

    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), {
        canDig: false,
        maxDrop: cfg.maxDrop ?? 1
      }))
    }

    equipSphere(bot)

    const target = nearestCatchable(bot, searchRadius, home(), leash)
    if (!target) {
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'idle'
      wanderOnIsland(bot, home(), wanderRadius, goals)
      return
    }

    const dist = bot.entity.position.distanceTo(target.position)
    bot.lookAt(target.position.offset(0, target.height * 0.6, 0), true).catch(() => {})

    if (dist > 5.5) {
      if (!withinLeash(target.position, home(), leash)) {
        note(bot, 'pet outside leash — throw from pad', 'catching')
        bot.pathfinder.setGoal(null)
      } else {
        note(bot, `approach ${target.name || 'entity'}`, 'pathing')
        bot.pathfinder.setGoal(new goals.GoalFollow(target, 2.5), true)
        return
      }
    }

    if (!onPad()) {
      cancelPath(bot)
      wanderOnIsland(bot, home(), Math.min(3, wanderRadius), goals)
      note(bot, 'return to habitat pad', 'recovering')
      return
    }

    bot.pathfinder.setGoal(null)
    const now = Date.now()
    const cooldown = throwCooldownMs * (0.75 + (bot.qaPersona?.patience ?? 0.5) * 0.7)
    if (now - lastThrow < cooldown) {
      note(bot, 'catch cooldown', 'catching')
      return
    }
    lastThrow = now
    if (maybeSkip(0.14)) {
      note(bot, 'hesitate throw', 'catching')
      fidget(bot, 'catching')
      return
    }
    try {
      note(bot, `throw sphere at ${target.name || 'entity'}`, 'catching')
      await bot.activateItem()
      bot.qaMinigame = bot.qaMinigame || {}
      bot.qaMinigame.biteUntil = Date.now() + 2200
      note(bot, 'catch timing window', 'minigame')
    } catch (err) {
      markError(bot, err)
    }
  }

  return function start() {
    if (running) return
    running = true
    if (bot.entity?.position) {
      bot.qaHome = bot.qaHome || { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
    }
    log(bot.stressName, 'catch loop start (pad throws + timing clicks)')
    note(bot, 'catch loop start', 'catching')
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `catch tick: ${err.message}`)
      })
    }, 300)
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

function nearestCatchable(bot, radius, home, leash) {
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities)) {
    if (!entity || entity === bot.entity) continue
    if (entity.type === 'player' || entity.type === 'orb' || entity.type === 'object') continue
    const name = (entity.name || entity.displayName || '').toLowerCase()
    if (!name) continue
    if (name.includes('item') || name.includes('experience') || name.includes('armor_stand')) continue
    if (name.includes('villager') || name.includes('iron_golem')) continue
    if (entity.position.y < bot.entity.position.y - 4) continue
    if (!withinLeash(entity.position, home, leash + 4)) continue
    const dist = bot.entity.position.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}
