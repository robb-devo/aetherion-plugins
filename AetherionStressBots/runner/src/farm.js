import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, cancelPath, setGoal, withinLeash, wanderOnIsland } from './safety.js'
import { assignGait, idleFidget, setNearGoal, tunePathfinder } from './motion.js'
import { findMatchingBlock, inventoryAlmostFull, jitter, markError, note, tossJunk, waitUntil, sleep } from './util.js'
import { takeIdleGoal } from './playstyle.js'

const { goals, Movements, pathfinder } = pathfinderPkg

const CROPS = [
  'wheat', 'carrots', 'potatoes', 'beetroots', 'nether_wart', 'cocoa',
  'sweet_berry_bush', 'torchflower_crop', 'pitcher_crop', 'melon', 'pumpkin', 'sugar_cane'
]
const FALLBACK = ['farmland', 'dirt', 'grass_block', 'hay_block']

function isHoe(item) {
  return Boolean(item?.name && item.name.toLowerCase().includes('hoe'))
}

function isMature(block) {
  if (!block) return false
  const age = block.metadata ?? block._properties?.age ?? block.getProperties?.().age
  const name = block.name
  if (age == null) return true
  if (name === 'wheat' || name === 'carrots' || name === 'potatoes') return Number(age) >= 7
  if (name === 'beetroots' || name === 'nether_wart') return Number(age) >= 3
  if (name === 'sweet_berry_bush' || name === 'pitcher_crop') return Number(age) >= 3
  return true
}

function equipHoe(bot) {
  if (isHoe(bot.heldItem)) return true
  const hoe = (bot.inventory.items() || []).find(isHoe)
  if (!hoe) return false
  bot.equip(hoe, 'hand').catch(() => {})
  return true
}

/**
 * Harvest mature crops on the Eldervale farm island. Falls back to wandering the pad
 * (and tapping farmland with the hoe) so the role still looks busy if the schematic is empty.
 */
export function createFarmLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  assignGait(bot)

  const leash = cfg.leashRadius ?? bot.qaLeash ?? 16
  const radius = cfg.searchRadius ?? 16
  const wanderRadius = cfg.wanderRadius ?? 6
  const names = new Set((cfg.crops || CROPS).map((s) => s.toLowerCase()))
  const fallback = new Set((cfg.fallback || FALLBACK).map((s) => s.toLowerCase()))
  let running = false
  let busy = false
  let lastTill = 0

  function home() {
    return bot.qaHome || bot.entity?.position
  }

  function pickCrop() {
    const origin = home() || bot.entity?.position
    const primary = findMatchingBlock(bot, names, radius, cfg.yRange ?? 4, origin, isMature)
    if (primary && withinLeash(primary.position.offset(0.5, 0.5, 0.5), home(), leash + 2)) {
      return primary
    }
    const any = findMatchingBlock(bot, names, radius, cfg.yRange ?? 4, origin)
    if (any && withinLeash(any.position.offset(0.5, 0.5, 0.5), home(), leash + 2)) {
      return any
    }
    const filler = findMatchingBlock(bot, fallback, Math.min(radius, leash), 3, origin)
    if (filler && withinLeash(filler.position.offset(0.5, 0.5, 0.5), home(), leash + 2)) {
      return filler
    }
    return null
  }

  async function tillNearby() {
    if (Date.now() - lastTill < 5000) return
    lastTill = Date.now()
    const dirt = findMatchingBlock(bot, new Set(['dirt', 'grass_block', 'farmland']), 6, 2, bot.entity.position)
    if (!dirt) return
    try {
      await bot.lookAt(dirt.position.offset(0.5, 1, 0.5), true)
      note(bot, `hoe ${dirt.name}`, 'farming')
      await bot.activateBlock(dirt)
    } catch {
      /* ignore */
    }
  }

  async function tick() {
    if (!bot.entity || busy || bot.qaSuspended) return
    if (bot.qaNeedRetarget) {
      bot.qaNeedRetarget = false
      setGoal(bot, null)
      cancelPath(bot)
      wanderOnIsland(bot, home(), wanderRadius, goals)
      await sleep(jitter(350, 0.4))
      return
    }
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(tunePathfinder(bot, applyIslandMovements(new Movements(bot), {
        canDig: false,
        maxDrop: cfg.maxDrop ?? 1
      }), { maxDrop: cfg.maxDrop ?? 1, sprint: bot.qaSprint }))
    }
    equipHoe(bot)
    if (inventoryAlmostFull(bot)) {
      tossJunk(bot, { keepResources: true })
    }

    const crop = pickCrop()
    const needGoal = takeIdleGoal(bot)
    if (!crop || needGoal) {
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : 'farming'
      if (!bot.pathfinder.isMoving()) {
        note(bot, needGoal ? 'farm hop' : 'scan crops', 'pathing')
        wanderOnIsland(bot, home(), wanderRadius, goals)
        await tillNearby()
        if (Math.random() < 0.3) idleFidget(bot, 'farming')
      }
      return
    }

    busy = true
    bot.qaGathering = true
    try {
      const dest = crop.position.offset(0.5, 0.5, 0.5)
      setGoal(bot, dest)
      if (bot.entity.position.distanceTo(dest) > 2.8) {
        note(bot, `path to ${crop.name}`, 'pathing')
        setNearGoal(bot, goals, dest, 2)
        await waitUntil(() => bot.qaSuspended || bot.qaNeedRetarget
          || bot.entity.position.distanceTo(crop.position.offset(0.5, 0.5, 0.5)) <= 2.8, 8000)
      }
      if (bot.qaSuspended || bot.qaNeedRetarget) return
      bot.pathfinder.setGoal(null)
      bot.qaDigging = true
      note(bot, `harvest ${crop.name}`, 'farming')
      await sleep(jitter(140, 0.5))
      await bot.lookAt(crop.position.offset(0.5, 0.4, 0.5), true)
      await Promise.race([
        bot.dig(crop),
        sleep(cfg.digTimeoutMs ?? 6000).then(() => { throw new Error('dig timeout') })
      ])
      await sleep(jitter(260, 0.4))
    } catch (err) {
      if (!String(err.message || err).includes('timeout')) markError(bot, err)
      try { bot.stopDigging() } catch { /* ignore */ }
      cancelPath(bot)
    } finally {
      bot.qaDigging = false
      bot.qaGathering = false
      busy = false
    }
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, 'farm loop start')
    note(bot, 'farm loop start', 'farming')
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `farm tick: ${err.message}`)
      })
    }, jitter(320, 0.15))
    bot.once('end', () => clearInterval(handle))
  }
}
