import { interpolate } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { BUSY_STATUSES } from '../../lib/format.js'
import { Link } from '../../router.jsx'
import { CopyButton, Spinner, StatusPill } from '../ui.jsx'
import { RamChip, ServerGlyph, SoftwareLabel } from './ServerBits.jsx'

/** The action a card or dashboard should offer for a server right now. */
export function primaryAction(server, slotHolder) {
  if (server.status === 'failed') return null
  if (BUSY_STATUSES.has(server.status) && server.status !== 'starting') return 'busy'
  if (server.status === 'offline') {
    return slotHolder && slotHolder.id !== server.id ? 'switch' : 'start'
  }
  return 'stop'
}

export function ServerActionButton({ server, slotHolder, busyAction, onStart, onStop, onSwitch, size = '' }) {
  const { copy } = useLang()
  const action = primaryAction(server, slotHolder)
  const pending = Boolean(busyAction)

  if (action === null) return null
  if (action === 'busy' || pending) {
    const label =
      busyAction === 'switch' ? copy.app.servers.switching : copy.app.status[server.status] ?? copy.ui.loading
    return (
      <button type="button" className={`btn btn-secondary notch ${size}`} disabled>
        <Spinner />
        {label}
      </button>
    )
  }
  if (action === 'start') {
    return (
      <button type="button" className={`btn btn-start notch ${size}`} onClick={onStart}>
        <PlayIcon />
        {copy.app.servers.start}
      </button>
    )
  }
  if (action === 'switch') {
    return (
      <button type="button" className={`btn btn-secondary notch ${size}`} onClick={onSwitch}>
        {copy.app.servers.switchHere}
      </button>
    )
  }
  return (
    <button type="button" className={`btn btn-secondary notch ${size}`} onClick={onStop}>
      <span className="h-2.5 w-2.5 bg-redstone" aria-hidden="true" />
      {copy.app.servers.stop}
    </button>
  )
}

export function PlayIcon() {
  return (
    <svg viewBox="0 0 12 12" className="h-3 w-3" aria-hidden="true" fill="currentColor">
      <path d="M2 1h2v1h2v1h2v1h2v4H8v1H6v1H4v1H2z" />
    </svg>
  )
}

function PreviewAction({ action }) {
  const { copy } = useLang()
  if (action === 'stop') {
    return (
      <span className="btn btn-secondary btn-sm notch">
        <span className="h-2.5 w-2.5 bg-redstone" aria-hidden="true" />
        {copy.app.servers.stop}
      </span>
    )
  }
  if (action === 'switch') return <span className="btn btn-secondary btn-sm notch">{copy.app.servers.switchHere}</span>
  return (
    <span className="btn btn-start btn-sm notch">
      <PlayIcon />
      {copy.app.servers.start}
    </span>
  )
}

export default function ServerCard({ server, slotHolder, busyAction, onStart, onStop, onSwitch, preview = false }) {
  const { copy } = useLang()
  const players = server.players

  return (
    <article className="panel flex h-full flex-col p-5">
      <div className="flex items-start gap-4">
        <ServerGlyph name={server.name} tier={server.tier} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="truncate text-lg font-bold text-white">{server.name}</h3>
            <StatusPill status={server.status} />
          </div>
          <div className="mt-1.5 flex flex-wrap items-center gap-2">
            <RamChip ramMb={server.ramMb} tier={server.tier} />
            <SoftwareLabel software={server.software} version={server.version} />
            {players ? (
              <span className="text-xs font-semibold text-emerald">
                {interpolate(copy.app.servers.players, { online: players.online, max: players.max })}
              </span>
            ) : null}
          </div>
        </div>
      </div>

      <div className="mt-4 flex items-center justify-between gap-2 rounded bg-black/30 px-3 py-2">
        <span className="truncate font-mono text-sm text-mist">{server.address}</span>
        {preview ? null : (
          <CopyButton
            value={server.address}
            className="btn btn-ghost btn-sm !min-h-7 !px-2 text-xs"
            toast={copy.app.dash.addressCopied}
          >
            {copy.ui.copy}
          </CopyButton>
        )}
      </div>

      <div className="mt-auto flex flex-wrap items-center justify-end gap-2 pt-4">
        {preview ? (
          <PreviewAction action={primaryAction(server, slotHolder)} />
        ) : (
          <>
            <Link to={`/servers/${server.id}`} className="btn btn-ghost btn-sm">
              {copy.app.servers.manage}
            </Link>
            <ServerActionButton
              server={server}
              slotHolder={slotHolder}
              busyAction={busyAction}
              onStart={onStart}
              onStop={onStop}
              onSwitch={onSwitch}
              size="btn-sm"
            />
          </>
        )}
      </div>
    </article>
  )
}
