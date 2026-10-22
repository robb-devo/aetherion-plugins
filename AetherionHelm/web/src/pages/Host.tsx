import { useMemo, useState } from "react";
import { Archive, Cpu, Database, Gauge, HardDrive, MemoryStick, Network, Server as ServerIcon, ShieldAlert, ShieldCheck, Thermometer } from "lucide-react";
import { useApi } from "../lib/api";
import { live } from "../lib/live";
import { useStore } from "../lib/store";
import { serverName, session } from "../lib/session";
import { getPref, setPref } from "../lib/prefs";
import { ago, byteRate, bytes, duration, n0, n1, pct, rate, short, loadTone } from "../lib/format";
import type { HostDetail, Point } from "../lib/types";
import { AreaChart, Donut, PALETTE, Spark } from "../components/charts";
import { Badge, Banner, Card, CardHead, Empty, KV, Meter, PageHead, Segmented, Skeleton, Stat } from "../components/ui";
import { toneColor } from "./Overview";

type Range = "1h" | "6h" | "24h" | "7d" | "30d";

function coreColor(v: number) {
  if (v < 0.5) return "linear-gradient(180deg, #5fd4ff, #8f7bff)";
  if (v < 0.75) return "linear-gradient(180deg, #ffe066, #ffb340)";
  if (v < 0.9) return "linear-gradient(180deg, #ffb340, #ff8a00)";
  return "linear-gradient(180deg, #ff6a5f, #ff3b30)";
}

export function Host() {
  const me = useStore(session, (s) => s.me);
  const { data: h, error } = useApi<HostDetail>("/api/host", { every: 3000 });
  const series = useStore(live, (s) => s.series);
  const seriesT = useStore(live, (s) => s.seriesT);
  const servers = useStore(live, (s) => s.snap?.servers ?? []);
  const [range, setRange] = useState<Range>(() => getPref("host.range", "6h"));
  const { data: hist } = useApi<Point[]>(`/api/host/history?range=${range}`, { every: 60000 });
  const [sort, setSort] = useState<"cpu" | "rss">("cpu");

  const liveData = useMemo(
    () =>
      seriesT.map((t, i) => ({
        t,
        cpu: (series["host.cpu"]?.[i] ?? NaN) * 100,
        mem: (series["host.mem"]?.[i] ?? NaN) * 100,
        rx: series["host.rx"]?.[i],
        tx: series["host.tx"]?.[i],
        r: series["host.diskRead"]?.[i],
        w: series["host.diskWrite"]?.[i],
      })),
    [series, seriesT],
  );

  if (error) return <div className="page"><Banner tone="red">{error.message}</Banner></div>;
  if (!h) {
    return (
      <div className="page">
        <PageHead title="Hetzner" sub="Lade Maschinendaten …" />
        <div className="grid g4">{[0, 1, 2, 3].map((i) => <Card key={i}><Skeleton h={120} /></Card>)}</div>
      </div>
    );
  }

  const st = h.static ?? {};
  const memFrac = h.memTotal ? (h.memUsed ?? 0) / h.memTotal : 0;
  const cacheFrac = h.memTotal ? (h.memCached ?? 0) / h.memTotal : 0;
  const swapFrac = h.swapTotal ? (h.swapUsed ?? 0) / h.swapTotal : 0;
  const diskFrac = h.diskTotal ? (h.diskUsed ?? 0) / h.diskTotal : 0;
  const loadPerCore = h.load && h.cores ? h.load[0] / h.cores : undefined;
  const raidBad = (st.raid ?? []).filter((r) => r.degraded || (r.sync && /recovery|resync/.test(r.sync)));

  // who holds the RAM: every server process, then everything else
  const rssParts = servers.filter((s) => s.rss).map((s, i) => ({ label: s.name, value: s.rss!, color: PALETTE[i % PALETTE.length] }));
  const rssSum = rssParts.reduce((a, p) => a + p.value, 0);
  const other = Math.max(0, (h.memUsed ?? 0) - rssSum);

  const procs = [...(h.processes ?? [])].sort((a, b) => (sort === "cpu" ? b.cpu - a.cpu : b.rss - a.rss)).slice(0, 18);
  const sizes = h.ctl?.sizes;
  const sizeRows = Object.entries(sizes?.servers ?? {}).sort((a, b) => (b[1].bytes ?? 0) - (a[1].bytes ?? 0));
  const sizeMax = Math.max(1, ...sizeRows.map((r) => r[1].bytes ?? 0));

  return (
    <div className="page">
      <PageHead
        title="Hetzner"
        sub={[st.vendor && st.product ? `${st.vendor} ${st.product}` : null, st.hostname, st.os, h.uptime ? `online seit ${duration(h.uptime)}` : null].filter(Boolean).join(" · ")}
      />

      {raidBad.length > 0 && (
        <div className="mb">
          <Banner tone="red" icon={<ShieldAlert />}>
            <b>RAID braucht Aufmerksamkeit:</b> {raidBad.map((r) => `${r.name} ${r.degraded ? "läuft degradiert" : r.sync}`).join(", ")}
          </Banner>
        </div>
      )}

      <div className="grid g4">
        <Card>
          <Stat icon={<Cpu />} label="CPU" value={<span style={{ color: toneColor(loadTone(h.cpu)) }}>{n0((h.cpu ?? 0) * 100)}</span>} unit="%" hint={`IO-Wait ${pct(h.iowait, 1)} · Steal ${pct(h.steal, 1)}`} />
          <div className="core-grid mt">
            {(h.perCore ?? []).map((v, i) => (
              <div className="core" key={i} title={`Kern ${i}: ${pct(v)}`}>
                <span>{Math.round(v * 100)}</span>
                <i style={{ height: `${Math.max(4, v * 100)}%`, background: coreColor(v) }} />
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <Stat icon={<MemoryStick />} label="Arbeitsspeicher" value={bytes(h.memUsed).split(" ")[0]} unit={`${bytes(h.memUsed).split(" ")[1]} von ${bytes(h.memTotal, 0)}`} hint={`${pct(memFrac)} belegt · ${bytes(h.memCached)} Cache`} />
          <div className="mt" style={{ position: "relative" }}>
            <div className="meter lg">
              <i style={{ width: `${memFrac * 100}%`, background: memFrac > 0.92 ? "linear-gradient(90deg, #ff3b30, #ff6a5f)" : "var(--accent-grad)" }} />
              <i style={{ left: `${memFrac * 100}%`, width: `${Math.min(cacheFrac, 1 - memFrac) * 100}%`, background: "color-mix(in srgb, var(--c2) 35%, transparent)", borderRadius: 0 }} />
            </div>
            <div className="row between tiny faint mt-s">
              <span>Swap {bytes(h.swapUsed)} / {bytes(h.swapTotal, 0)}</span>
              <span>{pct(swapFrac)}</span>
            </div>
          </div>
          <div className="stat-spark">
            <Spark values={(series["host.mem"] ?? []).map((v) => v * 100)} max={100} color="var(--c2)" height={40} />
          </div>
        </Card>
        <Card>
          <Stat icon={<HardDrive />} label="Platte (/)" value={n0(diskFrac * 100)} unit="%" hint={`${bytes(h.diskUsed)} von ${bytes(h.diskTotal, 0)} · ${bytes((h.diskTotal ?? 0) - (h.diskUsed ?? 0))} frei`} />
          <div className="mt">
            <Meter value={diskFrac} lg tone={diskFrac > 0.9 ? "red" : diskFrac > 0.8 ? "orange" : ""} />
            <div className="row between tiny faint mt-s">
              <span>Lesen {byteRate(h.diskRead)}</span>
              <span>Schreiben {byteRate(h.diskWrite)}</span>
            </div>
          </div>
          <div className="stat-spark">
            <Spark values={series["host.diskWrite"] ?? []} color="var(--c5)" height={40} />
          </div>
        </Card>
        <Card>
          <Stat icon={<Gauge />} label="Load" value={n1(h.load?.[0])} unit={`auf ${h.cores ?? "?"} Threads`} hint={`5 Min. ${n1(h.load?.[1])} · 15 Min. ${n1(h.load?.[2])}`} tone="" />
          <div className="mt">
            <Meter value={loadPerCore ?? 0} lg tone={loadPerCore == null ? "" : loadPerCore > 1 ? "red" : loadPerCore > 0.75 ? "orange" : "green"} />
            <div className="row between tiny faint mt-s">
              <span>{h.procs ?? "?"} Prozesse</span>
              <span>{pct(loadPerCore)} pro Thread</span>
            </div>
          </div>
          <div className="stat-spark">
            <Spark values={series["host.load"] ?? []} max={h.cores} color="var(--c4)" height={40} />
          </div>
        </Card>
      </div>

      <div className="grid g2 mt">
        <Card>
          <CardHead title="Live: CPU und RAM" icon={<Cpu />} sub="letzte 5 Minuten" />
          <AreaChart
            data={liveData}
            height={180}
            yMax={100}
            yFormat={(v) => n0(v) + "%"}
            series={[
              { key: "cpu", label: "CPU", color: "var(--c1)", format: (v) => n1(v) + " %" },
              { key: "mem", label: "RAM", color: "var(--c2)", area: false, format: (v) => n1(v) + " %" },
            ]}
            empty="Sammle Live-Daten …"
          />
        </Card>
        <Card>
          <CardHead
            title="Live: Netzwerk"
            icon={<Network />}
            right={
              <span className="tiny faint">
                ↓ {rate(h.rx)} · ↑ {rate(h.tx)}
              </span>
            }
          />
          <AreaChart
            data={liveData}
            height={180}
            yFormat={(v) => rate(v).replace("bit/s", "")}
            series={[
              { key: "tx", label: "Raus", color: "var(--c1)", format: rate },
              { key: "rx", label: "Rein", color: "var(--c3)", format: rate },
            ]}
            empty="Sammle Live-Daten …"
          />
        </Card>
      </div>

      <h2 className="section">
        Verlauf
        <span style={{ marginLeft: "auto" }}>
          <Segmented
            value={range}
            onChange={(r) => {
              setRange(r);
              setPref("host.range", r);
            }}
            options={[
              { value: "1h", label: "1 Std." },
              { value: "6h", label: "6 Std." },
              { value: "24h", label: "24 Std." },
              { value: "7d", label: "7 T." },
              { value: "30d", label: "30 T." },
            ]}
          />
        </span>
      </h2>
      <div className="grid g2">
        <Card>
          <CardHead title="CPU und Load" icon={<Cpu />} />
          <AreaChart
            data={(hist ?? []).map((p) => ({ t: p.t, cpu: p.cpu != null ? (p.cpu as number) * 100 : null, load: p.load1 != null && h.cores ? ((p.load1 as number) / h.cores) * 100 : null }))}
            height={190}
            yFormat={(v) => n0(v) + "%"}
            series={[
              { key: "cpu", label: "CPU", color: "var(--c1)", format: (v) => n1(v) + " %" },
              { key: "load", label: "Load pro Thread", color: "var(--c4)", area: false, format: (v) => n0(v) + " %" },
            ]}
          />
        </Card>
        <Card>
          <CardHead title="Arbeitsspeicher" icon={<MemoryStick />} />
          <AreaChart data={hist ?? []} height={190} yMax={h.memTotal} yFormat={(v) => bytes(v, 0)} series={[{ key: "mem", label: "Belegt", color: "var(--c2)", format: (v) => bytes(v) }, { key: "swap", label: "Swap", color: "var(--c6)", area: false, format: (v) => bytes(v) }]} />
        </Card>
        <Card>
          <CardHead title="Netzwerk" icon={<Network />} />
          <AreaChart data={hist ?? []} height={190} yFormat={(v) => rate(v).replace("bit/s", "")} series={[{ key: "tx", label: "Raus", color: "var(--c1)", format: rate }, { key: "rx", label: "Rein", color: "var(--c3)", format: rate }]} />
        </Card>
        <Card>
          <CardHead title="Platten-IO" icon={<HardDrive />} />
          <AreaChart data={hist ?? []} height={190} yFormat={(v) => bytes(v, 0)} series={[{ key: "diskWrite", label: "Schreiben", color: "var(--c5)", format: byteRate }, { key: "diskRead", label: "Lesen", color: "var(--c2)", format: byteRate }]} />
        </Card>
      </div>

      <div className="grid g-split mt" style={{ marginTop: 24 }}>
        <Card className="flush">
          <div style={{ padding: "18px 20px 4px" }}>
            <CardHead
              title="Prozesse"
              icon={<ServerIcon />}
              right={<Segmented value={sort} onChange={setSort} options={[{ value: "cpu", label: "CPU" }, { value: "rss", label: "RAM" }]} />}
            />
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Prozess</th>
                  <th className="r">CPU</th>
                  <th className="r">RAM</th>
                  <th className="r hide-m">Threads</th>
                  <th className="r hide-m">läuft</th>
                </tr>
              </thead>
              <tbody>
                {procs.map((p) => (
                  <tr key={p.pid}>
                    <td>
                      <div className="row" style={{ gap: 8 }}>
                        <span className="strong small">{p.server ? serverName(me, p.server) : p.name}</span>
                        {p.server && <Badge tone="accent">{p.name}</Badge>}
                      </div>
                      <div className="tiny faint mono">pid {p.pid}{p.user ? ` · ${p.user}` : ""}</div>
                    </td>
                    <td className="r num" style={{ color: p.cpu > 0.5 ? "var(--orange)" : undefined }}>{pct(p.cpu, 1)}</td>
                    <td className="r num">{bytes(p.rss)}</td>
                    <td className="r num hide-m">{p.threads}</td>
                    <td className="r num hide-m faint">{p.started ? short(Date.now() - p.started) : "–"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
        <div className="col" style={{ gap: 16 }}>
          <Card>
            <CardHead title="Wer belegt den RAM" icon={<MemoryStick />} />
            <div className="row" style={{ gap: 18, alignItems: "center" }}>
              <Donut parts={[...rssParts, { label: "Rest", value: other, color: "var(--text-4)" }]} size={128} stroke={14}>
                <b>{pct(memFrac)}</b>
                <span>belegt</span>
              </Donut>
              <div className="col grow" style={{ gap: 6 }}>
                {rssParts.map((p) => (
                  <div className="row between small" key={p.label}>
                    <span className="row" style={{ gap: 6 }}>
                      <i style={{ width: 8, height: 8, borderRadius: 3, background: p.color }} />
                      {p.label}
                    </span>
                    <span className="num faint">{bytes(p.value)}</span>
                  </div>
                ))}
                <div className="row between small">
                  <span className="row" style={{ gap: 6 }}>
                    <i style={{ width: 8, height: 8, borderRadius: 3, background: "var(--text-4)" }} />
                    System und Rest
                  </span>
                  <span className="num faint">{bytes(other)}</span>
                </div>
              </div>
            </div>
          </Card>
          <Card>
            <CardHead title="Speicherplatz" icon={<Archive />} />
            {sizeRows.length === 0 && !sizes?.backups ? (
              <div className="small faint">Die Größen werden alle 10 Minuten gemessen.</div>
            ) : (
              <div className="col" style={{ gap: 10 }}>
                {sizeRows.map(([id, v]) => (
                  <div className="col" style={{ gap: 4 }} key={id}>
                    <div className="row between small">
                      <span>{serverName(me, id)}</span>
                      <span className="num faint">{bytes(v.bytes)}</span>
                    </div>
                    <Meter value={(v.bytes ?? 0) / sizeMax} />
                  </div>
                ))}
                {sizes?.backups && (
                  <div className="row mt-s" style={{ padding: 12, borderRadius: 14, background: "var(--well)" }}>
                    <Database size={18} className="accent" />
                    <div className="grow">
                      <div className="strong small">Backups: {bytes(sizes.backups.bytes)}</div>
                      <div className="tiny faint">
                        {sizes.backups.count ?? 0} Snapshots{sizes.backups.latest ? ` · letzter ${ago(sizes.backups.latest)}` : ""}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </Card>
        </div>
      </div>

      <div className="grid g3 mt" style={{ marginTop: 24 }}>
        <Card>
          <CardHead title="Dateisysteme" icon={<HardDrive />} />
          <div className="col" style={{ gap: 12 }}>
            {(h.filesystems ?? []).map((f) => {
              const fr = f.total ? f.used / f.total : 0;
              return (
                <div key={f.mount} className="col" style={{ gap: 4 }}>
                  <div className="row between small">
                    <span className="mono">{f.mount}</span>
                    <span className="num faint">{bytes(f.used)} / {bytes(f.total, 0)}</span>
                  </div>
                  <Meter value={fr} tone={fr > 0.9 ? "red" : fr > 0.8 ? "orange" : ""} />
                  <span className="tiny faint">{f.device} · {f.type}</span>
                </div>
              );
            })}
          </div>
        </Card>
        <Card>
          <CardHead title="Laufwerke und RAID" icon={(st.raid ?? []).some((r) => r.degraded) ? <ShieldAlert /> : <ShieldCheck />} />
          <div className="col" style={{ gap: 10 }}>
            {(st.raid ?? []).map((r) => (
              <div key={r.name} className="row" style={{ padding: 10, borderRadius: 12, background: "var(--well)" }}>
                <span className={r.degraded ? "dot red pulse" : "dot green"} />
                <div className="grow">
                  <div className="strong small">{r.name} · {r.level}</div>
                  <div className="tiny faint">{r.devices.join(", ")} {r.map ? `· ${r.map}` : ""}</div>
                  {r.sync && <div className="tiny orange">{r.sync}</div>}
                </div>
                <Badge tone={r.degraded ? "red" : "green"}>{r.degraded ? "degradiert" : r.state}</Badge>
              </div>
            ))}
            {(st.disks ?? []).map((d) => {
              const io = h.disks?.find((x) => x.name === d.name);
              return (
                <div key={d.name} className="row">
                  <HardDrive size={16} className="faint" />
                  <div className="grow">
                    <div className="small strong">{d.name} <span className="faint" style={{ fontWeight: 400 }}>{d.model}</span></div>
                    <div className="tiny faint">{bytes(d.size, 0)} · {d.ssd ? "SSD/NVMe" : "HDD"}{io ? ` · ${pct(io.busy)} ausgelastet` : ""}</div>
                  </div>
                </div>
              );
            })}
            {!(st.raid ?? []).length && !(st.disks ?? []).length && <div className="small faint">Keine Laufwerksdaten.</div>}
          </div>
        </Card>
        <Card>
          <CardHead title="Hardware" icon={<Cpu />} />
          <KV
            rows={[
              ["CPU", st.cpuModel],
              ["Kerne", st.cores ? `${st.cores} Kerne · ${st.threads} Threads` : undefined],
              ["Takt", st.mhz ? `${n0(st.mhz)} MHz` : undefined],
              ["Kernel", st.kernel],
              ["Java", st.java],
              ...((st.nics ?? []).map((n) => [n.name, `${n.speed ? (n.speed >= 1000 ? n.speed / 1000 + " Gbit/s" : n.speed + " Mbit/s") : "?"} · ${n.state}`]) as [string, string][]),
              ["Gesendet gesamt", bytes(h.txTotal)],
              ["Empfangen gesamt", bytes(h.rxTotal)],
              ["Panel läuft seit", h.gatewayUptime ? duration(h.gatewayUptime) : undefined],
            ]}
          />
        </Card>
      </div>

      {(h.temps ?? []).length > 0 && (
        <>
          <h2 className="section">Temperaturen</h2>
          <div className="grid g-auto-sm">
            {h.temps!.map((t, i) => {
              const crit = t.crit ?? 100;
              const f = t.c / crit;
              return (
                <Card key={i} className="pad-s">
                  <div className="row">
                    <Thermometer size={18} style={{ color: f > 0.85 ? "var(--red)" : f > 0.7 ? "var(--orange)" : "var(--green)" }} />
                    <div className="grow">
                      <div className="strong num">{n0(t.c)} °C</div>
                      <div className="tiny faint ellipsis">{t.chip} · {t.label}</div>
                    </div>
                  </div>
                </Card>
              );
            })}
          </div>
        </>
      )}
      {h.error && <Empty title="Teilweise keine Daten" text={h.error} />}
    </div>
  );
}
