import { lazy, Suspense, useEffect, useMemo, useState } from 'react'
import Blocks from '../components/wiki/Blocks.jsx'
import { CopyButton, ItemSlot } from '../components/ui.jsx'
import { SITE, WIKI_SECTIONS } from '../content.js'
import { useLang } from '../i18n.jsx'
import { Link } from '../router.jsx'

const DATA_PAGES = {
  island: lazy(() => import('../components/wiki/IslandData.jsx')),
  bosses: lazy(() => import('../components/wiki/BossData.jsx')),
  pets: lazy(() => import('../components/wiki/PetData.jsx')),
  gear: lazy(() => import('../components/wiki/GearData.jsx')),
}

/** Highlights the section currently in view in the side contents list. */
function useActiveSection(ids) {
  const [active, setActive] = useState(ids[0])
  useEffect(() => {
    const nodes = ids.map((id) => document.getElementById(id)).filter(Boolean)
    if (!nodes.length) return undefined
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) if (entry.isIntersecting) setActive(entry.target.id)
      },
      { rootMargin: '-20% 0px -65% 0px' },
    )
    nodes.forEach((node) => observer.observe(node))
    return () => observer.disconnect()
  }, [ids])
  return active
}

function Neighbour({ section, label, title, align = 'left' }) {
  return (
    <Link to={section ? `/wiki/${section.id}` : '/wiki'} className={`wiki-near ${align === 'right' ? 'wiki-near-r' : ''}`}>
      {section ? <ItemSlot item={section.item} size="h-11 w-11" /> : null}
      <span>
        <span className="wiki-near-label">{label}</span>
        <span className="wiki-near-title">{title}</span>
      </span>
    </Link>
  )
}

/** A slim join line at the foot of every article: the archive is a side door, not a dead end. */
function JoinLine() {
  const { copy } = useLang()
  return (
    <div className="wiki-join">
      <p>{copy.home.finale.title}</p>
      <CopyButton value={SITE.ip} copiedLabel={copy.hero.copied} toast={copy.hero.ipCopied} title={copy.hero.copyTitle} className="btn btn-primary notch">
        {SITE.ip}
      </CopyButton>
    </div>
  )
}

/** One wiki article: title, contents list, sections, previous / next. */
export default function WikiPage({ slug }) {
  const { copy } = useLang()
  const t = copy.wiki
  const page = t.pages[slug]
  const index = WIKI_SECTIONS.findIndex((section) => section.id === slug)
  const meta = WIKI_SECTIONS[index]
  const prev = WIKI_SECTIONS[index - 1]
  const next = WIKI_SECTIONS[index + 1]
  const sections = useMemo(() => page.sections ?? [], [page])
  const ids = useMemo(() => sections.map((section) => section.id), [sections])
  const active = useActiveSection(ids)
  const Data = DATA_PAGES[slug]

  useEffect(() => {
    document.title = `${page.title} · ${t.name}`
  }, [page.title, t.name])

  return (
    <main className="inner">
      <div className="inner-wrap">
        <nav aria-label={t.archive} className="wiki-crumbs">
          <Link to="/wiki">{t.name}</Link>
          <span aria-hidden="true">/</span>
          <span aria-current="page">{page.title}</span>
        </nav>
        <header className="wiki-head">
          <ItemSlot item={meta.item} size="h-14 w-14 sm:h-16 sm:w-16" />
          <h1 className="inner-title">{page.title}</h1>
        </header>
        <p className="inner-lead inner-lead-lore">{page.lead}</p>

        {Data ? (
          <div className="wiki-data">
            <Suspense fallback={<div className="min-h-96" />}>
              <Data />
            </Suspense>
            <JoinLine />
          </div>
        ) : (
          <div className="wiki-body">
            <aside className="wiki-toc">
              <nav aria-label={t.toc}>
                <p>{t.toc}</p>
                <ul>
                  {sections.map((section) => (
                    <li key={section.id}>
                      <Link to={`/wiki/${slug}#${section.id}`} aria-current={active === section.id ? 'location' : undefined}>
                        {section.title}
                      </Link>
                    </li>
                  ))}
                </ul>
              </nav>
            </aside>
            <article className="min-w-0">
              {sections.map((section) => (
                <section key={section.id} id={section.id} className="wiki-section">
                  <div className="wiki-section-head">
                    {section.item ? <ItemSlot item={section.item} size="h-12 w-12" /> : null}
                    <h2>{section.title}</h2>
                  </div>
                  <div className="wiki-prose">
                    <Blocks blocks={section.blocks} />
                  </div>
                </section>
              ))}
              <JoinLine />
            </article>
          </div>
        )}

        <nav aria-label={t.archive} className="wiki-nearby">
          <Neighbour section={prev} label={t.prev} title={prev ? t.pages[prev.id].title : t.name} />
          {next ? <Neighbour section={next} label={t.next} title={t.pages[next.id].title} align="right" /> : null}
        </nav>
      </div>
    </main>
  )
}
