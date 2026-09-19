import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  clampToLeash,
  horizontalDistance,
  isBelowFloor,
  nearestAnchor,
  readAnchors,
  withinLeash
} from './safety.js'

describe('island safety helpers', () => {
  it('measures horizontal distance ignoring Y', () => {
    assert.equal(horizontalDistance({ x: 0, y: 90, z: 0 }, { x: 3, y: 10, z: 4 }), 5)
  })

  it('flags void floor', () => {
    assert.equal(isBelowFloor({ x: 1, y: 39.9, z: 1 }, 40), true)
    assert.equal(isBelowFloor({ x: 1, y: 40, z: 1 }, 40), false)
    assert.equal(isBelowFloor(null, 40), true)
  })

  it('picks the nearest configured pad', () => {
    const anchors = [
      { x: 22.5, y: 56, z: 305.5 },
      { x: 438.5, y: 56, z: -233.5 }
    ]
    const near = nearestAnchor({ x: 30, y: 56, z: 300 }, anchors)
    assert.equal(near.x, 22.5)
  })

  it('clamps a void-bound goal back onto the leash disk', () => {
    const home = { x: 560.5, y: 91, z: -200.5 }
    const off = { x: 560.5 + 40, y: 70, z: -200.5 }
    const clamped = clampToLeash(off, home, 8)
    assert.ok(horizontalDistance(clamped, home) <= 8.01)
    assert.equal(clamped.y, 70)
  })

  it('treats missing home as unleashed', () => {
    assert.equal(withinLeash({ x: 0, y: 0, z: 0 }, null, 8), true)
  })

  it('reads anchors or waypoints from role config', () => {
    assert.equal(readAnchors({ anchors: [{ x: 1, y: 2, z: 3 }] }).length, 1)
    assert.equal(readAnchors({ waypoints: [{ x: '8', y: 9, z: 10 }] })[0].x, 8)
    assert.equal(readAnchors({}).length, 0)
  })
})
