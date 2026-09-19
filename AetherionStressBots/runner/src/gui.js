/**
 * Shared window helpers. Roles should call these instead of inventing click sequences.
 * Slot numbers match AetherionItems MarketService / Quests LangMenu / QuestAcceptGUI.
 */

export const SLOTS = {
  languageEnglish: 11,
  languageGerman: 15,
  questAccept: 11,
  questDecline: 15,
  marketBack: 45,
  marketPrev: 46,
  marketInfo: 47,
  marketNext: 48,
  marketList: 49,
  marketCollect: 53,
  priceSuggested: 2,
  confirmBuy: 11,
  confirmCancel: 15
}

export function stripLegacy(text) {
  return String(text || '').replace(/§./g, '').replace(/\u00a7./g, '')
}

export function windowTitle(bot) {
  const window = bot?.currentWindow
  if (!window) return ''
  const raw = window.title ?? window.windowTitle ?? ''
  if (typeof raw === 'string') return stripLegacy(raw)
  try {
    if (typeof raw.toString === 'function') return stripLegacy(raw.toString())
  } catch {
    /* ignore */
  }
  return ''
}

export function titleMatches(title, ...needles) {
  const plain = stripLegacy(title).toLowerCase()
  return needles.some((needle) => plain.includes(String(needle).toLowerCase()))
}

export function isLanguageWindow(title) {
  const plain = stripLegacy(title).toLowerCase()
  return plain.includes('language') || plain.includes('sprache')
}

export function isAuctionWindow(title) {
  return titleMatches(title, 'auction house', 'auction')
}

export function isBazaarWindow(title) {
  return titleMatches(title, 'bazaar')
}

export function isQuestOfferWindow(title) {
  return titleMatches(title, 'quest offer', 'quest')
}

export function isListPriceWindow(title) {
  return titleMatches(title, 'list ·', 'list · bazaar', 'list · auction')
}

export function isConfirmPurchaseWindow(title) {
  return titleMatches(title, 'confirm purchase')
}

export function isBoosterWindow(title) {
  return titleMatches(title, 'booster')
}

export function hasWindow(bot) {
  return Boolean(bot?.currentWindow)
}

export async function clickSlot(bot, slot, { mouseButton = 0, mode = 0 } = {}) {
  if (!bot?.currentWindow) {
    throw new Error('no window')
  }
  await bot.clickWindow(slot, mouseButton, mode)
}

export async function closeWindow(bot) {
  try {
    if (bot.currentWindow) {
      bot.closeWindow(bot.currentWindow)
    }
  } catch {
    try { bot.closeWindow() } catch { /* ignore */ }
  }
}

export function waitForWindow(bot, predicate, timeoutMs = 4000) {
  return new Promise((resolve, reject) => {
    const start = Date.now()
    const tick = () => {
      const title = windowTitle(bot)
      if (bot.currentWindow && predicate(title, bot.currentWindow)) {
        resolve(title)
        return
      }
      if (Date.now() - start > timeoutMs) {
        reject(new Error('window timeout'))
        return
      }
      setTimeout(tick, 120)
    }
    tick()
  })
}

export function windowItems(bot) {
  const window = bot?.currentWindow
  if (!window) return []
  return (window.slots || []).map((item, slot) => ({ slot, item }))
}

export function findClickableSlots(bot, { skipChrome = true } = {}) {
  const chrome = new Set([
    SLOTS.marketBack, SLOTS.marketPrev, SLOTS.marketInfo,
    SLOTS.marketNext, SLOTS.marketList, SLOTS.marketCollect
  ])
  const out = []
  for (const { slot, item } of windowItems(bot)) {
    if (!item || !item.name) continue
    const name = item.name.toLowerCase()
    if (name.includes('glass_pane') || name.includes('barrier')) continue
    if (skipChrome && chrome.has(slot)) continue
    out.push({ slot, item, name: itemName(item) })
  }
  return out
}

export function itemName(item) {
  if (!item) return ''
  const display = item.customName || item.displayName || item.name || ''
  return stripLegacy(typeof display === 'string' ? display : String(display))
}

export function itemLore(item) {
  if (!item) return ''
  const lore = item.customLore || item.lore || []
  if (Array.isArray(lore)) {
    return stripLegacy(lore.map((line) => (typeof line === 'string' ? line : String(line))).join(' '))
  }
  return stripLegacy(String(lore))
}

export function parseQuestAcceptCommand(text) {
  const match = String(text || '').match(/\/aetherionquest\s+accept\s+(\S+)/i)
  return match ? `/aetherionquest accept ${match[1]}` : null
}

export async function clickNamed(bot, matcher) {
  const slots = findClickableSlots(bot, { skipChrome: false })
  const hit = slots.find((entry) => matcher(entry.name, entry.item, entry.slot))
  if (!hit) {
    throw new Error('no matching item')
  }
  await clickSlot(bot, hit.slot)
  return hit
}
