import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { FLAGS, ACTIVITIES, isWorkingActivity, maybeSkip, playstyleOf } from './playstyle.js'

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

describe('playstyle flags', () => {
  it('gives trade bots the TRADER flag and starter coins', () => {
    const style = playstyleOf('trade', { starterCoins: 2500 })
    assert.deepEqual(style.flags, [FLAGS.TRADER])
    assert.equal(style.starterCoins, 2500)
  })

  it('keeps gather roles as pocket change', () => {
    const style = playstyleOf('mine')
    assert.equal(style.flags.length, 0)
    assert.equal(style.starterCoins, 250)
  })

  it('treats new activities as working so idle cancel does not fire', () => {
    assert.equal(isWorkingActivity(ACTIVITIES.ah), true)
    assert.equal(isWorkingActivity(ACTIVITIES.bazaar), true)
    assert.equal(isWorkingActivity(ACTIVITIES.questDialog), true)
    assert.equal(isWorkingActivity(ACTIVITIES.minigame), true)
    assert.equal(isWorkingActivity(ACTIVITIES.padHop), true)
    assert.equal(isWorkingActivity(ACTIVITIES.idle), false)
  })
})
