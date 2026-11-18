import { stripLegacy } from './chat.js'

/**
 * The plugin's side door: `/testbot …` answers with one plain chat line `[tb] <verb> …` that only the bot sees.
 * Lines look like `[tb] hello server=mmo-r world=world 12.5 64.0 -3.5 role=mine setup=yes`: the verb, then
 * words, `key=value` pairs and the first three numbers as a position.
 */
export function parseTb(text) {
  const line = stripLegacy(String(text || '')).trim()
  if (!line.startsWith('[tb] ')) return null
  const words = line.slice(5).trim().split(/\s+/)
  const verb = (words.shift() || '').toLowerCase()
  const kv = {}
  const rest = []
  const numbers = []
  for (const word of words) {
    const eq = word.indexOf('=')
    if (eq > 0) {
      kv[word.slice(0, eq)] = word.slice(eq + 1)
    } else if (/^-?\d+(\.\d+)?$/.test(word)) {
      numbers.push(Number(word))
    } else {
      rest.push(word)
    }
  }
  const pos = numbers.length >= 3 ? { x: numbers[0], y: numbers[1], z: numbers[2] } : null
  return { verb, kv, words: rest, pos, line }
}

/** Plain text of a chat message the way mineflayer hands it over (ChatMessage, string or JSON). */
export function messageText(message) {
  if (message == null) return ''
  if (typeof message === 'string') return message
  try {
    if (typeof message.toString === 'function') return message.toString()
  } catch {
    /* fall through */
  }
  return String(message)
}

/** Emits `tb` on the bot for every helper line. Attach once per bot. */
export function attachHelper(bot) {
  if (bot.qaHelperAttached) return
  bot.qaHelperAttached = true
  bot.on('message', (message) => {
    const parsed = parseTb(messageText(message))
    if (parsed) bot.emit('tb', parsed)
  })
}

/**
 * Run `/testbot <args>` and wait for the answer with the same verb. Resolves null on timeout (the hub has
 * no helper, so `hello` there never answers).
 */
export function ask(bot, args, { timeoutMs = 6000 } = {}) {
  const verb = String(args).trim().split(/\s+/)[0].toLowerCase()
  return new Promise((resolve) => {
    let done = false
    const finish = (value) => {
      if (done) return
      done = true
      clearTimeout(timer)
      bot.removeListener('tb', onLine)
      bot.removeListener('end', onEnd)
      resolve(value)
    }
    const onLine = (parsed) => {
      if (parsed.verb === verb) finish(parsed)
    }
    const onEnd = () => finish(null)
    const timer = setTimeout(() => finish(null), timeoutMs)
    bot.on('tb', onLine)
    bot.once('end', onEnd)
    try {
      bot.chat(`/testbot ${args}`)
    } catch {
      finish(null)
    }
  })
}
