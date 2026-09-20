import { SITE } from '../content.js'
import { DiscordIcon } from './icons.jsx'
import { CopyButton } from './ui.jsx'

function Crystal({ className, gid }) {
  return (
    <svg viewBox="0 0 120 160" className={className} aria-hidden="true">
      <defs>
        <linearGradient id={gid} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#f5e9ff" />
          <stop offset="0.4" stopColor="#c084fc" />
          <stop offset="1" stopColor="#22d3ee" />
        </linearGradient>
      </defs>
      <path d="M60 6 L112 58 L60 154 L8 58 Z" fill={`url(#${gid})`} opacity="0.92" />
      <path d="M60 28 L92 58 L60 118 L28 58 Z" fill="#07060f" opacity="0.28" />
    </svg>
  )
}

export default function Hero() {
  return (
    <section id="top" className="aurora starfield relative overflow-hidden px-4 pt-28 pb-20 sm:px-6 sm:pt-32 sm:pb-28">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_center,transparent_0%,#07060f_78%)]" />
      <div className="pointer-events-none absolute -top-24 right-[8%] hidden w-40 opacity-70 sm:block">
        <Crystal
          gid="hero-crystal-a"
          className="float-slow h-auto w-full drop-shadow-[0_0_40px_rgba(192,132,252,0.35)]"
        />
      </div>
      <div className="pointer-events-none absolute bottom-10 left-[6%] hidden w-24 opacity-50 md:block">
        <Crystal gid="hero-crystal-b" className="float-slow h-auto w-full [animation-delay:-3s]" />
      </div>
      <div
        className="pulse-glow pointer-events-none absolute top-1/3 left-1/2 h-64 w-64 -translate-x-1/2 rounded-full bg-amethyst/20 blur-3xl"
        aria-hidden="true"
      />

      <div className="relative mx-auto flex max-w-6xl flex-col items-center text-center">
        <p className="mb-5 inline-flex items-center gap-2 rounded-full border border-white/12 bg-white/5 px-3 py-1 text-[0.7rem] font-bold tracking-[0.2em] text-mist/90 uppercase">
          Pre-Pre-Beta · Freundes-Netzwerk
        </p>

        <h1 className="font-display text-[clamp(2.6rem,12vw,7.4rem)] leading-[0.92] font-black tracking-[0.16em] text-white">
          <span className="bg-gradient-to-br from-white via-amethyst to-cyan bg-clip-text text-transparent">
            AETHERION
          </span>
        </h1>

        <p className="mt-6 max-w-2xl text-lg font-medium text-white/90 sm:text-xl">
          Hypixel-Feeling. Skyblock-Inseln. Eigene Skills.
        </p>
        <p className="mt-2 max-w-xl text-sm text-mist/75 sm:text-base">
          A Paper MMO prototype — optional support, never pay-to-win.
        </p>

        <div className="mt-8 flex w-full max-w-lg flex-col items-stretch gap-3 sm:flex-row sm:items-center sm:justify-center">
          <CopyButton
            value={SITE.ip}
            className="glass flex items-center justify-between gap-4 rounded-2xl px-4 py-3 text-left sm:min-w-[280px]"
            copiedLabel="IP kopiert — in Minecraft einfügen"
            title="Server-IP kopieren"
          >
            <span className="flex min-w-0 flex-col">
              <span className="text-[0.65rem] font-bold tracking-[0.18em] text-cyan uppercase">
                Java · {SITE.proxy} :{SITE.port}
              </span>
              <span className="truncate font-mono text-lg font-bold text-white">{SITE.ip}</span>
            </span>
            <span className="shrink-0 rounded-full bg-white/10 px-3 py-1 text-xs font-bold text-white">
              Kopieren
            </span>
          </CopyButton>
        </div>

        <div className="mt-5 flex flex-col gap-3 sm:flex-row">
          <CopyButton value={SITE.ip} className="btn btn-primary" copiedLabel="IP kopiert">
            Beitreten
          </CopyButton>
          <a
            href={SITE.discord}
            className="btn btn-ghost"
            target="_blank"
            rel="noreferrer"
          >
            <DiscordIcon className="h-4 w-4" />
            Discord
          </a>
        </div>

        <p className="mt-8 max-w-lg text-xs leading-relaxed text-mist/60">
          Minecraft Java Edition → Mehrspieler → Direktverbindung →{' '}
          <span className="text-white/80">{SITE.ip}</span>
          . Support ist optional. Shards kaufen keine Power.
        </p>
      </div>
    </section>
  )
}
