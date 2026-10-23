import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { SITE, VANILLA_SERVER } from '../../content.js'
import { interpolate, SITE_VARS } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { useLauncherDownload, useNetworkStatus, useVanillaStatus } from '../../lib/hooks.js'
import { easeOut } from '../../motion.js'
import { BrandImg } from '../Brand.jsx'
import { DiscordIcon } from '../icons.jsx'
import { CopyButton } from '../ui.jsx'

/** Signal bars from the multiplayer screen: pinging, connected, or nothing. */
function Ping({ state }) {
  const tone = state === 'online' ? 'text-emerald' : state === 'wait' ? 'text-emerald ping-wait' : 'text-ash/40'
  return (
    <span className={`ping ${tone}`} aria-hidden="true">
      <i />
      <i />
      <i />
      <i />
      <i />
    </span>
  )
}

/** Player count + status words, shared by the server entry and anything that wants a live line. */
function useLiveLabel() {
  const { copy } = useLang()
  const { status, loading } = useNetworkStatus()
  if (loading) return { state: 'wait', label: copy.hero.statusLoading, count: null }
  if (!status) return { state: 'unknown', label: copy.hero.badge, count: null }
  if (!status.online) return { state: 'offline', label: copy.hero.statusOffline, count: null }
  const n = Number(status.players) || 0
  const max = status.max ?? status.maxPlayers ?? null
  const label = n === 0 ? copy.hero.statusQuiet : n === 1 ? copy.hero.statusOnlineOne : interpolate(copy.hero.statusOnline, { n })
  return { state: 'online', label, count: { n, max } }
}

export function LiveStatus({ className = '' }) {
  const { state, label } = useLiveLabel()
  const tone = state === 'online' ? 'text-emerald' : state === 'offline' ? 'text-gold' : 'text-ash'
  return (
    <span className={`inline-flex items-center gap-2.5 text-xs font-semibold text-mist/75 ${className}`}>
      <span className={`dot ${tone} ${state === 'online' ? 'dot-live' : ''}`} aria-hidden="true" />
      <span aria-live="polite">{label}</span>
    </span>
  )
}

/**
 * Aetherion as it looks in Multiplayer: icon, name, two MOTD lines, players
 * and ping on the right — and under it, the address with the one button that
 * matters.
 */
function useVanillaLabel() {
  const { copy } = useLang()
  const { status, loading } = useVanillaStatus()
  if (loading) return { state: 'wait', label: copy.hero.statusLoading, count: null, motd: null, version: VANILLA_SERVER.version }
  if (!status?.online) {
    return { state: 'offline', label: copy.hero.vanillaOffline, count: null, motd: null, version: status?.version || VANILLA_SERVER.version }
  }
  const n = Number(status.players) || 0
  const max = status.maxPlayers ?? null
  const label = n === 0 ? copy.hero.statusQuiet : n === 1 ? copy.hero.statusOnlineOne : interpolate(copy.hero.statusOnline, { n })
  const motd = status.motd?.trim() || copy.hero.vanillaMotdFallback
  return { state: 'online', label, count: { n, max }, motd, version: status.version || VANILLA_SERVER.version }
}

/** Standalone vanilla Java — same multiplayer-row pattern as the realm entry. */
export function VanillaServerEntry({ className = '' }) {
  const { copy } = useLang()
  const live = useVanillaLabel()
  const versionLine = interpolate(copy.hero.vanillaVersion, { version: live.version || VANILLA_SERVER.version })
  return (
    <div className={`server-entry mt-3 ${className}`} role="group" aria-label={copy.hero.vanillaEntryLabel}>
      <span className="server-entry-icon bg-black/35" aria-hidden="true">
        <span className="grid h-full w-full place-items-center text-lg font-bold text-mist/80">V</span>
      </span>
      <div className="min-w-0 pt-0.5">
        <p className="text-[1.02rem] leading-tight font-extrabold text-white">{VANILLA_SERVER.name}</p>
        <p className="motd mt-1 text-bell-hi">{live.motd || copy.hero.vanillaMotdFallback}</p>
        <p className="motd text-mist/60">{versionLine}</p>
      </div>
      <div className="flex flex-col items-end gap-1 pt-0.5" title={live.label}>
        <span className="flex items-center gap-2 text-sm font-bold">
          {live.count ? (
            <span className="tabular-nums text-mist/80" aria-label={interpolate(copy.hero.playersAria, { n: live.count.n })}>
              {live.count.n}
              {live.count.max ? <span className="text-ash">/{live.count.max}</span> : null}
            </span>
          ) : null}
          <Ping state={live.state === 'online' ? 'online' : live.state === 'wait' ? 'wait' : 'none'} />
        </span>
        {live.state === 'online' ? null : (
          <span className={`text-right text-[0.7rem] font-semibold ${live.state === 'offline' ? 'text-gold' : 'text-ash'}`} aria-live="polite">
            {live.label}
          </span>
        )}
      </div>
      <div className="col-span-3 mt-2 flex items-center gap-2 rounded-[2px] bg-black/45 py-1.5 pr-1.5 pl-3 shadow-[inset_2px_2px_0_rgba(0,0,0,0.45)]">
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="text-[0.68rem] font-semibold text-ash">{copy.hero.ipLabel}</span>
          <span className="truncate font-mono text-[0.95rem] font-bold text-white sm:text-lg">{VANILLA_SERVER.join}</span>
        </span>
        <CopyButton
          value={VANILLA_SERVER.join}
          copiedLabel={copy.hero.copied}
          toast={copy.hero.ipCopied}
          title={copy.hero.copyTitle}
          className="btn btn-secondary notch min-h-12 px-5 sm:px-7"
        >
          {copy.hero.copy}
        </CopyButton>
      </div>
    </div>
  )
}

export function ServerEntry({ className = '' }) {
  const { copy } = useLang()
  const live = useLiveLabel()
  return (
    <div className={`server-entry ${className}`} role="group" aria-label={copy.hero.entryLabel}>
      <span className="server-entry-icon" aria-hidden="true">
        <BrandImg tile className="h-full w-full" />
      </span>
      <div className="min-w-0 pt-0.5">
        <p className="text-[1.02rem] leading-tight font-extrabold text-white">{SITE.name}</p>
        <p className="motd mt-1 text-bell-hi">{copy.hero.motd1}</p>
        <p className="motd text-mist/60">{copy.hero.motd2}</p>
      </div>
      <div className="flex flex-col items-end gap-1 pt-0.5" title={live.label}>
        <span className="flex items-center gap-2 text-sm font-bold">
          {live.count ? (
            <span className="tabular-nums text-mist/80" aria-label={interpolate(copy.hero.playersAria, { n: live.count.n })}>
              {live.count.n}
              {live.count.max ? <span className="text-ash">/{live.count.max}</span> : null}
            </span>
          ) : null}
          <Ping state={live.state === 'online' ? 'online' : live.state === 'wait' ? 'wait' : 'none'} />
        </span>
        {live.state === 'online' ? null : (
          <span className={`text-right text-[0.7rem] font-semibold ${live.state === 'offline' ? 'text-gold' : 'text-ash'}`} aria-live="polite">
            {live.label}
          </span>
        )}
      </div>
      <div className="col-span-3 mt-2 flex items-center gap-2 rounded-[2px] bg-black/45 py-1.5 pr-1.5 pl-3 shadow-[inset_2px_2px_0_rgba(0,0,0,0.45)]">
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="text-[0.68rem] font-semibold text-ash">{copy.hero.ipLabel}</span>
          <span className="truncate font-mono text-[0.95rem] font-bold text-white sm:text-lg">{SITE.ip}</span>
        </span>
        <CopyButton
          value={SITE.ip}
          copiedLabel={copy.hero.copied}
          toast={copy.hero.ipCopied}
          title={copy.hero.copyTitle}
          className="btn btn-primary notch min-h-12 px-5 sm:px-7"
        >
          {copy.hero.play}
        </CopyButton>
      </div>
    </div>
  )
}

/** Launcher + Discord, as stone buttons over imagery. */
export function JoinLinks({ className = '', showHint = true, align = 'center' }) {
  const { copy } = useLang()
  const launcher = useLauncherDownload()
  return (
    <div className={`flex flex-wrap items-center gap-2 ${align === 'start' ? 'justify-start' : 'justify-center'} ${className}`}>
      <a href={launcher} className="btn btn-veil notch min-h-12 flex-1 sm:flex-none" title={copy.hero.launcherHint}>
        <DownloadIcon />
        {copy.hero.launcher}
      </a>
      <a href={SITE.discord} className="btn btn-veil notch min-h-12 flex-1 sm:flex-none" target="_blank" rel="noreferrer">
        <DiscordIcon className="h-5 w-5" />
        Discord
      </a>
      {showHint ? (
        <p className="w-full pt-2 text-center text-xs font-medium text-mist/55">{interpolate(copy.hero.howTo, SITE_VARS)}</p>
      ) : null}
    </div>
  )
}

/** Directly under the hero: the join path as an actual sequence. */
export function JoinSteps() {
  const { copy } = useLang()
  const launcher = useLauncherDownload()
  const steps = copy.join.steps
  const numerals = ['I', 'II', 'III']
  return (
    <section aria-labelledby="join-title" className="relative bg-[linear-gradient(180deg,#070a10_0%,#0b1018_55%)] px-4 sm:px-6">
      <div className="mx-auto max-w-6xl py-10 sm:py-12">
        <h2 id="join-title" className="kicker">
          {copy.join.title}
        </h2>
        <ol className="mt-6 grid gap-8 md:grid-cols-3 md:gap-0">
          {[
            {
              key: 'launcher',
              action: (
                <a href={launcher} className="btn btn-secondary btn-sm notch mt-4">
                  <DownloadIcon />
                  {copy.hero.launcher}
                </a>
              ),
            },
            {
              key: 'server',
              action: (
                <CopyButton
                  value={SITE.ip}
                  copiedLabel={copy.hero.copied}
                  toast={copy.hero.ipCopied}
                  title={copy.hero.copyTitle}
                  className="btn btn-secondary btn-sm notch mt-4 font-mono"
                >
                  {SITE.ip}
                </CopyButton>
              ),
            },
            { key: 'egon', action: null },
          ].map((step, i) => (
            <li key={step.key} className="flex gap-4 md:border-l md:border-white/8 md:px-8 md:first:border-l-0 md:first:pl-0">
              <span className="font-display w-8 shrink-0 pt-0.5 text-2xl font-bold text-bell/80" aria-hidden="true">
                {numerals[i]}
              </span>
              <div className="min-w-0">
                <h3 className="text-base font-bold text-white">{steps[step.key].title}</h3>
                <p className="mt-1 text-sm leading-relaxed text-mist/65">{interpolate(steps[step.key].body, SITE_VARS)}</p>
                {step.action}
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}

/**
 * Phones only: once the hero is behind you, the address rides along at the
 * bottom of the screen. It steps aside again near the finale, which has its own.
 */
export function JoinDock() {
  const { copy } = useLang()
  const reduced = useReducedMotion()
  const launcher = useLauncherDownload()
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const onScroll = () => {
      const pastHero = window.scrollY > window.innerHeight * 0.9
      const nearEnd = window.scrollY + window.innerHeight > document.documentElement.scrollHeight - window.innerHeight * 1.1
      setVisible(pastHero && !nearEnd)
    }
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
    }
  }, [])

  return (
    <AnimatePresence>
      {visible ? (
        <motion.div
          className="dock fixed inset-x-0 bottom-0 z-40 px-3 pt-2.5 md:hidden"
          role="region"
          aria-label={copy.dock.label}
          initial={reduced ? { opacity: 0 } : { y: '110%' }}
          animate={reduced ? { opacity: 1 } : { y: 0 }}
          exit={reduced ? { opacity: 0 } : { y: '110%' }}
          transition={{ duration: 0.35, ease: easeOut }}
        >
          <div className="flex items-center gap-2">
            <CopyButton
              value={SITE.ip}
              copiedLabel={copy.hero.copied}
              toast={copy.hero.ipCopied}
              title={copy.hero.copyTitle}
              className="btn btn-primary notch min-h-12 flex-1 justify-between px-4"
            >
              <span className="truncate font-mono text-[0.92rem]">{SITE.ip}</span>
              <span className="shrink-0 text-[0.8rem]">{copy.dock.copy}</span>
            </CopyButton>
            <a href={launcher} className="btn btn-secondary notch min-h-12 !px-3.5" aria-label={copy.hero.launcher} title={copy.hero.launcher}>
              <DownloadIcon />
            </a>
          </div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  )
}

export function DownloadIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 20h14" strokeLinecap="square" />
    </svg>
  )
}
