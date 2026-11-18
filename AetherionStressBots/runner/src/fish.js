import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, standingIsSafe, wanderOnIsland } from './safety.js'
import { equipMatching, jitter, markError, note, runEvery, sleep } from './util.js'
import { ACTIVITIES, fidget } from './playstyle.js'
import { playBar, timingBar } from './timing.js'

const { goals, Movements, pathfinder } = pathfinderPkg

function isRod(item) {
  return Boolean(item?.name && item.name.toLowerCase().includes('fishing_rod'))
}

/** Open water: a water block with air above it, so the bobber lands on a surface. */
export function findWater(bot, radius = 10) {
  if (!bot.findBlocks) return null
  const water = bot.registry?.blocksByName?.water
  if (!water) return null
  let spots = []
  try {
    spots = bot.findBlocks({ matching: [water.id], maxDistance: radius, count: 48 })
  } catch {
    return null
  }
  const here = bot.entity.position
  let best = null
  let bestScore = Infinity
  for (const pos of spots) {
    const above = bot.blockAt(pos.offset(0, 1, 0))
    if (!above || (above.name !== 'air' && above.name !== 'cave_air')) continue
    const dist = here.distanceTo(pos)
    // 3–7 blocks out reads like a real cast; right under your feet or far away scores worse.
    const score = Math.abs(dist - 5) + (pos.y > here.y + 1 ? 6 : 0)
    if (score < bestScore) {
      best = bot.blockAt(pos)
      bestScore = score
    }
  }
  return best
}

/**
 * Fishing Eldervale's Line: cast, wait while "Fishing • 8.2s" runs, then reel only while the strike bar
 * is green (gold is perfect). Reeling on the "Bite!" title alone always missed, because the marker starts
 * outside the window.
 */
export function createFishLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const wanderRadius = cfg.wanderRadius ?? 5
  const searchRadius = cfg.searchRadius ?? 12
  const castWaitMs = cfg.castWaitMs ?? 45_000
  const stats = (bot.qaStats = bot.qaStats || {})
  let running = false
  let lastFidget = 0

  async function reel() {
    try { await bot.activateItem() } catch { /* ignore */ }
  }

  async function castAt(water) {
    await bot.lookAt(water.position.offset(0.5, 0.6, 0.5), true)
    note(bot, 'cast', ACTIVITIES.fishing)
    await reel()
    stats.casts = (stats.casts || 0) + 1
    const deadline = Date.now() + castWaitMs
    let sawBar = false
    while (Date.now() < deadline) {
      if (bot.qaSuspended || !bot.entity) return 'suspended'
      const bar = timingBar(bot, 'fish')
      if (bar) sawBar = true
      if (bar?.phase === 'strike') {
        note(bot, 'strike bar', ACTIVITIES.minigame)
        const result = await playBar(bot, 'fish', reel, { timeoutMs: 8000 })
        if (result === 'clicked') {
          stats.reels = (stats.reels || 0) + 1
          note(bot, 'reel on green', ACTIVITIES.fishing)
        } else {
          stats.misses = (stats.misses || 0) + 1
          note(bot, `strike ${result}`, ACTIVITIES.fishing)
        }
        await sleep(jitter(900, 0.3))
        return result
      }
      if (!sawBar && Date.now() > deadline - castWaitMs + 6000) {
        // No Fishing bar six seconds after the cast: the bobber missed the water. Pull it back in.
        note(bot, 'bobber missed water', ACTIVITIES.fishing)
        await reel()
        return 'no_water'
      }
      if (sawBar && !bar) {
        // Bar gone without a strike (fish escaped, cast aborted by the server).
        return 'gone'
      }
      await sleep(150)
    }
    note(bot, 'no bite — recast', ACTIVITIES.fishing)
    await reel()
    return 'timeout'
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: cfg.maxDrop ?? 1 }))
    }
    if (bot.currentWindow) return
    if (!await equipMatching(bot, isRod)) {
      note(bot, 'no fishing rod', 'idle')
      return
    }
    const pos = bot.entity.position
    if (bot.pathfinder.isMoving()) {
      bot.qaActivity = 'pathing'
      return
    }
    if (!standingIsSafe(bot, pos.x, pos.y, pos.z)) {
      wanderOnIsland(bot, bot.qaHome || pos, 3, goals)
      return
    }
    const water = findWater(bot, searchRadius)
    if (!water) {
      bot.qaActivity = ACTIVITIES.fishing
      wanderOnIsland(bot, bot.qaHome || pos, wanderRadius, goals)
      if (Date.now() - lastFidget > 4000) {
        lastFidget = Date.now()
        fidget(bot, ACTIVITIES.fishing)
      }
      return
    }
    const dist = pos.distanceTo(water.position.offset(0.5, 0, 0.5))
    if (dist > 8) {
      note(bot, 'walk to water', 'pathing')
      bot.pathfinder.setGoal(new goals.GoalNear(water.position.x, water.position.y + 1, water.position.z, 4))
      return
    }
    bot.pathfinder.setGoal(null)
    const result = await castAt(water)
    if (result === 'no_water') {
      wanderOnIsland(bot, bot.qaHome || pos, wanderRadius, goals)
    }
    await sleep(jitter(cfg.recastMs ?? 1400, 0.4))
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    log(bot.stressName, 'fish loop start (cast → strike bar → reel on green)')
    note(bot, 'fish loop start', ACTIVITIES.fishing)
    const halt = runEvery(bot, 400, tick, (err) => {
      markError(bot, err)
      log(bot.stressName, `fish tick: ${err.message}`)
    })
    stop = () => {
      halt()
      running = false
    }
    return stop
  }
}
