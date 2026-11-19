import { useEffect } from 'react'
import { DiscordIcon } from '../components/icons.jsx'
import { SITE } from '../content.js'
import { interpolate, SITE_VARS } from '../copy.js'
import { useLang } from '../i18n.jsx'

export default function Support() {
  const { copy } = useLang()
  const t = copy.support
  useEffect(() => {
    document.title = `${copy.nav.support} · Aetherion`
  }, [copy.nav.support])

  return (
    <main className="inner">
      <div className="inner-wrap">
        <h1 className="inner-title">{t.title}</h1>
        <p className="inner-lead">{t.sub}</p>
        <div className="support-promise">
          <p className="support-promise-title">{t.bannerTitle}</p>
          <p>{t.bannerBody}</p>
        </div>
        <div className="support-grid">
          <section className="panel inner-card">
            <h2>{t.fulfillTitle}</h2>
            <p>{t.fulfillIntro}</p>
            <ol className="inner-steps">
              {t.fulfill.map((line) => (
                <li key={line}>{interpolate(line, SITE_VARS)}</li>
              ))}
            </ol>
            <a href={SITE.discord} className="btn btn-secondary notch" target="_blank" rel="noreferrer">
              <DiscordIcon className="h-4 w-4" />
              {t.toDiscord}
            </a>
          </section>
          <section className="panel inner-card">
            <h2>{t.paymentsTitle}</h2>
            <p>{t.paymentsBody}</p>
          </section>
        </div>
      </div>
    </main>
  )
}
