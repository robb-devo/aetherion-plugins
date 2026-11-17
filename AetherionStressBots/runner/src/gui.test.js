import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { KINDS, SLOTS, marketListings, windowKind } from './gui.js'

/** A fake open window: {@code size} container slots plus the 36 player slots. */
function botWith(size, items, title = '') {
  const slots = new Array(size + 36).fill(null)
  for (const [slot, item] of Object.entries(items)) slots[Number(slot)] = item
  return { currentWindow: { title, inventoryStart: size, slots } }
}

const item = (name, customName, lore) => ({ name, customName, customLore: lore })

describe('window kinds (read from slots, titles are pack glyphs)', () => {
  it('reports none without a window', () => {
    assert.equal(windowKind({}).kind, KINDS.none)
  })

  it('finds the language picker by its two buttons', () => {
    const bot = botWith(27, {
      [SLOTS.languageEnglish]: item('paper', '§aEnglish'),
      [SLOTS.languageGerman]: item('paper', '§eDeutsch')
    }, '')
    assert.equal(windowKind(bot).kind, KINDS.language)
  })

  it('finds a market page and its counter', () => {
    const bot = botWith(54, {
      [SLOTS.marketList]: item('writable_book', 'Sell an item'),
      [SLOTS.marketInfo]: item('book', 'Bazaar · resources')
    })
    assert.deepEqual(windowKind(bot), { kind: KINDS.market, channel: 'bazaar' })
  })

  it('finds the price picker', () => {
    const bot = botWith(9, {
      0: item('gold_nugget', '500 coins (50%)'),
      1: item('gold_nugget', '800 coins (80%)'),
      2: item('gold_ingot', '1,000 coins (100%)')
    })
    assert.equal(windowKind(bot).kind, KINDS.price)
  })

  it('reads the confirm page state', () => {
    const buy = botWith(27, {
      [SLOTS.confirmBuy]: item('lime_wool', 'Buy for 120 coins'),
      [SLOTS.confirmCancel]: item('red_wool', 'Cancel')
    })
    assert.deepEqual(windowKind(buy), { kind: KINDS.confirm, state: 'buy' })
    const broke = botWith(27, {
      [SLOTS.confirmBuy]: item('lime_wool', 'Not enough coins'),
      [SLOTS.confirmCancel]: item('red_wool', 'Cancel')
    })
    assert.equal(windowKind(broke).state, 'broke')
  })

  it('reads the quest offer state', () => {
    const offer = botWith(27, {
      [SLOTS.questAccept]: item('lime_concrete', 'Accept'),
      [SLOTS.questDecline]: item('red_concrete', 'Decline')
    })
    assert.deepEqual(windowKind(offer), { kind: KINDS.questOffer, state: 'offer' })
    const blocked = botWith(27, {
      [SLOTS.questAccept]: item('barrier', 'Locked'),
      [SLOTS.questDecline]: item('red_concrete', 'Decline')
    })
    assert.equal(windowKind(blocked).state, 'blocked')
  })

  it('keeps market slot numbers aligned with MarketService', () => {
    assert.equal(SLOTS.marketList, 49)
    assert.equal(SLOTS.marketInfo, 47)
    assert.equal(SLOTS.marketSwitch, 51)
    assert.equal(SLOTS.priceSuggested, 2)
    assert.equal(SLOTS.confirmBuy, 11)
    assert.equal(SLOTS.confirmCancel, 15)
  })
})

describe('market listings', () => {
  it('reads seller, price and own listings from the lore', () => {
    const bot = botWith(54, {
      0: item('iron_sword', 'Iron Sword', ['Seller: QaTrade03', 'Buyout: 1,200 coins']),
      1: item('oak_log', 'Oak Log', ['Seller: Robb', 'Buyout: 40 coins', 'Your listing']),
      2: item('gray_stained_glass_pane', ' '),
      [SLOTS.marketList]: item('writable_book', 'Sell an item')
    })
    const listings = marketListings(bot)
    assert.equal(listings.length, 2)
    assert.deepEqual(
      listings.map(({ slot, seller, price, mine }) => ({ slot, seller, price, mine })),
      [
        { slot: 0, seller: 'QaTrade03', price: 1200, mine: false },
        { slot: 1, seller: 'Robb', price: 40, mine: true }
      ]
    )
  })
})
