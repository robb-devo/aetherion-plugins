import { stripLegacy, toPlain } from './chat.js'

export { stripLegacy } from './chat.js'

/**
 * Shared window helpers. Roles should call these instead of inventing click sequences.
 * Slot numbers match AetherionItems MarketService / Quests LangMenu / QuestAcceptGUI.
 *
 * Titles are display-only on Aetherion: the Manager book pages (ChromePage / ManagerChrome) draw the
 * whole title from pack glyphs, so "Auction House" never appears in it. Windows are told apart by what
 * sits in their slots, the same way the server routes them by InventoryHolder.
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
  marketMine: 50,
  marketSwitch: 51,
  marketCollect: 53,
  priceSuggested: 2,
  priceCancel: 8,
  confirmBuy: 11,
  confirmCancel: 15
}

export const KINDS = {
  none: 'none',
  language: 'language',
  market: 'market',
  price: 'price',
  confirm: 'confirm',
  questOffer: 'quest_offer',
  other: 'other'
}

export function windowTitle(bot) {
  const window = bot?.currentWindow
  if (!window) return ''
  return toPlain(window.title ?? window.windowTitle ?? '', bot.registry)
}

export function titleMatches(title, ...needles) {
  const plain = stripLegacy(title).toLowerCase()
  return needles.some((needle) => plain.includes(String(needle).toLowerCase()))
}

/** Slots of the top inventory only (the container, not the player's own bag). */
export function containerSize(window) {
  if (!window) return 0
  if (Number.isFinite(window.inventoryStart)) return window.inventoryStart
  return Math.max(0, (window.slots?.length || 0) - 36)
}

export function slotItem(bot, slot) {
  const window = bot?.currentWindow
  if (!window || !window.slots) return null
  return window.slots[slot] || null
}

export function itemName(item, registry) {
  if (!item) return ''
  const custom = item.customName
  if (custom != null && custom !== '') {
    const text = toPlain(custom, registry)
    if (text) return text
  }
  return stripLegacy(item.displayName || item.name || '')
}

export function itemLore(item, registry) {
  if (!item) return ''
  const lore = item.customLore
  if (lore == null) return ''
  return toPlain(Array.isArray(lore) ? lore : [lore], registry)
}

function slotName(bot, slot) {
  return itemName(slotItem(bot, slot), bot?.registry).toLowerCase()
}

function slotType(bot, slot) {
  return String(slotItem(bot, slot)?.name || '')
}

/**
 * What the open window is, read from its contents.
 * Market browse also reports its counter ({@code auction} or {@code bazaar}).
 */
export function windowKind(bot) {
  const window = bot?.currentWindow
  if (!window) return { kind: KINDS.none }
  const size = containerSize(window)
  const title = windowTitle(bot).toLowerCase()

  if (size === 27 && slotName(bot, SLOTS.languageEnglish).includes('english')
      && slotName(bot, SLOTS.languageGerman).includes('deutsch')) {
    return { kind: KINDS.language }
  }
  if (title.includes('language') || title.includes('sprache')) {
    return { kind: KINDS.language }
  }
  if (size === 54 && slotType(bot, SLOTS.marketList) === 'writable_book'
      && slotName(bot, SLOTS.marketList).includes('sell')) {
    const info = slotName(bot, SLOTS.marketInfo)
    const channel = info.includes('bazaar') ? 'bazaar' : info.includes('auction') ? 'auction' : 'unknown'
    return { kind: KINDS.market, channel }
  }
  if (size === 9) {
    const names = [0, 1, 2, 3].map((slot) => slotName(bot, slot))
    if (names.filter((name) => name.includes('coins') && name.includes('%')).length >= 2) {
      return { kind: KINDS.price }
    }
  }
  if (size === 27) {
    const accept = slotType(bot, SLOTS.confirmBuy)
    const cancel = slotType(bot, SLOTS.confirmCancel)
    if (accept === 'lime_wool' && cancel === 'red_wool') {
      const label = slotName(bot, SLOTS.confirmBuy)
      const state = label.includes('take it back') ? 'reclaim' : label.includes('not enough') ? 'broke' : 'buy'
      return { kind: KINDS.confirm, state }
    }
    if ((accept === 'lime_concrete' || accept === 'barrier') && cancel === 'red_concrete') {
      const label = slotName(bot, SLOTS.questAccept)
      const state = accept === 'barrier' ? 'blocked' : label.includes('anyway') ? 'abort_current' : 'offer'
      return { kind: KINDS.questOffer, state }
    }
  }
  return { kind: KINDS.other, title }
}

export function isLanguageWindow(bot) {
  return windowKind(bot).kind === KINDS.language
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
    /* ignore */
  }
}

/** Resolves once a window matching {@code predicate(kindInfo, window)} is open. */
export function waitForWindow(bot, predicate, timeoutMs = 4000) {
  return new Promise((resolve, reject) => {
    const start = Date.now()
    const tick = () => {
      if (bot.currentWindow) {
        const info = windowKind(bot)
        if (predicate(info, bot.currentWindow)) {
          resolve(info)
          return
        }
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

export function kindIs(kind) {
  return (info) => info.kind === kind
}

/** Market listings on the open browse page (slots 0..44), with seller and price read from the lore. */
export function marketListings(bot) {
  const window = bot?.currentWindow
  if (!window) return []
  const out = []
  for (let slot = 0; slot < 45; slot++) {
    const item = window.slots[slot]
    if (!item || !item.name) continue
    if (item.name.includes('glass_pane')) continue
    const lore = itemLore(item, bot.registry)
    const seller = (lore.match(/Seller:\s*(\S+)/i) || [])[1] || ''
    if (!seller) continue
    const price = Number(((lore.match(/Buyout:\s*([\d.,]+)/i) || [])[1] || '').replace(/[.,]/g, ''))
    out.push({
      slot,
      item,
      name: itemName(item, bot.registry),
      seller,
      price: Number.isFinite(price) ? price : 0,
      mine: /your listing/i.test(lore)
    })
  }
  return out
}
