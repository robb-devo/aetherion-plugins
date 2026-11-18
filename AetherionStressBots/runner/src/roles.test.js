import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import registryLoader from 'prismarine-registry'
import { clickCommands, toPlain } from './chat.js'
import { readBar } from './timing.js'
import { chooseReply, isNpcEntity } from './quest.js'
import { isGearItem, isResourceItem, pickListing } from './trade.js'
import { reconnectDelayMs } from './fleet.js'
import { mergeConfig } from './config.js'
import { homeFor } from './safety.js'

const registry = registryLoader('1.21.1')

describe('text components', () => {
  it('turns NBT and JSON components into plain text', () => {
    const nbt = {
      type: 'compound',
      value: {
        text: { type: 'string', value: '§aFishing ' },
        extra: { type: 'list', value: { type: 'compound', value: [{ text: { type: 'string', value: 'REEL' } }] } }
      }
    }
    assert.equal(toPlain(nbt, registry), 'Fishing REEL')
    assert.equal(toPlain(JSON.stringify({ text: 'Auction ', extra: [{ text: 'House' }] }), registry), 'Auction House')
    assert.equal(toPlain('§8plain'), 'plain')
  })

  it('collects run_command buttons from a chat line', () => {
    const line = {
      text: 'Maren: ',
      extra: [
        { text: '[Ask about the isle]', clickEvent: { action: 'run_command', value: '/aetalk pick a1b2 0' } },
        { text: ' ' },
        { text: '[Accept]', click_event: { action: 'run_command', command: '/aetherionquest accept welcome_aboard' } },
        { text: '[Wiki]', clickEvent: { action: 'open_url', value: 'https://example.org' } }
      ]
    }
    assert.deepEqual(clickCommands({ json: line }), ['/aetalk pick a1b2 0', '/aetherionquest accept welcome_aboard'])
  })
})

describe('timing bars', () => {
  it('reads the fishing bar phases', () => {
    assert.equal(readBar('Fishing • 8.2s', 'white').phase, 'wait')
    assert.deepEqual(readBar('Fishing [──██◆██──] REEL', 'green'), { kind: 'fish', phase: 'strike', hot: true, perfect: false })
    assert.equal(readBar('Fishing [◆──████──]', 'red').hot, false)
    assert.equal(readBar('Fishing [──█◆█──] NOW', 'yellow').perfect, true)
  })

  it('reads the fell bar, its preview and the miss cooldown', () => {
    assert.equal(readBar('Fell · READY · LMB', 'white').phase, 'preview')
    assert.equal(readBar('Fell · 4s · other trees free', 'red').phase, 'cooling')
    assert.equal(readBar('Fell [──██◆██──] CHOP', 'green').hot, true)
  })

  it('reads the catch window', () => {
    assert.equal(readBar('Hold it ┃━━━━┃', 'purple').hot, false)
    assert.equal(readBar('✦ Now ┃━━◆━┃', 'yellow').hot, true)
    assert.equal(readBar('Locked · perfect', 'yellow').phase, 'locked')
    assert.equal(readBar('Mana 120/200', 'blue'), null)
  })
})

describe('quest dialog', () => {
  it('only talks to unlisted player entities that are not bots', () => {
    const players = { Robb: { listed: true }, QaQuest02: { listed: true } }
    assert.equal(isNpcEntity({ type: 'player', username: 'Maren' }, players), true)
    assert.equal(isNpcEntity({ type: 'player', username: 'Robb' }, players), false)
    assert.equal(isNpcEntity({ type: 'player', username: 'QaQuest02' }, {}), false)
    assert.equal(isNpcEntity({ type: 'mob', username: 'Maren' }, players), false)
  })

  it('prefers accept, never declines while another reply exists', () => {
    const commands = ['/aetalk pick t 0', '/aetherionquest accept welcome_aboard', '/aetherionquest decline welcome_aboard']
    assert.equal(chooseReply(commands, () => 0), '/aetherionquest accept welcome_aboard')
    const replies = ['/aetalk pick t 0', '/aetherionquest decline x', '/spawn']
    for (const roll of [0.8, 0.99]) assert.equal(chooseReply(replies, () => roll), '/aetalk pick t 0')
    assert.equal(chooseReply(['/spawn']), null)
  })
})

describe('trading', () => {
  const listings = [
    { slot: 0, seller: 'Robb', price: 10, mine: false },
    { slot: 1, seller: 'QaTrade04', price: 900, mine: false },
    { slot: 2, seller: 'QaTrade01', price: 50, mine: true },
    { slot: 3, seller: 'StressM02', price: 30, mine: false }
  ]

  it('buys only from bots unless told otherwise', () => {
    const slots = new Set()
    for (let i = 0; i < 20; i++) slots.add(pickListing(listings, { rng: () => i / 20 }).slot)
    assert.deepEqual([...slots].sort(), [1, 3])
    assert.equal(pickListing(listings, { purse: 100, rng: () => 0 }).slot, 3)
    assert.equal(pickListing(listings, { buyFromPlayers: true, rng: () => 0 }).slot, 0)
    assert.equal(pickListing([listings[0]]), null)
  })

  it('sorts what goes to which counter', () => {
    assert.equal(isGearItem({ name: 'iron_pickaxe' }), true)
    assert.equal(isGearItem({ name: 'oak_log' }), false)
    assert.equal(isResourceItem({ name: 'spruce_log' }), true)
    assert.equal(isResourceItem({ name: 'diamond_sword' }), false)
  })
})

describe('fleet and config', () => {
  it('backs off reconnects and caps the wait', () => {
    assert.equal(reconnectDelayMs(0), 8000)
    assert.equal(reconnectDelayMs(2), 32_000)
    assert.equal(reconnectDelayMs(9), 120_000)
  })

  it('fills config.json from the example, local values win', () => {
    const merged = mergeConfig(
      { port: 1, safety: { voidFloorY: 40, stuckMs: 10 }, mine: { ores: ['a', 'b'] } },
      { port: 2, safety: { stuckMs: 99 }, mine: { ores: ['c'] }, velocitySecret: 's' }
    )
    assert.deepEqual(merged, { port: 2, safety: { voidFloorY: 40, stuckMs: 99 }, mine: { ores: ['c'] }, velocitySecret: 's' })
  })

  it('keeps home where the plugin put the bot unless it stands on an anchor', () => {
    const anchors = [{ x: 22.5, y: 90, z: 580.5 }]
    assert.deepEqual(homeFor({ x: 25, y: 90, z: 582 }, anchors), anchors[0])
    assert.deepEqual(homeFor({ x: 25, y: 60, z: 582 }, anchors), { x: 25, y: 60, z: 582 })
    assert.deepEqual(homeFor({ x: 200, y: 90, z: 580 }, anchors), { x: 200, y: 90, z: 580 })
  })
})
