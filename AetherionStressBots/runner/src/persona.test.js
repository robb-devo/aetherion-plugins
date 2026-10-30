import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  allowChat,
  chatLine,
  hashString,
  markChat,
  personaFor,
  resetChatGate
} from './persona.js'

describe('persona', () => {
  it('is stable for the same login name', () => {
    const a = personaFor('QaMine01', 'mine')
    const b = personaFor('QaMine01', 'mine')
    assert.equal(a.combatStyle, b.combatStyle)
    assert.equal(a.economyStyle, b.economyStyle)
    assert.equal(a.greed, b.greed)
    assert.equal(hashString('QaMine01'), hashString('QaMine01'))
  })

  it('does not give a whole fleet one personality', () => {
    const people = []
    for (let i = 1; i <= 8; i++) {
      people.push(personaFor(`QaMine${String(i).padStart(2, '0')}`, 'mine'))
    }
    const combats = new Set(people.map((p) => p.combatStyle))
    const economies = new Set(people.map((p) => p.economyStyle))
    assert.ok(combats.size >= 2, [...combats].join(','))
    assert.ok(economies.size >= 2, [...economies].join(','))
    for (const person of people) {
      for (const key of ['patience', 'greed', 'caution', 'sociability', 'focus']) {
        assert.ok(person[key] >= 0 && person[key] <= 1, key)
      }
    }
  })

  it('spaces chat across the fleet', () => {
    resetChatGate()
    const quiet = personaFor('QaRoam01', 'roam')
    const now = 1_000_000
    assert.equal(allowChat(now, quiet, () => 0.1), true)
    markChat(now, quiet)
    assert.equal(allowChat(now + 1000, quiet, () => 0.1), false)
    assert.equal(allowChat(now + 1000, personaFor('QaRoam02', 'roam'), () => 0.1), false)
  })

  it('keeps lines short', () => {
    const line = chatLine('social', 'roam', personaFor('QaRoam03', 'roam'), () => 0.99)
    assert.ok(line.length > 0)
    assert.ok(line.length < 80)
  })
})
