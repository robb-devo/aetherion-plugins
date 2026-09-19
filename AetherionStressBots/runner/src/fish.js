import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, cancelPath, setGoal, wanderOnIsland, withinLeash } from './safety.js'
import { fidget, findMatchingBlock, jitter, markError, note, sleep } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

const WATER = new Set([
  'water', 'kelp', 'kelp_plant', 'seagrass', 'tall_seagrass',
  'bubble_column', 'lily_pad'
])

/**
 * Cast a rod at water near a safe pad. Does not play the Aetherion strike minigame.
 */
export function createFishLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)

  const leash = cfg.leashRadius ?? bot.qaLeash ?? 14
  const wanderRadius = cfg.wanderRadius ?? 5
  const searchRadius = cfg.searchRadius ?? 16
  let running = false
  let busy = false
  let lastCast = 0
  let lastFidget = 0

  function home() {
    return bot.qaHome || bot.entity?.position
  }

  async function tick() {
    if (!bot.entity || busy || bot.qaSuspended) return
    if (bot.qaNeedRetarget) {
      bot.qaNeedRetarget = false
      cancelPath(bot)
      wanderOnIsland(bot, home(), wanderRadius, goals)
      return
    }
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), {
        canDig: false,
        maxDrop: cfg.maxDrop ?? 1
      }))
    }

    equipRod(bot)

    const water = findWater(bot, home(), searchRadius, leash)
    if (!water) {
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'idle'
      wanderOnIsland(bot, home(), wanderRadius, goals)
      if (Date.now() - lastFidget > 4000) {
        lastFidget = Date.now()
        fidget(bot, 'idle')
      }
      return
    }

    const dest = water.position.offset(0.5, 1, 0.5)
    const dist = bot.entity.position.distanceTo(dest)
    setGoal(bot, dest)
    if (dist > 3.8) {
      note(bot, 'walk to water', 'pathing')
      bot.pathfinder.setGoal(new goals.GoalNear(water.position.x, water.position.y + 1, water.position.z, 2))
      return
    }

    bot.pathfinder.setGoal(null)
    busy = true
    bot.qaGathering = true
    try {
      const now = Date.now()
      if (now - lastCast < jitter(cfg.castCooldownMs ?? 4200, 0.25)) {
        note(bot, 'wait for bite window', 'fishing')
        if (Math.random() < 0.2) fidget(bot, 'fishing')
        return
      }
      lastCast = now
      note(bot, 'cast rod', 'fishing')
      await bot.lookAt(water.position.offset(0.5, 0.2, 0.5), true)
      await sleep(jitter(180, 0.5))
      try {
        bot.activateItem()
      } catch (err) {
        markError(bot, err)
      }
      await sleep(jitter(cfg.reelWaitMs ?? 6500, 0.3))
      try {
        bot.activateItem()
      } catch {
        /* already reeled */
      }
      note(bot, 'reel', 'fishing')
      await sleep(jitter(900, 0.4))
    } finally {
      bot.qaGathering = false
      busy = false
    }
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, 'fish loop start (cast/reel; strike minigame not automated)')
    note(bot, 'fish loop start', 'fishing')
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `fish tick: ${err.message}`)
      })
    }, jitter(420, 0.2))
    bot.once('end', () => clearInterval(handle))
  }
}

function findWater(bot, home, radius, leash) {
  const block = findMatchingBlock(bot, WATER, radius, 4, home)
  if (!block) return null
  if (!withinLeash(block.position.offset(0.5, 0.5, 0.5), home, leash + 2)) return null
  return block
}

function equipRod(bot) {
  const held = bot.heldItem
  if (held && isRod(held)) return
  const rod = bot.inventory.items().find(isRod)
  if (!rod) return
  bot.equip(rod, 'hand').catch(() => {})
}

function isRod(item) {
  return item && item.name && item.name.toLowerCase().includes('fishing_rod')
}
