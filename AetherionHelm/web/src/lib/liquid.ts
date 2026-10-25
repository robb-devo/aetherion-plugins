// Liquid glass: Chromium (desktop, Android, the app's WebView) can run an SVG filter as a backdrop-filter, so
// whatever sits behind a glass surface is bent along a rounded bezel like the rim of a thick lens. Every glass
// element gets a displacement map for its own size and radius; CSS picks it up as var(--lq) and decides blur and
// tint itself. Safari and Firefox ignore url() backdrops and keep the frosted glass.

const ua = navigator.userAgent;
export const refraction = /Chrome\/|Chromium\//.test(ua) && !/CriOS|FxiOS|EdgiOS/.test(ua);

interface Lens {
  bezel: number; // px of the rim that bends light
  scale: number; // strongest shift in px at the very edge
}

const CHROME = ".sidebar, .tabbar, .topbar, .palette, .sheet, .toast, .menu, .auth-card";
const CARDS = ".glass";
const coarse = window.matchMedia("(pointer: coarse)");

function lensFor(el: Element): Lens {
  if (el.matches(".tabbar, .toast, .menu")) return { bezel: 20, scale: 64 };
  if (el.matches(".topbar")) return { bezel: 18, scale: 58 };
  if (el.matches(".sidebar, .palette, .sheet, .auth-card")) return { bezel: 28, scale: 70 };
  return { bezel: 20, scale: 34 };
}

let svg: SVGSVGElement | null = null;
const filters = new Map<string, string>(); // size key -> filter id
let seq = 0;

function defs(): SVGDefsElement {
  if (!svg) {
    svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("aria-hidden", "true");
    svg.setAttribute("width", "0");
    svg.setAttribute("height", "0");
    svg.style.cssText = "position:absolute;width:0;height:0;overflow:hidden;pointer-events:none";
    svg.appendChild(document.createElementNS("http://www.w3.org/2000/svg", "defs"));
    document.body.appendChild(svg);
  }
  return svg.firstChild as SVGDefsElement;
}

/** Signed distance to a rounded rectangle centred at the origin (negative inside). */
function sdf(x: number, y: number, hw: number, hh: number, r: number): number {
  const qx = Math.abs(x) - (hw - r);
  const qy = Math.abs(y) - (hh - r);
  const ox = Math.max(qx, 0);
  const oy = Math.max(qy, 0);
  return Math.hypot(ox, oy) + Math.min(Math.max(qx, qy), 0) - r;
}

/** RG displacement map at half resolution: neutral grey inside, vectors pointing inwards along the bezel. */
function map(w: number, h: number, r: number, lens: Lens): string {
  const k = 0.5;
  const W = Math.max(2, Math.ceil(w * k));
  const H = Math.max(2, Math.ceil(h * k));
  const c = document.createElement("canvas");
  c.width = W;
  c.height = H;
  const ctx = c.getContext("2d")!;
  const img = ctx.createImageData(W, H);
  const d = img.data;
  const hw = w / 2;
  const hh = h / 2;
  const rr = Math.min(r, hw, hh);
  const b = Math.min(lens.bezel, hw, hh);
  for (let j = 0; j < H; j++) {
    const y = (j + 0.5) / k - hh;
    for (let i = 0; i < W; i++) {
      const x = (i + 0.5) / k - hw;
      const o = (j * W + i) * 4;
      let vx = 0;
      let vy = 0;
      const s = sdf(x, y, hw, hh, rr);
      const depth = -s;
      if (depth >= 0 && depth < b) {
        // outward normal from the distance field's gradient
        const e = 0.75;
        const gx = sdf(x + e, y, hw, hh, rr) - sdf(x - e, y, hw, hh, rr);
        const gy = sdf(x, y + e, hw, hh, rr) - sdf(x, y - e, hw, hh, rr);
        const gl = Math.hypot(gx, gy) || 1;
        // convex rim: almost flat in the middle, steep at the edge
        const t = 1 - depth / b;
        const m = t * t * (3 - 2 * t) * t;
        vx = (-gx / gl) * m;
        vy = (-gy / gl) * m;
      }
      d[o] = 128 + vx * 127;
      d[o + 1] = 128 + vy * 127;
      d[o + 2] = 128;
      d[o + 3] = 255;
    }
  }
  ctx.putImageData(img, 0, 0);
  return c.toDataURL();
}

function filterFor(w: number, h: number, r: number, lens: Lens): string {
  const key = `${w}x${h}r${r}b${lens.bezel}s${lens.scale}`;
  const hit = filters.get(key);
  if (hit) return hit;
  const id = `lq${++seq}`;
  const ns = "http://www.w3.org/2000/svg";
  const f = document.createElementNS(ns, "filter");
  f.id = id;
  for (const [a, v] of Object.entries({ x: "0", y: "0", width: String(w), height: String(h), filterUnits: "userSpaceOnUse", primitiveUnits: "userSpaceOnUse", "color-interpolation-filters": "sRGB" })) f.setAttribute(a, v);
  const im = document.createElementNS(ns, "feImage");
  for (const [a, v] of Object.entries({ href: map(w, h, r, lens), x: "0", y: "0", width: String(w), height: String(h), preserveAspectRatio: "none", result: "map" })) im.setAttribute(a, v);
  const dm = document.createElementNS(ns, "feDisplacementMap");
  for (const [a, v] of Object.entries({ in: "SourceGraphic", in2: "map", scale: String(lens.scale), xChannelSelector: "R", yChannelSelector: "G" })) dm.setAttribute(a, v);
  f.append(im, dm);
  defs().appendChild(f);
  filters.set(key, id);
  return id;
}

const tracked = new Map<HTMLElement, string>(); // element -> size key it was given

/** Window resizes leave filters for sizes nobody has any more; drop them once there are many. */
function sweep() {
  if (filters.size < 120) return;
  const live = new Set<string>();
  tracked.forEach((_, el) => {
    const m = /url\(#(lq\d+)\)/.exec(el.style.getPropertyValue("--lq"));
    if (m) live.add(m[1]);
  });
  filters.forEach((id, key) => {
    if (live.has(id)) return;
    filters.delete(key);
    document.getElementById(id)?.remove();
  });
}

const ro = new ResizeObserver((entries) => {
  for (const en of entries) {
    const el = en.target as HTMLElement;
    if (!el.isConnected) {
      ro.unobserve(el);
      tracked.delete(el);
      continue;
    }
    const w = Math.round(el.offsetWidth / 4) * 4;
    const h = Math.round(el.offsetHeight / 4) * 4;
    if (w < 24 || h < 24 || w * h > 2_400_000) {
      el.style.removeProperty("--lq");
      continue;
    }
    const r = parseFloat(getComputedStyle(el).borderTopLeftRadius) || 0;
    const key = `${w}x${h}r${r}`;
    if (tracked.get(el) === key) continue;
    tracked.set(el, key);
    el.style.setProperty("--lq", `url(#${filterFor(w, h, r, lensFor(el))})`);
  }
  sweep();
});

function scan(root: ParentNode) {
  const sel = coarse.matches ? CHROME : `${CHROME}, ${CARDS}`;
  root.querySelectorAll<HTMLElement>(sel).forEach((el) => {
    if (tracked.has(el)) return;
    tracked.set(el, "");
    ro.observe(el);
  });
}

/** A soft light follows the pointer across the glass it hovers. */
function pointerLight() {
  if (!window.matchMedia("(hover: hover)").matches) return;
  let lit: HTMLElement | null = null;
  document.addEventListener(
    "pointermove",
    (e) => {
      const el = (e.target as Element | null)?.closest?.<HTMLElement>(".glass") ?? null;
      if (lit && lit !== el) {
        lit.style.removeProperty("--mx");
        lit.style.removeProperty("--my");
      }
      lit = el;
      if (!el) return;
      const r = el.getBoundingClientRect();
      el.style.setProperty("--mx", `${Math.round(e.clientX - r.left)}px`);
      el.style.setProperty("--my", `${Math.round(e.clientY - r.top)}px`);
    },
    { passive: true },
  );
}

export function initLiquid() {
  pointerLight();
  if (!refraction) return;
  document.documentElement.classList.add("lq");
  scan(document);
  let queued = false;
  new MutationObserver(() => {
    if (queued) return;
    queued = true;
    requestAnimationFrame(() => {
      queued = false;
      scan(document);
    });
  }).observe(document.body, { childList: true, subtree: true });
}
