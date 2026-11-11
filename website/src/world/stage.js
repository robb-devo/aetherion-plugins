import { dayAt, skyStops } from './day.js'
import { createWorld, loadMesh } from './engine.js'
import { CLOUDS, ROUTE, SETS, ZONES } from './route.js'
import { asset } from '../lib/asset.js'

/**
 * Everything behind the page, as one object that lives outside React: the sky, the sun and moon,
 * the stars and the world. Layers paint in this order, in the root stacking context:
 *
 *   sky canvas, stars, sun, moon   z -30
 *   the page's <h1>                z -20   (behind the islands)
 *   world canvas                   z -10   (transparent, fog is alpha)
 *   poster, cloud veil             z  -9
 *   normal page content            z   0
 *
 * Nothing swaps during a scroll, so nothing can pop. Only one sky exists: its colours are
 * recomputed every frame instead of crossfading stacked gradients.
 */

const lerp = (a, b, t) => a + (b - a) * t
const clamp01 = (x) => (x < 0 ? 0 : x > 1 ? 1 : x)
const smooth = (x) => { const t = clamp01(x); return t * t * (3 - 2 * t) }

/**
 * Monotone cubic tangents (Fritsch-Butland) over uneven spacing. Between two frames that are close
 * together the camera drifts; across a long gap it eases out, travels and eases in. It never
 * overshoots a key, which a plain Catmull-Rom spline does as soon as the keys are unevenly spaced.
 */
function tangents(x, y) {
  const n = y.length
  const m = new Array(n).fill(0)
  if (n < 2) return m
  const h = []
  const d = []
  for (let i = 0; i < n - 1; i += 1) {
    h[i] = x[i + 1] - x[i]
    d[i] = (y[i + 1] - y[i]) / h[i]
  }
  m[0] = d[0]
  m[n - 1] = d[n - 2]
  for (let i = 1; i < n - 1; i += 1) {
    if (d[i - 1] * d[i] <= 0) continue
    const w1 = 2 * h[i] + h[i - 1]
    const w2 = h[i] + 2 * h[i - 1]
    m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
  }
  return m
}
const hermite = (y0, y1, m0, m1, h, u) => {
  const u2 = u * u
  const u3 = u2 * u
  return (2 * u3 - 3 * u2 + 1) * y0 + (u3 - 2 * u2 + u) * h * m0 + (-2 * u3 + 3 * u2) * y1 + (u3 - u2) * h * m1
}
const CHANNELS = [['eye', 0], ['eye', 1], ['eye', 2], ['at', 0], ['at', 1], ['at', 2], ['fov'], ['sx'], ['sy']]

const SEARCH = typeof location !== 'undefined' ? new URLSearchParams(location.search) : new URLSearchParams()
export const SHOT = SEARCH.has('shot')

class Stage {
  constructor() {
    this.el = null
    this.world = null
    this.mode = 'idle' // idle → live | posters
    this.active = false // the home page is on screen
    this.keys = []
    this.anchors = []
    this.curves = []
    this.scroll = 0
    this.t = 0.8 // night until the home page asks for morning
    this.intro = 0
    this.introStart = 0
    this.time = 0
    this.tone = ''
    this.loaded = {}
    this.progress = 0
    this.listeners = new Set()
    this.orbit = { yaw: 0, pitch: 0, drag: false }
    this.parallax = { x: 0, y: 0, tx: 0, ty: 0 }
    this.roll = 0
    this.heading = null
    this.frameMs = 16
    this.slow = 0
    this.rungs = [1, 0.8, 0.66, 0.55, 0.45]
    this.rung = 0
    this.state = { tone: 'dark', progress: 0 }
    this.raf = 0
    this.last = 0
    this.frame = this.frame.bind(this)
  }

  /** Called once, when the stage's DOM exists. */
  attach(el) {
    if (this.el) return
    this.el = el
    this.skyCtx = el.sky.getContext('2d', { alpha: false })
    const motionOff = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const saveData = navigator.connection?.saveData === true
    const canDecode = typeof DecompressionStream !== 'undefined'
    if (!motionOff && !saveData && canDecode && !SEARCH.has('posters')) {
      try {
        this.world = createWorld(el.canvas, { antialias: true, preserve: SHOT })
      } catch (error) {
        console.warn('world: no shader support', error)
      }
    }
    this.mode = this.world ? 'live' : 'posters'
    document.documentElement.dataset.stage = this.mode
    if (this.world) {
      el.canvas.addEventListener('webglcontextlost', (e) => { e.preventDefault(); this.fallBack() })
    }
    this.measureSize()
    this.drawStars()
    this.ro = new ResizeObserver(() => this.remeasure())
    this.ro.observe(document.documentElement)
    window.addEventListener('resize', () => { this.measureSize(); this.remeasure() }, { passive: true })
    window.addEventListener('pointermove', (e) => {
      if (e.pointerType === 'touch') return
      this.parallax.tx = e.clientX / window.innerWidth - 0.5
      this.parallax.ty = e.clientY / window.innerHeight - 0.5
    }, { passive: true })
    document.addEventListener('visibilitychange', () => this.kick())
    document.fonts?.ready.then(() => this.remeasure())
    window.addEventListener('scroll', () => { if (this.mode === 'posters') this.kick() }, { passive: true })
    if (this.world) this.loadWorld()
    else this.usePosters()
    this.kick()
  }

  /** The first set is the one the page opens on. The others arrive behind it, unseen, in page order. */
  async loadWorld() {
    const [first, ...rest] = Object.keys(SETS)
    try {
      await this.loadSet(first, (p) => this.setProgress(p))
      this.introStart = performance.now()
      this.setProgress(1)
      this.emit()
    } catch (error) {
      console.warn('world: first set failed', error)
      this.fallBack()
      return
    }
    await new Promise((resolve) => { setTimeout(resolve, 1400) })
    for (const name of rest) {
      try {
        await this.loadSet(name)
      } catch (error) {
        console.warn('world: set failed', name, error)
      }
    }
  }

  async loadSet(name, onProgress) {
    const set = SETS[name]
    const mesh = await loadMesh(asset(set.url), onProgress)
    if (!set.place) {
      this.world.addMesh(mesh, set.origin, { name })
    } else {
      // A set made of parts that stand on their own (the islands): each placement draws its part of
      // the one mesh. The lantern list is regrouped so that every part owns a run of it.
      const partOf = (x) => Object.keys(set.parts).find((k) => x >= set.parts[k].x[0] && x < set.parts[k].x[1])
      const runs = {}
      const sorted = new Float32Array(mesh.lights.length)
      let n = 0
      for (const part of Object.keys(set.parts)) {
        const first = n
        for (let i = 0; i < mesh.lights.length; i += 4) {
          if (partOf(mesh.lights[i]) !== part) continue
          sorted.set(mesh.lights.subarray(i, i + 4), n * 4)
          n += 1
        }
        runs[part] = [first, n - first]
      }
      mesh.lights = sorted
      const stock = this.world.addMesh(mesh, [0, 0, 0], { name, draw: false })
      for (const [part, x, y, z] of set.place) {
        const c = set.parts[part].c
        this.world.addInstance(stock, [x - c[0], y - c[1], z - c[2]], {
          regions: stock.regions.filter((r) => partOf(r.c[0]) === part),
          lights: runs[part],
          center: [x, 0, z],
        })
      }
    }
    this.loaded[name] = true
    this.kick()
  }

  fallBack() {
    this.mode = 'posters'
    document.documentElement.dataset.stage = 'posters'
    this.el.canvas.style.display = 'none'
    this.usePosters()
    this.emit()
    this.kick()
  }

  /**
   * Without 3D, the world is a set of stills from the same camera, transparent where the sky is:
   * the sky still runs through the day, and the title still stands behind the islands.
   */
  async usePosters() {
    if (this.posterData) return
    this.posterData = { l: {}, p: {} }
    this.posterHorizon = 0.56
    this.posterTop = 'posterA'
    try {
      const res = await fetch(asset('posters/posters.json'))
      if (res.ok) this.posterData = await res.json()
    } catch (error) {
      console.warn('world: no posters', error)
    }
    this.kick()
  }

  posterId() {
    const tops = this.chapterTops
    if (!tops?.length) return 'hero'
    const mid = this.scroll + window.innerHeight * 0.5
    let id = tops[0].id
    for (const t of tops) if (t.top <= mid) id = t.id
    return id
  }

  /** Crossfades only once the next image can actually be shown: nothing pops in half-decoded. */
  showPoster(id) {
    if (id === this.posterWanted) return
    this.posterWanted = id
    const url = asset(`posters/${this.portrait ? 'p' : 'l'}-${id}.webp`)
    const img = new Image()
    img.src = url
    const ready = img.decode ? img.decode().catch(() => {}) : Promise.resolve()
    ready.then(() => {
      if (this.posterWanted !== id) return
      const next = this.posterTop === 'posterA' ? 'posterB' : 'posterA'
      this.el[next].src = url
      this.el[next].style.opacity = '1'
      this.el[this.posterTop].style.opacity = '0'
      this.posterTop = next
    })
  }

  stepPosters(day, dt) {
    this.remeasure() // only runs while the page moves, so a fresh measure costs nothing
    const id = this.posterId()
    this.showPoster(id)
    const meta = this.posterData?.[this.portrait ? 'p' : 'l']?.[id]
    const target = meta?.horizon ?? 0.56
    this.posterHorizon += (target - this.posterHorizon) * (1 - Math.exp(-dt / 220))
    this.paintSky(day, this.posterHorizon, 0)
    const el = this.el
    const place = (node, at) => {
      if (at) {
        node.style.transform = `translate3d(${(at[0] * this.cssW).toFixed(1)}px,${(at[1] * this.cssH).toFixed(1)}px,0)`
        node.style.opacity = '1'
      } else node.style.opacity = '0'
    }
    place(el.sun, meta?.sun)
    place(el.moon, meta?.moon)
    el.stars.style.opacity = day.stars.toFixed(3)
  }

  setProgress(p) {
    this.progress = p
    this.state = { ...this.state, progress: p }
    this.emit()
  }

  subscribe(fn) {
    this.listeners.add(fn)
    fn(this.state)
    return () => this.listeners.delete(fn)
  }

  emit() {
    for (const fn of this.listeners) fn(this.state)
  }

  /** On for the home page; elsewhere the night sky stays and the world rests. */
  setActive(active) {
    if (this.active === active) return
    this.active = active
    if (this.el) {
      document.documentElement.dataset.world = active ? 'on' : 'off'
      if (active) {
        this.remeasure()
        this.scroll = window.scrollY
      }
      this.kick()
    }
  }

  /** Chapter tops move when anything lays out differently: recalculated, never polled. */
  remeasure() {
    if (!this.active) return
    const sy = window.scrollY
    const nodes = {}
    document.querySelectorAll('[data-chapter]').forEach((n) => {
      const r = n.getBoundingClientRect()
      nodes[n.dataset.chapter] = { top: r.top + sy, h: r.height }
    })
    const portrait = window.innerHeight > window.innerWidth * 1.08
    const anchors = []
    const keys = []
    for (const k of ROUTE) {
      const n = nodes[k.ch]
      if (!n) continue
      const o = portrait && k.p ? { ...k, ...k.p } : k
      anchors.push(n.top + k.f * n.h)
      keys.push({ ...o, fov: o.fov, sx: o.sx && portrait ? 0 : o.sx || 0, sy: o.sy || 0, ch: k.ch })
    }
    if (!keys.length) return
    // the view centre passes the anchor, not the top of the screen
    const vh = window.innerHeight
    for (let i = 0; i < anchors.length; i += 1) anchors[i] = Math.max(0, anchors[i] - vh * 0.5)
    anchors[0] = 0
    const max = document.documentElement.scrollHeight - vh
    anchors[anchors.length - 1] = Math.max(anchors[anchors.length - 1], max)
    for (let i = 1; i < anchors.length; i += 1) if (anchors[i] <= anchors[i - 1]) anchors[i] = anchors[i - 1] + 1
    this.anchors = anchors
    this.keys = keys
    this.curves = CHANNELS.map(([name, axis]) => {
      const y = keys.map((k) => (axis === undefined ? k[name] : k[name][axis]))
      return { y, m: tangents(anchors, y) }
    })
    this.chapterTops = Object.entries(nodes).map(([id, n]) => ({ id, top: n.top })).sort((a, b) => a.top - b.top)
    this.portrait = portrait
  }

  /** Scroll position to a place on the route: key index plus fraction. */
  locate(y) {
    const a = this.anchors
    if (!a.length) return 0
    if (y <= a[0]) return 0
    const last = a.length - 1
    if (y >= a[last]) return last
    let lo = 0
    let hi = last
    while (hi - lo > 1) {
      const mid = (lo + hi) >> 1
      if (a[mid] <= y) lo = mid
      else hi = mid
    }
    return lo + (y - a[lo]) / (a[hi] - a[lo])
  }

  sample(s) {
    const K = this.keys
    const n = K.length - 1
    const i = Math.max(0, Math.min(n - 1, Math.floor(s)))
    const u = clamp01(s - i)
    const h = this.anchors[i + 1] - this.anchors[i]
    const v = this.curves.map((c) => hermite(c.y[i], c.y[i + 1], c.m[i], c.m[i + 1], h, u))
    const A = K[i]
    const B = K[i + 1]
    return {
      eye: [v[0], v[1], v[2]], at: [v[3], v[4], v[5]], fov: v[6], sx: v[7], sy: v[8],
      t: lerp(A.t, B.t, u), ff: lerp(A.ff ?? 1, B.ff ?? 1, u), solid: lerp(A.solid ?? 0, B.solid ?? 0, u), i, u,
    }
  }

  /**
   * How much of a zone is present at this point of the route. A zone leaves in the first half of
   * the gap between two keys and the next one arrives in the second half, so two places are never
   * on screen together: each dissolves into the air it stands in.
   */
  presence(name, i, u) {
    const a = this.keys[i].z === name ? 1 : 0
    const b = this.keys[i + 1].z === name ? 1 : 0
    if (a === b) return a
    return a ? 1 - smooth((u - 0.1) / 0.36) : smooth((u - 0.54) / 0.36)
  }

  /** The hero can be turned by hand. It springs back as soon as you let go and scroll. */
  orbitBy(dx, dy) {
    this.orbit.yaw += dx * 0.0032
    this.orbit.pitch = Math.max(-0.1, Math.min(0.14, this.orbit.pitch + dy * 0.0012))
  }

  setOrbitDrag(on) {
    this.orbit.drag = on
  }

  measureSize() {
    const lvh = this.el.lvh.offsetHeight || window.innerHeight
    this.cssW = window.innerWidth
    this.cssH = lvh
    const dprMax = this.world ? Math.min(window.devicePixelRatio || 1, 2) : 1
    // A fixed budget of pixels, so a 1440p phone doesn't pay for detail nobody can see.
    const budget = this.cssW < 900 ? 2.3e6 : 3.8e6
    const fit = Math.sqrt(budget / (this.cssW * lvh))
    this.dpr = Math.min(dprMax, fit) * this.rungs[this.rung]
    const c = this.el.canvas
    const w = Math.round(this.cssW * this.dpr)
    const h = Math.round(lvh * this.dpr)
    if (c.width !== w || c.height !== h) {
      c.width = w
      c.height = h
    }
  }

  drawStars() {
    const size = 512
    const c = document.createElement('canvas')
    c.width = size
    c.height = size
    const x = c.getContext('2d')
    let seed = 7
    const R = () => ((seed = (seed * 16807) % 2147483647) / 2147483647)
    for (let i = 0; i < 260; i += 1) {
      const big = R() < 0.1
      const s = big ? 2 : 1
      const a = big ? 0.95 : 0.25 + R() * 0.55
      x.fillStyle = R() < 0.14 ? `rgba(255,214,170,${a})` : `rgba(214,226,255,${a})`
      x.fillRect(Math.floor(R() * size), Math.floor(R() * size * 0.72), s, s)
    }
    this.el.stars.style.backgroundImage = `url(${c.toDataURL('image/png')})`
  }

  /** `roll` in radians: the sky tips with the camera, about the point where the horizon crosses the middle. */
  paintSky(day, horizon, roll) {
    const ctx = this.skyCtx
    const h = this.el.sky.height
    const g = ctx.createLinearGradient(0, 0, 0, h)
    const [a, b, c, d] = skyStops(day)
    // A screen is a third of this canvas, so the stops crowd around the horizon band at 53 %.
    g.addColorStop(0, a)
    g.addColorStop(0.31, a)
    g.addColorStop(0.405, b)
    g.addColorStop(0.485, c)
    g.addColorStop(0.525, d)
    g.addColorStop(0.55, d)
    g.addColorStop(0.62, c)
    g.addColorStop(0.75, b)
    g.addColorStop(1, b)
    ctx.fillStyle = g
    ctx.fillRect(0, 0, this.el.sky.width, h)
    // the canvas is 300 lvh tall; its horizon band sits at 53 %
    const y = horizon * this.cssH - this.cssH * 3 * 0.53
    const tip = Math.abs(roll) > 0.0004 ? ` rotate(${(-roll * 57.29578).toFixed(3)}deg)` : ''
    this.el.sky.style.transform = `translate3d(0,${y.toFixed(1)}px,0)${tip}`
  }

  kick() {
    if (!this.raf) {
      this.last = performance.now()
      this.raf = requestAnimationFrame(this.frame)
    }
  }

  frame(now) {
    this.raf = 0
    if (document.hidden || SHOT) return
    const dt = Math.min(80, now - this.last)
    this.last = now
    this.time += dt / 1000
    if (this.active) {
      this.scroll += (window.scrollY - this.scroll) * (1 - Math.exp(-dt / 110))
      this.step(now, dt)
      // stills cost nothing while nobody scrolls
      const settled = this.mode === 'posters' && Math.abs(window.scrollY - this.scroll) < 0.5 && Math.abs((this.posterData?.[this.portrait ? 'p' : 'l']?.[this.posterWanted]?.horizon ?? 0.56) - (this.posterHorizon ?? 0)) < 0.001
      if (!settled) this.raf = requestAnimationFrame(this.frame)
    } else {
      this.restFrame(dt)
    }
  }

  /** Inner pages: the sky settles at night, then nothing runs at all. */
  restFrame(dt) {
    const target = 0.8
    this.t += (target - this.t) * (1 - Math.exp(-dt / 260))
    const day = dayAt(this.t)
    this.paintSky(day, 0.56, 0)
    this.applyCelestial(day, null)
    this.setTone('dark')
    if (Math.abs(target - this.t) > 0.001) this.raf = requestAnimationFrame(this.frame)
  }

  applyCelestial(day, view) {
    const el = this.el
    const w = this.world
    if (view && w) {
      const p = w.projectDir(day.sunPos)
      const up = day.sunPos[1] > -0.12 && day.sunWarm
      if (p && up) {
        el.sun.style.transform = `translate3d(${(p[0] * this.cssW).toFixed(1)}px,${(p[1] * this.cssH).toFixed(1)}px,0)`
        el.sun.style.opacity = '1'
      } else el.sun.style.opacity = '0'
      const mp = !day.sunWarm && day.stars > 0.1 ? w.projectDir(day.moonPos) : null
      if (mp) {
        el.moon.style.transform = `translate3d(${(mp[0] * this.cssW).toFixed(1)}px,${(mp[1] * this.cssH).toFixed(1)}px,0)`
        el.moon.style.opacity = '1'
      } else el.moon.style.opacity = '0'
      const yaw = Math.atan2(view.at[0] - view.eye[0], view.at[2] - view.eye[2])
      el.stars.style.backgroundPosition = `${(yaw * 300).toFixed(0)}px 0`
    } else {
      el.sun.style.opacity = '0'
      el.moon.style.opacity = '0'
    }
    el.stars.style.opacity = day.stars.toFixed(3)
  }

  setTone(tone) {
    if (tone !== this.tone) {
      this.tone = tone
      document.documentElement.dataset.tone = tone
      this.state = { ...this.state, tone }
      this.emit()
    }
  }

  step(now, dt) {
    if (!this.keys.length) {
      this.remeasure()
      if (!this.keys.length) return
    }
    const c = this.peeking || this.sample(this.locate(this.scroll))
    // t runs with the camera, except right after arriving from an inner page
    const dtT = 1 - Math.exp(-dt / 180)
    this.t = Math.abs(c.t - this.t) > 0.25 && !SHOT ? lerp(this.t, c.t, dtT) : c.t
    const day = dayAt(this.t)
    day.fog *= c.ff
    day.mist *= c.ff

    // which place this is, and how much of it
    const zoneName = this.keys[c.u < 0.5 ? c.i : c.i + 1].z
    const zone = c.zone ? { ...ZONES[zoneName], ...c.zone } : ZONES[zoneName]
    const here = this.presence(zoneName, c.i, c.u)

    // what the user's hands add
    const o = this.orbit
    const atTop = 1 - smooth(this.scroll / (window.innerHeight * 0.75))
    if (!o.drag) {
      const back = 1 - Math.exp(-dt / (atTop > 0.5 ? 2400 : 300))
      o.yaw *= 1 - back
      o.pitch *= 1 - back
    }
    const pl = this.parallax
    pl.x += (pl.tx - pl.x) * (1 - Math.exp(-dt / 320))
    pl.y += (pl.ty - pl.y) * (1 - Math.exp(-dt / 320))
    let eye = c.eye
    const at = c.at
    // it is alive even when no one touches it: the camera breathes
    const still = SHOT ? 0 : 1
    const yaw = o.yaw * atTop + (pl.x * 0.022 + Math.sin(this.time * 0.13) * (0.006 + 0.012 * atTop)) * still
    const pitch = o.pitch * atTop
    const dx = eye[0] - at[0], dz = eye[2] - at[2]
    const cs = Math.cos(yaw), sn = Math.sin(yaw)
    const dist = Math.hypot(dx, dz)
    eye = [at[0] + dx * cs - dz * sn, eye[1] + dist * pitch - pl.y * 7 * still, at[2] + dx * sn + dz * cs]

    // it banks into its turns and levels out when the scrolling stops
    const head = Math.atan2(c.at[0] - c.eye[0], c.eye[2] - c.at[2])
    if (this.heading !== null && !SHOT && dt > 0) {
      let turn = head - this.heading
      if (turn > Math.PI) turn -= Math.PI * 2
      if (turn < -Math.PI) turn += Math.PI * 2
      const want = Math.max(-0.05, Math.min(0.05, (turn / dt) * 1000 * 0.12)) // radians of bank per rad/s of turn
      this.roll += (want - this.roll) * (1 - Math.exp(-dt / 420))
    }
    this.heading = head
    const roll = SHOT ? c.roll || 0 : this.roll

    const view = { eye, at, fov: c.fov, shiftX: -c.sx, shiftY: -c.sy, roll, clouds: c.clouds || this.cloudsFor(zoneName, here) }
    this.setTone(day.tone)

    // the veil: the moment the camera is inside a cloud layer, the screen is cloud
    const veil = this.veilAt(eye[1], day, view.clouds)
    if (veil !== this.veilLast) {
      this.veilLast = veil
      this.el.veil.style.opacity = veil.toFixed(3)
      this.el.veil.style.visibility = veil > 0.001 ? 'visible' : 'hidden'
    }

    const w = this.world
    if (w && this.mode === 'live' && w.meshes.length) {
      if (!SHOT && (this.cssW !== window.innerWidth)) this.measureSize()
      const r = SHOT ? 1 : clamp01((now - this.introStart) / 3800)
      const e = 1 - Math.pow(1 - r, 3)
      this.intro = e
      for (const m of w.meshes) {
        m.fade = this.presence(m.name, c.i, c.u)
        m.visible = m.fade > 0.004
      }
      const dusk = clamp01(day.emis * 1.4)
      w.render(view, day, this.time + 40, {
        intro: e,
        fogBoost: (1 - e) * 0.0046,
        haze: day.haze,
        solidAir: c.solid,
        mistY: zone.mistY,
        flock: zone.flock && here > 0.5 ? zone.flock : null,
        motes: {
          tint: zone.motes || [1, lerp(0.96, 0.74, dusk), lerp(0.84, 0.38, dusk)],
          alpha: lerp(0.3, 0.85, dusk) * here * e,
          wind: zone.wind || [0.5, 0.06, 0.22],
        },
      })
      this.paintSky(day, w.horizonY(view) ?? 0.56, roll)
      this.applyCelestial(day, view)
      if (!SHOT) this.govern(dt, now)
    } else if (this.mode === 'posters') {
      this.stepPosters(day, dt)
    } else {
      this.paintSky(day, 0.56, 0)
      this.applyCelestial(day, null)
    }
    this.lastView = view
  }

  /** The cloud layers of this place. Ones marked with a zone only exist there. */
  cloudsFor(zoneName, here) {
    return CLOUDS.map((layer) => (layer.zone ? { ...layer, alpha: layer.zone === zoneName ? layer.alpha * here : 0 } : layer)).filter((layer) => layer.alpha > 0.004)
  }

  veilAt(y, day, layers) {
    let k = 0
    for (const layer of layers) k = Math.max(k, (1 - smooth(Math.abs(y - layer.y) / layer.thick)) * layer.veil * Math.min(1, layer.alpha * 2))
    this.el.veil.style.background = `radial-gradient(120% 90% at 50% 50%, ${day.veilA}, ${day.veilB})`
    return k
  }

  /** Slower devices lose pixels, then the 3D, never the page. */
  govern(dt, now) {
    if (now - this.introStart < 2500) return
    this.frameMs += (dt - this.frameMs) * 0.05
    if (this.frameMs > 34) {
      this.slow += dt
      if (this.slow > 1800) {
        this.slow = 0
        this.frameMs = 20
        if (this.rung < this.rungs.length - 1) {
          this.rung += 1
          this.measureSize()
        } else {
          this.fallBack()
        }
      }
    } else {
      this.slow = Math.max(0, this.slow - dt * 2)
    }
  }

  /** Test hook: jump to a scroll position and paint it right now. */
  snap(y) {
    window.scrollTo({ top: y, behavior: 'instant' })
    this.scroll = y
    this.remeasure()
    this.peeking = null
    this.t = this.sample(this.locate(y)).t
    this.step(performance.now(), 16)
    this.world?.gl.finish()
    return { t: this.t, stats: this.world ? { ...this.world.stats } : null, keys: this.keys.length }
  }

  /** Test hook: paint an arbitrary view, to compose a frame before it becomes a key. `z` names its zone. */
  peek(view) {
    this.remeasure()
    const i = Math.max(0, this.keys.findIndex((k) => k.z === (view.z || 'isles')))
    this.peeking = { sx: 0, sy: 0, fov: 40, t: 0, ff: 1, solid: 0, ...view, i: Math.min(i, this.keys.length - 2), u: i >= this.keys.length - 1 ? 1 : 0 }
    this.t = this.peeking.t
    this.step(performance.now(), 16)
    this.world?.gl.finish()
    this.peeking = null
  }
}

export const stage = new Stage()
if (typeof window !== 'undefined' && SHOT) window.__stage = stage
