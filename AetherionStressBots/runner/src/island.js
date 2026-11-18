import pathfinderPkg from 'mineflayer-pathfinder'
import Vec3 from 'vec3'
import { applyIslandMovements, sampleSolidNear } from './safety.js'
import { ask, messageText } from './helper.js'
import { stripLegacy } from './chat.js'
import { closeWindow, KINDS, SLOTS, windowKind } from './gui.js'
import { isOurBot, waitFor } from './npc.js'
import { travel } from './travel.js'
import { note, runEvery, sleep } from './util.js'
import { telemetry } from './telemetry.js'

const { goals, Movements, pathfinder } = pathfinderPkg

const ISLAND_WORLD = /^aether_(islands|guilds)/
const BLOCKS = ['cobblestone', 'oak_planks']

/** "Kokos_Klaus wants to be friends." → "Kokos_Klaus" (AetherionGuilds FriendService). */
export function friendRequester(text) {
  const match = /^(\w{3,16}) wants to be friends\./.exec(stripLegacy(String(text || '')).trim())
  return match ? match[1] : null
}

/**
 * Private islands on mmo-i: `/island` crosses from the main world, `/island create` opens the starter pick
 * (three starters; the bot picks one, the server pastes it), then the bot lives there a while: walks around,
 * builds a little and takes it down again, opens the island menu, befriends other bots and visits their islands.
 * Islands need Aetherion level 20; the plugin raises island bots to it once (testbots.unlocks).
 */
export function createIslandLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const stayMs = cfg.stayMs ?? 8 * 60_000
  const stats = (bot.qaStats = bot.qaStats || {})
  let running = false
  let arrivedAt = 0
  let nextTripAt = 0
  let phase = 'arrive'
  let home = null
  let placed = []
  let lastAction = 0
  let befriended = new Set()

  bot.on('message', (message) => {
    const who = friendRequester(messageText(message))
    if (who && isOurBot(who, bot.qaFleet?.isFleetBot)) {
      setTimeout(() => bot.chat(`/friend accept ${who}`), 1500)
      telemetry.count('island friends accepted')
    }
  })

  async function world() {
    const answer = await ask(bot, 'where', { timeoutMs: 4000 })
    return answer?.kv?.world || ''
  }

  async function claim() {
    if (bot.currentWindow) closeWindow(bot)
    await sleep(500)
    bot.chat('/island create')
    bot.qaOwnWindows = true
    try {
      if (!(await waitFor(() => windowKind(bot).kind === KINDS.starter, 5000))) return false
      const info = windowKind(bot)
      if (info.state !== 'open') {
        telemetry.problem(bot.stressName, 'island starter menu is locked (level 20 not reached?)')
        return false
      }
      const slot = SLOTS.starters[Math.floor(Math.random() * SLOTS.starters.length)]
      await bot.clickWindow(slot, 0, 0)
      telemetry.count('islands claimed')
      note(bot, 'claimed a starter island', 'island')
      return true
    } finally {
      if (bot.currentWindow) closeWindow(bot)
      bot.qaOwnWindows = false
    }
  }

  async function arrive() {
    bot.chat('/island home')
    await sleep(5000)
    if (ISLAND_WORLD.test(await world())) {
      phase = 'live'
      home = bot.entity.position.clone()
      telemetry.count('island visits')
      return
    }
    if (await claim()) {
      // The paste takes a moment; the server brings the owner over when it is built.
      for (let i = 0; i < 12; i++) {
        await sleep(5000)
        if (ISLAND_WORLD.test(await world())) {
          phase = 'live'
          home = bot.entity.position.clone()
          telemetry.count('island visits')
          return
        }
      }
      telemetry.problem(bot.stressName, 'island claimed but never arrived on it')
    }
    phase = 'give-up'
  }

  async function build() {
    const item = (bot.inventory?.items?.() || []).find((i) => BLOCKS.includes(i.name))
    const below = bot.blockAt(bot.entity.position.offset(1, -1, 0).floored())
    const target = below ? bot.blockAt(below.position.offset(0, 1, 0)) : null
    if (!item || !below || below.boundingBox !== 'block' || !target || target.name !== 'air') return
    try {
      await bot.equip(item, 'hand')
      await bot.placeBlock(below, new Vec3(0, 1, 0))
      placed.push(below.position.offset(0, 1, 0))
      stats.placed = (stats.placed || 0) + 1
      note(bot, `placed ${item.name}`, 'building')
    } catch {
      /* spot taken or protected */
    }
  }

  async function tidy() {
    const pos = placed.shift()
    const block = pos ? bot.blockAt(pos) : null
    if (!block || !BLOCKS.includes(block.name)) return
    try {
      await bot.dig(block, true)
      note(bot, 'took a block back down', 'building')
    } catch {
      /* ignore */
    }
  }

  async function live() {
    const now = Date.now()
    if (now - lastAction < 6000) {
      if (!bot.pathfinder.isMoving() && home) {
        const spot = sampleSolidNear(bot, home, 10)
        if (spot) bot.pathfinder.setGoal(new goals.GoalNear(spot.x, spot.y, spot.z, 1))
      }
      return
    }
    lastAction = now
    const roll = Math.random()
    if (roll < 0.3) {
      await build()
    } else if (roll < 0.5 || placed.length > 6) {
      await tidy()
    } else if (roll < 0.62) {
      bot.qaOwnWindows = true
      const started = Date.now()
      bot.chat('/island menu')
      const ok = await waitFor(() => Boolean(bot.currentWindow), 4000, 40)
      telemetry.menu('/island menu', ok ? Date.now() - started : null)
      await sleep(1500)
      closeWindow(bot)
      bot.qaOwnWindows = false
    } else if (roll < 0.72) {
      const other = (bot.qaFleet?.peers?.(bot.role) || []).find((p) => p.name !== bot.stressName && !befriended.has(p.name))
      if (other) {
        befriended.add(other.name)
        bot.chat(`/friend add ${other.name}`)
        telemetry.count('island friend requests')
      }
    } else if (roll < 0.8 && befriended.size) {
      const friend = [...befriended][Math.floor(Math.random() * befriended.size)]
      bot.chat(`/friend visit ${friend}`)
      telemetry.count('island friend visits')
      note(bot, `visiting ${friend}`, 'island')
      await sleep(8000)
      home = bot.entity.position.clone()
    } else if (roll < 0.85) {
      bot.chat('/island home')
      await sleep(5000)
      home = bot.entity.position.clone()
    }
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended || bot.qaTravelling) return
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 2 }))
    }
    if (bot.qaServer !== 'mmo-i') {
      if (Date.now() < nextTripAt) return
      phase = 'arrive'
      arrivedAt = 0
      if (!(await travel(bot, 'mmo-i', { log }))) nextTripAt = Date.now() + 120_000
      return
    }
    if (!arrivedAt) arrivedAt = Date.now()
    if (phase === 'give-up' || Date.now() - arrivedAt > stayMs) {
      while (placed.length) await tidy()
      note(bot, 'back to the main world', 'travel')
      if (await travel(bot, 'mmo-r', { log })) await bot.qaEnsureKit?.()
      nextTripAt = Date.now() + (cfg.breakMs ?? 180_000)
      phase = 'arrive'
      return
    }
    if (bot.currentWindow && !bot.qaOwnWindows) closeWindow(bot)
    if (phase === 'arrive') {
      await arrive()
      return
    }
    await live()
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    log(bot.stressName, 'island loop start')
    note(bot, 'island loop start', 'island')
    const halt = runEvery(bot, 500, tick, (err) => log(bot.stressName, `island tick: ${err.message}`))
    stop = () => {
      halt()
      running = false
      bot.qaOwnWindows = false
    }
    return stop
  }
}
