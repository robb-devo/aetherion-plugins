import { SITE, VANILLA_SERVER } from '../../content.js'
import { interpolate, SITE_VARS } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { asset } from '../../lib/asset.js'
import { useLauncherDownload, useNetworkStatus, useVanillaStatus } from '../../lib/hooks.js'
import { DiscordIcon } from '../icons.jsx'
import { CopyButton } from '../ui.jsx'

/** The signal bars from the multiplayer screen: asking, connected, or nothing. */
function Ping({ state }) {
  return (
    <span className={`ping ping-${state}`} aria-hidden="true">
      <i />
      <i />
      <i />
      <i />
      <i />
    </span>
  )
}

function useLive(hook, fallback) {
  const { copy } = useLang()
  const { status, loading } = hook()
  if (loading) return { state: 'wait', label: copy.hero.statusLoading, n: null, max: null, status: null }
  if (!status) return { state: 'none', label: fallback, n: null, max: null, status: null }
  if (!status.online) return { state: 'none', label: copy.hero.statusOffline, n: null, max: null, status }
  const n = Number(status.players) || 0
  const max = status.max ?? status.maxPlayers ?? null
  const label = n === 0 ? copy.hero.statusQuiet : n === 1 ? copy.hero.statusOnlineOne : interpolate(copy.hero.statusOnline, { n })
  return { state: 'online', label, n, max, status }
}

/** One row of the multiplayer list, as Minecraft draws it. The icon is the one players see. */
function Row({ icon, name, motd1, motd2, live, label }) {
  const { copy } = useLang()
  return (
    <div className="mp-row" role="group" aria-label={label}>
      {icon}
      <div className="min-w-0">
        <div className="mp-top">
          <p className="mp-name">{name}</p>
          <p className="mp-count" title={live.label}>
            {live.n != null ? (
              <span aria-label={interpolate(copy.hero.playersAria, { n: live.n })}>
                {live.n}
                {live.max ? <span className="mp-max">/{live.max}</span> : null}
              </span>
            ) : null}
            <Ping state={live.state} />
          </p>
        </div>
        <p className="mp-motd mp-motd-a">{motd1}</p>
        <p className="mp-motd mp-motd-b">{live.state === 'online' || live.state === 'wait' ? motd2 : live.label}</p>
      </div>
    </div>
  )
}

export function ServerRow({ className = '' }) {
  const { copy } = useLang()
  const live = useLive(useNetworkStatus, copy.hero.motd2)
  return (
    <div className={`mp-list ${className}`}>
      <Row
        icon={<img className="mp-icon" src={asset('brand/server-icon.png')} alt="" width="64" height="64" draggable="false" />}
        name={SITE.name}
        motd1={copy.hero.motd1}
        motd2={copy.hero.motd2}
        live={live}
        label={copy.hero.entryLabel}
      />
      <div className="mp-address">
        <span className="mp-ip">
          <span className="mp-ip-label">{copy.hero.ipLabel}</span>
          <span className="mp-ip-value">{SITE.ip}</span>
        </span>
        <CopyButton value={SITE.ip} copiedLabel={copy.hero.copied} toast={copy.hero.ipCopied} title={copy.hero.copyTitle} className="btn btn-primary notch mp-copy">
          {copy.hero.play}
        </CopyButton>
      </div>
    </div>
  )
}

/** The standalone vanilla server. Its address comes from the server or the build, never from this repo. */
export function VanillaRow({ className = '' }) {
  const { copy } = useLang()
  const live = useLive(useVanillaStatus, copy.hero.vanillaMotdFallback)
  const join = live.status?.join || VANILLA_SERVER.join
  if (!join) return null
  return (
    <div className={`mp-list ${className}`}>
      <Row
        icon={<span className="mp-icon mp-icon-v" aria-hidden="true">V</span>}
        name={VANILLA_SERVER.name}
        motd1={live.status?.motd?.trim() || copy.hero.vanillaMotdFallback}
        motd2={interpolate(copy.hero.vanillaVersion, { version: live.status?.version || VANILLA_SERVER.version })}
        live={live}
        label={copy.hero.vanillaEntryLabel}
      />
      <div className="mp-address">
        <span className="mp-ip">
          <span className="mp-ip-label">{copy.hero.ipLabel}</span>
          <span className="mp-ip-value">{join}</span>
        </span>
        <CopyButton value={join} copiedLabel={copy.hero.copied} toast={copy.hero.ipCopied} title={copy.hero.copyTitle} className="btn btn-secondary notch mp-copy">
          {copy.hero.copy}
        </CopyButton>
      </div>
    </div>
  )
}

export function DownloadIcon() {
  return (
    <svg viewBox="0 0 16 16" className="h-4 w-4" aria-hidden="true" fill="currentColor" shapeRendering="crispEdges">
      <path d="M7 1h2v7h2v2h-1v1H9v1H7v-1H6v-1H5V8h2zM2 13h12v2H2z" />
    </svg>
  )
}

/** Launcher and Discord. */
export function JoinLinks({ className = '', align = 'center', variant = 'btn-veil' }) {
  const { copy } = useLang()
  const launcher = useLauncherDownload()
  return (
    <div className={`flex flex-wrap items-center gap-2 ${align === 'start' ? 'justify-start' : 'justify-center'} ${className}`}>
      <a href={launcher} className={`btn ${variant} notch min-h-12 flex-1 sm:flex-none`} title={copy.hero.launcherHint}>
        <DownloadIcon />
        {copy.hero.launcher}
      </a>
      <a href={SITE.discord} className={`btn ${variant} notch min-h-12 flex-1 sm:flex-none`} target="_blank" rel="noreferrer">
        <DiscordIcon className="h-5 w-5" />
        Discord
      </a>
    </div>
  )
}

export { SITE_VARS }
