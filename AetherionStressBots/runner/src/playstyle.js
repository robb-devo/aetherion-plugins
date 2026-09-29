import { applyIslandMovements } from './safety.js'
import { idleFidget } from './motion.js'
import { fidget as utilFidget, jitter, note, sleep, tossJunk } from './util.js'

const MENU_COMMANDS = ['skills', 'pets', 'guide', 'trades']
const BUSY = new Set([
  'fighting', 'combat', 'catching', 'fishing', 'mining', 'foraging', 'farming', 'questing',
  'quest_dialog', 'ah', 'bazaar', 'minigame', 'pad_hop', 'trading'
])

export const FLAGS = {
  TRADER: 'TRADER'
}

export const ACTIVITIES = {
  idle: 'idle',
  pathing: 'pathing',
  mining: 'mining',
  foraging: 'foraging',
  farming: 'farming',
  catching: 'catching',
  roaming: 'roaming',
  combat: 'combat',
  fishing: 'fishing',
  ah: 'ah',
  bazaar: 'bazaar',
  questDialog: 'quest_dialog',
  minigame: 'minigame',
  padHop: 'pad_hop',
  trading: 'trading'
}

const WORKING = new Set([
  'mining', 'foraging', 'farming', 'pathing', 'catching', 'fishing',
  'ah', 'bazaar', 'quest_dialog', 'minigame', 'pad_hop', 'combat', 'trading',
  'fighting', 'questing', 'hopping'
])

/**
 * Background human noise shared by every role: look/jump, toss junk, peek GUIs.
 * Skills have no hotkeys — opening /skills is the closest player-like "ability" use.
 */
export function attachPlaystyle(bot, log) {
  let lastMenu = Date.now() + jitter(12_000, 0.6)
  let lastInv = Date.now()
  let lastFidget = Date.now()

  const handle = setInterval(() => {
    tick().catch((err) => log?.(bot.stressName, `playstyle: ${err.message}`))
  }, jitter(3500, 0.25))
  bot.once('end', () => clearInterval(handle))

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
    const now = Date.now()
    const activity = bot.qaActivity || 'idle'
    const busy = BUSY.has(activity) || bot.qaDigging || bot.qaGathering

    if (!busy && now - lastFidget > jitter(7000, 0.4)) {
      lastFidget = now
      if (Math.random() < 0.55) fidget(bot, activity)
    }

    if (now - lastInv > jitter(18_000, 0.45)) {
      lastInv = now
      tossJunk(bot, { keepResources: bot.role === 'trade' || bot.role === 'farm' })
      if (Math.random() < 0.2 && !busy) cycleHotbar(bot)
    }

    if (!busy && now - lastMenu > jitter(48_000, 0.35)) {
      lastMenu = now
      const cmd = MENU_COMMANDS[Math.floor(Math.random() * MENU_COMMANDS.length)]
      note(bot, `/${cmd}`, activity === 'idle' ? 'browsing' : activity)
      try {
        bot.chat('/' + cmd)
      } catch {
        /* ignore */
      }
      await sleep(jitter(1600, 0.4))
      closeWindow(bot)
    }
  }
}

export function closeWindow(bot) {
  try {
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
  } catch {
    /* ignore */
  }
}

export function cycleHotbar(bot) {
  try {
    const slot = Math.floor(Math.random() * 9)
    bot.setQuickBarSlot(slot)
    note(bot, `hotbar ${slot}`, bot.qaActivity)
  } catch {
    /* ignore */
  }
}

/** Skip this action like a distracted player. */
export function maybeSkip(chance = 0.12) {
  return Math.random() < chance
}

export function isWorkingActivity(activity) {
  return WORKING.has(String(activity || ''))
}

export function playstyleOf(role, cfg = {}) {
  const flags = []
  if (role === 'trade' || cfg.unlockTrader) flags.push(FLAGS.TRADER)
  return {
    role,
    flags,
    starterCoins: role === 'trade' ? (cfg.starterCoins ?? 2500) : (cfg.pocketCoins ?? 250),
    fidgetMs: cfg.fidgetMs ?? 4500,
    idleGoalMs: cfg.idleGoalMs ?? 8000
  }
}

export function applyPathfinderDefaults(bot, movements) {
  if (bot.pathfinder) {
    bot.pathfinder.thinkTimeout = Math.max(Number(bot.pathfinder.thinkTimeout) || 0, 2200)
    if (Number.isFinite(bot.pathfinder.tickTimeout)) {
      bot.pathfinder.tickTimeout = Math.max(bot.pathfinder.tickTimeout, 50)
    } else {
      bot.pathfinder.tickTimeout = 50
    }
  }
  return applyIslandMovements(movements, { canDig: false, maxDrop: 2 })
}

export function fidget(bot, activity = 'idle') {
  if (bot?.pathfinder && typeof bot.pathfinder.isMoving === 'function' && bot.pathfinder.isMoving()) {
    return
  }
  try {
    idleFidget(bot, activity)
  } catch {
    if (typeof utilFidget === 'function') {
      try { utilFidget(bot, activity) } catch { /* ignore */ }
    }
  }
}

export function takeIdleGoal(bot) {
  if (!bot.qaNeedNewGoal) return false
  bot.qaNeedNewGoal = false
  return true
}

export function markWorking(bot) {
  bot.qaLastWorkMs = Date.now()
}

export function shouldKeepPath(bot) {
  if (bot.targetDigBlock) return true
  if (bot.pathfinder?.isMoving?.()) return true
  return isWorkingActivity(bot.qaActivity)
}
