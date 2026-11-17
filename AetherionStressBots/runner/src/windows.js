import { closeWindow, KINDS, windowKind } from './gui.js'
import { note } from './util.js'

/**
 * Stray windows stall every role loop (they all wait while a window is open). A window nobody is
 * working with — a booster pick, a quest board, a menu from a misclick — is closed after a while,
 * like a player pressing Escape. Roles that drive a window keep it: market pages for trade bots,
 * Quest Offer for quest bots.
 */
export function attachWindowWatchdog(bot, log, { maxOpenMs = 7000 } = {}) {
  let openedAt = 0

  bot.on('windowOpen', () => {
    openedAt = Date.now()
  })
  bot.on('windowClose', () => {
    openedAt = 0
  })

  const handle = setInterval(() => {
    if (!bot.currentWindow) {
      openedAt = 0
      return
    }
    if (!openedAt) openedAt = Date.now()
    const info = windowKind(bot)
    const owned = (bot.role === 'trade' && [KINDS.market, KINDS.price, KINDS.confirm].includes(info.kind))
      || (bot.role === 'quest' && info.kind === KINDS.questOffer)
    const limit = owned ? maxOpenMs * 3 : maxOpenMs
    if (Date.now() - openedAt > limit) {
      openedAt = 0
      closeWindow(bot)
      note(bot, `closed stray window (${info.kind}${info.title ? ' ' + info.title.slice(0, 24) : ''})`, bot.qaActivity || 'idle')
      log(bot.stressName, `closed stray ${info.kind} window`)
    }
  }, 1000)
  bot.once('end', () => clearInterval(handle))
}
