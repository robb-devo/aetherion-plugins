import pathfinderPkg from 'mineflayer-pathfinder'
import { findMatchingBlock, inventoryAlmostFull, markError, note, tossJunk, waitUntil, wanderNear, sleep } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/**
 * Shared dig loop: path to matching blocks and break them (mine = ores, forage = logs).
 */
export function createDigLoop(bot, cfg, log, { activity, names, searchRadius, yRange, canDig = true }) {
  bot.loadPlugin(pathfinder)

  const radius = searchRadius ?? cfg.searchRadius ?? 20
  const digTimeoutMs = cfg.digTimeoutMs ?? 12_000
  const nameSet = new Set((names || cfg.blocks || cfg.ores || []).map((s) => s.toLowerCase()))

  let running = false
  let busy = false
  let home = null

  async function tick() {
    if (!bot.entity || busy) return
    if (!home) home = bot.entity.position.clone()

    if (!bot.pathfinder.movements) {
      const movements = new Movements(bot)
      movements.allowSprinting = true
      movements.canDig = canDig
      bot.pathfinder.setMovements(movements)
    }

    if (inventoryAlmostFull(bot)) {
      tossJunk(bot)
    }

    const block = findMatchingBlock(bot, nameSet, radius, yRange ?? 6)
    if (!block) {
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'idle'
      wanderNear(bot, home, 10, goals)
      return
    }

    busy = true
    try {
      const dist = bot.entity.position.distanceTo(block.position.offset(0.5, 0.5, 0.5))
      if (dist > 3.2) {
        note(bot, `path to ${block.name}`, 'pathing')
        bot.pathfinder.setGoal(new goals.GoalNear(block.position.x, block.position.y, block.position.z, 2))
        await waitUntil(() => bot.entity.position.distanceTo(block.position.offset(0.5, 0.5, 0.5)) <= 3.2, 8000)
      }
      bot.pathfinder.setGoal(null)
      note(bot, `dig ${block.name}`, activity)
      await bot.lookAt(block.position.offset(0.5, 0.5, 0.5), true)
      await Promise.race([
        bot.dig(block),
        sleep(digTimeoutMs).then(() => {
          throw new Error('dig timeout')
        })
      ])
    } catch (err) {
      if (!String(err.message || err).includes('dig timeout') && !String(err.message || err).includes('wait timeout')) {
        markError(bot, err)
      }
      try { bot.stopDigging() } catch { /* ignore */ }
    } finally {
      busy = false
    }
  }

  return function start() {
    if (running) return
    running = true
    home = bot.entity?.position?.clone() ?? null
    log(bot.stressName, `${activity} loop start`)
    note(bot, `${activity} loop start`, activity)
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `${activity} tick: ${err.message}`)
      })
    }, 400)
    bot.once('end', () => clearInterval(handle))
  }
}

export function createMiningLoop(bot, cfg, log) {
  return createDigLoop(bot, cfg, log, {
    activity: 'mining',
    names: cfg.ores,
    searchRadius: cfg.searchRadius ?? 20,
    yRange: 6,
    canDig: true
  })
}

export function createForageLoop(bot, cfg, log) {
  return createDigLoop(bot, cfg, log, {
    activity: 'foraging',
    names: cfg.logs || cfg.blocks,
    searchRadius: cfg.searchRadius ?? 22,
    yRange: 8,
    canDig: true
  })
}
