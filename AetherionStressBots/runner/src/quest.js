import pathfinderPkg from 'mineflayer-pathfinder'
import { clickSlot, closeWindow, KINDS, SLOTS, windowKind } from './gui.js'
import { applyIslandMovements, cancelPath, wanderOnIsland } from './safety.js'
import { clickCommands } from './chat.js'
import { jitter, markError, note, runEvery, sleep } from './util.js'
import { ACTIVITIES, fidget } from './playstyle.js'

const { goals, Movements, pathfinder } = pathfinderPkg

const BOT_PREFIXES = ['qa', 'stress']

/**
 * Quest NPCs are FancyNpcs packet players: player entities that are not listed in the tab list.
 * Real players (and other bots) are listed, so the bot never right-clicks a person.
 */
export function isNpcEntity(entity, players) {
  if (!entity || entity.type !== 'player' || !entity.username) return false
  const lower = String(entity.username).toLowerCase()
  if (BOT_PREFIXES.some((prefix) => lower.startsWith(prefix))) return false
  const entry = players?.[entity.username]
  return !entry || entry.listed === false
}

/** Which clickable chat command to run: accept a quest most of the time, else any reply. */
export function chooseReply(commands, rng = Math.random) {
  const list = (commands || []).filter((cmd) => /^\/(aetherionquest|aetalk)\b/i.test(cmd))
  if (list.length === 0) return null
  const accept = list.find((cmd) => /\/aetherionquest\s+accept\s+/i.test(cmd))
  if (accept && rng() < 0.75) return accept
  const replies = list.filter((cmd) => !/\s(decline|abandon|abort)\b/i.test(cmd))
  const pool = replies.length ? replies : list
  return pool[Math.floor(rng() * pool.length)]
}

/**
 * Walk up to quest NPCs, right-click, and play the TalkUx dialog through its mirrored chat buttons
 * (/aetalk pick …, /aetherionquest accept …). A Quest Offer window is answered by its slots.
 */
export function createQuestLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const searchRadius = cfg.searchRadius ?? 16
  const wanderRadius = cfg.wanderRadius ?? 5
  const npcCooldownMs = cfg.npcCooldownMs ?? 60_000
  const stats = (bot.qaStats = bot.qaStats || {})
  const cooldown = new Map()
  let running = false
  let lastFidget = 0
  let pending = []

  bot.on('message', (message) => {
    const commands = clickCommands(message)
    if (commands.length) pending = commands
  })

  function pickNpc() {
    const now = Date.now()
    let best = null
    let bestDist = searchRadius
    for (const entity of Object.values(bot.entities)) {
      if (!isNpcEntity(entity, bot.players)) continue
      if ((cooldown.get(entity.username) || 0) > now) continue
      const dist = bot.entity.position.distanceTo(entity.position)
      if (dist < bestDist) {
        best = entity
        bestDist = dist
      }
    }
    return best
  }

  async function answerOffer(info) {
    if (info.state === 'offer' || (info.state === 'abort_current' && Math.random() < 0.25)) {
      await clickSlot(bot, SLOTS.questAccept)
      stats.accepts = (stats.accepts || 0) + 1
      note(bot, 'quest accept (offer)', ACTIVITIES.questDialog)
    } else {
      await clickSlot(bot, SLOTS.questDecline)
      note(bot, `quest offer ${info.state}`, ACTIVITIES.questDialog)
    }
    await sleep(300)
    await closeWindow(bot)
  }

  async function dialog(npc) {
    // Up to four steps: greeting → topic → offer → accept.
    for (let step = 0; step < 4; step++) {
      const until = Date.now() + 4500
      while (Date.now() < until && pending.length === 0 && !bot.currentWindow) await sleep(150)
      const info = windowKind(bot)
      if (info.kind === KINDS.questOffer) {
        await answerOffer(info)
        return
      }
      if (pending.length === 0) return
      const choice = chooseReply(pending)
      pending = []
      if (!choice) return
      // Read the line first, like a person would.
      await sleep(jitter(1400, 0.5))
      bot.chat(choice)
      if (/accept/i.test(choice)) {
        stats.accepts = (stats.accepts || 0) + 1
        note(bot, `quest accept ${choice.split(' ').pop()}`, ACTIVITIES.questDialog)
        return
      }
      stats.replies = (stats.replies || 0) + 1
      note(bot, `reply to ${npc.username}`, ACTIVITIES.questDialog)
    }
  }

  async function talk(npc) {
    cooldown.set(npc.username, Date.now() + jitter(npcCooldownMs, 0.4))
    pending = []
    note(bot, `talk ${npc.username}`, ACTIVITIES.questDialog)
    try {
      await bot.lookAt(npc.position.offset(0, npc.height || 1.6, 0), true)
    } catch { /* ignore */ }
    try {
      await bot.activateEntity(npc)
    } catch { /* FancyNpcs may ignore one packet */ }
    stats.talks = (stats.talks || 0) + 1
    await dialog(npc)
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 2 }))
    }
    const info = windowKind(bot)
    if (info.kind === KINDS.questOffer) {
      await answerOffer(info)
      return
    }
    if (bot.currentWindow) return
    const npc = pickNpc()
    if (!npc) {
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : ACTIVITIES.questDialog
      if (!bot.pathfinder.isMoving()) wanderOnIsland(bot, bot.qaHome || bot.entity.position, wanderRadius, goals)
      if (Date.now() - lastFidget > 4000) {
        lastFidget = Date.now()
        fidget(bot, ACTIVITIES.questDialog)
      }
      return
    }
    const dist = bot.entity.position.distanceTo(npc.position)
    if (dist > 3) {
      note(bot, `walk to ${npc.username}`, 'pathing')
      bot.pathfinder.setGoal(new goals.GoalNear(npc.position.x, npc.position.y, npc.position.z, 2))
      const until = Date.now() + 10_000
      while (Date.now() < until && bot.pathfinder.isMoving() && bot.entity.position.distanceTo(npc.position) > 3) {
        if (bot.qaSuspended) return
        await sleep(200)
      }
      if (bot.entity.position.distanceTo(npc.position) > 3.5) {
        cancelPath(bot)
        cooldown.set(npc.username, Date.now() + 20_000)
        return
      }
    }
    cancelPath(bot)
    await talk(npc)
    await sleep(jitter(2500, 0.4))
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, 'quest loop start (NPC talk + chat replies)')
    note(bot, 'quest loop start', ACTIVITIES.questDialog)
    runEvery(bot, 400, tick, (err) => {
      markError(bot, err)
      log(bot.stressName, `quest tick: ${err.message}`)
    })
  }
}
