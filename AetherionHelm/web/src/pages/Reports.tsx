import { useState } from "react";
import { CheckCircle2, Flag, Inbox } from "lucide-react";
import { post, useApi } from "../lib/api";
import { useStore } from "../lib/store";
import { can, serverName, session } from "../lib/session";
import { href } from "../lib/router";
import { ago, stamp } from "../lib/format";
import { attempt } from "../lib/toast";
import type { Report } from "../lib/types";
import { onEvent } from "../lib/live";
import { Sheet } from "../components/overlay";
import { Avatar, Badge, Button, Card, Empty, Field, PageHead, Segmented, Skeleton, useTick } from "../components/ui";
import { useEffect } from "react";

const QUICK = ["Erledigt, ermahnt", "Gemutet", "Gebannt", "Kein Verstoß gefunden", "Doppelte Meldung"];

export function Reports() {
  const me = useStore(session, (s) => s.me)!;
  const [status, setStatus] = useState<"open" | "resolved" | "all">("open");
  const { data, loading, reload } = useApi<Report[]>(`/api/reports${status === "all" ? "" : `?status=${status}`}`, { every: 30000 });
  const [resolve, setResolve] = useState<Report | null>(null);
  useTick(20000);
  useEffect(() => onEvent((e) => e.kind === "report" && reload()), [reload]);

  return (
    <div className="page">
      <PageHead
        title="Meldungen"
        sub="Was Spieler mit /report oder /melden im Spiel einreichen."
        actions={
          <Segmented
            value={status}
            onChange={setStatus}
            options={[
              { value: "open", label: "Offen" },
              { value: "resolved", label: "Erledigt" },
              { value: "all", label: "Alle" },
            ]}
          />
        }
      />
      {loading && !data ? (
        <Card><Skeleton h={160} /></Card>
      ) : !data?.length ? (
        <Card>
          <Empty icon={status === "open" ? <CheckCircle2 /> : <Inbox />} title={status === "open" ? "Keine offenen Meldungen" : "Nichts hier"} text={status === "open" ? "Alles abgearbeitet. Neue Meldungen erscheinen hier sofort." : undefined} />
        </Card>
      ) : (
        <div className="col" style={{ gap: 12 }}>
          {data.map((r) => (
            <Card key={r.id} className="pad-s" style={{ padding: 18 }}>
              <div className="row top" style={{ gap: 14 }}>
                <span className={`icon-tile ${r.status === "open" ? "orange" : "green"}`}>{r.status === "open" ? <Flag /> : <CheckCircle2 />}</span>
                <div className="grow col" style={{ gap: 6 }}>
                  <div className="row wrap" style={{ gap: 8 }}>
                    {r.targetUuid ? (
                      <a href={href("players", r.targetUuid)} className="row strong" style={{ gap: 8 }}>
                        <Avatar uuid={r.targetUuid} name={r.targetName} size="xs" />
                        {r.targetName}
                      </a>
                    ) : (
                      <b>{r.targetName ?? "Allgemein"}</b>
                    )}
                    <span className="faint small">gemeldet von</span>
                    {r.reporterUuid ? <a href={href("players", r.reporterUuid)} className="small strong">{r.reporterName}</a> : <span className="small">{r.reporterName}</span>}
                    <Badge outline>{serverName(me, r.server)}</Badge>
                    <span className="tiny faint" title={stamp(r.ts)}>{ago(r.ts)}</span>
                  </div>
                  <div style={{ fontSize: 14.5 }}>{r.reason}</div>
                  {r.status === "resolved" && (
                    <div className="small muted">
                      Erledigt von <b>{r.resolvedBy}</b> {ago(r.resolvedAt)}
                      {r.resolution ? `: ${r.resolution}` : ""}
                    </div>
                  )}
                </div>
                {r.status === "open" && can(me, "reports.manage") && (
                  <Button size="sm" variant="tinted" tone="green" icon={<CheckCircle2 />} onClick={() => setResolve(r)}>
                    Erledigen
                  </Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
      {resolve && <ResolveSheet r={resolve} onClose={() => setResolve(null)} onDone={reload} />}
    </div>
  );
}

function ResolveSheet({ r, onClose, onDone }: { r: Report; onClose: () => void; onDone: () => void }) {
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  return (
    <Sheet
      title="Meldung erledigen"
      sub={`${r.reporterName} über ${r.targetName ?? "allgemein"}: „${r.reason}“`}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>Abbrechen</Button>
          <Button
            variant="primary"
            busy={busy}
            onClick={async () => {
              setBusy(true);
              if (await attempt(() => post(`/api/reports/${r.id}/resolve`, { resolution: text.trim() || undefined }), "Meldung erledigt")) {
                onDone();
                close();
              }
              setBusy(false);
            }}
          >
            Erledigt
          </Button>
        </>
      )}
    >
      <Field label="Was wurde gemacht?" help="Optional. Steht danach bei der Meldung und im Protokoll.">
        <input className="input" value={text} onChange={(e) => setText(e.target.value)} placeholder="z. B. Gemutet für 1 Std." autoFocus />
        <div className="dur-presets">
          {QUICK.map((q) => (
            <button key={q} className={"chip" + (text === q ? " on" : "")} onClick={() => setText(q)}>{q}</button>
          ))}
        </div>
      </Field>
    </Sheet>
  );
}
