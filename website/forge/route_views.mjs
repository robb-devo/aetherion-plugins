// Camera positions along the page's own route, for the island bake (build_scenes.py, ISLAND_VIEWS):
// the baker leaves out every face none of them can see. Run it whenever src/world/route.js changes.
// Needs the dev server and Playwright.  usage: node forge/route_views.mjs <dev url> <out file>
import { writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT || 'playwright')
const [url = 'http://127.0.0.1:5180', out = 'island.views.txt'] = process.argv.slice(2)

// Every sample where the island is on screen. The hero can be turned by hand (stage.orbitBy clamps
// the turn to ±0.55 rad), so there the samples fan out over that arc and the pitch range. Small
// jitter covers the camera's breathing and the pointer parallax.
function sampleRoute() {
  const s = window.__stage
  s.remeasure()
  const seen = new Map()
  const add = (p) => {
    const k = p.map((v) => Math.round(v / 4)).join(',')
    if (!seen.has(k)) seen.set(k, p.map((v) => Math.round(v * 10) / 10))
  }
  const n = s.keys.length
  for (let x = 0; x <= n - 1; x += 0.05) {
    const c = s.sample(Math.min(x, n - 1 - 1e-6))
    if (s.presence('island', c.i, c.u) < 0.004) continue
    const ch = [s.keys[c.i].ch, s.keys[c.i + 1].ch]
    const hero = ch.includes('hero') || ch.includes('finale')
    const yaws = hero ? [-0.55, -0.3, 0, 0.3, 0.55] : [0]
    const pitches = hero ? [-0.1, 0.14] : [0]
    const dx = c.eye[0] - c.at[0]
    const dz = c.eye[2] - c.at[2]
    const dist = Math.hypot(dx, dz)
    for (const yaw of yaws) {
      for (const pitch of pitches) {
        const cs = Math.cos(yaw)
        const sn = Math.sin(yaw)
        add([c.at[0] + dx * cs - dz * sn, c.eye[1] + dist * pitch, c.at[2] + dx * sn + dz * cs])
      }
    }
    for (const [jx, jy, jz] of [[0, 0, 0], [6, 3, 0], [-6, -3, 0], [0, 3, 6], [0, -3, -6]]) add([c.eye[0] + jx, c.eye[1] + jy, c.eye[2] + jz])
  }
  return [...seen.values()]
}

// PLAYWRIGHT_EXE: any installed Chromium-based browser instead of Playwright's own download
const browser = await chromium.launch(process.env.PLAYWRIGHT_EXE ? { executablePath: process.env.PLAYWRIGHT_EXE } : {})
const all = new Map()
for (const [w, h] of [[1600, 900], [1280, 720], [390, 844], [810, 1080]]) {
  const page = await browser.newPage({ viewport: { width: w, height: h } })
  await page.goto(`${url}/?shot=1&posters`, { waitUntil: 'domcontentloaded' })
  await page.waitForFunction('window.__stage && window.__stage.keys.length > 0', null, { timeout: 60000 })
  for (const p of await page.evaluate(sampleRoute)) all.set(p.join(' '), p)
  await page.close()
}
await browser.close()
writeFileSync(out, [...all.keys()].join('\n') + '\n')
console.log(out, all.size, 'camera positions')
