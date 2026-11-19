// After the client and server builds: writes one real HTML file per public page and language.
// nginx serves them directly (try_files $uri $uri/ /index.html), so links, search engines and link
// previews get real content, and the page paints before any script has run.
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const dist = join(root, 'dist')
const template = readFileSync(join(dist, 'index.html'), 'utf8')
const ssr = join(root, 'dist-ssr', 'entry-server.js')
const { render, PAGES, metaFor } = await import(pathToFileURL(ssr).href)
const ORIGIN = 'https://donnernet.de'

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;')
const url = (path, lang) => `${ORIGIN}${lang === 'de' ? `/de${path === '/' ? '/' : path}` : path}`

let count = 0
for (const lang of ['en', 'de']) {
  for (const path of PAGES) {
    const body = await render(path, lang)
    const meta = metaFor(path, lang)
    const here = lang === 'de' ? `/de${path === '/' ? '' : path}` : path
    let html = template
      .replace('<html lang="en">', `<html lang="${lang}">`)
      .replace(/<title>[^<]*<\/title>/, `<title>${esc(meta.title)}</title>`)
      .replace(/<meta name="description"[^>]*>/, `<meta name="description" content="${esc(meta.description)}" />`)
      .replace(/<meta property="og:title"[^>]*>/, `<meta property="og:title" content="${esc(meta.title)}" />`)
      .replace(/<meta property="og:description"[^>]*>/, `<meta property="og:description" content="${esc(meta.description)}" />`)
      .replace(/<meta property="og:url"[^>]*>/, `<meta property="og:url" content="${url(path, lang)}" />`)
      .replace(/<meta property="og:locale" [^>]*>/, `<meta property="og:locale" content="${lang === 'de' ? 'de_DE' : 'en_US'}" />`)
      .replace(/<meta property="og:locale:alternate"[^>]*>/, `<meta property="og:locale:alternate" content="${lang === 'de' ? 'en_US' : 'de_DE'}" />`)
      .replace(/<link rel="canonical"[^>]*>/, `<link rel="canonical" href="${url(path, lang)}" />`)
      .replace(/<link rel="alternate" hreflang="en"[^>]*>/, `<link rel="alternate" hreflang="en" href="${url(path, 'en')}" />`)
      .replace(/<link rel="alternate" hreflang="de"[^>]*>/, `<link rel="alternate" hreflang="de" href="${url(path, 'de')}" />`)
      .replace('</head>', `<meta name="x-baked" content="${here}" />\n  </head>`)
      .replace('<div id="root"><!--app--></div>', `<div id="root">${body}</div>`)
    if (path !== '/') html = html.replace(/\s*<link rel="preload" as="fetch"[^>]*>/, '')
    const out = join(dist, lang === 'de' ? 'de' : '', path === '/' ? '' : path.slice(1), 'index.html')
    mkdirSync(dirname(out), { recursive: true })
    writeFileSync(out, html)
    count += 1
  }
}

// sitemap: both languages of every page
const sm = PAGES.flatMap((p) => ['en', 'de'].map((l) => `<url><loc>${url(p, l)}</loc><xhtml:link rel="alternate" hreflang="en" href="${url(p, 'en')}"/><xhtml:link rel="alternate" hreflang="de" href="${url(p, 'de')}"/></url>`))
writeFileSync(join(dist, 'sitemap.xml'), `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n${sm.join('\n')}\n</urlset>\n`)
if (existsSync(join(root, 'dist-ssr'))) rmSync(join(root, 'dist-ssr'), { recursive: true })
console.log(`prerendered ${count} pages`)
