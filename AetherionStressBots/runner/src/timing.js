import { toPlain } from './chat.js'

/**
 * The three timing bars bots play, read from the boss bars the server sends:
 *
 *   fish   AetherionFishing StrikeBar   "Fishing  [──██◆██──]  REEL"   green while the marker is in the window
 *   fell   AetherionForaging ForagingStrike "Fell  [──██◆██──]  CHOP"  green while the marker is in the window
 *   catch  AetherMobs CatchTimingSession "✦ Now  ┃…"                   yellow while the marker is in the window
 *
 * Each returns null when that bar is not showing.
 */

export function bars(bot) {
  const list = bot?.bossBars
  if (!list) return []
  const values = Array.isArray(list) ? list : list instanceof Map ? [...list.values()] : Object.values(list)
  return values.map((bar) => ({
    text: toPlain(bar?.title ?? '', bot.registry),
    color: String(bar?.color || '')
  }))
}

/** Pure parser so tests can feed it plain text + colour. */
export function readBar(text, color) {
  const plain = String(text || '')
  const lower = plain.toLowerCase()
  const bracket = plain.includes('[') && plain.includes(']')
  if (lower.includes('fishing')) {
    if (!bracket) {
      const approach = lower.includes('circling') || lower.includes('ready')
      return { kind: 'fish', phase: approach ? 'approach' : 'wait', hot: false, perfect: false }
    }
    const hot = color === 'green' || /\b(reel|now)\b/i.test(plain)
    return { kind: 'fish', phase: 'strike', hot, perfect: /\bnow\b/i.test(plain) }
  }
  if (lower.startsWith('fell') || /(^|\s)fell\s+\[/i.test(plain)) {
    if (lower.includes('lmb') || lower.includes('other trees free')) {
      const cooling = lower.includes('other trees free')
      return { kind: 'fell', phase: cooling ? 'cooling' : 'preview', hot: false, perfect: false }
    }
    const hot = color === 'green' || /\b(chop|now)\b/i.test(plain)
    return { kind: 'fell', phase: 'strike', hot, perfect: /\bnow\b/i.test(plain) }
  }
  if (lower.includes('hold it') || lower.includes('✦ now') || lower.includes('perfect') || lower.includes('locked')) {
    const locked = lower.includes('locked') || lower.includes('perfect')
    return {
      kind: 'catch',
      phase: locked ? 'locked' : 'window',
      hot: !locked && (color === 'yellow' || lower.includes('now')),
      perfect: false
    }
  }
  return null
}

export function timingBar(bot, kind) {
  for (const bar of bars(bot)) {
    const read = readBar(bar.text, bar.color)
    if (read && (!kind || read.kind === kind)) return read
  }
  return null
}

/**
 * How good this bot is at timing bars, stable per login name (0.55 … 0.95).
 * Good bots wait for the gold cell, weak ones take the first green and now and then miss on purpose,
 * so streaks, perfects and misses all show up in the isle telemetry.
 */
export function timingSkill(name) {
  let hash = 0
  for (const ch of String(name || 'bot')) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
  return 0.55 + (hash % 41) / 100
}

/**
 * Wait for the bar of {@code kind} to turn hot, then call {@code click} once.
 * Resolves 'clicked', 'gone' (bar closed without a click) or 'timeout'.
 */
export function playBar(bot, kind, click, { timeoutMs = 9000, skill = timingSkill(bot?.stressName), startGraceMs = 2500 } = {}) {
  return new Promise((resolve) => {
    const start = Date.now()
    let seen = false
    let hotPasses = 0
    let wasHot = false
    let done = false
    const finish = (result) => {
      if (done) return
      done = true
      clearInterval(handle)
      resolve(result)
    }
    const handle = setInterval(() => {
      if (!bot.entity) return finish('gone')
      const bar = timingBar(bot, kind)
      if (!bar) {
        if (seen || Date.now() - start > startGraceMs) return finish(seen ? 'gone' : 'timeout')
        return
      }
      seen = true
      if (bar.phase !== 'strike' && bar.phase !== 'window') {
        if (Date.now() - start > timeoutMs) finish('timeout')
        return
      }
      if (bar.hot && !wasHot) hotPasses++
      wasHot = bar.hot
      if (bar.hot) {
        // Gold cell for skilled bots on their first pass; anyone takes plain green after that.
        const wantPerfect = bar.perfect || hotPasses > 1 || Math.random() > skill
        const fumble = hotPasses === 1 && Math.random() > skill + 0.3
        if (wantPerfect && !fumble) {
          Promise.resolve().then(click).catch(() => {})
          finish('clicked')
          return
        }
      }
      if (Date.now() - start > timeoutMs) finish('timeout')
    }, 45)
  })
}
