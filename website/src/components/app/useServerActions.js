import { useCallback, useState } from 'react'
import { useAuth } from '../../auth.jsx'
import { useLang } from '../../i18n.jsx'
import { api, errorMessage } from '../../lib/api.js'
import { useToast } from '../ui.jsx'

const sleep = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms))

/**
 * Start / stop / restart / switch with shared error handling. `onUpdated`
 * receives every fresh server view so lists and dashboards update instantly.
 */
export function useServerActions(onUpdated) {
  const { copy } = useLang()
  const showToast = useToast()
  const { expire } = useAuth()
  const [busy, setBusy] = useState({})

  const mark = (id, action) =>
    setBusy((current) => {
      const next = { ...current }
      if (action) next[id] = action
      else delete next[id]
      return next
    })

  const fail = useCallback(
    (error) => {
      if (error?.status === 401) expire()
      showToast(errorMessage(error, copy), 'error')
    },
    [copy, expire, showToast],
  )

  const run = useCallback(
    async (id, action) => {
      mark(id, action)
      try {
        const server = await api.action(id, action)
        onUpdated?.(server)
        return server
      } catch (error) {
        fail(error)
        return null
      } finally {
        mark(id, null)
      }
    },
    [fail, onUpdated],
  )

  /** Stops the server holding the running slot, waits for it, then starts `toId`. */
  const switchTo = useCallback(
    async (fromId, toId) => {
      mark(toId, 'switch')
      try {
        onUpdated?.(await api.action(fromId, 'stop'))
        for (let i = 0; i < 30; i += 1) {
          const from = await api.server(fromId)
          onUpdated?.(from)
          if (from.status === 'offline' || from.status === 'failed') break
          await sleep(2000)
        }
        const started = await api.action(toId, 'start')
        onUpdated?.(started)
        return started
      } catch (error) {
        fail(error)
        return null
      } finally {
        mark(toId, null)
      }
    },
    [fail, onUpdated],
  )

  return { busy, run, switchTo, fail }
}
