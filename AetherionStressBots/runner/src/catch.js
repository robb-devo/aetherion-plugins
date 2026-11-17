import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, cancelPath, standingIsSafe, withinLeash, wanderOnIsland } from './safety.js'
import { equipMatching, fidget, jitter, markError, note, runEvery, sleep } from './util.js'
import { maybeSkip } from './playstyle.js'
import { playBar, timingBar } from './timing.js'

const { goals, Movements, pathfinder } = pathfinderPkg

const CATCHABLE_TYPES = new Set(['animal', 'passive', 'water_creature', 'ambient', 'mob', 'hostile'])

/**
 * Throw a catch sphere from a solid pad, then play AetherMobs' timing window: the bar runs
 * "Hold it" (purple) → "✦ Now" (yellow) and one click while it is yellow locks a perfect catch.
 * The click is a left click into the air (PlayerInteractEvent LEFT_CLICK_AIR), so the bot looks up
 * first: a swing that hits a block would not count.
 */
export function createCatchLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const searchRadius = cfg.searchRadius ?? 10
  const throwCooldownMs = cfg.throwCooldownMs ?? 2500
  const wanderRadius = cfg.wanderRadius ?? 5
  const leash = cfg.leashRadius ?? bot.qaLeash ?? 12
  const stats = (bot.qaStats = bot.qaStats || {})
  const tried = new Map()
  let running = false
  let lastThrow = 0

  function home() {
    return bot.qaHome || bot.entity?.position
  }

  function onPad() {
    const pos = bot.entity.position
    return standingIsSafe(bot, pos.x, pos.y, pos.z)
  }

  async function lockClick() {
    try {
      await bot.look(bot.entity.yaw, Math.PI / 4, true)
    } catch { /* ignore */ }
    bot.swingArm()
  }

  async function ritual(target) {
    const result = await playBar(bot, 'catch', lockClick, { timeoutMs: 4000, startGraceMs: 2600 })
    if (result === 'clicked') {
      stats.locks = (stats.locks || 0) + 1
      note(bot, `timing lock on ${target.name || 'pet'}`, 'minigame')
    } else if (result === 'timeout') {
      note(bot, 'sphere missed', 'catching')
      stats.misses = (stats.misses || 0) + 1
      return
    }
    // Let the shakes play out before the next throw (bar closes when the ritual ends).
    const until = Date.now() + 6000
    while (Date.now() < until && timingBar(bot, 'catch')) await sleep(200)
    await sleep(jitter(1500, 0.3))
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
    if (bot.currentWindow) return
    if (!home()) {
      bot.qaHome = { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
    }
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), {
        canDig: false,
        maxDrop: cfg.maxDrop ?? 1
      }))
    }
    if (!await equipMatching(bot, isSphere)) {
      note(bot, 'out of spheres', 'idle')
      return
    }

    const target = nearestCatchable(bot, searchRadius, home(), leash, tried)
    if (!target) {
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'idle'
      if (!bot.pathfinder.isMoving()) wanderOnIsland(bot, home(), wanderRadius, goals)
      return
    }

    const dist = bot.entity.position.distanceTo(target.position)
    if (dist > 6) {
      if (!withinLeash(target.position, home(), leash)) {
        tried.set(target.id, Date.now() + 20_000)
        return
      }
      note(bot, `approach ${target.name || 'entity'}`, 'pathing')
      bot.pathfinder.setGoal(new goals.GoalFollow(target, 3), true)
      return
    }

    if (!onPad()) {
      cancelPath(bot)
      wanderOnIsland(bot, home(), Math.min(3, wanderRadius), goals)
      note(bot, 'return to habitat pad', 'recovering')
      return
    }

    cancelPath(bot)
    if (Date.now() - lastThrow < throwCooldownMs) return
    lastThrow = Date.now()
    if (maybeSkip(0.12)) {
      note(bot, 'hesitate throw', 'catching')
      fidget(bot, 'catching')
      return
    }
    try {
      await bot.lookAt(target.position.offset(0, (target.height || 1) * 0.6, 0), true)
      note(bot, `throw sphere at ${target.name || 'entity'}`, 'catching')
      await bot.activateItem()
      stats.throws = (stats.throws || 0) + 1
      tried.set(target.id, Date.now() + 15_000)
      await ritual(target)
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
    log(bot.stressName, 'catch loop start (throw → timing window)')
    note(bot, 'catch loop start', 'catching')
    runEvery(bot, 320, tick, (err) => {
      markError(bot, err)
      log(bot.stressName, `catch tick: ${err.message}`)
    })
  }
}

/** AetherMobs catch spheres are snowballs with a custom model (BetaSphereManager). */
export function isSphere(item) {
  return Boolean(item?.name && item.name.toLowerCase() === 'snowball')
}

function nearestCatchable(bot, radius, home, leash, tried) {
  let best = null
  let bestDist = radius
  const now = Date.now()
  for (const entity of Object.values(bot.entities)) {
    if (!entity || entity === bot.entity || !entity.position) continue
    if (!CATCHABLE_TYPES.has(entity.type)) continue
    const name = (entity.name || '').toLowerCase()
    if (name.includes('villager') || name.includes('iron_golem') || name.includes('armor_stand')) continue
    if ((tried.get(entity.id) || 0) > now) continue
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
