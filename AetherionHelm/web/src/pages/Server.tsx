import { useEffect, useMemo, useState } from "react";
import { Activity, Boxes, Bug, Cpu, Gauge, Globe2, HardDrive, Info, MoreHorizontal, Network, Play, Power, Puzzle, Radar, RefreshCw, RotateCcw, Save, Search, Skull, Square, Terminal, Timer, Users } from "lucide-react";
import { api, post, useApi } from "../lib/api";
import { live } from "../lib/live";
import { useStore } from "../lib/store";
import { can, serverName, session } from "../lib/session";
import { go, href } from "../lib/router";
import { getPref, setPref } from "../lib/prefs";
import { ago, bytes, clockS, day, duration, n0, n1, pct, short, stamp, msptTone, tpsTone, pingTone } from "../lib/format";
import { attempt, toast } from "../lib/toast";
import type { Point, Server, ServerDetail } from "../lib/types";
import { useCrumb } from "../App";
import { AreaChart, Ring, Spark } from "../components/charts";
import { Console } from "../components/Console";
import { Confirm } from "../components/overlay";
import { useMenu } from "../components/overlay";
import { openSheet } from "../components/nav";
import { Avatar, Badge, Banner, Button, Card, CardHead, Empty, HoldButton, KV, Meter, Segmented, Skeleton, StateChip, Stat, Tabs, useTick } from "../components/ui";
import { toneColor } from "./Overview";
import { t } from "../lib/i18n";

type Tab = "overview" | "console" | "players" | "radar" | "plugins" | "system" | "backends";
type Range = "1h" | "6h" | "24h" | "7d" | "30d";

export function ServerPage({ id, tab: rawTab }: { id: string; tab?: string }) {
  const me = useStore(session, (s) => s.me)!;
  const s = useStore(live, (st) => st.snap?.servers.find((x) => x.id === id));
  const { data: detail, reload } = useApi<ServerDetail>(`/api/servers/${id}`, { every: 15000 });
  const [confirm, setConfirm] = useState<null | "kill">(null);
  const menu = useMenu();
  useCrumb("servers", s?.name ?? detail?.name);
  useTick(10000);
  const tab = (rawTab as Tab) || "overview";
  const proxy = (s ?? detail)?.kind === "velocity";

  if (!s && !detail) {
    return (
      <div className="page">
        <Skeleton h={40} w={260} />
        <div className="mt">
          <Skeleton h={280} />
        </div>
      </div>
    );
  }
  const srv = { ...(detail ?? {}), ...(s ?? {}) } as ServerDetail;
  const online = srv.state === "online";
  const canPower = can(me, "server.power");

  const power = async (action: "start" | "stop" | "restart" | "kill") => {
    const labels = { start: "Start", stop: t("Stop"), restart: t("Restart"), kill: "Kill" };
    await attempt(() => post(`/api/servers/${id}/power`, { action }), t("{action} for {name} in progress", { action: labels[action], name: srv.name }));
    reload();
  };

  const tabs: { value: Tab; label: string; icon: React.ReactNode; hidden?: boolean }[] = [
    { value: "overview", label: t("Overview"), icon: <Gauge /> },
    { value: "console", label: t("Console"), icon: <Terminal />, hidden: !can(me, "console.view") },
    { value: "players", label: t("Players"), icon: <Users /> },
    { value: "backends", label: "Backends", icon: <Network />, hidden: !proxy },
    { value: "radar", label: t("Lag radar"), icon: <Radar />, hidden: proxy },
    { value: "plugins", label: "Plugins", icon: <Puzzle /> },
    { value: "system", label: "System", icon: <Info /> },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div style={{ minWidth: 0 }}>
          <div className="row" style={{ gap: 12 }}>
            <h1 className="ellipsis">{srv.name}</h1>
            <StateChip state={srv.state} />
          </div>
          <p>
            {srv.role}
            {srv.info?.software ? ` · ${srv.info.software.replace(/\(.*\)/, "").trim()}` : ""}
            {srv.uptime && online ? t(" · up for {d}", { d: duration(srv.uptime) }) : ""}
          </p>
        </div>
        <div className="actions">
          {canPower && (srv.state === "stopped" || srv.state === "offline") && (
            <Button variant="primary" icon={<Play />} onClick={() => power("start")}>
              {t("Start")}
            </Button>
          )}
          {canPower && online && (
            <HoldButton variant="tinted" className="orange" icon={<RotateCcw />} onConfirm={() => power("restart")}>
              {t("Hold to restart")}
            </HoldButton>
          )}
          {canPower && (online || srv.state === "degraded" || srv.state === "starting") && (
            <HoldButton variant="tinted" className="red" icon={<Square />} onConfirm={() => power("stop")}>
              {t("Hold to stop")}
            </HoldButton>
          )}
          <Button icon={<MoreHorizontal />} onClick={menu.open} aria-label={t("More")}>
            <span className="hide-m">{t("More")}</span>
          </Button>
          {menu.render([
            { label: proxy ? t("Reload Velocity") : t("Save world (save-all)"), icon: <Save />, hidden: !can(me, "console.command") || !online, onClick: () => attempt(() => post(`/api/servers/${id}/save`), proxy ? t("Configuration reloaded") : t("Saving worlds")) },
            { label: t("Message to this server"), icon: <Activity />, hidden: !can(me, "chat.send") || proxy, onClick: () => openSheet({ kind: "broadcast", server: id }) },
            { label: t("Open console"), icon: <Terminal />, hidden: !can(me, "console.view"), onClick: () => go("servers", id, "console") },
            { sep: true, label: "", onClick: () => {}, hidden: !canPower },
            { label: t("Force-kill the process"), icon: <Skull />, danger: true, hidden: !canPower || !srv.pid, onClick: () => setConfirm("kill") },
          ])}
        </div>
      </div>

      {srv.power && Date.now() - srv.power.since < 5 * 60_000 && (
        <div className="mb">
          <Banner tone="accent" icon={<RefreshCw className="spin" />}>
            <b>{srv.power.op === "restart" ? t("Restart") : srv.power.op === "start" ? "Start" : srv.power.op === "kill" ? "Kill" : t("Stop")}</b> {t("in progress, started by")}{" "}{srv.power.by} {ago(srv.power.since)}.
            {srv.power.op !== "kill" && " Spieler bekommen den Countdown im Spiel."}
          </Banner>
        </div>
      )}
      {srv.state === "degraded" && (
        <div className="mb">
          <Banner tone="orange" icon={<Info />}>
            {t("The process is running, but the Helm plugin isn't reporting. Is")}{" "}<span className="mono">AetherionHelm.jar</span> {t("installed and the token set in config.yml?")}
          </Banner>
        </div>
      )}

      <Tabs value={tab} tabs={tabs} onChange={(t) => go("servers", id, t)} />

      {tab === "overview" && (proxy ? <ProxyOverview s={srv} /> : <PaperOverview s={srv} />)}
      {tab === "console" && <Console server={id} canSend={can(me, "console.command")} offline={!srv.agent} />}
      {tab === "players" && <ServerPlayers id={id} />}
      {tab === "backends" && <Backends s={srv} />}
      {tab === "radar" && <LagRadar id={id} online={online} />}
      {tab === "plugins" && <Plugins s={srv} />}
      {tab === "system" && <System s={srv} />}

      {confirm === "kill" && (
        <Confirm
          title={t("Force-stop {name}?", { name: srv.name })}
          text={t("Kill ends the process immediately without saving. Only use it when the server hangs and doesn't respond to Stop. The watchdog won't restart it afterwards.")}
          confirm={t("End now")}
          danger
          onConfirm={() => power("kill")}
          onClose={() => setConfirm(null)}
        />
      )}
    </div>
  );
}

function useHistory(id: string) {
  const [range, setRange] = useState<Range>(() => getPref("srv.range", "6h"));
  const { data } = useApi<Point[]>(`/api/servers/${id}/history?range=${range}`, { every: 60000 });
  const set = (r: Range) => {
    setRange(r);
    setPref("srv.range", r);
  };
  const picker = (
    <Segmented
      value={range}
      onChange={set}
      options={[
        { value: "1h", label: t("1 h") },
        { value: "6h", label: t("6 h") },
        { value: "24h", label: t("24 h") },
        { value: "7d", label: t("7 d") },
        { value: "30d", label: t("30 d") },
      ]}
    />
  );
  return { data: data ?? [], picker };
}

function PaperOverview({ s }: { s: ServerDetail }) {
  const series = useStore(live, (st) => st.series);
  const seriesT = useStore(live, (st) => st.seriesT);
  const { data, picker } = useHistory(s.id);
  const tps = s.tps?.[0];
  const heap = s.heapMax ? (s.heapUsed ?? 0) / s.heapMax : undefined;
  const ticks = s.ticks ?? [];
  const maxTick = Math.max(50, ...ticks);
  const liveData = useMemo(
    () => seriesT.map((t, i) => ({ t, tps: series[`${s.id}.tps`]?.[i], mspt: series[`${s.id}.mspt`]?.[i] })),
    [seriesT, series, s.id],
  );
  const heapData = data.map((p) => ({ t: p.t, heap: p.heap, rss: p.rss }));

  return (
    <div className="col" style={{ gap: 16 }}>
      <div className="grid g4">
        <Card>
          <div className="row" style={{ gap: 16 }}>
            <Ring value={tps != null ? tps / 20 : 0} size={78} stroke={8} color={toneColor(tpsTone(tps))}>
              <b>{tps != null ? n1(tps) : "–"}</b>
              <span>TPS</span>
            </Ring>
            <div className="col" style={{ gap: 4 }}>
              <span className="small muted">{t("Ticks per second")}</span>
              <span className="tiny faint num">{t("5 min")}{" "}{n1(s.tps?.[1])} {t("· 15 min")}{" "}{n1(s.tps?.[2])}</span>
              <span className="tiny faint">{t("Target is 20.")}</span>
            </div>
          </div>
        </Card>
        <Card>
          <Stat icon={<Timer />} label={t("Tick time avg")} value={<span style={{ color: toneColor(msptTone(s.mspt)) }}>{n1(s.mspt)}</span>} unit="ms" hint={t("P95 {a} ms · peak {b} ms", { a: n1(s.msptP95), b: n1(s.msptMax) })} />
        </Card>
        <Card>
          <Stat icon={<Cpu />} label="Heap" value={heap != null ? n0(heap * 100) : "–"} unit="%" hint={t("{a} of {b}", { a: bytes(s.heapUsed), b: bytes(s.heapMax, 0) }) + (s.rss ? t(" · process {p}", { p: bytes(s.rss) }) : "")} />
          <div style={{ marginTop: 10 }}>
            <Meter value={heap ?? 0} tone={heap == null ? "" : heap > 0.9 ? "red" : heap > 0.75 ? "orange" : ""} />
          </div>
        </Card>
        <Card>
          <Stat icon={<Users />} label={t("Players")} value={s.players} unit={s.maxPlayers ? `/ ${s.maxPlayers}` : undefined} hint={t("{e} entities · {c} chunks", { e: n0(s.entities), c: n0(s.chunks) })} />
          <div style={{ marginTop: 6 }}>
            <Spark values={series[`${s.id}.players`] ?? []} height={26} color="var(--c2)" />
          </div>
        </Card>
      </div>

      <div className="grid g-split">
        <Card>
          <CardHead title="Live" icon={<Activity />} sub={t("last 5 minutes")} />
          <AreaChart
            data={liveData}
            height={200}
            yMax={Math.max(60, ...liveData.map((d) => d.mspt ?? 0)) > 60 ? undefined : 60}
            series={[
              { key: "mspt", label: "MSPT", color: "var(--c4)", format: (v) => n1(v) + " ms" },
              { key: "tps", label: "TPS", color: "var(--c3)", area: false, format: (v) => n1(v) },
            ]}
            refLines={[{ y: 50, label: t("50 ms = lag threshold"), color: "var(--red)" }]}
            empty={t("Collecting live data …")}
          />
        </Card>
        <Card>
          <CardHead title={t("Last 60 ticks")} icon={<Gauge />} sub={ticks.length ? t("max {v} ms", { v: n1(Math.max(...ticks)) }) : undefined} />
          {ticks.length ? (
            <>
              <div className="tick-bars" style={{ height: 150, position: "relative" }}>
                <span style={{ position: "absolute", left: 0, right: 0, bottom: `${(50 / maxTick) * 100}%`, borderTop: "1px dashed var(--red)", opacity: 0.6 }} />
                {ticks.map((t, i) => (
                  <i key={i} style={{ height: `${Math.max(2, (t / maxTick) * 100)}%`, background: toneColor(msptTone(t)), opacity: 0.85 }} title={`${n1(t)} ms`} />
                ))}
              </div>
              <div className="row between tiny faint mt-s">
                <span>{t("3 s ago")}</span>
                <span>jetzt</span>
              </div>
            </>
          ) : (
            <Empty icon={<Gauge />} title={t("No tick data")} />
          )}
        </Card>
      </div>

      <h2 className="section">
        {t("History")}
        <span style={{ marginLeft: "auto" }}>{picker}</span>
      </h2>
      <div className="grid g2">
        <Card>
          <CardHead title={t("TPS and tick time")} icon={<Timer />} />
          <AreaChart
            data={data}
            height={190}
            series={[
              { key: "mspt", label: t("MSPT avg"), color: "var(--c4)", format: (v) => n1(v) + " ms" },
              { key: "msptMax", label: "MSPT max", color: "var(--c6)", area: false, dashed: true, format: (v) => n1(v) + " ms" },
              { key: "tpsMin", label: "TPS min", color: "var(--c3)", area: false, format: (v) => n1(v) },
            ]}
          />
        </Card>
        <Card>
          <CardHead title={t("Players")} icon={<Users />} />
          <AreaChart data={data} height={190} series={[{ key: "players", label: t("Players"), color: "var(--c1)" }, { key: "playersMax", label: t("Peak"), color: "var(--c2)", area: false, dashed: true }]} />
        </Card>
        <Card>
          <CardHead title={t("Memory")} icon={<HardDrive />} />
          <AreaChart
            data={heapData}
            height={190}
            yFormat={(v) => bytes(v, 0)}
            series={[
              { key: "rss", label: t("Process (RSS)"), color: "var(--c5)", area: false, format: (v) => bytes(v) },
              { key: "heap", label: "Heap", color: "var(--c2)", format: (v) => bytes(v) },
            ]}
          />
        </Card>
        <Card>
          <CardHead title="CPU" icon={<Cpu />} sub={t("Share of the whole machine")} />
          <AreaChart
            data={data.map((p) => ({ t: p.t, cpu: p.cpu != null ? (p.cpu as number) * 100 : null }))}
            height={190}
            yFormat={(v) => n0(v) + "%"}
            series={[{ key: "cpu", label: "CPU", color: "var(--c1)", format: (v) => n1(v) + " %" }]}
          />
        </Card>
        <Card>
          <CardHead title="Entities" icon={<Bug />} />
          <AreaChart data={data} height={190} series={[{ key: "entities", label: "Entities", color: "var(--c4)", format: (v) => n0(v) }]} />
        </Card>
        <Card>
          <CardHead title={t("Loaded chunks")} icon={<Boxes />} />
          <AreaChart data={data} height={190} series={[{ key: "chunks", label: "Chunks", color: "var(--c3)", format: (v) => n0(v) }]} />
        </Card>
      </div>

      {s.worlds && s.worlds.length > 0 && (
        <>
          <h2 className="section">{t("Worlds")}</h2>
          <Card className="flush">
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>{t("World")}</th>
                    <th className="r">{t("Players")}</th>
                    <th className="r">Chunks</th>
                    <th className="r">Entities</th>
                    <th className="r">Tile-Entities</th>
                    <th className="r">{t("Load")}</th>
                  </tr>
                </thead>
                <tbody>
                  {[...s.worlds].sort((a, b) => b.entities + b.tiles - (a.entities + a.tiles)).map((w) => {
                    const load = (w.entities + w.tiles) / Math.max(1, (s.entities ?? 0) + (s.tiles ?? 0));
                    return (
                      <tr key={w.name}>
                        <td>
                          <div className="row" style={{ gap: 8 }}>
                            <Globe2 size={15} className={w.env === "nether" ? "red" : w.env === "the_end" ? "accent" : "green"} />
                            <span className="strong">{w.name}</span>
                            {w.storm && <Badge tone="blue">Regen</Badge>}
                          </div>
                        </td>
                        <td className="r num">{w.players}</td>
                        <td className="r num">{n0(w.chunks)}</td>
                        <td className="r num">{n0(w.entities)}</td>
                        <td className="r num">{n0(w.tiles)}</td>
                        <td className="r" style={{ width: 160 }}>
                          <Meter value={load} />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </Card>
        </>
      )}
      <div className="grid g4">
        <Card className="pad-s">
          <Stat size="sm" label="Threads" value={n0(s.threads)} />
        </Card>
        <Card className="pad-s">
          <Stat size="sm" label="Garbage Collection" value={n0(s.gcCount)} unit={t("runs")} hint={s.gcTime ? t("{d} total", { d: duration(s.gcTime) }) : undefined} />
        </Card>
        <Card className="pad-s">
          <Stat size="sm" label={t("Process CPU")} value={s.cpu != null ? n1(s.cpu * 100) : "–"} unit="%" hint={t("of the whole machine")} />
        </Card>
        <Card className="pad-s">
          <Stat size="sm" label="Tile-Entities" value={n0(s.tiles)} />
        </Card>
      </div>
    </div>
  );
}

function ProxyOverview({ s }: { s: ServerDetail }) {
  const series = useStore(live, (st) => st.series);
  const { data, picker } = useHistory(s.id);
  const heap = s.heapMax ? (s.heapUsed ?? 0) / s.heapMax : undefined;
  return (
    <div className="col" style={{ gap: 16 }}>
      <div className="grid g4">
        <Card>
          <Stat icon={<Users />} label={t("Players on the network")} value={s.players} unit={s.maxPlayers ? `/ ${s.maxPlayers}` : undefined} />
          <div style={{ marginTop: 6 }}>
            <Spark values={series[`${s.id}.players`] ?? []} height={26} color="var(--c1)" />
          </div>
        </Card>
        <Card>
          <Stat icon={<Network />} label={t("Backends reachable")} value={s.backends ? `${s.backends.filter((b) => b.reachable).length}/${s.backends.length}` : "–"} hint={t("Status ping every 10 s")} />
        </Card>
        <Card>
          <Stat icon={<Cpu />} label="Heap" value={heap != null ? n0(heap * 100) : "–"} unit="%" hint={t("{a} of {b}", { a: bytes(s.heapUsed), b: bytes(s.heapMax, 0) })} />
          <div style={{ marginTop: 10 }}>
            <Meter value={heap ?? 0} />
          </div>
        </Card>
        <Card>
          <Stat icon={<Timer />} label={t("Uptime")} value={short(s.uptime)} hint={t("{n} threads · CPU {c}", { n: n0(s.threads), c: pct(s.cpu, 1) })} />
        </Card>
      </div>
      <Backends s={s} />
      <h2 className="section">
        {t("History")}
        <span style={{ marginLeft: "auto" }}>{picker}</span>
      </h2>
      <div className="grid g2">
        <Card>
          <CardHead title={t("Players")} icon={<Users />} />
          <AreaChart data={data} height={190} series={[{ key: "players", label: t("Players"), color: "var(--c1)" }]} />
        </Card>
        <Card>
          <CardHead title={t("Memory")} icon={<HardDrive />} />
          <AreaChart data={data} height={190} yFormat={(v) => bytes(v, 0)} series={[{ key: "heap", label: "Heap", color: "var(--c2)", format: (v) => bytes(v) }]} />
        </Card>
      </div>
    </div>
  );
}

function Backends({ s }: { s: Server }) {
  const me = useStore(session, (st) => st.me);
  return (
    <Card className="flush">
      <div style={{ padding: "18px 20px 4px" }}>
        <CardHead title="Backends" icon={<Network />} sub={t("as seen from the proxy")} />
      </div>
      {(s.backends ?? []).length === 0 ? (
        <Empty icon={<Network />} title={t("No data")} text={t("The proxy reports its backends once the Helm plugin runs there.")} />
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Server</th>
                <th>Status</th>
                <th className="r">{t("Players")}</th>
                <th className="r">Ping</th>
                <th>Version</th>
              </tr>
            </thead>
            <tbody>
              {s.backends!.map((b) => (
                <tr key={b.name} className="clickable" onClick={() => go("servers", b.id)}>
                  <td>
                    <div className="strong">{serverName(me, b.id)}</div>
                    <div className="tiny faint">{t("in the proxy:")}{" "}{b.name}</div>
                  </td>
                  <td>{b.reachable == null ? <Badge>{t("checking …")}</Badge> : b.reachable ? <Badge tone="green">erreichbar</Badge> : <Badge tone="red">{t("unreachable")}</Badge>}</td>
                  <td className="r num">{b.connected}</td>
                  <td className="r num" style={{ color: b.latency != null ? toneColor(pingTone(b.latency)) : undefined }}>{b.latency != null ? `${n1(b.latency)} ms` : "–"}</td>
                  <td className="small muted">{b.version ?? "–"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

function ServerPlayers({ id }: { id: string }) {
  const players = useStore(live, (s) => s.snap?.players ?? []);
  const here = players.filter((p) => p.server === id);
  if (!here.length) {
    return (
      <Card>
        <Empty icon={<Users />} title={t("No one online")} text={t("Players on this server show up here live.")} />
      </Card>
    );
  }
  return (
    <Card className="flush">
      <div className="list">
        {here.map((p) => (
          <a key={p.uuid} href={href("players", p.uuid)} className="list-item inset">
            <Avatar uuid={p.uuid} name={p.name} size="sm" />
            <div className="grow">
              <div className="title">{p.name}</div>
              <div className="sub">
                {p.world ?? "?"} · {p.gamemode ?? "?"}
                {p.afk ? ` · AFK ${short(p.afk * 1000)}` : ""}
              </div>
            </div>
            <span className="small num" style={{ color: toneColor(pingTone(p.ping)) }}>{p.ping != null ? `${p.ping} ms` : ""}</span>
          </a>
        ))}
      </div>
    </Card>
  );
}

interface Radar {
  worlds?: { name: string; types: { type: string; count: number }[]; entities: number; chunks?: number; tiles?: number }[];
  types?: { type: string; count: number }[];
  hot?: { world: string; x: number; z: number; entities: number; tiles: number; top?: string }[];
}

function LagRadar({ id, online }: { id: string; online: boolean }) {
  const [data, setData] = useState<Radar | null>(null);
  const [busy, setBusy] = useState(false);
  const [at, setAt] = useState<number | null>(null);
  const scan = async () => {
    setBusy(true);
    try {
      setData(await api<Radar>(`/api/servers/${id}/entities`));
      setAt(Date.now());
    } catch (e) {
      toast((e as Error).message, "err");
    } finally {
      setBusy(false);
    }
  };
  useEffect(() => {
    if (online) scan();
    // one scan when the tab opens; later scans are on demand
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);
  const max = Math.max(1, ...(data?.types ?? []).map((t) => t.count));
  return (
    <div className="col" style={{ gap: 16 }}>
      <Card strong>
        <div className="row wrap" style={{ gap: 16 }}>
          <span className="icon-tile accent" style={{ width: 48, height: 48, borderRadius: 15 }}>
            <Radar />
          </span>
          <div className="grow">
            <div className="strong" style={{ fontSize: 16 }}>{t("Lag radar")}</div>
            <div className="small muted">{t("Counts entities per type and finds the most crowded chunks. A scan takes a second and barely costs the server anything.")}</div>
          </div>
          <Button variant="primary" icon={<Search />} busy={busy} onClick={scan} disabled={!online}>
            {data ? t("Scan again") : t("Scan now")}
          </Button>
        </div>
        {at && <div className="tiny faint mt-s">{t("As of")}{" "}{clockS(at)}</div>}
      </Card>
      {data && (
        <div className="grid g-split">
          <Card className="flush">
            <div style={{ padding: "18px 20px 6px" }}>
              <CardHead title={t("Crowded chunks")} icon={<Boxes />} sub={t("from 25 entities or tile entities")} />
            </div>
            {(data.hot ?? []).length === 0 ? (
              <Empty title={t("No crowded chunks")} text={t("Nothing is overly crowded right now.")} />
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>{t("World")}</th>
                      <th>Chunk</th>
                      <th>{t("Block coordinates")}</th>
                      <th className="r">Entities</th>
                      <th className="r">Tiles</th>
                      <th>{t("Most")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.hot!.map((c, i) => (
                      <tr key={i}>
                        <td className="strong">{c.world}</td>
                        <td className="mono">{c.x}, {c.z}</td>
                        <td className="mono faint">{c.x * 16 + 8} ~ {c.z * 16 + 8}</td>
                        <td className="r num" style={{ color: c.entities > 150 ? "var(--red)" : c.entities > 60 ? "var(--orange)" : undefined }}>{c.entities}</td>
                        <td className="r num">{c.tiles}</td>
                        <td className="small muted">{c.top ?? "–"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
          <Card>
            <CardHead title={t("Entities by type")} icon={<Boxes />} />
            <div className="col" style={{ gap: 8 }}>
              {(data.types ?? []).map((t) => (
                <div key={t.type} className="col" style={{ gap: 4 }}>
                  <div className="row between small">
                    <span>{t.type.replace(/_/g, " ")}</span>
                    <span className="num faint">{n0(t.count)}</span>
                  </div>
                  <Meter value={t.count / max} />
                </div>
              ))}
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}

function Plugins({ s }: { s: ServerDetail }) {
  const [q, setQ] = useState("");
  const list = (s.info?.plugins ?? []).filter((p) => p.name.toLowerCase().includes(q.toLowerCase())).sort((a, b) => a.name.localeCompare(b.name));
  return (
    <Card className="flush">
      <div style={{ padding: 16 }} className="row">
        <div className="input-icon grow" style={{ maxWidth: 340 }}>
          <Search />
          <input className="input" placeholder={t("Search {n} plugins", { n: s.info?.plugins?.length ?? 0 })} value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
      </div>
      {list.length === 0 ? (
        <Empty icon={<Puzzle />} title={t("No plugins")} text={s.info ? t("Nothing found.") : t("Appears once the Helm plugin is connected.")} />
      ) : (
        <div className="list">
          {list.map((p) => (
            <div key={p.name} className="list-item">
              <span className={p.enabled ? "dot green" : "dot red"} />
              <div className="grow strong small">{p.name}</div>
              <span className="mono small faint">{p.version}</span>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

function System({ s }: { s: ServerDetail }) {
  const i = s.info ?? {};
  const disk = s.disk;
  return (
    <div className="grid g2">
      <Card>
        <CardHead title="Software" icon={<Info />} />
        <KV
          rows={[
            ["Software", i.software],
            ["Minecraft", i.mc],
            ["Java", i.java ? `${i.java} (${i.jvm ?? ""})` : undefined],
            ["Helm-Plugin", s.agentVersion ?? (s.agent ? "verbunden" : t("not connected"))],
            [t("Online mode"), i.onlineMode == null ? undefined : i.onlineMode ? "an" : t("off (proxy)")],
            ...(s.kind === "paper"
              ? ([
                  [t("View distance"), i.viewDistance != null ? t("{v} chunks · simulation {s}", { v: i.viewDistance ?? "", s: i.simulationDistance ?? "" }) : undefined],
                  ["Whitelist", i.whitelist == null ? undefined : i.whitelist ? "an" : "aus"],
                ] as [string, React.ReactNode][])
              : ([[t("Compression from"), i.compressionThreshold != null ? t("{n} bytes", { n: i.compressionThreshold ?? "" }) : undefined]] as [string, React.ReactNode][])),
            ["MOTD", i.motd],
            [t("Started"), i.startedAt ? stamp(i.startedAt) : undefined],
          ]}
        />
      </Card>
      <Card>
        <CardHead title={t("Process")} icon={<Cpu />} />
        <KV
          rows={[
            ["PID", s.pid ? <span className="mono">{s.pid}</span> : undefined],
            ["Port", s.port ? <span className="mono">{s.port}</span> : undefined],
            ["Max. Heap (Xmx)", s.xmx],
            [t("RAM (process)"), s.rss ? bytes(s.rss) : undefined],
            [t("CPU cores visible"), i.cpus],
            [t("Used on disk"), disk?.bytes ? bytes(disk.bytes) : undefined],
          ]}
        />
        {disk?.worlds && (
          <div className="col mt" style={{ gap: 6 }}>
            {Object.entries(disk.worlds)
              .sort((a, b) => b[1] - a[1])
              .map(([w, b]) => (
                <div key={w} className="col" style={{ gap: 4 }}>
                  <div className="row between small">
                    <span>{w}</span>
                    <span className="num faint">{bytes(b)}</span>
                  </div>
                  <Meter value={b / Math.max(1, disk.bytes ?? b)} />
                </div>
              ))}
          </div>
        )}
      </Card>
      {i.jvmArgs && i.jvmArgs.length > 0 && (
        <Card>
          <CardHead title="JVM-Argumente" icon={<Terminal />} />
          <div className="row wrap" style={{ gap: 6 }}>
            {i.jvmArgs.map((a) => (
              <span key={a} className="badge outline mono" style={{ fontWeight: 500 }}>{a}</span>
            ))}
          </div>
        </Card>
      )}
      <Card>
        <CardHead title="Watchdog" icon={<Power />} sub={t("restarts crashed servers")} />
        {(s.watchdog ?? []).length === 0 ? (
          <div className="small faint">{t("No entries for this server. Good.")}</div>
        ) : (
          <div className="col mono small" style={{ gap: 4 }}>
            {s.watchdog!.slice(-12).reverse().map((l, k) => (
              <div key={k} className="faint" style={{ overflowWrap: "anywhere" }}>{l}</div>
            ))}
          </div>
        )}
        <div className="tiny faint mt-s">{t("As of")}{" "}{day(Date.now())}</div>
      </Card>
    </div>
  );
}
