// Turns the preview build's index.html into the fragment an Artifact page expects:
// no doctype/html/head/body, title first, everything relative.
import { readFileSync, writeFileSync } from 'node:fs'

const file = process.argv[2]
const html = readFileSync(file, 'utf8')
const pick = (re) => [...html.matchAll(re)].map((m) => m[0])
const out = [
  '<title>Aetherion Preview</title>',
  '<meta name="claude:viewport-fit-sides" content="cover">',
  '<meta name="color-scheme" content="dark">',
  ...pick(/<link rel="preload" as="font"[^>]*>/g),
  ...pick(/<link rel="stylesheet"[^>]*>/g),
  ...pick(/<script type="module"[^>]*><\/script>/g),
  '<div id="root"></div>',
].join('\n')
writeFileSync(file, out + '\n')
