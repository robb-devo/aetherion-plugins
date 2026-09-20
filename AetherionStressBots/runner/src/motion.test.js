import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { isSpinning, sameGoal } from './motion.js'

describe('motion helpers', () => {
  it('treats nearby goals as the same destination', () => {
    assert.equal(sameGoal({ x: 10, y: 90, z: 4 }, { x: 10.4, y: 90.2, z: 4.2 }, 1.5), true)
    assert.equal(sameGoal({ x: 10, y: 90, z: 4 }, { x: 18, y: 90, z: 4 }, 1.5), false)
  })

  it('flags in-place yaw spin and ignores walking turns', () => {
    const spinner = {
      entity: { yaw: 0, position: { x: 0, y: 90, z: 0 } },
      qaYawSamples: []
    }
    for (let i = 0; i < 8; i++) {
      spinner.entity.yaw = i * 0.9
      assert.equal(typeof isSpinning(spinner), 'boolean')
    }
    assert.equal(isSpinning(spinner), true)

    const walker = {
      entity: { yaw: 0, position: { x: 0, y: 90, z: 0 } },
      qaYawSamples: []
    }
    for (let i = 0; i < 8; i++) {
      walker.entity.yaw = i * 0.15
      walker.entity.position = { x: i * 0.4, y: 90, z: 0 }
      isSpinning(walker)
    }
    assert.equal(isSpinning(walker), false)
  })
})
