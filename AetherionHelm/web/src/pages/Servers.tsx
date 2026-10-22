import { LayoutGrid, Rows3 } from "lucide-react";
import { useState } from "react";
import { live } from "../lib/live";
import { getPref, setPref } from "../lib/prefs";
import { useStore } from "../lib/store";
import { go } from "../lib/router";
import { bytes, n1, pct, short, msptTone, tpsTone } from "../lib/format";
import { Card, PageHead, Segmented, StateChip } from "../components/ui";
import { ServerTile, toneColor } from "./Overview";
import { t } from "../lib/i18n";

export function Servers() {
  const snap = useStore(live, (s) => s.snap);
  const series = useStore(live, (s) => s.series);
  const [view, setView] = useState<"tiles" | "table">(() => getPref("servers.view", "tiles"));
  const servers = snap?.servers ?? [];
  const setV = (v: "tiles" | "table") => {
    setView(v);
    setPref("servers.view", v);
  };
  return (
    <div className="page">
      <PageHead
        title="Server"
        sub={t("{up} of {all} online · {p} players", { up: servers.filter((s) => s.state === "online").length, all: servers.length, p: snap?.network.online ?? 0 })}
        actions={
          <Segmented
            value={view}
            onChange={setV}
            options={[
              { value: "tiles", label: t("Tiles"), icon: <LayoutGrid /> },
              { value: "table", label: t("Table"), icon: <Rows3 /> },
            ]}
          />
        }
      />
      {view === "tiles" ? (
        <div className="grid g-auto">
          {servers.map((s) => (
            <ServerTile key={s.id} s={s} spark={series[`${s.id}.players`]} />
          ))}
        </div>
      ) : (
        <Card className="flush">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Server</th>
                  <th>Status</th>
                  <th className="r">{t("Players")}</th>
                  <th className="r">TPS 1 · 5 · 15</th>
                  <th className="r">{t("MSPT avg · P95")}</th>
                  <th className="r">Heap</th>
                  <th className="r">CPU</th>
                  <th className="r">{t("RAM (process)")}</th>
                  <th className="r">{t("Uptime")}</th>
                  <th className="r">Entities</th>
                </tr>
              </thead>
              <tbody>
                {servers.map((s) => (
                  <tr key={s.id} className="clickable" onClick={() => go("servers", s.id)}>
                    <td>
                      <div className="strong">{s.name}</div>
                      <div className="tiny faint">{s.role}</div>
                    </td>
                    <td>
                      <StateChip state={s.state} />
                    </td>
                    <td className="r num">{s.players}</td>
                    <td className="r num">
                      {s.tps ? (
                        <>
                          <b style={{ color: toneColor(tpsTone(s.tps[0])) }}>{n1(s.tps[0])}</b>
                          <span className="faint"> · {n1(s.tps[1])} · {n1(s.tps[2])}</span>
                        </>
                      ) : (
                        "–"
                      )}
                    </td>
                    <td className="r num">
                      {s.mspt != null ? (
                        <>
                          <b style={{ color: toneColor(msptTone(s.mspt)) }}>{n1(s.mspt)}</b>
                          <span className="faint"> · {n1(s.msptP95)}</span>
                        </>
                      ) : (
                        "–"
                      )}
                    </td>
                    <td className="r num">{s.heapMax ? `${pct((s.heapUsed ?? 0) / s.heapMax)}` : "–"}</td>
                    <td className="r num">{s.cpu != null ? pct(s.cpu, 1) : "–"}</td>
                    <td className="r num">{s.rss ? bytes(s.rss) : "–"}</td>
                    <td className="r num">{s.uptime ? short(s.uptime) : "–"}</td>
                    <td className="r num">{s.entities ?? "–"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
