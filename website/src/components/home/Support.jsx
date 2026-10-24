import { SITE } from '../../content.js'
import { interpolate, SITE_VARS } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { DiscordIcon } from '../icons.jsx'
import { Reveal, Section, SectionHeading } from '../ui.jsx'

export default function Support() {
  const { copy } = useLang()

  return (
    <Section id="support" className="border-t hairline">
      <Reveal>
        <SectionHeading kicker={copy.support.kicker} title={copy.support.title} sub={copy.support.sub} />
        <div className="mt-8 flex gap-4 rounded-md border border-gold/35 bg-gold/[0.06] px-5 py-4">
          <span className="mt-1 h-2.5 w-2.5 shrink-0 bg-gold" aria-hidden="true" />
          <div>
            <p className="text-xs font-extrabold tracking-[0.18em] text-gold uppercase">{copy.support.bannerKicker}</p>
            <p className="mt-1.5 text-sm font-semibold text-white">{copy.support.bannerTitle}</p>
            <p className="mt-1 text-sm text-mist/70">{copy.support.bannerBody}</p>
          </div>
        </div>
      </Reveal>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <Reveal>
          <article className="panel h-full p-6">
            <h3 className="text-lg font-bold text-white">{copy.support.fulfillTitle}</h3>
            <p className="mt-2 text-sm text-mist/65">{copy.support.fulfillIntro}</p>
            <ol className="mt-4 list-decimal space-y-2 pl-5 text-sm leading-relaxed text-mist/75">
              {copy.support.fulfill.map((line) => (
                <li key={line}>{interpolate(line, SITE_VARS)}</li>
              ))}
            </ol>
            <a href={SITE.discord} className="btn btn-secondary notch mt-5" target="_blank" rel="noreferrer">
              <DiscordIcon className="h-4 w-4" />
              {copy.support.toDiscord}
            </a>
          </article>
        </Reveal>

        <Reveal delay={60}>
          <article className="panel h-full p-6">
            <h3 className="text-lg font-bold text-white">{copy.support.paymentsTitle}</h3>
            <p className="mt-2 text-sm leading-relaxed text-mist/70">{copy.support.paymentsBody}</p>
          </article>
        </Reveal>
      </div>
    </Section>
  )
}
