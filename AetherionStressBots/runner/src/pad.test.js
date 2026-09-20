import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { pickNextPad, readPads } from './pads.js'

describe('pad hop selection', () => {
  const pads = readPads({
    pads: [
      { id: 'origin_to_mining', x: 22.5, y: 56, z: 305.5 },
      { id: 'origin_to_forage', x: 438.5, y: 56, z: -233.5 },
      { id: 'mining_to_origin', x: 53.5, y: 91, z: 482.5 }
    ]
  })

  it('reads configured pad centers', () => {
    assert.equal(pads.length, 3)
    assert.equal(pads[0].id, 'origin_to_mining')
  })

  it('avoids the last pad when others exist', () => {
    const next = pickNextPad({ x: 22.5, y: 56, z: 305.5 }, pads, 'origin_to_mining', { minHop: 0 })
    assert.ok(next)
    assert.notEqual(next.id, 'origin_to_mining')
  })

  it('refuses distant cross-island pads so bots do not walk the void', () => {
    const next = pickNextPad({ x: 22.5, y: 56, z: 305.5 }, pads, 'origin_to_mining', { minHop: 4, maxHop: 16 })
    assert.equal(next, null)
  })

  it('still returns a pad when only one exists', () => {
    const one = pickNextPad({ x: 0, y: 56, z: 0 }, [pads[0]], 'origin_to_mining')
    assert.equal(one.id, 'origin_to_mining')
  })
})
