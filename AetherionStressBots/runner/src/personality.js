/** Seeded player-like variation. Hash matches Java String.hashCode. */

export const PROFILES = {
  explorer: {
    activities: ['roam', 'quest', 'forage', 'pad', 'fish'],
    aggression: 0.25,
    wanderlust: 0.85,
    patience: 0.45,
    switchiness: 0.7
  },
  farmer: {
    activities: ['forage', 'fish', 'catch', 'mine'],
    aggression: 0.2,
    wanderlust: 0.35,
    patience: 0.7,
    switchiness: 0.4
  },
  miner: {
    activities: ['mine', 'forage', 'trade'],
    aggression: 0.3,
    wanderlust: 0.25,
    patience: 0.8,
    switchiness: 0.3
  },
  fighter: {
    activities: ['combat', 'roam', 'catch'],
    aggression: 0.9,
    wanderlust: 0.45,
    patience: 0.35,
    switchiness: 0.45
  },
  fisher: {
    activities: ['fish', 'roam', 'forage'],
    aggression: 0.15,
    wanderlust: 0.4,
    patience: 0.85,
    switchiness: 0.35
  },
  general: {
    activities: ['mine', 'forage', 'combat', 'fish', 'trade', 'quest', 'roam', 'catch'],
    aggression: 0.5,
    wanderlust: 0.55,
    patience: 0.5,
    switchiness: 0.65
  },
  catcher: {
    activities: ['catch'],
    aggression: 0.2,
    wanderlust: 0.2,
    patience: 0.6,
    switchiness: 0.15
  },
  trader: {
    activities: ['trade'],
    aggression: 0.2,
    wanderlust: 0.3,
    patience: 0.55,
    switchiness: 0.2
  },
  wanderer: {
    activities: ['roam', 'pad'],
    aggression: 0.15,
    wanderlust: 0.9,
    patience: 0.4,
    switchiness: 0.5
  }
}

const ROLE_PROFILE = {
  mine: 'miner',
  mining: 'miner',
  forage: 'farmer',
  catch: 'catcher',
  combat: 'fighter',
  fish: 'fisher',
  trade: 'trader',
  quest: 'explorer',
  roam: 'wanderer',
  pad: 'wanderer',
  general: 'general'
}

const GENERAL_KEYS = ['explorer', 'farmer', 'miner', 'fighter', 'fisher', 'general']

export function javaHash(name) {
  let h = 0
  const s = String(name || '')
  for (let i = 0; i < s.length; i++) {
    h = (Math.imul(31, h) + s.charCodeAt(i)) | 0
  }
  return h
}

export function floorMod(n, m) {
  return ((n % m) + m) % m
}

export function personalityOf(name, role = 'general') {
  const key = role === 'general'
    ? GENERAL_KEYS[floorMod(javaHash(name), GENERAL_KEYS.length)]
    : (ROLE_PROFILE[role] || 'general')
  const base = PROFILES[key] || PROFILES.general
  const salt = floorMod(javaHash(name), 1000) / 1000
  const wobble = (value, amount) => Math.min(0.98, Math.max(0.05, value + (salt - 0.5) * amount))
  return {
    id: key,
    role,
    activities: [...base.activities],
    aggression: wobble(base.aggression, 0.25),
    wanderlust: wobble(base.wanderlust, 0.25),
    patience: wobble(base.patience, 0.2),
    switchiness: wobble(base.switchiness, 0.2),
    dwellMs: Math.round(35_000 + base.patience * 90_000 + salt * 25_000),
    idleChance: 0.08 + (1 - base.patience) * 0.12,
    skipChance: 0.06 + (1 - base.patience) * 0.1,
    tickJitter: 0.12 + salt * 0.2
  }
}

export function startActivity(name, role = 'general') {
  const personality = personalityOf(name, role)
  const pool = personality.activities || ['roam']
  return pool[floorMod(Math.trunc(javaHash(name) / 7), pool.length)]
}

export function pickActivity(personality, current, failed = '') {
  const pool = (personality.activities || []).filter((id) => id !== current && id !== failed)
  const choices = pool.length > 0 ? pool : (personality.activities || ['roam'])
  return choices[Math.floor(Math.random() * choices.length)]
}
