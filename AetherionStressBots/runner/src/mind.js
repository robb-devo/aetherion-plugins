import { emptyEconomy, applyCoinEvent, parseCoinText } from './economy.js'
import { allowChat, chatLine, markChat, personaFor } from './persona.js'
import {
  countMatching,
  findFood,
  isResourceItem,
  nearbyPlayers,
  summarizeInventory,
  surplusGearCount,
  threatened
} from './sense.js'
import { note, sleep } from './util.js'

/**
 * What to do besides the role script. Pure so tests can pin imperfect choices.
 */
export function decideInterrupt({
  health = 20,
  food = 20,
  digging = false,
  moving = false,
  economyBusy = false,
  threatened: inDanger = false,
  persona,
  sellable = 0,
  msSinceSide = 999999,
  msSinceLinger = 999999,
  role = 'roam',
  rng = Math.random
} = {}) {
  const style = persona || personaFor('default', role)
  const out = {
    lingerMs: 0,
    eat: false,
    retreat: false,
    sideSell: false,
    chat: null,
    look: false,
    newGoal: false
  }
  const hp = Math.max(0, Math.min(1, health / 20))
  const retreatAt = style.combatStyle === 'aggressive' ? Math.min(style.retreatAt, 0.22) : style.retreatAt
  if (hp < retreatAt) out.retreat = true
  if (!digging && (hp < style.eatAt || food <= style.eatHunger)) out.eat = true

  if (inDanger) {
    if (out.retreat && rng() < 0.18) out.chat = 'low'
    return out
  }

  if (!digging && !moving && !economyBusy && msSinceLinger > style.lingerGapMs) {
    const lingerChance = 0.06 + (1 - style.focus) * 0.16
    if (rng() < lingerChance) {
      out.lingerMs = Math.round(900 + style.patience * 4200 * (0.4 + rng()))
    }
  }
  const sideRole = role !== 'trade'
  if (sideRole && !digging && !moving && !economyBusy && !out.lingerMs
      && sellable >= style.sellAt && msSinceSide > style.sideJobGapMs) {
    if (rng() < 0.12 + style.greed * 0.55) out.sideSell = true
  }
  if (rng() < style.sociability * 0.4) out.look = true
  if (rng() < style.sociability * 0.07) out.chat = 'social'
  else if (rng() < 0.012 + style.sociability * 0.02) out.chat = role
  if (!digging && !out.sideSell && rng() < (1 - style.focus) * 0.07) out.newGoal = true
  return out
}

export function isLingering(bot, now = Date.now()) {
  return !!(bot?.qaLingerUntil && now < bot.qaLingerUntil)
}

export function shouldYield(bot, now = Date.now()) {
  if (!bot?.entity || bot.qaSuspended) return true
  if (bot.qaEating) return true
  if (bot.qaEconomyBusy) return true
  if (isLingering(bot, now)) return true
  return false
}

export function goalText(bot) {
  if (bot?.qaGoalLabel) return bot.qaGoalLabel
  const activity = bot?.qaActivity || 'idle'
  const persona = bot?.qaPersona
  if (persona?.economyStyle && (activity === 'ah' || activity === 'bazaar' || activity === 'trading')) {
    return `${activity} (${persona.economyStyle})`
  }
  if (activity === 'combat' || activity === 'fighting') {
    const style = persona?.combatStyle || 'balanced'
    const target = bot.qaTargetName ? ` ${bot.qaTargetName}` : ''
    return `${style} combat${target}`
  }
  return activity
}

export function describeBot(bot, now = Date.now()) {
  const persona = bot?.qaPersona || null
  const economy = bot?.qaEconomy || emptyEconomy()
  const pos = bot?.entity?.position
  let heldItem = '-'
  try {
    heldItem = bot?.heldItem?.name || '-'
  } catch {
    heldItem = '-'
  }
  return {
    name: bot?.stressName || bot?.username || '?',
    role: bot?.role || '',
    activity: bot?.qaActivity || 'idle',
    goal: goalText(bot),
    target: bot?.qaTargetName || '',
    heldItem,
    deaths: bot?.qaDeaths || 0,
    lastAction: bot?.qaLastAction || '',
    lastError: bot?.qaLastError || '',
    recentActions: bot?.qaRecent || [],
    position: pos ? { x: pos.x, y: pos.y, z: pos.z } : null,
    world: bot?.game?.dimension || 'overworld',
    health: Number.isFinite(bot?.health) ? bot.health : null,
    food: Number.isFinite(bot?.food) ? bot.food : null,
    uptimeMs: bot?.qaSince ? Math.max(0, now - bot.qaSince) : 0,
    persona: persona ? {
      combatStyle: persona.combatStyle,
      economyStyle: persona.economyStyle,
      sociability: round3(persona.sociability),
      caution: round3(persona.caution),
      greed: round3(persona.greed),
      focus: round3(persona.focus),
      patience: round3(persona.patience)
    } : null,
    inventory: summarizeInventory(bot),
    economy: {
      purse: economy.purse,
      purseKnown: !!economy.purseKnown,
      listed: economy.listed || 0,
      bought: economy.bought || 0,
      sales: economy.sales || 0,
      spent: economy.spent || 0,
      earned: economy.earned || 0,
      failed: economy.failed || 0,
      skipped: economy.skipped || 0,
      browsed: economy.browsed || 0
    },
    chats: bot?.qaChats || 0,
    eats: bot?.qaEats || 0
  }
}

export function attachMind(bot, log) {
  const persona = personaFor(bot.stressName || bot.username, bot.role)
  bot.qaPersona = persona
  bot.qaEconomy = emptyEconomy(bot.role === 'trade' ? 2500 : 250)
  bot.qaSince = bot.qaSince || Date.now()
  bot.qaLastSideJob = 0
  bot.qaLastLinger = 0
  bot.qaChats = 0
  bot.qaEats = 0
  bot.qaGoalLabel = `${bot.role} (${persona.combatStyle}/${persona.economyStyle})`

  const onChat = (text) => {
    const event = parseCoinText(text)
    if (!event) return
    applyCoinEvent(bot.qaEconomy, event)
  }
  bot.on('messagestr', onChat)
  bot.on('message', (json) => {
    try {
      onChat(typeof json?.toString === 'function' ? json.toString() : String(json || ''))
    } catch {
      /* ignore */
    }
  })

  const first = setTimeout(() => {
    const handle = setInterval(() => {
      tick().catch((err) => log?.(bot.stressName, `mind: ${err.message}`))
    }, 2800 + Math.floor(Math.random() * 900))
    bot.once('end', () => clearInterval(handle))
  }, Math.floor(Math.random() * 2200))
  bot.once('end', () => clearTimeout(first))

  async function tick() {
    if (!bot.entity || bot.qaSuspended || bot.qaEating || bot.qaEconomyBusy) return
    const now = Date.now()
    if (bot.qaActivity === 'error' && bot.qaErrorAt && now - bot.qaErrorAt > 4000) {
      note(bot, 'back to it', 'idle')
      bot.qaNeedNewGoal = true
    }

    const decision = decideInterrupt({
      health: bot.health ?? 20,
      food: bot.food ?? 20,
      digging: !!bot.qaDigging,
      moving: !!bot.pathfinder?.isMoving?.(),
      economyBusy: !!bot.qaEconomyBusy,
      threatened: threatened(bot, 7),
      persona,
      sellable: countMatching(bot, isResourceItem),
      msSinceSide: now - (bot.qaLastSideJob || 0),
      msSinceLinger: now - (bot.qaLastLinger || 0),
      role: bot.role
    })

    bot.qaRetreat = decision.retreat
    if (decision.eat && findFood(bot)) {
      await eat(bot, log)
      return
    }
    if (decision.lingerMs > 0 && !bot.qaDigging) {
      bot.qaLingerUntil = now + decision.lingerMs
      bot.qaLastLinger = now
      note(bot, 'hanging around', 'lingering')
      try { bot.pathfinder?.setGoal(null) } catch { /* ignore */ }
    }
    if (decision.newGoal) bot.qaNeedNewGoal = true
    if (decision.look) lookAtSomeone(bot)
    if (decision.chat) maybeSay(bot, decision.chat, persona)
    if (decision.sideSell) {
      bot.qaLastSideJob = now
      const { opportunisticSell } = await import('./trade.js')
      await opportunisticSell(bot, log)
    }
    if (!decision.lingerMs && !decision.sideSell) {
      bot.qaGoalLabel = goalText(bot)
    }
  }
}

async function eat(bot, log) {
  const food = findFood(bot)
  if (!food || bot.qaEating) return false
  bot.qaEating = true
  note(bot, `eat ${food.name}`, 'eating')
  try {
    if (bot.equip) await bot.equip(food, 'hand')
    if (typeof bot.consume === 'function') {
      await bot.consume()
    } else {
      bot.activateItem()
      await sleep(1400)
      try { bot.deactivateItem() } catch { /* ignore */ }
    }
    bot.qaEats = (bot.qaEats || 0) + 1
    return true
  } catch (err) {
    log?.(bot.stressName, `eat: ${err.message}`)
    return false
  } finally {
    bot.qaEating = false
  }
}

function lookAtSomeone(bot) {
  const people = nearbyPlayers(bot, 10)
  if (people.length === 0 || bot.qaDigging) return
  const person = people[Math.floor(Math.random() * people.length)]
  bot.lookAt(person.position.offset(0, person.height || 1.6, 0), true).catch(() => {})
  note(bot, `notice ${person.username || 'player'}`, bot.qaActivity || 'idle')
}

function maybeSay(bot, kind, persona) {
  const now = Date.now()
  if (!allowChat(now, persona)) return
  const line = chatLine(kind, bot.role, persona)
  try {
    bot.chat(line)
    markChat(now, persona)
    bot.qaChats = (bot.qaChats || 0) + 1
    note(bot, `say: ${line}`, bot.qaActivity || 'chatting')
  } catch {
    /* ignore */
  }
}

function round3(n) {
  return Math.round((Number(n) || 0) * 1000) / 1000
}

export function surplusForTrade(bot) {
  return {
    resources: countMatching(bot, isResourceItem),
    gearSurplus: surplusGearCount(bot)
  }
}
