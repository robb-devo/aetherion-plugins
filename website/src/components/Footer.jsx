import { SITE } from '../content.js'
import { MotionLink } from './ui.jsx'

export default function Footer() {
  return (
    <footer className="border-t border-white/10 px-4 py-12 sm:px-6">
      <div className="mx-auto flex max-w-6xl flex-col gap-8 md:flex-row md:items-start md:justify-between">
        <div>
          <p className="font-display text-sm font-bold tracking-[0.22em] text-white">AETHERION</p>
          <p className="mt-2 max-w-sm text-sm text-mist/70">
            Paper-MMO / Skyblock-Netzwerk von {SITE.owner}. Join{' '}
            <span className="text-white/85">{SITE.ip}</span>
          </p>
        </div>
        <div className="flex flex-col gap-2 text-sm">
          <MotionLink
            href={SITE.discord}
            className="w-fit text-mist/80 no-underline hover:text-white"
            target="_blank"
            rel="noreferrer"
          >
            Discord
          </MotionLink>
          <MotionLink href="#support" className="w-fit text-mist/80 no-underline hover:text-white">
            Support-Shop
          </MotionLink>
          <MotionLink href="#karten" className="w-fit text-mist/80 no-underline hover:text-white">
            Karten
          </MotionLink>
        </div>
      </div>
      <p className="mx-auto mt-10 max-w-6xl text-xs leading-relaxed text-mist/50">
        Aetherion ist ein privates Fan-Netzwerk und steht in keiner Verbindung zu Mojang Studios oder
        Microsoft. Minecraft ist eine Marke von Mojang Studios. Nicht von Mojang oder Microsoft
        genehmigt.
      </p>
    </footer>
  )
}
