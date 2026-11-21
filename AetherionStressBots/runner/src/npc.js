import pathfinderPkg from 'mineflayer-pathfinder'
import { toPlain } from './chat.js'
import { closeWindow, windowKind } from './gui.js'
import { isBotName } from './names.js'
import { sleep } from './util.js'

const { goals } = pathfinderPkg

/** The name an entity shows: a player's name, else its custom name (metadata 2), else its type. */
export function entityLabel(entity, registry) {
  if (!entity) return ''
  if (entity.type === 'player' && entity.username) return String(entity.username)
  const raw = entity.metadata?.[2]
  if (raw != null && raw !== false) {
    const text = toPlain(raw, registry).trim()
    if (text) return text
  }
  return String(entity.displayName || entity.name || '')
}

/**
 * Another bot of this fleet: the only players a bot ever hits or invites. With the fleet's own list at hand only its
 * exact names count (a person who happens to be called Testbot_Toni is left alone); without it, any runner name.
 */
export function isBotPlayer(entity, isFleetBot) {
  if (!entity || entity.type !== 'player' || !entity.username) return false
  return isOurBot(entity.username, isFleetBot)
}

/** {@link isBotPlayer} for a bare name. */
export function isOurBot(name, isFleetBot) {
  if (!name) return false
  return typeof isFleetBot === 'function' ? Boolean(isFleetBot(name)) : isBotName(name)
}

/** People who can be talked to: unlisted player entities (FancyNpcs), villagers and other named passive mobs. */
export function isTalkable(entity, bot) {
  if (!entity || entity === bot.entity) return false
  if (entity.type === 'player') {
    if (!entity.username || isBotName(entity.username)) return false
    const entry = bot.players?.[entity.username]
    return !entry || entry.listed === false
  }
  const name = String(entity.name || '').toLowerCase()
  if (name === 'villager' || name === 'wandering_trader') return true
  return entity.type !== 'hostile' && entity.metadata?.[2] != null && !['item', 'experience_orb', 'arrow'].includes(name)
}

export function nearestEntity(bot, predicate, radius = 32) {
  const me = bot.entity?.position
  if (!me) return null
  let best = null
  let bestDist = radius
  for (const entity of Object.values(bot.entities || {})) {
    if (!entity || entity === bot.entity || !entity.position) continue
    if (!predicate(entity)) continue
    const dist = me.distanceTo(entity.position)
    if (dist < bestDist) {
      best = entity
      bestDist = dist
    }
  }
  return best
}

export function entitiesBy(bot, predicate, radius = 32) {
  const me = bot.entity?.position
  if (!me) return []
  return Object.values(bot.entities || {})
    .filter((entity) => entity && entity !== bot.entity && entity.position && predicate(entity)
      && me.distanceTo(entity.position) <= radius)
    .sort((a, b) => me.distanceTo(a.position) - me.distanceTo(b.position))
}

/** Walk until within {@code range} of a point (gives up after {@code timeoutMs}). True when close enough. */
export async function walkTo(bot, pos, { range = 2.5, timeoutMs = 20_000 } = {}) {
  if (!bot.pathfinder || !pos) return false
  const goal = new goals.GoalNear(pos.x, pos.y, pos.z, Math.max(1, Math.floor(range)))
  const started = Date.now()
  try {
    bot.pathfinder.setGoal(goal)
  } catch {
    return false
  }
  while (Date.now() - started < timeoutMs) {
    const here = bot.entity?.position
    if (!here) return false
    if (here.distanceTo(pos) <= range + 0.6) {
      bot.pathfinder.setGoal(null)
      return true
    }
    if (!bot.pathfinder.isMoving() && Date.now() - started > 2500) break
    await sleep(250)
  }
  try {
    bot.pathfinder.setGoal(null)
  } catch {
    /* ignore */
  }
  return bot.entity?.position ? bot.entity.position.distanceTo(pos) <= range + 1.5 : false
}

/** Right-click an entity the way a player does (look at it first). */
export async function talkTo(bot, entity) {
  if (!entity?.position) return false
  try {
    await bot.lookAt(entity.position.offset(0, (entity.height || 1.6) * 0.85, 0), true)
    await bot.activateEntity(entity)
    return true
  } catch {
    return false
  }
}

/**
 * Walk up to nearby people one by one and right-click them until one opens a window that {@code wanted(kindInfo)}
 * accepts (the dungeon keeper's menu, a trader …). Other windows are closed again. Returns the entity or null.
 */
export async function findNpcWithWindow(bot, wanted, { radius = 40, tries = 5, remember = null } = {}) {
  const candidates = entitiesBy(bot, (e) => isTalkable(e, bot), radius)
  if (remember) {
    candidates.sort((a, b) => a.position.distanceTo(remember) - b.position.distanceTo(remember))
  }
  for (const entity of candidates.slice(0, tries)) {
    if (!(await walkTo(bot, entity.position, { range: 2.5, timeoutMs: 15_000 }))) continue
    await talkTo(bot, entity)
    const opened = await waitFor(() => Boolean(bot.currentWindow), 2500)
    if (opened && wanted(windowKind(bot))) return entity
    if (bot.currentWindow) closeWindow(bot)
    await sleep(400)
  }
  return null
}

export async function waitFor(predicate, timeoutMs, stepMs = 120) {
  const started = Date.now()
  while (Date.now() - started < timeoutMs) {
    if (predicate()) return true
    await sleep(stepMs)
  }
  return predicate()
}
