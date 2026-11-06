import { interpolate, SITE_VARS } from '../copy.js'
import { SITE } from '../content.js'
import { useLang } from '../i18n.jsx'
import { useLauncherDownload } from '../lib/hooks.js'
import { Link } from '../router.jsx'
import { BrandImg } from './Brand.jsx'

export default function Footer() {
  const { copy } = useLang()
  const launcher = useLauncherDownload()
  const links = [
    { to: '/wiki', label: copy.footer.wiki },
    { to: '/wiki/faq', label: copy.footer.faq },
    { to: '/playground', label: copy.footer.playground },
    { to: '/servers', label: copy.footer.servers },
    { to: '/support', label: copy.footer.shop },
  ]

  return (
    <footer className="site-footer-pad relative bg-obsidian px-4 py-12 sm:px-6">
      <div className="mx-auto grid max-w-6xl gap-10 md:grid-cols-[1.4fr_1fr_1fr]">
        <div>
          <div className="flex items-center gap-2.5">
            <BrandImg className="h-8 w-8" />
            <p className="font-display text-lg font-extrabold text-white">Aetherion</p>
          </div>
          <p className="mt-3 max-w-sm text-base leading-snug text-mist/75">{interpolate(copy.footer.blurb, SITE_VARS)}</p>
        </div>
        <nav className="flex flex-col gap-2 text-base font-medium" aria-label="Footer">
          {links.map((link) => (
            <Link key={link.to} to={link.to} className="w-fit text-mist/70 no-underline transition-colors hover:text-white">
              {link.label}
            </Link>
          ))}
        </nav>
        <div className="flex flex-col gap-2 text-base font-medium">
          <a href={SITE.discord} target="_blank" rel="noreferrer" className="w-fit text-mist/70 no-underline hover:text-white">
            Discord
          </a>
          <a href={launcher} className="w-fit text-mist/70 no-underline hover:text-white">
            {copy.footer.launcher}
          </a>
          <span className="w-fit text-sm font-bold text-ash">{SITE.ip}</span>
        </div>
      </div>
      <p className="mx-auto mt-10 max-w-6xl text-sm leading-snug text-ash">{copy.footer.legal}</p>
    </footer>
  )
}
