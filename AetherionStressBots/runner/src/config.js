/**
 * config.json on the server only needs what is local (velocitySecret, host, port, control token).
 * Everything else falls back to config.example.json from the same checkout, so new role settings
 * arrive with a git pull instead of a hand edit. Objects merge key by key; arrays and values in
 * config.json win as a whole.
 */
export function mergeConfig(defaults, local) {
  if (Array.isArray(local) || local === null || typeof local !== 'object') return local
  const out = { ...(defaults && typeof defaults === 'object' && !Array.isArray(defaults) ? defaults : {}) }
  for (const [key, value] of Object.entries(local)) {
    out[key] = key in out ? mergeConfig(out[key], value) : value
  }
  return out
}
