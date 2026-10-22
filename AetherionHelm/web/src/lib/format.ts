// German formatting helpers. Everything the UI prints goes through here so units stay consistent.

const nf0 = new Intl.NumberFormat("de-DE", { maximumFractionDigits: 0 });
const nf1 = new Intl.NumberFormat("de-DE", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const nf2 = new Intl.NumberFormat("de-DE", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const time = new Intl.DateTimeFormat("de-DE", { hour: "2-digit", minute: "2-digit" });
const timeS = new Intl.DateTimeFormat("de-DE", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
const date = new Intl.DateTimeFormat("de-DE", { day: "2-digit", month: "2-digit", year: "numeric" });
const dateShort = new Intl.DateTimeFormat("de-DE", { day: "2-digit", month: "short" });
const dateTime = new Intl.DateTimeFormat("de-DE", { day: "2-digit", month: "2-digit", year: "2-digit", hour: "2-digit", minute: "2-digit" });
const weekday = new Intl.DateTimeFormat("de-DE", { weekday: "short" });

export const n0 = (v?: number | null) => (v == null || !isFinite(v) ? "–" : nf0.format(v));
export const n1 = (v?: number | null) => (v == null || !isFinite(v) ? "–" : nf1.format(v));
export const n2 = (v?: number | null) => (v == null || !isFinite(v) ? "–" : nf2.format(v));
export const pct = (v?: number | null, digits = 0) =>
  v == null || !isFinite(v) ? "–" : (digits ? nf1.format(v * 100) : nf0.format(v * 100)) + " %";

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

/** 3725000 → "1 Std. 2 Min." */
export function duration(ms?: number | null, parts = 2): string {
  if (ms == null || !isFinite(ms)) return "–";
  if (ms < 1000) return "0 Sek.";
  let s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400);
  s -= d * 86400;
  const h = Math.floor(s / 3600);
  s -= h * 3600;
  const m = Math.floor(s / 60);
  s -= m * 60;
  const out: string[] = [];
  if (d) out.push(d + (d === 1 ? " Tag" : " Tage"));
  if (h) out.push(h + " Std.");
  if (m) out.push(m + " Min.");
  if (s && !d) out.push(s + " Sek.");
  return out.slice(0, parts).join(" ") || "0 Sek.";
}

/** compact: "3 T 4 h", "5 h 12 m", "42 m", "12 s" */
export function short(ms?: number | null): string {
  if (ms == null || !isFinite(ms)) return "–";
  const s = Math.floor(ms / 1000);
  if (s < 60) return s + " s";
  const m = Math.floor(s / 60);
  if (m < 60) return m + " m";
  const h = Math.floor(m / 60);
  if (h < 48) return h + " h " + (m % 60) + " m";
  const d = Math.floor(h / 24);
  return d + " T " + (h % 24) + " h";
}

export function ago(ts?: number | null, now = Date.now()): string {
  if (!ts) return "–";
  const s = Math.round((now - ts) / 1000);
  if (s < 5) return "gerade eben";
  if (s < 60) return `vor ${s} Sek.`;
  const m = Math.floor(s / 60);
  if (m < 60) return `vor ${m} Min.`;
  const h = Math.floor(m / 60);
  if (h < 24) return `vor ${h} Std.`;
  const d = Math.floor(h / 24);
  if (d === 1) return "gestern";
  if (d < 7) return `vor ${d} Tagen`;
  return date.format(ts);
}

export function until(ts?: number | null, now = Date.now()): string {
  if (!ts) return "dauerhaft";
  const left = ts - now;
  if (left <= 0) return "abgelaufen";
  return "noch " + duration(left, 2);
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
  if (d.toDateString() === y.toDateString()) return "gestern " + time.format(ts);
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
      return { label: "Online", tone: "green", pulse: true };
    case "starting":
      return { label: "Startet", tone: "blue", pulse: true };
    case "restarting":
      return { label: "Neustart", tone: "blue", pulse: true };
    case "stopping":
      return { label: "Stoppt", tone: "orange", pulse: true };
    case "degraded":
      return { label: "Ohne Plugin", tone: "yellow", pulse: false };
    case "stopped":
      return { label: "Gestoppt", tone: "", pulse: false };
    case "offline":
      return { label: "Offline", tone: "red", pulse: true };
    default:
      return { label: "Unbekannt", tone: "", pulse: false };
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
  { label: "30 Min.", ms: 30 * 60_000 },
  { label: "1 Std.", ms: 3600_000 },
  { label: "6 Std.", ms: 6 * 3600_000 },
  { label: "1 Tag", ms: 86400_000 },
  { label: "3 Tage", ms: 3 * 86400_000 },
  { label: "7 Tage", ms: 7 * 86400_000 },
  { label: "30 Tage", ms: 30 * 86400_000 },
  { label: "Dauerhaft", ms: null },
];
