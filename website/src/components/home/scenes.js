import { asset } from '../../lib/asset.js'

/**
 * The four little worlds behind the job mini-games. All of it is drawn here, pixel by pixel, on a small
 * canvas the browser scales up without smoothing. The only outside art is the tool itself: the game's own
 * item sprite. Backgrounds are painted once and kept; only what moves is drawn per frame.
 */
export const W = 200
export const H = 115

const seeded = (s) => () => ((s = (s * 16807) % 2147483647) / 2147483647)
const rect = (x, c, a, b, w, h) => {
  x.fillStyle = c
  x.fillRect(Math.round(a), Math.round(b), Math.round(w), Math.round(h))
}

function canvas(w = W, h = H) {
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  return c
}

const cache = new Map()
function once(key, paint, w, h) {
  let c = cache.get(key)
  if (!c) {
    c = canvas(w, h)
    paint(c.getContext('2d'), w ?? W, h ?? H)
    cache.set(key, c)
  }
  return c
}

const sprites = new Map()
function sprite(name) {
  let s = sprites.get(name)
  if (!s) {
    s = new Image()
    s.src = asset(`items/${name}.png`)
    sprites.set(name, s)
  }
  return s.complete && s.naturalWidth ? s : null
}

/** The tool in the player's hand: pivots at the grip, swings on a hit. */
function tool(x, name, px, py, angle, flip = false, size = 34) {
  const s = sprite(name)
  if (!s) return
  x.save()
  x.translate(Math.round(px), Math.round(py))
  x.rotate(angle)
  if (flip) x.scale(-1, 1)
  // item sprites run from the handle, bottom left, to the head, top right
  x.drawImage(s, -size * 0.14, -size * 0.86, size, size)
  x.restore()
}

const swingAngle = (at, now, rest, hit) => {
  if (!at) return rest
  const k = (now - at) / 260
  if (k >= 1) return rest
  return k < 0.28 ? rest + (hit - rest) * (k / 0.28) : hit + (rest - hit) * ((k - 0.28) / 0.72)
}

function skyBand(x, stops, w = W, h = H) {
  const g = x.createLinearGradient(0, 0, 0, h)
  stops.forEach(([at, c]) => g.addColorStop(at, c))
  x.fillStyle = g
  x.fillRect(0, 0, w, h)
}

function puff(x, R, cx, cy, n, spreadX, spreadY, col) {
  x.fillStyle = col
  for (let i = 0; i < n; i += 1) {
    const a = R() * 6.283
    const r = Math.sqrt(R())
    const s = 3 + Math.floor(R() * 5)
    x.fillRect(Math.round(cx + Math.cos(a) * r * spreadX - s / 2), Math.round(cy + Math.sin(a) * r * spreadY - s / 2), s, Math.max(2, s - 2))
  }
}

function pines(x, R, base, tall, step, col, w = W) {
  x.fillStyle = col
  for (let px = -4; px < w + 4; px += step + Math.floor(R() * step)) {
    const h = tall * (0.55 + R() * 0.55)
    const half = 3 + Math.floor(R() * 3)
    for (let y = 0; y < h; y += 1) {
      const wd = Math.max(1, Math.round((y / h) * half) - (y % 4 === 0 ? 1 : 0))
      x.fillRect(px - wd, base - h + y, wd * 2 + 1, 1)
    }
    x.fillRect(px - 20, base - 1, 40, 12)
  }
}

export function sparks(x, list, now) {
  for (const p of list) {
    const age = (now - p.t) / p.life
    if (age < 0 || age > 1) continue
    x.globalAlpha = 1 - age * age * age
    x.fillStyle = p.c
    const px = p.x + p.vx * age * p.life * 0.05
    const py = p.y + p.vy * age * p.life * 0.05 + age * age * (p.g ?? 18)
    x.fillRect(Math.round(px), Math.round(py), p.s, p.s)
  }
  x.globalAlpha = 1
}

function burst(list, a, b, colors, n, now, opts = {}) {
  for (let i = 0; i < n; i += 1) {
    list.push({
      x: a + (Math.random() - 0.5) * (opts.w ?? 6),
      y: b + (Math.random() - 0.5) * (opts.h ?? 6),
      vx: (Math.random() - 0.5) * (opts.vx ?? 1.2),
      vy: (opts.up ?? -0.25) - Math.random() * (opts.vy ?? 0.9),
      c: colors[Math.floor(Math.random() * colors.length)],
      s: Math.random() < 0.3 ? 2 : 1,
      t: now,
      life: (opts.life ?? 520) + Math.random() * 480,
      g: opts.g,
    })
  }
}

/* ----------------------------------------------------------------------------------- the forest */
const LOG = 11
const LOGS = 4

function logBlock(x, a, b, cut = false) {
  const R = seeded(a * 7 + b * 13 + 5)
  rect(x, '#6f4d2c', a, b, 18, LOG)
  for (let i = 0; i < 18; i += 1) {
    const d = R()
    rect(x, d < 0.3 ? '#5a3d21' : d < 0.55 ? '#7d5935' : '#6f4d2c', a + i, b, 1, LOG)
    if (R() < 0.3) rect(x, '#4b321a', a + i, b + Math.floor(R() * (LOG - 3)), 1, 3)
  }
  rect(x, '#8a6640', a, b, 1, LOG)
  rect(x, '#3f2a15', a + 17, b, 1, LOG)
  rect(x, '#3f2a15', a, b + LOG - 1, 18, 1)
  if (cut) {
    rect(x, '#b98f58', a + 2, b + 2, 14, LOG - 4)
    rect(x, '#a07a48', a + 5, b + 4, 8, LOG - 8)
    rect(x, '#c9a06a', a + 8, b + 5, 2, 2)
  }
}

function canopy(palette, seed) {
  return (x, w, h) => {
    const R = seeded(seed)
    const cx = w / 2
    const cy = h / 2 + 3
    const blobs = [[0, 0, 40, 25], [-22, 6, 22, 15], [22, 6, 22, 15], [-11, -11, 22, 13], [13, -12, 20, 12]]
    // dark mass first, then lit clumps on top: the shape reads from far away and has grain up close
    for (const [ox, oy, rx, ry] of blobs) {
      for (let i = 0; i < 260; i += 1) {
        const a = R() * 6.283
        const r = Math.sqrt(R())
        const px = cx + ox + Math.cos(a) * r * rx
        const py = cy + oy + Math.sin(a) * r * ry
        const s = 2 + Math.floor(R() * 2)
        x.fillStyle = palette[0]
        x.fillRect(Math.round(px), Math.round(py), s, s)
      }
    }
    for (const [ox, oy, rx, ry] of blobs) {
      for (let i = 0; i < 300; i += 1) {
        const a = R() * 6.283
        const r = Math.sqrt(R()) * 0.92
        const px = cx + ox + Math.cos(a) * r * rx
        const py = cy + oy + Math.sin(a) * r * ry
        const lit = (cy + oy + ry - py) / (2 * ry) // 1 at the top of a clump
        const n = lit + (R() - 0.5) * 0.5
        x.fillStyle = n > 0.82 ? palette[3] : n > 0.58 ? palette[2] : palette[1]
        const s = 1 + Math.floor(R() * 2)
        x.fillRect(Math.round(px), Math.round(py), s, s)
      }
    }
  }
}

const forage = {
  speed: 1.05, green: 0.2, gold: 0.06, goal: 3, tool: 'foraging_axe_5',
  draw(x, g, now) {
    x.drawImage(once('forest-bg', (c, w, h) => {
      const R = seeded(77)
      skyBand(c, [[0, '#6fb0e6'], [0.62, '#cfe9f3'], [1, '#f2f6e6']], w, h)
      puff(c, R, 42, 40, 26, 24, 5, '#f4fbff')
      puff(c, R, 160, 30, 22, 20, 4, '#eef8fd')
      pines(c, R, 90, 26, 7, '#9cc4c0', w)
      pines(c, R, 96, 30, 8, '#6d9e8d', w)
      pines(c, R, 102, 28, 9, '#44775d', w)
      // ground
      rect(c, '#6b4a2f', 0, 101, w, 14)
      for (let i = 0; i < w; i += 1) {
        const v = R()
        rect(c, v < 0.3 ? '#5c9e3f' : v < 0.7 ? '#67ad47' : '#78bd54', i, 99 + Math.floor(R() * 2), 1, 3 + Math.floor(R() * 3))
        if (R() < 0.34) rect(c, '#88cc60', i, 97 + Math.floor(R() * 3), 1, 2)
        if (R() < 0.3) rect(c, '#5a3d27', i, 106 + Math.floor(R() * 8), 2, 1)
      }
    }), 0, 0)
    const fall = g.fallAt ? Math.min(1, (now - g.fallAt) / 130) : 1
    const bounce = g.fallAt && fall === 1 ? Math.max(0, 1 - (now - g.fallAt - 130) / 120) * Math.abs(Math.sin((now - g.fallAt) / 26)) * 1.2 : 0
    const logs = LOGS - g.progress
    const ground = 100
    const cx = 91
    const lift = (1 - fall) * LOG + bounce
    for (let i = 0; i < logs; i += 1) logBlock(x, cx, ground - (i + 1) * LOG - lift)
    // stump roots stay on the ground
    rect(x, '#5a3d21', cx - 3, ground - 2, 24, 3)
    rect(x, '#5a3d21', cx - 6, ground, 30, 1)
    const top = ground - logs * LOG - lift
    const sway = Math.round(Math.sin(now / 900) * 1.2)
    const crown = once(g.won ? 'crown-gold' : 'crown', canopy(g.won ? ['#7a5a12', '#c9962a', '#efbe4a', '#ffe9a0'] : ['#1f4a24', '#2f6e33', '#47923f', '#74b95a'], 91), 110, 70)
    x.drawImage(crown, cx + 9 - 55 + sway, top - 51)
    if (g.logFly) {
      const k = (now - g.logFly.t) / 460
      if (k < 1) {
        x.save()
        x.translate(cx + 30 + k * 62, ground - 12 - Math.sin(k * 3.14) * 30 + k * 10)
        x.rotate(k * 7)
        logBlock(x, -9, -6, true)
        x.restore()
      }
    }
    tool(x, this.tool, cx + 50, ground - 4, swingAngle(g.fallAt, now, 0.35, -0.95), true)
  },
  onHit(g, now, list) {
    g.fallAt = now
    g.logFly = { t: now }
    burst(list, 100, 92, ['#b98f58', '#6f4d2c', '#c9a06a'], 12, now, { w: 10 })
    burst(list, 100, 28, ['#2f6e33', '#47923f', '#74b95a'], 12, now, { w: 60, h: 18, up: 0.1, vy: 0.1, g: 60, life: 900 })
  },
  onWin(g, now, list) {
    burst(list, 100, 36, ['#ffdc85', '#efbe4a', '#fff7cf', '#ffffff'], 70, now, { w: 70, h: 30, vx: 1.6, vy: 1.3, g: 34, life: 900 })
  },
}

/* ----------------------------------------------------------------------------------- the field */
const WHEAT = ['#7a5f1c', '#b18f2c', '#dcbd4e', '#f6e38a']
const GOLD = ['#a8741a', '#e0a92f', '#ffd65c', '#fff3b8']

function plant(x, a, base, h, sway, ramp) {
  for (const [ox, dh, top] of [[0, 0, 1], [3, -4, 0], [6, -1, 1], [9, -5, 0], [12, -2, 1]]) {
    const stem = h + dh
    rect(x, ramp[1], a + ox, base - stem, 2, stem)
    rect(x, ramp[0], a + ox + 1, base - stem + 3, 1, stem - 3)
    const lean = sway * (ox % 2 ? -1 : 1)
    // the ear: a fat little column with a bright edge and whiskers
    rect(x, ramp[2], a + ox - 1 + lean, base - stem - 8, 3, 9)
    rect(x, ramp[3], a + ox - 1 + lean, base - stem - 8, 1, 6 + top)
    rect(x, ramp[0], a + ox + 1 + lean, base - stem - 3, 1, 3)
    rect(x, ramp[3], a + ox + lean, base - stem - 11, 1, 3)
  }
}

const farm = {
  speed: 1.18, green: 0.24, gold: 0.08, goal: 5, tool: 'farming_hoe_5',
  draw(x, g, now) {
    x.drawImage(once('field-bg', (c, w, h) => {
      const R = seeded(31)
      skyBand(c, [[0, '#7dc0ea'], [0.55, '#d6edf1'], [0.8, '#fbeec9'], [1, '#f6dca0']], w, h)
      puff(c, R, 60, 30, 22, 26, 4, '#ffffff')
      // low sun
      rect(c, '#fff7cf', 150, 34, 15, 15)
      rect(c, '#fff0b0', 148, 36, 19, 11)
      // hills
      for (const [base, col, amp, f] of [[78, '#a9c982', 7, 0.035], [84, '#8db766', 5, 0.05]]) {
        for (let i = 0; i < w; i += 1) {
          const top = Math.round(base + Math.sin(i * f + base) * amp + Math.sin(i * f * 2.7) * 2)
          rect(c, col, i, top, 1, h - top)
        }
      }
      // barn and mill on the far hill
      rect(c, '#a33c2e', 24, 66, 22, 14)
      rect(c, '#8a2f24', 24, 76, 22, 4)
      c.fillStyle = '#6d2a22'
      for (let i = 0; i < 12; i += 1) c.fillRect(24 + i - 1, 66 - i, 24 - i * 2 + 2, 1)
      rect(c, '#f1e5c8', 33, 71, 5, 9)
      rect(c, '#d9d2c0', 166, 62, 8, 20)
      rect(c, '#b9b1a0', 166, 62, 2, 20)
      c.fillStyle = '#7d5a3a'
      c.fillRect(165, 60, 10, 3)
      // fence
      for (let i = 56; i < 150; i += 8) rect(c, '#8a6a42', i, 76, 1, 6)
      rect(c, '#8a6a42', 56, 78, 94, 1)
      // the standing field behind the row you cut
      for (let i = -2; i < w; i += 3) {
        const v = R()
        rect(c, v < 0.5 ? '#c9b257' : '#bba34a', i, 83 + Math.floor(R() * 2), 3, 6)
        rect(c, '#e2cf7f', i + 1, 82 + Math.floor(R() * 2), 1, 2)
      }
      // a strip of grass and bare earth between that field and the row in front
      rect(c, '#7f9a48', 0, 89, w, 4)
      for (let i = 0; i < w; i += 1) if (R() < 0.5) rect(c, '#6d8a3d', i, 89 + Math.floor(R() * 4), 1, 2)
      rect(c, '#6a4a2c', 0, 93, w, 8)
      for (let i = 0; i < 90; i += 1) rect(c, '#5a3d24', Math.floor(R() * w), 93 + Math.floor(R() * 8), 2, 1)
      // soil and furrows
      rect(c, '#5a3d24', 0, 100, w, 15)
      for (let row = 102; row < h; row += 4) rect(c, '#42291a', 0, row, w, 1)
      for (let i = 0; i < 80; i += 1) rect(c, '#6b4a2e', Math.floor(R() * w), 100 + Math.floor(R() * 15), 2, 1)
    }), 0, 0)
    const ramp = g.won ? GOLD : WHEAT
    const count = 11
    const cut = (g.progress / this.goal) * count
    const base = 101
    for (let i = 0; i < count; i += 1) {
      const a = 12 + i * 16
      const sway = Math.sin(now / 460 + i * 0.7) > 0.45 ? 1 : 0
      if (i < cut) {
        for (const ox of [0, 3, 6, 9, 12]) rect(x, g.won ? '#c98f1e' : '#9a7c2a', a + ox, base - 3 - (ox % 2), 2, 3 + (ox % 2))
      } else {
        plant(x, a, base, 20, sway, ramp)
      }
    }
    if (g.sheaf) {
      const k = (now - g.sheaf.t) / 520
      if (k < 1) {
        const a = g.sheaf.x + k * 12
        const b = 74 - Math.sin(k * 3.14) * 26 + k * 6
        rect(x, '#b18f2c', a, b, 5, 10)
        rect(x, '#f6e38a', a, b, 5, 3)
        rect(x, '#7a5f1c', a, b + 5, 5, 1)
      }
    }
    const reach = 12 + Math.min(count, cut) * 16
    tool(x, this.tool, reach - 18, base + 2, swingAngle(g.cutAt, now, -0.55, 0.75))
  },
  onHit(g, now, list) {
    g.cutAt = now
    const count = 11
    const i = Math.max(0, Math.floor(((g.progress - 0.5) / this.goal) * count))
    g.sheaf = { t: now, x: 13 + i * 16 }
    burst(list, 13 + i * 16 + 4, 80, ['#dcbd4e', '#f6e38a', '#b18f2c'], 16, now, { w: 14, h: 14 })
  },
  onWin(g, now, list) {
    burst(list, 100, 70, ['#ffd65c', '#fff3b8', '#ffffff'], 70, now, { w: 180, h: 16, vx: 0.4, vy: 1.3, g: 28, life: 1000 })
  },
}

/* ----------------------------------------------------------------------------------- the water */
const fish = {
  speed: 1.5, green: 0.2, gold: 0.06, goal: 3, patience: true, tool: 'fishing_rod_5',
  draw(x, g, now) {
    x.drawImage(once('water-bg', (c, w, h) => {
      const R = seeded(23)
      skyBand(c, [[0, '#6aa9e0'], [0.34, '#bfe0ee'], [0.5, '#f4eedc']], w, h)
      puff(c, R, 150, 22, 24, 26, 4, '#ffffff')
      puff(c, R, 36, 34, 14, 18, 3, '#f7fbff')
      // an island far off, with a jetty: you are not the only one out here
      rect(c, '#7fa6a4', 18, 49, 46, 8)
      for (let i = 0; i < 30; i += 1) rect(c, '#6a9490', 20 + Math.floor(R() * 40), 42 + Math.floor(R() * 7), 3, 8)
      rect(c, '#8db3b2', 60, 54, 24, 2)
      rect(c, '#8db3b2', 170, 52, 30, 6)
    }), 0, 0)
    const horizon = 56
    const warm = Math.min(1, g.progress / this.goal)
    // water: three bands of colour, warming as the streak grows
    const mix = (a, b, k) => a.map((v, i) => Math.round(v + (b[i] - v) * k))
    const top = mix([86, 170, 200], [150, 208, 170], warm)
    const low = mix([18, 70, 124], [28, 112, 128], warm)
    for (let i = 0; i < 9; i += 1) {
      const k = i / 8
      const c = mix(top, low, Math.pow(k, 0.8))
      x.fillStyle = `rgb(${c[0]},${c[1]},${c[2]})`
      x.fillRect(0, horizon + Math.round(k * k * 60), W, H - horizon)
    }
    // glitter and swell
    for (let i = 0; i < 26; i += 1) {
      const d = i * 37.7
      const lane = i % 7
      const speed = 60 + lane * 26
      const a = (d + now / speed) % (W + 30) - 15
      const y = horizon + 3 + lane * 8 + (i % 3)
      const pale = warm > 0.5 ? 'rgba(255,226,150,.7)' : 'rgba(225,245,255,.42)'
      rect(x, pale, a, y, 5 + (i % 4) * 4, 1)
    }
    // the shape of what is under you
    const shade = 'rgba(8,30,60,.32)'
    for (let i = 0; i < 3; i += 1) {
      const a = (W + 40 - ((now / (130 + i * 50) + i * 90) % (W + 80))) - 20
      const b = 86 + i * 7
      rect(x, shade, a, b, 16 - i * 2, 4)
      rect(x, shade, a + 16 - i * 2, b - 1, 3, 6)
    }
    const bite = g.mode === 'reel'
    const bob = bite ? 3 + Math.sin(now / 45) * 2.5 : Math.sin(now / 520)
    const fx = 118
    const fy = horizon + 12 + bob
    // ripples
    const ring = (now / 700) % 1
    x.strokeStyle = `rgba(255,255,255,${(1 - ring) * 0.55})`
    x.strokeRect(fx - ring * 16, horizon + 15, ring * 32 + 2, 1)
    if (bite) {
      rect(x, 'rgba(255,255,255,.7)', fx - 9, horizon + 14, 20, 1)
    }
    // the rod is the player's hand: the line leaves from its tip
    const rest = swingAngle(g.pullAt, now, 0, -0.34) + (bite ? 0.1 + Math.sin(now / 60) * 0.02 : 0)
    const rod = 58
    const vx = rod * 0.78
    const vy = -rod * 0.74
    const tipX = 4 + vx * Math.cos(rest) - vy * Math.sin(rest)
    const tipY = 112 + vx * Math.sin(rest) + vy * Math.cos(rest)
    x.strokeStyle = 'rgba(30,34,60,.85)'
    x.lineWidth = 1
    x.beginPath()
    x.moveTo(tipX, tipY)
    x.quadraticCurveTo((tipX + fx) / 2, bite ? tipY + 18 : tipY + 34, fx + 1, fy)
    x.stroke()
    rect(x, '#e24a4a', fx - 1, fy - 3, 4, 3)
    rect(x, '#ffffff', fx - 1, fy, 4, 2)
    rect(x, '#10183a', fx, fy - 5, 1, 2)
    tool(x, this.tool, 4, 112, rest, false, rod)
    if (g.fishUp) {
      const k = (now - g.fishUp.t) / 680
      if (k < 1) {
        const a = fx - k * 88
        const b = horizon + 10 - Math.sin(k * 3.14) * 46
        const c = g.fishUp.gold ? ['#ffcf4e', '#fff0a8', '#c98f1e'] : ['#a8cfe0', '#e4f4fa', '#6f9fb6']
        rect(x, c[0], a, b, 15, 6)
        rect(x, c[1], a + 2, b, 11, 2)
        rect(x, c[2], a + 15, b - 2, 4, 3)
        rect(x, c[2], a + 15, b + 5, 4, 3)
        rect(x, c[2], a + 6, b - 2, 4, 2)
        rect(x, '#10183a', a + 2, b + 2, 1, 1)
      }
    }
  },
  onHit(g, now, list, gold) {
    g.fishUp = { t: now, gold }
    g.pullAt = now
    burst(list, 118, 66, ['#ffffff', '#cfe9f4', '#8fc6e0'], 26, now, { w: 18, h: 3, vx: 1.2, vy: 1.3, g: 40 })
  },
  onWin(g, now, list) {
    burst(list, 100, 60, ['#ffdc85', '#ff9d5c', '#ffffff'], 60, now, { w: 170, h: 10, vx: 0.5, vy: 0.9, g: 16, life: 1100 })
  },
}

/* ----------------------------------------------------------------------------------- the rock */
const B = 18 // a block of the rock face
const FACE = { x: 37, y: 22, cols: 7, rows: 4 }
const VEIN = [17, 10, 18, 11, 24] // the order you dig along the vein

function stoneBlock(x, a, b, seed) {
  const R = seeded(seed * 97 + 11)
  rect(x, '#5e6068', a, b, B, B)
  for (let i = 0; i < 70; i += 1) rect(x, ['#51535b', '#6a6c75', '#474950', '#74767f'][Math.floor(R() * 4)], a + Math.floor(R() * B), b + Math.floor(R() * B), 1 + Math.floor(R() * 2), 1)
  rect(x, '#393b42', a, b + B - 1, B, 1)
  rect(x, '#393b42', a + B - 1, b, 1, B)
  rect(x, '#777a84', a, b, B - 1, 1)
}

function crystal(x, a, b, h, ramp) {
  rect(x, ramp[0], a, b - h, 3, h)
  rect(x, ramp[1], a, b - h, 1, h)
  rect(x, ramp[2], a + 1, b - h - 1, 1, 2)
}

const AME = ['#8456d6', '#c9a8ff', '#f1e4ff']

function oreBlock(x, a, b, seed, now) {
  stoneBlock(x, a, b, seed)
  const R = seeded(seed * 31 + 3)
  for (let i = 0; i < 5; i += 1) crystal(x, a + 2 + Math.floor(R() * 12), b + 15 - Math.floor(R() * 6), 4 + Math.floor(R() * 6), AME)
  if ((now + seed * 300) % 1500 < 120) rect(x, '#ffffff', a + 3 + (seed % 11), b + 3 + (seed % 9), 1, 1)
}

const mine = {
  speed: 1.28, green: 0.2, gold: 0.07, goal: 5, tool: 'mining_pickaxe_5',
  draw(x, g, now) {
    x.drawImage(once('mine-bg', (c, w, h) => {
      const R = seeded(41)
      rect(c, '#14111d', 0, 0, w, h)
      // the far wall, picked out by lantern light
      for (let i = 0; i < 900; i += 1) {
        const a = R() * w
        const b = R() * h
        const d = Math.hypot((a - 100) / 110, (b - 58) / 64)
        if (d > 1) continue
        c.fillStyle = `rgba(${90 + R() * 40},${84 + R() * 30},${92 + R() * 30},${0.5 * (1 - d)})`
        c.fillRect(a, b, 3, 2)
      }
      // floor
      rect(c, '#231e29', 0, 98, w, 17)
      for (let i = 0; i < 120; i += 1) rect(c, '#312b37', Math.floor(R() * w), 98 + Math.floor(R() * 17), 2 + Math.floor(R() * 4), 1)
      // rails
      rect(c, '#6e5a44', 0, 108, w, 1)
      rect(c, '#6e5a44', 0, 112, w, 1)
      for (let i = 4; i < w; i += 9) rect(c, '#4a3826', i, 106, 3, 9)
      // timber: two posts and a beam, the frame of the shot
      const wood = (a, b, wd, ht) => {
        rect(c, '#5d4227', a, b, wd, ht)
        for (let i = 0; i < wd * ht * 0.3; i += 1) rect(c, '#4b331c', a + Math.floor(R() * wd), b + Math.floor(R() * ht), 1, 3)
      }
      wood(14, 12, 13, 92)
      rect(c, '#7a5a36', 14, 12, 2, 92)
      wood(173, 12, 13, 92)
      rect(c, '#7a5a36', 173, 12, 2, 92)
      wood(8, 10, 184, 9)
      rect(c, '#7a5a36', 8, 10, 184, 2)
    }), 0, 0)
    const order = VEIN
    const broken = new Set(order.slice(0, g.progress))
    for (let r = 0; r < FACE.rows; r += 1) {
      for (let c = 0; c < FACE.cols; c += 1) {
        const i = r * FACE.cols + c
        const a = FACE.x + c * B
        const b = FACE.y + r * B
        if (broken.has(i)) {
          rect(x, '#0c0a13', a, b, B, B)
          const pulse = 0.5 + Math.sin(now / 240 + i) * 0.25
          rect(x, `rgba(140,90,230,${pulse * 0.42})`, a + 1, b + 1, B - 2, B - 2)
          continue
        }
        if (order.includes(i)) oreBlock(x, a, b, i, now)
        else stoneBlock(x, a, b, i)
      }
    }
    // cracks on the block you are at, deepening just after each hit
    if (!g.won) {
      const i = order[Math.min(g.progress, order.length - 1)]
      const a = FACE.x + (i % FACE.cols) * B
      const b = FACE.y + Math.floor(i / FACE.cols) * B
      const on = now % 760 < 420
      x.strokeStyle = on ? '#ffdc85' : 'rgba(255,220,133,.4)'
      x.strokeRect(a + 0.5, b + 0.5, B - 1, B - 1)
    } else {
      // the geode opens: light spills out and a cluster stands in the dark
      const pulse = 0.7 + Math.sin(now / 200) * 0.3
      x.globalCompositeOperation = 'lighter'
      const light = x.createRadialGradient(100, 58, 2, 100, 58, 60)
      light.addColorStop(0, `rgba(190,130,255,${0.65 * pulse})`)
      light.addColorStop(1, 'rgba(120,70,220,0)')
      x.fillStyle = light
      x.fillRect(30, 10, 140, 96)
      x.globalCompositeOperation = 'source-over'
      for (let k = 0; k < 16; k += 1) crystal(x, 74 + (k * 37) % 54, 84 - (k * 13) % 26, 8 + (k * 7) % 12, AME)
    }
    // lantern and its pool of warm light
    const flick = 0.92 + Math.sin(now / 90) * 0.04 + Math.sin(now / 37) * 0.04
    x.globalCompositeOperation = 'lighter'
    const lamp = x.createRadialGradient(34, 32, 2, 34, 32, 58)
    lamp.addColorStop(0, `rgba(255,186,110,${0.5 * flick})`)
    lamp.addColorStop(1, 'rgba(255,150,70,0)')
    x.fillStyle = lamp
    x.fillRect(0, 0, 110, 96)
    x.globalCompositeOperation = 'source-over'
    rect(x, '#2c2c34', 29, 19, 9, 2)
    rect(x, '#2c2c34', 30, 21, 7, 12)
    rect(x, '#ffcf7a', 31, 22, 5, 9)
    rect(x, '#fff2c4', 33, 24, 2, 5)
    tool(x, this.tool, 170, 104, swingAngle(g.hitAt, now, -0.25, -1.05), true, 40)
  },
  onHit(g, now, list) {
    g.hitAt = now
    const i = VEIN[g.progress - 1]
    burst(list, FACE.x + (i % FACE.cols) * B + 9, FACE.y + Math.floor(i / FACE.cols) * B + 9, ['#c9a8ff', '#f1e4ff', '#8456d6', '#74767f'], 22, now, { w: 12, h: 12, vx: 1.6, vy: 1, g: 34 })
  },
  onWin(g, now, list) {
    burst(list, 100, 56, ['#f1e4ff', '#c9a8ff', '#ffffff'], 70, now, { w: 60, h: 36, vx: 1.7, vy: 1.3, g: 26, life: 1100 })
  },
}

export const GAMES = { forage, farm, fish, mine }
