import { useEffect, useState } from "react";
import { Ban, History, KeyRound, LogIn, MessageSquare, Power, ScrollText, Settings, ShieldCheck, Terminal, Users } from "lucide-react";
import { api, useApi } from "../lib/api";
import { onAudit } from "../lib/live";
import { ago, maskIp, stamp } from "../lib/format";
import type { AuditRow, TeamUser } from "../lib/types";
import { Badge, Button, Card, Empty, PageHead, Skeleton, cx, useTick } from "../components/ui";

const LABELS: Record<string, string> = {
  login: "Angemeldet",
  "login.failed": "Fehlgeschlagene Anmeldung",
  "2fa.enabled": "Zwei-Faktor eingerichtet",
  "password.changed": "Passwort geändert",
  "session.revoked": "Sitzung beendet",
  "console.command": "Konsolenbefehl",
  "server.start": "Server gestartet",
  "server.stop": "Server gestoppt",
  "server.restart": "Server neu gestartet",
  "server.kill": "Server hart beendet",
  "server.save": "Welten gespeichert",
  "player.inventory": "Inventar angesehen",
  "player.kick": "Gekickt",
  "player.ban": "Gebannt",
  "player.unban": "Entbannt",
  "player.mute": "Gemutet",
  "player.unmute": "Entmutet",
  "player.warn": "Verwarnt",
  "player.message": "Nachricht geschickt",
  "player.send": "Verschoben",
  "player.note": "Notiz",
  "chat.broadcast": "Broadcast",
  "report.resolved": "Meldung erledigt",
  "alert.ack": "Warnung bestätigt",
  "team.created": "Konto erstellt",
  "team.updated": "Konto geändert",
  "team.removed": "Konto entfernt",
  "team.reset-2fa": "2FA zurückgesetzt",
  "team.reset-password": "Passwort zurückgesetzt",
  "settings.changed": "Einstellungen geändert",
};

const GROUPS: { id: string; label: string; prefix: string; icon: React.ReactNode }[] = [
  { id: "", label: "Alles", prefix: "", icon: <ScrollText /> },
  { id: "player", label: "Moderation", prefix: "player", icon: <Ban /> },
  { id: "console", label: "Konsole", prefix: "console", icon: <Terminal /> },
  { id: "server", label: "Server", prefix: "server", icon: <Power /> },
  { id: "chat", label: "Chat", prefix: "chat", icon: <MessageSquare /> },
  { id: "login", label: "Anmeldungen", prefix: "login", icon: <LogIn /> },
  { id: "team", label: "Team", prefix: "team", icon: <Users /> },
  { id: "settings", label: "Einstellungen", prefix: "settings", icon: <Settings /> },
];

function iconFor(a: string) {
  if (a.startsWith("player")) return { i: <Ban />, t: "red" };
  if (a.startsWith("console")) return { i: <Terminal />, t: "" };
  if (a.startsWith("server")) return { i: <Power />, t: "blue" };
  if (a === "login.failed") return { i: <KeyRound />, t: "orange" };
  if (a.startsWith("login") || a.startsWith("2fa") || a.startsWith("password") || a.startsWith("session")) return { i: <ShieldCheck />, t: "green" };
  if (a.startsWith("team")) return { i: <Users />, t: "accent" };
  if (a.startsWith("chat")) return { i: <MessageSquare />, t: "accent" };
  return { i: <Settings />, t: "" };
}

export function Audit() {
  const [group, setGroup] = useState("");
  const [user, setUser] = useState("");
  const { data: team } = useApi<{ users: TeamUser[] }>("/api/team");
  const { data, loading, setData } = useApi<AuditRow[]>(`/api/audit?limit=150${group ? `&action=${group}` : ""}${user ? `&user=${encodeURIComponent(user)}` : ""}`);
  const [more, setMore] = useState(false);
  useTick(20000);

  useEffect(
    () =>
      onAudit((r) => {
        if ((group && !r.action.startsWith(group)) || (user && r.user !== user)) return;
        setData((d) => (d ? [r, ...d] : [r]));
      }),
    [group, user, setData],
  );

  const older = async () => {
    const last = data?.[data.length - 1];
    if (!last) return;
    setMore(true);
    try {
      const r = await api<AuditRow[]>(`/api/audit?limit=150&before=${last.id}${group ? `&action=${group}` : ""}${user ? `&user=${encodeURIComponent(user)}` : ""}`);
      setData((d) => [...(d ?? []), ...r]);
    } finally {
      setMore(false);
    }
  };

  return (
    <div className="page">
      <PageHead title="Protokoll" sub="Jede Aktion im Panel, mit Person, Zeit und Herkunft. Nicht löschbar." />
      <div className="row wrap mb" style={{ gap: 10 }}>
        <div className="dur-presets">
          {GROUPS.map((g) => (
            <button key={g.id} className={cx("chip", group === g.prefix && "on")} onClick={() => setGroup(g.prefix)}>
              {g.icon}
              {g.label}
            </button>
          ))}
        </div>
        <select className="select" style={{ width: 200, marginLeft: "auto" }} value={user} onChange={(e) => setUser(e.target.value)}>
          <option value="">Alle Personen</option>
          {(team?.users ?? []).map((u) => (
            <option key={u.id} value={u.display}>{u.display}</option>
          ))}
        </select>
      </div>
      <Card className="flush">
        {loading && !data ? (
          <div style={{ padding: 16 }}><Skeleton h={200} /></div>
        ) : !data?.length ? (
          <Empty icon={<ScrollText />} title="Keine Einträge" />
        ) : (
          <div className="list">
            {data.map((r) => {
              const ic = iconFor(r.action);
              return (
                <div key={r.id} className="list-item inset" style={{ alignItems: "flex-start" }}>
                  <span className={`icon-tile sm ${ic.t}`}>{ic.i}</span>
                  <div className="grow">
                    <div className="row wrap" style={{ gap: 6 }}>
                      <b className="small">{r.user}</b>
                      <span className="small">{LABELS[r.action] ?? r.action}</span>
                      {r.target && <Badge outline>{r.target}</Badge>}
                    </div>
                    {r.detail && <div className={cx("small muted", r.action === "console.command" && "mono")} style={{ marginTop: 2, overflowWrap: "anywhere" }}>{r.detail}</div>}
                    <div className="sub">{stamp(r.ts)} · {ago(r.ts)}{r.ip ? ` · ${maskIp(r.ip)}` : ""}</div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
        {data && data.length >= 150 && (
          <div style={{ padding: 16, display: "flex", justifyContent: "center" }}>
            <Button variant="ghost" icon={<History />} busy={more} onClick={older}>Ältere laden</Button>
          </div>
        )}
      </Card>
    </div>
  );
}
