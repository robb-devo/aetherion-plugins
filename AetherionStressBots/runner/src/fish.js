import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, standingIsSafe, wanderOnIsland } from './safety.js'
import { markError, note, sleep } from './util.js'
import { ACTIVITIES, fidget } from './playstyle.js'
import { maybeOpenBooster } from './minigame.js'
import { shouldYield } from './mind.js'

const { goals, Movements, pathfinder } = pathfinderPkg

function isRod(item) {
  if (!item?.name) return false
  return item.name.toLowerCase().includes('fishing_rod')
}

export function findWater(bot, radius = 8) {
  if (!bot.findBlock) return null
  try {
    return bot.findBlock({
      matching: (block) => block && (block.name === 'water' || block.name === 'bubble_column'),
      maxDistance: radius
    })
  } catch {
    return null
  }
}

export function createFishLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const wanderRadius = cfg.wanderRadius ?? 5
  let running = false
  let lastCast = 0
  let lastFidget = 0
  let casting = false

  function equipRod() {
    if (bot.heldItem && isRod(bot.heldItem)) return true
    const rod = (bot.inventory.items() || []).find(isRod)
    if (!rod) return false
    bot.equip(rod, 'hand').catch(() => {})
    return true
  }

  async function castAt(water) {
    if (casting) return
    casting = true
    lastCast = Date.now()
    try {
      await bot.lookAt(water.position.offset(0.5, 0.2, 0.5), true)
      note(bot, 'cast', ACTIVITIES.fishing)
      await bot.activateItem()
      const deadline = Date.now() + (cfg.castWaitMs ?? 16_000)
      while (Date.now() < deadline) {
        if (bot.qaSuspended) break
        if ((bot.qaMinigame?.biteUntil || 0) > Date.now()) {
          note(bot, 'reel', ACTIVITIES.minigame)
          try { await bot.activateItem() } catch { /* ignore */ }
          await sleep(250)
          try { await bot.activateItem() } catch { /* ignore */ }
          note(bot, 'fish strike', ACTIVITIES.fishing)
          break
        }
        await sleep(180)
      }
    } finally {
      casting = false
      try { bot.deactivateItem() } catch { /* ignore */ }
    }
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended || casting) return
    if (shouldYield(bot)) return
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 2 }))
    }
    if (!equipRod()) {
      note(bot, 'no fishing rod', 'idle')
      return
    }
    const pos = bot.entity.position
    if (!standingIsSafe(bot, pos.x, pos.y, pos.z)) {
      wanderOnIsland(bot, bot.qaHome || pos, 3, goals)
      return
    }
    const water = findWater(bot, cfg.searchRadius ?? 10)
    if (!water) {
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : ACTIVITIES.fishing
      wanderOnIsland(bot, bot.qaHome || pos, wanderRadius, goals)
      if (Date.now() - lastFidget > 4000) {
        lastFidget = Date.now()
        fidget(bot, ACTIVITIES.fishing)
      }
      return
    }
    const dist = pos.distanceTo(water.position.offset(0.5, 0, 0.5))
    if (dist > 4.5) {
      note(bot, 'path to water', 'pathing')
      bot.pathfinder.setGoal(new goals.GoalNear(water.position.x, pos.y, water.position.z, 2))
      return
    }
    bot.pathfinder.setGoal(null)
    const patience = bot.qaPersona?.patience ?? 0.5
    const recast = (cfg.recastMs ?? 2200) * (0.55 + patience)
    if (Date.now() - lastCast < recast) return
    await castAt(water)
    if (Math.random() < 0.12) {
      await maybeOpenBooster(bot)
    }
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, 'fish loop start')
    note(bot, 'fish loop start', ACTIVITIES.fishing)
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `fish tick: ${err.message}`)
      })
    }, 360)
    bot.once('end', () => clearInterval(handle))
  }
}
