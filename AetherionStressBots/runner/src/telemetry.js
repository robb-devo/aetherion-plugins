/**
 * What the fleet learned about the network while it played: how long each road between servers took and
 * whether the player road worked, how long each menu took to open, what happened in dungeons, islands, the Pit
 * and the casino, and the problems worth a look. `/report.txt` turns it into the lines /botreport prints.
 */

const MAX_PROBLEMS = 12

export function createTelemetry(now = () => Date.now()) {
  const routes = new Map()
  const menus = new Map()
  const events = new Map()
  const problems = []
  const startedAt = now()

  function routeEntry(key) {
    if (!routes.has(key)) routes.set(key, { ok: 0, fallback: 0, failed: 0, totalMs: 0, maxMs: 0, last: '' })
    return routes.get(key)
  }

  return {
    /** One hop between two servers. {@code how}: 'road' (the player command worked), 'fallback' or 'failed'. */
    hop(from, to, how, ms, detail = '') {
      const entry = routeEntry(`${from || '?'}>${to || '?'}`)
      if (how === 'road') entry.ok++
      else if (how === 'fallback') entry.fallback++
      else entry.failed++
      if (how !== 'failed' && Number.isFinite(ms)) {
        entry.totalMs += ms
        entry.maxMs = Math.max(entry.maxMs, ms)
      }
      if (detail) entry.last = detail
    },

    /** A menu command answered with a window after {@code ms} (null = no window at all). */
    menu(command, ms) {
      if (!menus.has(command)) menus.set(command, { opened: 0, missed: 0, totalMs: 0, maxMs: 0 })
      const entry = menus.get(command)
      if (ms == null) {
        entry.missed++
        return
      }
      entry.opened++
      entry.totalMs += ms
      entry.maxMs = Math.max(entry.maxMs, ms)
    },

    count(name, by = 1) {
      events.set(name, (events.get(name) || 0) + by)
    },

    problem(who, text) {
      const line = `${who}: ${String(text || '').slice(0, 140)}`
      const at = problems.findIndex((p) => p.line === line)
      if (at >= 0) {
        problems[at].times++
        problems[at].at = now()
        return
      }
      problems.unshift({ line, times: 1, at: now() })
      if (problems.length > MAX_PROBLEMS) problems.pop()
    },

    snapshot() {
      return {
        since: startedAt,
        routes: Object.fromEntries(routes),
        menus: Object.fromEntries(menus),
        events: Object.fromEntries(events),
        problems: problems.map((p) => ({ ...p }))
      }
    },

    /** Report lines for /botreport. {@code bots}: fleet snapshot bots (name, role, server, activity, online). */
    lines(bots = []) {
      const out = []
      const minutes = Math.max(1, Math.round((now() - startedAt) / 60000))
      const perServer = {}
      for (const bot of bots) {
        if (!bot.online) continue
        const server = bot.server || '?'
        perServer[server] = (perServer[server] || 0) + 1
      }
      const servers = Object.entries(perServer).sort((a, b) => b[1] - a[1]).map(([s, n]) => `${s} ${n}`)
      out.push(`Bots online: ${servers.length ? servers.join(' · ') : 'none'} (runner up ${minutes} min)`)

      const roads = [...routes.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([key, r]) => {
        const done = r.ok + r.fallback
        const avg = done ? (r.totalMs / done / 1000).toFixed(1) : '-'
        const parts = [`${r.ok} ok`]
        if (r.fallback) parts.push(`${r.fallback} fallback`)
        if (r.failed) parts.push(`${r.failed} failed`)
        return `${key.replace('>', '→')} ${parts.join(' ')} avg ${avg}s max ${(r.maxMs / 1000).toFixed(1)}s`
          + (r.last && (r.fallback || r.failed) ? ` (${r.last})` : '')
      })
      if (roads.length) out.push(`Roads: ${roads.join(' | ')}`)

      const menuParts = [...menus.entries()].sort((a, b) => (b[1].maxMs) - (a[1].maxMs)).map(([cmd, m]) => {
        const avg = m.opened ? Math.round(m.totalMs / m.opened) : 0
        return `${cmd} ${m.opened ? `${avg}ms (max ${m.maxMs})` : 'never opened'}${m.missed ? ` ${m.missed} missed` : ''}`
      })
      if (menuParts.length) out.push(`Menus: ${menuParts.join(' · ')}`)

      const eventParts = [...events.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([k, v]) => `${k} ${v}`)
      if (eventParts.length) out.push(`Done: ${eventParts.join(' · ')}`)

      const busy = {}
      for (const bot of bots) {
        if (!bot.online) continue
        const key = `${bot.role}@${bot.server || '?'}`
        busy[key] = busy[key] || []
        if (bot.step) busy[key].push(bot.step)
      }
      const roleParts = Object.entries(busy).map(([key, steps]) => {
        const top = mostCommon(steps)
        return top ? `${key} (${top})` : key
      })
      if (roleParts.length) out.push(`Who is where: ${roleParts.join(' · ')}`)

      for (const p of problems.slice(0, 8)) {
        out.push(`Problem: ${p.line}${p.times > 1 ? ` ×${p.times}` : ''}`)
      }
      return out
    }
  }
}

function mostCommon(values) {
  const counts = new Map()
  for (const v of values) counts.set(v, (counts.get(v) || 0) + 1)
  let best = null
  let bestN = 0
  for (const [v, n] of counts) {
    if (n > bestN) {
      best = v
      bestN = n
    }
  }
  return best
}

/** The fleet's one telemetry book. */
export const telemetry = createTelemetry()
