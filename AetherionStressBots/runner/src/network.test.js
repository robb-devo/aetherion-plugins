import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { NAME_POOLS, botName, isBotName, slotOf, validName } from './names.js'
import { parseTb } from './helper.js'
import { planRoute, ROADS } from './travel.js'
import { createTelemetry } from './telemetry.js'
import { partyInviter, partyPlan } from './party.js'
import { friendRequester } from './island.js'
import { KINDS, SLOTS, nextPageSlots, windowKind } from './gui.js'
import { isRipe } from './farm.js'
import { pickStep, TOUR_STEPS } from './tour.js'
import { connectTarget, PROXY_GAP_MS } from './fleet.js'
import { isOurBot } from './npc.js'
import { peopleOnline } from './dungeon.js'

function botWith(size, items) {
  const slots = new Array(size + 36).fill(null)
  for (const [slot, item] of Object.entries(items)) slots[Number(slot)] = item
  return { currentWindow: { title: '', inventoryStart: size, slots } }
}
const item = (name, customName) => ({ name, customName })

describe('login names', () => {
  it('are valid Minecraft names and never repeat across roles', () => {
    const all = Object.values(NAME_POOLS).flat()
    for (const name of all) assert.ok(validName(name), name)
    assert.equal(new Set(all.map((n) => n.toLowerCase())).size, all.length)
  })

  it('number the slots past the end of a pool and stay within 16 characters', () => {
    const pool = NAME_POOLS.mine
    assert.equal(botName('mine', 1), pool[0])
    assert.equal(botName('mine', pool.length + 1), `${pool[0]}2`)
    for (let i = 1; i <= 40; i++) {
      for (const role of Object.keys(NAME_POOLS)) assert.ok(validName(botName(role, i)), `${role} ${i}`)
    }
    assert.equal(slotOf('mine', botName('mine', 11)), 11)
  })

  it('keeps the QaMine01 style and the old StressM names', () => {
    assert.equal(botName('mine', 3, { style: 'qa' }), 'QaMine03')
    assert.equal(botName('mining', 2), 'StressM02')
    assert.equal(botName('general', 1, { style: 'qa' }), 'QaGeneral01')
  })

  it('recognises bot names, not people', () => {
    assert.ok(isBotName('Kumpel_Kalle'))
    assert.ok(isBotName('Kumpel_Kalle2'))
    assert.ok(isBotName('Testbot_Toni'))
    assert.ok(isBotName('QaTrade04'))
    assert.ok(isBotName(botName('trade', 30)))
    assert.ok(!isBotName('Robb'))
    assert.ok(!isBotName('Ast1'))
    assert.ok(!isBotName('Shuminite'))
  })

  it('fights and invites only the fleet\'s own names when the fleet is known', () => {
    const fleet = (name) => name === 'Pit_Bulli'
    assert.ok(isOurBot('Pit_Bulli', fleet))
    assert.ok(!isOurBot('Knock_Knut', fleet))
    assert.ok(isOurBot('Knock_Knut'))
  })
})

describe('helper lines', () => {
  it('parses verb, key=value pairs, words and position', () => {
    const line = parseTb('§7[tb] hello server=mmo-r world=world 12.5 64.0 -3.5 role=mine setup=yes')
    assert.equal(line.verb, 'hello')
    assert.equal(line.kv.server, 'mmo-r')
    assert.equal(line.kv.setup, 'yes')
    assert.deepEqual(line.pos, { x: 12.5, y: 64, z: -3.5 })
    assert.deepEqual(parseTb('[tb] stage farm ok 1.0 2.0 3.0').words, ['farm', 'ok'])
    assert.equal(parseTb('hello there'), null)
  })
})

describe('roads', () => {
  it('always go through the main world', () => {
    assert.deepEqual(planRoute('hub', 'mmo-d'), [{ from: 'hub', to: 'mmo-r' }, { from: 'mmo-r', to: 'mmo-d' }])
    assert.deepEqual(planRoute('mmo-c', 'mmo-i'), [{ from: 'mmo-c', to: 'mmo-r' }, { from: 'mmo-r', to: 'mmo-i' }])
    assert.deepEqual(planRoute('mmo-r', 'mmo-r'), [])
  })

  it('never lead back to the hub', () => {
    assert.equal(planRoute('mmo-r', 'hub'), null)
    assert.equal(planRoute(null, 'mmo-d'), null)
    for (const key of Object.keys(ROADS)) assert.ok(!key.endsWith('>hub'), key)
  })
})

describe('telemetry', () => {
  it('turns roads, menus, counts and problems into report lines', () => {
    let now = 0
    const book = createTelemetry(() => now)
    book.hop('hub', 'mmo-r', 'road', 2400)
    book.hop('mmo-r', 'mmo-c', 'fallback', 6000, '/exile go did not move')
    book.menu('/skills', 140)
    book.menu('/gamble', null)
    book.count('dungeon runs finished')
    book.problem('Kerker_Kurt', 'dungeon hub: no Dungeon Keeper found')
    book.problem('Kerker_Kurt', 'dungeon hub: no Dungeon Keeper found')
    now = 5 * 60_000
    const lines = book.lines([
      { name: 'Testbot_Toni', role: 'general', server: 'mmo-d', step: 'dungeon', online: true },
      { name: 'Pit_Bulli', role: 'pit', server: 'hub', step: 'pit', online: true }
    ])
    const text = lines.join('\n')
    assert.match(text, /Bots online: (mmo-d 1 · hub 1|hub 1 · mmo-d 1) \(runner up 5 min\)/)
    assert.match(text, /hub→mmo-r 1 ok avg 2\.4s/)
    assert.match(text, /mmo-r→mmo-c 0 ok 1 fallback .*\(\/exile go did not move\)/)
    assert.match(text, /\/gamble never opened 1 missed/)
    assert.match(text, /Who is where: general@mmo-d \(dungeon\)/)
    assert.match(text, /Problem: Kerker_Kurt: dungeon hub: no Dungeon Keeper found ×2/)
  })
})

describe('parties and friends', () => {
  it('groups by slot, lowest slot leads', () => {
    const plan = partyPlan([
      { name: 'B', slot: 2 }, { name: 'A', slot: 1 }, { name: 'E', slot: 5 }, { name: 'C', slot: 3 }
    ], 4)
    assert.equal(plan[0].leader.name, 'A')
    assert.deepEqual(plan[0].members.map((m) => m.name), ['B', 'C'])
    assert.equal(plan[1].leader.name, 'E')
  })

  it('reads invites and friend requests', () => {
    assert.equal(partyInviter('§eKerker_Kurt invited you to a party. /party accept'), 'Kerker_Kurt')
    assert.equal(partyInviter('Someone said hi'), null)
    assert.equal(friendRequester('Kokos_Klaus wants to be friends.'), 'Kokos_Klaus')
  })

  it('counts people, not bots, on a dungeon server', () => {
    const players = {
      Robb: { username: 'Robb' },
      Kerker_Kurt: { username: 'Kerker_Kurt' },
      Npc1: { username: 'Npc1', listed: false }
    }
    assert.deepEqual(peopleOnline(players), ['Robb'])
  })
})

describe('network windows', () => {
  it('finds the Dungeon Keeper menu', () => {
    const bot = botWith(54, {
      [SLOTS.dungeonHeader]: item('end_portal_frame', 'Dungeons'),
      [SLOTS.dungeonFloor1]: item('deepslate_bricks', 'Floor 1 · Prison'),
      [SLOTS.dungeonClose]: item('barrier', 'Close')
    })
    assert.equal(windowKind(bot).kind, KINDS.dungeon)
  })

  it('finds the slots table', () => {
    const bot = botWith(54, {
      [SLOTS.slotsLogo]: item('gold_block', "Lucky Vince's Slots"),
      [SLOTS.slotsSpin]: item('lever', 'Spin')
    })
    assert.equal(windowKind(bot).kind, KINDS.slots)
  })

  it('reads the island starter pick, open or locked', () => {
    const starters = Object.fromEntries(SLOTS.starters.map((slot) => [slot, item('grass_block', 'Starter')]))
    const open = botWith(27, { ...starters, [SLOTS.starterHeader]: item('nether_star', 'Pick your island') })
    assert.deepEqual(windowKind(open), { kind: KINDS.starter, state: 'open' })
    const locked = botWith(27, { ...starters, [SLOTS.starterHeader]: item('clock', 'Level 20') })
    assert.equal(windowKind(locked).state, 'locked')
  })

  it('pages forward on arrows only', () => {
    const bot = botWith(54, {
      45: item('arrow', '« Back'),
      53: item('arrow', 'Next page ▶'),
      49: item('emerald', 'Next: buy')
    })
    assert.deepEqual(nextPageSlots(bot), [53])
  })
})

describe('farming', () => {
  it('cuts only ripe crops', () => {
    const crop = (name, age) => ({ name, getProperties: () => ({ age }) })
    assert.ok(isRipe(crop('wheat', 7)))
    assert.ok(!isRipe(crop('wheat', 6)))
    assert.ok(isRipe(crop('beetroots', 3)))
    assert.ok(isRipe({ name: 'melon' }))
    assert.ok(!isRipe({ name: 'stone' }))
  })
})

describe('the tour', () => {
  it('never repeats a step and honours weight 0', () => {
    for (let i = 0; i < 200; i++) {
      const id = pickStep({ last: 'mine', weights: { dungeon: 0 } })
      assert.ok(id && id !== 'mine' && id !== 'dungeon', id)
    }
  })

  it('starts with the step it was told to (the Pit after a relog)', () => {
    assert.equal(pickStep({ startWith: 'pit', server: 'hub' }), 'pit')
    assert.equal(pickStep({ server: 'hub', rng: () => 0.1 }), 'pit')
  })

  it('covers every server of the network', () => {
    const servers = new Set(Object.values(TOUR_STEPS).map((s) => s.server))
    assert.deepEqual([...servers].sort(), ['hub', 'mmo-c', 'mmo-d', 'mmo-i', 'mmo-r'])
  })
})

describe('login target', () => {
  it('goes through the proxy door when a token is set', () => {
    const target = connectTarget({ connect: { mode: 'proxy', proxy: { host: '127.0.0.1', port: 25565, token: 'ABCdef0123456789abcdef01' } } })
    assert.equal(target.mode, 'proxy')
    assert.equal(target.fakeHost, 'abcdef0123456789abcdef01.testbots')
    assert.ok(target.gapMs >= PROXY_GAP_MS)
  })

  it('falls back to a direct login without a token', () => {
    const target = connectTarget({ host: '127.0.0.1', port: 1, connect: { mode: 'proxy', proxy: { token: 'CHANGE_ME' } } })
    assert.equal(target.mode, 'direct')
    assert.equal(target.server, 'mmo-r')
    assert.ok(target.fellBack)
  })
})
