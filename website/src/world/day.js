/**
 * One day over the islands. The whole page is this day: every colour in the sky,
 * on the stone and in the type comes from one of these keys.
 *
 * sky: top → upper → lower → horizon stops of the CSS gradient behind the canvas.
 * sunW: how much of each baked sun (dawn, dusk, high) falls on the world.
 */
export const DAY = [
  { name: 'dawn', at: 0.0, tone: 'light',
    sky: ['#2b3a78', '#8d6fa8', '#f0a58a', '#ffdcae'],
    sunW: [1, 0, 0], sunCol: [2.9, 1.75, 1.05], ambUp: [0.36, 0.37, 0.62], ambDn: [0.38, 0.27, 0.3], block: [1.5, 0.92, 0.45],
    emis: 0.35, fog: 0.00125, mist: 0.75, cloudLit: [1.0, 0.82, 0.74], cloudDark: [0.52, 0.42, 0.62], exposure: 0.74, sunPos: [0.85, 0.14, -0.42], stars: 0.0 },
  { name: 'morning', at: 0.12, tone: 'light',
    sky: ['#2f6db8', '#74aee6', '#c4e0f2', '#fdeed2'],
    sunW: [0.8, 0, 0.42], sunCol: [2.35, 2.0, 1.55], ambUp: [0.4, 0.5, 0.72], ambDn: [0.34, 0.3, 0.29], block: [0.8, 0.5, 0.25],
    emis: 0.0, fog: 0.00072, mist: 0.42, cloudLit: [1.0, 0.97, 0.92], cloudDark: [0.6, 0.67, 0.8], exposure: 0.62, sunPos: [0.72, 0.42, -0.2], stars: 0 },
  { name: 'noon', at: 0.26, tone: 'light',
    sky: ['#1b58ad', '#3f8cdd', '#93c9ef', '#e0f2fb'],
    sunW: [0.1, 0.1, 1], sunCol: [2.1, 2.0, 1.8], ambUp: [0.42, 0.53, 0.76], ambDn: [0.32, 0.3, 0.3], block: [0.4, 0.25, 0.12],
    emis: 0.0, fog: 0.0005, mist: 0.2, cloudLit: [1.0, 1.0, 1.0], cloudDark: [0.62, 0.72, 0.86], exposure: 0.58, sunPos: [0.22, 0.95, 0.26], stars: 0 },
  { name: 'afternoon', at: 0.38, tone: 'light',
    sky: ['#2a5aa8', '#6f93cf', '#d7bfae', '#ffd9a4'],
    sunW: [0, 0.85, 0.35], sunCol: [2.55, 1.95, 1.25], ambUp: [0.38, 0.45, 0.68], ambDn: [0.36, 0.3, 0.26], block: [0.5, 0.3, 0.14],
    emis: 0.0, fog: 0.00062, mist: 0.3, cloudLit: [1.0, 0.92, 0.8], cloudDark: [0.58, 0.58, 0.7], exposure: 0.62, sunPos: [-0.62, 0.5, -0.12], stars: 0 },
  { name: 'golden', at: 0.48, tone: 'dark',
    sky: ['#29356f', '#7b5a8e', '#ea8d62', '#ffc677'],
    sunW: [0, 1, 0.02], sunCol: [3.5, 1.9, 0.82], ambUp: [0.33, 0.35, 0.62], ambDn: [0.44, 0.28, 0.24], block: [1.4, 0.85, 0.4],
    emis: 0.22, fog: 0.00082, mist: 0.5, cloudLit: [1.0, 0.72, 0.52], cloudDark: [0.42, 0.32, 0.5], exposure: 0.82, sunPos: [-0.8, 0.16, -0.18], stars: 0 },
  { name: 'dusk', at: 0.56, tone: 'dark',
    sky: ['#101a4b', '#3b2a70', '#a3466f', '#f07f55'],
    sunW: [0, 0.55, 0], sunCol: [3.4, 1.2, 0.72], ambUp: [0.32, 0.36, 0.72], ambDn: [0.42, 0.24, 0.34], block: [2.6, 1.55, 0.7],
    emis: 0.66, fog: 0.00078, mist: 0.46, cloudLit: [0.82, 0.44, 0.46], cloudDark: [0.22, 0.17, 0.36], exposure: 0.95, sunPos: [-0.86, -0.02, -0.2], stars: 0.25 },
  { name: 'below', at: 0.63, tone: 'dark',
    sky: ['#04030b', '#07050f', '#120914', '#2a0f14'],
    sunW: [0, 0, 0.03], sunCol: [0.5, 0.45, 0.6], ambUp: [0.09, 0.08, 0.14], ambDn: [0.09, 0.05, 0.07], block: [1.35, 0.8, 0.36],
    emis: 1.0, fog: 0.0012, mist: 0, cloudLit: [0.14, 0.09, 0.16], cloudDark: [0.03, 0.02, 0.05], exposure: 1.0, sunPos: [0.2, -0.9, 0.2], stars: 0 },
  { name: 'below2', at: 0.71, tone: 'dark',
    sky: ['#04030b', '#07050f', '#120914', '#2a0f14'],
    sunW: [0, 0, 0.03], sunCol: [0.5, 0.45, 0.6], ambUp: [0.09, 0.08, 0.14], ambDn: [0.09, 0.05, 0.07], block: [1.35, 0.8, 0.36],
    emis: 1.0, fog: 0.0012, mist: 0, cloudLit: [0.14, 0.09, 0.16], cloudDark: [0.03, 0.02, 0.05], exposure: 1.0, sunPos: [0.2, -0.9, 0.2], stars: 0 },
  { name: 'night', at: 0.78, tone: 'dark',
    sky: ['#050a26', '#0a1540', '#14224f', '#26305e'],
    sunW: [0, 0, 0.34], sunCol: [0.55, 0.72, 1.3], ambUp: [0.14, 0.2, 0.42], ambDn: [0.07, 0.08, 0.17], block: [3.6, 2.15, 0.95],
    emis: 1.0, fog: 0.00052, mist: 0.28, cloudLit: [0.3, 0.38, 0.62], cloudDark: [0.05, 0.07, 0.17], exposure: 1.06, sunPos: [0.4, 0.82, 0.3], stars: 1 },
  { name: 'deep', at: 0.88, tone: 'dark',
    sky: ['#040820', '#0b1336', '#1c2350', '#3a3a6c'],
    sunW: [0.16, 0, 0.14], sunCol: [0.6, 0.72, 1.25], ambUp: [0.13, 0.17, 0.4], ambDn: [0.1, 0.09, 0.2], block: [3.4, 2.05, 0.95],
    emis: 0.95, fog: 0.00062, mist: 0.4, cloudLit: [0.34, 0.36, 0.6], cloudDark: [0.07, 0.07, 0.2], exposure: 1.04, sunPos: [0.7, -0.2, 0.62], stars: 0.75 },
  { name: 'dawn2', at: 1.0, tone: 'light',
    sky: ['#2b3a78', '#8d6fa8', '#f0a58a', '#ffdcae'],
    sunW: [1, 0, 0], sunCol: [2.9, 1.75, 1.05], ambUp: [0.36, 0.37, 0.62], ambDn: [0.38, 0.27, 0.3], block: [1.5, 0.92, 0.45],
    emis: 0.35, fog: 0.00125, mist: 0.75, cloudLit: [1.0, 0.82, 0.74], cloudDark: [0.52, 0.42, 0.62], exposure: 0.74, sunPos: [0.85, 0.14, -0.42], stars: 0 },
]

const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)]
/** sRGB to OKLab and back: between two skies the colours walk through colour, not through grey. */
const toLin = (c) => { const x = c / 255; return x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4 }
const toSrgb = (x) => { const c = x <= 0.0031308 ? 12.92 * x : 1.055 * x ** (1 / 2.4) - 0.055; return Math.max(0, Math.min(255, Math.round(c * 255))) }
function oklab([R, G, B]) {
  const r = toLin(R), g = toLin(G), b = toLin(B)
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
  return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s, 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s]
}
function fromLab([L, a, b]) {
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
  const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3
  return [toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s), toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s), toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s)]
}
const LAB = new Map()
const labOf = (h) => { let v = LAB.get(h); if (!v) { v = oklab(hex(h)); LAB.set(h, v) } return v }
const css = ([r, g, b]) => `rgb(${r},${g},${b})`

const lerp = (a, b, t) => a + (b - a) * t
const lerp3 = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)]
const smooth = (t) => t * t * (3 - 2 * t)

/** Interpolated state for t in 0..1. `index` / `mix` drive the CSS sky crossfade. */
export function dayAt(t) {
  const x = Math.max(0, Math.min(1, t))
  let i = 0
  while (i < DAY.length - 2 && x > DAY[i + 1].at) i += 1
  const a = DAY[i]
  const b = DAY[i + 1]
  const k = smooth((x - a.at) / (b.at - a.at))
  return {
    index: i,
    mix: k,
    tone: k < 0.5 ? a.tone : b.tone,
    sunW: lerp3(a.sunW, b.sunW, k),
    sunCol: lerp3(a.sunCol, b.sunCol, k),
    ambUp: lerp3(a.ambUp, b.ambUp, k),
    ambDn: lerp3(a.ambDn, b.ambDn, k),
    block: lerp3(a.block, b.block, k),
    emis: lerp(a.emis, b.emis, k),
    fog: lerp(a.fog, b.fog, k),
    mist: lerp(a.mist, b.mist, k),
    cloudLit: lerp3(a.cloudLit, b.cloudLit, k),
    cloudDark: lerp3(a.cloudDark, b.cloudDark, k),
    exposure: lerp(a.exposure, b.exposure, k),
    sunPos: lerp3(a.sunPos, b.sunPos, k),
    stars: lerp(a.stars, b.stars, k),
    moonPos: [-0.78, 0.46, 0.42],
    sunWarm: lerp(a.warm ?? (a.emis < 0.8 ? 1 : 0), b.warm ?? (b.emis < 0.8 ? 1 : 0), k) > 0.5,
    haze: hazeOf(a, b, k),
    veilA: css(fromLab(lerp3(labOf(a.sky[3]), labOf(b.sky[3]), k))),
    veilB: css(fromLab(lerp3(labOf(a.sky[2]), labOf(b.sky[2]), k))),
    a,
    b,
    k,
  }
}

/** Far fog tints towards the horizon's colour. */
function hazeOf(a, b, k) {
  const c = fromLab(lerp3(labOf(a.sky[3]), labOf(b.sky[3]), k))
  return [c[0] / 255, c[1] / 255, c[2] / 255]
}

/** The four sky colours of this moment. */
export function skyStops(day) {
  return [0, 1, 2, 3].map((i) => css(fromLab(lerp3(labOf(day.a.sky[i]), labOf(day.b.sky[i]), day.k))))
}

export function skyCss(key) {
  const [a, b, c, d] = key.sky
  return `linear-gradient(180deg, ${a} 0%, ${b} 26%, ${c} 44%, ${d} 50%, ${d} 56%, ${c} 72%, ${b} 100%)`
}
