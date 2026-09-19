#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import mineflayer from 'mineflayer'
import { createCombatLoop } from './combat.js'
import { createMiningLoop } from './mining.js'
import { attachVelocityForwarding } from './velocity.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const configPath = path.join(__dirname, '..', 'config.json')
const baseConfig = JSON.parse(fs.readFileSync(configPath, 'utf8'))

function parseArgs(argv) {
  const out = {
    combat: 5,
    mining: 5,
    host: baseConfig.host,
    port: baseConfig.port,
    version: baseConfig.version,
    joinDelayMs: baseConfig.joinDelayMs
  }
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    const next = argv[i + 1]
    if (arg === '--combat' && next != null) {
      out.combat = Math.max(0, Number(next))
      i++
    } else if (arg === '--mining' && next != null) {
      out.mining = Math.max(0, Number(next))
      i++
    } else if (arg === '--host' && next != null) {
      out.host = next
      i++
    } else if (arg === '--port' && next != null) {
      out.port = Number(next)
      i++
    } else if (arg === '--version' && next != null) {
      out.version = next
      i++
    } else if (arg === '--help' || arg === '-h') {
      out.help = true
    }
  }
  return out
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function log(tag, msg) {
  const ts = new Date().toISOString().slice(11, 19)
  console.log(`[${ts}] [${tag}] ${msg}`)
}

function spawnBot({ name, role, host, port, version }) {
  const bot = mineflayer.createBot({
    host,
    port,
    username: name,
    version,
    auth: 'offline',
    hideErrors: true,
    checkTimeoutInterval: 60_000
  })

  bot.role = role
  bot.stressName = name

  if (baseConfig.velocitySecret) {
    attachVelocityForwarding(bot, baseConfig.velocitySecret, name)
  }

  bot.once('login', () => log(name, `login → ${host}:${port}`))
  bot.on('spawn', () => log(name, `spawn @ ${fmtPos(bot.entity?.position)}`))
  bot.on('death', () => log(name, 'died — waiting for respawn/setup'))
  bot.on('kicked', (reason) => log(name, `kicked: ${stringify(reason)}`))
  bot.on('error', (err) => {
    if (err && (err.name === 'PartialReadError' || /PartialReadError|SlotComponent/.test(String(err)))) {
      return
    }
    log(name, `error: ${err.message}`)
  })

  const startLoop = role === 'combat'
    ? createCombatLoop(bot, baseConfig.combat, log)
    : createMiningLoop(bot, baseConfig.mining, log)

  let started = false
  bot.on('spawn', () => {
    if (started) return
    started = true
    setTimeout(() => startLoop(), 4500)
  })

  bot.on('end', (reason) => {
    log(name, `disconnected: ${reason || 'end'}${shuttingDown ? '' : ' — retry in 8s'}`)
    if (shuttingDown) return
    setTimeout(() => {
      if (shuttingDown) return
      try {
        const replacement = spawnBot({ name, role, host, port, version })
        const idx = activeBots.findIndex((b) => b.stressName === name)
        if (idx >= 0) activeBots[idx] = replacement
        else activeBots.push(replacement)
      } catch (err) {
        log(name, `reconnect failed: ${err.message}`)
      }
    }, 8000)
  })

  return bot
}

const activeBots = []


function fmtPos(pos) {
  if (!pos) return '?'
  return `${pos.x.toFixed(1)} ${pos.y.toFixed(1)} ${pos.z.toFixed(1)}`
}

function stringify(value) {
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  if (args.help) {
    console.log(`Usage:
  node src/index.js [--combat N] [--mining N] [--host 127.0.0.1] [--port 25567]

Defaults: 5 combat (Borderlands) + 5 mining (Shabby Mine)
Connects offline to MMO-R backend (not Velocity).
Names: ${baseConfig.prefixes.combat}01.. / ${baseConfig.prefixes.mining}01..
Stop with Ctrl+C. Scale up anytime by raising --combat / --mining.`)
    process.exit(0)
  }

  const total = args.combat + args.mining
  if (total <= 0) {
    console.error('Nothing to start. Use --combat and/or --mining > 0.')
    process.exit(1)
  }

  log('main', `starting ${args.combat} combat + ${args.mining} mining → ${args.host}:${args.port} (${args.version})`)
  await waitForPort(args.host, args.port, 120_000)
  const bots = activeBots

  for (let i = 1; i <= args.combat; i++) {
    const name = `${baseConfig.prefixes.combat}${pad(i)}`
    bots.push(spawnBot({
      name,
      role: 'combat',
      host: args.host,
      port: args.port,
      version: args.version
    }))
    await sleep(args.joinDelayMs)
  }

  for (let i = 1; i <= args.mining; i++) {
    const name = `${baseConfig.prefixes.mining}${pad(i)}`
    bots.push(spawnBot({
      name,
      role: 'mining',
      host: args.host,
      port: args.port,
      version: args.version
    }))
    await sleep(args.joinDelayMs)
  }

  const shutdown = () => {
    shuttingDown = true
    log('main', `stopping ${bots.length} bots…`)
    for (const bot of bots) {
      try { bot.quit('stress stop') } catch { /* ignore */ }
    }
    setTimeout(() => process.exit(0), 1500)
  }
  process.on('SIGINT', shutdown)
  process.on('SIGTERM', shutdown)
}

let shuttingDown = false

function waitForPort(host, port, timeoutMs) {
  return new Promise(async (resolve, reject) => {
    const net = await import('node:net')
    const start = Date.now()
    const tryOnce = () => new Promise((res) => {
      const socket = net.createConnection({ host, port }, () => {
        socket.end()
        res(true)
      })
      socket.on('error', () => res(false))
      socket.setTimeout(1500, () => {
        socket.destroy()
        res(false)
      })
    })
    while (Date.now() - start < timeoutMs) {
      if (await tryOnce()) {
        log('main', `backend ${host}:${port} is up`)
        resolve()
        return
      }
      await sleep(2000)
    }
    reject(new Error(`timeout waiting for ${host}:${port}`))
  })
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
