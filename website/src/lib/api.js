/**
 * Client for the website API (/api/web, proxied by nginx to the Control API).
 * Errors carry the server's `code` so the UI can show a translated message.
 */

export class ApiError extends Error {
  constructor(message, { status = 0, code = 'NETWORK', details = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

const BASE = '/api/web'

async function request(path, { method = 'GET', body, signal } = {}) {
  const headers = { Accept: 'application/json' }
  if (method !== 'GET') headers['X-Aetherion-Web'] = '1'
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response
  try {
    response = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      credentials: 'same-origin',
      signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new ApiError('Network error', { code: 'NETWORK' })
  }

  const text = await response.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }
  if (!response.ok) {
    throw new ApiError(data?.error || `Request failed (${response.status})`, {
      status: response.status,
      code: data?.code || (response.status >= 500 ? 'UPSTREAM' : 'GENERIC'),
      details: data?.details ?? null,
    })
  }
  return data
}

export const api = {
  network: () => request('/network'),
  options: () => request('/options'),
  me: () => request('/auth/me'),
  login: (username, password) => request('/auth/login', { method: 'POST', body: { username, password } }),
  register: (username, password) => request('/auth/register', { method: 'POST', body: { username, password } }),
  logout: () => request('/auth/logout', { method: 'POST' }),
  servers: () => request('/servers'),
  server: (id) => request(`/servers/${encodeURIComponent(id)}`),
  createServer: (input) => request('/servers', { method: 'POST', body: input }),
  updateServer: (id, patch) => request(`/servers/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
  deleteServer: (id) => request(`/servers/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  action: (id, action) => request(`/servers/${encodeURIComponent(id)}/${action}`, { method: 'POST' }),
  console: (id, lines = 250) => request(`/servers/${encodeURIComponent(id)}/console?lines=${lines}`),
  command: (id, command) => request(`/servers/${encodeURIComponent(id)}/console`, { method: 'POST', body: { command } }),
}

/** Localised message for an ApiError, falling back to the server's text. */
export function errorMessage(error, copy) {
  const table = copy?.app?.errors ?? {}
  if (error?.code === 'RUNNING_LIMIT' && error.details?.runningName && table.RUNNING_LIMIT_NAMED) {
    return table.RUNNING_LIMIT_NAMED.replace('{name}', error.details.runningName)
  }
  if (error?.code === 'RATE_LIMITED' && error.details?.retryAfterSec && table.RATE_LIMITED_WAIT) {
    return table.RATE_LIMITED_WAIT.replace('{min}', String(Math.max(1, Math.ceil(error.details.retryAfterSec / 60))))
  }
  return table[error?.code] || error?.message || table.GENERIC || 'Something went wrong.'
}
