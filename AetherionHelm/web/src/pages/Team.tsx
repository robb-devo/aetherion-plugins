import { useState } from "react";
import { Check, Copy, KeyRound, MoreHorizontal, Pencil, ShieldCheck, ShieldOff, Trash2, UserPlus, Users } from "lucide-react";
import { del, patch, post, useApi } from "../lib/api";
import { useStore } from "../lib/store";
import { can, session } from "../lib/session";
import { ago } from "../lib/format";
import { attempt, toast } from "../lib/toast";
import type { TeamUser } from "../lib/types";
import { Confirm, Sheet, useMenu } from "../components/overlay";
import { Avatar, Badge, Banner, Button, Card, CardHead, Field, IconButton, IconTile, PageHead, Skeleton, useTick } from "../components/ui";

interface TeamData {
  users: TeamUser[];
  roles: { id: string; label: string; perms: string[] }[];
}

export const PERM_LABELS: Record<string, string> = {
  view: "Übersicht und Server sehen",
  "players.view": "Spieler und Akten sehen",
  "players.inventory": "Inventare ansehen",
  "players.kick": "Kicken und verwarnen",
  "players.mute": "Muten",
  "players.ban": "Bannen",
  "players.message": "Spielern schreiben",
  "players.send": "Spieler verschieben",
  "notes.write": "Notizen schreiben",
  "chat.send": "Broadcasts senden",
  "console.view": "Konsole lesen",
  "console.command": "Konsolenbefehle senden",
  "server.power": "Server starten und stoppen",
  "host.view": "Hetzner-Auslastung sehen",
  "audit.view": "Protokoll sehen",
  "reports.manage": "Meldungen erledigen",
  "settings.manage": "Alerts und Discord einstellen",
  "team.manage": "Team-Konten verwalten",
};

const ROLE_TONE: Record<string, string> = { owner: "accent", coowner: "accent", admin: "red", moderator: "green", supporter: "blue" };

export function Team() {
  const me = useStore(session, (s) => s.me)!;
  const { data, reload } = useApi<TeamData>("/api/team", { every: 30000 });
  const [create, setCreate] = useState(false);
  const [pw, setPw] = useState<{ name: string; password: string } | null>(null);
  const manage = can(me, "team.manage");
  useTick(30000);

  return (
    <div className="page">
      <PageHead
        title="Team"
        sub="Wer Zugang zum Panel hat. Jede Person hat ein eigenes Konto mit Zwei-Faktor-Anmeldung."
        actions={manage ? <Button variant="primary" icon={<UserPlus />} onClick={() => setCreate(true)}>Neues Konto</Button> : undefined}
      />
      {!data ? (
        <div className="grid g-auto">{[0, 1].map((i) => <Card key={i}><Skeleton h={90} /></Card>)}</div>
      ) : (
        <div className="grid g-auto">
          {data.users.map((u) => (
            <Member key={u.id} u={u} self={u.id === me.user.id} manage={manage && u.role !== "owner" && u.id !== me.user.id} roles={data.roles} onChanged={reload} onPassword={(p) => setPw({ name: u.display, password: p })} />
          ))}
        </div>
      )}

      {data && <RoleMatrix roles={data.roles} />}

      {create && (
        <CreateSheet
          roles={(data?.roles ?? []).filter((r) => r.id !== "owner")}
          onClose={() => setCreate(false)}
          onCreated={(name, password) => {
            reload();
            setPw({ name, password });
          }}
        />
      )}
      {pw && <PasswordSheet name={pw.name} password={pw.password} onClose={() => setPw(null)} />}
    </div>
  );
}

function Member({ u, self, manage, roles, onChanged, onPassword }: {
  u: TeamUser;
  self: boolean;
  manage: boolean;
  roles: TeamData["roles"];
  onChanged: () => void;
  onPassword: (pw: string) => void;
}) {
  const menu = useMenu();
  const [edit, setEdit] = useState(false);
  const [confirm, setConfirm] = useState<null | "2fa" | "remove" | "pw">(null);
  return (
    <Card>
      <div className="row" style={{ gap: 14 }}>
        <Avatar name={u.mcName ?? u.display} uuid={u.mcName} size="lg" online={u.online} />
        <div className="grow">
          <div className="row wrap" style={{ gap: 8 }}>
            <b style={{ fontSize: 16 }}>{u.display}</b>
            {self && <Badge outline>du</Badge>}
          </div>
          <div className="small faint">@{u.username}{u.mcName ? ` · ${u.mcName}` : ""}</div>
          <div className="row wrap mt-s" style={{ gap: 6 }}>
            <Badge tone={ROLE_TONE[u.role] ?? ""}>{u.roleLabel}</Badge>
            {u.totp ? <Badge tone="green" icon={<ShieldCheck />}>2FA aktiv</Badge> : <Badge tone="orange" icon={<ShieldOff />}>2FA noch nicht eingerichtet</Badge>}
          </div>
        </div>
        {manage && (
          <>
            <IconButton label="Mehr" onClick={menu.open}>
              <MoreHorizontal />
            </IconButton>
            {menu.render([
              { label: "Bearbeiten", icon: <Pencil />, onClick: () => setEdit(true) },
              { label: "Passwort zurücksetzen", icon: <KeyRound />, onClick: () => setConfirm("pw") },
              { label: "2FA zurücksetzen", icon: <ShieldOff />, onClick: () => setConfirm("2fa") },
              { sep: true, label: "", onClick: () => {} },
              { label: "Konto entfernen", icon: <Trash2 />, danger: true, onClick: () => setConfirm("remove") },
            ])}
          </>
        )}
      </div>
      <div className="tiny faint mt">
        {u.online ? <span className="green">gerade im Panel</span> : u.lastLogin ? `zuletzt angemeldet ${ago(u.lastLogin)}` : "noch nie angemeldet"}
      </div>
      {edit && <EditSheet u={u} roles={roles.filter((r) => r.id !== "owner")} onClose={() => setEdit(false)} onDone={onChanged} />}
      {confirm === "2fa" && (
        <Confirm
          title={`2FA für ${u.display} zurücksetzen?`}
          text="Die Person wird abgemeldet und richtet beim nächsten Login die Authenticator-App neu ein."
          confirm="Zurücksetzen"
          danger
          onConfirm={() => attempt(() => post(`/api/team/${u.id}/reset-2fa`), "2FA zurückgesetzt").then(onChanged)}
          onClose={() => setConfirm(null)}
        />
      )}
      {confirm === "pw" && (
        <Confirm
          title={`Neues Passwort für ${u.display}?`}
          text="Erzeugt ein einmaliges Passwort und meldet die Person überall ab. Du siehst es danach genau einmal."
          confirm="Neues Passwort"
          onConfirm={async () => {
            try {
              const r = await post<{ password: string }>(`/api/team/${u.id}/reset-password`);
              onPassword(r.password);
            } catch (e) {
              toast((e as Error).message, "err");
            }
          }}
          onClose={() => setConfirm(null)}
        />
      )}
      {confirm === "remove" && (
        <Confirm
          title={`${u.display} entfernen?`}
          text="Das Konto wird gesperrt und sofort überall abgemeldet. Einträge im Protokoll bleiben erhalten."
          confirm="Entfernen"
          danger
          onConfirm={() => attempt(() => del(`/api/team/${u.id}`), "Konto entfernt").then(onChanged)}
          onClose={() => setConfirm(null)}
        />
      )}
    </Card>
  );
}

function CreateSheet({ roles, onClose, onCreated }: { roles: TeamData["roles"]; onClose: () => void; onCreated: (name: string, pw: string) => void }) {
  const [username, setUsername] = useState("");
  const [display, setDisplay] = useState("");
  const [mcName, setMcName] = useState("");
  const [role, setRole] = useState("moderator");
  const [busy, setBusy] = useState(false);
  const ok = /^[a-z0-9_.-]{2,24}$/.test(username) && display.trim().length >= 2;
  return (
    <Sheet
      title="Neues Team-Konto"
      sub="Du bekommst ein einmaliges Passwort. Beim ersten Login richtet die Person ihre Zwei-Faktor-Anmeldung ein."
      icon={<IconTile tone="accent"><UserPlus /></IconTile>}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>Abbrechen</Button>
          <Button
            variant="primary"
            busy={busy}
            disabled={!ok}
            onClick={async () => {
              setBusy(true);
              try {
                const r = await post<{ password: string }>("/api/team", { username, display: display.trim(), role, mcName: mcName.trim() || undefined });
                onCreated(display.trim(), r.password);
                close();
              } catch (e) {
                toast((e as Error).message, "err");
              } finally {
                setBusy(false);
              }
            }}
          >
            Konto anlegen
          </Button>
        </>
      )}
    >
      <Field label="Benutzername" help="Klein, ohne Leerzeichen. Damit meldet sich die Person an.">
        <input className="input" value={username} onChange={(e) => setUsername(e.target.value.toLowerCase())} placeholder="z. B. lena" autoFocus autoCapitalize="off" spellCheck={false} />
      </Field>
      <Field label="Anzeigename" help="Steht im Protokoll und im Spiel bei Team-Nachrichten.">
        <input className="input" value={display} onChange={(e) => setDisplay(e.target.value)} placeholder="z. B. Lena" />
      </Field>
      <Field label="Minecraft-Name (optional)" help="Für den Kopf als Profilbild.">
        <input className="input" value={mcName} onChange={(e) => setMcName(e.target.value)} placeholder="z. B. LenaMC" autoCapitalize="off" spellCheck={false} />
      </Field>
      <Field label="Rolle">
        <select className="select" value={role} onChange={(e) => setRole(e.target.value)}>
          {roles.map((r) => (
            <option key={r.id} value={r.id}>{r.label}</option>
          ))}
        </select>
      </Field>
    </Sheet>
  );
}

function EditSheet({ u, roles, onClose, onDone }: { u: TeamUser; roles: TeamData["roles"]; onClose: () => void; onDone: () => void }) {
  const [display, setDisplay] = useState(u.display);
  const [mcName, setMcName] = useState(u.mcName ?? "");
  const [role, setRole] = useState(u.role);
  const [busy, setBusy] = useState(false);
  return (
    <Sheet
      title={`${u.display} bearbeiten`}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>Abbrechen</Button>
          <Button
            variant="primary"
            busy={busy}
            onClick={async () => {
              setBusy(true);
              if (await attempt(() => patch(`/api/team/${u.id}`, { display: display.trim(), role, mcName: mcName.trim() || undefined }), "Gespeichert")) {
                onDone();
                close();
              }
              setBusy(false);
            }}
          >
            Speichern
          </Button>
        </>
      )}
    >
      <Field label="Anzeigename">
        <input className="input" value={display} onChange={(e) => setDisplay(e.target.value)} />
      </Field>
      <Field label="Minecraft-Name">
        <input className="input" value={mcName} onChange={(e) => setMcName(e.target.value)} />
      </Field>
      <Field label="Rolle">
        <select className="select" value={role} onChange={(e) => setRole(e.target.value)}>
          {roles.map((r) => (
            <option key={r.id} value={r.id}>{r.label}</option>
          ))}
        </select>
      </Field>
    </Sheet>
  );
}

function PasswordSheet({ name, password, onClose }: { name: string; password: string; onClose: () => void }) {
  return (
    <Sheet
      title={`Passwort für ${name}`}
      sub="Wird nur jetzt angezeigt. Schick es der Person auf einem sicheren Weg; sie kann es danach unter Einstellungen ändern."
      icon={<IconTile tone="green"><KeyRound /></IconTile>}
      onClose={onClose}
      foot={(close) => <Button variant="primary" icon={<Check />} onClick={close}>Fertig</Button>}
    >
      <div className="secret" style={{ fontSize: 18, padding: 16 }}>{password}</div>
      <Button icon={<Copy />} onClick={() => attempt(() => navigator.clipboard.writeText(password), "Kopiert")}>Kopieren</Button>
      <Banner tone="accent">Anmeldung unter derselben Adresse wie du. Beim ersten Login führt das Panel durch die 2FA-Einrichtung.</Banner>
    </Sheet>
  );
}

function RoleMatrix({ roles }: { roles: TeamData["roles"] }) {
  const perms = Object.keys(PERM_LABELS);
  return (
    <>
      <h2 className="section">Was darf wer?</h2>
      <Card className="flush">
        <div style={{ padding: "18px 20px 4px" }}>
          <CardHead title="Rollen" icon={<Users />} sub="Owner und Co-Owner dürfen fast alles; nur Team-Konten verwaltet allein der Owner." />
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Berechtigung</th>
                {roles.map((r) => (
                  <th key={r.id} className="r" style={{ textAlign: "center" }}>{r.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {perms.map((p) => (
                <tr key={p}>
                  <td className="small">{PERM_LABELS[p]}</td>
                  {roles.map((r) => (
                    <td key={r.id} style={{ textAlign: "center" }}>
                      {r.perms.includes(p) ? <Check size={16} className="green" /> : <span className="faint">·</span>}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}
