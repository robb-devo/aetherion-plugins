import { prerenderToNodeStream } from 'react-dom/static'
import App from './App.jsx'
import { COPY } from './copy.js'
import { WIKI_SECTIONS } from './content.js'

/** Every page that exists without an account. The rest of the site is an app and renders in the browser. */
export const PAGES = ['/', '/wiki', ...WIKI_SECTIONS.map((s) => s.to), '/playground', '/support']

export function metaFor(path, lang) {
  const copy = COPY[lang]
  const wiki = path.startsWith('/wiki/') ? copy.wiki.pages[path.slice(6)] : null
  if (wiki) return { title: `${wiki.title} · ${copy.wiki.name}`, description: wiki.lead }
  if (path === '/wiki') return { title: copy.wiki.metaTitle, description: copy.wiki.body }
  if (path === '/playground') return { title: `${copy.nav.playground} · Aetherion`, description: copy.playground.body }
  if (path === '/support') return { title: `${copy.nav.support} · Aetherion`, description: copy.support.sub }
  return { title: copy.meta.title, description: copy.meta.description }
}

export async function render(path, lang) {
  const { prelude } = await prerenderToNodeStream(<App ssrPath={path} ssrLang={lang} />)
  const chunks = []
  for await (const chunk of prelude) chunks.push(chunk)
  return Buffer.concat(chunks).toString('utf8')
}
