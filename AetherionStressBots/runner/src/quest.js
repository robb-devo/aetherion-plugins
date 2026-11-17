import pathfinderPkg from 'mineflayer-pathfinder'
import { clickSlot, closeWindow, isQuestOfferWindow, parseQuestAcceptCommand, SLOTS, waitForWindow, windowTitle } from './gui.js'
import { applyIslandMovements, cancelPath, wanderOnIsland } from './safety.js'
import { markError, note, sleep } from './util.js'
import { ACTIVITIES, fidget } from './playstyle.js'
import { shouldYield } from './mind.js'

const { goals, Movements, pathfinder } = pathfinderPkg

const SKIP_NAMES = ['item', 'experience', 'armor_stand', 'fishing_bobber', 'area_effect_cloud']

export function createQuestLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const searchRadius = cfg.searchRadius ?? 10
  const wanderRadius = cfg.wanderRadius ?? 5
  let running = false
  let lastTalk = 0
  let lastFidget = 0
  let lastNpcId = null

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
      const isPerson = entity.type === 'player' || entity.type === 'mob' || name.includes('villager')
        || name.includes('egon') || name.includes('lark') || name.includes('temper')
        || name.includes('foreman') || name.includes('quartermaster') || name.includes('npc')
      if (!isPerson && entity.type !== 'player') continue
      if (entity.username && entity.username === bot.username) continue
      const dist = bot.entity.position.distanceTo(entity.position)
      if (dist < bestDist && entity.id !== lastNpcId) {
        best = entity
        bestDist = dist
      }
    }
    if (!best) {
      for (const entity of Object.values(bot.entities)) {
        if (!entity || entity === bot.entity) continue
        if (entity.type !== 'player' && entity.type !== 'mob') continue
        const name = (entity.username || entity.name || '').toLowerCase()
        if (name.startsWith('qa') || name.startsWith('stress')) continue
        const dist = bot.entity.position.distanceTo(entity.position)
        if (dist < bestDist) {
          best = entity
          bestDist = dist
        }
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
      /* FancyNpcs may ignore a missed packet; retry once */
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
    if (shouldYield(bot)) return
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 2 }))
    }
    if ((bot.qaPersona?.patience ?? 1) < 0.32 && Math.random() < 0.18) {
      note(bot, 'wandered off mid-errand', 'pathing')
      wanderOnIsland(bot, bot.qaHome || bot.entity.position, wanderRadius, goals)
      return
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
      wanderOnIsland(bot, bot.qaHome || bot.entity.position, wanderRadius, goals)
      if (Date.now() - lastFidget > 4000) {
        lastFidget = Date.now()
        fidget(bot, ACTIVITIES.questDialog)
      }
      return
    }
    const dist = bot.entity.position.distanceTo(npc.position)
    if (dist > 3.2) {
      note(bot, `path npc ${npc.username || npc.name || ''}`, 'pathing')
      bot.pathfinder.setGoal(new goals.GoalFollow(npc, 2), true)
      return
    }
    cancelPath(bot)
    if (Date.now() - lastTalk < 2200) return
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
