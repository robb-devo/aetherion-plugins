import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { maybeSkip } from './playstyle.js'

describe('player-like playstyle helpers', () => {
  it('never skips at chance 0', () => {
    for (let i = 0; i < 20; i++) {
      assert.equal(maybeSkip(0), false)
    }
  })

  it('always skips at chance 1', () => {
    for (let i = 0; i < 20; i++) {
      assert.equal(maybeSkip(1), true)
    }
  })
})
