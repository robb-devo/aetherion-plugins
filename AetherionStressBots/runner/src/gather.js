import pathfinderPkg from 'mineflayer-pathfinder'
import {
  applyIslandMovements,
  cancelPath,
  sampleSolidNear,
  setGoal,
  withinLeash
} from './safety.js'
import { findMatchingBlock, inventoryAlmostFull, jitter, markError, note, tossJunk, sleep } from './util.js'
import { applyPathfinderDefaults, fidget, takeIdleGoal } from './playstyle.js'
import { isLingering, shouldYield } from './mind.js'
import { assignGoal, canDigFrom, floorY, forgetGoal, insideDisk, isFailedBlock, isFooting, planDig, rememberFailure, standFeet } from './move.js'
import Vec3 from 'vec3'

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

  function worldAt(x, y, z) {
    if (!bot.blockAt) return null
    try {
      return bot.blockAt(new Vec3(Math.floor(x), Math.floor(y), Math.floor(z)))
    } catch {
      return null
    }
  }

  function usable(block, primary) {
    if (!block?.position || !bot.entity?.position) return null
    if (isFooting(bot.entity.position, block)) return null
    if (isFailedBlock(bot, block.position)) return null
    const stand = standFeet(worldAt, block.position.x, block.position.y, block.position.z, bot.entity.position)
    const decision = planDig({
      pos: bot.entity.position,
      blockPos: block.position,
      stand,
      home: home(),
      leash,
      failed: false,
      footing: false,
      primary
    })
    if (decision === 'skip') return null
    return { stand, decision }
  }

  function pickBlock() {
    const now = Date.now()
    if (cached && now < cached.until) {
      const held = bot.blockAt(cached.pos)
      const plan = held && usable(held, nameSet.has(held.name))
      if (plan && (nameSet.has(held.name) || fallbackSet.has(held.name))) {
        return { block: held, ...plan }
      }
      cached = null
    }
    const origin = bot.entity?.position || home()
    const primary = findMatchingBlock(bot, nameSet, radius, yRange ?? 6, origin, (block) => !!usable(block, true))
    const primaryPlan = primary && usable(primary, true)
    if (primary && primaryPlan) {
      cached = { pos: primary.position, until: now + 5000 }
      return { block: primary, ...primaryPlan }
    }
    if (fallbackSet.size === 0) return null
    const filler = findMatchingBlock(
      bot,
      fallbackSet,
      Math.min(radius, leash),
      Math.min(yRange ?? 6, 5),
      origin,
      (block) => !!usable(block, false)
    )
    const fillerPlan = filler && usable(filler, false)
    if (filler && fillerPlan) {
      cached = { pos: filler.position, until: now + 4000 }
      return { block: filler, ...fillerPlan }
    }
    return null
  }

  function dropTarget(reason) {
    if (cached?.pos) rememberFailure(bot, cached.pos)
    cached = null
    bot.qaDigging = false
    cancelPath(bot)
    forgetGoal(bot)
    setGoal(bot, null)
    note(bot, reason, activity)
  }

  function stepAtFloor() {
    if (!bot.pathfinder || bot.pathfinder.isMoving()) return
    const pos = bot.entity.position
    const anchor = home()
    const floor = { x: anchor.x, y: floorY(pos.y, anchor.y), z: anchor.z }
    const reach = Math.min(wanderRadius, 4)
    const pad = sampleSolidNear(bot, floor, reach)
    if (!pad || Math.abs(pad.y - pos.y) > 2) return
    if (!insideDisk(pad, anchor, Math.max(2, leash - 1))) return
    const issued = assignGoal(bot, goals, pad, 1.5, { minIntervalMs: 2800 })
    if (issued) {
      note(bot, 'looking for ore', 'pathing')
      setGoal(bot, pad)
    }
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
      bot.qaGathering = false
      if (bot.qaActivity !== 'recovering') {
        dropTarget('drop target')
        return
      }
      if (cached?.pos) rememberFailure(bot, cached.pos)
      cached = null
    }
    if (home() && !withinLeash(bot.entity.position, home(), Math.max(2, leash - 1))) {
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

    const picked = pickBlock()
    const needGoal = takeIdleGoal(bot)
    if (!picked || needGoal) {
      bot.qaGathering = false
      bot.qaDigging = false
      if (needGoal) {
        cached = null
        forgetGoal(bot)
      }
      if (!bot.pathfinder.isMoving()) stepAtFloor()
      else bot.qaActivity = 'pathing'
      if (Math.random() < 0.08) fidget(bot, activity)
      return
    }

    const { block, stand, decision } = picked
    if (decision === 'dig' || canDigFrom(bot.entity.position, block.position)) {
      await digBlock(block)
      return
    }

    bot.qaGathering = true
    const issued = assignGoal(bot, goals, stand, 1.15, { minIntervalMs: 2200 })
    if (issued) {
      note(bot, `path to ${block.name}`, 'pathing')
      setGoal(bot, stand)
      bot.qaPathProgressAt = Date.now()
    } else if (!bot.qaPathProgressAt) {
      bot.qaPathProgressAt = bot.qaMoveIssued || Date.now()
    }
    const movedCloser = bot.entity.position.distanceTo(block.position.offset(0.5, 0.5, 0.5))
    if (!bot.qaPathDist || movedCloser < bot.qaPathDist - 0.45) {
      bot.qaPathDist = movedCloser
      bot.qaPathProgressAt = Date.now()
    }
    if ((Date.now() - (bot.qaPathProgressAt || 0)) > 2500) {
      bot.qaPathDist = null
      dropTarget(`gave up on ${block.name}`)
    }
  }

  async function digBlock(block) {
    busy = true
    bot.qaGathering = true
    bot.qaDigging = true
    cancelPath(bot)
    forgetGoal(bot)
    setGoal(bot, null)
    try {
      bot.setControlState('jump', false)
      bot.setControlState('forward', false)
      bot.setControlState('sprint', false)
    } catch {
      /* ignore */
    }
    note(bot, `dig ${block.name}`, activity)
    try {
      await sleep(jitter(160, 0.5))
      if (aborted()) return
      await bot.lookAt(block.position.offset(0.5, 0.5, 0.5), true)
      await Promise.race([
        bot.dig(block, true),
        sleep(digTimeoutMs).then(() => {
          throw new Error('dig timeout')
        })
      ])
      cached = null
      bot.qaPathDist = null
    } catch (err) {
      const message = String(err?.message || err)
      const expected = message.includes('dig timeout')
        || message.includes('Digging aborted')
        || message.includes('Block not in view')
        || message.includes('Infinity')
      if (!expected) markError(bot, err)
      try { bot.stopDigging() } catch { /* ignore */ }
      rememberFailure(bot, block?.position)
      cached = null
      bot.qaPathDist = null
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
