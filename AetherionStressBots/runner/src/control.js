import http from 'node:http'
import { dashboardHtml } from './dashboard.js'

/**
 * Localhost-only control plane for the Dev menu / plugin.
 * Does not expose the Velocity secret.
 */
export function startControlServer({ host, port, token, fleet, log }) {
  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url || '/', `http://${host}:${port}`)
      const authed = !token || req.headers['x-testbots-token'] === token || url.searchParams.get('token') === token
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
