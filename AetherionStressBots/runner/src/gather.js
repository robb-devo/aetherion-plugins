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
import { applyPathfinderDefaults, fidget, takeIdleGoal } from './playstyle.js'
import { isLingering, shouldYield } from './mind.js'
import { assignGoal, forgetGoal, hasDigApproach, isFailedBlock, isFooting, rememberFailure } from './move.js'

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
  let cached = null

  function home() {
    return bot.qaHome || bot.entity?.position
  }

  function approachable(block) {
    if (!block?.position) return false
    if (isFooting(bot.entity?.position, block)) return false
    if (isFailedBlock(bot, block.position)) return false
    if (!withinLeash(block.position.offset(0.5, 0.5, 0.5), home(), pickLeash)) return false
    return hasDigApproach(
      (x, y, z) => bot.blockAt(block.position.offset(x - block.position.x, y - block.position.y, z - block.position.z)),
      block.position.x,
      block.position.y,
      block.position.z
    )
  }

  function pickBlock() {
    const now = Date.now()
    if (cached && now < cached.until) {
      const held = bot.blockAt(cached.pos)
      if (held && approachable(held) && (nameSet.has(held.name) || fallbackSet.has(held.name))) return held
      cached = null
    }
    const origin = bot.entity?.position || home()
    const primary = findMatchingBlock(bot, nameSet, radius, yRange ?? 6, origin)
    if (primary && approachable(primary)) {
      cached = { pos: primary.position, until: now + 5000 }
      return primary
    }
    if (fallbackSet.size === 0) return null
    const focus = bot.qaPersona?.focus ?? 0.6
    if (Math.random() < focus * 0.25) return null
    const filler = findMatchingBlock(bot, fallbackSet, Math.min(radius, pickLeash), Math.min(yRange ?? 6, 5), origin)
    if (filler && approachable(filler)) {
      cached = { pos: filler.position, until: now + 4000 }
      return filler
    }
    return null
  }

  function aborted() {
    return !!(bot.qaSuspended || bot.qaNeedRetarget)
  }

  async function tick() {
    if (!bot.entity || busy || bot.qaSuspended) return
    if (shouldYield(bot)) {
      if (isLingering(bot)) note(bot, 'taking a break', 'lingering')
      return
    }
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
      const moves = applyIslandMovements(new Movements(bot), {
        canDig,
        maxDrop: cfg.maxDrop ?? 2,
        bot,
        sprint: false
      })
      bot.pathfinder.setMovements(moves)
      applyPathfinderDefaults(bot, moves)
    }

    if (inventoryAlmostFull(bot)) {
      tossJunk(bot)
    }

    const block = pickBlock()
    const needGoal = takeIdleGoal(bot)
    if (!block || needGoal) {
      bot.qaGathering = false
      bot.qaDigging = false
      if (needGoal) {
        cached = null
        forgetGoal(bot)
      }
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'mining'
      if (!bot.pathfinder.isMoving()) {
        const reach = Math.min(wanderRadius, (bot.qaPersona?.wanderMul ?? 1) * 4)
        const pad = sampleSolidNear(bot, home(), reach)
        const issued = pad
          ? assignGoal(bot, goals, pad, 1.8, { minIntervalMs: 2500 })
          : wanderOnIsland(bot, home(), wanderRadius, goals)
        if (issued) {
          note(bot, needGoal ? 'new spot' : 'looking for ore', 'pathing')
          setGoal(bot, pad || bot.qaMoveGoal)
        }
        if (Math.random() < 0.12) fidget(bot, activity)
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
        const stand = block.position.offset(
          Math.sign((bot.entity.position.x - block.position.x) || 1),
          0,
          Math.sign((bot.entity.position.z - block.position.z) || 0)
        )
        assignGoal(bot, goals, { x: stand.x + 0.5, y: bot.entity.position.y, z: stand.z + 0.5 }, 1.4, { force: true })
        await waitUntil(() => {
          if (aborted()) return true
          return bot.entity.position.distanceTo(block.position.offset(0.5, 0.5, 0.5)) <= 3.2
        }, 10_000)
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
      rememberFailure(bot, block?.position)
      cached = null
      cancelPath(bot)
      forgetGoal(bot)
      setGoal(bot, null)
      wanderOnIsland(bot, home(), Math.min(4, wanderRadius), goals)
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
