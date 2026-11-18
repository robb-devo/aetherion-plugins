import mineflayer from 'mineflayer'
import pathfinderPkg from 'mineflayer-pathfinder'
import { attachVelocityForwarding } from './velocity.js'
import { attachSafety, cancelPath, readAnchors } from './safety.js'
import { attachPlaystyle } from './playstyle.js'
import { attachLocale } from './locale.js'
import { attachWindowWatchdog } from './windows.js'
import { attachHelper, ask } from './helper.js'
import { activityConfig, loopFor } from './activities.js'
import { createTourLoop } from './tour.js'
import { closeWindow } from './gui.js'
import { botName } from './names.js'
import { HUB, MAIN, reconnectToHub, travel } from './travel.js'
import { telemetry } from './telemetry.js'
import { fmtPos, heldName, note, runEvery, sleep } from './util.js'
import { toPlain } from './chat.js'

const { goals } = pathfinderPkg

export const WAVE1 = ['mine', 'forage', 'catch', 'roam']
export const WAVE2 = ['combat', 'fish', 'trade', 'quest', 'pad']
/** The network roles: the tour, the main world's extras, the Pit on the hub and the far servers. */
export const WAVE3 = ['general', 'farm', 'menus', 'casino', 'social', 'pit', 'dungeon', 'exile', 'island']
export const ALL_ROLES = [...WAVE1, ...WAVE2, ...WAVE3, 'mining']

/** Roles that only play on the main world: they walk there from the hub and back after any detour. */
const MAIN_ROLES = new Set([...WAVE1, ...WAVE2, 'mining', 'farm', 'menus', 'casino', 'social'])
/** Roles whose loops travel on their own once they are kitted (mmo-d, mmo-c, mmo-i). */
const FAR_ROLES = new Set(['dungeon', 'exile', 'island'])
/** Roles that walk wherever their job takes them: no runner leash, only a void hold far below. */
const FREE_ROLES = new Set(['general', 'pit', 'dungeon', 'exile', 'island'])
const LOOSE_ROLES = new Set(['farm', 'menus', 'casino', 'social'])

/** Velocity lets one login through per address every 3 s; the gate keeps a little more than that. */
export const PROXY_GAP_MS = 3500

/** Reconnect delay after {@code fails} short-lived sessions in a row: 8 s, 16 s, 32 s … capped. */
export function reconnectDelayMs(fails, { baseMs = 8000, maxMs = 120_000 } = {}) {
  const n = Math.max(0, Math.min(10, Number(fails) || 0))
  return Math.min(maxMs, baseMs * 2 ** n)
}

/**
 * Where the bots log in. "proxy" (the default once a token is set): through Velocity like every player, with the
 * test-bot door's token in the handshake host, so they start on the hub and walk the network's roads. "direct":
 * straight into one backend (the old setup, MMO-R only) with forged Velocity forwarding.
 */
export function connectTarget(config) {
  const connect = config.connect || {}
  const proxy = connect.proxy || {}
  const token = String(proxy.token || '').trim().toLowerCase()
  const wantsProxy = (connect.mode || 'proxy') === 'proxy'
  if (wantsProxy && token && token !== 'change_me') {
    return {
      mode: 'proxy',
      host: proxy.host || '127.0.0.1',
      port: Number(proxy.port || 25565),
      fakeHost: `${token}.testbots`,
      gapMs: Math.max(PROXY_GAP_MS, Number(config.joinDelayMs) || 0)
    }
  }
  return {
    mode: 'direct',
    host: config.host,
    port: Number(config.port),
    server: connect.directServer || MAIN,
    gapMs: Math.max(250, Number(config.joinDelayMs) || 2500),
    fellBack: wantsProxy && connect.mode === 'proxy'
  }
}

export function createFleet({ config, log }) {
  const slots = new Map()
  // Per login name, kept across reconnects: counters, kicks, the reconnect streak, where the tour left off.
  const meta = new Map()
  const maxFails = Math.max(1, Number(config.reconnect?.maxFails ?? 8))
  const stableMs = Number(config.reconnect?.stableMs ?? 60_000)
  const desired = Object.fromEntries(ALL_ROLES.map((role) => [role, 0]))
  const target = connectTarget(config)
  const nameStyle = config.names?.style || 'german'
  // Tells this runner's logins apart in the plugin's /botreport (a restart starts a new session).
  const session = `r${Date.now().toString(36)}`
  let shuttingDown = false
  let spawnQueue = Promise.resolve()
  let nextConnectAt = 0

  if (target.fellBack) {
    log('fleet', 'connect.mode is proxy but connect.proxy.token is missing: logging in directly instead (MMO-R only)')
  }
  log('fleet', `bots log in ${target.mode === 'proxy' ? 'through the proxy' : 'directly'} at ${target.host}:${target.port}`)

  const fleetApi = {
    isFleetBot: (name) => slots.has(name),
    peers: (role) => [...slots.values()]
      .filter((bot) => bot.role === role && bot.entity && !bot.managedStop)
      .map((bot) => ({ name: bot.stressName, slot: bot.qaSlot, server: bot.qaServer }))
  }

  function nameFor(role, slot) {
    return botName(role, slot, { style: nameStyle, prefixes: config.prefixes || {} })
  }

  function roleConfig(role) {
    return activityConfig(config, role)
  }

  /** One login at a time, {@code gapMs} apart, for first logins and reconnects alike. */
  async function connectGate() {
    const now = Date.now()
    const at = Math.max(now, nextConnectAt)
    nextConnectAt = at + target.gapMs
    if (at > now) await sleep(at - now)
  }

  function safetyFor(role, cfg) {
    const safety = config.safety || {}
    if (FREE_ROLES.has(role)) return { voidFloorY: cfg.voidFloorY ?? -512, leashRadius: Infinity }
    if (LOOSE_ROLES.has(role)) return { voidFloorY: cfg.voidFloorY ?? -64, leashRadius: cfg.leashRadius ?? Infinity }
    return {
      voidFloorY: cfg.voidFloorY ?? safety.voidFloorY ?? 40,
      leashRadius: cfg.leashRadius ?? safety.leashRadius ?? defaultLeash(role)
    }
  }

  function defaultLeash(role) {
    if (role === 'catch') return 12
    if (role === 'roam' || role === 'fish' || role === 'trade' || role === 'quest') return 14
    if (role === 'combat') return 22
    if (role === 'forage') return 20
    if (role === 'pad') return 72
    return 16
  }

  function metaFor(name, role) {
    if (!meta.has(name)) {
      meta.set(name, {
        role, fails: 0, reconnects: 0, kicks: 0, lastKick: '', stats: {}, gaveUp: false, startWith: null, lastStep: null
      })
    }
    return meta.get(name)
  }

  /** Which server the bot just landed on: the plugin answers `hello` on every backend, the hub has no plugin. */
  async function identify(bot) {
    const login = bot.qaLogins
    await sleep(1500)
    for (let attempt = 0; attempt < 2; attempt++) {
      if (login !== bot.qaLogins || bot.qaEnded) return
      const answer = await ask(bot, `hello ${bot.role} ${bot.qaSlot} ${session}`, { timeoutMs: 4500 })
      if (login !== bot.qaLogins || bot.qaEnded) return
      if (answer?.kv?.server) {
        const server = answer.kv.server === '?' ? (target.mode === 'direct' ? target.server : '?') : answer.kv.server
        arrive(bot, server, answer.kv.world)
        return
      }
    }
    arrive(bot, target.mode === 'proxy' ? HUB : target.server, '')
  }

  function arrive(bot, server, world) {
    bot.qaServer = server
    bot.qaWorld = world || ''
    bot.qaArrivedAt = Date.now()
    note(bot, `on ${server}${world ? ` (${world})` : ''}`, bot.qaActivity)
    log(bot.stressName, `on ${server}${world ? ` / ${world}` : ''} @ ${fmtPos(bot.entity?.position)}`)
    bot.emit('qa:server', server)
  }

  /** Kit and role spot from the plugin, once per arrival on the main world (the plugin clears and refills). */
  async function ensureKit(bot) {
    if (bot.qaServer !== MAIN) return false
    if (bot.qaKitLogin === bot.qaLogins) return true
    const answer = await ask(bot, `hello ${bot.role} ${bot.qaSlot} setup ${session}`, { timeoutMs: 8000 })
    if (answer?.kv?.setup === 'yes') {
      bot.qaKitLogin = bot.qaLogins
      telemetry.count('kits handed out')
      note(bot, 'kitted on the main world', bot.qaActivity)
      // The plugin clears the inventory, hands out the kit and walks the bot to its role spot.
      await sleep(3500)
      return true
    }
    telemetry.problem(bot.stressName, `no kit on ${MAIN} (${answer ? answer.line : 'no answer'})`)
    return false
  }

  /**
   * What a role does once the bot knows where it is: the tour drives itself everywhere, the Pit bot wants the hub,
   * the main-world roles walk to MMO-R, get their kit and start their job (and walk back after any detour), the far
   * roles get kitted on MMO-R and then travel on their own.
   */
  function attachDriver(bot) {
    const role = bot.role
    const cfg = roleConfig(role)
    const loopOf = (id, overrides) => loopFor(bot, id, config, log, overrides)
    const main = role === 'general' ? createTourLoop(bot, cfg, log, { loopFor: loopOf }) : loopOf(role)
    let stopLoop = null
    let nextTry = 0
    let kitMisses = 0
    let pitWarned = false

    const start = () => {
      if (!stopLoop) stopLoop = main()
    }
    const halt = () => {
      if (!stopLoop) return
      stopLoop()
      stopLoop = null
      cancelPath(bot)
      if (bot.currentWindow) closeWindow(bot)
    }

    runEvery(bot, 2500, async () => {
      if (!bot.entity || bot.qaTravelling || !bot.qaServer) return
      const server = bot.qaServer
      if (role === 'general') {
        start()
        return
      }
      if (role === 'pit') {
        if (server === HUB) {
          start()
          return
        }
        halt()
        if (target.mode !== 'proxy') {
          if (!pitWarned) telemetry.problem(bot.stressName, 'the Pit is on the hub: pit bots need connect.mode proxy')
          pitWarned = true
          return
        }
        if (Date.now() < nextTry) return
        nextTry = Date.now() + 60_000
        await reconnectToHub(bot)
        return
      }
      if (FAR_ROLES.has(role) && stopLoop) return
      if (server !== MAIN) {
        halt()
        if (Date.now() < nextTry) return
        if (!(await travel(bot, MAIN, { log }))) nextTry = Date.now() + 30_000
        return
      }
      if (stopLoop) return
      if (!(await ensureKit(bot)) && ++kitMisses < 3) {
        nextTry = Date.now() + 20_000
        return
      }
      kitMisses = 0
      start()
    }, (err) => log(bot.stressName, `driver: ${err.message}`))
  }

  async function connect(name, role, slot, still = () => true) {
    await connectGate()
    if (shuttingDown || !still()) return null
    const info = metaFor(name, role)
    info.gaveUp = false
    const bot = mineflayer.createBot({
      host: target.host,
      port: target.port,
      ...(target.fakeHost ? { fakeHost: target.fakeHost } : {}),
      username: name,
      version: config.version,
      auth: 'offline',
      hideErrors: true,
      checkTimeoutInterval: 60_000
    })

    bot.role = role
    bot.stressName = name
    bot.qaSlot = slot
    bot.qaMeta = info
    bot.qaFleet = fleetApi
    bot.qaActivity = 'idle'
    bot.qaRecent = []
    bot.qaDeaths = 0
    bot.qaLastError = ''
    bot.managedStop = false
    bot.qaStats = info.stats
    bot.qaSpawnedAt = 0
    bot.qaServer = null
    bot.qaLogins = 0
    bot.qaEnsureKit = () => ensureKit(bot)

    if (target.mode === 'direct' && config.velocitySecret && config.velocitySecret !== 'CHANGE_ME') {
      attachVelocityForwarding(bot, config.velocitySecret, name)
    }
    attachHelper(bot)

    bot.on('login', () => {
      bot.qaLogins++
      bot.qaServer = null
      if (bot.qaLogins === 1) log(name, `login → ${target.host}:${target.port} (${role} #${slot})`)
      identify(bot).catch((err) => log(name, `identify: ${err.message}`))
    })
    // The proxy sends the network's pack on every login; a player who declines is kicked.
    bot.on('resourcePack', () => {
      try {
        bot.acceptResourcePack()
      } catch {
        /* configuration phase answers itself */
      }
    })
    bot.on('spawn', () => {
      if (!bot.qaSpawnedAt) bot.qaSpawnedAt = Date.now()
      note(bot, `spawn @ ${fmtPos(bot.entity?.position)}`, 'idle')
    })
    bot.on('death', () => {
      bot.qaDeaths = (bot.qaDeaths || 0) + 1
      bot.qaSuspended = true
      note(bot, 'died', 'recovering')
      bot.qaLastError = 'died'
      log(name, `died on ${bot.qaServer || '?'}`)
    })
    bot.on('kicked', (reason) => {
      const text = kickText(reason, bot.registry)
      info.kicks++
      info.lastKick = text
      note(bot, `kicked: ${text}`, 'error')
      bot.qaLastError = text
      telemetry.problem(name, `kicked on ${bot.qaServer || 'login'}: ${text}`)
      log(name, `kicked: ${text}`)
    })
    bot.on('error', (err) => {
      if (err && (err.name === 'PartialReadError' || /PartialReadError|SlotComponent/.test(String(err)))) {
        return
      }
      bot.qaLastError = err.message
      note(bot, `error: ${err.message}`, 'error')
      log(name, `error: ${err.message}`)
    })

    const cfg = roleConfig(role)
    attachSafety(bot, {
      ...safetyFor(role, cfg),
      anchors: FREE_ROLES.has(role) ? [] : readAnchors(cfg),
      stuckMs: cfg.stuckMs ?? config.safety?.stuckMs ?? 10_000,
      digStuckMs: cfg.digStuckMs ?? config.safety?.digStuckMs ?? 28_000,
      gatherStuckMs: cfg.gatherStuckMs ?? config.safety?.gatherStuckMs ?? 18_000,
      idleGoalMs: cfg.idleGoalMs ?? config.safety?.idleGoalMs ?? 8000,
      resumeDelayMs: config.safety?.resumeDelayMs ?? 2800,
      goals,
      log
    })
    attachLocale(bot, log)
    attachWindowWatchdog(bot, log)
    attachPlaystyle(bot, log)
    attachDriver(bot)

    bot.on('end', (reason) => {
      bot.qaEnded = true
      if (bot.managedStop || shuttingDown) {
        log(name, `disconnected: ${reason || 'end'}`)
        if (slots.get(name) === bot) slots.delete(name)
        return
      }
      let delay
      if (bot.qaWantReconnect) {
        // Logged out on purpose (the way back to the hub): straight back in, not a failure.
        delay = 1500 + Math.round(Math.random() * 1500)
      } else {
        const lived = bot.qaSpawnedAt ? Date.now() - bot.qaSpawnedAt : 0
        info.fails = lived >= stableMs ? 0 : info.fails + 1
        if (info.fails >= maxFails) {
          info.gaveUp = true
          if (slots.get(name) === bot) slots.delete(name)
          telemetry.problem(name, `gave up after ${info.fails} short sessions (last kick: ${info.lastKick || 'none'})`)
          log(name, `disconnected: ${reason || 'end'}: gave up after ${info.fails} short sessions`
            + ` (last kick: ${info.lastKick || 'none'}). Start the role again to retry.`)
          return
        }
        delay = reconnectDelayMs(info.fails - 1) + Math.round(Math.random() * 2000)
      }
      log(name, `disconnected: ${reason || 'end'}, back in ${Math.round(delay / 1000)}s`)
      setTimeout(async () => {
        if (shuttingDown || bot.managedStop || slots.get(name) !== bot) return
        info.reconnects++
        try {
          // Stopped while it waited for its turn at the gate: stay out.
          await connect(name, role, slot, () => !bot.managedStop && slots.get(name) === bot)
        } catch (err) {
          log(name, `reconnect failed: ${err.message}`)
          if (slots.get(name) === bot) slots.delete(name)
        }
      }, delay)
    })

    slots.set(name, bot)
    return bot
  }

  function live(role) {
    return [...slots.values()].filter((bot) => bot.role === role && !bot.managedStop)
  }

  function maxFor(role) {
    const caps = config.caps || {}
    const cap = Number(caps[role] ?? config.maxTotal ?? 40)
    const total = Number(config.maxTotal ?? 40)
    const others = [...slots.values()].filter((bot) => bot.role !== role && !bot.managedStop).length
    return Math.max(0, Math.min(cap, total - others))
  }

  async function reconcile(role) {
    const want = Math.max(0, Math.min(desired[role] || 0, maxFor(role)))
    desired[role] = want
    const current = live(role).sort((a, b) => a.qaSlot - b.qaSlot)

    while (current.length > want) {
      const bot = current.pop()
      bot.managedStop = true
      log('fleet', `stop ${bot.stressName}`)
      try { bot.quit('testbot stop') } catch { /* ignore */ }
      slots.delete(bot.stressName)
    }

    const taken = new Set(current.map((bot) => bot.qaSlot))
    let next = 1
    while (live(role).length < want && !shuttingDown) {
      while (taken.has(next) || slots.has(nameFor(role, next))) next++
      const name = nameFor(role, next)
      taken.add(next)
      log('fleet', `spawn ${name} (${role} #${next})`)
      await connect(name, role, next)
      next++
    }
    return {
      ok: true,
      role,
      desired: want,
      online: live(role).length,
      message: `${role} desired=${want} tracked=${live(role).length}`
    }
  }

  function enqueue(work) {
    const run = spawnQueue.then(work, work)
    spawnQueue = run.catch((err) => log('fleet', `queue: ${err.message}`))
    return run
  }

  return {
    WAVE1,
    WAVE2,
    WAVE3,
    ALL_ROLES,
    target,
    setDesired(role, count) {
      if (!ALL_ROLES.includes(role)) {
        return Promise.resolve({ ok: false, message: `unknown role ${role}` })
      }
      desired[role] = Math.max(0, Number(count) || 0)
      log('fleet', `desired ${role}=${desired[role]}`)
      enqueue(() => reconcile(role))
      return Promise.resolve({
        ok: true,
        role,
        desired: desired[role],
        message: `${role} queued desired=${desired[role]}`
      })
    },
    stop(role) {
      if (!ALL_ROLES.includes(role)) {
        return Promise.resolve({ ok: false, message: `unknown role ${role}` })
      }
      desired[role] = 0
      enqueue(() => reconcile(role))
      return Promise.resolve({ ok: true, role, desired: 0, message: `${role} queued stop` })
    },
    stopAll() {
      for (const role of ALL_ROLES) desired[role] = 0
      enqueue(async () => {
        for (const bot of [...slots.values()]) {
          bot.managedStop = true
          try { bot.quit('testbot stop-all') } catch { /* ignore */ }
        }
        slots.clear()
        log('fleet', 'stop-all')
      })
      return Promise.resolve({ ok: true, message: 'stop-all queued' })
    },
    shutdown() {
      shuttingDown = true
      for (const bot of slots.values()) {
        bot.managedStop = true
        try { bot.quit('stress stop') } catch { /* ignore */ }
      }
    },
    snapshot() {
      const bots = [...slots.values()].map((bot) => ({
        name: bot.stressName,
        role: bot.role,
        slot: bot.qaSlot,
        server: bot.qaServer || '',
        world: bot.qaWorld || '',
        step: bot.qaStep || bot.qaActivity || '',
        online: Boolean(bot.entity) && !bot.qaEnded,
        uptimeS: bot.qaSpawnedAt ? Math.round((Date.now() - bot.qaSpawnedAt) / 1000) : 0,
        reconnects: meta.get(bot.stressName)?.reconnects || 0,
        kicks: meta.get(bot.stressName)?.kicks || 0,
        lastKick: meta.get(bot.stressName)?.lastKick || '',
        stats: { ...(bot.qaStats || {}) },
        activity: bot.qaActivity || 'idle',
        heldItem: heldName(bot),
        deaths: bot.qaDeaths || 0,
        lastAction: bot.qaLastAction || '',
        lastError: bot.qaLastError || '',
        recentActions: bot.qaRecent || [],
        position: bot.entity?.position
          ? { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
          : null
      }))
      const roles = {}
      for (const role of ALL_ROLES) {
        const members = bots.filter((b) => b.role === role)
        const totals = {}
        for (const bot of members) {
          for (const [key, value] of Object.entries(bot.stats)) {
            if (Number.isFinite(value)) totals[key] = (totals[key] || 0) + value
          }
        }
        const gaveUp = [...meta.entries()].filter(([, m]) => m.role === role && m.gaveUp).map(([n]) => n)
        roles[role] = {
          desired: desired[role] || 0,
          online: members.filter((b) => b.online).length,
          tracked: members.length,
          activity: members.map((b) => b.activity).join(','),
          servers: [...new Set(members.map((b) => b.server).filter(Boolean))].join(','),
          totals,
          gaveUp
        }
      }
      return { mode: target.mode, bots, roles, desired: { ...desired } }
    },
    report() {
      return telemetry.lines(this.snapshot().bots)
    },
    size() {
      return slots.size
    }
  }
}

function kickText(reason, registry) {
  const text = toPlain(reason, registry).trim()
  return text || stringify(reason)
}

function stringify(value) {
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}
