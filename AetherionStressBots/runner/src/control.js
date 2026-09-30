import http from 'node:http'
import { randomBytes } from 'node:crypto'
import { dashboardHtml } from './dashboard.js'

/**
 * Localhost-only control plane for the Dev menu / plugin.
 * Does not expose the Velocity secret.
 */
export function createSessionStore(ttlMs = 12 * 60 * 60 * 1000) {
  const sessions = new Map()
  return {
    mint(now = Date.now(), hours) {
      const life = Number.isFinite(hours) && hours > 0 ? hours * 60 * 60 * 1000 : ttlMs
      const session = randomBytes(18).toString('base64url')
      const expiresAt = now + life
      sessions.set(session, expiresAt)
      return { token: session, expiresAt }
    },
    valid(session, now = Date.now()) {
      if (!session) return false
      const expiresAt = sessions.get(session)
      if (!expiresAt) return false
      if (now > expiresAt) {
        sessions.delete(session)
        return false
      }
      return true
    }
  }
}

export function startControlServer({ host, port, token, fleet, log, sessionTtlMs } = {}) {
  const sessions = createSessionStore(sessionTtlMs)
  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url || '/', `http://${host}:${port}`)
      const presented = req.headers['x-testbots-token'] || url.searchParams.get('token') || ''
      const authed = !token || presented === token || sessions.valid(presented)
      if (req.method === 'GET' && (url.pathname === '/' || url.pathname === '/dashboard')) {
        sendHtml(res, 200, dashboardHtml())
        return
      }
      if (!authed) {
        send(res, 401, { ok: false, message: 'unauthorized' })
        return
      }
      if (req.method === 'GET' && url.pathname === '/health') {
        send(res, 200, { ok: true, message: `fleet ${fleet.size()} bots` })
        return
      }
      if (req.method === 'GET' && url.pathname === '/status') {
        send(res, 200, { ok: true, ...fleet.snapshot() })
        return
      }
      const body = await readBody(req)
      if (req.method === 'POST' && url.pathname === '/session') {
        const hours = Number(body.hours)
        const minted = sessions.mint(Date.now(), Number.isFinite(hours) ? hours : undefined)
        send(res, 200, { ok: true, token: minted.token, expiresAt: minted.expiresAt, message: 'dashboard session' })
        return
      }
      if (req.method === 'POST' && url.pathname === '/desired') {
        const role = String(body.role || '').toLowerCase()
        const count = Number(body.count)
        const result = await fleet.setDesired(role, count)
        send(res, result.ok ? 200 : 400, result)
        return
      }
      if (req.method === 'POST' && url.pathname === '/stop') {
        const role = String(body.role || '').toLowerCase()
        const result = await fleet.stop(role)
        send(res, result.ok ? 200 : 400, result)
        return
      }
      if (req.method === 'POST' && (url.pathname === '/stop-all' || url.pathname === '/stopall')) {
        const result = await fleet.stopAll()
        send(res, 200, result)
        return
      }
      send(res, 404, { ok: false, message: 'not found' })
    } catch (err) {
      log('control', `error: ${err.message}`)
      send(res, 500, { ok: false, message: err.message })
    }
  })

  server.listen(port, host, () => {
    log('control', `listening on http://${host}:${port}  dashboard http://${host}:${port}/`)
  })
  server.on('error', (err) => {
    log('control', `bind failed: ${err.message}`)
  })
  return server
}

function sendHtml(res, status, html) {
  res.writeHead(status, {
    'Content-Type': 'text/html; charset=utf-8',
    'Content-Length': Buffer.byteLength(html),
    'Cache-Control': 'no-store'
  })
  res.end(html)
}

function send(res, status, payload) {
  const json = JSON.stringify(payload)
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(json)
  })
  res.end(json)
}

function readBody(req) {
  return new Promise((resolve) => {
    const chunks = []
    req.on('data', (chunk) => chunks.push(chunk))
    req.on('end', () => {
      if (chunks.length === 0) {
        resolve({})
        return
      }
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}'))
      } catch {
        resolve({})
      }
    })
  })
}
