import mineflayer from 'mineflayer'
import pathfinderPkg from 'mineflayer-pathfinder'
import { createCombatLoop } from './combat.js'
import { createMiningLoop, createForageLoop } from './gather.js'
import { createCatchLoop } from './catch.js'
import { createRoamLoop } from './roam.js'
import { createFishLoop } from './fish.js'
import { createTradeLoop } from './trade.js'
import { createQuestLoop } from './quest.js'
import { createPadLoop } from './pad.js'
import { attachVelocityForwarding } from './velocity.js'
import { attachSafety, readAnchors } from './safety.js'
import { attachPlaystyle } from './playstyle.js'
import { attachLocale } from './locale.js'
import { attachWindowWatchdog } from './windows.js'
import { fmtPos, heldName, note, sleep } from './util.js'
import { toPlain } from './chat.js'

const { goals } = pathfinderPkg

const WAVE1 = ['mine', 'forage', 'catch', 'roam']
const WAVE2 = ['combat', 'fish', 'trade', 'quest', 'pad']
const ALL_ROLES = [...WAVE1, ...WAVE2, 'mining']

/** Reconnect delay after {@code fails} short-lived sessions in a row: 8 s, 16 s, 32 s … capped. */
export function reconnectDelayMs(fails, { baseMs = 8000, maxMs = 120_000 } = {}) {
  const n = Math.max(0, Math.min(10, Number(fails) || 0))
  return Math.min(maxMs, baseMs * 2 ** n)
}

export function createFleet({ config, log }) {
  const slots = new Map()
  // Per login name, kept across reconnects: counters, kicks, the reconnect streak.
  const meta = new Map()
  const maxFails = Math.max(1, Number(config.reconnect?.maxFails ?? 8))
  const stableMs = Number(config.reconnect?.stableMs ?? 60_000)
  const desired = Object.fromEntries(ALL_ROLES.map((role) => [role, 0]))
  let shuttingDown = false
  let spawnQueue = Promise.resolve()

  function prefix(role) {
    const prefixes = config.prefixes || {}
    return prefixes[role] || defaultPrefix(role)
  }

  function defaultPrefix(role) {
    return {
      mine: 'QaMine',
      forage: 'QaForage',
      catch: 'QaCatch',
      roam: 'QaRoam',
      combat: 'QaCombat',
      fish: 'QaFish',
      trade: 'QaTrade',
      quest: 'QaQuest',
      pad: 'QaPad',
      mining: 'StressM'
    }[role] || 'QaBot'
  }

  function pad(n) {
    return String(n).padStart(2, '0')
  }

  function botName(role, index) {
    return `${prefix(role)}${pad(index)}`
  }

  function roleConfig(role) {
    if (role === 'mining') return config.mining || {}
    if (role === 'mine') return config.mine || config.mining || {}
    return config[role] || {}
  }

  function startLoop(bot, role) {
    const cfg = roleConfig(role)
    const safety = config.safety || {}
    attachSafety(bot, {
      voidFloorY: cfg.voidFloorY ?? safety.voidFloorY ?? 40,
      leashRadius: cfg.leashRadius ?? safety.leashRadius ?? defaultLeash(role),
      anchors: readAnchors(cfg),
      stuckMs: cfg.stuckMs ?? safety.stuckMs ?? 10_000,
      digStuckMs: cfg.digStuckMs ?? safety.digStuckMs ?? 28_000,
      gatherStuckMs: cfg.gatherStuckMs ?? safety.gatherStuckMs ?? 18_000,
      idleGoalMs: cfg.idleGoalMs ?? safety.idleGoalMs ?? 8000,
      resumeDelayMs: safety.resumeDelayMs ?? 2800,
      goals,
      log
    })
    attachLocale(bot, log)
    attachWindowWatchdog(bot, log)
    if (role === 'combat') return createCombatLoop(bot, cfg, log)
    if (role === 'forage') return createForageLoop(bot, cfg, log)
    if (role === 'catch') return createCatchLoop(bot, cfg, log)
    if (role === 'roam') return createRoamLoop(bot, cfg, log)
    if (role === 'fish') return createFishLoop(bot, cfg, log)
    if (role === 'trade') return createTradeLoop(bot, cfg, log)
    if (role === 'quest') return createQuestLoop(bot, cfg, log)
    if (role === 'pad') return createPadLoop(bot, cfg, log)
    return createMiningLoop(bot, cfg, log)
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
      meta.set(name, { role, fails: 0, reconnects: 0, kicks: 0, lastKick: '', stats: {}, gaveUp: false })
    }
    return meta.get(name)
  }

  function spawnBot(name, role) {
    const info = metaFor(name, role)
    info.gaveUp = false
    const bot = mineflayer.createBot({
      host: config.host,
      port: config.port,
      username: name,
      version: config.version,
      auth: 'offline',
      hideErrors: true,
      checkTimeoutInterval: 60_000
    })

    bot.role = role
    bot.stressName = name
    bot.qaActivity = 'idle'
    bot.qaRecent = []
    bot.qaDeaths = 0
    bot.qaLastError = ''
    bot.managedStop = false
    bot.qaStats = info.stats
    bot.qaSpawnedAt = 0

    if (config.velocitySecret) {
      attachVelocityForwarding(bot, config.velocitySecret, name)
    }

    bot.once('login', () => log(name, `login → ${config.host}:${config.port} (${role})`))
    bot.on('spawn', () => {
      if (!bot.qaSpawnedAt) bot.qaSpawnedAt = Date.now()
      note(bot, `spawn @ ${fmtPos(bot.entity?.position)}`, 'idle')
      log(name, `spawn @ ${fmtPos(bot.entity?.position)}`)
    })
    bot.on('death', () => {
      bot.qaDeaths = (bot.qaDeaths || 0) + 1
      bot.qaSuspended = true
      note(bot, 'died', 'recovering')
      bot.qaLastError = 'died'
      log(name, 'died — holding for plugin re-anchor')
    })
    bot.on('kicked', (reason) => {
      const text = kickText(reason, bot.registry)
      info.kicks++
      info.lastKick = text
      note(bot, `kicked: ${text}`, 'error')
      bot.qaLastError = text
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

    const loop = startLoop(bot, role)
    attachPlaystyle(bot, log)
    let started = false
    bot.on('spawn', () => {
      if (started) return
      started = true
      setTimeout(() => loop(), 4500)
    })

    bot.on('end', (reason) => {
      if (bot.managedStop || shuttingDown) {
        log(name, `disconnected: ${reason || 'end'}`)
        if (slots.get(name) === bot) slots.delete(name)
        return
      }
      const lived = bot.qaSpawnedAt ? Date.now() - bot.qaSpawnedAt : 0
      info.fails = lived >= stableMs ? 0 : info.fails + 1
      if (info.fails >= maxFails) {
        info.gaveUp = true
        if (slots.get(name) === bot) slots.delete(name)
        log(name, `disconnected: ${reason || 'end'} — gave up after ${info.fails} short sessions`
          + ` (last kick: ${info.lastKick || 'none'}). Start the role again to retry.`)
        return
      }
      const delay = reconnectDelayMs(info.fails - 1) + Math.round(Math.random() * 2000)
      log(name, `disconnected: ${reason || 'end'} — retry in ${Math.round(delay / 1000)}s`)
      setTimeout(() => {
        if (shuttingDown || bot.managedStop || slots.get(name) !== bot) return
        info.reconnects++
        try {
          spawnBot(name, role)
        } catch (err) {
          log(name, `reconnect failed: ${err.message}`)
          slots.delete(name)
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
    return Math.max(0, cap)
  }

  async function reconcile(role) {
    const want = Math.max(0, Math.min(desired[role] || 0, maxFor(role)))
    desired[role] = want
    let current = live(role)

    while (current.length > want) {
      const bot = current.pop()
      bot.managedStop = true
      log('fleet', `stop ${bot.stressName}`)
      try { bot.quit('testbot stop') } catch { /* ignore */ }
      slots.delete(bot.stressName)
    }

    const existingIndexes = new Set(
      current.map((bot) => Number(String(bot.stressName).replace(/\D/g, ''))).filter((n) => n > 0)
    )
    let next = 1
    const joinDelay = Math.max(250, Number(config.joinDelayMs || 2500))
    while (live(role).length < want) {
      while (existingIndexes.has(next) || slots.has(botName(role, next))) next++
      const name = botName(role, next)
      existingIndexes.add(next)
      log('fleet', `spawn ${name} (${role})`)
      spawnBot(name, role)
      next++
      await sleep(joinDelay)
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
    ALL_ROLES,
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
        online: Boolean(bot.entity),
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
          for (const [key, value] of Object.entries(bot.stats)) totals[key] = (totals[key] || 0) + value
        }
        const gaveUp = [...meta.entries()].filter(([, m]) => m.role === role && m.gaveUp).map(([n]) => n)
        roles[role] = {
          desired: desired[role] || 0,
          online: members.filter((b) => b.online).length,
          tracked: members.length,
          activity: members.map((b) => b.activity).join(','),
          totals,
          gaveUp
        }
      }
      return { bots, roles, desired: { ...desired } }
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
