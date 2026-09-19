import { applyIslandMovements } from './safety.js'
import { note } from './util.js'

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
  'ah', 'bazaar', 'quest_dialog', 'minigame', 'pad_hop', 'combat', 'trading'
])

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
  return applyIslandMovements(movements, { canDig: false, maxDrop: 2 })
}

export function fidget(bot, activity = 'idle') {
  const roll = Math.random()
  try {
    if (roll < 0.34) {
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
