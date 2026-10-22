import { useMemo, useState } from "react";
import { Activity as ActivityIcon, AlertTriangle, ArrowDownRight, ArrowUpRight, CheckCircle2, ChevronRight, Cpu, Flag, Gauge, HardDrive, MemoryStick, Megaphone, Network, Users } from "lucide-react";
import { useApi } from "../lib/api";
import { live } from "../lib/live";
import { useStore } from "../lib/store";
import { session, can, serverName } from "../lib/session";
import { href, go } from "../lib/router";
import { ago, bytes, n0, n1, pct, rate, short, tpsTone, msptTone, loadTone } from "../lib/format";
import type { Point, Server } from "../lib/types";
import { AreaChart, PALETTE, Ring, Spark } from "../components/charts";
import { openSheet } from "../components/nav";
import { Avatar, Badge, Button, Card, CardHead, Count, Empty, IconTile, Meter, Segmented, Skeleton, StateChip, Stat, cx, useTick } from "../components/ui";
import { EventRow } from "./Activity";

function greeting() {
  const h = new Date().getHours();
  if (h < 5) return "Gute Nacht";
  if (h < 11) return "Guten Morgen";
  if (h < 17) return "Hallo";
  if (h < 22) return "Guten Abend";
  return "Gute Nacht";
}

export const toneColor = (t: string) => (t ? `var(--${t})` : "var(--text-3)");

export function Overview() {
  const me = useStore(session, (s) => s.me)!;
  const snap = useStore(live, (s) => s.snap);
  const series = useStore(live, (s) => s.series);
  useTick(20000);

  if (!snap) return <OverviewSkeleton />;

  const paper = snap.servers.filter((s) => s.kind === "paper");
  const up = snap.servers.filter((s) => s.state === "online").length;
  // lowest TPS first; while everything holds 20 TPS, the one with the longest ticks is closest to lagging
  const worst = paper.filter((s) => s.state === "online" && s.tps).sort((a, b) => (a.tps![0] ?? 20) - (b.tps![0] ?? 20) || (b.mspt ?? 0) - (a.mspt ?? 0))[0];
  const crit = snap.alerts.filter((a) => a.level === "crit");
  const summary = crit.length
    ? `${crit.length === 1 ? "Ein Problem braucht" : crit.length + " Probleme brauchen"} Aufmerksamkeit.`
    : up === snap.servers.length
      ? `Alles läuft. ${up} von ${snap.servers.length} Servern online, ${snap.network.online} ${snap.network.online === 1 ? "Spieler" : "Spieler"} im Netzwerk.`
      : `${up} von ${snap.servers.length} Servern online, ${snap.network.online} Spieler im Netzwerk.`;

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>
            {greeting()}, {me.user.display.split(" ")[0]}
          </h1>
          <p className="row" style={{ gap: 8 }}>
            {crit.length ? <AlertTriangle size={16} className="red" /> : <CheckCircle2 size={16} className="green" />}
            {summary}
          </p>
        </div>
        <div className="actions">
          {can(me, "chat.send") && (
            <Button icon={<Megaphone />} onClick={() => openSheet({ kind: "broadcast" })}>
              Broadcast
            </Button>
          )}
        </div>
      </div>

      <NetworkHero />

      <div className="grid g4 mt">
        <MiniStat
          icon={<Gauge />}
          label="Schwächster Server"
          value={worst ? n1(worst.tps![0]) : "–"}
          unit="TPS"
          tone={worst ? tpsTone(worst.tps![0]) : ""}
          hint={worst ? `${worst.name} · ${n1(worst.mspt)} ms pro Tick` : "keine Daten"}
          spark={worst ? series[`${worst.id}.tps`] : undefined}
          sparkMax={20}
          onClick={worst ? () => go("servers", worst.id) : undefined}
        />
        <MiniStat
          icon={<Cpu />}
          label="Hetzner CPU"
          value={snap.host.cpu != null ? n0(snap.host.cpu * 100) : "–"}
          unit="%"
          tone={loadTone(snap.host.cpu)}
          hint={snap.host.load ? `Load ${snap.host.load.map((l) => n1(l)).join(" · ")}` : undefined}
          spark={series["host.cpu"]?.map((v) => v * 100)}
          sparkMax={100}
          onClick={can(me, "host.view") ? () => go("host") : undefined}
        />
        <MiniStat
          icon={<MemoryStick />}
          label="Arbeitsspeicher"
          value={snap.host.memTotal ? bytes(snap.host.memUsed).split(" ")[0] : "–"}
          unit={snap.host.memTotal ? `${bytes(snap.host.memUsed).split(" ")[1]} von ${bytes(snap.host.memTotal, 0)}` : ""}
          tone={loadTone(snap.host.memTotal ? (snap.host.memUsed ?? 0) / snap.host.memTotal : undefined)}
          hint={snap.host.memCached ? `${bytes(snap.host.memCached)} Cache` : undefined}
          spark={series["host.mem"]?.map((v) => v * 100)}
          sparkMax={100}
          onClick={can(me, "host.view") ? () => go("host") : undefined}
        />
        <MiniStat
          icon={<Network />}
          label="Netzwerk"
          value={rate(snap.host.tx).split(" ")[0]}
          unit={rate(snap.host.tx).split(" ")[1] + " raus"}
          hint={`${rate(snap.host.rx)} rein`}
          spark={series["host.tx"]}
          onClick={can(me, "host.view") ? () => go("host") : undefined}
        />
      </div>

      <h2 className="section">
        Server
        <span className="faint small" style={{ fontWeight: 500 }}>
          {up}/{snap.servers.length} online
        </span>
        <a href={href("servers")} className="btn ghost sm" style={{ marginLeft: "auto" }}>
          Alle <ChevronRight />
        </a>
      </h2>
      <div className="grid g-auto">
        {snap.servers.map((s) => (
          <ServerTile key={s.id} s={s} spark={series[`${s.id}.players`]} />
        ))}
      </div>

      <div className="grid g-split" style={{ marginTop: 24 }}>
        <LiveFeed />
        <div className="col" style={{ gap: 16 }}>
          <AlertsCard />
          <OnlineCard />
        </div>
      </div>
    </div>
  );
}

function OverviewSkeleton() {
  return (
    <div className="page">
      <div className="page-head">
        <div className="col">
          <Skeleton h={38} w={320} />
          <Skeleton h={18} w={420} />
        </div>
      </div>
      <Card className="hero">
        <Skeleton h={220} />
      </Card>
      <div className="grid g4 mt">
        {[0, 1, 2, 3].map((i) => (
          <Card key={i}>
            <Skeleton h={80} />
          </Card>
        ))}
      </div>
    </div>
  );
}

function MiniStat({ icon, label, value, unit, hint, tone, spark, sparkMax, onClick }: {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
  unit?: string;
  hint?: string;
  tone?: string;
  spark?: number[];
  sparkMax?: number;
  onClick?: () => void;
}) {
  return (
    <Card onClick={onClick} className="pad-s" style={{ padding: 16 }}>
      <Stat icon={icon} label={label} value={<span style={{ color: tone ? toneColor(tone) : undefined }}>{value}</span>} unit={unit} hint={hint} size="sm" />
      <div style={{ marginTop: 10 }}>
        <Spark values={spark ?? []} color={tone ? toneColor(tone) : "var(--accent)"} height={34} max={sparkMax} />
      </div>
    </Card>
  );
}

type Range = "live" | "1h" | "24h" | "7d" | "30d";

function NetworkHero() {
  const snap = useStore(live, (s) => s.snap)!;
  const series = useStore(live, (s) => s.series);
  const seriesT = useStore(live, (s) => s.seriesT);
  const [range, setRange] = useState<Range>("24h");
  const { data } = useApi<Point[]>(range === "live" ? null : `/api/network/history?range=${range}`, { every: 60000 });

  const chart = useMemo(() => {
    if (range === "live") return seriesT.map((t, i) => ({ t, players: series["net.players"]?.[i] ?? null }));
    return (data ?? []).map((p) => ({ t: p.t, players: p.players, peak: p.playersMax }));
  }, [range, data, series, seriesT]);

  const dist = snap.servers.filter((s) => s.kind === "paper");
  const total = dist.reduce((a, s) => a + (s.players || 0), 0);
  const last = series["net.players"] ?? [];
  const trend = last.length > 30 ? last[last.length - 1] - last[last.length - 31] : 0;

  return (
    <div className="glass strong hero">
      <div className="glow" />
      <div className="col" style={{ gap: 18, justifyContent: "space-between" }}>
        <div>
          <div className="eyebrow row" style={{ gap: 8 }}>
            <Users size={13} /> Gerade online
          </div>
          <div className="row" style={{ alignItems: "baseline", gap: 12, marginTop: 6 }}>
            <span className="stat lg">
              <span className="value">
                <Count value={snap.network.online} />
              </span>
            </span>
            {snap.network.maxPlayers ? <span className="faint num" style={{ fontSize: 17 }}>/ {snap.network.maxPlayers}</span> : null}
            {trend !== 0 && (
              <Badge tone={trend > 0 ? "green" : "orange"} icon={trend > 0 ? <ArrowUpRight /> : <ArrowDownRight />}>
                {trend > 0 ? "+" : ""}
                {Math.round(trend)} in 1 Min.
              </Badge>
            )}
          </div>
          <div className="row wrap" style={{ gap: 18, marginTop: 10 }}>
            <span className="small muted">
              Peak heute <b className="num" style={{ color: "var(--text)" }}>{snap.network.peakToday}</b>
            </span>
            <span className="small muted">
              Verschiedene Spieler heute <b className="num" style={{ color: "var(--text)" }}>{snap.network.uniqueToday}</b>
            </span>
          </div>
        </div>
        <div className="col" style={{ gap: 10 }}>
          <div className="split">
            {dist.map((s, i) => (
              <i key={s.id} style={{ flexGrow: Math.max(s.players, total ? 0 : 1), background: PALETTE[i % PALETTE.length] }} title={`${s.name}: ${s.players}`} />
            ))}
          </div>
          <div className="legend">
            {dist.map((s, i) => (
              <a key={s.id} href={href("servers", s.id)} style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                <i style={{ background: PALETTE[i % PALETTE.length] }} />
                {s.name}
                <b className="num" style={{ color: "var(--text)" }}>{s.players}</b>
              </a>
            ))}
          </div>
        </div>
      </div>
      <div className="col" style={{ gap: 10, minWidth: 0 }}>
        <div className="row between">
          <span className="eyebrow">Spieler im Verlauf</span>
          <Segmented
            value={range}
            onChange={setRange}
            options={[
              { value: "live", label: "Live" },
              { value: "1h", label: "1 Std." },
              { value: "24h", label: "24 Std." },
              { value: "7d", label: "7 T." },
              { value: "30d", label: "30 T." },
            ]}
          />
        </div>
        <AreaChart
          data={chart}
          height={210}
          series={[
            { key: "players", label: "Spieler", color: "var(--c1)" },
            ...(range !== "live" && range !== "1h" ? [{ key: "peak", label: "Spitze", color: "var(--c2)", area: false, dashed: true }] : []),
          ]}
          empty={range === "live" ? "Sammle Live-Daten …" : "Der Verlauf füllt sich, sobald das Panel ein paar Minuten läuft."}
        />
      </div>
    </div>
  );
}

export function ServerTile({ s, spark }: { s: Server; spark?: number[] }) {
  const tps = s.tps?.[0];
  const heap = s.heapMax ? (s.heapUsed ?? 0) / s.heapMax : undefined;
  const on = s.state === "online";
  const isProxy = s.kind === "velocity";
  return (
    <a href={href("servers", s.id)} className={cx("glass card interactive srv-tile", !on && "off")}>
      <div className="srv-head">
        {isProxy ? (
          <Ring value={on ? 1 : 0} size={48} stroke={5} color="var(--accent)">
            <Network size={18} />
          </Ring>
        ) : (
          <Ring value={tps != null ? tps / 20 : 0} size={48} stroke={5} color={toneColor(tpsTone(tps))}>
            <b style={{ fontSize: 13 }}>{tps != null ? n1(tps) : "–"}</b>
          </Ring>
        )}
        <div className="grow">
          <div className="srv-name ellipsis">{s.name}</div>
          <div className="srv-role ellipsis">{s.role}</div>
        </div>
        <StateChip state={s.state} />
      </div>
      <div className="srv-metrics">
        <div className="m">
          <b>{s.players ?? 0}</b>
          <span>Spieler</span>
        </div>
        {isProxy ? (
          <div className="m">
            <b>{s.backends ? `${s.backends.filter((b) => b.reachable).length}/${s.backends.length}` : "–"}</b>
            <span>Backends</span>
          </div>
        ) : (
          <div className="m">
            <b style={{ color: s.mspt != null ? toneColor(msptTone(s.mspt)) : undefined }}>{s.mspt != null ? n1(s.mspt) : "–"}</b>
            <span>MSPT</span>
          </div>
        )}
        <div className="m">
          <b>{s.uptime ? short(s.uptime) : "–"}</b>
          <span>Laufzeit</span>
        </div>
      </div>
      <div className="col" style={{ gap: 6 }}>
        <div className="row between tiny faint">
          <span>Heap</span>
          <span className="num">
            {heap != null ? `${bytes(s.heapUsed)} / ${bytes(s.heapMax, 0)}` : s.xmx ? `Xmx ${s.xmx}` : "–"}
          </span>
        </div>
        <Meter value={heap ?? 0} tone={heap == null ? "" : heap > 0.9 ? "red" : heap > 0.75 ? "orange" : ""} />
      </div>
      <Spark values={spark ?? []} height={28} color="var(--c2)" />
    </a>
  );
}

function LiveFeed() {
  const events = useStore(live, (s) => s.events);
  const me = useStore(session, (s) => s.me);
  const shown = events.filter((e) => e.kind !== "command").slice(0, 14);
  return (
    <Card className="flush">
      <div style={{ padding: "18px 20px 6px" }}>
        <CardHead
          title="Live-Aktivität"
          icon={<ActivityIcon />}
          right={
            <a href={href("activity")} className="btn ghost sm">
              Alles <ChevronRight />
            </a>
          }
        />
      </div>
      {shown.length === 0 ? (
        <Empty icon={<ActivityIcon />} title="Noch ruhig" text="Joins, Chat, Tode und Strafen erscheinen hier in Echtzeit." />
      ) : (
        <div style={{ paddingBottom: 8 }}>
          {shown.map((e) => (
            <EventRow key={e.id} e={e} serverLabel={serverName(me, e.server)} />
          ))}
        </div>
      )}
    </Card>
  );
}

function AlertsCard() {
  const alerts = useStore(live, (s) => s.snap?.alerts ?? []);
  const reports = useStore(live, (s) => s.snap?.openReports ?? 0);
  const me = useStore(session, (s) => s.me);
  return (
    <Card>
      <CardHead
        title="Lage"
        icon={<AlertTriangle />}
        right={
          <a href={href("alerts")} className="btn ghost sm">
            Details <ChevronRight />
          </a>
        }
      />
      <div className="col" style={{ gap: 8 }}>
        {alerts.length === 0 && (
          <div className="row">
            <IconTile tone="green">
              <CheckCircle2 />
            </IconTile>
            <div>
              <div className="strong">Alles im grünen Bereich</div>
              <div className="small faint">Keine Warnungen. TPS, Speicher und Platten sind ok.</div>
            </div>
          </div>
        )}
        {alerts.slice(0, 4).map((a) => (
          <div className="row" key={a.key}>
            <IconTile tone={a.level === "crit" ? "red" : a.level === "warn" ? "orange" : "accent"} size="sm">
              <AlertTriangle />
            </IconTile>
            <div className="grow">
              <div className="strong small ellipsis">{a.title}</div>
              <div className="tiny faint ellipsis">
                {a.detail} · {ago(a.since)}
              </div>
            </div>
          </div>
        ))}
        <a href={href("reports")} className="row" style={{ marginTop: 6, padding: "10px 12px", borderRadius: 14, background: "var(--well)" }}>
          <IconTile tone={reports ? "orange" : ""} size="sm">
            <Flag />
          </IconTile>
          <div className="grow">
            <div className="strong small">{reports ? `${reports} offene Meldung${reports === 1 ? "" : "en"}` : "Keine offenen Meldungen"}</div>
            <div className="tiny faint">Aus /report im Spiel</div>
          </div>
          <ChevronRight size={16} className="faint" />
        </a>
        {can(me, "host.view") && <HostMini />}
      </div>
    </Card>
  );
}

function HostMini() {
  const h = useStore(live, (s) => s.snap?.host);
  if (!h) return null;
  const disk = h.diskTotal ? (h.diskUsed ?? 0) / h.diskTotal : 0;
  return (
    <a href={href("host")} className="col" style={{ gap: 8, padding: "12px", borderRadius: 14, background: "var(--well)" }}>
      <div className="row between small">
        <span className="row" style={{ gap: 6 }}>
          <HardDrive size={14} className="faint" /> Platte
        </span>
        <span className="num faint">
          {bytes(h.diskUsed)} / {bytes(h.diskTotal, 0)}
        </span>
      </div>
      <Meter value={disk} tone={disk > 0.9 ? "red" : disk > 0.8 ? "orange" : ""} />
      <div className="row between tiny faint">
        <span>Online seit {short(h.uptime)}</span>
        <span>{pct(h.iowait, 1)} IO-Wait</span>
      </div>
    </a>
  );
}

function OnlineCard() {
  const players = useStore(live, (s) => s.snap?.players ?? []);
  const me = useStore(session, (s) => s.me);
  return (
    <Card>
      <CardHead
        title="Online"
        icon={<Users />}
        sub={players.length ? `${players.length}` : undefined}
        right={
          <a href={href("players")} className="btn ghost sm">
            Alle <ChevronRight />
          </a>
        }
      />
      {players.length === 0 ? (
        <div className="small faint">Gerade ist niemand online.</div>
      ) : (
        <div className="row wrap" style={{ gap: 8 }}>
          {players.slice(0, 30).map((p) => (
            <a key={p.uuid} href={href("players", p.uuid)} title={`${p.name} · ${serverName(me, p.server)}`} style={{ transition: "transform 140ms" }}>
              <Avatar uuid={p.uuid} name={p.name} size="sm" />
            </a>
          ))}
          {players.length > 30 && <span className="badge">+{players.length - 30}</span>}
        </div>
      )}
    </Card>
  );
}
