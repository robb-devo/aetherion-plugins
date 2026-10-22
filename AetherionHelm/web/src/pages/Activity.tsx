import { useMemo, useState } from "react";
import { ArrowRightLeft, Flag, Gavel, History, LogIn, LogOut, Mail, Megaphone, MessageSquare, Pause, Play, Power, Search, Skull, Terminal, Zap } from "lucide-react";
import { api, useApi } from "../lib/api";
import { live, seedEvents } from "../lib/live";
import { useStore } from "../lib/store";
import { can, serverName, session } from "../lib/session";
import { href } from "../lib/router";
import { clock, when } from "../lib/format";
import type { HelmEvent } from "../lib/types";
import { AreaChart } from "../components/charts";
import { openSheet } from "../components/nav";
import { Button, Card, CardHead, Empty, PageHead, cx, useTick } from "../components/ui";

const KIND: Record<string, { icon: typeof Zap; tone: string; label: string }> = {
  chat: { icon: MessageSquare, tone: "blue", label: "Chat" },
  command: { icon: Terminal, tone: "", label: "Befehl" },
  death: { icon: Skull, tone: "red", label: "Tod" },
  join: { icon: LogIn, tone: "green", label: "Join" },
  quit: { icon: LogOut, tone: "", label: "Quit" },
  switch: { icon: ArrowRightLeft, tone: "accent", label: "Wechsel" },
  mod: { icon: Gavel, tone: "red", label: "Strafe" },
  report: { icon: Flag, tone: "orange", label: "Meldung" },
  staffmsg: { icon: Mail, tone: "accent", label: "Team-Nachricht" },
  staffchat: { icon: Megaphone, tone: "accent", label: "Broadcast" },
  server: { icon: Power, tone: "blue", label: "Server" },
};

export function EventRow({ e, serverLabel, compact }: { e: HelmEvent; serverLabel?: string; compact?: boolean }) {
  const k = KIND[e.kind] ?? { icon: Zap, tone: "", label: e.kind };
  const I = k.icon;
  const name = e.name && e.uuid ? <a href={href("players", e.uuid)} className="strong">{e.name}</a> : e.name ? <b>{e.name}</b> : null;
  let body: React.ReactNode = e.text;
  if (e.kind === "chat") body = <>{name}<span className="faint">: </span>{e.text}</>;
  else if (e.kind === "command") body = <>{name} <span className="mono faint">{e.text}</span></>;
  else if (e.kind === "staffchat") body = <><b style={{ color: "var(--pink)" }}>[Team] {e.name}</b> {e.text}</>;
  else if (name && e.text?.startsWith(e.name ?? "\u0000")) body = <>{name}{e.text!.slice(e.name!.length)}</>;
  else if (name && e.kind === "mod") body = <>{name} {e.text}</>;
  return (
    <div className="feed-item" style={compact ? { padding: "8px 14px" } : undefined}>
      <span className={cx("icon-tile sm", k.tone)}>
        <I />
      </span>
      <div className="ft">
        <div>{body}</div>
        <div className="fm">
          {when(e.ts)}
          {serverLabel && serverLabel !== "–" ? ` · ${serverLabel}` : ""}
        </div>
      </div>
    </div>
  );
}

const FILTERS: { id: string; label: string; kinds: string[] }[] = [
  { id: "chat", label: "Chat", kinds: ["chat", "staffchat", "staffmsg"] },
  { id: "command", label: "Befehle", kinds: ["command"] },
  { id: "join", label: "Joins", kinds: ["join", "quit", "switch"] },
  { id: "death", label: "Tode", kinds: ["death"] },
  { id: "mod", label: "Strafen & Meldungen", kinds: ["mod", "report"] },
  { id: "server", label: "Server", kinds: ["server"] },
];

export function Activity() {
  const me = useStore(session, (s) => s.me)!;
  const events = useStore(live, (s) => s.events);
  const servers = useStore(live, (s) => s.snap?.servers ?? []);
  const [on, setOn] = useState<Set<string>>(new Set(["chat", "join", "death", "mod", "server"]));
  const [server, setServer] = useState("");
  const [q, setQ] = useState("");
  const [paused, setPaused] = useState<HelmEvent[] | null>(null);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const { data: rate } = useApi<{ t: number; n: number }[]>("/api/chat/stats", { every: 120000 });
  useTick(15000);

  const kinds = useMemo(() => new Set(FILTERS.filter((f) => on.has(f.id)).flatMap((f) => f.kinds)), [on]);
  const src = paused ?? events;
  const shown = src.filter((e) => {
    if (!kinds.has(e.kind)) return false;
    if (server && e.server !== server) return false;
    if (q) {
      const l = q.toLowerCase();
      if (!(e.text ?? "").toLowerCase().includes(l) && !(e.name ?? "").toLowerCase().includes(l)) return false;
    }
    return true;
  });

  const older = async () => {
    const oldest = src[src.length - 1]?.id;
    if (!oldest) return;
    setLoadingOlder(true);
    try {
      const k = [...kinds].join(",");
      const r = await api<HelmEvent[]>(`/api/activity?before=${oldest}&limit=150&kinds=${k}${server ? `&server=${server}` : ""}`);
      if (paused) setPaused([...paused, ...r]);
      else seedEvents(r);
    } finally {
      setLoadingOlder(false);
    }
  };

  const toggle = (id: string) => {
    const n = new Set(on);
    if (n.has(id)) n.delete(id);
    else n.add(id);
    setOn(n);
  };

  const chatPerHour = (rate ?? []).map((r) => ({ t: r.t, n: r.n }));

  return (
    <div className="page">
      <PageHead
        title="Chat & Aktivität"
        sub="Alles, was im Netzwerk passiert, live und durchsuchbar."
        actions={
          <>
            <Button icon={paused ? <Play /> : <Pause />} onClick={() => setPaused(paused ? null : events)}>
              {paused ? "Weiter" : "Pausieren"}
            </Button>
            {can(me, "chat.send") && (
              <Button variant="primary" icon={<Megaphone />} onClick={() => openSheet({ kind: "broadcast" })}>
                Broadcast
              </Button>
            )}
          </>
        }
      />
      <div className="grid g-split-r">
        <div className="col" style={{ gap: 16 }}>
          <Card>
            <CardHead title="Filter" icon={<Search />} />
            <div className="col" style={{ gap: 12 }}>
              <div className="input-icon">
                <Search />
                <input className="input" placeholder="Text oder Spieler" value={q} onChange={(e) => setQ(e.target.value)} />
              </div>
              <select className="select" value={server} onChange={(e) => setServer(e.target.value)}>
                <option value="">Alle Server</option>
                {servers.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
              <div className="dur-presets">
                {FILTERS.map((f) => (
                  <button key={f.id} className={cx("chip", on.has(f.id) && "on")} onClick={() => toggle(f.id)}>
                    {f.label}
                  </button>
                ))}
              </div>
            </div>
          </Card>
          <Card>
            <CardHead title="Chat pro Stunde" icon={<MessageSquare />} sub="letzte 24 Std." />
            <AreaChart data={chatPerHour} height={150} series={[{ key: "n", label: "Nachrichten", color: "var(--c2)" }]} xFormat={clock} />
          </Card>
        </div>
        <Card className="flush">
          <div style={{ padding: "18px 20px 6px" }}>
            <CardHead
              title={paused ? "Pausiert" : "Live"}
              icon={paused ? <Pause /> : <span className="dot green pulse" style={{ marginRight: 2 }} />}
              sub={`${shown.length} Einträge`}
            />
          </div>
          {shown.length === 0 ? (
            <Empty icon={<MessageSquare />} title="Nichts zu sehen" text="Passe die Filter an oder warte, bis im Spiel etwas passiert." />
          ) : (
            <div>
              {shown.slice(0, 400).map((e) => (
                <EventRow key={e.id} e={e} serverLabel={serverName(me, e.server)} />
              ))}
            </div>
          )}
          <div style={{ padding: 16, display: "flex", justifyContent: "center" }}>
            <Button variant="ghost" icon={<History />} busy={loadingOlder} onClick={older}>
              Ältere laden
            </Button>
          </div>
        </Card>
      </div>
    </div>
  );
}
