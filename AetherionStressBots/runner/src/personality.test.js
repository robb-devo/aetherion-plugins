import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { floorMod, javaHash, personalityOf, pickActivity } from './personality.js'

describe('personality', () => {
  it('hashes ascii names stably and uniquely', () => {
    assert.equal(javaHash('QaMine01'), javaHash('QaMine01'))
    assert.notEqual(javaHash('QaGeneral01'), javaHash('QaGeneral02'))
    assert.equal(typeof javaHash('QaGeneral01'), 'number')
  })

  it('gives different general bots different profiles', () => {
    const a = personalityOf('QaGeneral01', 'general')
    const b = personalityOf('QaGeneral02', 'general')
    const c = personalityOf('QaGeneral03', 'general')
    const ids = new Set([a.id, b.id, c.id])
    assert.ok(ids.size >= 2)
    assert.ok(a.activities.length >= 3)
  })

  it('keeps dedicated roles on their profile family', () => {
    assert.equal(personalityOf('QaMine03', 'mine').id, 'miner')
    assert.equal(personalityOf('QaCombat01', 'combat').id, 'fighter')
    assert.equal(personalityOf('QaFish02', 'fish').id, 'fisher')
  })

  it('picks a different activity after a failure', () => {
    const p = personalityOf('QaGeneral01', 'general')
    const next = pickActivity(p, 'mine', 'mine')
    assert.notEqual(next, 'mine')
    assert.ok(p.activities.includes(next) || next === 'roam')
  })

  it('floorMod stays non-negative', () => {
    assert.equal(floorMod(-1, 6), 5)
    assert.equal(floorMod(7, 6), 1)
  })
})
