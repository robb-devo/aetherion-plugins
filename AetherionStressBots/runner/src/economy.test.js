import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  applyCoinEvent,
  chooseMarketAction,
  choosePriceSlot,
  emptyEconomy,
  parseCoinText,
  shouldConfirmBuy
} from './economy.js'

const saver = { economyStyle: 'saver', clumsiness: 0, greed: 0.8, patience: 0.6, sellAt: 28 }
const spender = { economyStyle: 'spender', clumsiness: 0, greed: 0.2, patience: 0.4, sellAt: 18 }

describe('coin chat', () => {
  it('reads purse, listings, buys, and payouts without treating a buyout label as a purchase', () => {
    assert.equal(parseCoinText('§7Balance: §e2,500 coins').purse, 2500)
    assert.equal(parseCoinText('§aListed for §61,200 coins§a.').listed, 1200)
    assert.equal(parseCoinText('§aBought for §6400 coins§a.').bought, 400)
    assert.equal(parseCoinText('§6Bazaar §8» §a+400 coins §7from §fMapleReed').earned, 400)
    const lore = parseCoinText('Buyout: 900 coins Your purse: 2,500 coins')
    assert.equal(lore.price, 900)
    assert.equal(lore.purse, 2500)
    assert.equal(lore.bought, undefined)
  })

  it('moves the purse when a buy or sale actually happens', () => {
    const book = emptyEconomy(2500)
    applyCoinEvent(book, parseCoinText('Bought for 400 coins.'))
    assert.equal(book.purse, 2100)
    assert.equal(book.spent, 400)
    applyCoinEvent(book, parseCoinText('+400 coins from someone'))
    assert.equal(book.purse, 2500)
    assert.equal(book.earned, 400)
    assert.equal(book.bought, 1)
  })
})

describe('market choices', () => {
  it('savers list high and spenders list at or under the suggested price', () => {
    for (let i = 0; i < 30; i++) {
      const slot = choosePriceSlot(saver, () => i / 30)
      assert.ok(slot >= 3 && slot <= 7, String(slot))
    }
    for (let i = 0; i < 20; i++) {
      const slot = choosePriceSlot(spender, () => (i + 1) / 21)
      assert.ok(slot <= 2, String(slot))
    }
  })

  it('refuses a purchase the purse cannot cover unless the bot is clumsy', () => {
    assert.equal(shouldConfirmBuy({ persona: saver, price: 900, purse: 200, rng: () => 0.5 }), false)
    assert.equal(shouldConfirmBuy({ persona: { ...spender, clumsiness: 0 }, price: 900, purse: 200, rng: () => 0.99 }), false)
    assert.equal(shouldConfirmBuy({ persona: spender, price: 400, purse: 2000, rng: () => 0.5 }), true)
    assert.equal(shouldConfirmBuy({ persona: saver, price: 400, purse: 1000, rng: () => 0 }), false)
  })

  it('sells when the bag is full of resources and does not loop the same failure', () => {
    const rolls = [0.5, 0.5, 0.01]
    let cursor = 0
    const sell = chooseMarketAction({
      persona: { ...spender, greed: 0.9, economyStyle: 'flipper', sellAt: 10 },
      resources: 40,
      gearSurplus: 0,
      purse: 2500,
      recentFails: 0,
      lastAction: '',
      rng: () => rolls[Math.min(cursor++, rolls.length - 1)]
    })
    assert.equal(sell, 'bazaar_sell')
    const rest = chooseMarketAction({
      persona: saver,
      resources: 40,
      gearSurplus: 1,
      purse: 100,
      recentFails: 4,
      rng: () => 0.1
    })
    assert.equal(rest, 'idle')
  })
})
