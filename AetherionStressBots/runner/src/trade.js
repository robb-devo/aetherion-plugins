import pathfinderPkg from 'mineflayer-pathfinder'
import {
  clickSlot,
  closeWindow,
  KINDS,
  marketListings,
  SLOTS,
  waitForWindow,
  windowKind
} from './gui.js'
import { applyIslandMovements, wanderOnIsland } from './safety.js'
import { jitter, markError, note, runEvery, sleep } from './util.js'
import { ACTIVITIES, fidget } from './playstyle.js'
import { isBotName } from './names.js'

const { goals, Movements, pathfinder } = pathfinderPkg


export function isGearItem(item) {
  if (!item?.name) return false
  const n = item.name.toLowerCase()
  return n.endsWith('_sword') || n.endsWith('_pickaxe') || n.endsWith('_axe') || n.endsWith('_shovel')
    || n.endsWith('_hoe') || n.endsWith('_helmet') || n.endsWith('_chestplate')
    || n.endsWith('_leggings') || n.endsWith('_boots')
}

export function isResourceItem(item) {
  if (!item?.name) return false
  const n = item.name.toLowerCase()
  return n === 'coal' || n === 'cobblestone' || n.endsWith('_log') || n === 'iron_ingot' || n === 'raw_iron'
    || n === 'stone' || n === 'cobbled_deepslate' || n.endsWith('_planks')
}

/**
 * Which listing a trade bot buys. Only other bots' listings unless {@code buyFromPlayers} is set,
 * so a load test never spends bot coins on real players' items (or pulls their listings).
 */
export function pickListing(listings, { buyFromPlayers = false, purse = Infinity, rng = Math.random } = {}) {
  const pool = listings.filter((entry) => !entry.mine
    && (buyFromPlayers || isBotName(entry.seller))
    && (!Number.isFinite(purse) || entry.price <= purse))
  if (pool.length === 0) return null
  return pool[Math.floor(rng() * pool.length)]
}

async function equipSurplus(bot, predicate) {
  const held = bot.heldItem
  if (held && predicate(held)) return true
  const item = (bot.inventory.items() || []).find(predicate)
  if (!item) return false
  await bot.equip(item, 'hand')
  return true
}

/**
 * Auction House (gear) and Bazaar (resources) through the real MarketService windows: list what is in
 * hand at the fair price, and buy another bot's listing through the confirm page.
 */
export function createTradeLoop(bot, cfg, log) {
  bot.loadPlugin(pathfinder)
  const wanderRadius = cfg.wanderRadius ?? 5
  const buyFromPlayers = cfg.buyFromPlayers === true
  const stats = (bot.qaStats = bot.qaStats || {})
  let running = false
  let step = Math.floor(Math.random() * 4)
  let lastFidget = 0

  async function openMarket(command, channel) {
    await closeWindow(bot)
    bot.chat(command)
    note(bot, command, channel === 'auction' ? ACTIVITIES.ah : ACTIVITIES.bazaar)
    await waitForWindow(bot, (info) => info.kind === KINDS.market, 5000)
    const info = windowKind(bot)
    if (info.channel !== 'unknown' && info.channel !== channel) {
      // Opened the other counter (the switch slot) — flip to the one we asked for.
      await clickSlot(bot, SLOTS.marketSwitch)
      await sleep(400)
    }
  }

  async function list(channel, predicate) {
    const activity = channel === 'auction' ? ACTIVITIES.ah : ACTIVITIES.bazaar
    if (!await equipSurplus(bot, predicate)) {
      note(bot, `${channel} list skipped (nothing to sell)`, activity)
      return
    }
    await openMarket(channel === 'auction' ? '/ah' : '/bazaar', channel)
    await clickSlot(bot, SLOTS.marketList)
    try {
      await waitForWindow(bot, (info) => info.kind === KINDS.price, 3500)
    } catch {
      note(bot, `${channel} list refused (full or wrong counter)`, activity)
      return
    }
    await sleep(jitter(700, 0.4))
    await clickSlot(bot, SLOTS.priceSuggested)
    stats.listings = (stats.listings || 0) + 1
    note(bot, `${channel} listed at fair price`, activity)
    await sleep(400)
  }

  async function buy(channel) {
    const activity = channel === 'auction' ? ACTIVITIES.ah : ACTIVITIES.bazaar
    await openMarket(channel === 'auction' ? '/ah' : '/bazaar', channel)
    await sleep(jitter(900, 0.4))
    const pick = pickListing(marketListings(bot), { buyFromPlayers })
    if (!pick) {
      note(bot, `${channel} nothing from bots to buy`, activity)
      return
    }
    await clickSlot(bot, pick.slot)
    const info = await waitForWindow(bot, (kind) => kind.kind === KINDS.confirm, 3500)
    if (info.state !== 'buy') {
      note(bot, `${channel} confirm ${info.state}`, activity)
      await clickSlot(bot, SLOTS.confirmCancel)
      return
    }
    await sleep(jitter(600, 0.4))
    await clickSlot(bot, SLOTS.confirmBuy)
    stats.buys = (stats.buys || 0) + 1
    note(bot, `${channel} bought ${pick.name} from ${pick.seller}`, activity)
    await sleep(400)
  }

  const steps = [
    () => list('auction', isGearItem),
    () => buy('auction'),
    () => list('bazaar', isResourceItem),
    () => buy('bazaar')
  ]

  async function tick() {
    if (!bot.entity || bot.qaSuspended) return
    if (!bot.pathfinder.movements) {
      bot.pathfinder.setMovements(applyIslandMovements(new Movements(bot), { canDig: false, maxDrop: 2 }))
    }
    if (bot.currentWindow && windowKind(bot).kind !== KINDS.market) return
    const action = steps[step % steps.length]
    step++
    try {
      await action()
    } catch (err) {
      const message = err.message || String(err)
      if (message.includes('window timeout')) {
        note(bot, 'market window did not open', ACTIVITIES.trading)
        stats.guiFails = (stats.guiFails || 0) + 1
      } else {
        markError(bot, err)
      }
    } finally {
      await closeWindow(bot)
    }
    if (!bot.pathfinder.isMoving()) {
      wanderOnIsland(bot, bot.qaHome || bot.entity.position, wanderRadius, goals)
    }
    if (Date.now() - lastFidget > 5000) {
      lastFidget = Date.now()
      fidget(bot, ACTIVITIES.trading)
    }
    await sleep(jitter(cfg.actionGapMs ?? 6000, 0.4))
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    log(bot.stressName, `trade loop start (ah + bazaar windows, buy from ${buyFromPlayers ? 'anyone' : 'bots only'})`)
    note(bot, 'trade loop start', ACTIVITIES.trading)
    const halt = runEvery(bot, 500, tick, (err) => {
      markError(bot, err)
      log(bot.stressName, `trade tick: ${err.message}`)
    })
    stop = () => {
      halt()
      running = false
    }
    return stop
  }
}
