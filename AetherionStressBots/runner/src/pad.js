import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, cancelPath, horizontalDistance, nearestAnchor, wanderOnIsland } from './safety.js'
import { fidget, jitter, markError, note } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/**
 * Hop between known Hub/island jump-pad coords. Distant pads are plugin teleports.
 */
export function createPadLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const pads = (cfg.pads || cfg.waypoints || cfg.anchors || []).map((p) => ({
    x: Number(p.x),
    y: Number(p.y),
    z: Number(p.z)
  })).filter((p) => Number.isFinite(p.x) && Number.isFinite(p.y) && Number.isFinite(p.z))

  const maxHop = cfg.maxHop ?? 8
  const hopRadius = cfg.wanderRadius ?? 4
  let running = false
  let lastHop = 0
  let lastFidget = 0
  let cursor = 0

  function home() {
    if (bot.qaHome) return bot.qaHome
    const pos = bot.entity?.position
    return nearestAnchor(pos, pads) || pos
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
    if (bot.qaNeedRetarget) {
      bot.qaNeedRetarget = false
      cancelPath(bot)
      wanderOnIsland(bot, home(), hopRadius, goals)
      return
    }
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), {
        canDig: false,
        maxDrop: cfg.maxDrop ?? 2
      }))
    }

    if (!bot.qaHome) {
      const pad = home()
      if (pad) bot.qaHome = { x: pad.x, y: pad.y, z: pad.z }
    }

    if (pads.length === 0) {
      note(bot, 'pad idle (no pads)', 'idle')
      fidget(bot, 'idle')
      return
    }

    const now = Date.now()
    if (bot.pathfinder.isMoving()) {
      bot.qaActivity = 'hopping'
    } else if (now - lastHop > jitter(1600, 0.35)) {
      lastHop = now
      const nearby = pads.filter((wp) => horizontalDistance(bot.entity.position, wp) <= maxHop)
      const dest = nearby.length > 0
        ? nearby[Math.floor(Math.random() * nearby.length)]
        : pads[cursor % pads.length]
      cursor++
      if (dest && horizontalDistance(bot.entity.position, dest) <= maxHop) {
        note(bot, `stand on pad ${dest.x.toFixed(0)} ${dest.z.toFixed(0)}`, 'hopping')
        bot.pathfinder.setGoal(new goals.GoalNear(dest.x, dest.y, dest.z, 1))
      } else {
        note(bot, 'local pad fidget', 'hopping')
        wanderOnIsland(bot, home(), hopRadius, goals)
      }
    } else {
      bot.qaActivity = 'hopping'
    }

    if (now - lastFidget > 3200) {
      lastFidget = now
      fidget(bot, 'hopping')
    }
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, `pad loop start (${pads.length} pads, hop<=${maxHop}; far pads = plugin TP)`)
    note(bot, 'pad loop start', 'hopping')
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `pad tick: ${err.message}`)
      })
    }, jitter(380, 0.15))
    bot.once('end', () => clearInterval(handle))
  }
}
