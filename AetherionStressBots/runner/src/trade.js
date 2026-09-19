import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, cancelPath, horizontalDistance, nearestAnchor, wanderOnIsland } from './safety.js'
import { fidget, jitter, markError, note, sleep } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/**
 * Best-effort AH / Bazaar: try /ah and /bazaar, else walk to a configured NPC and right-click.
 * Does not complete listings. Progression may gate the GUIs (TRADER flag).
 */
export function createTradeLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const commands = (cfg.commands && cfg.commands.length ? cfg.commands : ['ah', 'bazaar'])
    .map((c) => String(c).replace(/^\//, ''))
  const npcs = cfg.npcs || []
  const waypoints = (cfg.waypoints || cfg.anchors || []).map(asPoint).filter(Boolean)
  const wanderRadius = cfg.wanderRadius ?? 5
  let running = false
  let lastCommand = 0
  let lastClick = 0
  let lastFidget = 0
  let cmdIndex = 0

  function home() {
    return bot.qaHome || nearestAnchor(bot.entity?.position, waypoints) || bot.entity?.position
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
    if (bot.qaNeedRetarget) {
      bot.qaNeedRetarget = false
      cancelPath(bot)
      wanderOnIsland(bot, home(), wanderRadius, goals)
      return
    }
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), {
        canDig: false,
        maxDrop: cfg.maxDrop ?? 2
      }))
    }

    const now = Date.now()
    if (now - lastCommand > jitter(cfg.commandEveryMs ?? 14000, 0.3) && commands.length > 0) {
      lastCommand = now
      const cmd = commands[cmdIndex % commands.length]
      cmdIndex++
      note(bot, `/${cmd}`, 'trading')
      try {
        bot.chat('/' + cmd)
      } catch (err) {
        markError(bot, err)
      }
      await sleep(jitter(1600, 0.4))
      closeWindow(bot)
      return
    }

    const npc = nearestTradeNpc(bot, npcs, cfg.searchRadius ?? 14)
    if (npc && now - lastClick > jitter(cfg.clickEveryMs ?? 9000, 0.3)) {
      const dist = bot.entity.position.distanceTo(npc.position)
      if (dist > 3.2) {
        note(bot, `walk to ${npc.name || 'trader'}`, 'pathing')
        bot.pathfinder.setGoal(new goals.GoalFollow(npc, 2), true)
        return
      }
      lastClick = now
      bot.pathfinder.setGoal(null)
      note(bot, `click ${npc.name || 'trader'}`, 'trading')
      try {
        await bot.lookAt(npc.position.offset(0, npc.height * 0.7, 0), true)
        await bot.activateEntity(npc)
      } catch (err) {
        markError(bot, err)
      }
      await sleep(jitter(2200, 0.4))
      closeWindow(bot)
      return
    }

    if (!bot.pathfinder.isMoving()) {
      const nearby = waypoints.filter((wp) => horizontalDistance(bot.entity.position, wp) <= (cfg.maxHop ?? 10))
      if (nearby.length > 0 && Math.random() < 0.4) {
        const dest = nearby[Math.floor(Math.random() * nearby.length)]
        note(bot, 'market stroll', 'trading')
        bot.pathfinder.setGoal(new goals.GoalNear(dest.x, dest.y, dest.z, 2))
      } else {
        wanderOnIsland(bot, home(), wanderRadius, goals)
      }
    } else {
      bot.qaActivity = 'trading'
    }

    if (now - lastFidget > 3800) {
      lastFidget = now
      fidget(bot, 'trading')
    }
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, `trade loop start (/${commands.join(' /')}; GUI listing not automated)`)
    note(bot, 'trade loop start', 'trading')
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `trade tick: ${err.message}`)
      })
    }, jitter(480, 0.2))
    bot.once('end', () => clearInterval(handle))
  }
}

function closeWindow(bot) {
  try {
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  } catch {
    /* ignore */
  }
}

function nearestTradeNpc(bot, hints, radius) {
  const keys = hints.map((h) => String(h.name || h.id || '').toLowerCase()).filter(Boolean)
  if (keys.length === 0) {
    keys.push('bazaar', 'auction', 'trader', 'merchant', 'ah')
  }
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities)) {
    if (!entity || entity === bot.entity) continue
    const name = (entity.username || entity.name || entity.displayName || '').toString().toLowerCase()
    if (!name) continue
    if (!keys.some((key) => name.includes(key))) continue
    const dist = bot.entity.position.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}

function asPoint(p) {
  const x = Number(p?.x)
  const y = Number(p?.y)
  const z = Number(p?.z)
  if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) return null
  return { x, y, z }
}
