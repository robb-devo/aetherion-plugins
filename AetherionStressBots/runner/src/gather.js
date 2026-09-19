import pathfinderPkg from 'mineflayer-pathfinder'
import {
  applyIslandMovements,
  cancelPath,
  sampleSolidNear,
  withinLeash,
  wanderOnIsland
} from './safety.js'
import { findMatchingBlock, inventoryAlmostFull, markError, note, tossJunk, waitUntil, sleep } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/**
 * Shared dig loop: path to matching blocks and break them (mine = ores, forage = logs).
 * Stays leashed to the role pad so Skyblock edges are not path goals.
 */
export function createDigLoop(bot, cfg, log, { activity, names, searchRadius, yRange, canDig = false, fallback = [] }) {
  bot.loadPlugin(pathfinder)

  const leash = cfg.leashRadius ?? bot.qaLeash ?? 16
  const radius = Math.max(leash, searchRadius ?? cfg.searchRadius ?? 16)
  const digTimeoutMs = cfg.digTimeoutMs ?? 10_000
  const nameSet = new Set((names || cfg.blocks || cfg.ores || []).map((s) => s.toLowerCase()))
  const fallbackSet = new Set((fallback.length ? fallback : (cfg.fallback || [])).map((s) => s.toLowerCase()))
  const wanderRadius = cfg.wanderRadius ?? 6

  let running = false
  let busy = false

  function home() {
    return bot.qaHome || bot.entity?.position
  }

  function pickBlock() {
    const primary = findMatchingBlock(bot, nameSet, radius, yRange ?? 6)
    if (primary && withinLeash(primary.position.offset(0.5, 0.5, 0.5), home(), leash)) {
      return primary
    }
    if (fallbackSet.size === 0) {
      return null
    }
    const filler = findMatchingBlock(bot, fallbackSet, leash, Math.min(yRange ?? 6, 5))
    if (filler && withinLeash(filler.position.offset(0.5, 0.5, 0.5), home(), leash)) {
      return filler
    }
    return null
  }

  async function tick() {
    if (!bot.entity || busy || bot.qaSuspended) return
    if (!home()) {
      bot.qaHome = { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
    }

    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), {
        canDig,
        maxDrop: cfg.maxDrop ?? 2
      }))
    }

    if (inventoryAlmostFull(bot)) {
      tossJunk(bot)
    }

    const block = pickBlock()
    if (!block) {
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'idle'
      if (!bot.pathfinder.isMoving()) {
        const pad = sampleSolidNear(bot, home(), wanderRadius)
        if (pad) {
          note(bot, 'scan hop', 'pathing')
          bot.pathfinder.setGoal(new goals.GoalNear(pad.x, pad.y, pad.z, 1))
        } else {
          wanderOnIsland(bot, home(), wanderRadius, goals)
        }
      }
      return
    }

    busy = true
    try {
      const dest = block.position.offset(0.5, 0.5, 0.5)
      const dist = bot.entity.position.distanceTo(dest)
      if (dist > 3.2) {
        note(bot, `path to ${block.name}`, 'pathing')
        bot.pathfinder.setGoal(new goals.GoalNear(block.position.x, block.position.y, block.position.z, 2))
        await waitUntil(() => {
          if (bot.qaSuspended) return true
          return bot.entity.position.distanceTo(block.position.offset(0.5, 0.5, 0.5)) <= 3.2
        }, 6000)
      }
      if (bot.qaSuspended) {
        cancelPath(bot)
        return
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
      cancelPath(bot)
    } finally {
      busy = false
    }
  }

  return function start() {
    if (running) return
    running = true
    if (bot.entity?.position) {
      bot.qaHome = bot.qaHome || { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
    }
    log(bot.stressName, `${activity} loop start`)
    note(bot, `${activity} loop start`, activity)
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `${activity} tick: ${err.message}`)
      })
    }, 280)
    bot.once('end', () => clearInterval(handle))
  }
}

export function createMiningLoop(bot, cfg, log) {
  return createDigLoop(bot, cfg, log, {
    activity: 'mining',
    names: cfg.ores,
    searchRadius: cfg.searchRadius ?? 16,
    yRange: 8,
    canDig: false,
    fallback: cfg.fallback || [
      'stone', 'cobblestone', 'deepslate', 'cobbled_deepslate',
      'andesite', 'diorite', 'granite', 'tuff', 'calcite'
    ]
  })
}

export function createForageLoop(bot, cfg, log) {
  return createDigLoop(bot, cfg, log, {
    activity: 'foraging',
    names: cfg.logs || cfg.blocks,
    searchRadius: cfg.searchRadius ?? 16,
    yRange: 8,
    canDig: false,
    fallback: cfg.fallback || [
      'oak_leaves', 'spruce_leaves', 'birch_leaves', 'jungle_leaves',
      'acacia_leaves', 'dark_oak_leaves', 'azalea_leaves', 'flowering_azalea_leaves',
      'mangrove_leaves', 'cherry_leaves', 'dirt', 'grass_block', 'rooted_dirt', 'podzol'
    ]
  })
}
