import { useCallback, useEffect, useState } from 'react'
import { interpolate } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { api, errorMessage } from '../../lib/api.js'
import { ACTIVE_STATUSES } from '../../lib/format.js'
import { Notice, Skeleton, Spinner, useToast } from '../ui.jsx'

const MODRINTH = 'https://api.modrinth.com/v2'

function formatDownloads(n, lang) {
  return new Intl.NumberFormat(lang === 'de' ? 'de-DE' : 'en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(n)
}

function formatSize(bytes) {
  if (!bytes) return ''
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

async function searchModrinth({ query, kind, loaders, version }) {
  const facets = [[`project_type:${kind}`], loaders.map((loader) => `categories:${loader}`), [`versions:${version}`]]
  const params = new URLSearchParams({
    query,
    limit: '12',
    index: query ? 'relevance' : 'downloads',
    facets: JSON.stringify(facets),
  })
  const response = await fetch(`${MODRINTH}/search?${params}`)
  if (!response.ok) throw new Error('search failed')
  return (await response.json()).hits
}

async function compatibleVersion(projectId, loaders, version) {
  const params = new URLSearchParams({ loaders: JSON.stringify(loaders), game_versions: JSON.stringify([version]) })
  const response = await fetch(`${MODRINTH}/project/${projectId}/version?${params}`)
  if (!response.ok) return null
  const versions = await response.json()
  return versions.find((item) => item.version_type === 'release') ?? versions[0] ?? null
}

/** Modrinth-style plugin/mod browser for one server. */
export default function AddonsPanel({ server }) {
  const { copy, lang } = useLang()
  const t = copy.app.dash.addons
  const showToast = useToast()
  const [info, setInfo] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState(null)
  const [searchError, setSearchError] = useState(false)
  const [busy, setBusy] = useState({})
  const [changed, setChanged] = useState(false)
  const running = ACTIVE_STATUSES.has(server.status)

  const load = useCallback(async () => {
    try {
      setInfo(await api.addons(server.id))
      setLoadError(null)
    } catch (error) {
      setLoadError(error)
    }
  }, [server.id])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!info?.supported) return undefined
    let alive = true
    const timer = window.setTimeout(() => {
      searchModrinth({ query: query.trim(), kind: info.kind, loaders: info.loaders, version: info.version })
        .then((hits) => alive && (setResults(hits), setSearchError(false)))
        .catch(() => alive && setSearchError(true))
    }, 300)
    return () => {
      alive = false
      window.clearTimeout(timer)
    }
  }, [query, info])

  async function install(hit) {
    setBusy((current) => ({ ...current, [hit.project_id]: true }))
    try {
      const version = await compatibleVersion(hit.project_id, info.loaders, info.version)
      if (!version) {
        showToast(interpolate(t.noBuild, { version: info.version }), 'error')
        return
      }
      await api.installAddon(server.id, version.id)
      showToast(interpolate(t.installed, { name: hit.title }), 'success')
      setChanged(true)
      await load()
    } catch (error) {
      showToast(errorMessage(error, copy), 'error')
    } finally {
      setBusy((current) => ({ ...current, [hit.project_id]: false }))
    }
  }

  async function remove(file) {
    setBusy((current) => ({ ...current, [file]: true }))
    try {
      await api.removeAddon(server.id, file)
      showToast(interpolate(t.removed, { name: file }), 'success')
      setChanged(true)
      await load()
    } catch (error) {
      showToast(errorMessage(error, copy), 'error')
    } finally {
      setBusy((current) => ({ ...current, [file]: false }))
    }
  }

  if (loadError) return <Notice tone="error">{errorMessage(loadError, copy)}</Notice>
  if (!info) return <Skeleton className="h-64" />
  if (!info.supported) return <Notice tone="info">{t.vanilla}</Notice>

  const label = info.kind === 'mod' ? t.mods : t.plugins

  return (
    <div className="space-y-6">
      {changed && running ? <Notice tone="warn">{t.restartHint}</Notice> : null}

      <section>
        <h3 className="label">{interpolate(t.installedTitle, { kind: label, n: info.addons.length })}</h3>
        {info.addons.length ? (
          <ul className="divide-y divide-white/5 overflow-hidden rounded-md border border-white/8 bg-black/25">
            {info.addons.map((addon) => (
              <li key={addon.file} className="flex items-center justify-between gap-3 px-3 py-2.5">
                <span className="min-w-0 truncate font-mono text-sm text-white">{addon.file}</span>
                <span className="flex shrink-0 items-center gap-3">
                  <span className="text-xs text-ash">{formatSize(addon.size)}</span>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm !min-h-8 text-redstone"
                    disabled={busy[addon.file]}
                    onClick={() => remove(addon.file)}
                  >
                    {busy[addon.file] ? <Spinner /> : null}
                    {t.remove}
                  </button>
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-ash">{interpolate(t.none, { kind: label })}</p>
        )}
      </section>

      <section>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h3 className="label !mb-0">{interpolate(t.browse, { kind: label, version: info.version })}</h3>
          <a href="https://modrinth.com" target="_blank" rel="noreferrer" className="text-xs text-ash no-underline hover:text-white">
            {t.poweredBy}
          </a>
        </div>
        <input
          className="field mt-2"
          placeholder={interpolate(t.search, { kind: label })}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          aria-label={interpolate(t.search, { kind: label })}
        />
        {searchError ? (
          <div className="mt-3">
            <Notice tone="error">{t.searchError}</Notice>
          </div>
        ) : !results ? (
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="h-20" />
            ))}
          </div>
        ) : results.length === 0 ? (
          <p className="mt-3 text-sm text-ash">{interpolate(t.empty, { version: info.version })}</p>
        ) : (
          <ul className="mt-3 grid gap-2 sm:grid-cols-2">
            {results.map((hit) => (
              <li key={hit.project_id} className="flex gap-3 rounded-md border border-white/8 bg-black/25 p-3">
                <span className="slot h-12 w-12 shrink-0 overflow-hidden">
                  {hit.icon_url ? <img src={hit.icon_url} alt="" loading="lazy" className="!h-full !w-full !object-cover [image-rendering:auto]" /> : null}
                </span>
                <div className="min-w-0 flex-1">
                  <a
                    href={`https://modrinth.com/${hit.project_type}/${hit.slug}`}
                    target="_blank"
                    rel="noreferrer"
                    className="block truncate text-sm font-bold text-white no-underline hover:text-amethyst"
                  >
                    {hit.title}
                  </a>
                  <p className="mt-0.5 line-clamp-2 text-xs text-mist/60">{hit.description}</p>
                  <div className="mt-2 flex items-center justify-between gap-2">
                    <span className="text-[0.68rem] text-ash">
                      {interpolate(t.downloads, { n: formatDownloads(hit.downloads, lang) })}
                    </span>
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm notch !min-h-8"
                      disabled={busy[hit.project_id]}
                      onClick={() => install(hit)}
                    >
                      {busy[hit.project_id] ? <Spinner /> : null}
                      {t.install}
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
