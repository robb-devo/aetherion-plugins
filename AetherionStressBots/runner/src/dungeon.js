import pathfinderPkg from 'mineflayer-pathfinder'
import Vec3 from 'vec3'
import { applyIslandMovements } from './safety.js'
import { ask, messageText } from './helper.js'
import { closeWindow, containerSize, KINDS, SLOTS, windowKind } from './gui.js'
import { fightStep } from './fight.js'
import { findNpcWithWindow, nearestEntity, talkTo, waitFor, walkTo } from './npc.js'
import { attachPartyAccept, myParty } from './party.js'
import { isBotName } from './names.js'
import { travel } from './travel.js'
import { note, runEvery, sleep } from './util.js'
import { telemetry } from './telemetry.js'
import { stripLegacy } from './chat.js'

const { Movements, pathfinder } = pathfinderPkg

export const INSTANCE_PREFIX = 'aedun_'

/** Somebody on this server who is not a bot (tab list: Velocity lists the players of the current backend). */
export function peopleOnline(players, isFleetBot = () => false) {
  return Object.values(players || {}).filter((p) => p?.username && p.listed !== false
    && !isFleetBot(p.username) && !isBotName(p.username)).map((p) => p.username)
}

/**
 * Dungeons, the way a party plays them: `/dhub` to the dungeon hub on mmo-d, the group's leader invites the
 * others and opens the Dungeon Keeper's menu (Floor 1 · Prison), everyone tells the Gate Warden they are ready,
 * then the bots fight room to room (the plugin points at the next mob or loot cache), loot the cache and leave.
 *
 * AetherionDungeons runs one instance at a time, so bots yield: they never start while a person is on mmo-d,
 * leave a run when one shows up, and give a run at most {@code maxRunMs}.
 */
export function createDungeonLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const size = cfg.partySize ?? 4
  const maxRunMs = cfg.maxRunMs ?? 8 * 60_000
  const floorSlot = cfg.floor === 3 ? SLOTS.dungeonFloor3 : cfg.floor === 2 ? SLOTS.dungeonFloor2 : SLOTS.dungeonFloor1
  const yieldToPeople = cfg.yieldToPlayers !== false
  const stats = (bot.qaStats = bot.qaStats || {})
  const isFleetBot = (name) => Boolean(bot.qaFleet?.isFleetBot?.(name))
  const fight = {}
  let running = false
  let world = ''
  let lastWhere = 0
  let runStartedAt = 0
  let ready = false
  let keeperAt = null
  let busyUntil = 0
  let lastSeek = 0
  let idleSince = 0
  let invitedAt = 0
  let runsDone = 0
  let nextTripAt = 0
  const opened = new Set()

  attachPartyAccept(bot, { leaderOf: () => plan()?.leader?.name || null, log })
  bot.on('message', (message) => {
    if (!running) return
    const text = stripLegacy(messageText(message))
    if (/A dungeon is already running/i.test(text)) {
      busyUntil = Date.now() + 45_000
      telemetry.count('dungeon busy (one at a time)')
    } else if (/ cleared\. \d+\/\d+/.test(text)) {
      stats.rooms = (stats.rooms || 0) + 1
      telemetry.count('dungeon rooms cleared')
    }
  })

  function plan() {
    const peers = (bot.qaFleet?.peers?.(bot.role) || []).filter((p) => p.server === 'mmo-d')
    return myParty(bot, peers, size)
  }

  async function refreshWorld(force = false) {
    if (!force && Date.now() - lastWhere < 10_000 && world) return world
    const answer = await ask(bot, 'where', { timeoutMs: 4000 })
    lastWhere = Date.now()
    if (answer?.kv?.world) world = answer.kv.world
    return world
  }

  async function hubTick() {
    if (runStartedAt) {
      // Back from a run.
      runsDone++
      telemetry.count('dungeon runs finished')
      note(bot, `run over (${Math.round((Date.now() - runStartedAt) / 1000)} s)`, 'dungeon')
      runStartedAt = 0
      ready = false
      opened.clear()
    }
    if (runsDone >= (cfg.runsBeforeHome ?? 3)) {
      runsDone = 0
      note(bot, 'back to the main world for a break', 'travel')
      if (await travel(bot, 'mmo-r', { log })) await bot.qaEnsureKit?.()
      nextTripAt = Date.now() + (cfg.homeBreakMs ?? 120_000)
      return
    }
    if (Date.now() < busyUntil) return
    const people = peopleOnline(bot.players, isFleetBot)
    if (yieldToPeople && people.length) {
      note(bot, `waiting: ${people.length} people on mmo-d`, 'dungeon')
      busyUntil = Date.now() + 30_000
      return
    }
    const party = plan()
    const leading = !party || party.leader.name === bot.stressName
    if (!leading) {
      note(bot, `waiting for ${party.leader.name} to open the gate`, 'dungeon')
      return
    }
    if (party?.members.length && Date.now() - invitedAt > 120_000) {
      invitedAt = Date.now()
      for (const member of party.members) {
        bot.chat(`/party invite ${member.name}`)
        await sleep(900)
      }
      await sleep(5000)
    }
    bot.qaOwnWindows = true
    try {
      const keeper = await findNpcWithWindow(bot, (k) => k.kind === KINDS.dungeon, { radius: 48, tries: 6, remember: keeperAt })
      if (!keeper) {
        const hint = await ask(bot, 'near name:keeper 96', { timeoutMs: 4000 })
        if (hint?.pos) {
          keeperAt = new Vec3(hint.pos.x, hint.pos.y, hint.pos.z)
          await walkTo(bot, keeperAt, { range: 3, timeoutMs: 25_000 })
        } else {
          telemetry.problem(bot.stressName, 'dungeon hub: no Dungeon Keeper found')
          busyUntil = Date.now() + 60_000
        }
        return
      }
      keeperAt = keeper.position.clone()
      const started = Date.now()
      await bot.clickWindow(floorSlot, 0, 0)
      telemetry.count('dungeon starts')
      note(bot, 'opened Floor ' + (cfg.floor || 1), 'dungeon')
      const entered = await waitWorldChange(started)
      if (!entered) busyUntil = Date.now() + 20_000
    } finally {
      if (bot.currentWindow) closeWindow(bot)
      bot.qaOwnWindows = false
    }
  }

  async function waitWorldChange(started) {
    while (Date.now() - started < 30_000) {
      await sleep(2500)
      const now = await refreshWorld(true)
      if (now.startsWith(INSTANCE_PREFIX)) return true
      if (Date.now() < busyUntil) return false
    }
    return false
  }

  async function instanceTick() {
    const now = Date.now()
    if (!runStartedAt) {
      runStartedAt = now
      ready = false
      idleSince = 0
      telemetry.count('dungeon runs entered')
      note(bot, 'in the dungeon', 'dungeon')
    }
    if (now - runStartedAt > maxRunMs || (yieldToPeople && peopleOnline(bot.players, isFleetBot).length)) {
      bot.chat('/dungeon leave')
      telemetry.count(now - runStartedAt > maxRunMs ? 'dungeon left (time)' : 'dungeon left (people waiting)')
      lastWhere = 0
      await sleep(4000)
      return
    }
    if (!ready) {
      const warden = nearestEntity(bot, (e) => String(e.name || '').toLowerCase() === 'villager', 28)
      if (warden && await walkTo(bot, warden.position, { range: 2.5, timeoutMs: 15_000 })) {
        await talkTo(bot, warden)
        ready = true
        telemetry.count('dungeon ready checks')
        note(bot, 'told the Gate Warden: ready', 'dungeon')
      } else if (now - runStartedAt > 30_000) {
        ready = true
      }
      return
    }
    if (await fightStep(bot, fight, { radius: 18, attackMs: cfg.attackIntervalMs ?? 460 })) {
      idleSince = 0
      note(bot, 'fighting', 'dungeon')
      return
    }
    if (bot.pathfinder.isMoving() || now - lastSeek < 3000) return
    lastSeek = now
    const mob = await ask(bot, 'near hostile 112', { timeoutMs: 4000 })
    if (mob?.pos) {
      idleSince = 0
      note(bot, 'to the next room', 'dungeon')
      await walkTo(bot, new Vec3(mob.pos.x, mob.pos.y, mob.pos.z), { range: 6, timeoutMs: 20_000 })
      return
    }
    const chest = await ask(bot, 'near chest 64', { timeoutMs: 4000 })
    if (chest?.pos && !opened.has(posKey(chest.pos))) {
      opened.add(posKey(chest.pos))
      await loot(new Vec3(Math.floor(chest.pos.x), Math.floor(chest.pos.y), Math.floor(chest.pos.z)))
      return
    }
    idleSince = idleSince || now
    if (now - idleSince > (cfg.clearedLeaveMs ?? 75_000)) {
      note(bot, 'nothing left: leaving', 'dungeon')
      bot.chat('/dungeon leave')
      telemetry.count('dungeon left (cleared)')
      lastWhere = 0
      await sleep(4000)
    }
  }

  async function loot(pos) {
    if (!(await walkTo(bot, pos, { range: 3, timeoutMs: 20_000 }))) return
    const block = bot.blockAt(pos)
    if (!block) return
    bot.qaOwnWindows = true
    try {
      await bot.lookAt(pos.offset(0.5, 0.5, 0.5), true)
      await bot.activateBlock(block)
      if (!(await waitFor(() => Boolean(bot.currentWindow), 3000))) return
      const window = bot.currentWindow
      let taken = 0
      for (let slot = 0; slot < containerSize(window); slot++) {
        if (!window.slots[slot]) continue
        try {
          await bot.clickWindow(slot, 0, 1)
          taken++
        } catch {
          break
        }
        await sleep(120)
      }
      stats.loot = (stats.loot || 0) + taken
      telemetry.count('dungeon caches looted')
      note(bot, `looted ${taken} stacks`, 'dungeon')
    } finally {
      if (bot.currentWindow) closeWindow(bot)
      bot.qaOwnWindows = false
    }
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended || bot.qaTravelling) return
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 4 }))
    }
    if (bot.qaServer !== 'mmo-d') {
      if (Date.now() < nextTripAt) return
      world = ''
      if (!(await travel(bot, 'mmo-d', { log }))) nextTripAt = Date.now() + 60_000
      return
    }
    if (bot.currentWindow && windowKind(bot).kind !== KINDS.dungeon) closeWindow(bot)
    const here = await refreshWorld()
    if (here.startsWith(INSTANCE_PREFIX)) {
      await instanceTick()
    } else {
      await hubTick()
    }
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    world = ''
    log(bot.stressName, 'dungeon loop start')
    note(bot, 'dungeon loop start', 'dungeon')
    const halt = runEvery(bot, 450, tick, (err) => log(bot.stressName, `dungeon tick: ${err.message}`))
    stop = () => {
      halt()
      running = false
      bot.qaOwnWindows = false
    }
    return stop
  }
}

function posKey(pos) {
  return `${Math.floor(pos.x)},${Math.floor(pos.y)},${Math.floor(pos.z)}`
}
