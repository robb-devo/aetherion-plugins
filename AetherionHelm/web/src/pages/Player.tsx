import { useState } from "react";
import { ArrowRightLeft, Ban, CalendarClock, Clock, Copy, Eye, EyeOff, FileText, Fingerprint, Gamepad2, Gavel, Heart, History, MessageSquare, MicOff, NotebookPen, Plane, Send, ShieldCheck, TriangleAlert, User, UserX, Users, Utensils, Wifi } from "lucide-react";
import { post, useApi } from "../lib/api";
import { live } from "../lib/live";
import { useStore } from "../lib/store";
import { can, serverName, session } from "../lib/session";
import { go, href } from "../lib/router";
import { ago, day, duration, maskIp, n0, n1, short, stamp, until, when, pingTone } from "../lib/format";
import { attempt } from "../lib/toast";
import type { PlayerDetail, Punishment } from "../lib/types";
import { useCrumb } from "../App";
import { HeatMap } from "../components/charts";
import { InventoryView } from "../components/Inventory";
import { MessageSheet, PunishSheet, SendSheet, revoke } from "../components/actions";
import { Avatar, Badge, Banner, Button, Card, CardHead, Empty, KV, Meter, Skeleton, Tabs, useTick } from "../components/ui";
import { EventRow } from "./Activity";
import { toneColor } from "./Overview";

type Tab = "overview" | "inventory" | "record" | "links" | "history";

function active(p: Punishment) {
  return !p.revokedAt && (!p.expires || p.expires > Date.now());
}

export function PlayerPage({ uuid, tab: rawTab }: { uuid: string; tab?: string }) {
  const me = useStore(session, (s) => s.me)!;
  const { data, error, reload } = useApi<PlayerDetail>(`/api/players/${uuid}`, { every: 20000 });
  const liveP = useStore(live, (s) => s.snap?.players.find((p) => p.uuid === uuid));
  const [sheet, setSheet] = useState<null | "ban" | "mute" | "kick" | "warn" | "msg" | "send">(null);
  useCrumb("players", data?.name ?? liveP?.name);
  useTick(15000);
  const tab = (rawTab as Tab) || "overview";

  if (error) return <div className="page"><Banner tone="red">{error.message}</Banner></div>;
  if (!data) {
    return (
      <div className="page">
        <Card className="player-hero">
          <Skeleton h={120} w={80} />
          <div className="col">
            <Skeleton h={34} w={220} />
            <Skeleton h={16} w={320} />
          </div>
        </Card>
      </div>
    );
  }

  const online = !!liveP || !!data.live;
  const lv = { uuid: data.uuid, name: data.name, ...(data.live ?? {}), ...(liveP ?? {}) };
  const ban = data.punishments.find((p) => p.type === "ban" && active(p));
  const mute = data.punishments.find((p) => p.type === "mute" && active(p));
  const rec = data.record;
  const name = data.name;

  const tabs = [
    { value: "overview" as Tab, label: "Übersicht", icon: <User /> },
    { value: "inventory" as Tab, label: "Inventar", icon: <Gamepad2 />, hidden: !can(me, "players.inventory") },
    { value: "record" as Tab, label: `Akte${data.punishments.length ? ` · ${data.punishments.length}` : ""}`, icon: <FileText /> },
    { value: "links" as Tab, label: "Verbindungen", icon: <Fingerprint /> },
    { value: "history" as Tab, label: "Verlauf", icon: <History /> },
  ];

  return (
    <div className="page">
      <div className="glass strong player-hero">
        <BodyRender key={uuid} uuid={uuid} name={name} />
        <div className="col" style={{ gap: 8, minWidth: 0 }}>
          <div className="row wrap" style={{ gap: 10 }}>
            <h1 className="ellipsis">{name}</h1>
            {online ? <Badge tone="green" icon={<Wifi />}>online · {serverName(me, lv.server)}</Badge> : <Badge>zuletzt {ago(rec?.lastSeen)}</Badge>}
            {ban && <Badge tone="red" icon={<Ban />}>gebannt · {until(ban.expires)}</Badge>}
            {mute && <Badge tone="orange" icon={<MicOff />}>gemutet · {until(mute.expires)}</Badge>}
          </div>
          <div className="row wrap small muted" style={{ gap: 16 }}>
            <span className="row" style={{ gap: 6 }}><Clock size={14} /> {duration(rec?.playtime, 2)} gespielt</span>
            <span className="row" style={{ gap: 6 }}><CalendarClock size={14} /> seit {day(rec?.firstSeen)}</span>
            <span className="row" style={{ gap: 6 }}><History size={14} /> {rec?.sessions ?? 0} Sitzungen</span>
            {lv.client || rec?.client ? <span className="row" style={{ gap: 6 }}><Gamepad2 size={14} /> {lv.client ?? rec?.client}</span> : null}
          </div>
          <button className="row tiny faint mono" style={{ gap: 6, width: "fit-content" }} onClick={() => attempt(() => navigator.clipboard.writeText(uuid), "UUID kopiert")}>
            {uuid} <Copy size={12} />
          </button>
        </div>
        <div className="ph-actions row wrap" style={{ gap: 8, justifyContent: "flex-end" }}>
          {can(me, "players.message") && <Button size="sm" icon={<MessageSquare />} disabled={!online} onClick={() => setSheet("msg")}>Nachricht</Button>}
          {can(me, "players.send") && <Button size="sm" icon={<ArrowRightLeft />} disabled={!online} onClick={() => setSheet("send")}>Verschieben</Button>}
          {can(me, "players.kick") && <Button size="sm" icon={<UserX />} disabled={!online} onClick={() => setSheet("kick")}>Kick</Button>}
          {can(me, "players.kick") && <Button size="sm" icon={<TriangleAlert />} onClick={() => setSheet("warn")}>Verwarnen</Button>}
          {can(me, "players.mute") &&
            (mute ? (
              <Button size="sm" variant="tinted" icon={<MicOff />} onClick={async () => (await revoke("mute", uuid, name)) && reload()}>Entmuten</Button>
            ) : (
              <Button size="sm" variant="tinted" tone="orange" icon={<MicOff />} onClick={() => setSheet("mute")}>Muten</Button>
            ))}
          {can(me, "players.ban") &&
            (ban ? (
              <Button size="sm" variant="tinted" tone="green" icon={<ShieldCheck />} onClick={async () => (await revoke("ban", uuid, name)) && reload()}>Entbannen</Button>
            ) : (
              <Button size="sm" variant="danger" icon={<Ban />} onClick={() => setSheet("ban")}>Bannen</Button>
            ))}
        </div>
      </div>

      <div className="mt">
        <Tabs value={tab} tabs={tabs} onChange={(t) => go("players", uuid, t)} />
      </div>

      {tab === "overview" && <Overview data={data} online={online} lv={lv} />}
      {tab === "inventory" && <InventoryView uuid={uuid} online={online} />}
      {tab === "record" && <Record data={data} reload={reload} />}
      {tab === "links" && <Links data={data} />}
      {tab === "history" && <PlayerHistory data={data} />}

      {(sheet === "ban" || sheet === "mute" || sheet === "kick" || sheet === "warn") && <PunishSheet type={sheet} uuid={uuid} name={name} onClose={() => setSheet(null)} onDone={reload} />}
      {sheet === "msg" && <MessageSheet uuid={uuid} name={name} onClose={() => setSheet(null)} />}
      {sheet === "send" && <SendSheet uuid={uuid} name={name} current={lv.server} onClose={() => setSheet(null)} />}
    </div>
  );
}

function Overview({ data, online, lv }: { data: PlayerDetail; online: boolean; lv: NonNullable<PlayerDetail["live"]> }) {
  const me = useStore(session, (s) => s.me);
  const maxH = lv.maxHealth ?? 20;
  const sessions = data.sessions.slice(0, 20).reverse();
  const longest = Math.max(1, ...sessions.map((s) => (s.end ?? Date.now()) - s.start));
  return (
    <div className="grid g-split">
      <div className="col" style={{ gap: 16 }}>
        {online ? (
          <Card>
            <CardHead title="Gerade" icon={<Wifi />} sub={serverName(me, lv.server)} />
            <div className="grid g2" style={{ gap: 18 }}>
              <div className="col" style={{ gap: 12 }}>
                <div className="col" style={{ gap: 5 }}>
                  <div className="row between small"><span className="row" style={{ gap: 6 }}><Heart size={14} className="red" /> Leben</span><span className="num">{n1(lv.health)} / {n0(maxH)}</span></div>
                  <Meter value={(lv.health ?? 0) / maxH} tone="red" />
                </div>
                <div className="col" style={{ gap: 5 }}>
                  <div className="row between small"><span className="row" style={{ gap: 6 }}><Utensils size={14} className="orange" /> Hunger</span><span className="num">{lv.food ?? "–"} / 20</span></div>
                  <Meter value={(lv.food ?? 0) / 20} tone="orange" />
                </div>
                <div className="row wrap" style={{ gap: 6 }}>
                  {lv.gamemode && <Badge tone="accent">{lv.gamemode}</Badge>}
                  {lv.op && <Badge tone="red">OP</Badge>}
                  {lv.flying && <Badge icon={<Plane />}>fliegt</Badge>}
                  {lv.afk ? <Badge tone="yellow">AFK {short(lv.afk * 1000)}</Badge> : null}
                  {lv.level != null && <Badge>Level {lv.level}</Badge>}
                </div>
              </div>
              <KV
                rows={[
                  ["Welt", lv.world],
                  ["Position", lv.x != null ? <span className="mono">{lv.x} {lv.y} {lv.z}</span> : undefined],
                  ["Ping", lv.ping != null ? <span style={{ color: toneColor(pingTone(lv.ping)) }}>{lv.ping} ms</span> : undefined],
                  ["Client", [lv.client, lv.brand].filter(Boolean).join(" · ") || undefined],
                  ["Sprache", lv.locale],
                  ["Adresse", lv.host],
                ]}
              />
            </div>
          </Card>
        ) : (
          <Card>
            <Empty icon={<Wifi />} title="Gerade offline" text={`Zuletzt ${ago(data.record?.lastSeen)} auf ${serverName(me, data.record?.lastServer)}.`} />
          </Card>
        )}
        <Card>
          <CardHead title="Wann online" icon={<CalendarClock />} sub="letzte 4 Wochen, nach Wochentag und Uhrzeit" />
          <HeatMap grid={data.activity?.length === 7 ? data.activity : Array.from({ length: 7 }, () => Array(24).fill(0))} />
        </Card>
      </div>
      <div className="col" style={{ gap: 16 }}>
        <Card>
          <CardHead title="Letzte Sitzungen" icon={<History />} />
          {sessions.length === 0 ? (
            <div className="small faint">Noch keine Sitzungen erfasst.</div>
          ) : (
            <div className="col" style={{ gap: 8 }}>
              {[...sessions].reverse().slice(0, 8).map((s, i) => {
                const len = (s.end ?? Date.now()) - s.start;
                return (
                  <div key={i} className="col" style={{ gap: 4 }}>
                    <div className="row between tiny">
                      <span className="muted">{when(s.start)}</span>
                      <span className="num">{s.end ? duration(len, 2) : <b className="green">läuft · {duration(len, 1)}</b>}</span>
                    </div>
                    <Meter value={len / longest} tone={s.end ? "" : "green"} />
                  </div>
                );
              })}
            </div>
          )}
        </Card>
        <Card className="flush">
          <div style={{ padding: "18px 20px 4px" }}>
            <CardHead title="Zuletzt" icon={<MessageSquare />} />
          </div>
          {data.events.length === 0 ? (
            <Empty title="Noch nichts" />
          ) : (
            <div style={{ paddingBottom: 8 }}>
              {data.events.slice(0, 8).map((e) => (
                <EventRow key={e.id} e={e} serverLabel={serverName(me, e.server)} compact />
              ))}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}

function Record({ data, reload }: { data: PlayerDetail; reload: () => void }) {
  const me = useStore(session, (s) => s.me);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const icon = { ban: <Ban />, mute: <MicOff />, kick: <UserX />, warn: <TriangleAlert /> };
  const tone = { ban: "red", mute: "orange", kick: "orange", warn: "yellow" };
  const label = { ban: "Bann", mute: "Mute", kick: "Kick", warn: "Verwarnung" };
  return (
    <div className="grid g-split">
      <Card className="flush">
        <div style={{ padding: "18px 20px 4px" }}>
          <CardHead title="Strafen" icon={<Gavel />} sub={data.punishments.length ? `${data.punishments.length} insgesamt` : undefined} />
        </div>
        {data.punishments.length === 0 ? (
          <Empty icon={<ShieldCheck />} title="Weiße Weste" text="Keine Bans, Mutes, Kicks oder Verwarnungen." />
        ) : (
          <div className="list">
            {data.punishments.map((p) => {
              const act = (p.type === "ban" || p.type === "mute") && active(p);
              return (
                <div key={p.id} className="list-item inset" style={{ alignItems: "flex-start" }}>
                  <span className={`icon-tile sm ${tone[p.type]}`}>{icon[p.type]}</span>
                  <div className="grow">
                    <div className="row wrap" style={{ gap: 8 }}>
                      <span className="title">{label[p.type]}</span>
                      {act && <Badge tone="red">aktiv · {until(p.expires)}</Badge>}
                      {p.revokedAt && <Badge outline>aufgehoben von {p.revokedBy}</Badge>}
                      {!act && !p.revokedAt && (p.type === "ban" || p.type === "mute") && <Badge outline>abgelaufen</Badge>}
                    </div>
                    <div className="small" style={{ marginTop: 2 }}>{p.reason || <span className="faint">kein Grund angegeben</span>}</div>
                    <div className="sub">
                      {stamp(p.created)} · von {p.byUser}
                      {p.expires ? ` · bis ${stamp(p.expires)}` : p.type === "ban" || p.type === "mute" ? " · dauerhaft" : ""}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>
      <Card>
        <CardHead title="Team-Notizen" icon={<NotebookPen />} sub="nur fürs Team sichtbar" />
        {can(me, "notes.write") && (
          <div className="col mb" style={{ gap: 8 }}>
            <textarea className="textarea" placeholder="z. B. „Hat sich für den Spam entschuldigt, beim nächsten Mal Mute.“" value={note} onChange={(e) => setNote(e.target.value)} maxLength={2000} />
            <Button
              variant="primary"
              size="sm"
              icon={<Send />}
              busy={busy}
              disabled={!note.trim()}
              style={{ alignSelf: "flex-end" }}
              onClick={async () => {
                setBusy(true);
                if (await attempt(() => post(`/api/players/${data.uuid}/notes`, { text: note.trim() }), "Notiz gespeichert")) {
                  setNote("");
                  reload();
                }
                setBusy(false);
              }}
            >
              Notiz speichern
            </Button>
          </div>
        )}
        {data.notes.length === 0 ? (
          <div className="small faint">Noch keine Notizen.</div>
        ) : (
          <div className="col" style={{ gap: 10 }}>
            {data.notes.map((n) => (
              <div key={n.id} style={{ padding: 12, borderRadius: 14, background: "var(--well)" }}>
                <div className="small" style={{ whiteSpace: "pre-wrap" }}>{n.text}</div>
                <div className="tiny faint mt-s">{n.byUser} · {when(n.ts)}</div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}

function Links({ data }: { data: PlayerDetail }) {
  const [reveal, setReveal] = useState(false);
  return (
    <div className="grid g2">
      <Card className="flush">
        <div style={{ padding: "18px 20px 4px" }}>
          <CardHead
            title="IP-Adressen"
            icon={<Fingerprint />}
            right={
              <Button size="sm" variant="ghost" icon={reveal ? <EyeOff /> : <Eye />} onClick={() => setReveal(!reveal)}>
                {reveal ? "Verbergen" : "Zeigen"}
              </Button>
            }
          />
        </div>
        {data.ips.length === 0 ? (
          <Empty title="Keine Daten" />
        ) : (
          <div className="list">
            {data.ips.map((ip) => (
              <div key={ip.ip} className="list-item">
                <div className="grow">
                  <div className="title mono">{reveal ? ip.ip : maskIp(ip.ip)}</div>
                  <div className="sub">zuerst {day(ip.first)} · zuletzt {ago(ip.last)}</div>
                </div>
                {ip.accounts > 1 ? <Badge tone="orange">{ip.accounts} Konten</Badge> : <Badge outline>nur dieses Konto</Badge>}
              </div>
            ))}
          </div>
        )}
      </Card>
      <Card className="flush">
        <div style={{ padding: "18px 20px 4px" }}>
          <CardHead title="Mögliche Zweitkonten" icon={<Users />} sub="gleiche IP gesehen" />
        </div>
        {data.alts.length === 0 ? (
          <Empty icon={<ShieldCheck />} title="Keine gefunden" text="Niemand sonst hat sich von diesen Adressen eingeloggt." />
        ) : (
          <div className="list">
            {data.alts.map((a) => (
              <a key={a.uuid} href={href("players", a.uuid)} className="list-item inset">
                <Avatar uuid={a.uuid} name={a.name} size="sm" />
                <div className="grow">
                  <div className="title">{a.name}</div>
                  <div className="sub">zuletzt {ago(a.lastSeen)}</div>
                </div>
              </a>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}

function PlayerHistory({ data }: { data: PlayerDetail }) {
  const me = useStore(session, (s) => s.me);
  return (
    <Card className="flush">
      {data.events.length === 0 ? (
        <Empty icon={<History />} title="Noch kein Verlauf" />
      ) : (
        <div style={{ padding: "8px 0" }}>
          {data.events.map((e) => (
            <EventRow key={e.id} e={e} serverLabel={serverName(me, e.server)} />
          ))}
        </div>
      )}
    </Card>
  );
}

/** The full skin render; when the skin service is unreachable, the initials tile stands in at the same size. */
function BodyRender({ uuid, name }: { uuid: string; name: string }) {
  const [bad, setBad] = useState(false);
  if (bad) {
    return (
      <span className="body-render fallback">
        <Avatar name={name} size="xl" />
      </span>
    );
  }
  return <img className="body-render" src={`https://mc-heads.net/body/${uuid}/180`} alt="" onError={() => setBad(true)} />;
}
