import { SITE } from '../../content.js'
import { interpolate, SITE_VARS } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { DiscordIcon } from '../icons.jsx'
import { RevealGroup, RevealLine, Section } from '../ui.jsx'

export default function Support() {
  const { copy } = useLang()

  return (
    <Section id="support" className="bg-[linear-gradient(180deg,#070a10_0%,#0a0e16_18%,#0a0e16_82%,#070a10_100%)]">
      <RevealGroup className="grid gap-8 lg:grid-cols-[1fr_1fr] lg:items-end lg:gap-16">
        <div>
          <RevealLine as="h2" className="font-display text-4xl font-bold tracking-wide text-white sm:text-5xl sm:leading-[1.05]">
            {copy.support.title}
          </RevealLine>
          <RevealLine as="p" className="mt-4 max-w-[44ch] text-base leading-relaxed text-mist/70 sm:text-lg">
            {copy.support.sub}
          </RevealLine>
        </div>
        <RevealLine className="border-l-2 border-gold/60 py-1 pl-5">
          <p className="text-sm font-bold text-gold">{copy.support.bannerKicker}</p>
          <p className="mt-1.5 text-[0.95rem] font-semibold text-white">{copy.support.bannerTitle}</p>
          <p className="mt-1 text-sm leading-relaxed text-mist/65">{copy.support.bannerBody}</p>
        </RevealLine>
      </RevealGroup>

      <div className="mt-10 grid gap-4 lg:grid-cols-2">
        <article className="panel h-full p-6">
          <h3 className="text-lg font-bold text-white">{copy.support.fulfillTitle}</h3>
          <p className="mt-2 text-sm leading-relaxed text-mist/70">{copy.support.fulfillIntro}</p>
          <ol className="mt-4 list-decimal space-y-2 pl-5 text-sm leading-relaxed text-mist/75 marker:font-bold marker:text-bell">
            {copy.support.fulfill.map((line) => (
              <li key={line}>{interpolate(line, SITE_VARS)}</li>
            ))}
          </ol>
          <a href={SITE.discord} className="btn btn-secondary notch mt-5" target="_blank" rel="noreferrer">
            <DiscordIcon className="h-4 w-4" />
            {copy.support.toDiscord}
          </a>
        </article>

        <article className="panel h-full p-6">
          <h3 className="text-lg font-bold text-white">{copy.support.paymentsTitle}</h3>
          <p className="mt-2 text-sm leading-relaxed text-mist/70">{copy.support.paymentsBody}</p>
        </article>
      </div>
    </Section>
  )
}
