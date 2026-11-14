// Stills of the world from the page's own camera, for devices that don't get the 3D, plus the link preview.
// Needs the dev server running. usage: node capture_posters.cjs <url> <public dir>
const { chromium } = require('playwright')
const fs = require('fs')
const path = require('path')
const { execFileSync } = require('child_process')

const [url = 'http://127.0.0.1:5180', pub = path.join(__dirname, '../public')] = process.argv.slice(2)
const out = path.join(pub, 'posters')
fs.mkdirSync(out, { recursive: true })
const tmp = path.join(out, '_tmp')
fs.mkdirSync(tmp, { recursive: true })
const CHAPTERS = ['hero', 'about', 'jobs', 'world', 'isle', 'pets', 'dungeons', 'exile', 'join', 'finale']

;(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'] })
  const manifest = { l: {}, p: {} }
  for (const [key, w, h] of [['l', 1600, 900], ['p', 810, 1440]]) {
    const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 })
    const page = await ctx.newPage()
    await page.goto(`${url}/?shot=1`, { waitUntil: 'domcontentloaded' })
    await page.waitForFunction('window.__stage && window.__stage.loaded.city', { timeout: 300000 })
    await page.waitForTimeout(800)
    const grab = async (id) => {
      const data = await page.evaluate((ch) => {
        const s = window.__stage
        s.remeasure()
        const idx = s.keys.map((k, i) => (k.ch === ch ? i : -1)).filter((i) => i >= 0)
        const y = (s.anchors[idx[0]] + s.anchors[idx[idx.length - 1]]) / 2
        s.snap(y)
        const w = s.world
        return {
          png: document.querySelector('.w-canvas').toDataURL('image/png'),
          horizon: w.horizonY(s.lastView),
          sun: s.el.sun.style.opacity === '1' ? parseT(s.el.sun, s) : null,
          moon: s.el.moon.style.opacity === '1' ? parseT(s.el.moon, s) : null,
        }
        function parseT(node, st) {
          const m = node.style.transform.match(/translate3d\(([-\d.]+)px, ?([-\d.]+)px/)
          return m ? [Number(m[1]) / st.cssW, Number(m[2]) / st.cssH] : null
        }
      }, id)
      fs.writeFileSync(path.join(tmp, `${key}-${id}.png`), Buffer.from(data.png.split(',')[1], 'base64'))
      manifest[key][id] = { horizon: Number((data.horizon ?? 0.56).toFixed(4)), sun: data.sun, moon: data.moon }
      console.log(key, id)
    }
    for (const ch of CHAPTERS) await grab(ch)
    await ctx.close()
  }
  fs.writeFileSync(path.join(out, 'posters.json'), JSON.stringify(manifest))

  // link preview: the title behind the islands, nothing else
  const og = await browser.newContext({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1 })
  const page = await og.newPage()
  await page.goto(`${url}/?shot=1`, { waitUntil: 'domcontentloaded' })
  await page.waitForFunction('window.__stage && window.__stage.loaded.isles', { timeout: 300000 })
  await page.waitForTimeout(800)
  await page.addStyleTag({ content: '.site-nav,.hero-foot{visibility:hidden!important}' })
  await page.evaluate(() => window.__stage.snap(0))
  await page.waitForTimeout(200)
  await page.screenshot({ path: path.join(pub, 'brand/og-aetherion.jpg'), type: 'jpeg', quality: 88 })
  await browser.close()
  execFileSync('python3', ['-c', `
import glob,os
from PIL import Image
for f in glob.glob(${JSON.stringify(tmp)}+'/*.png'):
    Image.open(f).save(os.path.join(${JSON.stringify(out)},os.path.basename(f)[:-4]+'.webp'),'WEBP',quality=74,alpha_quality=88,method=6)
`])
  fs.rmSync(tmp, { recursive: true, force: true })
})()
