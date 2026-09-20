import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { isMarketResource } from './util.js'

describe('inventory churn', () => {
  it('keeps bazaar mats for trade/farm bots', () => {
    assert.equal(isMarketResource('coal'), true)
    assert.equal(isMarketResource('oak_log'), true)
    assert.equal(isMarketResource('wheat'), true)
    assert.equal(isMarketResource('cobblestone'), true)
    assert.equal(isMarketResource('rotten_flesh'), false)
  })
})
