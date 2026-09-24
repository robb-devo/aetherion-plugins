import { useEffect, useId, useMemo, useState } from 'react'
import { useAuth } from '../auth.jsx'
import { AppShell, BackLink, PageHeader } from '../components/app/AppShell.jsx'
import { RamChip, RamTierPicker, ServerGlyph } from '../components/app/ServerBits.jsx'
import { Notice, Skeleton, Spinner } from '../components/ui.jsx'
import { SITE } from '../content.js'
import { interpolate } from '../copy.js'
import { useLang } from '../i18n.jsx'
import { api, errorMessage } from '../lib/api.js'
import { formatRam, SOFTWARE_LABEL } from '../lib/format.js'
import { useRouter } from '../router.jsx'

export default function CreateServer() {
  const { copy } = useLang()
  const t = copy.app.create
  const { expire } = useAuth()
  const { navigate } = useRouter()
  const nameId = useId()
  const versionId = useId()

  const [options, setOptions] = useState(null)
  const [holder, setHolder] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [name, setName] = useState('')
  const [ramMb, setRamMb] = useState(1024)
  const [software, setSoftware] = useState('paper')
  const [version, setVersion] = useState('')
  const [startNow, setStartNow] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    document.title = `${t.title} · Aetherion`
  }, [t.title])

  useEffect(() => {
    let alive = true
    Promise.all([api.options(), api.servers()])
      .then(([opts, list]) => {
        if (!alive) return
        setOptions(opts)
        setRamMb(opts.defaultRamMb)
        setSoftware(opts.defaultSoftware)
        setVersion(opts.software.find((entry) => entry.id === opts.defaultSoftware)?.versions[0] ?? '')
        const running = list.servers.find((server) => server.id === list.runningId) ?? null
        setHolder(running)
        if (running) setStartNow(false)
      })
      .catch((err) => {
        if (!alive) return
        if (err?.status === 401) expire()
        setLoadError(err)
      })
    return () => {
      alive = false
    }
  }, [expire])

  const versions = useMemo(
    () => options?.software.find((entry) => entry.id === software)?.versions ?? [],
    [options, software],
  )

  const trimmed = name.trim() || t.namePlaceholder
  const nameValid = trimmed.length >= 2 && trimmed.length <= 32
  const canSubmit = options && nameValid && version && !busy
  const selectedTier = options?.tiers.find((tier) => tier.ramMb === ramMb)
  const poolLow = options && options.pool.freeMb < ramMb

  async function submit(event) {
    event.preventDefault()
    if (!canSubmit) return
    setBusy(true)
    setError(null)
    try {
      const server = await api.createServer({ name: trimmed, ramMb, software, version, startNow: startNow && !holder })
      navigate(`/servers/${server.id}?setup=1`)
    } catch (err) {
      if (err?.status === 401) expire()
      setError(err)
      setBusy(false)
    }
  }

  return (
    <AppShell>
      <PageHeader back={<BackLink to="/servers">{t.back}</BackLink>} title={t.title} sub={t.sub} />

      {loadError ? (
        <div className="mt-8">
          <Notice tone="error">
            {t.loadError} {errorMessage(loadError, copy)}
          </Notice>
        </div>
      ) : !options ? (
        <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_20rem]">
          <Skeleton className="h-[30rem]" />
          <Skeleton className="h-64" />
        </div>
      ) : (
        <form className="mt-8 grid items-start gap-6 lg:grid-cols-[1fr_20rem]" onSubmit={submit} noValidate>
          <div className="panel space-y-8 p-6 sm:p-7">
            <div>
              <label className="label" htmlFor={nameId}>
                {t.name}
              </label>
              <input
                id={nameId}
                className="field"
                placeholder={t.namePlaceholder}
                maxLength={32}
                value={name}
                autoFocus
                aria-invalid={error?.code === 'INVALID_NAME'}
                onChange={(event) => setName(event.target.value)}
              />
              <p className="mt-1.5 text-xs text-ash">{t.nameHint}</p>
            </div>

            <fieldset>
              <legend className="label">{t.ram}</legend>
              <RamTierPicker tiers={options.tiers} value={ramMb} onChange={setRamMb} />
              <p className="mt-3 text-xs leading-relaxed text-ash">{t.ramNote}</p>
            </fieldset>

            <fieldset>
              <legend className="label">{t.software}</legend>
              <div role="radiogroup" className="grid gap-2 sm:grid-cols-2">
                {options.software.map((entry) => {
                  const selected = entry.id === software
                  return (
                    <label
                      key={entry.id}
                      className={`flex cursor-pointer items-start gap-3 rounded-md border bg-black/25 p-3 transition-colors ${
                        selected ? 'border-amethyst/60 bg-white/[0.04]' : 'border-white/8 hover:border-white/16'
                      }`}
                    >
                      <input
                        type="radio"
                        name="software"
                        className="mt-1 accent-[#7c4dff]"
                        checked={selected}
                        onChange={() => {
                          setSoftware(entry.id)
                          setVersion(entry.versions[0] ?? '')
                        }}
                      />
                      <span>
                        <span className="block text-sm font-bold text-white">
                          {SOFTWARE_LABEL[entry.id]}
                          {entry.id === options.defaultSoftware ? (
                            <span className="ml-2 text-[0.62rem] font-extrabold tracking-wide text-amethyst uppercase">
                              {t.recommended}
                            </span>
                          ) : null}
                        </span>
                        <span className="mt-0.5 block text-xs text-mist/60">{t.softwareHints[entry.id]}</span>
                      </span>
                    </label>
                  )
                })}
              </div>
            </fieldset>

            <div className="grid gap-6 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor={versionId}>
                  {t.version}
                </label>
                <select id={versionId} className="field" value={version} onChange={(event) => setVersion(event.target.value)}>
                  {versions.map((item, index) => (
                    <option key={item} value={item}>
                      {item}
                      {index === 0 ? ` (${t.latest})` : ''}
                      {item === SITE.minecraft ? ' · Aetherion' : ''}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <span className="label">{t.startNow}</span>
                {holder ? (
                  <p className="text-sm leading-relaxed text-mist/70">{interpolate(t.startBlocked, { name: holder.name })}</p>
                ) : (
                  <label className="flex min-h-[2.9rem] cursor-pointer items-center gap-3">
                    <input
                      type="checkbox"
                      className="h-4 w-4 accent-[#45c46b]"
                      checked={startNow}
                      onChange={(event) => setStartNow(event.target.checked)}
                    />
                    <span className="text-sm text-mist/80">{t.startNowHint}</span>
                  </label>
                )}
              </div>
            </div>
          </div>

          <aside className="panel p-6 lg:sticky lg:top-24">
            <p className="label">{t.summary}</p>
            <div className="mt-3 flex items-center gap-3">
              <ServerGlyph name={trimmed} tier={selectedTier?.rarity} />
              <div className="min-w-0">
                <p className="truncate font-bold text-white">{trimmed}</p>
                <div className="mt-1 flex items-center gap-2">
                  <RamChip ramMb={ramMb} tier={selectedTier?.rarity} />
                  <span className="text-xs text-ash">
                    {SOFTWARE_LABEL[software]} {version}
                  </span>
                </div>
              </div>
            </div>
            <ul className="mt-5 space-y-2 border-t hairline pt-4 text-xs leading-relaxed text-mist/65">
              <li>{t.summaryLimit}</li>
              {options.idleStopMinutes ? <li>{interpolate(t.summaryIdle, { n: options.idleStopMinutes })}</li> : null}
              <li>
                {formatRam(ramMb)} · {selectedTier?.cpuCores} CPU
              </li>
            </ul>
            {poolLow ? (
              <div className="mt-4">
                <Notice tone="warn">{t.poolLow}</Notice>
              </div>
            ) : null}
            {error ? (
              <div className="mt-4">
                <Notice tone="error">{errorMessage(error, copy)}</Notice>
              </div>
            ) : null}
            <button type="submit" className="btn btn-primary btn-lg notch mt-6 w-full" disabled={!canSubmit}>
              {busy ? <Spinner /> : null}
              {busy ? t.submitting : t.submit}
            </button>
          </aside>
        </form>
      )}
    </AppShell>
  )
}
