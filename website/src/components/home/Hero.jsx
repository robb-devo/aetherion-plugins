import { motion, useReducedMotion } from 'framer-motion'
import { useMemo } from 'react'
import { HERO_IMAGE, SITE } from '../../content.js'
import { interpolate, SITE_VARS } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { useLauncherDownload, useNetworkStatus } from '../../lib/hooks.js'
import { fadeUp, heroStagger } from '../../motion.js'
import { DiscordIcon } from '../icons.jsx'
import { CopyButton } from '../ui.jsx'

function Motes() {
  const motes = useMemo(
    () =>
      Array.from({ length: 14 }, (_, i) => ({
        left: `${(i * 37) % 100}%`,
        bottom: `${(i * 23) % 40}%`,
        duration: `${9 + ((i * 7) % 8)}s`,
        delay: `${-((i * 3) % 12)}s`,
      })),
    [],
  )
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
      {motes.map((mote, i) => (
        <span
          key={i}
          className="mote"
          style={{ left: mote.left, bottom: mote.bottom, animationDuration: mote.duration, animationDelay: mote.delay }}
        />
      ))}
    </div>
  )
}

function LiveStatus() {
  const { copy } = useLang()
  const { status, loading } = useNetworkStatus()
  let tone = 'text-ash'
  let label = copy.hero.statusLoading
  if (!loading && status) {
    if (!status.online) {
      tone = 'text-gold'
      label = copy.hero.statusOffline
    } else {
      tone = 'text-emerald'
      label =
        status.players === 0
          ? copy.hero.statusQuiet
          : status.players === 1
            ? copy.hero.statusOnlineOne
            : interpolate(copy.hero.statusOnline, { n: status.players })
    }
  } else if (!loading && !status) {
    label = copy.hero.badge
  }
  return (
    <span className="notch inline-flex items-center gap-2.5 bg-black/45 px-3 py-1.5 text-xs font-bold text-white/90 backdrop-blur">
      <span className={`dot ${tone} ${status?.online ? 'dot-live' : ''}`} aria-hidden="true" />
      <span aria-live="polite">{label}</span>
      <span className="text-white/35">·</span>
      <span className="text-white/60">{copy.hero.badge}</span>
    </span>
  )
}

export default function Hero() {
  const reduced = useReducedMotion()
  const { copy } = useLang()
  const launcher = useLauncherDownload()
  const enter = reduced ? { hidden: {}, show: {} } : heroStagger
  const item = reduced ? { hidden: { opacity: 1 }, show: { opacity: 1 } } : fadeUp

  return (
    <section id="top" className="relative isolate flex min-h-[92vh] items-end overflow-hidden px-4 pt-28 pb-16 sm:px-6 sm:pb-24">
      <motion.img
        src={HERO_IMAGE}
        alt=""
        className="absolute inset-0 -z-20 h-full w-full object-cover object-[60%_50%]"
        initial={reduced ? false : { scale: 1.06 }}
        animate={{ scale: 1 }}
        transition={{ duration: 2.4, ease: [0.22, 1, 0.36, 1] }}
        fetchPriority="high"
      />
      <div className="world-fade absolute inset-0 -z-10" aria-hidden="true" />
      <div className="grain absolute inset-0 -z-10 opacity-60" aria-hidden="true" />
      <Motes />

      <motion.div className="relative mx-auto w-full max-w-6xl" variants={enter} initial="hidden" animate="show">
        <motion.div variants={item}>
          <LiveStatus />
        </motion.div>

        <motion.h1
          variants={item}
          className="font-display mt-6 text-[clamp(3rem,10vw,7rem)] leading-[0.9] font-black tracking-[0.12em] text-white [text-shadow:0_4px_30px_rgba(0,0,0,0.55)]"
        >
          AETHERION
        </motion.h1>
        <motion.p variants={item} className="mt-5 max-w-xl text-xl font-semibold text-white sm:text-2xl">
          {copy.hero.line}
        </motion.p>
        <motion.p variants={item} className="mt-3 max-w-lg text-base leading-relaxed text-mist/80">
          {copy.hero.sub}
        </motion.p>

        <motion.div variants={item} className="mt-9 flex flex-col gap-4 lg:flex-row lg:items-stretch">
          <div className="panel flex items-center gap-4 px-4 py-3 sm:min-w-[22rem]">
            <span className="flex min-w-0 flex-1 flex-col">
              <span className="text-[0.68rem] font-extrabold tracking-[0.18em] text-ash uppercase">{copy.hero.ipLabel}</span>
              <span className="truncate font-mono text-lg font-bold text-white">{SITE.ip}</span>
              <span className="text-xs text-ash">{interpolate(copy.hero.edition, SITE_VARS)}</span>
            </span>
            <CopyButton
              value={SITE.ip}
              copiedLabel={copy.hero.copied}
              toast={copy.hero.ipCopied}
              title={copy.hero.copyTitle}
              className="btn btn-primary notch"
            >
              {copy.hero.play}
            </CopyButton>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <a href={launcher} className="btn btn-secondary btn-lg notch" title={copy.hero.launcherHint}>
              <DownloadIcon />
              {copy.hero.launcher}
            </a>
            <a href={SITE.discord} className="btn btn-ghost btn-lg" target="_blank" rel="noreferrer">
              <DiscordIcon className="h-4 w-4" />
              Discord
            </a>
          </div>
        </motion.div>

        <motion.p variants={item} className="mt-5 text-xs text-mist/55">
          {interpolate(copy.hero.howTo, SITE_VARS)}
        </motion.p>
      </motion.div>
    </section>
  )
}

function DownloadIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 20h14" strokeLinecap="square" />
    </svg>
  )
}
