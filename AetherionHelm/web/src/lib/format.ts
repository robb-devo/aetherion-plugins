// Formatting helpers for the UI language. Everything the UI prints goes through here so units stay consistent.
import { locale, t } from "./i18n";

const nf0 = new Intl.NumberFormat(locale, { maximumFractionDigits: 0 });
const nf1 = new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const nf2 = new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const time = new Intl.DateTimeFormat(locale, { hour: "2-digit", minute: "2-digit" });
const timeS = new Intl.DateTimeFormat(locale, { hour: "2-digit", minute: "2-digit", second: "2-digit" });
const date = new Intl.DateTimeFormat(locale, { day: "2-digit", month: "2-digit", year: "numeric" });
const dateShort = new Intl.DateTimeFormat(locale, { day: "2-digit", month: "short" });
const dateTime = new Intl.DateTimeFormat(locale, { day: "2-digit", month: "2-digit", year: "2-digit", hour: "2-digit", minute: "2-digit" });
const weekday = new Intl.DateTimeFormat(locale, { weekday: "short" });

export const n0 = (v?: number | null) => (v == null || !isFinite(v) ? "–" : nf0.format(v));
export const n1 = (v?: number | null) => (v == null || !isFinite(v) ? "–" : nf1.format(v));
export const n2 = (v?: number | null) => (v == null || !isFinite(v) ? "–" : nf2.format(v));
export const pct = (v?: number | null, digits = 0) =>
  v == null || !isFinite(v) ? "–" : t("{n}%", { n: digits ? nf1.format(v * 100) : nf0.format(v * 100) });

export function bytes(v?: number | null, digits = 1): string {
  if (v == null || !isFinite(v)) return "–";
  const u = ["B", "KB", "MB", "GB", "TB", "PB"];
  let i = 0;
  let x = Math.abs(v);
  while (x >= 1024 && i < u.length - 1) {
    x /= 1024;
    i++;
  }
  const s = i === 0 ? nf0.format(x) : x >= 100 ? nf0.format(x) : digits === 2 ? nf2.format(x) : nf1.format(x);
  return (v < 0 ? "-" : "") + s + " " + u[i];
}

/** Bytes per second as network throughput (bits, the way providers quote links). */
export function rate(bps?: number | null): string {
  if (bps == null || !isFinite(bps)) return "–";
  const bits = bps * 8;
  const u = ["bit/s", "kbit/s", "Mbit/s", "Gbit/s"];
  let i = 0;
  let x = bits;
  while (x >= 1000 && i < u.length - 1) {
    x /= 1000;
    i++;
  }
  return (x >= 100 || i === 0 ? nf0.format(x) : nf1.format(x)) + " " + u[i];
}

export function byteRate(bps?: number | null): string {
  if (bps == null || !isFinite(bps)) return "–";
  return bytes(bps) + "/s";
}

/** 3725000 → "1 h 2 min" */
export function duration(ms?: number | null, parts = 2): string {
  if (ms == null || !isFinite(ms)) return "–";
  if (ms < 1000) return t("{n} s", { n: 0 });
  let s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400);
  s -= d * 86400;
  const h = Math.floor(s / 3600);
  s -= h * 3600;
  const m = Math.floor(s / 60);
  s -= m * 60;
  const out: string[] = [];
  if (d) out.push(d === 1 ? t("1 day") : t("{n} days", { n: d }));
  if (h) out.push(t("{n} h", { n: h }));
  if (m) out.push(t("{n} min", { n: m }));
  if (s && !d) out.push(t("{n} s", { n: s }));
  return out.slice(0, parts).join(" ") || t("{n} s", { n: 0 });
}

/** compact: "3 d 4 h", "5 h 12 m", "42 m", "12 s" */
export function short(ms?: number | null): string {
  if (ms == null || !isFinite(ms)) return "–";
  const s = Math.floor(ms / 1000);
  if (s < 60) return s + " s";
  const m = Math.floor(s / 60);
  if (m < 60) return m + " m";
  const h = Math.floor(m / 60);
  if (h < 48) return h + " h " + (m % 60) + " m";
  const d = Math.floor(h / 24);
  return t("{d} d {h} h", { d, h: h % 24 });
}

export function ago(ts?: number | null, now = Date.now()): string {
  if (!ts) return "–";
  const s = Math.round((now - ts) / 1000);
  if (s < 5) return t("just now");
  if (s < 60) return t("{n} s ago", { n: s });
  const m = Math.floor(s / 60);
  if (m < 60) return t("{n} min ago", { n: m });
  const h = Math.floor(m / 60);
  if (h < 24) return t("{n} h ago", { n: h });
  const d = Math.floor(h / 24);
  if (d === 1) return t("yesterday");
  if (d < 7) return t("{n} days ago", { n: d });
  return date.format(ts);
}

export function until(ts?: number | null, now = Date.now()): string {
  if (!ts) return t("permanent");
  const left = ts - now;
  if (left <= 0) return t("expired");
  return t("{time} left", { time: duration(left, 2) });
}

export const clock = (ts?: number | null) => (ts ? time.format(ts) : "–");
export const clockS = (ts?: number | null) => (ts ? timeS.format(ts) : "–");
export const day = (ts?: number | null) => (ts ? date.format(ts) : "–");
export const dayShort = (ts?: number | null) => (ts ? dateShort.format(ts) : "–");
export const stamp = (ts?: number | null) => (ts ? dateTime.format(ts) : "–");
export const wday = (ts: number) => weekday.format(ts);

/** Smart timestamp: time today, weekday this week, else date. */
export function when(ts?: number | null): string {
  if (!ts) return "–";
  const d = new Date(ts);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) return time.format(ts);
  const y = new Date(now);
  y.setDate(now.getDate() - 1);
  if (d.toDateString() === y.toDateString()) return t("Yesterday {time}", { time: time.format(ts) });
  if (now.getTime() - ts < 6 * 86400_000) return weekday.format(ts) + " " + time.format(ts);
  return dateTime.format(ts);
}

export function plural(n: number, one: string, many: string) {
  return `${n0(n)} ${n === 1 ? one : many}`;
}

export const tpsTone = (t?: number) => (t == null ? "" : t >= 19.5 ? "green" : t >= 17 ? "yellow" : t >= 12 ? "orange" : "red");
export const msptTone = (m?: number) => (m == null ? "" : m <= 35 ? "green" : m <= 50 ? "yellow" : m <= 80 ? "orange" : "red");
export const loadTone = (f?: number) => (f == null ? "" : f < 0.6 ? "green" : f < 0.8 ? "yellow" : f < 0.92 ? "orange" : "red");
export const pingTone = (p?: number) => (p == null ? "" : p < 60 ? "green" : p < 120 ? "yellow" : p < 250 ? "orange" : "red");

export function stateInfo(s: string): { label: string; tone: string; pulse: boolean } {
  switch (s) {
    case "online":
      return { label: t("Online"), tone: "green", pulse: true };
    case "starting":
      return { label: t("Starting"), tone: "blue", pulse: true };
    case "restarting":
      return { label: t("Restarting"), tone: "blue", pulse: true };
    case "stopping":
      return { label: t("Stopping"), tone: "orange", pulse: true };
    case "degraded":
      return { label: t("No plugin"), tone: "yellow", pulse: false };
    case "stopped":
      return { label: t("Stopped"), tone: "", pulse: false };
    case "offline":
      return { label: t("Offline"), tone: "red", pulse: true };
    default:
      return { label: t("Unknown"), tone: "", pulse: false };
  }
}

export function initials(name?: string) {
  if (!name) return "?";
  return name.slice(0, 2).toUpperCase();
}

export function maskIp(ip?: string) {
  if (!ip) return "–";
  if (ip.includes(":")) return ip.split(":").slice(0, 2).join(":") + ":••••";
  const p = ip.split(".");
  return p.length === 4 ? `${p[0]}.${p[1]}.•••.•••` : ip;
}

export const DURATIONS: { label: string; ms: number | null }[] = [
  { label: t("{n} min", { n: 30 }), ms: 30 * 60_000 },
  { label: t("{n} h", { n: 1 }), ms: 3600_000 },
  { label: t("{n} h", { n: 6 }), ms: 6 * 3600_000 },
  { label: t("1 day"), ms: 86400_000 },
  { label: t("{n} days", { n: 3 }), ms: 3 * 86400_000 },
  { label: t("{n} days", { n: 7 }), ms: 7 * 86400_000 },
  { label: t("{n} days", { n: 30 }), ms: 30 * 86400_000 },
  { label: t("Permanent"), ms: null },
];
