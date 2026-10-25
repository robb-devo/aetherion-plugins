import { useEffect, useState } from "react";
import { Ban, History, KeyRound, LogIn, MessageSquare, Power, ScrollText, Settings, ShieldCheck, Terminal, Users } from "lucide-react";
import { api, useApi } from "../lib/api";
import { onAudit } from "../lib/live";
import { ago, maskIp, stamp } from "../lib/format";
import type { AuditRow, TeamUser } from "../lib/types";
import { Badge, Button, Card, Empty, PageHead, Skeleton, cx, useTick } from "../components/ui";
import { t } from "../lib/i18n";

const LABELS: Record<string, string> = {
  login: t("Signed in"),
  "login.failed": t("Failed sign-in"),
  "2fa.enabled": t("Two-factor set up"),
  "password.changed": t("Password changed"),
  "session.revoked": t("Session ended"),
  "console.command": t("Console command"),
  "server.start": t("Server started"),
  "server.stop": t("Server stopped"),
  "server.restart": t("Server restarted"),
  "server.kill": t("Server killed"),
  "server.save": t("Worlds saved"),
  "player.inventory": t("Viewed inventory"),
  "player.kick": t("Kicked"),
  "player.ban": t("Banned"),
  "player.unban": t("Unbanned"),
  "player.mute": t("Muted"),
  "player.unmute": t("Unmuted"),
  "player.warn": t("Warned"),
  "player.message": t("Message sent"),
  "player.send": t("Moved"),
  "player.note": t("Note"),
  "chat.broadcast": "Broadcast",
  "report.resolved": t("Report resolved"),
  "alert.ack": t("Alert acknowledged"),
  "team.created": t("Account created"),
  "team.updated": t("Account updated"),
  "team.removed": t("Account removed"),
  "team.reset-2fa": t("2FA reset"),
  "team.reset-password": t("Password reset"),
  "settings.changed": t("Settings changed"),
};

const GROUPS: { id: string; label: string; prefix: string; icon: React.ReactNode }[] = [
  { id: "", label: t("All"), prefix: "", icon: <ScrollText /> },
  { id: "player", label: "Moderation", prefix: "player", icon: <Ban /> },
  { id: "console", label: t("Console"), prefix: "console", icon: <Terminal /> },
  { id: "server", label: "Server", prefix: "server", icon: <Power /> },
  { id: "chat", label: "Chat", prefix: "chat", icon: <MessageSquare /> },
  { id: "login", label: t("Sign-ins"), prefix: "login", icon: <LogIn /> },
  { id: "team", label: "Team", prefix: "team", icon: <Users /> },
  { id: "settings", label: t("Settings"), prefix: "settings", icon: <Settings /> },
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
      <PageHead title={t("Audit log")} sub={t("Every action in the panel, with who, when and from where. Cannot be deleted.")} />
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
          <option value="">{t("Everyone")}</option>
          {(team?.users ?? []).map((u) => (
            <option key={u.id} value={u.display}>{u.display}</option>
          ))}
        </select>
      </div>
      <Card className="flush">
        {loading && !data ? (
          <div style={{ padding: 16 }}><Skeleton h={200} /></div>
        ) : !data?.length ? (
          <Empty icon={<ScrollText />} title={t("No entries")} />
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
            <Button variant="ghost" icon={<History />} busy={more} onClick={older}>{t("Load older")}</Button>
          </div>
        )}
      </Card>
    </div>
  );
}
