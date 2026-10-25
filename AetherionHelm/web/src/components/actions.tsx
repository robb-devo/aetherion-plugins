import { useState } from "react";
import { ArrowRightLeft, Ban, Gavel, Megaphone, MessageSquare, MicOff, TriangleAlert, UserX } from "lucide-react";
import { del, post } from "../lib/api";
import { DURATIONS } from "../lib/format";
import { t } from "../lib/i18n";
import { useMe } from "../lib/session";
import { useStore } from "../lib/store";
import { live } from "../lib/live";
import { tap } from "../lib/native";
import { toast } from "../lib/toast";
import { Sheet } from "./overlay";
import { ui } from "./nav";
import { Button, Field, IconTile, cx } from "./ui";

const REASONS: Record<string, string[]> = {
  ban: [t("Hacking / cheats"), "Griefing", t("Exploiting bugs"), t("Insults"), t("Advertising"), t("Ban evasion")],
  mute: ["Spam", t("Insults"), t("Advertising"), "Caps", t("Provocation")],
  kick: ["AFK", t("Please reconnect"), t("Ignored a warning")],
  warn: ["Spam", t("Chat tone"), t("Follow rule 3"), t("No begging")],
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
  ban: {
    icon: <Ban />,
    tone: "red",
    verb: t("Ban player"),
    title: (name: string) => t("Ban {name}", { name }),
    done: (name: string) => t("{name} banned", { name }),
    sub: t("Takes effect immediately across the network, even if the player is online."),
  },
  mute: {
    icon: <MicOff />,
    tone: "orange",
    verb: t("Mute player"),
    title: (name: string) => t("Mute {name}", { name }),
    done: (name: string) => t("{name} muted", { name }),
    sub: t("Blocks chat and message commands."),
  },
  kick: {
    icon: <UserX />,
    tone: "orange",
    verb: t("Kick player"),
    title: (name: string) => t("Kick {name}", { name }),
    done: (name: string) => t("{name} kicked", { name }),
    sub: t("Disconnects the player. They can rejoin right away."),
  },
  warn: {
    icon: <TriangleAlert />,
    tone: "yellow",
    verb: t("Warn player"),
    title: (name: string) => t("Warn {name}", { name }),
    done: (name: string) => t("{name} warned", { name }),
    sub: t("The player sees the warning in chat. It stays on their record."),
  },
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
      title={m.title(name)}
      sub={m.sub}
      icon={<IconTile tone={m.tone}>{m.icon}</IconTile>}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>{t("Cancel")}</Button>
          <Button
            variant={type === "ban" ? "danger" : "primary"}
            busy={busy}
            disabled={type === "warn" && !reason.trim()}
            onClick={async () => {
              setBusy(true);
              try {
                await post(`/api/players/${uuid}/${type}`, { reason: reason.trim() || undefined, duration: timed ? dur ?? undefined : undefined });
                tap("success");
                toast(m.done(name));
                onDone?.();
                close();
              } catch (e) {
                tap("error");
                toast(t((e as Error).message), "err");
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
        <Field label={t("Duration")}>
          <Durations value={dur} onPick={setDur} />
        </Field>
      )}
      <Field label={type === "warn" ? t("Reason (required)") : t("Reason")} help={t("The player sees the reason. It’s also kept on their record and in the audit log.")}>
        <input className="input" value={reason} maxLength={300} placeholder={t("e.g. Spam in chat")} onChange={(e) => setReason(e.target.value)} autoFocus />
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
      title={t("Punish by name")}
      sub={t("Works even if the player is offline or has never joined. The name is looked up with Mojang.")}
      icon={<IconTile tone="red"><Gavel /></IconTile>}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>{t("Cancel")}</Button>
          <Button
            variant={type === "ban" ? "danger" : "primary"}
            busy={busy}
            disabled={!/^[A-Za-z0-9_]{3,16}$/.test(name.trim())}
            onClick={async () => {
              setBusy(true);
              try {
                const r = await post<{ name: string }>("/api/punish", { type, target: name.trim(), reason: reason.trim() || undefined, duration: dur ?? undefined });
                toast(type === "ban" ? t("{name} is now banned", { name: r.name }) : t("{name} is now muted", { name: r.name }));
                close();
              } catch (e) {
                toast(t((e as Error).message), "err");
              } finally {
                setBusy(false);
              }
            }}
          >
            {type === "ban" ? t("Ban player") : t("Mute player")}
          </Button>
        </>
      )}
    >
      <div className="dur-presets">
        <button className={cx("chip", type === "ban" && "on")} onClick={() => setType("ban")}><Ban /> {t("Ban")}</button>
        <button className={cx("chip", type === "mute" && "on")} onClick={() => setType("mute")}><MicOff /> Mute</button>
      </div>
      <Field label={t("Minecraft name")}>
        <input className="input" value={name} placeholder={t("e.g. Notch")} onChange={(e) => setName(e.target.value)} autoFocus spellCheck={false} autoCapitalize="off" />
      </Field>
      <Field label={t("Duration")}>
        <Durations value={dur} onPick={setDur} />
      </Field>
      <Field label={t("Reason")}>
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
      title={t("Message {name}", { name })}
      sub={t("Arrives in chat as “Team · {sender}”. Only {name} can see it.", { sender: me?.user.display ?? "", name })}
      icon={<IconTile tone="accent"><MessageSquare /></IconTile>}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>{t("Cancel")}</Button>
          <Button
            variant="primary"
            busy={busy}
            disabled={!text.trim()}
            onClick={async () => {
              setBusy(true);
              try {
                await post(`/api/players/${uuid}/message`, { text: text.trim() });
                toast(t("Message delivered"));
                close();
              } catch (e) {
                toast(t((e as Error).message), "err");
              } finally {
                setBusy(false);
              }
            }}
          >
            {t("Send")}
          </Button>
        </>
      )}
    >
      <textarea className="textarea" value={text} maxLength={400} onChange={(e) => setText(e.target.value)} placeholder={t("Hey, quick note about your build…")} autoFocus />
    </Sheet>
  );
}

export function SendSheet({ uuid, name, current, onClose }: { uuid: string; name: string; current?: string; onClose: () => void }) {
  const servers = useStore(live, (s) => s.snap?.servers);
  const [busy, setBusy] = useState<string | null>(null);
  const targets = (servers ?? []).filter((s) => s.kind === "paper");
  return (
    <Sheet title={t("Move {name}", { name })} sub={t("Sends the player to another server through the proxy.")} icon={<IconTile tone="blue"><ArrowRightLeft /></IconTile>} onClose={onClose}>
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
                toast(t((e as Error).message), "err");
              } finally {
                setBusy(null);
              }
            }}
          >
            <div className="grow">
              <div className="title">{s.name}</div>
              <div className="sub">{s.id === current ? t("Currently here") : s.state !== "online" ? t("Not online") : `${s.players === 1 ? t("1 player") : t("{n} players", { n: s.players })} · ${t(s.role)}`}</div>
            </div>
            {busy === s.id ? <span className="faint small">{t("Moving…")}</span> : null}
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
  const [before, after] = (server ? t("Appears in chat as {sender} for all players on this server.") : t("Appears in chat as {sender} for all players.")).split("{sender}");
  return (
    <Sheet
      title={t("Message the network")}
      sub={<>{before}<b style={{ color: "#FF55FF" }}>[Team] {me?.user.display}</b>{after}</>}
      icon={<IconTile tone="accent"><Megaphone /></IconTile>}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>{t("Cancel")}</Button>
          <Button
            variant="primary"
            busy={busy}
            disabled={!text.trim()}
            icon={<Megaphone />}
            onClick={async () => {
              setBusy(true);
              try {
                await post("/api/chat", { text: text.trim(), server: server || undefined });
                toast(t("Sent"));
                close();
              } catch (e) {
                toast(t((e as Error).message), "err");
              } finally {
                setBusy(false);
              }
            }}
          >
            {t("Send")}
          </Button>
        </>
      )}
    >
      <Field label={t("Send to")}>
        <select className="select" value={server} onChange={(e) => setServer(e.target.value)}>
          <option value="">{t("Entire network")}</option>
          {(servers ?? []).filter((s) => s.kind === "paper").map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </Field>
      <Field label={t("Message")}>
        <textarea className="textarea" value={text} maxLength={400} onChange={(e) => setText(e.target.value)} placeholder={t("Server restart in 10 minutes. Please find a safe spot.")} autoFocus />
      </Field>
    </Sheet>
  );
}

export async function revoke(type: "ban" | "mute", uuid: string, name: string) {
  try {
    await del(`/api/players/${uuid}/${type}`);
    toast(type === "ban" ? t("{name} unbanned", { name }) : t("{name} unmuted", { name }));
    return true;
  } catch (e) {
    toast(t((e as Error).message), "err");
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
