import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  alreadyThere,
  hasDigApproach,
  isFailedBlock,
  isFooting,
  isRailName,
  rememberFailure,
  shouldIssueGoal,
  shouldReleaseJump
} from './move.js'
import { dashboardUrl } from './dashboard.js'

describe('path goal churn', () => {
  const here = { x: 10, y: 90, z: 10 }

  it('does not reissue the same goal while it is still fresh', () => {
    assert.equal(shouldIssueGoal({
      moving: false,
      current: here,
      next: { x: 10.2, y: 90, z: 10.1 },
      now: 5_000,
      issuedAt: 4_200,
      minIntervalMs: 1400
    }), false)
  })

  it('does not replace a goal the bot is already walking', () => {
    assert.equal(shouldIssueGoal({
      moving: true,
      current: here,
      next: { x: 11, y: 90, z: 10.4 },
      now: 9_000,
      issuedAt: 1_000
    }), false)
  })

  it('allows a genuinely different goal after the debounce', () => {
    assert.equal(shouldIssueGoal({
      moving: false,
      current: here,
      next: { x: 18, y: 90, z: 14 },
      now: 5_000,
      issuedAt: 2_000
    }), true)
  })

  it('treats a bot standing on the goal as already there', () => {
    assert.equal(alreadyThere({ x: 10.4, y: 90.1, z: 10.2 }, here, 1.6), true)
    assert.equal(alreadyThere({ x: 14, y: 90, z: 10 }, here, 1.6), false)
  })
})

describe('dig targets that cause corner jumps', () => {
  it('rejects an ore with no standable face', () => {
    const solid = new Set(['1,64,0', '0,64,1', '-1,64,0', '0,64,-1', '1,65,0', '0,65,1', '-1,65,0', '0,65,-1'])
    const blockAt = (x, y, z) => solid.has(`${x},${y},${z}`) ? 'stone' : 'air'
    assert.equal(hasDigApproach(blockAt, 0, 64, 0), false)
  })

  it('accepts an ore with air beside it and floor under that air', () => {
    const blockAt = (x, y, z) => {
      if (x === 1 && y === 63 && z === 0) return 'stone'
      if (x === 1 && y >= 64 && z === 0) return 'air'
      return 'stone'
    }
    assert.equal(hasDigApproach(blockAt, 0, 64, 0), true)
  })

  it('does not dig the block under the bot feet', () => {
    const block = { position: { x: 4, y: 89, z: 7 } }
    assert.equal(isFooting({ x: 4.5, y: 90.1, z: 7.4 }, block), true)
    assert.equal(isFooting({ x: 7.5, y: 90, z: 7.5 }, block), false)
  })

  it('remembers a failed corner so it is not retried immediately', () => {
    const bot = {}
    rememberFailure(bot, { x: 3.2, y: 70, z: 8.8 }, 1_000, 5_000)
    assert.equal(isFailedBlock(bot, { x: 3, y: 70, z: 8 }, 2_000), true)
    assert.equal(isFailedBlock(bot, { x: 3, y: 70, z: 8 }, 7_000), false)
  })
})

describe('stuck jump / rail float', () => {
  it('releases jump only after a hover, not during a normal hop', () => {
    assert.equal(shouldReleaseJump({ onGround: false, velocityY: 0.4, airMs: 200, horizontalSpeed: 0.2 }), false)
    assert.equal(shouldReleaseJump({ onGround: true, velocityY: 0, airMs: 2000, horizontalSpeed: 0 }), false)
    assert.equal(shouldReleaseJump({ onGround: false, velocityY: 0.01, airMs: 900, horizontalSpeed: 0.02 }), true)
  })

  it('recognises rail blocks', () => {
    assert.equal(isRailName('powered_rail'), true)
    assert.equal(isRailName('minecraft:rail'), true)
    assert.equal(isRailName('stone'), false)
  })
})

describe('dashboard url', () => {
  it('joins a public base with a session token', () => {
    assert.equal(dashboardUrl('http://play.example:18765/', 'abc'), 'http://play.example:18765/?token=abc')
    assert.equal(dashboardUrl('http://play.example:18765', ''), 'http://play.example:18765/')
    assert.equal(dashboardUrl('', 'abc'), '')
    assert.equal(dashboardUrl('not a url', 'abc'), '')
  })
})
