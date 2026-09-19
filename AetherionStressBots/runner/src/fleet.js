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
import { fmtPos, heldName, note, sleep } from './util.js'

const { goals } = pathfinderPkg

const WAVE1 = ['mine', 'forage', 'catch', 'roam']
const WAVE2 = ['combat', 'fish', 'trade', 'quest', 'pad']
const ALL_ROLES = [...WAVE1, ...WAVE2, 'mining']

export function createFleet({ config, log }) {
  const slots = new Map()
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
      resumeDelayMs: safety.resumeDelayMs ?? 2800,
      goals,
      log
    })
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
    if (role === 'catch' || role === 'pad') return 12
    if (role === 'roam' || role === 'fish' || role === 'trade' || role === 'quest') return 14
    if (role === 'combat') return 22
    if (role === 'forage') return 20
    return 16
  }

  function spawnBot(name, role) {
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

    if (config.velocitySecret) {
      attachVelocityForwarding(bot, config.velocitySecret, name)
    }

    bot.once('login', () => log(name, `login → ${config.host}:${config.port} (${role})`))
    bot.on('spawn', () => {
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
      const text = stringify(reason)
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
      log(name, `disconnected: ${reason || 'end'}${bot.managedStop || shuttingDown ? '' : ' — retry in 8s'}`)
      if (bot.managedStop || shuttingDown) {
        slots.delete(name)
        return
      }
      setTimeout(() => {
        if (shuttingDown || bot.managedStop) return
        try {
          const replacement = spawnBot(name, role)
          slots.set(name, replacement)
        } catch (err) {
          log(name, `reconnect failed: ${err.message}`)
        }
      }, 8000)
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
        roles[role] = {
          desired: desired[role] || 0,
          online: members.length,
          activity: members.map((b) => b.activity).join(',')
        }
      }
      return { bots, roles, desired: { ...desired } }
    },
    size() {
      return slots.size
    }
  }
}

function stringify(value) {
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}
