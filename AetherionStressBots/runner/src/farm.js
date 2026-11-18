import pathfinderPkg from 'mineflayer-pathfinder'
import { applyIslandMovements, wanderOnIsland } from './safety.js'
import { ask } from './helper.js'
import { closeWindow } from './gui.js'
import { equipMatching, findMatchingBlock, inventoryAlmostFull, note, runEvery, sleep } from './util.js'
import { telemetry } from './telemetry.js'

const { goals, Movements, pathfinder } = pathfinderPkg

/** Ripe at this age (1.21.1 block states). Melons and pumpkins are ripe when they exist. */
export const RIPE_AGE = { wheat: 7, carrots: 7, potatoes: 7, beetroots: 3, nether_wart: 3, cocoa: 2 }
const WHOLE = new Set(['melon', 'pumpkin'])
const CROPS = new Set([...Object.keys(RIPE_AGE), ...WHOLE])
const JUNK = /^(wheat_seeds|beetroot_seeds|poisonous_potato|melon_seeds|pumpkin_seeds)$/

export function isRipe(block) {
  if (!block?.name || !CROPS.has(block.name)) return false
  if (WHOLE.has(block.name)) return true
  const age = typeof block.getProperties === 'function' ? Number(block.getProperties().age) : NaN
  return Number.isFinite(age) && age >= RIPE_AGE[block.name]
}

/**
 * Farm fields: warp with `/farm` like a player (else the plugin stages the bot at the Hub spawn "farm"), then
 * harvest ripe crops with the farming hoe. AetherionFarming regrows what was cut, so the field never runs out.
 */
export function createFarmLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const radius = cfg.searchRadius ?? 18
  const stats = (bot.qaStats = bot.qaStats || {})
  let running = false
  let arrived = false
  let misses = 0
  const skip = new Map()

  async function goToField() {
    bot.chat(cfg.warp || '/farm')
    await sleep(3500)
    if (findMatchingBlock(bot, CROPS, radius, 6)) {
      note(bot, 'at the farm (warp)', 'farming')
      return true
    }
    const staged = await ask(bot, `stage ${cfg.stage || 'farm'}`, { timeoutMs: 5000 })
    await sleep(1500)
    if (staged?.words.includes('ok')) {
      note(bot, 'at the farm (staged)', 'farming')
      return true
    }
    telemetry.problem(bot.stressName, 'farm: no field after /farm and stage')
    return false
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended || bot.qaTravelling) return
    if (bot.qaServer && bot.qaServer !== 'mmo-r') return
    if (bot.currentWindow) {
      closeWindow(bot)
      return
    }
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 3 }))
    }
    if (!arrived) {
      arrived = await goToField()
      if (!arrived) await sleep(20_000)
      return
    }
    if (inventoryAlmostFull(bot)) tossSurplus(bot)
    const now = Date.now()
    const crop = findMatchingBlock(bot, CROPS, radius, 6, bot.entity.position,
      (block) => !isRipe(block) || (skip.get(key(block.position)) || 0) > now)
    if (!crop) {
      misses++
      note(bot, 'waiting for the field to regrow', 'farming')
      if (misses > 40) {
        arrived = false
        misses = 0
      } else if (!bot.pathfinder.isMoving()) {
        wanderOnIsland(bot, bot.qaHome || bot.entity.position, 8, goals)
      }
      return
    }
    misses = 0
    const dist = bot.entity.position.distanceTo(crop.position.offset(0.5, 0.5, 0.5))
    if (dist > 4) {
      bot.pathfinder.setGoal(new goals.GoalNear(crop.position.x, crop.position.y, crop.position.z, 2))
      note(bot, `to ${crop.name}`, 'farming')
      return
    }
    bot.pathfinder.setGoal(null)
    await equipMatching(bot, (item) => item.name.endsWith('_hoe'))
    try {
      bot.qaDigging = true
      await bot.dig(crop, true)
      stats.harvests = (stats.harvests || 0) + 1
      if (stats.harvests % 25 === 0) telemetry.count('crops harvested', 25)
      note(bot, `harvest ${crop.name}`, 'farming')
    } catch {
      skip.set(key(crop.position), now + 20_000)
    } finally {
      bot.qaDigging = false
    }
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    arrived = false
    log(bot.stressName, 'farm loop start')
    note(bot, 'farm loop start', 'farming')
    const halt = runEvery(bot, 350, tick, (err) => log(bot.stressName, `farm tick: ${err.message}`))
    stop = () => {
      halt()
      running = false
    }
    return stop
  }
}

/** Seeds and poisonous potatoes go first; past six stacks of a crop the oldest stack is dropped. */
function tossSurplus(bot) {
  const items = bot.inventory?.items?.() || []
  for (const item of items) {
    if (JUNK.test(item.name)) bot.tossStack(item).catch(() => {})
  }
  const byName = new Map()
  for (const item of items) {
    if (JUNK.test(item.name)) continue
    byName.set(item.name, [...(byName.get(item.name) || []), item])
  }
  for (const stacks of byName.values()) {
    if (stacks.length > 6) bot.tossStack(stacks[0]).catch(() => {})
  }
}

function key(pos) {
  return `${pos.x},${pos.y},${pos.z}`
}
