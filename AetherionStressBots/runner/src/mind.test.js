import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { decideInterrupt, describeBot, shouldYield } from './mind.js'
import { personaFor } from './persona.js'
import { dashboardHtml } from './dashboard.js'
import http from 'node:http'
import { startControlServer } from './control.js'

describe('mind', () => {
  it('retreats and does not go shopping while something is hitting them', () => {
    const persona = { ...personaFor('QaCombat01', 'combat'), retreatAt: 0.5, combatStyle: 'cautious' }
    const decision = decideInterrupt({
      health: 4,
      food: 20,
      threatened: true,
      persona,
      sellable: 64,
      msSinceSide: 999999,
      role: 'combat',
      rng: () => 0
    })
    assert.equal(decision.retreat, true)
    assert.equal(decision.sideSell, false)
    assert.equal(decision.lingerMs, 0)
  })

  it('lets a greedy miner leave to sell once the stack is worth the trip', () => {
    const persona = {
      ...personaFor('QaMine04', 'mine'),
      greed: 0.95,
      focus: 0.9,
      sellAt: 10,
      lingerGapMs: 999999,
      sideJobGapMs: 1000,
      eatAt: 0.1,
      eatHunger: 0,
      retreatAt: 0.05,
      sociability: 0
    }
    const decision = decideInterrupt({
      health: 20,
      food: 20,
      digging: false,
      moving: false,
      threatened: false,
      persona,
      sellable: 32,
      msSinceSide: 50_000,
      msSinceLinger: 0,
      role: 'mine',
      rng: () => 0.2
    })
    assert.equal(decision.sideSell, true)
    assert.equal(decision.lingerMs, 0)
  })

  it('yields while eating, selling, or lingering', () => {
    const now = 5_000
    assert.equal(shouldYield({ entity: {}, qaEating: true }, now), true)
    assert.equal(shouldYield({ entity: {}, qaEconomyBusy: true }, now), true)
    assert.equal(shouldYield({ entity: {}, qaLingerUntil: now + 1000 }, now), true)
    assert.equal(shouldYield({ entity: {} }, now), false)
    assert.equal(shouldYield({ qaSuspended: true, entity: {} }, now), true)
  })

  it('keeps forty bots from making the same choice every tick', () => {
    const roles = ['mine', 'forage', 'catch', 'roam', 'combat', 'fish', 'trade', 'quest', 'pad']
    const seen = new Set()
    let sells = 0
    let lingers = 0
    let retreats = 0
    for (let n = 1; n <= 40; n++) {
      const role = roles[n % roles.length]
      const persona = personaFor(`Qa${role}${String(n).padStart(2, '0')}`, role)
      for (let step = 0; step < 40; step++) {
        const decision = decideInterrupt({
          health: step % 17 === 0 ? 3 : 18,
          food: 16,
          digging: step % 5 === 0,
          moving: step % 4 === 0,
          threatened: step % 17 === 0,
          persona,
          sellable: 10 + (n % 30),
          msSinceSide: step * 4000,
          msSinceLinger: step * 3000,
          role,
          rng: () => ((n * 17 + step * 13) % 100) / 100
        })
        if (decision.sideSell) sells++
        if (decision.lingerMs > 0) lingers++
        if (decision.retreat) retreats++
        if (step % 17 === 0) assert.equal(decision.sideSell, false)
        seen.add(`${decision.retreat}:${decision.sideSell}:${decision.lingerMs > 0}:${decision.chat || ''}`)
      }
    }
    assert.ok(sells > 0)
    assert.ok(lingers > 0)
    assert.ok(retreats > 0)
    assert.ok(seen.size >= 4)
  })

  it('describes a bot for the dashboard without a live client', () => {
    const view = describeBot({
      stressName: 'QaTrade01',
      role: 'trade',
      qaActivity: 'bazaar',
      qaGoalLabel: 'bazaar sell',
      qaPersona: personaFor('QaTrade01', 'trade'),
      qaEconomy: { purse: 1800, purseKnown: true, listed: 2, bought: 1, sales: 0, spent: 200, earned: 0, failed: 0, skipped: 1, browsed: 3 },
      qaSince: 1_000,
      qaDeaths: 1,
      qaChats: 2,
      health: 18,
      food: 14,
      entity: { position: { x: 22.4, y: 56, z: 305.2 } },
      inventory: { items: () => [{ name: 'coal', count: 20 }] }
    }, 11_000)
    assert.equal(view.name, 'QaTrade01')
    assert.equal(view.goal, 'bazaar sell')
    assert.equal(view.economy.purse, 1800)
    assert.equal(view.inventory[0].count, 20)
    assert.equal(view.uptimeMs, 10_000)
    assert.ok(view.persona.economyStyle)
  })
})

describe('dashboard', () => {
  it('serves a page that polls /status and keeps the control API', async () => {
    const html = dashboardHtml()
    assert.match(html, /Stress Bots/)
    assert.match(html, /fetch\('status'/)
    assert.match(html, /id="start"/)
    assert.match(html, /stop-all/)
    assert.match(html, /id="roles"/)
    assert.match(html, /lastAction/)

    const fleet = {
      size: () => 0,
      snapshot: () => ({ bots: [], roles: {}, desired: {}, economy: { listed: 0 }, chats: 0 })
    }
    const server = startControlServer({
      host: '127.0.0.1',
      port: 0,
      token: 'secret',
      fleet,
      log: () => {}
    })
    await new Promise((resolve) => server.once('listening', resolve))
    const port = server.address().port
    try {
      const page = await get(port, '/')
      assert.equal(page.status, 200)
      assert.match(page.body, /Stress Bots/)
      const denied = await get(port, '/status')
      assert.equal(denied.status, 401)
      const ok = await get(port, '/status', { 'x-testbots-token': 'secret' })
      assert.equal(ok.status, 200)
      assert.match(ok.body, /"bots":\[\]/)
      const minted = await post(port, '/session', { hours: 1 }, { 'x-testbots-token': 'secret' })
      assert.equal(minted.status, 200)
      const session = JSON.parse(minted.body).token
      assert.ok(session)
      const withSession = await get(port, '/status?token=' + encodeURIComponent(session))
      assert.equal(withSession.status, 200)
    } finally {
      await new Promise((resolve, reject) => server.close((err) => err ? reject(err) : resolve()))
    }
  })
})

function get(port, path, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = http.get({ host: '127.0.0.1', port, path, headers }, (res) => {
      const chunks = []
      res.on('data', (chunk) => chunks.push(chunk))
      res.on('end', () => resolve({ status: res.statusCode, body: Buffer.concat(chunks).toString('utf8') }))
    })
    req.on('error', reject)
  })
}

function post(port, path, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify(body || {})
    const req = http.request({
      host: '127.0.0.1',
      port,
      path,
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) }
    }, (res) => {
      const chunks = []
      res.on('data', (chunk) => chunks.push(chunk))
      res.on('end', () => resolve({ status: res.statusCode, body: Buffer.concat(chunks).toString('utf8') }))
    })
    req.on('error', reject)
    req.end(payload)
  })
}
