import { useState } from "react";
import { ArrowRightLeft, Ban, Gavel, Megaphone, MessageSquare, MicOff, TriangleAlert, UserX } from "lucide-react";
import { del, post } from "../lib/api";
import { DURATIONS } from "../lib/format";
import { useMe } from "../lib/session";
import { useStore } from "../lib/store";
import { live } from "../lib/live";
import { tap } from "../lib/native";
import { toast } from "../lib/toast";
import { Sheet } from "./overlay";
import { ui } from "./nav";
import { Button, Field, IconTile, cx } from "./ui";

const REASONS: Record<string, string[]> = {
  ban: ["Hacking / Cheats", "Griefing", "Ausnutzen von Bugs", "Beleidigung", "Werbung", "Ban-Umgehung"],
  mute: ["Spam", "Beleidigung", "Werbung", "Caps", "Provokation"],
  kick: ["AFK", "Bitte neu verbinden", "Verwarnung missachtet"],
  warn: ["Spam", "Ton im Chat", "Regel 3 beachten", "Kein Betteln"],
};

function Reasons({ type, value, onPick }: { type: string; value: string; onPick: (r: string) => void }) {
  return (
    <div className="dur-presets">
      {(REASONS[type] ?? []).map((r) => (
        <button key={r} className={cx("chip", value === r && "on")} onClick={() => onPick(r)}>
          {r}
        </button>
      ))}
    </div>
  );
}

function Durations({ value, onPick }: { value: number | null; onPick: (v: number | null) => void }) {
  return (
    <div className="dur-presets">
      {DURATIONS.map((d) => (
        <button key={d.label} className={cx("chip", value === d.ms && "on")} onClick={() => onPick(d.ms)}>
          {d.label}
        </button>
      ))}
    </div>
  );
}

const META = {
  ban: { title: "Bannen", icon: <Ban />, tone: "red", verb: "Bannen" },
  mute: { title: "Muten", icon: <MicOff />, tone: "orange", verb: "Muten" },
  kick: { title: "Kicken", icon: <UserX />, tone: "orange", verb: "Kicken" },
  warn: { title: "Verwarnen", icon: <TriangleAlert />, tone: "yellow", verb: "Verwarnen" },
} as const;

/** Ban, mute, kick or warn one known player. */
export function PunishSheet({ type, uuid, name, onClose, onDone }: { type: keyof typeof META; uuid: string; name: string; onClose: () => void; onDone?: () => void }) {
  const [reason, setReason] = useState("");
  const [dur, setDur] = useState<number | null>(type === "mute" ? 3600_000 : type === "ban" ? 86400_000 : null);
  const [busy, setBusy] = useState(false);
  const m = META[type];
  const timed = type === "ban" || type === "mute";
  return (
    <Sheet
      title={`${name} ${m.title.toLowerCase()}`}
      sub={type === "ban" ? "Gilt sofort im ganzen Netzwerk, auch wenn der Spieler gerade online ist." : type === "mute" ? "Chat und Nachrichten-Befehle werden gesperrt." : type === "warn" ? "Der Spieler sieht die Verwarnung im Chat; sie bleibt in seiner Akte." : "Trennt die Verbindung, der Spieler kann sofort wieder joinen."}
      icon={<IconTile tone={m.tone}>{m.icon}</IconTile>}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>Abbrechen</Button>
          <Button
            variant={type === "ban" ? "danger" : "primary"}
            busy={busy}
            disabled={type === "warn" && !reason.trim()}
            onClick={async () => {
              setBusy(true);
              try {
                await post(`/api/players/${uuid}/${type}`, { reason: reason.trim() || undefined, duration: timed ? dur ?? undefined : undefined });
                tap("success");
                toast(`${name}: ${m.verb.toLowerCase()} erledigt`);
                onDone?.();
                close();
              } catch (e) {
                tap("error");
                toast((e as Error).message, "err");
              } finally {
                setBusy(false);
              }
            }}
          >
            {m.verb}
          </Button>
        </>
      )}
    >
      {timed && (
        <Field label="Dauer">
          <Durations value={dur} onPick={setDur} />
        </Field>
      )}
      <Field label={type === "warn" ? "Grund (Pflicht)" : "Grund"} help="Der Spieler sieht den Grund. Er landet auch in seiner Akte und im Protokoll.">
        <input className="input" value={reason} maxLength={300} placeholder="z. B. Spam im Chat" onChange={(e) => setReason(e.target.value)} autoFocus />
        <Reasons type={type} value={reason} onPick={setReason} />
      </Field>
    </Sheet>
  );
}

/** Ban or mute by name: works for players who are offline or have never been here. */
export function PunishByName({ type: initial = "ban", name: initialName = "", onClose }: { type?: "ban" | "mute"; name?: string; onClose: () => void }) {
  const [type, setType] = useState<"ban" | "mute">(initial);
  const [name, setName] = useState(initialName);
  const [reason, setReason] = useState("");
  const [dur, setDur] = useState<number | null>(type === "mute" ? 3600_000 : 86400_000);
  const [busy, setBusy] = useState(false);
  return (
    <Sheet
      title="Strafe per Name"
      sub="Funktioniert auch, wenn der Spieler offline ist oder noch nie hier war. Der Name wird bei Mojang nachgeschlagen."
      icon={<IconTile tone="red"><Gavel /></IconTile>}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>Abbrechen</Button>
          <Button
            variant={type === "ban" ? "danger" : "primary"}
            busy={busy}
            disabled={!/^[A-Za-z0-9_]{3,16}$/.test(name.trim())}
            onClick={async () => {
              setBusy(true);
              try {
                const r = await post<{ name: string }>("/api/punish", { type, target: name.trim(), reason: reason.trim() || undefined, duration: dur ?? undefined });
                toast(`${r.name} ist jetzt ${type === "ban" ? "gebannt" : "gemutet"}`);
                close();
              } catch (e) {
                toast((e as Error).message, "err");
              } finally {
                setBusy(false);
              }
            }}
          >
            {type === "ban" ? "Bannen" : "Muten"}
          </Button>
        </>
      )}
    >
      <div className="dur-presets">
        <button className={cx("chip", type === "ban" && "on")} onClick={() => setType("ban")}><Ban /> Bann</button>
        <button className={cx("chip", type === "mute" && "on")} onClick={() => setType("mute")}><MicOff /> Mute</button>
      </div>
      <Field label="Minecraft-Name">
        <input className="input" value={name} placeholder="z. B. Notch" onChange={(e) => setName(e.target.value)} autoFocus spellCheck={false} autoCapitalize="off" />
      </Field>
      <Field label="Dauer">
        <Durations value={dur} onPick={setDur} />
      </Field>
      <Field label="Grund">
        <input className="input" value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} />
        <Reasons type={type} value={reason} onPick={setReason} />
      </Field>
    </Sheet>
  );
}

export function MessageSheet({ uuid, name, onClose }: { uuid: string; name: string; onClose: () => void }) {
  const me = useMe();
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  return (
    <Sheet
      title={`Nachricht an ${name}`}
      sub={`Kommt im Chat an als „Team · ${me?.user.display}“. Nur ${name} sieht sie.`}
      icon={<IconTile tone="accent"><MessageSquare /></IconTile>}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>Abbrechen</Button>
          <Button
            variant="primary"
            busy={busy}
            disabled={!text.trim()}
            onClick={async () => {
              setBusy(true);
              try {
                await post(`/api/players/${uuid}/message`, { text: text.trim() });
                toast("Nachricht zugestellt");
                close();
              } catch (e) {
                toast((e as Error).message, "err");
              } finally {
                setBusy(false);
              }
            }}
          >
            Senden
          </Button>
        </>
      )}
    >
      <textarea className="textarea" value={text} maxLength={400} onChange={(e) => setText(e.target.value)} placeholder="Hey, kurz zu deinem Bauwerk …" autoFocus />
    </Sheet>
  );
}

export function SendSheet({ uuid, name, current, onClose }: { uuid: string; name: string; current?: string; onClose: () => void }) {
  const servers = useStore(live, (s) => s.snap?.servers);
  const [busy, setBusy] = useState<string | null>(null);
  const targets = (servers ?? []).filter((s) => s.kind === "paper");
  return (
    <Sheet title={`${name} verschieben`} sub="Schickt den Spieler über den Proxy auf einen anderen Server." icon={<IconTile tone="blue"><ArrowRightLeft /></IconTile>} onClose={onClose}>
      <div className="list glass" style={{ borderRadius: 18, background: "var(--well)" }}>
        {targets.map((s) => (
          <button
            key={s.id}
            className="list-item"
            disabled={s.id === current || s.state !== "online" || !!busy}
            style={{ opacity: s.id === current || s.state !== "online" ? 0.45 : 1 }}
            onClick={async () => {
              setBusy(s.id);
              try {
                await post(`/api/players/${uuid}/send`, { server: s.id });
                toast(`${name} → ${s.name}`);
                onClose();
              } catch (e) {
                toast((e as Error).message, "err");
              } finally {
                setBusy(null);
              }
            }}
          >
            <div className="grow">
              <div className="title">{s.name}</div>
              <div className="sub">{s.id === current ? "Ist gerade hier" : s.state !== "online" ? "Nicht online" : `${s.players} Spieler · ${s.role}`}</div>
            </div>
            {busy === s.id ? <span className="faint small">verbinde …</span> : null}
          </button>
        ))}
      </div>
    </Sheet>
  );
}

export function BroadcastSheet({ server: initial, onClose }: { server?: string; onClose: () => void }) {
  const me = useMe();
  const servers = useStore(live, (s) => s.snap?.servers);
  const [text, setText] = useState("");
  const [server, setServer] = useState<string>(initial ?? "");
  const [busy, setBusy] = useState(false);
  return (
    <Sheet
      title="Nachricht ans Netzwerk"
      sub={<>Erscheint im Chat als <b style={{ color: "#FF55FF" }}>[Team] {me?.user.display}</b> bei allen Spielern{server ? " auf diesem Server" : ""}.</>}
      icon={<IconTile tone="accent"><Megaphone /></IconTile>}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>Abbrechen</Button>
          <Button
            variant="primary"
            busy={busy}
            disabled={!text.trim()}
            icon={<Megaphone />}
            onClick={async () => {
              setBusy(true);
              try {
                await post("/api/chat", { text: text.trim(), server: server || undefined });
                toast("Gesendet");
                close();
              } catch (e) {
                toast((e as Error).message, "err");
              } finally {
                setBusy(false);
              }
            }}
          >
            Senden
          </Button>
        </>
      )}
    >
      <Field label="An">
        <select className="select" value={server} onChange={(e) => setServer(e.target.value)}>
          <option value="">Ganzes Netzwerk</option>
          {(servers ?? []).filter((s) => s.kind === "paper").map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </Field>
      <Field label="Nachricht">
        <textarea className="textarea" value={text} maxLength={400} onChange={(e) => setText(e.target.value)} placeholder="Server-Neustart in 10 Minuten. Bitte sucht euch einen sicheren Ort." autoFocus />
      </Field>
    </Sheet>
  );
}

export async function revoke(type: "ban" | "mute", uuid: string, name: string) {
  try {
    await del(`/api/players/${uuid}/${type}`);
    toast(`${name} ${type === "ban" ? "entbannt" : "entmutet"}`);
    return true;
  } catch (e) {
    toast((e as Error).message, "err");
    return false;
  }
}

/** Mounted once in the shell: the sheets that can be opened from anywhere (palette, quick actions). */
export function GlobalSheets() {
  const sheet = useStore(ui, (s) => s.sheet);
  const close = () => ui.set((s) => ({ ...s, sheet: null }));
  if (!sheet) return null;
  if (sheet.kind === "broadcast") return <BroadcastSheet server={sheet.server} onClose={close} />;
  if (sheet.kind === "punish") return <PunishByName type={sheet.type} name={sheet.name} onClose={close} />;
  return null;
}
