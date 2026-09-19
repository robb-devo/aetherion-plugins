import {
  clickNamed,
  clickSlot,
  closeWindow,
  isBoosterWindow,
  isLanguageWindow,
  windowTitle
} from './gui.js'
import { note } from './util.js'
import { ACTIVITIES } from './playstyle.js'

function plain(value) {
  return String(value || '').replace(/§./g, '').toLowerCase()
}

function bossBarText(bot) {
  const bars = bot.bossBars
  if (!bars) return ''
  const list = bars instanceof Map ? [...bars.values()] : Object.values(bars)
  return list.map((bar) => plain(bar.title || bar.text || bar)).join(' | ')
}

function hotCatch(text) {
  const p = plain(text)
  return p.includes('click!') || p.includes('perfect catch') || p.includes('✦')
}

function hotFish(text) {
  const p = plain(text)
  return p.includes('reel') || (p.includes('fishing') && p.includes('◆'))
}

function biteTitle(text) {
  const p = plain(text)
  return p.includes('bite') || p.includes('reel on green')
}

/**
 * Shared minigame clicks: fishing strike, catch timing, leftover booster GUIs.
 * Attach once per bot; roles do not duplicate this.
 */
export function attachMinigames(bot, log) {
  bot.qaMinigame = bot.qaMinigame || { biteUntil: 0, lastClick: 0 }

  const rememberBite = () => {
    bot.qaMinigame.biteUntil = Date.now() + 3500
    note(bot, 'minigame bite', ACTIVITIES.minigame)
  }

  bot.on('title', (text) => {
    if (biteTitle(text)) rememberBite()
    if (isLanguageWindow(String(text || ''))) return
  })
  bot.on('actionBar', (text) => {
    const p = plain(text)
    if (biteTitle(p) || hotFish(p) || hotCatch(p)) {
      if (hotFish(p) || biteTitle(p)) rememberBite()
      tryClick(bot, p)
    }
  })
  if (typeof bot.on === 'function') {
    bot.on('bossBarUpdated', (bar) => {
      const text = plain(bar?.title || bar?.text || '')
      if (hotFish(text) || hotCatch(text) || biteTitle(text)) {
        if (hotFish(text) || biteTitle(text)) rememberBite()
        tryClick(bot, text)
      }
    })
  }

  const handle = setInterval(() => {
    const bars = bossBarText(bot)
    if (hotCatch(bars) || hotFish(bars) || Date.now() < (bot.qaMinigame.biteUntil || 0)) {
      tryClick(bot, bars || 'strike')
    }
    const title = windowTitle(bot)
    if (isBoosterWindow(title) && Date.now() - (bot.qaMinigame.lastGui || 0) > 4000) {
      bot.qaMinigame.lastGui = Date.now()
      clickBooster(bot).catch(() => {})
    }
  }, 160)

  bot.once('end', () => clearInterval(handle))
  return { rememberBite }
}

function tryClick(bot, reason) {
  if (Date.now() - (bot.qaMinigame.lastClick || 0) < 140) return
  bot.qaMinigame.lastClick = Date.now()
  note(bot, `minigame click ${plain(reason).slice(0, 24)}`, ACTIVITIES.minigame)
  try {
    bot.activateItem()
  } catch {
    try { bot.swingArm() } catch { /* ignore */ }
  }
}

async function clickBooster(bot) {
  note(bot, 'booster gui', ACTIVITIES.minigame)
  try {
    await clickNamed(bot, (name) => /apply|use|confirm|booster/i.test(name))
  } catch {
    try { await clickSlot(bot, 11) } catch { await closeWindow(bot) }
  }
}

export async function maybeOpenBooster(bot) {
  const item = (bot.inventory?.items?.() || []).find((stack) => {
    const n = String(stack.name || '').toLowerCase()
    const label = String(stack.customName || stack.displayName || '').toLowerCase()
    return n.includes('booster') || label.includes('booster')
  })
  if (!item) return false
  if (Date.now() - (bot.qaMinigame.lastBoosterOpen || 0) < 45_000) return false
  bot.qaMinigame.lastBoosterOpen = Date.now()
  try {
    await bot.equip(item, 'hand')
    note(bot, 'hold booster', ACTIVITIES.minigame)
    await bot.activateItem()
    return true
  } catch {
    return false
  }
}
