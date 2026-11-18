import { closeWindow, containerSize, nextPageSlots, windowKind } from './gui.js'
import { waitFor } from './npc.js'
import { jitter, note, runEvery, sleep } from './util.js'
import { telemetry } from './telemetry.js'

/**
 * Player menus, one after another: run the command, time how long the server takes to open the window, page
 * forward on the arrow buttons a couple of times (timing each page) and close it again like Escape. Nothing in a
 * menu is bought, sold, reset or claimed: the only buttons pressed are arrows named next / more / ▶.
 * The timings go to /botreport ("Menus: /skills 140ms (max 410) …"), so a slow menu stands out.
 */
export const DEFAULT_MENUS = [
  '/aetherion', '/skills', '/collection', '/codex', '/bestiary', '/pets', '/bazaar', '/ah', '/shardshop',
  '/trades', '/atrecipes', '/spawns', '/gamble'
]

export function createMenusLoop(bot, cfg, log) {
  const menus = Array.isArray(cfg.commands) && cfg.commands.length ? cfg.commands : DEFAULT_MENUS
  const openTimeoutMs = cfg.openTimeoutMs ?? 5000
  const stats = (bot.qaStats = bot.qaStats || {})
  let running = false
  let index = Math.floor(Math.random() * menus.length)

  async function openTimed(command) {
    if (bot.currentWindow) {
      closeWindow(bot)
      await sleep(300)
    }
    const started = Date.now()
    bot.chat(command)
    const opened = await waitFor(() => Boolean(bot.currentWindow), openTimeoutMs, 40)
    const ms = Date.now() - started
    telemetry.menu(command, opened ? ms : null)
    return opened ? ms : null
  }

  async function pageForward(command) {
    for (let page = 0; page < (cfg.maxPages ?? 2); page++) {
      const slots = nextPageSlots(bot)
      if (!slots.length || !bot.currentWindow) return
      const before = JSON.stringify((bot.currentWindow.slots || []).slice(0, containerSize(bot.currentWindow)).map((s) => s?.name || ''))
      const started = Date.now()
      try {
        await bot.clickWindow(slots[0], 0, 0)
      } catch {
        return
      }
      const changed = await waitFor(() => {
        const window = bot.currentWindow
        if (!window) return true
        const now = JSON.stringify((window.slots || []).slice(0, containerSize(window)).map((s) => s?.name || ''))
        return now !== before
      }, 3000, 40)
      telemetry.menu(`${command} page`, changed ? Date.now() - started : null)
      await sleep(jitter(900, 0.4))
    }
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended || bot.qaTravelling) return
    if (bot.qaServer && bot.qaServer !== 'mmo-r') return
    const command = menus[index % menus.length]
    index++
    bot.qaOwnWindows = true
    try {
      const ms = await openTimed(command)
      if (ms == null) {
        note(bot, `${command}: no window`, 'menus')
        telemetry.problem(bot.stressName, `${command} opened no window within ${openTimeoutMs} ms`)
        return
      }
      stats.menus = (stats.menus || 0) + 1
      note(bot, `${command} ${ms} ms (${windowKind(bot).kind})`, 'menus')
      await sleep(jitter(1500, 0.4))
      await pageForward(command)
      await sleep(jitter(800, 0.4))
    } finally {
      closeWindow(bot)
      bot.qaOwnWindows = false
    }
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    log(bot.stressName, `menus loop start (${menus.length} menus)`)
    note(bot, 'menus loop start', 'menus')
    const halt = runEvery(bot, cfg.gapMs ?? 4000, tick, (err) => log(bot.stressName, `menus tick: ${err.message}`))
    stop = () => {
      halt()
      running = false
      bot.qaOwnWindows = false
    }
    return stop
  }
}
