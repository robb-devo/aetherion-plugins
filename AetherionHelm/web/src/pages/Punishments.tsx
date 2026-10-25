import { useState } from "react";
import { Ban, Gavel, MicOff, Search, ShieldCheck, TriangleAlert, UserX } from "lucide-react";
import { useApi } from "../lib/api";
import { useStore } from "../lib/store";
import { can, session } from "../lib/session";
import { href } from "../lib/router";
import { ago, stamp, until } from "../lib/format";
import type { Punishment } from "../lib/types";
import { revoke } from "../components/actions";
import { openSheet } from "../components/nav";
import { Avatar, Badge, Button, Card, CardHead, Empty, PageHead, Segmented, Skeleton, useTick } from "../components/ui";
import { t } from "../lib/i18n";

const ICON = { ban: <Ban />, mute: <MicOff />, kick: <UserX />, warn: <TriangleAlert /> };
const TONE = { ban: "red", mute: "orange", kick: "orange", warn: "yellow" };
const LABEL = { ban: t("Ban"), mute: "Mute", kick: "Kick", warn: t("Warning") };

export function Punishments() {
  const me = useStore(session, (s) => s.me)!;
  const { data, loading, reload } = useApi<{ active: Punishment[]; recent: Punishment[] }>("/api/punishments", { every: 30000 });
  const [type, setType] = useState<"all" | "ban" | "mute">("all");
  const [q, setQ] = useState("");
  useTick(30000);

  const act = (data?.active ?? []).filter((p) => (type === "all" || p.type === type) && (!q || (p.name ?? "").toLowerCase().includes(q.toLowerCase())));

  return (
    <div className="page">
      <PageHead
        title={t("Punishments")}
        sub={data ? t("{b} active bans · {m} active mutes", { b: data.active.filter((p) => p.type === "ban").length, m: data.active.filter((p) => p.type === "mute").length }) : "…"}
        actions={
          <>
            {can(me, "players.mute") && <Button icon={<MicOff />} onClick={() => openSheet({ kind: "punish", type: "mute" })}>{t("Mute")}</Button>}
            {can(me, "players.ban") && <Button variant="danger" icon={<Ban />} onClick={() => openSheet({ kind: "punish", type: "ban" })}>{t("Ban")}</Button>}
          </>
        }
      />
      <div className="grid g-split">
        <Card className="flush">
          <div style={{ padding: "18px 20px 6px" }}>
            <CardHead title={t("Active")} icon={<Gavel />} right={<Segmented value={type} onChange={setType} options={[{ value: "all", label: t("All") }, { value: "ban", label: "Bans" }, { value: "mute", label: "Mutes" }]} />} />
            <div className="input-icon" style={{ marginBottom: 8 }}>
              <Search />
              <input className="input" placeholder={t("Filter players")} value={q} onChange={(e) => setQ(e.target.value)} />
            </div>
          </div>
          {loading && !data ? (
            <div style={{ padding: 16 }}><Skeleton h={120} /></div>
          ) : act.length === 0 ? (
            <Empty icon={<ShieldCheck />} title={t("Nothing active")} text={t("No active bans or mutes.")} />
          ) : (
            <div className="list">
              {act.map((p) => (
                <div key={p.id} className="list-item inset">
                  <Avatar uuid={p.uuid} name={p.name} size="sm" />
                  <div className="grow">
                    <div className="row wrap" style={{ gap: 8 }}>
                      <a href={href("players", p.uuid!)} className="title">{p.name}</a>
                      <Badge tone={TONE[p.type]} icon={ICON[p.type]}>{LABEL[p.type]}</Badge>
                      <span className="tiny faint">{until(p.expires)}</span>
                    </div>
                    <div className="sub">{p.reason || t("no reason")} · {p.byUser} · {ago(p.created)}</div>
                  </div>
                  {can(me, p.type === "ban" ? "players.ban" : "players.mute") && (
                    <Button size="sm" variant="ghost" onClick={async () => (await revoke(p.type as "ban" | "mute", p.uuid!, p.name ?? "")) && reload()}>
                      {t("Revoke")}
                    </Button>
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>
        <Card className="flush">
          <div style={{ padding: "18px 20px 4px" }}>
            <CardHead title={t("Last seen")} icon={<Gavel />} sub={t("all punishments, newest first")} />
          </div>
          {(data?.recent ?? []).length === 0 ? (
            <Empty title={t("No punishments yet")} />
          ) : (
            <div className="list">
              {data!.recent.slice(0, 50).map((p) => (
                <a key={p.id} href={href("players", p.uuid!)} className="list-item inset">
                  <span className={`icon-tile sm ${TONE[p.type]}`}>{ICON[p.type]}</span>
                  <div className="grow">
                    <div className="row" style={{ gap: 6 }}>
                      <span className="title">{p.name}</span>
                      {p.revokedAt && <Badge outline>aufgehoben</Badge>}
                    </div>
                    <div className="sub ellipsis">{LABEL[p.type]} · {p.reason || t("no reason")} · {p.byUser}</div>
                  </div>
                  <span className="tiny faint" title={stamp(p.created)}>{ago(p.created)}</span>
                </a>
              ))}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
