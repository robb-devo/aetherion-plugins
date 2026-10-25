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
import { t } from "../lib/i18n";

interface TeamData {
  users: TeamUser[];
  roles: { id: string; label: string; perms: string[] }[];
}

export const PERM_LABELS: Record<string, string> = {
  view: t("View overview and servers"),
  "players.view": t("View players and records"),
  "players.inventory": t("View inventories"),
  "players.kick": t("Kick and warn"),
  "players.mute": t("Mute"),
  "players.ban": t("Ban"),
  "players.message": t("Message players"),
  "players.send": t("Move players"),
  "notes.write": t("Write notes"),
  "chat.send": t("Send broadcasts"),
  "console.view": t("Read console"),
  "console.command": t("Send console commands"),
  "server.power": t("Start and stop servers"),
  "host.view": t("See Hetzner load"),
  "audit.view": t("View audit log"),
  "reports.manage": t("Resolve reports"),
  "settings.manage": t("Configure alerts and Discord"),
  "team.manage": t("Manage team accounts"),
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
        sub={t("Who has access to the panel. Everyone has their own account with two-factor sign-in.")}
        actions={manage ? <Button variant="primary" icon={<UserPlus />} onClick={() => setCreate(true)}>{t("New account")}</Button> : undefined}
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
            {u.totp ? <Badge tone="green" icon={<ShieldCheck />}>{t("2FA on")}</Badge> : <Badge tone="orange" icon={<ShieldOff />}>{t("2FA not set up yet")}</Badge>}
          </div>
        </div>
        {manage && (
          <>
            <IconButton label={t("More")} onClick={menu.open}>
              <MoreHorizontal />
            </IconButton>
            {menu.render([
              { label: t("Edit"), icon: <Pencil />, onClick: () => setEdit(true) },
              { label: t("Reset password"), icon: <KeyRound />, onClick: () => setConfirm("pw") },
              { label: t("Reset 2FA"), icon: <ShieldOff />, onClick: () => setConfirm("2fa") },
              { sep: true, label: "", onClick: () => {} },
              { label: t("Remove account"), icon: <Trash2 />, danger: true, onClick: () => setConfirm("remove") },
            ])}
          </>
        )}
      </div>
      <div className="tiny faint mt">
        {u.online ? <span className="green">{t("in the panel now")}</span> : u.lastLogin ? t("last signed in {when}", { when: ago(u.lastLogin) }) : t("never signed in")}
      </div>
      {edit && <EditSheet u={u} roles={roles.filter((r) => r.id !== "owner")} onClose={() => setEdit(false)} onDone={onChanged} />}
      {confirm === "2fa" && (
        <Confirm
          title={t("Reset 2FA for {name}?", { name: u.display })}
          text={t("They are signed out and set up their authenticator app again at the next login.")}
          confirm={t("Reset")}
          danger
          onConfirm={() => attempt(() => post(`/api/team/${u.id}/reset-2fa`), t("2FA reset")).then(onChanged)}
          onClose={() => setConfirm(null)}
        />
      )}
      {confirm === "pw" && (
        <Confirm
          title={t("New password for {name}?", { name: u.display })}
          text={t("Creates a one-time password and signs them out everywhere. You'll see it exactly once.")}
          confirm={t("New password")}
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
          title={t("Remove {name}?", { name: u.display })}
          text={t("The account is locked and signed out everywhere right away. Its audit log entries stay.")}
          confirm={t("Remove")}
          danger
          onConfirm={() => attempt(() => del(`/api/team/${u.id}`), t("Account removed")).then(onChanged)}
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
      title={t("New team account")}
      sub={t("You get a one-time password. At their first login they set up two-factor sign-in.")}
      icon={<IconTile tone="accent"><UserPlus /></IconTile>}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>{t("Cancel")}</Button>
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
            {t("Create account")}
          </Button>
        </>
      )}
    >
      <Field label={t("Username")} help={t("Lowercase, no spaces. They sign in with this.")}>
        <input className="input" value={username} onChange={(e) => setUsername(e.target.value.toLowerCase())} placeholder={t("e.g. lena")} autoFocus autoCapitalize="off" spellCheck={false} />
      </Field>
      <Field label={t("Display name")} help={t("Shown in the audit log and on team messages in game.")}>
        <input className="input" value={display} onChange={(e) => setDisplay(e.target.value)} placeholder={t("e.g. Lena")} />
      </Field>
      <Field label={t("Minecraft name (optional)")} help={t("Used for the head avatar.")}>
        <input className="input" value={mcName} onChange={(e) => setMcName(e.target.value)} placeholder={t("e.g. LenaMC")} autoCapitalize="off" spellCheck={false} />
      </Field>
      <Field label={t("Role")}>
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
      title={t("Edit {name}", { name: u.display })}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>{t("Cancel")}</Button>
          <Button
            variant="primary"
            busy={busy}
            onClick={async () => {
              setBusy(true);
              if (await attempt(() => patch(`/api/team/${u.id}`, { display: display.trim(), role, mcName: mcName.trim() || undefined }), t("Saved"))) {
                onDone();
                close();
              }
              setBusy(false);
            }}
          >
            {t("Save")}
          </Button>
        </>
      )}
    >
      <Field label={t("Display name")}>
        <input className="input" value={display} onChange={(e) => setDisplay(e.target.value)} />
      </Field>
      <Field label={t("Minecraft name")}>
        <input className="input" value={mcName} onChange={(e) => setMcName(e.target.value)} />
      </Field>
      <Field label={t("Role")}>
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
      title={t("Password for {name}", { name })}
      sub={t("Shown only now. Send it to them over a safe channel; they can change it later in Settings.")}
      icon={<IconTile tone="green"><KeyRound /></IconTile>}
      onClose={onClose}
      foot={(close) => <Button variant="primary" icon={<Check />} onClick={close}>{t("Done")}</Button>}
    >
      <div className="secret" style={{ fontSize: 18, padding: 16 }}>{password}</div>
      <Button icon={<Copy />} onClick={() => attempt(() => navigator.clipboard.writeText(password), t("Copied"))}>{t("Copy")}</Button>
      <Banner tone="accent">{t("They sign in at the same address as you. On first login the panel walks them through setting up 2FA.")}</Banner>
    </Sheet>
  );
}

function RoleMatrix({ roles }: { roles: TeamData["roles"] }) {
  const perms = Object.keys(PERM_LABELS);
  return (
    <>
      <h2 className="section">{t("Who can do what?")}</h2>
      <Card className="flush">
        <div style={{ padding: "18px 20px 4px" }}>
          <CardHead title={t("Roles")} icon={<Users />} sub={t("Owner and Co-Owner can do almost everything; only the owner manages team accounts.")} />
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>{t("Permission")}</th>
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
