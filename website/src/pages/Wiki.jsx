import { useEffect } from 'react'
import { DiscordIcon } from '../components/icons.jsx'
import { ItemSlot } from '../components/ui.jsx'
import { SITE, WIKI_SECTIONS } from '../content.js'
import { useLang } from '../i18n.jsx'
import { Link } from '../router.jsx'

/** The archive. Rows keep their ids, so older links such as /wiki#lore still land. */
export default function Wiki() {
  const { copy } = useLang()
  const t = copy.wiki

  useEffect(() => {
    document.title = t.metaTitle
  }, [t.metaTitle])

  return (
    <main className="inner">
      <div className="inner-wrap">
        <h1 className="inner-title inner-title-xl">{t.title}</h1>
        <p className="inner-lead inner-lead-lore">{t.body}</p>
        <div className="inner-actions">
          <Link to="/wiki/faq" className="btn btn-primary btn-lg notch">
            {t.start}
          </Link>
          <a href={SITE.discord} className="btn btn-secondary btn-lg notch" target="_blank" rel="noreferrer">
            <DiscordIcon className="h-4 w-4" />
            {t.ask}
          </a>
        </div>

        <h2 className="sr-only">{t.index}</h2>
        <ul className="shelves">
          {WIKI_SECTIONS.map((section) => {
            const entry = t.sections[section.id]
            return (
              <li key={section.id} id={section.id}>
                <Link to={section.to} className="shelf">
                  <ItemSlot item={section.item} size="h-14 w-14" />
                  <span className="shelf-text">
                    <span className="shelf-title">{entry.title}</span>
                    <span className="shelf-tease">{entry.tease}</span>
                  </span>
                </Link>
              </li>
            )
          })}
        </ul>
        <p className="inner-note">{t.askNote}</p>
      </div>
    </main>
  )
}
