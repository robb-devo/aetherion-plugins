import pathfinderPkg from 'mineflayer-pathfinder'
import {
  applyIslandMovements,
  cancelPath,
  sampleSolidNear,
  setGoal,
  withinLeash,
  wanderOnIsland
} from './safety.js'
import { findMatchingBlock, inventoryAlmostFull, jitter, markError, note, tossJunk, waitUntil, sleep } from './util.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/**
 * Shared dig loop: path to matching blocks and break them (mine = ores, forage = logs).
 * Stays leashed to the role pad so Skyblock edges are not path goals.
 * Forage trees often look motionless while pathing/breaking — safety retargets instead of freezing on activity=stuck.
 */
export function createDigLoop(bot, cfg, log, { activity, names, searchRadius, yRange, canDig = false, fallback = [] }) {
  bot.loadPlugin(pathfinder)

  const leash = cfg.leashRadius ?? bot.qaLeash ?? 16
  const searchLeashBonus = Number(cfg.searchLeashBonus ?? (activity === 'foraging' ? 6 : 2))
  const pickLeash = leash + Math.max(0, searchLeashBonus)
  const radius = Math.max(pickLeash, searchRadius ?? cfg.searchRadius ?? 16)
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
    const origin = home() || bot.entity?.position
    const primary = findMatchingBlock(bot, nameSet, radius, yRange ?? 6, origin)
    if (primary && withinLeash(primary.position.offset(0.5, 0.5, 0.5), home(), pickLeash)) {
      return primary
    }
    if (fallbackSet.size === 0) {
      return null
    }
    const filler = findMatchingBlock(bot, fallbackSet, Math.min(radius, pickLeash), Math.min(yRange ?? 6, 5), origin)
    if (filler && withinLeash(filler.position.offset(0.5, 0.5, 0.5), home(), pickLeash)) {
      return filler
    }
    return null
  }

  function aborted() {
    return !!(bot.qaSuspended || bot.qaNeedRetarget)
  }

  async function tick() {
    if (!bot.entity || busy || bot.qaSuspended) return
    if (bot.qaNeedRetarget) {
      bot.qaNeedRetarget = false
      bot.qaDigging = false
      bot.qaGathering = false
      setGoal(bot, null)
      cancelPath(bot)
      note(bot, 'retarget after stuck/leash', 'idle')
      wanderOnIsland(bot, home(), wanderRadius, goals)
      await sleep(jitter(400, 0.5))
      return
    }
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
      bot.qaGathering = false
      bot.qaDigging = false
      setGoal(bot, null)
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'idle'
      if (!bot.pathfinder.isMoving()) {
        const pad = sampleSolidNear(bot, home(), wanderRadius)
        if (pad) {
          note(bot, 'scan hop', 'pathing')
          setGoal(bot, pad)
          bot.pathfinder.setGoal(new goals.GoalNear(pad.x, pad.y, pad.z, 1))
        } else {
          wanderOnIsland(bot, home(), wanderRadius, goals)
        }
      }
      return
    }

    busy = true
    bot.qaGathering = true
    try {
      const dest = block.position.offset(0.5, 0.5, 0.5)
      setGoal(bot, dest)
      const dist = bot.entity.position.distanceTo(dest)
      if (dist > 3.2) {
        note(bot, `path to ${block.name}`, 'pathing')
        bot.pathfinder.setGoal(new goals.GoalNear(block.position.x, block.position.y, block.position.z, 2))
        await waitUntil(() => {
          if (aborted()) return true
          return bot.entity.position.distanceTo(block.position.offset(0.5, 0.5, 0.5)) <= 3.2
        }, 9000)
      }
      if (aborted()) {
        cancelPath(bot)
        setGoal(bot, null)
        return
      }
      bot.pathfinder.setGoal(null)
      bot.qaDigging = true
      note(bot, `dig ${block.name}`, activity)
      await sleep(jitter(180, 0.6))
      if (Math.random() < 0.12) {
        note(bot, 'scratch head', activity)
        await sleep(jitter(700, 0.5))
      }
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
      setGoal(bot, null)
      wanderOnIsland(bot, home(), wanderRadius, goals)
    } finally {
      bot.qaDigging = false
      bot.qaGathering = false
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
    }, jitter(280, 0.15))
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
    searchRadius: cfg.searchRadius ?? 22,
    yRange: 8,
    canDig: false,
    fallback: cfg.fallback || [
      'oak_leaves', 'spruce_leaves', 'birch_leaves', 'jungle_leaves',
      'acacia_leaves', 'dark_oak_leaves', 'azalea_leaves', 'flowering_azalea_leaves',
      'mangrove_leaves', 'cherry_leaves', 'dirt', 'grass_block', 'rooted_dirt', 'podzol'
    ]
  })
}
