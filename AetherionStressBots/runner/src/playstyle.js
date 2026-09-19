import { fidget, jitter, note, sleep, tossJunk } from './util.js'

const MENU_COMMANDS = ['skills', 'pets', 'guide', 'trades']
const BUSY = new Set(['fighting', 'catching', 'fishing', 'mining', 'foraging', 'questing'])

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
      tossJunk(bot)
      if (Math.random() < 0.2) cycleHotbar(bot)
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
