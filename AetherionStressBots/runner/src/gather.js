import pathfinderPkg from 'mineflayer-pathfinder'
import {
  applyIslandMovements,
  cancelPath,
  sampleSolidNear,
  setGoal,
  withinLeash,
  wanderOnIsland
} from './safety.js'
import {
  equipMatching,
  findMatchingBlock,
  inventoryAlmostFull,
  jitter,
  markError,
  note,
  runEvery,
  sleep,
  tossJunk,
  waitUntil
} from './util.js'
import { applyPathfinderDefaults, fidget, takeIdleGoal } from './playstyle.js'
import { playBar, timingBar } from './timing.js'

const { goals, Movements, pathfinder } = pathfinderPkg

const REACH = 4.2

export function isPickaxe(item) {
  return Boolean(item?.name && item.name.endsWith('_pickaxe'))
}

export function isAxe(item) {
  return Boolean(item?.name && item.name.endsWith('_axe') && !item.name.endsWith('_pickaxe'))
}

/** Blocks a bot gave up on (unreachable, spent tree, miss cooldown), forgotten after a while. */
export function createMemory() {
  const until = new Map()
  const key = (pos) => `${pos.x},${pos.y},${pos.z}`
  return {
    skip(pos, ms) {
      until.set(key(pos), Date.now() + ms)
      if (until.size > 400) {
        const now = Date.now()
        for (const [k, t] of until) if (t < now) until.delete(k)
      }
    },
    skipped(pos) {
      const t = until.get(key(pos))
      if (!t) return false
      if (t < Date.now()) {
        until.delete(key(pos))
        return false
      }
      return true
    },
    /** A whole tree: every log within {@code radius} of the one that was clicked. */
    near(pos, radius) {
      for (const [k, t] of until) {
        if (t < Date.now()) continue
        const [x, y, z] = k.split(',').map(Number)
        if (Math.abs(x - pos.x) <= radius && Math.abs(z - pos.z) <= radius && Math.abs(y - pos.y) <= 12) return true
      }
      return false
    }
  }
}

/**
 * Shared gather loop: find a matching block inside the leash, walk next to it, work it.
 * Mining digs for real (Eldervale pays ore and stone per break). Foraging plays the Fell bar:
 * a click on a log starts the chop, a second click while the bar is green fells the tree.
 */
export function createDigLoop(bot, cfg, log, { activity, names, searchRadius, yRange, fallback = [], mode = 'dig' }) {
  bot.loadPlugin(pathfinder)

  const leash = cfg.leashRadius ?? bot.qaLeash ?? 16
  const searchLeashBonus = Number(cfg.searchLeashBonus ?? (mode === 'fell' ? 6 : 2))
  const pickLeash = leash + Math.max(0, searchLeashBonus)
  const radius = Math.max(pickLeash, searchRadius ?? cfg.searchRadius ?? 16)
  const digTimeoutMs = cfg.digTimeoutMs ?? 10_000
  const nameSet = new Set((names || cfg.blocks || cfg.ores || []).map((s) => s.toLowerCase()))
  const fallbackSet = mode === 'fell'
    ? new Set()
    : new Set((fallback.length ? fallback : (cfg.fallback || [])).map((s) => s.toLowerCase()))
  const wanderRadius = cfg.wanderRadius ?? 6
  const memory = createMemory()
  const stats = (bot.qaStats = bot.qaStats || {})
  const toolOk = mode === 'fell' ? isAxe : isPickaxe

  let running = false

  function home() {
    return bot.qaHome || bot.entity?.position
  }

  function skip(block) {
    if (memory.skipped(block.position)) return true
    return mode === 'fell' && memory.near(block.position, 3)
  }

  function pickBlock() {
    const origin = home() || bot.entity?.position
    const primary = findMatchingBlock(bot, nameSet, radius, yRange ?? 6, origin, skip)
    if (primary && withinLeash(primary.position.offset(0.5, 0.5, 0.5), home(), pickLeash)) {
      return primary
    }
    if (fallbackSet.size === 0) return null
    // Filler (stone, leaves) only when it is already in reach: walking to filler is what made miners hop.
    const filler = findMatchingBlock(bot, fallbackSet, REACH, 3, bot.entity.position, skip)
    if (filler && withinLeash(filler.position.offset(0.5, 0.5, 0.5), home(), pickLeash)) {
      return filler
    }
    return null
  }

  function aborted() {
    return !!(bot.qaSuspended || bot.qaNeedRetarget)
  }

  function inReach(block) {
    return bot.entity.position.offset(0, bot.entity.eyeHeight ?? 1.62, 0)
      .distanceTo(block.position.offset(0.5, 0.5, 0.5)) <= REACH
  }

  async function walkTo(block) {
    if (inReach(block)) return true
    note(bot, `path to ${block.name}`, 'pathing')
    setGoal(bot, block.position.offset(0.5, 0.5, 0.5))
    bot.pathfinder.setGoal(new goals.GoalNear(block.position.x, block.position.y, block.position.z, 2))
    try {
      await waitUntil(() => aborted() || inReach(block) || !bot.pathfinder.isMoving(), 12_000)
    } catch {
      /* wait timeout */
    }
    if (!inReach(block)) {
      cancelPath(bot)
      setGoal(bot, null)
      memory.skip(block.position, 45_000)
      return false
    }
    bot.pathfinder.setGoal(null)
    return true
  }

  /** One left click on a block: start digging, cancel right away (LEFT_CLICK_BLOCK on the server). */
  async function tap(block) {
    const digging = bot.dig(block, true).catch(() => {})
    try {
      await waitUntil(() => bot.targetDigBlock != null, 600)
    } catch {
      /* dig never started */
    }
    await sleep(40)
    try { bot.stopDigging() } catch { /* ignore */ }
    await digging
  }

  async function dig(block) {
    bot.qaDigging = true
    note(bot, `dig ${block.name}`, activity)
    await Promise.race([
      bot.dig(block, true),
      sleep(digTimeoutMs).then(() => {
        throw new Error('dig timeout')
      })
    ])
    stats.blocks = (stats.blocks || 0) + 1
  }

  async function fell(block) {
    bot.qaDigging = true
    note(bot, `chop ${block.name}`, activity)
    await tap(block)
    try {
      await waitUntil(() => {
        const bar = timingBar(bot, 'fell')
        return bar != null && bar.phase !== 'preview'
      }, 1500)
    } catch {
      // No fell bar: spent tree, not a tree the isle knows, or chopping is still locked for this bot.
      note(bot, `no fell bar on ${block.name}`, activity)
      memory.skip(block.position, 60_000)
      return
    }
    const bar = timingBar(bot, 'fell')
    if (bar?.phase === 'cooling') {
      note(bot, 'tree on miss cooldown', activity)
      memory.skip(block.position, 30_000)
      return
    }
    const result = await playBar(bot, 'fell', () => tap(block), { timeoutMs: 7000 })
    memory.skip(block.position, result === 'clicked' ? 50_000 : 30_000)
    if (result === 'clicked') {
      stats.fells = (stats.fells || 0) + 1
      note(bot, 'fell on green', activity)
      await sleep(jitter(1800, 0.3))
    } else {
      stats.misses = (stats.misses || 0) + 1
      note(bot, `fell ${result}`, activity)
    }
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
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
    if (bot.currentWindow) return
    if (!home()) {
      bot.qaHome = { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
    }
    if (!bot.pathfinder.movements) {
      const moves = applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: cfg.maxDrop ?? 2 })
      bot.pathfinder.setMovements(moves)
      applyPathfinderDefaults(bot, moves)
    }
    if (inventoryAlmostFull(bot)) tossJunk(bot)

    const block = takeIdleGoal(bot) ? null : pickBlock()
    if (!block) {
      bot.qaGathering = false
      bot.qaDigging = false
      setGoal(bot, null)
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'idle'
      if (!bot.pathfinder.isMoving()) {
        const spot = sampleSolidNear(bot, home(), wanderRadius)
        if (spot) {
          note(bot, 'scan hop', 'pathing')
          setGoal(bot, spot)
          bot.pathfinder.setGoal(new goals.GoalNear(spot.x, spot.y, spot.z, 1))
        } else {
          wanderOnIsland(bot, home(), wanderRadius, goals)
        }
        if (Math.random() < 0.25) fidget(bot, activity)
      }
      return
    }

    bot.qaGathering = true
    try {
      if (!await walkTo(block)) return
      if (aborted()) return
      if (!await equipMatching(bot, toolOk)) {
        note(bot, `no ${mode === 'fell' ? 'axe' : 'pickaxe'}`, activity)
      }
      if (Math.random() < 0.1) {
        note(bot, 'scratch head', activity)
        await sleep(jitter(700, 0.5))
      }
      const current = bot.blockAt(block.position)
      if (!current || current.name !== block.name) return
      if (mode === 'fell') {
        await fell(current)
      } else {
        await dig(current)
      }
    } catch (err) {
      const message = String(err?.message || err)
      if (!message.includes('dig timeout') && !message.includes('aborted')) {
        markError(bot, err)
      }
      try { bot.stopDigging() } catch { /* ignore */ }
      memory.skip(block.position, 20_000)
      cancelPath(bot)
      setGoal(bot, null)
    } finally {
      bot.qaDigging = false
      bot.qaGathering = false
      setGoal(bot, null)
    }
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    if (bot.entity?.position) {
      bot.qaHome = bot.qaHome || { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
    }
    log(bot.stressName, `${activity} loop start (${mode})`)
    note(bot, `${activity} loop start`, activity)
    const halt = runEvery(bot, 300, tick, (err) => {
      markError(bot, err)
      log(bot.stressName, `${activity} tick: ${err.message}`)
    })
    stop = () => {
      halt()
      running = false
    }
    return stop
  }
}

export function createMiningLoop(bot, cfg, log) {
  return createDigLoop(bot, cfg, log, {
    activity: 'mining',
    mode: 'dig',
    names: cfg.ores,
    searchRadius: cfg.searchRadius ?? 16,
    yRange: 8,
    fallback: cfg.fallback || [
      'stone', 'cobblestone', 'deepslate', 'cobbled_deepslate',
      'andesite', 'diorite', 'granite', 'tuff', 'calcite'
    ]
  })
}

export function createForageLoop(bot, cfg, log) {
  return createDigLoop(bot, cfg, log, {
    activity: 'foraging',
    mode: 'fell',
    names: cfg.logs || cfg.blocks || [
      'oak_log', 'birch_log', 'spruce_log', 'dark_oak_log', 'jungle_log',
      'acacia_log', 'mangrove_log', 'cherry_log'
    ],
    searchRadius: cfg.searchRadius ?? 22,
    yRange: 8,
    // Felling is the whole job; leaves and dirt are not worth a click.
    fallback: []
  })
}
