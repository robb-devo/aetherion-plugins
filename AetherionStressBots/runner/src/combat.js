import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, wanderOnIsland, withinLeash } from './safety.js'
import { fidget, jitter, note } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

export function createCombatLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const attackIntervalMs = cfg.attackIntervalMs ?? 450
  const searchRadius = cfg.searchRadius ?? 28
  const wanderRadius = cfg.wanderRadius ?? 10
  const leash = cfg.leashRadius ?? bot.qaLeash ?? 22

  let running = false
  let lastAttack = 0
  let lastFidget = 0

  function home() {
    return bot.qaHome || bot.entity?.position
  }

  async function tick() {
    if (!bot.entity || bot.entity.isValid === false || bot.qaSuspended) return

    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), {
        canDig: false,
        maxDrop: cfg.maxDrop ?? 3
      }))
    }

    const pad = home()
    const target = nearestHostile(bot, searchRadius, pad, leash)
    if (target) {
      const dist = bot.entity.position.distanceTo(target.position)
      note(bot, `combat ${target.name || 'mob'}`, 'fighting')
      bot.lookAt(target.position.offset(0, target.height * 0.85, 0), true).catch(() => {})
      if (dist > 2.6) {
        bot.pathfinder.setGoal(new goals.GoalFollow(target, 1.6), true)
      } else {
        bot.pathfinder.setGoal(null)
        const now = Date.now()
        if (now - lastAttack >= jitter(attackIntervalMs, 0.2)) {
          lastAttack = now
          try {
            bot.attack(target)
          } catch {
            // entity may despawn mid-swing
          }
        }
      }
      return
    }

    bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'idle'
    if (!bot.pathfinder.isMoving()) {
      wanderOnIsland(bot, pad, Math.min(12, wanderRadius), goals)
    }
    if (Date.now() - lastFidget > 4000) {
      lastFidget = Date.now()
      fidget(bot, bot.qaActivity)
    }
  }

  return function start() {
    if (running) return
    running = true
    if (bot.entity?.position && !bot.qaHome) {
      bot.qaHome = { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
    }
    log(bot.stressName, 'combat loop start')
    note(bot, 'combat loop start', 'fighting')
    const handle = setInterval(() => {
      tick().catch((err) => log(bot.stressName, `combat tick: ${err.message}`))
    }, jitter(250, 0.15))
    bot.once('end', () => clearInterval(handle))
  }
}

function nearestHostile(bot, radius, home, leash) {
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities)) {
    if (!entity || entity === bot.entity) continue
    if (entity.type !== 'mob' && entity.type !== 'hostile') continue
    const name = (entity.name || entity.displayName || '').toLowerCase()
    if (name.includes('villager') || name.includes('iron_golem') || name.includes('armor_stand')) continue
    if (name.includes('item') || name.includes('experience')) continue
    if (home && !withinLeash(entity.position, home, leash + 4)) continue
    const dist = bot.entity.position.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}
