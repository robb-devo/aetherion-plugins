import { useEffect, useState } from 'react'
import { SITE } from '../content.js'
import { api } from './api.js'
import { usePolling } from './usePolling.js'

/** Live player count of the Aetherion network, refreshed every 30 s. */
export function useNetworkStatus() {
  const { data, loading } = usePolling(() => api.network(), { interval: 30000 })
  return { status: data, loading }
}

const RELEASES = `https://github.com/${SITE.launcherRepo}/releases/latest`
let launcherCache = null

/** Direct link to the newest launcher installer, falling back to the releases page. */
export function useLauncherDownload() {
  const [href, setHref] = useState(launcherCache ?? RELEASES)

  useEffect(() => {
    if (launcherCache) return undefined
    const controller = new AbortController()
    fetch(`https://api.github.com/repos/${SITE.launcherRepo}/releases/latest`, { signal: controller.signal })
      .then((response) => (response.ok ? response.json() : null))
      .then((release) => {
        const asset = release?.assets?.find((item) => /\.exe$/i.test(item.name) && !/blockmap/i.test(item.name))
        if (asset?.browser_download_url) {
          launcherCache = asset.browser_download_url
          setHref(asset.browser_download_url)
        }
      })
      .catch(() => {})
    return () => controller.abort()
  }, [])

  return href
}

/** Re-renders every `ms` so relative times (uptime, countdowns) stay live. */
export function useNow(ms = 1000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), ms)
    return () => window.clearInterval(id)
  }, [ms])
  return now
}
