import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, cancelPath, horizontalDistance, nearestAnchor, wanderOnIsland } from './safety.js'
import { fidget, jitter, markError, note, sleep } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/**
 * Walk to known quest FancyNPC names and right-click. No dialogue tree automation.
 */
export function createQuestLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const npcs = (cfg.npcs || []).map((n) => ({
    id: String(n.id || n.name || '').toLowerCase(),
    name: String(n.name || n.id || '').toLowerCase(),
    x: Number(n.x),
    y: Number(n.y),
    z: Number(n.z)
  }))
  const waypoints = (cfg.waypoints || cfg.anchors || []).map(asPoint).filter(Boolean)
  const wanderRadius = cfg.wanderRadius ?? 5
  const maxHop = cfg.maxHop ?? 12
  let running = false
  let lastClick = 0
  let lastFidget = 0
  let lastHop = 0
  let npcCursor = 0

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

    const target = nearestQuestNpc(bot, npcs, cfg.searchRadius ?? 16)
    const now = Date.now()
    if (target) {
      const dist = bot.entity.position.distanceTo(target.position)
      if (dist > 3.0) {
        note(bot, `walk to ${target.name || 'npc'}`, 'pathing')
        bot.pathfinder.setGoal(new goals.GoalFollow(target, 2), true)
        return
      }
      if (now - lastClick > jitter(cfg.clickEveryMs ?? 8000, 0.35)) {
        lastClick = now
        bot.pathfinder.setGoal(null)
        note(bot, `talk ${target.name || 'npc'}`, 'questing')
        try {
          await bot.lookAt(target.position.offset(0, target.height * 0.8, 0), true)
          await sleep(jitter(250, 0.5))
          await bot.activateEntity(target)
        } catch (err) {
          markError(bot, err)
        }
        await sleep(jitter(1800, 0.4))
        closeWindow(bot)
      }
      if (now - lastFidget > 3500) {
        lastFidget = now
        fidget(bot, 'questing')
      }
      return
    }

    if (!bot.pathfinder.isMoving() && now - lastHop > 1600) {
      lastHop = now
      const hint = npcs.length === 0 ? null : npcs[npcCursor % npcs.length]
      npcCursor++
      const nearbyPads = waypoints.filter((wp) => horizontalDistance(bot.entity.position, wp) <= maxHop)
      if (hint && Number.isFinite(hint.x) && horizontalDistance(bot.entity.position, hint) <= maxHop) {
        note(bot, `seek ${hint.id || 'npc'}`, 'questing')
        bot.pathfinder.setGoal(new goals.GoalNear(hint.x, hint.y, hint.z, 2))
      } else if (nearbyPads.length > 0 && Math.random() < 0.45) {
        const dest = nearbyPads[Math.floor(Math.random() * nearbyPads.length)]
        note(bot, 'npc pad hop', 'questing')
        bot.pathfinder.setGoal(new goals.GoalNear(dest.x, dest.y, dest.z, 2))
      } else {
        note(bot, 'look for npc', 'questing')
        wanderOnIsland(bot, home(), wanderRadius, goals)
      }
    } else {
      bot.qaActivity = 'questing'
    }

    if (now - lastFidget > 4000) {
      lastFidget = now
      fidget(bot, 'questing')
    }
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, `quest loop start (${npcs.length} npc hints; no dialogue automation)`)
    note(bot, 'quest loop start', 'questing')
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `quest tick: ${err.message}`)
      })
    }, jitter(400, 0.2))
    bot.once('end', () => clearInterval(handle))
  }
}

function nearestQuestNpc(bot, hints, radius) {
  const keys = hints.flatMap((n) => [n.id, n.name]).filter(Boolean)
  if (keys.length === 0) {
    keys.push('eldervale', 'maren', 'twig', 'forage', 'egon', 'welcome', 'guide')
  }
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities)) {
    if (!entity || entity === bot.entity) continue
    if (entity.type === 'player' && entity.username === bot.username) continue
    const name = (entity.username || entity.name || entity.displayName || '').toString().toLowerCase()
    if (!name) continue
    if (name.includes('item') || name.includes('experience') || name.includes('armor_stand')) continue
    if (!keys.some((key) => key && name.includes(key))) continue
    const dist = bot.entity.position.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}

function closeWindow(bot) {
  try {
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  } catch {
    /* ignore */
  }
}

function asPoint(p) {
  const x = Number(p?.x)
  const y = Number(p?.y)
  const z = Number(p?.z)
  if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) return null
  return { x, y, z }
}
