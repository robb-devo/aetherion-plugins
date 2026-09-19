import { clickSlot, closeWindow, isLanguageWindow, SLOTS, windowTitle } from './gui.js'
import { note } from './util.js'

/**
 * Force English and dismiss Language / Sprache GUIs (Quests + Beta).
 * English is slot 11 in both menus — verified against LangMenu / LanguageMenu.
 */
export function attachLocale(bot, log) {
  let lastDismiss = 0

  const dismiss = async (reason) => {
    if (Date.now() - lastDismiss < 800) return
    lastDismiss = Date.now()
    const title = windowTitle(bot)
    if (!isLanguageWindow(title) && !bot.currentWindow) {
      try { bot.chat('/language en') } catch { /* ignore */ }
      return
    }
    if (!isLanguageWindow(title)) return
    try {
      await clickSlot(bot, SLOTS.languageEnglish)
      note(bot, `language dismissed (${reason || title})`, bot.qaActivity || 'idle')
      log(bot.stressName, `language dismissed: ${title}`)
    } catch {
      try { bot.chat('/language en') } catch { /* ignore */ }
      await closeWindow(bot)
      note(bot, 'language closed (fallback en)', bot.qaActivity || 'idle')
    }
  }

  bot.on('windowOpen', () => {
    setTimeout(() => {
      dismiss('windowOpen').catch(() => {})
    }, 180)
  })
  bot.on('spawn', () => {
    setTimeout(() => {
      try { bot.chat('/language en') } catch { /* ignore */ }
      dismiss('spawn').catch(() => {})
    }, 900)
  })

  const handle = setInterval(() => {
    if (isLanguageWindow(windowTitle(bot))) {
      dismiss('poll').catch(() => {})
    }
  }, 2500)
  bot.once('end', () => clearInterval(handle))

  return { dismiss }
}
