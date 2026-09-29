import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { isMarketResource, mergeMissing } from './util.js'

describe('inventory churn', () => {
  it('keeps bazaar mats for trade/farm bots', () => {
    assert.equal(isMarketResource('coal'), true)
    assert.equal(isMarketResource('oak_log'), true)
    assert.equal(isMarketResource('wheat'), true)
    assert.equal(isMarketResource('cobblestone'), true)
    assert.equal(isMarketResource('rotten_flesh'), false)
  })
})

describe('mergeMissing', () => {
  it('adds farm role from example without clobbering live secret or anchors', () => {
    const live = {
      velocitySecret: 'real-secret',
      prefixes: { mine: 'QaMine' },
      fish: { anchors: [{ x: 22.5, y: 56, z: 305.5 }] }
    }
    const example = {
      velocitySecret: 'CHANGE_ME',
      prefixes: { mine: 'QaMine', farm: 'QaFarm' },
      farm: { anchors: [{ x: -600.5, y: 90, z: 427.5 }] },
      fish: { anchors: [{ x: -585.5, y: 90, z: -649.5 }], leashRadius: 14 }
    }
    const merged = mergeMissing(live, example)
    assert.equal(merged.velocitySecret, 'real-secret')
    assert.equal(merged.prefixes.farm, 'QaFarm')
    assert.equal(merged.farm.anchors[0].x, -600.5)
    assert.equal(merged.fish.anchors[0].x, 22.5)
    assert.equal(merged.fish.leashRadius, 14)
  })
})
