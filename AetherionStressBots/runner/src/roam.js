import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, cancelPath, horizontalDistance, nearestAnchor, wanderOnIsland } from './safety.js'
import { assignGait, idleFidget, setNearGoal, tunePathfinder } from './motion.js'
import { markError, note } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

const HOSTILE = ['zombie', 'husk', 'skeleton', 'stray', 'creeper', 'spider', 'drowned', 'witch', 'pillager', 'phantom']

/**
 * Local pad hops + fidgets. Distant waypoints are plugin teleports, not void walks.
 */
export function createRoamLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  assignGait(bot)

  const waypoints = (cfg.waypoints || []).map((p) => ({
    x: Number(p.x),
    y: Number(p.y),
    z: Number(p.z)
  })).filter((p) => Number.isFinite(p.x) && Number.isFinite(p.y) && Number.isFinite(p.z))

  const maxHop = cfg.maxHop ?? 12
  const fleeRadius = cfg.fleeRadius ?? 8
  const hopRadius = cfg.wanderRadius ?? 6
  let running = false
  let lastFidget = 0
  let lastHop = 0

  function home() {
    if (bot.qaHome) return bot.qaHome
    const pos = bot.entity?.position
    const nearest = nearestAnchor(pos, waypoints)
    return nearest || pos
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return

    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(tunePathfinder(bot, applyIslandMovements(new Movements(bot), {
        canDig: false,
        maxDrop: cfg.maxDrop ?? 2
      }), { maxDrop: cfg.maxDrop ?? 2, sprint: bot.qaSprint }))
    }

    const pad = home()
    if (!bot.qaHome && pad) {
      bot.qaHome = { x: pad.x, y: pad.y, z: pad.z }
    }

    const hostile = nearestHostile(bot, fleeRadius)
    if (hostile) {
      cancelPath(bot)
      note(bot, `flee ${hostile.name || 'mob'}`, 'recovering')
      wanderOnIsland(bot, pad, Math.min(4, hopRadius), goals)
      idleFidget(bot, 'recovering')
      return
    }

    if (waypoints.length === 0) {
      note(bot, 'roam idle (no waypoints)', 'idle')
      idleFidget(bot, 'idle')
      return
    }

    if (bot.pathfinder.isMoving()) {
      bot.qaActivity = 'roaming'
    } else if (Date.now() - lastHop > 2400 || bot.qaNeedNewGoal) {
      bot.qaNeedNewGoal = false
      lastHop = Date.now()
      const nearby = waypoints.filter((wp) => horizontalDistance(bot.entity.position, wp) <= maxHop)
      const dest = nearby.length > 0 && Math.random() < 0.35
        ? nearby[Math.floor(Math.random() * nearby.length)]
        : null
      if (dest) {
        note(bot, `pad hop ${dest.x.toFixed(0)} ${dest.z.toFixed(0)}`, 'roaming')
        setNearGoal(bot, goals, dest, 2)
      } else {
        note(bot, 'local hop', 'roaming')
        wanderOnIsland(bot, pad, hopRadius, goals)
      }
    } else {
      bot.qaActivity = 'roaming'
    }

    if (Date.now() - lastFidget > 4500) {
      lastFidget = Date.now()
      idleFidget(bot, 'roaming')
    }
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, `roam loop start (${waypoints.length} local waypoints, hop<=${maxHop})`)
    note(bot, 'roam loop start', 'roaming')
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `roam tick: ${err.message}`)
      })
    }, 380)
    bot.once('end', () => clearInterval(handle))
  }
}

function nearestHostile(bot, radius) {
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities)) {
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
