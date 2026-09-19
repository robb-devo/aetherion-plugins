import pathfinderPkg from 'mineflayer-pathfinder'
import { markError, note } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/**
 * Walk configured waypoints (capital/hub pads) with occasional jump/look/swing.
 */
export function createRoamLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const waypoints = (cfg.waypoints || []).map((p) => ({
    x: Number(p.x),
    y: Number(p.y),
    z: Number(p.z)
  })).filter((p) => Number.isFinite(p.x) && Number.isFinite(p.y) && Number.isFinite(p.z))

  let running = false
  let index = 0
  let lastFidget = 0

  async function tick() {
    if (!bot.entity) return

    if (!bot.pathfinder.movements) {
      const movements = new Movements(bot)
      movements.allowSprinting = true
      movements.canDig = false
      bot.pathfinder.setMovements(movements)
    }

    if (waypoints.length === 0) {
      note(bot, 'roam idle (no waypoints)', 'idle')
      fidget(bot)
      return
    }

    const target = waypoints[index % waypoints.length]
    const dist = Math.hypot(bot.entity.position.x - target.x, bot.entity.position.z - target.z)
    if (dist < 3.5) {
      index = (index + 1) % waypoints.length
      note(bot, `reached roam wp ${index}`, 'roaming')
      fidget(bot)
      return
    }

    if (!bot.pathfinder.isMoving()) {
      note(bot, `walk to ${target.x.toFixed(0)} ${target.z.toFixed(0)}`, 'pathing')
      bot.pathfinder.setGoal(new goals.GoalNear(target.x, target.y, target.z, 2))
    } else {
      bot.qaActivity = 'pathing'
    }

    if (Date.now() - lastFidget > 8000) {
      lastFidget = Date.now()
      fidget(bot)
    }
  }

  function fidget(botRef) {
    const roll = Math.random()
    try {
      if (roll < 0.33) {
        botRef.setControlState('jump', true)
        setTimeout(() => botRef.setControlState('jump', false), 250)
        note(botRef, 'jump', 'roaming')
      } else if (roll < 0.66) {
        botRef.swingArm()
        note(botRef, 'swing', 'roaming')
      } else {
        botRef.look(Math.random() * Math.PI * 2, (Math.random() - 0.5) * 0.4, true).catch(() => {})
        note(botRef, 'look around', 'roaming')
      }
    } catch (err) {
      markError(botRef, err)
    }
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, `roam loop start (${waypoints.length} waypoints)`)
    note(bot, 'roam loop start', 'roaming')
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `roam tick: ${err.message}`)
      })
    }, 500)
    bot.once('end', () => clearInterval(handle))
  }
}
