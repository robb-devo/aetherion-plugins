/**
 * Imperfect market decisions. Prices match MarketService PRICE_PERCENTS
 * slots 0..7 = 50, 75, 100, 125, 150, 200, 250, 300. Slot 2 is the suggested price.
 */

export const PRICE_PERCENTS = [50, 75, 100, 125, 150, 200, 250, 300]

export function parseCoinAmount(raw) {
  if (raw == null) return null
  const digits = String(raw).replace(/[^\d]/g, '')
  if (!digits) return null
  const value = Number(digits)
  return Number.isFinite(value) ? value : null
}

export function parseCoinText(text) {
  const plain = String(text || '').replace(/§./g, '')
  const out = {}
  const purse = plain.match(/(?:purse|balance)\s*:\s*([\d,]+)\s*coins/i)
  if (purse) out.purse = parseCoinAmount(purse[1])
  const listed = plain.match(/listed for\s*([\d,]+)\s*coins/i)
  if (listed) {
    out.listed = parseCoinAmount(listed[1])
    out.listedCount = 1
  }
  const bought = plain.match(/bought for\s*([\d,]+)\s*coins/i)
  if (bought) {
    out.bought = parseCoinAmount(bought[1])
    out.boughtCount = 1
  }
  const earned = plain.match(/\+\s*([\d,]+)\s*coins/i)
  if (earned) {
    out.earned = parseCoinAmount(earned[1])
  }
  const buyout = plain.match(/buyout\s*:\s*([\d,]+)\s*coins/i)
  if (buyout) out.price = parseCoinAmount(buyout[1])
  const need = plain.match(/need\s*([\d,]+)\s*coins/i)
  if (need) {
    out.shortfall = parseCoinAmount(need[1])
    out.failed = 1
  }
  const gone = /listing is gone|nothing to collect|no coin value|hold the item/i.test(plain)
  if (gone) out.failed = (out.failed || 0) + 1
  if (Object.keys(out).length === 0) return null
  return out
}

export function applyCoinEvent(economy, event) {
  const book = economy || emptyEconomy()
  if (!event) return book
  if (event.purse != null) {
    book.purse = event.purse
    book.purseKnown = true
  }
  if (event.listed != null) {
    book.listed += 1
    book.lastListPrice = event.listed
  }
  if (event.bought != null) {
    book.bought += 1
    book.spent += event.bought
    if (book.purse != null) book.purse = Math.max(0, book.purse - event.bought)
  }
  if (event.earned != null) {
    book.earned += event.earned
    book.sales += 1
    if (book.purse != null) book.purse += event.earned
  }
  if (event.failed) book.failed += event.failed
  if (event.shortfall != null && book.purse != null && book.purse >= event.shortfall) {
    book.purse = Math.max(0, event.shortfall - 1)
    book.purseKnown = false
  }
  return book
}

export function emptyEconomy(purse = null) {
  return {
    purse,
    purseKnown: false,
    listed: 0,
    bought: 0,
    sales: 0,
    spent: 0,
    earned: 0,
    failed: 0,
    skipped: 0,
    browsed: 0,
    lastListPrice: null
  }
}

/**
 * Saver lists high (sometimes unsellably high). Spender underprices.
 * Casual mostly uses the suggested 100% slot and occasionally misclicks.
 */
export function choosePriceSlot(persona, rng = Math.random) {
  const style = persona?.economyStyle || 'casual'
  const roll = rng()
  const clumsy = persona?.clumsiness ?? 0
  if (roll < clumsy * 0.22) return Math.floor(rng() * PRICE_PERCENTS.length)
  if (style === 'saver') {
    if (roll < 0.12) return 7
    if (roll < 0.4) return 5
    if (roll < 0.75) return 4
    return 3
  }
  if (style === 'spender') {
    if (roll < 0.18) return 0
    if (roll < 0.62) return 1
    return 2
  }
  if (style === 'flipper') {
    if (roll < 0.5) return 2
    if (roll < 0.82) return 3
    return 1
  }
  if (roll < 0.7) return 2
  if (roll < 0.88) return 1
  return 3
}

export function chooseMarketAction({
  persona,
  resources = 0,
  gearSurplus = 0,
  purse = null,
  recentFails = 0,
  lastAction = '',
  rng = Math.random
} = {}) {
  const style = persona?.economyStyle || 'casual'
  const greed = persona?.greed ?? 0.5
  if (recentFails >= 3 && rng() < 0.65) return 'idle'
  if (rng() < 0.07) return 'collect'
  if (rng() < 0.1 + (1 - greed) * 0.08) return rng() < 0.5 ? 'browse_ah' : 'browse_bazaar'

  const sellResource = resources >= (persona?.sellAt ?? 14)
  const canListGear = gearSurplus > 0
  const rich = purse == null ? style !== 'saver' : purse >= 800
  const comfortable = purse == null ? true : purse >= 250

  const weights = {
    bazaar_sell: sellResource ? 3 + greed * 4 : 0.15,
    ah_list: canListGear ? 2.2 + greed * 2 : 0.05,
    bazaar_buy: 0.4,
    ah_buy: 0.35,
    browse_bazaar: 0.8,
    browse_ah: 0.7,
    idle: 0.45
  }
  if (style === 'saver') {
    weights.bazaar_buy *= 0.25
    weights.ah_buy *= 0.2
    weights.bazaar_sell *= 1.3
    weights.idle += 0.6
  } else if (style === 'spender') {
    weights.bazaar_buy *= rich ? 2.4 : 1.1
    weights.ah_buy *= comfortable ? 2.2 : 0.8
    weights.bazaar_sell *= 0.7
  } else if (style === 'flipper') {
    weights.bazaar_sell *= 1.5
    weights.ah_list *= 1.6
    weights.bazaar_buy *= comfortable ? 1.4 : 0.4
    weights.ah_buy *= comfortable ? 1.3 : 0.3
  }
  if (lastAction && weights[lastAction]) weights[lastAction] *= 0.45
  if (!sellResource) weights.bazaar_sell *= 0.2
  if (!canListGear) weights.ah_list = 0.02

  return weightedPick(weights, rng)
}

export function shouldConfirmBuy({ persona, price = null, purse = null, rng = Math.random } = {}) {
  const style = persona?.economyStyle || 'casual'
  const clumsy = persona?.clumsiness ?? 0
  if (purse != null && price != null && price > purse) {
    return rng() < clumsy * 0.2
  }
  if (price == null) {
    if (style === 'saver') return rng() < 0.22
    if (style === 'spender') return rng() < 0.7
    return rng() < 0.45
  }
  const purseSafe = purse == null ? price * 4 : purse
  const ratio = purseSafe <= 0 ? 1 : price / purseSafe
  if (style === 'saver') return ratio < 0.12 && rng() < 0.55
  if (style === 'spender') return ratio < 0.65 || rng() < 0.35
  if (style === 'flipper') return ratio < 0.35 && rng() < 0.8
  return ratio < 0.4 && rng() < 0.7
}

export function shouldAbortListing(persona, rng = Math.random) {
  const patience = persona?.patience ?? 0.5
  return rng() < (0.04 + (1 - patience) * 0.12)
}

function weightedPick(weights, rng) {
  let total = 0
  for (const value of Object.values(weights)) total += Math.max(0, value)
  if (total <= 0) return 'idle'
  let cursor = rng() * total
  for (const [key, value] of Object.entries(weights)) {
    cursor -= Math.max(0, value)
    if (cursor <= 0) return key
  }
  return 'idle'
}
