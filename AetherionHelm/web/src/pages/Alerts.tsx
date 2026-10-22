import { AlertOctagon, AlertTriangle, BellRing, Check, CheckCircle2, Info } from "lucide-react";
import { post, useApi } from "../lib/api";
import { live } from "../lib/live";
import { useStore } from "../lib/store";
import { serverName, session } from "../lib/session";
import { ago, duration, stamp } from "../lib/format";
import { attempt } from "../lib/toast";
import { Badge, Button, Card, Empty, IconTile, PageHead, useTick } from "../components/ui";
import { t } from "../lib/i18n";

interface HistoryRow {
  id: number;
  key: string;
  level: string;
  title: string;
  detail?: string;
  server?: string;
  since: number;
  resolved?: number;
}

const LV = {
  crit: { tone: "red", icon: <AlertOctagon />, label: t("Critical") },
  warn: { tone: "orange", icon: <AlertTriangle />, label: t("Warning") },
  info: { tone: "accent", icon: <Info />, label: t("Info") },
} as Record<string, { tone: string; icon: React.ReactNode; label: string }>;

export function Alerts() {
  const me = useStore(session, (s) => s.me);
  const active = useStore(live, (s) => s.snap?.alerts ?? []);
  const { data } = useApi<{ history: HistoryRow[] }>("/api/alerts", { every: 30000 });
  useTick(15000);
  return (
    <div className="page">
      <PageHead title={t("Alerts")} sub={t("Every 5 seconds Helm checks servers, TPS, memory, disks and the RAID. Anything wrong for more than a few seconds lands here.")} />
      {active.length === 0 ? (
        <Card strong>
          <div className="row" style={{ gap: 16 }}>
            <IconTile tone="green"><CheckCircle2 /></IconTile>
            <div>
              <div className="strong" style={{ fontSize: 16 }}>{t("All good")}</div>
              <div className="small muted">{t("Nothing to do right now.")}</div>
            </div>
          </div>
        </Card>
      ) : (
        <div className="col" style={{ gap: 12 }}>
          {active.map((a) => {
            const l = LV[a.level] ?? LV.info;
            return (
              <Card key={a.key} className="pad-s" style={{ padding: 18 }}>
                <div className="row top" style={{ gap: 14 }}>
                  <IconTile tone={l.tone}>{l.icon}</IconTile>
                  <div className="grow">
                    <div className="row wrap" style={{ gap: 8 }}>
                      <b>{a.title}</b>
                      <Badge tone={l.tone}>{l.label}</Badge>
                      {a.server && <Badge outline>{serverName(me, a.server)}</Badge>}
                    </div>
                    {a.detail && <div className="small muted" style={{ marginTop: 3 }}>{a.detail}</div>}
                    <div className="tiny faint" style={{ marginTop: 4 }}>
                      seit {duration(Date.now() - a.since, 2)}
                      {a.ackedBy ? t(" · seen by {name}", { name: a.ackedBy }) : ""}
                    </div>
                  </div>
                  {!a.ackedBy && (
                    <Button size="sm" icon={<Check />} onClick={() => attempt(() => post(`/api/alerts/${encodeURIComponent(a.key)}/ack`), t("Marked as seen"))}>
                      {t("Seen")}
                    </Button>
                  )}
                </div>
              </Card>
            );
          })}
        </div>
      )}
      <h2 className="section">{t("History")}</h2>
      <Card className="flush">
        {!(data?.history ?? []).length ? (
          <Empty icon={<BellRing />} title={t("No alerts yet")} text={t("This will show what went wrong, when, and for how long.")} />
        ) : (
          <div className="list">
            {data!.history.map((h) => {
              const l = LV[h.level] ?? LV.info;
              return (
                <div key={h.id} className="list-item inset">
                  <span className={`icon-tile sm ${l.tone}`}>{l.icon}</span>
                  <div className="grow">
                    <div className="title">{h.title}</div>
                    <div className="sub">
                      {stamp(h.since)}
                      {h.resolved ? t(" · lasted {d}", { d: duration(h.resolved - h.since, 2) }) : t(" · still active")}
                      {h.server ? ` · ${serverName(me, h.server)}` : ""}
                    </div>
                  </div>
                  <span className="tiny faint">{ago(h.since)}</span>
                </div>
              );
            })}
          </div>
        )}
      </Card>
    </div>
  );
}
