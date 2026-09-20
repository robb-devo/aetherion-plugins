import { interpolate, SITE_VARS } from '../copy.js'
import { SITE } from '../content.js'
import { useLang } from '../i18n.jsx'
import { FadeLang } from './LangToggle.jsx'
import { MotionLink } from './ui.jsx'

export default function Footer() {
  const { copy } = useLang()

  return (
    <footer className="border-t border-white/10 px-4 py-12 sm:px-6">
      <div className="mx-auto flex max-w-6xl flex-col gap-8 md:flex-row md:items-start md:justify-between">
        <div>
          <p className="font-display text-sm font-bold tracking-[0.22em] text-white">AETHERION</p>
          <FadeLang>
            <p className="mt-2 max-w-sm text-sm text-mist/70">
              {interpolate(copy.footer.blurb, { ...SITE_VARS, ip: SITE.ip })}
            </p>
          </FadeLang>
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
            <FadeLang as="span">{copy.footer.shop}</FadeLang>
          </MotionLink>
          <MotionLink href="#karten" className="w-fit text-mist/80 no-underline hover:text-white">
            <FadeLang as="span">{copy.footer.maps}</FadeLang>
          </MotionLink>
        </div>
      </div>
      <FadeLang>
        <p className="mx-auto mt-10 max-w-6xl text-xs leading-relaxed text-mist/50">{copy.footer.legal}</p>
      </FadeLang>
    </footer>
  )
}
