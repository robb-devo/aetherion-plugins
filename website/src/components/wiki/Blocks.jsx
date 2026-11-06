import { Fragment } from 'react'
import { BOSS_DROPS, SITE } from '../../content.js'
import { interpolate, SITE_VARS } from '../../copy.js'
import { useLang } from '../../i18n.jsx'
import { Link } from '../../router.jsx'
import { JoinLinks, ServerRow } from '../home/Server.jsx'
import { ItemSlot } from '../ui.jsx'

const VARS = { ...SITE_VARS, discord: SITE.discord }
const INLINE = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g

/** **bold**, `code` and [label](href). Internal hrefs use the router. */
export function Inline({ text }) {
  const parts = interpolate(text, VARS).split(INLINE)
  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**')) return <strong key={i}>{part.slice(2, -2)}</strong>
    if (part.startsWith('`') && part.endsWith('`')) return <code key={i}>{part.slice(1, -1)}</code>
    const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(part)
    if (link) {
      const [, label, href] = link
      return href.startsWith('/') ? (
        <Link key={i} to={href}>
          {label}
        </Link>
      ) : (
        <a key={i} href={href} target="_blank" rel="noreferrer">
          {label}
        </a>
      )
    }
    return <Fragment key={i}>{part}</Fragment>
  })
}

/** The same Multiplayer entry as the homepage, so the join path looks identical everywhere. */
function JoinPanel() {
  return (
    <div className="not-prose my-6 max-w-[40rem]">
      <ServerRow />
      <JoinLinks className="mt-3" align="start" variant="btn-secondary" />
    </div>
  )
}

function Block({ block }) {
  const { copy } = useLang()
  if (typeof block === 'string') {
    return (
      <p>
        <Inline text={block} />
      </p>
    )
  }
  if (block.h3) return <h3>{block.h3}</h3>
  if (block.list) {
    return (
      <ul>
        {block.list.map((item) => (
          <li key={item}>
            <Inline text={item} />
          </li>
        ))}
      </ul>
    )
  }
  if (block.steps) {
    return (
      <ol>
        {block.steps.map((item) => (
          <li key={item}>
            <Inline text={item} />
          </li>
        ))}
      </ol>
    )
  }
  if (block.note) {
    return (
      <p className="wiki-note">
        <Inline text={block.note} />
      </p>
    )
  }
  if (block.tale) {
    return (
      <figure className="wiki-tale">
        <blockquote>
          <Inline text={block.tale.text} />
        </blockquote>
        <figcaption>{block.tale.who}</figcaption>
      </figure>
    )
  }
  if (block.qa) {
    return (
      <div className="wiki-qa">
        {block.qa.map((entry) => (
          <div key={entry.q} className="wiki-qa-item">
            <h3>{entry.q}</h3>
            <Blocks blocks={entry.a} />
          </div>
        ))}
      </div>
    )
  }
  if (block.join) return <JoinPanel />
  if (block.drops) {
    return (
      <div className="not-prose my-6">
        <div className="flex flex-wrap gap-1.5">
          {BOSS_DROPS.map((drop) => (
            <ItemSlot key={drop} item={drop} size="h-12 w-12" />
          ))}
        </div>
        <p className="mt-2 text-sm font-bold text-legendary">{copy.wiki.gearDrops}</p>
      </div>
    )
  }
  if (block.items) {
    return (
      <div className="not-prose my-6 flex flex-wrap gap-1.5" aria-hidden="true">
        {block.items.map((item) => (
          <ItemSlot key={item} item={item} size="h-12 w-12" />
        ))}
      </div>
    )
  }
  return null
}

export default function Blocks({ blocks }) {
  return blocks.map((block, i) => <Block key={i} block={block} />)
}
