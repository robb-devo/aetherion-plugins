import { closeWindow, KINDS, slotItem, SLOTS, windowKind } from './gui.js'
import { waitFor } from './npc.js'
import { jitter, note, runEvery, sleep } from './util.js'
import { telemetry } from './telemetry.js'
import { itemName } from './gui.js'

/**
 * Lucky Vince's slots: `/gamble slots`, pull the lever a few times with the table's default bet, watch the
 * reels stop, leave. Only the spin button is pressed (never the staff rigger or auto-spin), so a bot spends at
 * most a few small bets of its pocket coins per visit.
 */
export function createCasinoLoop(bot, cfg, log) {
  const spinsPerVisit = cfg.spinsPerVisit ?? 3
  const stats = (bot.qaStats = bot.qaStats || {})
  let running = false

  function spinReady() {
    const spin = slotItem(bot, SLOTS.slotsSpin)
    return spin?.name === 'lever'
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended || bot.qaTravelling) return
    if (bot.qaServer && bot.qaServer !== 'mmo-r') return
    bot.qaOwnWindows = true
    try {
      if (bot.currentWindow) closeWindow(bot)
      const started = Date.now()
      bot.chat('/gamble slots')
      const opened = await waitFor(() => windowKind(bot).kind === KINDS.slots, 5000, 50)
      telemetry.menu('/gamble slots', opened ? Date.now() - started : null)
      if (!opened) {
        note(bot, 'no slots table', 'casino')
        return
      }
      for (let i = 0; i < spinsPerVisit; i++) {
        if (!(await waitFor(spinReady, 8000, 100))) break
        const label = itemName(slotItem(bot, SLOTS.slotsSpin), bot.registry).toLowerCase()
        if (!label.includes('spin')) break
        await bot.clickWindow(SLOTS.slotsSpin, 0, 0)
        stats.spins = (stats.spins || 0) + 1
        telemetry.count('casino spins')
        note(bot, 'spin', 'casino')
        await sleep(jitter(1200, 0.3))
        await waitFor(spinReady, 9000, 150)
        await sleep(jitter(900, 0.4))
      }
    } finally {
      closeWindow(bot)
      bot.qaOwnWindows = false
    }
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    log(bot.stressName, 'casino loop start')
    note(bot, 'casino loop start', 'casino')
    const halt = runEvery(bot, cfg.visitGapMs ?? 30_000, tick, (err) => log(bot.stressName, `casino tick: ${err.message}`))
    stop = () => {
      halt()
      running = false
      bot.qaOwnWindows = false
    }
    return stop
  }
}
