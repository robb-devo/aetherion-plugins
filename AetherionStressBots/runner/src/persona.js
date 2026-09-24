/**
 * Stable per-bot personality. Same login name always gets the same traits,
 * so a fleet does not share one optimal script.
 */

export function hashString(value) {
  let hash = 2166136261
  const text = String(value || '')
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

export function mulberry32(seed) {
  let state = seed >>> 0
  return function next() {
    state = (state + 0x6D2B79F5) | 0
    let t = Math.imul(state ^ (state >>> 15), 1 | state)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function clamp01(n) {
  return Math.max(0, Math.min(1, n))
}

function pickCombatStyle(rng, caution, aggression) {
  if (aggression > 0.72 && caution < 0.42) return 'aggressive'
  if (caution > 0.68) return 'cautious'
  const roll = rng()
  if (roll < 0.28) return 'strafe'
  if (roll < 0.5) return 'burst'
  if (aggression > 0.58) return 'aggressive'
  return 'balanced'
}

function pickEconomyStyle(rng, greed) {
  const roll = rng()
  if (greed > 0.78) return roll < 0.55 ? 'flipper' : 'saver'
  if (greed < 0.28) return 'spender'
  if (roll < 0.22) return 'saver'
  if (roll < 0.44) return 'flipper'
  if (roll < 0.62) return 'spender'
  return 'casual'
}

function sellThreshold(style) {
  if (style === 'saver') return 28
  if (style === 'flipper') return 10
  if (style === 'spender') return 18
  return 14
}

/**
 * @param {string} name login name (QaMine01, …)
 * @param {string} role
 */
export function personaFor(name, role = 'roam') {
  const rng = mulberry32(hashString(`${role}:${name}`))
  const patience = clamp01(0.15 + rng() * 0.8)
  const greed = clamp01(0.08 + rng() * 0.88)
  const caution = clamp01(0.1 + rng() * 0.85)
  const sociability = clamp01(rng() * 0.9)
  const clumsiness = clamp01(rng() * 0.55)
  const focus = clamp01(0.25 + rng() * 0.7)
  const aggression = clamp01(0.15 + rng() * 0.8)
  const economyStyle = pickEconomyStyle(rng, greed)
  const combatStyle = pickCombatStyle(rng, caution, aggression)
  return {
    name: String(name || ''),
    role,
    patience,
    greed,
    caution,
    sociability,
    clumsiness,
    focus,
    aggression,
    economyStyle,
    combatStyle,
    retreatAt: clamp01(0.18 + caution * 0.5),
    eatAt: clamp01(0.28 + caution * 0.4),
    eatHunger: 3 + Math.round(caution * 8),
    lingerGapMs: Math.round(14_000 + focus * 46_000),
    sideJobGapMs: Math.round(75_000 + (1 - greed) * 140_000),
    sellAt: sellThreshold(economyStyle),
    chatGapMs: Math.round(45_000 + (1 - sociability) * 150_000),
    attackMul: combatStyle === 'aggressive' ? 0.72 : combatStyle === 'cautious' ? 1.45 : combatStyle === 'burst' ? 0.85 : 1,
    wanderMul: 0.65 + (1 - focus) * 0.7
  }
}

const CHAT = {
  social: ['hey', 'yo', 'oh hey', 'you grinding too?', 'nice spot', 'quiet over here'],
  mine: ['vein is mid', 'coal again', 'one more stack', 'pick feels fine'],
  forage: ['lots of trees', 'this grove is huge', 'need a better axe eventually'],
  combat: ['these hit hard', 'almost ate that', 'stay back', 'got one'],
  fish: ['nothing biting', 'one more cast', 'water is calm'],
  trade: ['prices are weird', 'listed something', 'window shopping', 'broke-ish'],
  catch: ['hold still', 'missed that', 'cute thing'],
  quest: ['where was that npc', 'uh huh', 'okay okay'],
  roam: ['just looking', 'wrong island', 'back in a sec'],
  pad: ['that hop was high', 'again', 'bad landing'],
  low: ['ouch', 'need food', 'that hurt', 'too close'],
  death: ['rip me', 'that was dumb', 'respawned'],
  idle: ['brb', 'hmm', 'one sec', 'lag?']
}

export function chatLine(kind, role, persona, rng = Math.random) {
  const pool = CHAT[kind] || CHAT[role] || CHAT.idle
  const fallback = CHAT[role] || CHAT.idle
  const line = (pool[Math.floor(rng() * pool.length)] || fallback[0] || 'hmm')
  return humanize(line, persona, rng)
}

export function humanize(line, persona, rng = Math.random) {
  const text = String(line || '')
  const clumsy = persona?.clumsiness ?? 0
  if (rng() > clumsy * 0.55 || text.length < 4) return text
  if (rng() < 0.5) return text + (rng() < 0.5 ? ' lol' : '')
  const at = 1 + Math.floor(rng() * (text.length - 2))
  return text.slice(0, at) + text.slice(at + 1)
}

let fleetChatAt = 0

export function resetChatGate() {
  fleetChatAt = 0
}

/** Fleet-wide gap so dozens of bots cannot talk over each other. */
export function allowChat(now, persona, rng = Math.random) {
  if (now < fleetChatAt) return false
  if (persona && now < (persona.nextChatAt || 0)) return false
  if (rng() > 0.92 && (persona?.sociability ?? 0) < 0.85) return false
  return true
}

export function markChat(now, persona) {
  const fleetGap = 7000 + Math.floor(Math.random() * 5000)
  fleetChatAt = now + fleetGap
  if (persona) {
    const personal = persona.chatGapMs ?? 90_000
    persona.nextChatAt = now + Math.round(personal * (0.75 + Math.random() * 0.6))
  }
}
