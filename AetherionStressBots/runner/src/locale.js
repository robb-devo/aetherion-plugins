import { clickSlot, closeWindow, KINDS, SLOTS, windowKind } from './gui.js'
import { note } from './util.js'

/**
 * Dismiss the first-join Language / Sprache picker (Quests LangMenu) by clicking English, slot 11.
 * The plugin already sets English on join, so this only fires when that raced or Quests reloaded.
 * The menu is recognised by its slots (English map, Deutsch book): its title is pack glyphs.
 */
export function attachLocale(bot, log) {
  let lastDismiss = 0

  const dismiss = async (reason) => {
    if (Date.now() - lastDismiss < 800) return
    if (windowKind(bot).kind !== KINDS.language) return
    lastDismiss = Date.now()
    try {
      await clickSlot(bot, SLOTS.languageEnglish)
      note(bot, `language dismissed (${reason})`, bot.qaActivity || 'idle')
      log(bot.stressName, 'language picker → English')
    } catch {
      try { bot.chat('/language en') } catch { /* ignore */ }
      await closeWindow(bot)
      note(bot, 'language closed (fallback /language en)', bot.qaActivity || 'idle')
    }
  }

  bot.on('windowOpen', () => {
    // Slots arrive right after the open packet.
    setTimeout(() => dismiss('windowOpen').catch(() => {}), 250)
  })

  return { dismiss }
}
