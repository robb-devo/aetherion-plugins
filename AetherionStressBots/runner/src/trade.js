import pathfinderPkg from 'mineflayer-pathfinder'
import {
  clickSlot,
  closeWindow,
  findClickableSlots,
  isAuctionWindow,
  isBazaarWindow,
  isConfirmPurchaseWindow,
  isListPriceWindow,
  SLOTS,
  waitForWindow,
  windowTitle
} from './gui.js'
import { applyIslandMovements, wanderOnIsland } from './safety.js'
import { markError, note, sleep } from './util.js'
import { ACTIVITIES, fidget } from './playstyle.js'
import { maybeOpenBooster } from './minigame.js'

const { goals, Movements, pathfinder } = pathfinderPkg

function isGearItem(item) {
  if (!item?.name) return false
  const n = item.name.toLowerCase()
  return n.includes('sword') || n.includes('pickaxe') || n.includes('axe') || n.includes('shovel')
    || n.includes('hoe') || n.includes('helmet') || n.includes('chestplate')
    || n.includes('leggings') || n.includes('boots')
}

function isResourceItem(item) {
  if (!item?.name) return false
  const n = item.name.toLowerCase()
  return n.includes('coal') || n.includes('cobble') || n.includes('log') || n.includes('oak')
    || n.includes('iron') || n.includes('dirt') || n.includes('stone')
}

async function openMarket(bot, command, predicate, activity) {
  try { await closeWindow(bot) } catch { /* ignore */ }
  bot.chat(command)
  note(bot, command, activity)
  await waitForWindow(bot, predicate, 5000)
}

async function listHeld(bot, activity) {
  await clickSlot(bot, SLOTS.marketList)
  await waitForWindow(bot, isListPriceWindow, 3500)
  await clickSlot(bot, SLOTS.priceSuggested)
  note(bot, activity === ACTIVITIES.ah ? 'ah list' : 'bazaar sell', activity)
  await sleep(400)
}

async function buyListing(bot, activity) {
  const listings = findClickableSlots(bot, { skipChrome: true }).filter((entry) => entry.slot < 45)
  if (listings.length === 0) {
    note(bot, activity === ACTIVITIES.ah ? 'ah buy fail (empty)' : 'bazaar buy fail (empty)', activity)
    await closeWindow(bot)
    return false
  }
  const pick = listings[Math.floor(Math.random() * listings.length)]
  await clickSlot(bot, pick.slot)
  await waitForWindow(bot, isConfirmPurchaseWindow, 3500)
  await clickSlot(bot, SLOTS.confirmBuy)
  note(bot, activity === ACTIVITIES.ah ? 'ah buy' : 'bazaar buy', activity)
  await sleep(400)
  return true
}

async function equipSurplus(bot, predicate) {
  const held = bot.heldItem
  if (held && predicate(held)) return true
  const item = (bot.inventory.items() || []).find(predicate)
  if (!item) return false
  await bot.equip(item, 'hand')
  return true
}

export function createTradeLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const wanderRadius = cfg.wanderRadius ?? 5
  let running = false
  let step = 0
  let lastFidget = 0
  let busy = false

  async function runAhList() {
    if (!await equipSurplus(bot, isGearItem)) {
      note(bot, 'ah list fail (no gear)', ACTIVITIES.ah)
      return
    }
    await openMarket(bot, '/ah', isAuctionWindow, ACTIVITIES.ah)
    await listHeld(bot, ACTIVITIES.ah)
    await closeWindow(bot)
  }

  async function runAhBuy() {
    await openMarket(bot, '/ah', isAuctionWindow, ACTIVITIES.ah)
    await buyListing(bot, ACTIVITIES.ah)
    await closeWindow(bot)
  }

  async function runBazaarSell() {
    if (!await equipSurplus(bot, isResourceItem)) {
      note(bot, 'bazaar sell fail (no mats)', ACTIVITIES.bazaar)
      return
    }
    await openMarket(bot, '/bazaar', isBazaarWindow, ACTIVITIES.bazaar)
    await listHeld(bot, ACTIVITIES.bazaar)
    await closeWindow(bot)
  }

  async function runBazaarBuy() {
    await openMarket(bot, '/bazaar', isBazaarWindow, ACTIVITIES.bazaar)
    await buyListing(bot, ACTIVITIES.bazaar)
    await closeWindow(bot)
  }

  const steps = [runAhList, runAhBuy, runBazaarSell, runBazaarBuy]

  async function tick() {
    if (!bot.entity || bot.qaSuspended || busy) return
    busy = true
    try {
      if (!bot.pathfinder.movements) {
        bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 2 }))
      }
      if (bot.currentWindow && !isAuctionWindow(windowTitle(bot)) && !isBazaarWindow(windowTitle(bot))) {
        return
      }
      const action = steps[step % steps.length]
      step++
      try {
        await action()
      } catch (err) {
        const message = err.message || String(err)
        if (message.includes('window timeout')) {
          note(bot, `${action.name} fail (gui)`, step % 2 === 0 ? ACTIVITIES.bazaar : ACTIVITIES.ah)
        } else {
          markError(bot, err)
        }
        try { await closeWindow(bot) } catch { /* ignore */ }
      }
      if (Math.random() < 0.2) {
        await maybeOpenBooster(bot)
      }
      if (!bot.pathfinder.isMoving()) {
        wanderOnIsland(bot, bot.qaHome || bot.entity.position, wanderRadius, goals)
      }
      if (Date.now() - lastFidget > 5000) {
        lastFidget = Date.now()
        fidget(bot, ACTIVITIES.trading)
      }
      await sleep(cfg.actionGapMs ?? 3500)
    } finally {
      busy = false
    }
  }

  return function start() {
    if (running) return
    running = true
    log(bot.stressName, 'trade loop start (ah + bazaar GUIs)')
    note(bot, 'trade loop start', ACTIVITIES.trading)
    const handle = setInterval(() => {
      tick().catch((err) => {
        markError(bot, err)
        log(bot.stressName, `trade tick: ${err.message}`)
      })
    }, 480)
    bot.once('end', () => clearInterval(handle))
  }
}
