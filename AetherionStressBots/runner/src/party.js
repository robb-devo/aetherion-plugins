import { messageText } from './helper.js'
import { stripLegacy } from './chat.js'
import { isOurBot } from './npc.js'
import { jitter, note, runEvery, sleep } from './util.js'
import { telemetry } from './telemetry.js'

/** "Kerker_Kurt invited you to a party. /party accept" → "Kerker_Kurt" (AetherionItems PartyService). */
export function partyInviter(text) {
  const match = /^(\w{3,16}) invited you to a party/.exec(stripLegacy(String(text || '')).trim())
  return match ? match[1] : null
}

/**
 * Accept party invites, but only from other bots, and only from {@code leaderOf()} when it names one.
 * Attach once per bot; returns nothing, the listener lives as long as the connection.
 */
export function attachPartyAccept(bot, { leaderOf = () => null, log = () => {} } = {}) {
  if (bot.qaPartyAttached) return
  bot.qaPartyAttached = true
  bot.on('message', (message) => {
    const inviter = partyInviter(messageText(message))
    if (!inviter || !isOurBot(inviter, bot.qaFleet?.isFleetBot)) return
    const wanted = leaderOf()
    if (wanted && wanted !== inviter) return
    setTimeout(() => {
      bot.chat('/party accept')
      bot.qaPartyLeader = inviter
      telemetry.count('party joins')
      note(bot, `joined ${inviter}'s party`, 'party')
      log(bot.stressName, `party accept (${inviter})`)
    }, jitter(1500, 0.4))
  })
}

/**
 * Groups of up to {@code size} by slot: slots 1–4 are one party, 5–8 the next. The lowest slot online in a
 * group leads it. {@code peers}: [{ name, slot, server }].
 */
export function partyPlan(peers, size = 4) {
  const groups = new Map()
  for (const peer of peers) {
    const group = Math.floor((Math.max(1, peer.slot) - 1) / size)
    if (!groups.has(group)) groups.set(group, [])
    groups.get(group).push(peer)
  }
  return [...groups.values()].map((members) => {
    const sorted = [...members].sort((a, b) => a.slot - b.slot)
    return { leader: sorted[0], members: sorted.slice(1) }
  })
}

export function myParty(bot, peers, size = 4) {
  return partyPlan(peers, size).find((p) => p.leader.name === bot.stressName || p.members.some((m) => m.name === bot.stressName)) || null
}

/**
 * Social bots on the main world: the leader of each group invites the others, everyone stays together a few
 * minutes (the party list is read, `/party` chat lines are counted), then the party is broken up and built again
 * in a new mix. Tests invites, accepts, leaves and disbands without touching people.
 */
export function createSocialLoop(bot, cfg, log) {
  const size = cfg.partySize ?? 4
  // Every bot works out the same plan from the same clock: one mix per period, then a new one.
  const periodMs = cfg.holdMs ?? 180_000
  const stats = (bot.qaStats = bot.qaStats || {})
  let running = false
  let epoch = -1
  let inParty = false
  let invited = false

  attachPartyAccept(bot, { leaderOf: () => plan()?.leader?.name || null, log })
  bot.on('message', (message) => {
    if (partyInviter(messageText(message))) inParty = true
  })

  function plan() {
    const turn = Math.floor(Date.now() / periodMs) % 12
    const peers = (bot.qaFleet?.peers?.(bot.role) || []).filter((p) => p.server === bot.qaServer)
      .map((p) => ({ ...p, slot: ((p.slot - 1 + turn) % 12) + 1 }))
    return myParty(bot, peers, size)
  }

  async function tick() {
    if (!bot.entity || bot.qaSuspended || bot.qaTravelling) return
    if (bot.qaServer && bot.qaServer !== 'mmo-r') return
    const now = Date.now()
    const current = Math.floor(now / periodMs)
    if (current !== epoch) {
      if (inParty) {
        const leading = bot.qaPartyLeader === bot.stressName
        bot.chat(leading ? '/party disband' : '/party leave')
        telemetry.count(leading ? 'party disbands' : 'party leaves')
        note(bot, leading ? 'party disbanded' : 'left the party', 'party')
      }
      epoch = current
      inParty = false
      invited = false
      bot.qaPartyLeader = null
      return
    }
    const party = plan()
    if (!party) return
    const leading = party.leader.name === bot.stressName
    // Give everyone ten seconds to leave the last mix before the new leader invites.
    if (!leading || invited || now - current * periodMs < 10_000) {
      if (inParty && Math.random() < 0.05) bot.chat('/party')
      return
    }
    invited = true
    inParty = true
    bot.qaPartyLeader = bot.stressName
    for (const member of party.members) {
      bot.chat(`/party invite ${member.name}`)
      stats.invites = (stats.invites || 0) + 1
      telemetry.count('party invites')
      await sleep(jitter(1200, 0.3))
    }
    note(bot, `invited ${party.members.map((m) => m.name).join(', ') || 'nobody'}`, 'party')
  }

  let stop = () => {}
  return function start() {
    if (running) return stop
    running = true
    log(bot.stressName, 'social loop start (parties with bots)')
    note(bot, 'social loop start', 'party')
    const halt = runEvery(bot, 4000, tick, (err) => log(bot.stressName, `social tick: ${err.message}`))
    stop = () => {
      halt()
      running = false
    }
    return stop
  }
}
