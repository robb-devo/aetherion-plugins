import pathfinderPkg from 'mineflayer-pathfinder'
import {
  clickSlot,
  closeWindow,
  findClickableSlots,
  isAuctionWindow,
  isBazaarWindow,
  isConfirmPurchaseWindow,
  isListPriceWindow,
  itemLore,
  SLOTS,
  waitForWindow,
  windowItems,
  windowTitle
} from './gui.js'
import { applyIslandMovements, wanderOnIsland } from './safety.js'
import { markError, note, sleep } from './util.js'
import { ACTIVITIES, fidget } from './playstyle.js'
import { maybeOpenBooster } from './minigame.js'
import {
  applyCoinEvent,
  chooseMarketAction,
  choosePriceSlot,
  parseCoinText,
  shouldAbortListing,
  shouldConfirmBuy
} from './economy.js'
import { isLingering } from './mind.js'
import { isResourceItem, resourceStack, surplusGearItem } from './sense.js'
import { surplusForTrade } from './mind.js'

const { goals, Movements, pathfinder } = pathfinderPkg

async function openMarket(bot, command, predicate, activity) {
  try { await closeWindow(bot) } catch { /* ignore */ }
  bot.chat(command)
  note(bot, command, activity)
  await waitForWindow(bot, predicate, 5000)
  absorbPurse(bot)
}

function absorbPurse(bot) {
  const window = bot.currentWindow
  if (!window) return
  const blob = windowItems(bot).map(({ item }) => `${item?.name || ''} ${itemLore(item)}`).join(' ')
  const event = parseCoinText(blob)
  if (event) applyCoinEvent(bot.qaEconomy, event)
}

async function listHeld(bot, activity) {
  const persona = bot.qaPersona
  if (shouldAbortListing(persona)) {
    note(bot, 'changed mind before listing', activity)
    if (bot.qaEconomy) bot.qaEconomy.skipped += 1
    await closeWindow(bot)
    return
  }
  await clickSlot(bot, SLOTS.marketList)
  await waitForWindow(bot, isListPriceWindow, 3500)
  await sleep(280 + Math.floor(Math.random() * 500))
  if (shouldAbortListing(persona)) {
    note(bot, 'closed the price menu', activity)
    if (bot.qaEconomy) bot.qaEconomy.skipped += 1
    await clickSlot(bot, 8).catch(() => closeWindow(bot))
    return
  }
  const slot = choosePriceSlot(persona)
  await clickSlot(bot, slot)
  note(bot, `${activity === ACTIVITIES.ah ? 'ah list' : 'bazaar sell'} @${slot}`, activity)
  await sleep(400)
}

async function buyListing(bot, activity) {
  absorbPurse(bot)
  const listings = findClickableSlots(bot, { skipChrome: true }).filter((entry) => entry.slot < 45)
  if (listings.length === 0) {
    note(bot, activity === ACTIVITIES.ah ? 'ah empty' : 'bazaar empty', activity)
    if (bot.qaEconomy) bot.qaEconomy.browsed += 1
    await closeWindow(bot)
    return false
  }
  const persona = bot.qaPersona
  const pick = listings[Math.floor(Math.random() * Math.min(listings.length, persona?.economyStyle === 'spender' ? 6 : 3))]
  const price = priceFromItem(pick.item)
  await clickSlot(bot, pick.slot)
  await waitForWindow(bot, isConfirmPurchaseWindow, 3500)
  absorbPurse(bot)
  const purse = bot.qaEconomy?.purse ?? null
  const knownPrice = price ?? priceFromWindow(bot)
  if (!shouldConfirmBuy({ persona, price: knownPrice, purse })) {
    note(bot, 'nah, too much', activity)
    if (bot.qaEconomy) bot.qaEconomy.skipped += 1
    await clickSlot(bot, SLOTS.confirmCancel).catch(() => {})
    await sleep(250)
    await closeWindow(bot)
    return false
  }
  await clickSlot(bot, SLOTS.confirmBuy)
  note(bot, activity === ACTIVITIES.ah ? 'ah buy' : 'bazaar buy', activity)
  await sleep(400)
  return true
}

function priceFromItem(item) {
  return parseCoinText(itemLore(item))?.price ?? null
}

function priceFromWindow(bot) {
  for (const { item } of windowItems(bot)) {
    const lore = itemLore(item)
    const buyout = String(lore).match(/buyout\s*:\s*([\d,]+)\s*coins/i)
    if (buyout) {
      const digits = buyout[1].replace(/[^\d]/g, '')
      return digits ? Number(digits) : null
    }
  }
  return null
}

async function equipItem(bot, item) {
  if (!item) return false
  if (bot.heldItem && bot.heldItem.slot === item.slot) return true
  await bot.equip(item, 'hand')
  return true
}

async function runAhList(bot) {
  const gear = surplusGearItem(bot)
  if (!gear) {
    note(bot, 'keeping my gear', ACTIVITIES.ah)
    if (bot.qaEconomy) bot.qaEconomy.skipped += 1
    return
  }
  await equipItem(bot, gear)
  await openMarket(bot, '/ah', isAuctionWindow, ACTIVITIES.ah)
  await listHeld(bot, ACTIVITIES.ah)
  await closeWindow(bot)
}

async function runAhBuy(bot) {
  await openMarket(bot, '/ah', isAuctionWindow, ACTIVITIES.ah)
  await buyListing(bot, ACTIVITIES.ah)
  await closeWindow(bot)
}

async function runBazaarSell(bot) {
  const stack = resourceStack(bot)
  if (!stack) {
    note(bot, 'nothing to sell', ACTIVITIES.bazaar)
    if (bot.qaEconomy) bot.qaEconomy.skipped += 1
    return
  }
  if ((bot.qaPersona?.economyStyle === 'saver') && (stack.count || 1) < 16 && countResources(bot) < (bot.qaPersona?.sellAt || 28)) {
    note(bot, 'holding mats', ACTIVITIES.bazaar)
    if (bot.qaEconomy) bot.qaEconomy.skipped += 1
    return
  }
  await equipItem(bot, stack)
  await openMarket(bot, '/bazaar', isBazaarWindow, ACTIVITIES.bazaar)
  await listHeld(bot, ACTIVITIES.bazaar)
  await closeWindow(bot)
}

function countResources(bot) {
  return surplusForTrade(bot).resources
}

async function runBazaarBuy(bot) {
  await openMarket(bot, '/bazaar', isBazaarWindow, ACTIVITIES.bazaar)
  await buyListing(bot, ACTIVITIES.bazaar)
  await closeWindow(bot)
}

async function browse(bot, command, predicate, activity) {
  await openMarket(bot, command, predicate, activity)
  note(bot, `browse ${activity}`, activity)
  if (bot.qaEconomy) bot.qaEconomy.browsed += 1
  if (Math.random() < 0.35) {
    await clickSlot(bot, SLOTS.marketNext).catch(() => {})
    await sleep(500)
  }
  await sleep(600 + Math.floor(Math.random() * 900))
  await closeWindow(bot)
}

async function collectReturns(bot) {
  await openMarket(bot, '/bazaar', isBazaarWindow, ACTIVITIES.bazaar)
  await clickSlot(bot, SLOTS.marketCollect).catch(() => {})
  note(bot, 'collect returns', ACTIVITIES.bazaar)
  await sleep(400)
  await closeWindow(bot)
}

const RUNNERS = {
  ah_list: runAhList,
  ah_buy: runAhBuy,
  bazaar_sell: runBazaarSell,
  bazaar_buy: runBazaarBuy,
  browse_ah: (bot) => browse(bot, '/ah', isAuctionWindow, ACTIVITIES.ah),
  browse_bazaar: (bot) => browse(bot, '/bazaar', isBazaarWindow, ACTIVITIES.bazaar),
  collect: collectReturns
}

export async function opportunisticSell(bot, log) {
  if (!bot?.entity || bot.qaEconomyBusy || bot.qaDigging) return false
  const stack = resourceStack(bot)
  if (!stack) return false
  bot.qaEconomyBusy = true
  bot.qaGoalLabel = 'sell a stack'
  try {
    await runBazaarSell(bot)
    return true
  } catch (err) {
    const message = err?.message || String(err)
    if (!message.includes('window timeout')) {
      log?.(bot.stressName, `side sell: ${message}`)
    } else {
      note(bot, 'bazaar closed', ACTIVITIES.bazaar)
    }
    try { await closeWindow(bot) } catch { /* ignore */ }
    return false
  } finally {
    bot.qaEconomyBusy = false
  }
}

export function createTradeLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const wanderRadius = cfg.wanderRadius ?? 5
  let running = false
  let lastFidget = 0
  let busy = false
  let lastAction = ''
  let fails = 0

  async function tick() {
    if (!bot.entity || bot.qaSuspended || busy || bot.qaEating || isLingering(bot)) {
      if (isLingering(bot)) note(bot, 'lingering by the stalls', 'lingering')
      return
    }
    busy = true
    bot.qaEconomyBusy = true
    try {
      if (!bot.pathfinder.movements) {
        bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 2 }))
      }
      if (bot.currentWindow && !isAuctionWindow(windowTitle(bot)) && !isBazaarWindow(windowTitle(bot))) {
        return
      }
      const stock = surplusForTrade(bot)
      const action = chooseMarketAction({
        persona: bot.qaPersona,
        resources: stock.resources,
        gearSurplus: stock.gearSurplus,
        purse: bot.qaEconomy?.purse ?? null,
        recentFails: fails,
        lastAction
      })
      bot.qaGoalLabel = action.replaceAll('_', ' ')
      if (action === 'idle' || !RUNNERS[action]) {
        note(bot, 'just standing at market', ACTIVITIES.trading)
        if (!bot.pathfinder.isMoving()) {
          wanderOnIsland(bot, bot.qaHome || bot.entity.position, wanderRadius, goals)
        }
        await sleep(cfg.actionGapMs ?? 2800)
        return
      }
      lastAction = action
      try {
        await RUNNERS[action](bot)
        fails = 0
      } catch (err) {
        const message = err.message || String(err)
        fails += 1
        if (message.includes('window timeout') || message.includes('no window')) {
          note(bot, `${action} missed the gui`, action.startsWith('ah') || action.includes('ah') ? ACTIVITIES.ah : ACTIVITIES.bazaar)
          if (bot.qaEconomy) bot.qaEconomy.failed += 1
        } else {
          markError(bot, err)
        }
        try { await closeWindow(bot) } catch { /* ignore */ }
      }
      if (Math.random() < 0.12) {
        await maybeOpenBooster(bot)
      }
      if (!bot.pathfinder.isMoving()) {
        wanderOnIsland(bot, bot.qaHome || bot.entity.position, wanderRadius, goals)
      }
      if (Date.now() - lastFidget > 7000 && Math.random() < 0.4) {
        lastFidget = Date.now()
        fidget(bot, ACTIVITIES.trading)
      }
      const gap = (cfg.actionGapMs ?? 3500) * (0.65 + (bot.qaPersona?.patience ?? 0.5))
      await sleep(gap)
    } finally {
      bot.qaEconomyBusy = false
      busy = false
    }
  }

  return function start() {
    if (running) return
    running = true
    const style = bot.qaPersona?.economyStyle || 'casual'
    log(bot.stressName, `trade loop start (${style})`)
    note(bot, `trade loop start ${style}`, ACTIVITIES.trading)
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `trade tick: ${err.message}`)
      })
    }, 520 + Math.floor(Math.random() * 180))
    bot.once('end', () => clearInterval(handle))
  }
}
