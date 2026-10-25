import { useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { clock, dayShort, n0 } from "../lib/format";
import { t } from "../lib/i18n";

// Hand-drawn SVG charts: no chart library, so they look exactly like the rest of the app.

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [w, setW] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver((e) => setW(Math.floor(e[0].contentRect.width)));
    ro.observe(el);
    setW(el.clientWidth);
    return () => ro.disconnect();
  }, []);
  return [ref, w] as const;
}

/** Monotone cubic (Fritsch–Carlson) through the points; gaps (NaN) split the path. */
function smoothPath(pts: [number, number][]): string {
  const n = pts.length;
  if (n === 0) return "";
  if (n === 1) return `M${pts[0][0]},${pts[0][1]}`;
  const dx: number[] = [], dy: number[] = [], m: number[] = [];
  for (let i = 0; i < n - 1; i++) {
    dx[i] = pts[i + 1][0] - pts[i][0];
    dy[i] = pts[i + 1][1] - pts[i][1];
    m[i] = dx[i] === 0 ? 0 : dy[i] / dx[i];
  }
  const t: number[] = [m[0]];
  for (let i = 1; i < n - 1; i++) t[i] = m[i - 1] * m[i] <= 0 ? 0 : (3 * (dx[i - 1] + dx[i])) / ((2 * dx[i] + dx[i - 1]) / m[i - 1] + (dx[i] + 2 * dx[i - 1]) / m[i]);
  t[n - 1] = m[n - 2];
  let d = `M${pts[0][0]},${pts[0][1]}`;
  for (let i = 0; i < n - 1; i++) {
    const h = dx[i] / 3;
    d += `C${pts[i][0] + h},${pts[i][1] + h * t[i]} ${pts[i + 1][0] - h},${pts[i + 1][1] - h * t[i + 1]} ${pts[i + 1][0]},${pts[i + 1][1]}`;
  }
  return d;
}

function segments(xs: number[], ys: (number | null | undefined)[], sx: (v: number) => number, sy: (v: number) => number) {
  const out: [number, number][][] = [];
  let cur: [number, number][] = [];
  for (let i = 0; i < ys.length; i++) {
    const v = ys[i];
    if (v == null || !isFinite(v)) {
      if (cur.length) out.push(cur);
      cur = [];
    } else cur.push([sx(xs[i]), sy(v)]);
  }
  if (cur.length) out.push(cur);
  return out;
}

function niceMax(v: number) {
  if (v <= 0) return 1;
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  const f = v / p;
  const nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
  return nf * p;
}

export interface SeriesDef {
  key: string;
  label: string;
  color: string;
  area?: boolean;
  format?: (v: number) => string;
  dashed?: boolean;
}

export function AreaChart({ data, series, height = 200, yMax, yMin = 0, yFormat = n0, xFormat, refLines = [], empty = t("No data yet") }: {
  data: Record<string, any>[];
  series: SeriesDef[];
  height?: number;
  yMax?: number;
  yMin?: number;
  yFormat?: (v: number) => string;
  xFormat?: (t: number) => string;
  refLines?: { y: number; label: string; color?: string }[];
  empty?: ReactNode;
}) {
  const [ref, w] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const uid = useId().replace(/:/g, "");
  const padL = 40, padR = 8, padT = 10, padB = 22;
  const iw = Math.max(10, w - padL - padR), ih = height - padT - padB;

  const xs = useMemo(() => data.map((d) => d.t as number), [data]);
  const max = useMemo(() => {
    if (yMax != null) return yMax;
    let m = 0;
    for (const d of data) for (const s of series) {
      const v = d[s.key];
      if (typeof v === "number" && isFinite(v)) m = Math.max(m, v);
    }
    for (const r of refLines) m = Math.max(m, r.y);
    return niceMax(m * 1.08);
  }, [data, series, yMax, refLines]);
  const span = data.length > 1 ? xs[xs.length - 1] - xs[0] : 1;
  const fmtX = xFormat ?? (span > 2 * 86400_000 ? dayShort : clock);
  const x0 = xs[0] ?? 0, x1 = xs[xs.length - 1] ?? 1;
  const sx = (t: number) => padL + (x1 === x0 ? iw / 2 : ((t - x0) / (x1 - x0)) * iw);
  const sy = (v: number) => padT + ih - ((Math.min(max, Math.max(yMin, v)) - yMin) / (max - yMin || 1)) * ih;

  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => yMin + (max - yMin) * f);
  const xt = data.length > 1 ? [0, 0.25, 0.5, 0.75, 1].map((f) => x0 + (x1 - x0) * f) : [];

  const onMove = (e: React.PointerEvent) => {
    if (!data.length) return;
    const r = (e.currentTarget as SVGElement).getBoundingClientRect();
    const px = e.clientX - r.left;
    const t = x0 + ((px - padL) / iw) * (x1 - x0);
    let best = 0, bd = Infinity;
    for (let i = 0; i < xs.length; i++) {
      const d = Math.abs(xs[i] - t);
      if (d < bd) { bd = d; best = i; }
    }
    setHover(best);
  };

  return (
    <div className="chart" ref={ref} style={{ height }}>
      {w > 0 && data.length === 0 && (
        <div className="empty" style={{ height, padding: 0, justifyContent: "center" }}>
          <span>{empty}</span>
        </div>
      )}
      {w > 0 && data.length > 0 && (
        <svg width={w} height={height} onPointerMove={onMove} onPointerLeave={() => setHover(null)}>
          <defs>
            {series.map((s) => (
              <linearGradient key={s.key} id={`g${uid}${s.key.replace(/\W/g, "")}`} x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stopColor={s.color} stopOpacity={0.32} />
                <stop offset="100%" stopColor={s.color} stopOpacity={0} />
              </linearGradient>
            ))}
          </defs>
          {ticks.map((v, i) => (
            <g key={i}>
              <line className="gridline" x1={padL} x2={w - padR} y1={sy(v)} y2={sy(v)} opacity={i === 0 ? 1 : 0.6} />
              <text className="axis" x={padL - 8} y={sy(v) + 3.5} textAnchor="end">{yFormat(v)}</text>
            </g>
          ))}
          {xt.map((t, i) => (
            <text key={i} className="axis" x={sx(t)} y={height - 5} textAnchor={i === 0 ? "start" : i === xt.length - 1 ? "end" : "middle"}>{fmtX(t)}</text>
          ))}
          {refLines.map((r, i) => (
            <g key={i}>
              <line x1={padL} x2={w - padR} y1={sy(r.y)} y2={sy(r.y)} stroke={r.color ?? "var(--text-3)"} strokeDasharray="4 4" strokeWidth={1} opacity={0.6} />
              <text className="axis" x={w - padR} y={sy(r.y) - 5} textAnchor="end" style={{ fill: r.color ?? "var(--text-3)" }}>{r.label}</text>
            </g>
          ))}
          {series.map((s) => {
            const segs = segments(xs, data.map((d) => d[s.key]), sx, sy);
            return (
              <g key={s.key}>
                {s.area !== false &&
                  segs.map((pts, i) => (
                    <path key={i} d={smoothPath(pts) + `L${pts[pts.length - 1][0]},${padT + ih}L${pts[0][0]},${padT + ih}Z`} fill={`url(#g${uid}${s.key.replace(/\W/g, "")})`} />
                  ))}
                {segs.map((pts, i) => (
                  <path key={i} d={smoothPath(pts)} fill="none" stroke={s.color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" strokeDasharray={s.dashed ? "4 4" : undefined} />
                ))}
              </g>
            );
          })}
          {hover != null && data[hover] && (
            <g>
              <line className="cross" x1={sx(xs[hover])} x2={sx(xs[hover])} y1={padT} y2={padT + ih} />
              {series.map((s) => {
                const v = data[hover][s.key];
                return typeof v === "number" && isFinite(v) ? (
                  <circle key={s.key} cx={sx(xs[hover])} cy={sy(v)} r={4} fill={s.color} stroke="var(--bg)" strokeWidth={2} />
                ) : null;
              })}
            </g>
          )}
        </svg>
      )}
      {hover != null && data[hover] && (
        <div className="tip" style={{ left: Math.min(Math.max(sx(xs[hover]), 70), w - 70), top: padT + 6 }}>
          <div className="t">{span > 2 * 86400_000 ? `${dayShort(xs[hover])} ${clock(xs[hover])}` : clock(xs[hover])}</div>
          {series.map((s) => {
            const v = data[hover][s.key];
            return (
              <div className="v" key={s.key}>
                <i style={{ background: s.color }} />
                <span style={{ color: "var(--text-2)", fontWeight: 500 }}>{s.label}</span>
                <span style={{ marginLeft: "auto", paddingLeft: 10 }}>{typeof v === "number" && isFinite(v) ? (s.format ?? yFormat)(v) : "–"}</span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

/** Tiny trend line for tiles. */
export function Spark({ values, color = "var(--accent)", height = 36, max, min = 0, area = true }: { values: number[]; color?: string; height?: number; max?: number; min?: number; area?: boolean }) {
  const uid = useId().replace(/:/g, "");
  const W = 200;
  const clean = values.filter((v) => isFinite(v));
  if (clean.length < 2) return <div style={{ height }} />;
  const hi = (max ?? Math.max(...clean) * 1.15) || 1;
  const lo = Math.min(min, ...clean);
  const n = values.length;
  const xs = values.map((_, i) => i);
  const segs = segments(xs, values, (i) => (i / (n - 1)) * W, (v) => height - 2 - ((v - lo) / (hi - lo || 1)) * (height - 4));
  return (
    <svg viewBox={`0 0 ${W} ${height}`} preserveAspectRatio="none" style={{ width: "100%", height, display: "block", overflow: "visible" }}>
      <defs>
        <linearGradient id={`s${uid}`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.3} />
          <stop offset="100%" stopColor={color} stopOpacity={0} />
        </linearGradient>
      </defs>
      {area && segs.map((p, i) => <path key={"a" + i} d={smoothPath(p) + `L${p[p.length - 1][0]},${height}L${p[0][0]},${height}Z`} fill={`url(#s${uid})`} />)}
      {segs.map((p, i) => (
        <path key={i} d={smoothPath(p)} fill="none" stroke={color} strokeWidth={1.8} vectorEffect="non-scaling-stroke" strokeLinecap="round" />
      ))}
    </svg>
  );
}

export function Ring({ value, size = 72, stroke = 7, color = "var(--accent)", children, track = "var(--well-2)" }: { value: number; size?: number; stroke?: number; color?: string; children?: ReactNode; track?: string }) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const v = Math.max(0, Math.min(1, isFinite(value) ? value : 0));
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size}>
        <circle cx={size / 2} cy={size / 2} r={r} stroke={track} strokeWidth={stroke} fill="none" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke={color}
          strokeWidth={stroke}
          fill="none"
          strokeLinecap="round"
          strokeDasharray={`${c * v} ${c}`}
          style={{ transition: "stroke-dasharray 600ms var(--ease), stroke 600ms" }}
        />
      </svg>
      <div className="ring-label">{children}</div>
    </div>
  );
}

export function Donut({ parts, size = 140, stroke = 16, children }: { parts: { value: number; color: string; label?: string }[]; size?: number; stroke?: number; children?: ReactNode }) {
  const total = parts.reduce((a, p) => a + p.value, 0);
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  let off = 0;
  const gap = total > 0 && parts.filter((p) => p.value > 0).length > 1 ? 3 : 0;
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size}>
        <circle cx={size / 2} cy={size / 2} r={r} stroke="var(--well-2)" strokeWidth={stroke} fill="none" />
        {total > 0 &&
          parts.map((p, i) => {
            const len = (p.value / total) * c;
            const el = (
              <circle
                key={i}
                cx={size / 2}
                cy={size / 2}
                r={r}
                stroke={p.color}
                strokeWidth={stroke}
                fill="none"
                strokeDasharray={`${Math.max(0, len - gap)} ${c}`}
                strokeDashoffset={-off}
                strokeLinecap="butt"
                style={{ transition: "stroke-dasharray 600ms var(--ease), stroke-dashoffset 600ms var(--ease)" }}
              >
                {p.label && <title>{p.label}</title>}
              </circle>
            );
            off += len;
            return el;
          })}
      </svg>
      <div className="ring-label">{children}</div>
    </div>
  );
}

export function HeatMap({ grid }: { grid: number[][] }) {
  const days = [t("Mon"), t("Tue"), t("Wed"), t("Thu"), t("Fri"), t("Sat"), t("Sun")];
  const max = Math.max(1, ...grid.flat());
  return (
    <div className="heat">
      <span />
      {Array.from({ length: 24 }, (_, h) => (
        <span key={h} style={{ textAlign: "center" }}>{h % 6 === 0 ? h : ""}</span>
      ))}
      {grid.map((row, d) => (
        <div key={d} style={{ display: "contents" }}>
          <span>{days[d]}</span>
          {row.map((v, h) => (
            <i
              key={h}
              title={t("{day}, {from}:00–{to}:00 · {n} min over 4 weeks", { day: days[d], from: h, to: h + 1, n: Math.round(v) })}
              style={{ background: v ? `color-mix(in srgb, var(--accent) ${Math.round(18 + (v / max) * 82)}%, transparent)` : undefined }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

export const PALETTE = ["var(--c1)", "var(--c2)", "var(--c3)", "var(--c4)", "var(--c5)", "var(--c6)"];
