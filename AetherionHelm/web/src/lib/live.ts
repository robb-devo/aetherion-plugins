import { createStore } from "./store";
import type { Alert, AuditRow, ConsoleLine, HelmEvent, Snapshot } from "./types";
import { demoLive } from "./demo";
import { toast } from "./toast";
import { t } from "./i18n";

export type Conn = "connecting" | "live" | "offline";

/** A rolling window of live samples per metric key, newest last. */
export type Series = Record<string, number[]>;
const SERIES_LEN = 150; // 5 minutes at one snapshot every 2 s

export interface LiveState {
  conn: Conn;
  snap: Snapshot | null;
  series: Series;
  seriesT: number[];
  events: HelmEvent[];
  console: { server: string | null; lines: ConsoleLine[]; v: number };
}

export const live = createStore<LiveState>({
  conn: "connecting",
  snap: null,
  series: {},
  seriesT: [],
  events: [],
  console: { server: null, lines: [], v: 0 },
});

const auditListeners = new Set<(r: AuditRow) => void>();
export function onAudit(fn: (r: AuditRow) => void) {
  auditListeners.add(fn);
  return () => {
    auditListeners.delete(fn);
  };
}

const eventListeners = new Set<(e: HelmEvent) => void>();
export function onEvent(fn: (e: HelmEvent) => void) {
  eventListeners.add(fn);
  return () => {
    eventListeners.delete(fn);
  };
}

let ws: WebSocket | null = null;
let attempt = 0;
let stopped = true;
let wantConsole: string | null = null;
let retryTimer: number | undefined;
const CONSOLE_KEEP = 3000;

function push(series: Series, key: string, v: number | undefined | null) {
  const arr = series[key] ? series[key].slice(-(SERIES_LEN - 1)) : [];
  arr.push(v == null || !isFinite(v) ? NaN : v);
  series[key] = arr;
}

function onSnap(s: Snapshot) {
  live.set((st) => {
    const series: Series = { ...st.series };
    push(series, "net.players", s.network.online);
    push(series, "host.cpu", s.host.cpu);
    push(series, "host.mem", s.host.memTotal ? (s.host.memUsed ?? 0) / s.host.memTotal : undefined);
    push(series, "host.rx", s.host.rx);
    push(series, "host.tx", s.host.tx);
    push(series, "host.diskRead", s.host.diskRead);
    push(series, "host.diskWrite", s.host.diskWrite);
    push(series, "host.load", s.host.load?.[0]);
    for (const v of s.servers) {
      push(series, `${v.id}.players`, v.players);
      push(series, `${v.id}.tps`, v.tps?.[0]);
      push(series, `${v.id}.mspt`, v.mspt);
      push(series, `${v.id}.heap`, v.heapUsed);
      push(series, `${v.id}.cpu`, v.cpu);
      push(series, `${v.id}.rss`, v.rss);
      push(series, `${v.id}.entities`, v.entities);
    }
    const seriesT = st.seriesT.slice(-(SERIES_LEN - 1));
    seriesT.push(s.ts);
    // alerts that just appeared get a toast
    const before = new Set((st.snap?.alerts ?? []).map((a) => a.key));
    if (st.snap) for (const a of s.alerts) if (!before.has(a.key) && a.level === "crit") toast(t(a.title), "err");
    return { ...st, snap: s, series, seriesT };
  });
}

export function handleMessage(m: any) {
  switch (m.t) {
    case "snap":
      onSnap(m.data as Snapshot);
      break;
    case "console": {
      if (m.server !== wantConsole) break;
      live.set((st) => {
        const base = m.reset || st.console.server !== m.server ? [] : st.console.lines;
        const lines = base.concat(m.lines as ConsoleLine[]);
        return { ...st, console: { server: m.server, lines: lines.length > CONSOLE_KEEP ? lines.slice(-CONSOLE_KEEP) : lines, v: st.console.v + 1 } };
      });
      break;
    }
    case "event": {
      const e = m.data as HelmEvent;
      live.set((st) => ({ ...st, events: [e, ...st.events].slice(0, 2000) }));
      eventListeners.forEach((f) => f(e));
      break;
    }
    case "alert": {
      const a = m.data as Alert;
      if (a.level === "info") toast(t(a.title), "info");
      break;
    }
    case "audit":
      auditListeners.forEach((f) => f(m.data as AuditRow));
      break;
  }
}

function connect() {
  if (stopped) return;
  live.set((s) => ({ ...s, conn: s.snap ? s.conn : "connecting" }));
  const url = (location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/api/live";
  const sock = new WebSocket(url);
  ws = sock;
  sock.onopen = () => {
    attempt = 0;
    live.set((s) => ({ ...s, conn: "live" }));
    if (wantConsole) sock.send(JSON.stringify({ t: "sub", console: wantConsole }));
  };
  sock.onmessage = (ev) => {
    try {
      handleMessage(JSON.parse(ev.data));
    } catch {
      // ignore malformed frames
    }
  };
  sock.onclose = () => {
    if (ws !== sock) return;
    ws = null;
    live.set((s) => ({ ...s, conn: "offline" }));
    if (stopped) return;
    const delay = Math.min(15000, 600 * 2 ** Math.min(attempt++, 5));
    retryTimer = window.setTimeout(connect, delay);
  };
}

export function startLive() {
  if (!stopped) return;
  stopped = false;
  if (__DEMO__) {
    demoLive(handleMessage, () => wantConsole);
    live.set((s) => ({ ...s, conn: "live" }));
    return;
  }
  connect();
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && !ws && !stopped) {
      clearTimeout(retryTimer);
      attempt = 0;
      connect();
    }
  });
}

export function stopLive() {
  stopped = true;
  clearTimeout(retryTimer);
  ws?.close();
  ws = null;
}

/** Only one console stream at a time: the server whose console is on screen. */
export function watchConsole(server: string | null) {
  wantConsole = server;
  live.set((s) => ({ ...s, console: { server, lines: [], v: s.console.v + 1 } }));
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ t: "sub", console: server }));
}

export function seedEvents(events: HelmEvent[]) {
  live.set((s) => {
    const have = new Set(s.events.map((e) => e.id));
    const merged = [...s.events, ...events.filter((e) => !have.has(e.id))].sort((a, b) => b.ts - a.ts).slice(0, 2000);
    return { ...s, events: merged };
  });
}
