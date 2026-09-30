import { applyIslandMovements } from './safety.js'
import { avoidRailBlocks, shouldPeekMenu } from './move.js'
import { fidget as utilFidget, jitter, note, sleep, tossJunk } from './util.js'

const MENU_COMMANDS = ['skills', 'pets', 'guide', 'trades']

export const FLAGS = {
  TRADER: 'TRADER'
}

export const ACTIVITIES = {
  idle: 'idle',
  pathing: 'pathing',
  mining: 'mining',
  foraging: 'foraging',
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
  'mining', 'foraging', 'pathing', 'catching', 'fishing',
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
    const moving = !!bot.pathfinder?.isMoving?.()
    const menus = shouldPeekMenu({
      activity,
      digging: !!bot.qaDigging,
      gathering: !!bot.qaGathering,
      moving
    })

    if (menus && now - lastFidget > jitter(7000, 0.4)) {
      lastFidget = now
      if (Math.random() < 0.55) fidget(bot, activity)
    }

    if (menus && now - lastInv > jitter(18_000, 0.45)) {
      lastInv = now
      tossJunk(bot)
      if (Math.random() < 0.2) cycleHotbar(bot)
    }

    if (menus && now - lastMenu > jitter(48_000, 0.35)) {
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
    if (Number.isFinite(bot.pathfinder.thinkTimeout)) {
      bot.pathfinder.thinkTimeout = Math.max(bot.pathfinder.thinkTimeout, 800)
    } else {
      bot.pathfinder.thinkTimeout = 800
    }
    if (Number.isFinite(bot.pathfinder.tickTimeout)) {
      bot.pathfinder.tickTimeout = Math.max(bot.pathfinder.tickTimeout, 40)
    }
  }
  const canDig = movements?.canDig === true
  const sprint = movements?.allowSprinting === true
  const tuned = applyIslandMovements(movements, {
    canDig,
    maxDrop: movements?.maxDropDown ?? 2,
    bot,
    sprint
  })
  return avoidRailBlocks(tuned, bot)
}

export function fidget(bot, activity = 'idle') {
  if (typeof utilFidget === 'function') {
    try {
      utilFidget(bot, activity)
      return
    } catch {
      /* fall through */
    }
  }
  const roll = Math.random()
  try {
    if (!bot?.pathfinder && roll < 0.34) {
      bot.setControlState('jump', true)
      setTimeout(() => bot.setControlState('jump', false), 180)
      note(bot, 'fidget jump', activity)
    } else if (roll < 0.67) {
      bot.swingArm()
      note(bot, 'fidget swing', activity)
    } else {
      bot.look(Math.random() * Math.PI * 2, (Math.random() - 0.5) * 0.4, true).catch(() => {})
      note(bot, 'fidget look', activity)
    }
  } catch {
    /* ignore */
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
