export function formatRam(mb) {
  if (mb == null) return '—'
  if (mb < 1024) return `${mb} MB`
  const gb = mb / 1024
  return `${Number.isInteger(gb) ? gb : gb.toFixed(1)} GB`
}

export function formatDuration(fromIso, now = Date.now()) {
  if (!fromIso) return '—'
  const seconds = Math.max(0, Math.floor((now - Date.parse(fromIso)) / 1000))
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days}d ${hours}h`
  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`
  return `${seconds}s`
}

export function minutesUntil(iso, now = Date.now()) {
  if (!iso) return null
  return Math.max(0, Math.ceil((Date.parse(iso) - now) / 60000))
}

export const SOFTWARE_LABEL = {
  paper: 'Paper',
  vanilla: 'Vanilla',
  purpur: 'Purpur',
  fabric: 'Fabric',
}

/** Status groups the UI treats alike. */
export const BUSY_STATUSES = new Set(['preparing', 'starting', 'stopping', 'restarting'])
export const ACTIVE_STATUSES = new Set(['starting', 'online', 'stopping', 'restarting'])
