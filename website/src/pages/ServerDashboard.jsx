import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth.jsx'
import { AppShell, BackLink } from '../components/app/AppShell.jsx'
import AddonsPanel from '../components/app/AddonsPanel.jsx'
import ConsolePanel from '../components/app/ConsolePanel.jsx'
import { ServerActionButton } from '../components/app/ServerCard.jsx'
import { RamChip, ServerGlyph, SoftwareLabel } from '../components/app/ServerBits.jsx'
import SettingsPanel from '../components/app/SettingsPanel.jsx'
import { useServerActions } from '../components/app/useServerActions.js'
import { CopyButton, Dialog, Notice, Skeleton, Spinner, StatusPill, useToast } from '../components/ui.jsx'
import { HERO_IMAGE } from '../content.js'
import { interpolate } from '../copy.js'
import { useLang } from '../i18n.jsx'
import { api, errorMessage } from '../lib/api.js'
import { BUSY_STATUSES, formatDuration, formatRam, minutesUntil, SOFTWARE_LABEL } from '../lib/format.js'
import { useNow } from '../lib/hooks.js'
import { usePolling } from '../lib/usePolling.js'
import { Link, useRouter } from '../router.jsx'

function pollInterval(data) {
  const status = data?.server?.status
  if (!status) return 5000
  if (BUSY_STATUSES.has(status)) return 2000
  return status === 'online' ? 5000 : 10000
}

function Stat({ label, value, sub }) {
  return (
    <div className="panel-raised px-4 py-3.5">
      <p className="text-[0.66rem] font-extrabold tracking-[0.16em] text-ash uppercase">{label}</p>
      <p className="mt-1 truncate text-lg font-bold text-white">{value}</p>
      {sub ? <p className="mt-0.5 truncate text-xs text-mist/55">{sub}</p> : null}
    </div>
  )
}

function SetupProgress({ server, onDismiss }) {
  const { copy } = useLang()
  const t = copy.app.dash.setup
  const { status } = server
  const stepState = (index) => {
    if (index === 0) return 'done'
    if (index === 1) return status === 'preparing' ? 'active' : status === 'failed' ? 'error' : 'done'
    if (index === 2) {
      if (status === 'starting' || status === 'restarting') return 'active'
      return status === 'online' ? 'done' : 'todo'
    }
    return status === 'online' ? 'done' : 'todo'
  }
  const finished = status === 'online' || (status === 'offline' && !server.job)
  const title = status === 'online' ? t.readyTitle : status === 'offline' ? t.stoppedTitle : t.title
  const body = status === 'online' ? t.readyBody : status === 'offline' ? t.stoppedBody : null

  return (
    <section className="panel p-5 sm:p-6" aria-live="polite">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-white">{title}</h2>
          {body ? <p className="mt-1 text-sm text-mist/70">{body}</p> : null}
        </div>
        {finished ? (
          <button type="button" className="btn btn-ghost btn-sm" onClick={onDismiss}>
            {copy.ui.close}
          </button>
        ) : null}
      </div>
      <ol className="mt-5 grid gap-2 sm:grid-cols-4">
        {t.steps.map((label, index) => {
          const state = stepState(index)
          if (status === 'offline' && index >= 2 && !server.job) return null
          return (
            <li
              key={label}
              className={`flex items-center gap-2.5 rounded-md px-3 py-2.5 text-sm font-semibold ${
                state === 'active' ? 'bg-white/[0.06] text-white' : state === 'done' ? 'text-emerald' : state === 'error' ? 'text-redstone' : 'text-ash'
              }`}
            >
              <span className="grid h-5 w-5 shrink-0 place-items-center text-xs" aria-hidden="true">
                {state === 'done' ? '✓' : state === 'active' ? <Spinner /> : state === 'error' ? '✕' : index + 1}
              </span>
              {label}
            </li>
          )
        })}
      </ol>
    </section>
  )
}

function DangerZone({ server }) {
  const { copy } = useLang()
  const t = copy.app.dash.danger
  const showToast = useToast()
  const { navigate } = useRouter()
  const [open, setOpen] = useState(false)
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const matches = typed.trim() === server.name

  async function remove() {
    if (!matches || busy) return
    setBusy(true)
    setError(null)
    try {
      await api.deleteServer(server.id)
      showToast(t.deleted, 'success')
      navigate('/servers', { replace: true })
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }

  return (
    <section className="rounded-lg border border-redstone/25 bg-redstone/[0.04] p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="max-w-lg">
          <h2 className="font-bold text-white">{t.title}</h2>
          <p className="mt-1 text-sm text-mist/65">{t.body}</p>
        </div>
        <button type="button" className="btn btn-danger notch" onClick={() => setOpen(true)} disabled={server.status === 'preparing'}>
          {t.delete}
        </button>
      </div>
      <Dialog
        open={open}
        onClose={() => {
          if (busy) return
          setOpen(false)
          setTyped('')
          setError(null)
        }}
        title={interpolate(t.confirmTitle, { name: server.name })}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setOpen(false)} disabled={busy}>
              {copy.ui.cancel}
            </button>
            <button type="button" className="btn btn-danger notch" onClick={remove} disabled={!matches || busy}>
              {busy ? <Spinner /> : null}
              {busy ? t.deleting : t.confirm}
            </button>
          </>
        }
      >
        <p>{t.confirmBody}</p>
        <input
          className="field mt-4"
          value={typed}
          placeholder={server.name}
          aria-label={t.confirmTitle}
          onChange={(event) => setTyped(event.target.value)}
          onKeyDown={(event) => event.key === 'Enter' && remove()}
        />
        {error ? (
          <div className="mt-3">
            <Notice tone="error">{errorMessage(error, copy)}</Notice>
          </div>
        ) : null}
      </Dialog>
    </section>
  )
}

export default function ServerDashboard({ id }) {
  const { copy } = useLang()
  const t = copy.app.dash
  const { user, expire } = useAuth()
  const { search, navigate } = useRouter()
  const now = useNow(1000)
  const [tab, setTab] = useState('console')
  const [tiers, setTiers] = useState(null)
  const [showSetup, setShowSetup] = useState(() => new URLSearchParams(search).has('setup'))
  const [confirmSwitch, setConfirmSwitch] = useState(false)

  const { data, error, loading, offline, mutate } = usePolling(
    async () => {
      const [server, list] = await Promise.all([api.server(id), api.servers()])
      return { server, list }
    },
    { interval: pollInterval, deps: [id] },
  )

  useEffect(() => {
    api.options().then((opts) => setTiers(opts.tiers)).catch(() => setTiers(null))
  }, [])

  useEffect(() => {
    if (error?.status === 401) expire()
  }, [error, expire])

  const server = data?.server
  const serverName = server?.name
  useEffect(() => {
    if (serverName) document.title = `${serverName} · Aetherion`
  }, [serverName])

  const applyServer = useCallback(
    (next) => {
      if (!next?.id) return
      mutate((current) => {
        if (!current) return current
        const servers = current.list.servers.map((item) => (item.id === next.id ? next : item))
        const active = servers.find((item) => !['offline', 'preparing', 'failed'].includes(item.status))
        return {
          server: next.id === id ? { ...current.server, ...next } : current.server,
          list: { ...current.list, servers, runningId: active?.id ?? null },
        }
      })
    },
    [id, mutate],
  )

  const { busy, run, switchTo } = useServerActions(applyServer)

  if (error?.status === 404 || (error && !data && error.code === 'NOT_FOUND')) {
    return (
      <AppShell>
        <BackLink to="/servers">{t.back}</BackLink>
        <div className="panel mt-4 p-8 text-center">
          <p className="text-mist/75">{t.notFound}</p>
          <Link to="/servers" className="btn btn-primary notch mt-6">
            {t.back}
          </Link>
        </div>
      </AppShell>
    )
  }

  if (loading && !data) {
    return (
      <AppShell>
        <Skeleton className="h-5 w-32" />
        <Skeleton className="mt-4 h-40" />
        <div className="mt-6 grid grid-cols-2 gap-3 lg:grid-cols-5">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-20" />
          ))}
        </div>
        <Skeleton className="mt-6 h-96" />
      </AppShell>
    )
  }

  if (!server) {
    return (
      <AppShell offline={offline}>
        <Notice tone="error">{errorMessage(error, copy)}</Notice>
      </AppShell>
    )
  }

  const list = data.list
  const holder = list.servers.find((item) => item.id === list.runningId) ?? null
  const blockedBy = server.status === 'offline' && holder && holder.id !== server.id ? holder : null
  const busyAction = busy[server.id]
  const idleMinutesLeft = minutesUntil(server.idleStopAt, now)
  const statusHint =
    server.status === 'starting' || server.status === 'restarting'
      ? t.startingHint
      : server.status === 'stopping'
        ? t.stoppingHint
        : server.status === 'preparing'
          ? t.preparingHint
          : server.status === 'failed'
            ? server.job?.error || t.failedHint
            : null

  return (
    <AppShell offline={offline}>
      <BackLink to="/servers">{t.back}</BackLink>

      <section className="panel relative isolate overflow-hidden">
        <img src={HERO_IMAGE} alt="" className="absolute inset-0 -z-20 h-full w-full object-cover opacity-25" />
        <div className="absolute inset-0 -z-10 bg-gradient-to-r from-obsidian via-obsidian/90 to-obsidian/50" aria-hidden="true" />
        <div className="flex flex-col gap-6 p-5 sm:p-7 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex min-w-0 items-start gap-4">
            <ServerGlyph name={server.name} tier={server.tier} size="h-16 w-16 text-2xl" />
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-3">
                <h1 className="font-display truncate text-2xl font-bold tracking-wide text-white sm:text-3xl">{server.name}</h1>
                <StatusPill status={server.status} size="lg" />
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <RamChip ramMb={server.ramMb} tier={server.tier} />
                <SoftwareLabel software={server.software} version={server.version} />
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <span className="rounded bg-black/40 px-2.5 py-1.5 font-mono text-sm text-white">{server.address}</span>
                <CopyButton value={server.address} className="btn btn-secondary btn-sm notch" toast={t.addressCopied}>
                  {t.copyAddress}
                </CopyButton>
              </div>
            </div>
          </div>

          <div className="flex flex-col items-stretch gap-2 lg:items-end">
            <div className="flex flex-wrap gap-2">
              {server.status === 'online' && !busyAction ? (
                <button type="button" className="btn btn-secondary btn-lg notch" onClick={() => run(server.id, 'restart')}>
                  {t.restart}
                </button>
              ) : null}
              <ServerActionButton
                server={server}
                slotHolder={holder}
                busyAction={busyAction}
                onStart={() => run(server.id, 'start')}
                onStop={() => run(server.id, 'stop')}
                onSwitch={() => setConfirmSwitch(true)}
                size="btn-lg min-w-40"
              />
            </div>
            {statusHint ? <p className="text-xs text-mist/60 lg:text-right">{statusHint}</p> : null}
            {blockedBy ? <p className="text-xs text-gold lg:text-right">{interpolate(t.blockedBy, { name: blockedBy.name })}</p> : null}
          </div>
        </div>
      </section>

      <div className="mt-6 space-y-6">
        {showSetup ? (
          <SetupProgress
            server={server}
            onDismiss={() => {
              setShowSetup(false)
              navigate(`/servers/${server.id}`, { replace: true })
            }}
          />
        ) : null}

        {server.notice && t.notices[server.notice.code] ? (
          <Notice tone={server.notice.code === 'IDLE_STOPPED' ? 'info' : 'warn'}>{t.notices[server.notice.code]}</Notice>
        ) : null}

        <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
          <Stat
            label={t.stats.players}
            value={server.players ? `${server.players.online} / ${server.players.max}` : t.stats.offline}
            sub={server.players ? server.players.names.join(', ') || t.stats.noPlayers : null}
          />
          <Stat
            label={t.stats.memory}
            value={server.usage?.memoryMb != null ? formatRam(server.usage.memoryMb) : t.stats.offline}
            sub={interpolate(t.stats.allocated, { ram: formatRam(server.ramMb) })}
          />
          <Stat label={t.stats.cpu} value={server.usage?.cpuPercent != null ? `${server.usage.cpuPercent} %` : t.stats.offline} />
          <Stat label={t.stats.uptime} value={server.startedAt ? formatDuration(server.startedAt, now) : t.stats.offline} />
          <Stat label={t.stats.version} value={server.version} sub={SOFTWARE_LABEL[server.software] ?? server.software} />
        </div>

        {server.status === 'online' || server.status === 'starting' ? (
          <p className="text-xs text-ash">
            {idleMinutesLeft != null ? interpolate(t.idleCountdown, { n: idleMinutesLeft }) : list.idleStopMinutes ? interpolate(t.idleNote, { n: list.idleStopMinutes }) : null}
          </p>
        ) : null}

        <div className="grid gap-6 lg:grid-cols-[1fr_18rem]">
          <section className="panel p-4 sm:p-5">
            <div className="mb-4 flex gap-1" role="tablist">
              {['console', 'addons', 'settings'].map((key) => (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={tab === key}
                  className={`notch cursor-pointer px-4 py-2 text-sm font-bold transition-colors ${
                    tab === key ? 'bg-white/10 text-white' : 'text-ash hover:text-white'
                  }`}
                  onClick={() => setTab(key)}
                >
                  {key === 'addons' ? (server.software === 'fabric' ? t.addons.mods : t.addons.plugins) : t.tabs[key]}
                </button>
              ))}
            </div>
            {tab === 'console' ? (
              <ConsolePanel server={server} username={user?.username ?? 'Steve'} />
            ) : tab === 'addons' ? (
              <AddonsPanel server={server} />
            ) : (
              <SettingsPanel server={server} tiers={tiers} onSaved={applyServer} />
            )}
          </section>

          <aside className="panel h-fit p-5">
            <h2 className="text-sm font-bold text-white">{t.join.title}</h2>
            <ol className="mt-3 space-y-2.5 text-sm text-mist/75">
              {t.join.steps.map((step, index) => (
                <li key={step} className="flex gap-3">
                  <span className="notch grid h-5 w-5 shrink-0 place-items-center bg-white/8 text-[0.65rem] font-black text-white">
                    {index + 1}
                  </span>
                  {interpolate(step, { version: server.version })}
                </li>
              ))}
            </ol>
            <CopyButton value={server.address} className="btn btn-primary notch mt-5 w-full font-mono text-sm" toast={t.addressCopied}>
              {server.address}
            </CopyButton>
          </aside>
        </div>

        <DangerZone server={server} />
      </div>

      <Dialog
        open={confirmSwitch}
        onClose={() => setConfirmSwitch(false)}
        title={copy.app.servers.switchTitle}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setConfirmSwitch(false)}>
              {copy.ui.cancel}
            </button>
            <button
              type="button"
              className="btn btn-start notch"
              onClick={() => {
                setConfirmSwitch(false)
                if (holder) void switchTo(holder.id, server.id)
              }}
            >
              {copy.app.servers.switchConfirm}
            </button>
          </>
        }
      >
        {holder ? interpolate(copy.app.servers.switchBody, { from: holder.name, to: server.name }) : null}
      </Dialog>
    </AppShell>
  )
}
