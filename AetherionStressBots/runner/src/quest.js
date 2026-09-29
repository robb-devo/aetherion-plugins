import pathfinderPkg from 'mineflayer-pathfinder'
import { clickSlot, closeWindow, isQuestOfferWindow, parseQuestAcceptCommand, SLOTS, waitForWindow, windowTitle } from './gui.js'
import { applyIslandMovements, cancelPath, horizontalDistance, wanderOnIsland } from './safety.js'
import { assignGait, followIfNeeded, idleFidget, setNearGoal, tunePathfinder } from './motion.js'
import { markError, note, sleep } from './util.js'
import { ACTIVITIES } from './playstyle.js'

const { goals, Movements, pathfinder } = pathfinderPkg

const SKIP_NAMES = ['item', 'experience', 'armor_stand', 'fishing_bobber', 'area_effect_cloud']

export function createQuestLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  assignGait(bot)
  const searchRadius = cfg.searchRadius ?? 16
  const wanderRadius = cfg.wanderRadius ?? 5
  const npcs = (cfg.npcs || []).map((npc) => ({
    id: npc.id || npc.name,
    name: String(npc.name || npc.id || '').toLowerCase(),
    x: Number(npc.x),
    y: Number(npc.y),
    z: Number(npc.z)
  })).filter((npc) => Number.isFinite(npc.x) && Number.isFinite(npc.z))
  let running = false
  let lastTalk = 0
  let lastFidget = 0
  let lastNpcId = null
  let padIndex = 0
  let lastPadSwitch = 0

  bot.on('message', (json) => {
    const text = typeof json.toString === 'function' ? json.toString() : String(json)
    const command = parseQuestAcceptCommand(text)
    if (command && Date.now() - lastTalk < 12_000) {
      try {
        bot.chat(command)
        note(bot, `quest accept ${command.split(' ').pop()}`, ACTIVITIES.questDialog)
      } catch {
        /* ignore */
      }
    }
  })

  function currentPad() {
    if (!npcs.length) return bot.qaHome
    if (Date.now() - lastPadSwitch > 9000) {
      padIndex = (padIndex + 1) % npcs.length
      lastPadSwitch = Date.now()
    }
    return npcs[padIndex]
  }

  function pickNpc() {
    let best = null
    let bestDist = searchRadius
    for (const entity of Object.values(bot.entities)) {
      if (!entity || entity === bot.entity) continue
      const name = (entity.username || entity.displayName || entity.name || '').toString().toLowerCase()
      if (!name) continue
      if (SKIP_NAMES.some((key) => name.includes(key))) continue
      if (entity.type === 'orb' || entity.type === 'object') continue
      if (name.startsWith('qa') || name.startsWith('stress')) continue
      const listed = npcs.some((npc) => npc.name && name.includes(npc.name))
      const isPerson = listed || entity.type === 'player' || entity.type === 'mob' || name.includes('villager')
        || name.includes('egon') || name.includes('lark') || name.includes('temper')
        || name.includes('foreman') || name.includes('quartermaster') || name.includes('npc')
        || name.includes('maren') || name.includes('twig') || name.includes('forgehand')
      if (!isPerson && entity.type !== 'player') continue
      if (entity.username && entity.username === bot.username) continue
      const dist = bot.entity.position.distanceTo(entity.position)
      const score = listed ? dist - 4 : dist
      if (score < bestDist && entity.id !== lastNpcId) {
        best = entity
        bestDist = score
      }
    }
    return best
  }

  async function talk(entity) {
    lastTalk = Date.now()
    lastNpcId = entity.id
    note(bot, `quest talk ${entity.username || entity.name || 'npc'}`, ACTIVITIES.questDialog)
    try {
      await bot.lookAt(entity.position.offset(0, entity.height || 1.6, 0), true)
    } catch { /* ignore */ }
    try {
      await bot.activateEntity(entity)
    } catch {
      try { await bot.activateEntity(entity) } catch { /* ignore */ }
    }
    try {
      await waitForWindow(bot, isQuestOfferWindow, 2800)
      await clickSlot(bot, SLOTS.questAccept)
      note(bot, 'quest accept gui', ACTIVITIES.questDialog)
      await sleep(300)
      await closeWindow(bot)
    } catch {
      if (isQuestOfferWindow(windowTitle(bot))) {
        try {
          await clickSlot(bot, SLOTS.questAccept)
          note(bot, 'quest accept gui', ACTIVITIES.questDialog)
        } catch { /* ignore */ }
      }
    }
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(tunePathfinder(bot, applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 2 })))
    }
    if (isQuestOfferWindow(windowTitle(bot))) {
      try {
        await clickSlot(bot, SLOTS.questAccept)
        note(bot, 'quest accept gui', ACTIVITIES.questDialog)
      } catch { /* ignore */ }
      return
    }
    const npc = pickNpc()
    if (!npc) {
      bot.qaActivity = bot.pathfinder.isMoving() ? 'pathing' : ACTIVITIES.questDialog
      const pad = currentPad()
      if (pad && horizontalDistance(bot.entity.position, pad) > 3.5) {
        note(bot, `path npc pad ${pad.name || pad.id || ''}`, 'pathing')
        setNearGoal(bot, goals, pad, 2)
      } else {
        wanderOnIsland(bot, bot.qaHome || bot.entity.position, wanderRadius, goals)
      }
      if (Date.now() - lastFidget > 4000) {
        lastFidget = Date.now()
        idleFidget(bot, ACTIVITIES.questDialog)
      }
      return
    }
    const dist = bot.entity.position.distanceTo(npc.position)
    if (dist > 3.2) {
      note(bot, `path npc ${npc.username || npc.name || ''}`, 'pathing')
      followIfNeeded(bot, goals, npc, 2)
      return
    }
    cancelPath(bot)
    if (Date.now() - lastTalk < 2800) return
    await talk(npc)
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, 'quest loop start')
    note(bot, 'quest loop start', ACTIVITIES.questDialog)
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `quest tick: ${err.message}`)
      })
    }, 380)
    bot.once('end', () => clearInterval(handle))
  }
}
