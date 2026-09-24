import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth.jsx'
import { AppShell, PageHeader } from '../components/app/AppShell.jsx'
import ServerCard from '../components/app/ServerCard.jsx'
import { ServerGlyph } from '../components/app/ServerBits.jsx'
import { useServerActions } from '../components/app/useServerActions.js'
import { Dialog, ItemSlot, Notice, Skeleton, StatusPill } from '../components/ui.jsx'
import { interpolate } from '../copy.js'
import { useLang } from '../i18n.jsx'
import { api, errorMessage } from '../lib/api.js'
import { BUSY_STATUSES } from '../lib/format.js'
import { usePolling } from '../lib/usePolling.js'
import { Link } from '../router.jsx'

function RunningSlot({ holder }) {
  const { copy } = useLang()
  const t = copy.app.servers
  return (
    <div className="panel flex flex-wrap items-center gap-4 px-5 py-4">
      {holder ? <ServerGlyph name={holder.name} tier={holder.tier} size="h-11 w-11 text-base" /> : <ItemSlot size="h-11 w-11" />}
      <div className="min-w-0 flex-1">
        <p className="text-[0.68rem] font-extrabold tracking-[0.18em] text-ash uppercase">{t.slotLabel} · 1/1</p>
        <p className="mt-0.5 truncate text-sm font-semibold text-white">
          {holder
            ? interpolate(t.slotUsed, { name: holder.name, status: copy.app.status[holder.status].toLowerCase() })
            : t.slotFree}
        </p>
      </div>
      {holder ? (
        <div className="flex items-center gap-3">
          <StatusPill status={holder.status} />
          <Link to={`/servers/${holder.id}`} className="btn btn-secondary btn-sm notch">
            {t.slotManage}
          </Link>
        </div>
      ) : null}
    </div>
  )
}

function EmptyState() {
  const { copy } = useLang()
  const t = copy.app.servers
  return (
    <div className="panel flex flex-col items-center px-6 py-16 text-center">
      <div className="grid grid-cols-3 gap-1.5" aria-hidden="true">
        <ItemSlot size="h-12 w-12" />
        <ItemSlot item="compacted_diamond_pickaxe" size="h-12 w-12" />
        <ItemSlot size="h-12 w-12" />
      </div>
      <h2 className="mt-6 text-xl font-bold text-white">{t.emptyTitle}</h2>
      <p className="mt-2 max-w-sm text-sm text-mist/65">{t.emptyBody}</p>
      <Link to="/servers/new" className="btn btn-primary btn-lg notch mt-7">
        {t.emptyCta}
      </Link>
    </div>
  )
}

export default function Servers() {
  const { copy } = useLang()
  const t = copy.app.servers
  const { expire } = useAuth()
  const [confirm, setConfirm] = useState(null)

  const { data, error, loading, offline, mutate, refresh } = usePolling(() => api.servers(), {
    interval: (current) => (current?.servers?.some((server) => BUSY_STATUSES.has(server.status)) ? 2500 : 8000),
  })

  useEffect(() => {
    document.title = `${t.title} · Aetherion`
  }, [t.title])

  useEffect(() => {
    if (error?.status === 401) expire()
  }, [error, expire])

  const applyServer = useCallback(
    (server) => {
      if (!server?.id) return
      mutate((current) => {
        const base = current ?? { servers: [] }
        const servers = base.servers.map((item) => (item.id === server.id ? server : item))
        const active = servers.find((item) => !['offline', 'preparing', 'failed'].includes(item.status))
        return { ...base, servers, runningId: active?.id ?? null }
      })
    },
    [mutate],
  )

  const { busy, run, switchTo } = useServerActions(applyServer)
  const servers = data?.servers ?? []
  const holder = servers.find((server) => server.id === data?.runningId) ?? null

  return (
    <AppShell offline={offline}>
      <PageHeader
        title={t.title}
        sub={t.sub}
        actions={
          servers.length ? (
            <Link to="/servers/new" className="btn btn-primary notch">
              + {t.create}
            </Link>
          ) : null
        }
      />

      <div className="mt-8 space-y-6">
        {loading && !data ? (
          <>
            <Skeleton className="h-[4.5rem]" />
            <div className="grid gap-4 md:grid-cols-2">
              <Skeleton className="h-48" />
              <Skeleton className="h-48" />
            </div>
          </>
        ) : error && !data ? (
          <Notice
            tone="error"
            action={
              <button type="button" className="btn btn-secondary btn-sm notch" onClick={refresh}>
                {copy.ui.retry}
              </button>
            }
          >
            {t.loadError} {errorMessage(error, copy)}
          </Notice>
        ) : servers.length === 0 ? (
          <EmptyState />
        ) : (
          <>
            <RunningSlot holder={holder} />
            <div className="grid gap-4 md:grid-cols-2">
              {servers.map((server) => (
                <ServerCard
                  key={server.id}
                  server={server}
                  slotHolder={holder}
                  busyAction={busy[server.id]}
                  onStart={() => run(server.id, 'start')}
                  onStop={() => run(server.id, 'stop')}
                  onSwitch={() => setConfirm({ from: holder, to: server })}
                />
              ))}
            </div>
          </>
        )}
      </div>

      <Dialog
        open={Boolean(confirm)}
        onClose={() => setConfirm(null)}
        title={t.switchTitle}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setConfirm(null)}>
              {copy.ui.cancel}
            </button>
            <button
              type="button"
              className="btn btn-start notch"
              onClick={() => {
                const { from, to } = confirm
                setConfirm(null)
                void switchTo(from.id, to.id)
              }}
            >
              {t.switchConfirm}
            </button>
          </>
        }
      >
        {confirm ? interpolate(t.switchBody, { from: confirm.from?.name, to: confirm.to.name }) : null}
      </Dialog>
    </AppShell>
  )
}
