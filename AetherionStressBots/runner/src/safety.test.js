import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  clampToLeash,
  goalProgress,
  horizontalDistance,
  isBelowFloor,
  nearestAnchor,
  readAnchors,
  shouldCancelStuck,
  stuckTimeoutMs,
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

describe('forage stuck cancel', () => {
  it('gives diggers a longer freeze window than roamers', () => {
    assert.equal(stuckTimeoutMs({ stuckMs: 10_000 }), 10_000)
    assert.equal(stuckTimeoutMs({ gathering: true, gatherStuckMs: 18_000, stuckMs: 10_000 }), 18_000)
    assert.equal(stuckTimeoutMs({ digging: true, digStuckMs: 28_000, stuckMs: 10_000 }), 28_000)
  })

  it('does not cancel a still-approaching dig path', () => {
    assert.equal(shouldCancelStuck({
      moved: false,
      progressedTowardGoal: true,
      gathering: true,
      frozenMs: 20_000,
      gatherStuckMs: 18_000
    }), false)
  })

  it('does not cancel an active dig before digStuckMs', () => {
    assert.equal(shouldCancelStuck({
      moved: false,
      digging: true,
      frozenMs: 12_000,
      stuckMs: 10_000,
      digStuckMs: 28_000
    }), false)
  })

  it('cancels when frozen with no goal progress past the gather window', () => {
    assert.equal(shouldCancelStuck({
      moved: false,
      progressedTowardGoal: false,
      gathering: true,
      frozenMs: 19_000,
      gatherStuckMs: 18_000
    }), true)
  })

  it('treats shrinking distance to the log as progress', () => {
    const first = goalProgress({ x: 10, y: 90, z: 0 }, { x: 0, y: 90, z: 0 }, undefined)
    assert.equal(first.progressed, true)
    const closer = goalProgress({ x: 8, y: 90, z: 0 }, { x: 0, y: 90, z: 0 }, first.dist)
    assert.equal(closer.progressed, true)
    const same = goalProgress({ x: 8, y: 90, z: 0 }, { x: 0, y: 90, z: 0 }, closer.dist)
    assert.equal(same.progressed, false)
  })
})
