import { ask } from './helper.js'
import { cancelPath } from './safety.js'
import { closeWindow } from './gui.js'
import { note, sleep } from './util.js'
import { telemetry } from './telemetry.js'

/**
 * The roads players take between the servers. The main world is the crossroads: the hub only leads there,
 * and mmo-d, mmo-c and mmo-i are reached from it and lead back to it. Nothing leads back to the hub; a player
 * who wants the lobby logs out and in again (so does a bot, see {@link reconnectToHub}).
 */
export const MAIN = 'mmo-r'
export const HUB = 'hub'

export const ROADS = {
  'hub>mmo-r': ['/aetherion', '/play'],
  'mmo-r>mmo-d': ['/dhub'],
  'mmo-d>mmo-r': ['/dungeon home', '/spawn'],
  'mmo-r>mmo-c': ['/exile go'],
  'mmo-c>mmo-r': ['/exile return', '/spawn'],
  'mmo-r>mmo-i': ['/island'],
  'mmo-i>mmo-r': ['/spawn', '/capital']
}

/** Hops from {@code from} to {@code to}, always through the main world. Null when there is no road (to the hub). */
export function planRoute(from, to) {
  if (!to || from === to) return []
  if (to === HUB) return null
  if (!from || from === '?') return null
  const hops = []
  let at = from
  if (at !== MAIN) {
    if (!ROADS[`${at}>${MAIN}`]) return null
    hops.push({ from: at, to: MAIN })
    at = MAIN
  }
  if (to !== MAIN) {
    if (!ROADS[`${MAIN}>${to}`]) return null
    hops.push({ from: MAIN, to })
  }
  return hops
}

/** Resolves with the next server the bot says hello from (or null after {@code timeoutMs}). */
export function nextServer(bot, timeoutMs) {
  return new Promise((resolve) => {
    let done = false
    const finish = (value) => {
      if (done) return
      done = true
      clearTimeout(timer)
      bot.removeListener('qa:server', onServer)
      bot.removeListener('end', onEnd)
      resolve(value)
    }
    const onServer = (server) => finish(server)
    const onEnd = () => finish(null)
    const timer = setTimeout(() => finish(null), timeoutMs)
    bot.on('qa:server', onServer)
    bot.once('end', onEnd)
  })
}

/** Waits until the fleet knows which server the bot is on (first hello after a login). */
export async function knownServer(bot, timeoutMs = 30_000) {
  if (bot.qaServer) return bot.qaServer
  return nextServer(bot, timeoutMs)
}

/**
 * Take the bot to {@code target} the way a player would: the road's commands in order, then (when every road
 * command left the bot where it was) the plugin's `/testbot goto` fallback. Every hop lands in telemetry, so
 * /botreport shows which roads work and how long they take. Resolves true when the bot is on {@code target}.
 */
export async function travel(bot, target, { log = () => {}, hopTimeoutMs = 25_000, fallback = true } = {}) {
  for (let attempt = 0; attempt < 4; attempt++) {
    const from = await knownServer(bot)
    if (!from) return false
    if (from === target) return true
    const route = planRoute(from, target)
    if (!route || route.length === 0) {
      telemetry.problem(bot.stressName, `no road ${from} → ${target}`)
      return false
    }
    const hop = route[0]
    const arrived = await takeHop(bot, hop, { log, hopTimeoutMs, fallback })
    if (!arrived) return false
  }
  return bot.qaServer === target
}

async function takeHop(bot, hop, { log, hopTimeoutMs, fallback }) {
  const key = `${hop.from}>${hop.to}`
  cancelPath(bot)
  if (bot.currentWindow) closeWindow(bot)
  bot.qaTravelling = true
  note(bot, `road ${hop.from} → ${hop.to}`, 'travel')
  try {
    for (const command of ROADS[key] || []) {
      const started = Date.now()
      const waiting = nextServer(bot, hopTimeoutMs)
      bot.chat(command)
      const landed = await waiting
      if (landed === hop.to) {
        telemetry.hop(hop.from, hop.to, 'road', Date.now() - started)
        bump(bot, 'hops')
        log(bot.stressName, `${command}: ${hop.from} → ${hop.to} in ${((Date.now() - started) / 1000).toFixed(1)}s`)
        return true
      }
      if (landed && landed !== hop.from) {
        // Somewhere else (a fallback server after a refused connect): the caller plans again from there.
        telemetry.hop(hop.from, hop.to, 'failed', null, `${command} landed on ${landed}`)
        return true
      }
      note(bot, `${command} kept me on ${hop.from}`, 'travel')
    }
    if (!fallback || hop.from === 'hub') {
      telemetry.hop(hop.from, hop.to, 'failed', null, `${(ROADS[key] || []).join(', ')} did not move`)
      telemetry.problem(bot.stressName, `road ${hop.from} → ${hop.to} did not move`)
      return false
    }
    const started = Date.now()
    const waiting = nextServer(bot, hopTimeoutMs)
    const answer = await ask(bot, `goto ${hop.to}`, { timeoutMs: 5000 })
    if (!answer || answer.words.includes('refused')) {
      telemetry.hop(hop.from, hop.to, 'failed', null, `goto ${answer ? 'refused' : 'unanswered'}`)
      telemetry.problem(bot.stressName, `road ${hop.from} → ${hop.to}: player road and /testbot goto both failed`)
      return false
    }
    const landed = await waiting
    if (landed === hop.to) {
      telemetry.hop(hop.from, hop.to, 'fallback', Date.now() - started, `${(ROADS[key] || []).join(', ')} did not move`)
      bump(bot, 'hops')
      return true
    }
    telemetry.hop(hop.from, hop.to, 'failed', null, `goto ok but still on ${landed || hop.from}`)
    return false
  } finally {
    bot.qaTravelling = false
  }
}

/** The way back to the lobby: log out and in again. The fleet reconnects through the proxy, which lands on the hub. */
export async function reconnectToHub(bot) {
  bot.qaWantReconnect = true
  note(bot, 'log out (back to the hub)', 'travel')
  await sleep(300)
  try {
    bot.quit('testbot: back to the hub')
  } catch {
    /* ignore */
  }
}

function bump(bot, key) {
  bot.qaStats = bot.qaStats || {}
  bot.qaStats[key] = (bot.qaStats[key] || 0) + 1
}
