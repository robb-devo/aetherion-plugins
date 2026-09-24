import { interpolate, SITE_VARS } from '../copy.js'
import { SITE } from '../content.js'
import { useLang } from '../i18n.jsx'
import { useLauncherDownload } from '../lib/hooks.js'
import { Link } from '../router.jsx'

export default function Footer() {
  const { copy } = useLang()
  const launcher = useLauncherDownload()
  const links = [
    { to: '/#world', label: copy.footer.world },
    { to: '/#playground', label: copy.footer.playground },
    { to: '/servers', label: copy.footer.servers },
    { to: '/#support', label: copy.footer.shop },
  ]

  return (
    <footer className="border-t hairline bg-obsidian/60 px-4 py-12 sm:px-6">
      <div className="mx-auto grid max-w-6xl gap-10 md:grid-cols-[1.4fr_1fr_1fr]">
        <div>
          <div className="flex items-center gap-2.5">
            <img src="/favicon.svg" alt="" className="h-7 w-7" />
            <p className="font-display text-sm font-bold tracking-[0.24em] text-white">AETHERION</p>
          </div>
          <p className="mt-3 max-w-sm text-sm leading-relaxed text-mist/65">{interpolate(copy.footer.blurb, SITE_VARS)}</p>
        </div>
        <nav className="flex flex-col gap-2 text-sm" aria-label="Footer">
          {links.map((link) => (
            <Link key={link.to} to={link.to} className="w-fit text-mist/70 no-underline transition-colors hover:text-white">
              {link.label}
            </Link>
          ))}
        </nav>
        <div className="flex flex-col gap-2 text-sm">
          <a href={SITE.discord} target="_blank" rel="noreferrer" className="w-fit text-mist/70 no-underline hover:text-white">
            Discord
          </a>
          <a href={launcher} className="w-fit text-mist/70 no-underline hover:text-white">
            {copy.footer.launcher}
          </a>
          <span className="w-fit font-mono text-xs text-ash">{SITE.ip}</span>
        </div>
      </div>
      <p className="mx-auto mt-10 max-w-6xl text-xs leading-relaxed text-ash/80">{copy.footer.legal}</p>
    </footer>
  )
}
