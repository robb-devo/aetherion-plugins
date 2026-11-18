import prismarineChat from 'prismarine-chat'

/**
 * Text helpers for 1.21 payloads. Window titles, item names, lore and boss bars arrive as NBT
 * text components (or JSON / plain strings on older paths); everything here turns them into
 * plain text so roles can read them.
 */

const chatClasses = new Map()

function chatClass(registry) {
  if (!registry) return null
  const key = registry.version?.minecraftVersion || 'default'
  if (!chatClasses.has(key)) {
    try {
      chatClasses.set(key, prismarineChat(registry))
    } catch {
      chatClasses.set(key, null)
    }
  }
  return chatClasses.get(key)
}

export function stripLegacy(text) {
  return String(text ?? '').replace(/§./g, '')
}

/** Plain text of a string, JSON text, NBT component, ChatMessage or an array of those. */
export function toPlain(value, registry) {
  if (value == null) return ''
  if (Array.isArray(value)) return value.map((part) => toPlain(part, registry)).join(' ')
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (trimmed.startsWith('{') || trimmed.startsWith('[') || trimmed.startsWith('"')) {
      const ChatMessage = chatClass(registry)
      if (ChatMessage) {
        try {
          return stripLegacy(ChatMessage.fromNotch(value).toString())
        } catch {
          /* fall through to the raw string */
        }
      }
    }
    return stripLegacy(value)
  }
  if (typeof value === 'object') {
    if (typeof value.toMotd === 'function' && typeof value.toString === 'function') {
      return stripLegacy(value.toString())
    }
    const ChatMessage = chatClass(registry)
    if (ChatMessage) {
      try {
        return stripLegacy(ChatMessage.fromNotch(value).toString())
      } catch {
        /* ignore */
      }
    }
    if (typeof value.value === 'string') return stripLegacy(value.value)
    if (typeof value.text === 'string') return stripLegacy(value.text)
  }
  return stripLegacy(String(value))
}

/**
 * Every run_command behind a clickable chat line, in order. Covers 1.21.1 ({@code clickEvent.value})
 * and 1.21.5+ ({@code click_event.command}).
 */
export function clickCommands(message) {
  const out = []
  const root = message && typeof message === 'object' && 'json' in message ? message.json : message
  const walk = (node) => {
    if (!node || typeof node !== 'object') return
    if (Array.isArray(node)) {
      node.forEach(walk)
      return
    }
    const click = node.clickEvent || node.click_event
    if (click && (click.action === 'run_command' || click.action === 'suggest_command')) {
      const command = click.value ?? click.command
      if (typeof command === 'string' && command.startsWith('/')) out.push(command)
    }
    if (node.extra) walk(node.extra)
    if (node.with) walk(node.with)
  }
  walk(root)
  return out
}
