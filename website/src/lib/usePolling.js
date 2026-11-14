import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'

/**
 * Fetches now, then again every `interval(data)` ms while the tab is visible.
 * Keeps the last good data on failure and reports `offline` after two misses,
 * so a flaky connection shows a banner instead of an empty page.
 */
export function usePolling(fetcher, { interval = 5000, enabled = true, deps = [] } = {}) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [offline, setOffline] = useState(false)
  const misses = useRef(0)
  const timer = useRef(0)
  const fetcherRef = useRef(fetcher)
  const intervalRef = useRef(interval)
  const dataRef = useRef(null)
  const depsKey = JSON.stringify(deps)

  useLayoutEffect(() => {
    fetcherRef.current = fetcher
    intervalRef.current = interval
  })

  const run = useCallback(async function tick() {
    window.clearTimeout(timer.current)
    try {
      const next = await fetcherRef.current()
      dataRef.current = next
      setData(next)
      setError(null)
      misses.current = 0
      setOffline(false)
    } catch (err) {
      setError(err)
      if (err?.code === 'NETWORK' || err?.code === 'UPSTREAM') {
        misses.current += 1
        if (misses.current >= 2) setOffline(true)
      }
    } finally {
      setLoading(false)
    }
    const wait = typeof intervalRef.current === 'function' ? intervalRef.current(dataRef.current) : intervalRef.current
    if (wait && document.visibilityState === 'visible') {
      timer.current = window.setTimeout(tick, misses.current ? Math.min(15000, wait * 2) : wait)
    }
  }, [])

  useEffect(() => {
    if (!enabled) return undefined
    void run()
    const onVisible = () => {
      if (document.visibilityState === 'visible') void run()
      else window.clearTimeout(timer.current)
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      window.clearTimeout(timer.current)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [enabled, run, depsKey])

  /** Accepts a value or an updater `(current) => next`, like setState. */
  const mutate = useCallback((next) => {
    const value = typeof next === 'function' ? next(dataRef.current) : next
    dataRef.current = value
    setData(value)
  }, [])

  return { data, error, loading, offline, refresh: run, mutate }
}
