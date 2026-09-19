import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, wanderOnIsland } from './safety.js'
import { note } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

export function createCombatLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const attackIntervalMs = cfg.attackIntervalMs ?? 450
  const searchRadius = cfg.searchRadius ?? 28
  const wanderRadius = cfg.wanderRadius ?? 22

  let running = false
  let lastAttack = 0
  let home = null

  async function tick() {
    if (!bot.entity || bot.entity.isValid === false || bot.qaSuspended) return
    if (!home) home = bot.entity.position.clone()

    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), {
        canDig: false,
        maxDrop: cfg.maxDrop ?? 3
      }))
    }

    const target = nearestHostile(bot, searchRadius)
    if (target) {
      const dist = bot.entity.position.distanceTo(target.position)
      note(bot, `combat ${target.name || 'mob'}`, 'pathing')
      bot.lookAt(target.position.offset(0, target.height * 0.85, 0), true).catch(() => {})
      if (dist > 2.6) {
        bot.pathfinder.setGoal(new goals.GoalFollow(target, 1.6), true)
      } else {
        bot.pathfinder.setGoal(null)
        const now = Date.now()
        if (now - lastAttack >= attackIntervalMs) {
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
      wanderOnIsland(bot, home, Math.min(12, wanderRadius), goals)
    }
  }

  return function start() {
    if (running) return
    running = true
    home = bot.entity?.position?.clone() ?? null
    log(bot.stressName, 'combat loop start')
    const handle = setInterval(() => {
      tick().catch((err) => log(bot.stressName, `combat tick: ${err.message}`))
    }, 250)
    bot.once('end', () => clearInterval(handle))
  }
}

function nearestHostile(bot, radius) {
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities)) {
    if (!entity || entity === bot.entity) continue
    if (entity.type !== 'mob' && entity.type !== 'hostile') continue
    // Skip obvious friendlies / ambient
    const name = (entity.name || entity.displayName || '').toLowerCase()
    if (name.includes('villager') || name.includes('iron_golem') || name.includes('armor_stand')) continue
    if (name.includes('item') || name.includes('experience')) continue
    const dist = bot.entity.position.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}
