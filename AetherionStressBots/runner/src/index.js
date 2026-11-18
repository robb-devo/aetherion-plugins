#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import net from 'node:net'
import { ALL_ROLES, createFleet, WAVE1, WAVE2, WAVE3 } from './fleet.js'
import { startControlServer } from './control.js'
import { sleep } from './util.js'
import { mergeConfig } from './config.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const configPath = path.join(__dirname, '..', 'config.json')
const examplePath = path.join(__dirname, '..', 'config.example.json')

if (!fs.existsSync(configPath)) {
  console.error(`Missing ${configPath}. Copy config.example.json and set velocitySecret locally.`)
  process.exit(1)
}

const exampleConfig = fs.existsSync(examplePath) ? JSON.parse(fs.readFileSync(examplePath, 'utf8')) : {}
const baseConfig = mergeConfig(exampleConfig, JSON.parse(fs.readFileSync(configPath, 'utf8')))

/** `--<role> N` for every role (plus the old `--ah` for trade), `--host/--port/--version`, `--listen`. */
function parseArgs(argv, defaults = {}) {
  const out = {
    counts: Object.fromEntries(ALL_ROLES.map((role) => [role, 0])),
    listen: false,
    host: defaults.host,
    port: defaults.port,
    version: defaults.version,
    joinDelayMs: defaults.joinDelayMs
  }
  const aliases = { ah: 'trade', tour: 'general', player: 'general', dungeons: 'dungeon', islands: 'island' }
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    const next = argv[i + 1]
    const key = arg.startsWith('--') ? arg.slice(2) : ''
    const role = aliases[key] || key
    if (ALL_ROLES.includes(role) && next != null) {
      out.counts[role] = Math.max(0, Number(next) || 0)
      i++
    } else if (arg === '--host' && next != null) { out.host = next; i++ }
    else if (arg === '--port' && next != null) { out.port = Number(next); i++ }
    else if (arg === '--version' && next != null) { out.version = next; i++ }
    else if (arg === '--listen') out.listen = true
    else if (arg === '--help' || arg === '-h') out.help = true
  }
  return out
}

function log(tag, msg) {
  const ts = new Date().toISOString().slice(11, 19)
  console.log(`[${ts}] [${tag}] ${msg}`)
}

async function main() {
  const args = parseArgs(process.argv.slice(2), baseConfig)
  if (args.help) {
    console.log(`Usage:
  node src/index.js [--listen] [--<role> N ...] [--host H] [--port P]

Roles (any number of --<role> N):
  Main world jobs:  ${WAVE1.join(' ')} ${WAVE2.join(' ')}
  Network players:  ${WAVE3.join(' ')}
  Legacy:           mining (StressM01 …)

  general   Testbot_ players who tour everything: isles, menus, dungeons, exile, islands, the Pit
  pit       hub Pit PvP (bots only fight bots)       dungeon  parties on mmo-d (yield to people)
  exile     the story island on mmo-c                island   private islands on mmo-i
  farm / menus / casino / social   the main world's fields, menus, slots and parties

--listen   start with 0 bots and keep the HTTP control server up (Dev menu, /testbots start …)
Login: connect.mode "proxy" (through Velocity, needs connect.proxy.token) or "direct" (${baseConfig.host}:${baseConfig.port}).
Copy ${path.basename(examplePath)} → config.json and put the secrets there. Do not commit it.
Stop with Ctrl+C.`)
    process.exit(0)
  }

  const config = {
    ...baseConfig,
    host: args.host,
    port: args.port,
    version: args.version,
    joinDelayMs: args.joinDelayMs
  }

  const fleet = createFleet({ config, log })
  if (fleet.target.mode === 'direct' && (!config.velocitySecret || config.velocitySecret === 'CHANGE_ME')) {
    log('main', 'WARNING: velocitySecret missing or CHANGE_ME: direct logins need Velocity forwarding. Set it in runner/config.json (gitignored).')
  }
  const control = config.control || {}
  const bindHost = control.bind || '127.0.0.1'
  const bindPort = Number(control.port || 18765)
  startControlServer({
    host: bindHost,
    port: bindPort,
    token: control.token || '',
    fleet,
    log
  })

  const planned = args.counts
  const total = Object.values(planned).reduce((a, b) => a + b, 0)
  if (total <= 0 && !args.listen) {
    log('main', 'nothing to spawn: keeping the control server up (--listen implied). Use the Dev menu or CLI flags.')
  }

  await waitForPort(fleet.target.host, fleet.target.port, 120_000)

  for (const [role, count] of Object.entries(planned)) {
    if (count > 0) {
      await fleet.setDesired(role, count)
    }
  }

  const shutdown = () => {
    log('main', `stopping ${fleet.size()} bots…`)
    fleet.shutdown()
    setTimeout(() => process.exit(0), 1500)
  }
  process.on('SIGINT', shutdown)
  process.on('SIGTERM', shutdown)
}

function waitForPort(host, port, timeoutMs) {
  return new Promise(async (resolve, reject) => {
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
        log('main', `${host}:${port} is up`)
        resolve()
        return
      }
      await sleep(2000)
    }
    reject(new Error(`timeout waiting for ${host}:${port}`))
  })
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
