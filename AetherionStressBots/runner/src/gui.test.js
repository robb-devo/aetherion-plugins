import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  SLOTS,
  isLanguageWindow,
  isAuctionWindow,
  isBazaarWindow,
  isQuestOfferWindow,
  isConfirmPurchaseWindow,
  isListPriceWindow,
  isBoosterWindow,
  stripLegacy,
  titleMatches
} from './gui.js'
import { parseQuestAcceptCommand } from './gui.js'

describe('gui window titles', () => {
  it('detects Quests Language / Sprache (verified title)', () => {
    assert.equal(isLanguageWindow('§8Language / Sprache'), true)
    assert.equal(isLanguageWindow('§bSprache / Language'), true)
    assert.equal(isLanguageWindow('§d✦ Language / Sprache'), true)
    assert.equal(isLanguageWindow('§8Auction House'), false)
  })

  it('detects AH and Bazaar browse titles', () => {
    assert.equal(isAuctionWindow('§8Auction House §8· 1/1'), true)
    assert.equal(isBazaarWindow('§8Bazaar §8· 1/1'), true)
    assert.equal(isBazaarWindow('§8Auction House'), false)
  })

  it('detects list / confirm / quest / booster', () => {
    assert.equal(isListPriceWindow('§8List · Auction House'), true)
    assert.equal(isConfirmPurchaseWindow('§8Confirm purchase'), true)
    assert.equal(isQuestOfferWindow('§8Quest Offer'), true)
    assert.equal(isBoosterWindow('§8Booster Tutor'), true)
  })

  it('keeps market slot numbers aligned with MarketService', () => {
    assert.equal(SLOTS.marketList, 49)
    assert.equal(SLOTS.confirmBuy, 11)
    assert.equal(SLOTS.priceSuggested, 2)
    assert.equal(SLOTS.languageEnglish, 11)
    assert.equal(SLOTS.questAccept, 11)
  })

  it('strips legacy color and matches needles', () => {
    assert.equal(stripLegacy('§aEnglish'), 'English')
    assert.equal(titleMatches('§8Auction House', 'auction'), true)
  })
})

describe('quest chat fallback', () => {
  it('parses /aetherionquest accept from clickable chat', () => {
    assert.equal(parseQuestAcceptCommand('click /aetherionquest accept welcome_aboard'), '/aetherionquest accept welcome_aboard')
    assert.equal(parseQuestAcceptCommand('nope'), null)
  })
})
