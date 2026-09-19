#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import net from 'node:net'
import { createFleet } from './fleet.js'
import { startControlServer } from './control.js'
import { sleep } from './util.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const configPath = path.join(__dirname, '..', 'config.json')
const examplePath = path.join(__dirname, '..', 'config.example.json')

if (!fs.existsSync(configPath)) {
  console.error(`Missing ${configPath}. Copy config.example.json and set velocitySecret locally.`)
  process.exit(1)
}

const baseConfig = JSON.parse(fs.readFileSync(configPath, 'utf8'))

function parseArgs(argv) {
  const out = {
    combat: 0,
    mining: 0,
    mine: 0,
    forage: 0,
    catch: 0,
    roam: 0,
    listen: false,
    host: baseConfig.host,
    port: baseConfig.port,
    version: baseConfig.version,
    joinDelayMs: baseConfig.joinDelayMs
  }
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    const next = argv[i + 1]
    const takeNum = (key) => {
      out[key] = Math.max(0, Number(next))
      i++
    }
    if (arg === '--combat' && next != null) takeNum('combat')
    else if (arg === '--mining' && next != null) takeNum('mining')
    else if (arg === '--mine' && next != null) takeNum('mine')
    else if (arg === '--forage' && next != null) takeNum('forage')
    else if (arg === '--catch' && next != null) takeNum('catch')
    else if (arg === '--roam' && next != null) takeNum('roam')
    else if (arg === '--host' && next != null) { out.host = next; i++ }
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
  const args = parseArgs(process.argv.slice(2))
  if (args.help) {
    console.log(`Usage:
  node src/index.js [--listen] [--mine N] [--forage N] [--catch N] [--roam N] [--combat N] [--mining N]

Wave 1 QA: --mine / --forage / --catch / --roam  (or Dev menu via --listen)
Phase 1 stress: --combat / --mining (StressC / StressM)

--listen   start with 0 bots and keep the HTTP control server up (Dev menu)
Defaults connect offline to MMO-R (${baseConfig.host}:${baseConfig.port}).
Copy ${path.basename(examplePath)} → config.json and set velocitySecret locally. Do not commit it.
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

  if (!config.velocitySecret || config.velocitySecret === 'CHANGE_ME') {
    log('main', 'WARNING: velocitySecret missing or CHANGE_ME — Paper Velocity forwarding will fail. Set it in runner/config.json (gitignored).')
  }

  const fleet = createFleet({ config, log })
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

  const planned = {
    mine: args.mine,
    forage: args.forage,
    catch: args.catch,
    roam: args.roam,
    combat: args.combat,
    mining: args.mining
  }
  const total = Object.values(planned).reduce((a, b) => a + b, 0)
  if (total <= 0 && !args.listen) {
    log('main', 'nothing to spawn — keeping control server (--listen implied). Use Dev menu or CLI flags.')
  }

  await waitForPort(args.host, args.port, 120_000)

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
        log('main', `backend ${host}:${port} is up`)
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
