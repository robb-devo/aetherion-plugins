import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { interpolate } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { api, errorMessage } from '../../lib/api.js'
import { ACTIVE_STATUSES } from '../../lib/format.js'
import { Notice, Skeleton, Spinner, useToast } from '../ui.jsx'

function formatSize(bytes) {
  if (!bytes) return ''
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

const MAX_UPLOAD_BYTES = 64 * 1024 * 1024

async function fileToBase64(file) {
  const buffer = await file.arrayBuffer()
  const bytes = new Uint8Array(buffer)
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

/** Instance-local file browser (root + plugins/mods). */
export default function FilesPanel({ server, initialPath = '' }) {
  const { copy } = useLang()
  const t = copy.app.dash.files
  const showToast = useToast()
  const inputId = useId()
  const fileRef = useRef(null)
  const [path, setPath] = useState(initialPath)
  const [listing, setListing] = useState(null)
  const [error, setError] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [changed, setChanged] = useState(false)
  const running = ACTIVE_STATUSES.has(server.status)

  const load = useCallback(
    async (nextPath = path) => {
      try {
        setListing(await api.files(server.id, nextPath))
        setError(null)
      } catch (err) {
        setError(err)
      }
    },
    [path, server.id],
  )

  useEffect(() => {
    void load(path)
  }, [load, path])

  useEffect(() => {
    if (initialPath) setPath(initialPath)
  }, [initialPath])

  const crumbs = path ? path.split('/').filter(Boolean) : []
  const inAddonFolder = Boolean(listing?.addonFolder && (path === listing.addonFolder || path.startsWith(`${listing.addonFolder}/`)))
  const canUpload = Boolean(listing?.addonFolder && path === listing.addonFolder)

  function open(entry) {
    if (!entry.directory) return
    setPath(entry.path)
  }

  function goCrumb(index) {
    if (index < 0) {
      setPath('')
      return
    }
    setPath(crumbs.slice(0, index + 1).join('/'))
  }

  async function onUpload(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (!file.name.toLowerCase().endsWith('.jar')) {
      showToast(t.uploadJarOnly, 'error')
      return
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      showToast(t.uploadTooLarge, 'error')
      return
    }
    setUploading(true)
    try {
      const data = await fileToBase64(file)
      const result = await api.uploadAddon(server.id, file.name, data)
      showToast(interpolate(t.uploaded, { name: result.file || file.name }), 'success')
      setChanged(true)
      await load(path)
    } catch (err) {
      showToast(errorMessage(err, copy), 'error')
    } finally {
      setUploading(false)
    }
  }

  if (error && !listing) return <Notice tone="error">{errorMessage(error, copy)}</Notice>
  if (!listing) return <Skeleton className="h-48" />

  return (
    <div className="space-y-3">
      {changed && running ? <Notice tone="warn">{t.restartHint}</Notice> : null}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav className="flex min-w-0 flex-wrap items-center gap-1 text-sm" aria-label={t.breadcrumb}>
          <button type="button" className="text-ash hover:text-white" onClick={() => goCrumb(-1)}>
            {t.root}
          </button>
          {crumbs.map((part, index) => (
            <span key={`${part}-${index}`} className="flex items-center gap-1">
              <span className="text-ash/50">/</span>
              <button type="button" className="truncate text-ash hover:text-white" onClick={() => goCrumb(index)}>
                {part}
              </button>
            </span>
          ))}
        </nav>
        <div className="flex flex-wrap gap-2">
          {listing.addonFolder ? (
            <button type="button" className="btn btn-ghost btn-sm !min-h-8" onClick={() => setPath(listing.addonFolder)}>
              {listing.addonFolder === 'mods' ? t.openMods : t.openPlugins}
            </button>
          ) : null}
          {canUpload ? (
            <>
              <input
                id={inputId}
                ref={fileRef}
                type="file"
                accept=".jar,application/java-archive"
                className="hidden"
                disabled={uploading}
                onChange={onUpload}
              />
              <button
                type="button"
                className="btn btn-secondary btn-sm notch !min-h-8"
                disabled={uploading}
                onClick={() => fileRef.current?.click()}
              >
                {uploading ? <Spinner /> : null}
                {uploading ? t.uploading : t.upload}
              </button>
            </>
          ) : null}
        </div>
      </div>

      <p className="text-xs text-ash">{canUpload ? t.uploadHint : inAddonFolder ? t.subfolderHint : t.browseHint}</p>

      {error ? <Notice tone="error">{errorMessage(error, copy)}</Notice> : null}

      {listing.entries.length === 0 ? (
        <p className="rounded-md border border-white/8 bg-black/25 px-3 py-4 text-sm text-ash">{t.empty}</p>
      ) : (
        <ul className="divide-y divide-white/5 overflow-hidden rounded-md border border-white/8 bg-black/25">
          {listing.entries.map((entry) => (
            <li key={entry.path}>
              {entry.directory ? (
                <button
                  type="button"
                  className="flex w-full items-center justify-between gap-3 px-3 py-2.5 text-left hover:bg-white/5"
                  onClick={() => open(entry)}
                >
                  <span className="min-w-0 truncate font-mono text-sm text-amethyst">{entry.name}/</span>
                  <span className="text-xs text-ash">{t.folder}</span>
                </button>
              ) : (
                <div className="flex items-center justify-between gap-3 px-3 py-2.5">
                  <span className="min-w-0 truncate font-mono text-sm text-white">{entry.name}</span>
                  <span className="shrink-0 text-xs text-ash">{formatSize(entry.size)}</span>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
