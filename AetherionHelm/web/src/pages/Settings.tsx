import { useEffect, useState } from "react";
import { Bell, Download, KeyRound, LogOut, Monitor, Moon, Palette as PaletteIcon, Send, ShieldCheck, Smartphone, SlidersHorizontal, Sun, User } from "lucide-react";
import { del, post, put, useApi } from "../lib/api";
import { useStore } from "../lib/store";
import { can, logout, session } from "../lib/session";
import { theme, setTheme, type ThemePref } from "../lib/theme";
import { ago, maskIp } from "../lib/format";
import { attempt } from "../lib/toast";
import { isNative } from "../lib/native";
import { Avatar, Badge, Banner, Button, Card, CardHead, Field, KV, PageHead, Segmented } from "../components/ui";

interface SessionRow {
  id: number;
  created: number;
  lastSeen: number;
  ip?: string;
  ua?: string;
  state: string;
  current: boolean;
}

function device(ua?: string) {
  if (!ua) return "Unbekanntes Gerät";
  const os = /Android/i.test(ua) ? "Android" : /iPhone|iPad/i.test(ua) ? "iOS" : /Windows/i.test(ua) ? "Windows" : /Mac OS/i.test(ua) ? "macOS" : /Linux/i.test(ua) ? "Linux" : "";
  const app = /wv\)|AetherionHelm/i.test(ua) ? "Helm-App" : /Edg\//.test(ua) ? "Edge" : /Firefox/.test(ua) ? "Firefox" : /Chrome/.test(ua) ? "Chrome" : /Safari/.test(ua) ? "Safari" : "Browser";
  return [app, os].filter(Boolean).join(" auf ");
}

export function Settings() {
  const me = useStore(session, (s) => s.me)!;
  const pref = useStore(theme, (t) => t);
  return (
    <div className="page">
      <PageHead title="Einstellungen" />
      <div className="grid g2">
        <Card>
          <CardHead title="Konto" icon={<User />} />
          <div className="row" style={{ gap: 14 }}>
            <Avatar name={me.user.mcName ?? me.user.display} uuid={me.user.mcName} size="lg" />
            <div className="grow">
              <b style={{ fontSize: 17 }}>{me.user.display}</b>
              <div className="small faint">@{me.user.username}{me.user.mcName ? ` · ${me.user.mcName}` : ""}</div>
              <div className="mt-s"><Badge tone="accent">{me.user.roleLabel}</Badge></div>
            </div>
            <Button variant="tinted" tone="red" icon={<LogOut />} onClick={() => logout()}>Abmelden</Button>
          </div>
        </Card>
        <Card>
          <CardHead title="Darstellung" icon={<PaletteIcon />} />
          <Segmented<ThemePref>
            value={pref}
            onChange={setTheme}
            options={[
              { value: "dark", label: "Dunkel", icon: <Moon /> },
              { value: "light", label: "Hell", icon: <Sun /> },
              { value: "system", label: "System", icon: <Monitor /> },
            ]}
          />
          <p className="small faint" style={{ marginBottom: 0 }}>Tipp: <kbd>⌘</kbd> <kbd>K</kbd> oder <kbd>/</kbd> öffnet überall die Suche.</p>
        </Card>
        <PasswordCard />
        <SecurityCard recoveryLeft={me.recoveryLeft} />
        <SessionsCard />
        <AppCard />
        {can(me, "settings.manage") && <AlertSettings />}
      </div>
      <p className="tiny faint" style={{ textAlign: "center", marginTop: 32 }}>Aetherion Helm {me.version}</p>
    </div>
  );
}

function PasswordCard() {
  const [cur, setCur] = useState("");
  const [next, setNext] = useState("");
  const [again, setAgain] = useState("");
  const [busy, setBusy] = useState(false);
  const strong = next.length >= 10;
  const match = next === again;
  return (
    <Card>
      <CardHead title="Passwort ändern" icon={<KeyRound />} />
      <form
        className="col"
        style={{ gap: 12 }}
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          if (await attempt(() => post("/api/auth/password", { current: cur, next }), "Passwort geändert. Andere Geräte wurden abgemeldet.")) {
            setCur("");
            setNext("");
            setAgain("");
          }
          setBusy(false);
        }}
      >
        <input className="input" type="password" autoComplete="current-password" placeholder="Aktuelles Passwort" value={cur} onChange={(e) => setCur(e.target.value)} />
        <input className="input" type="password" autoComplete="new-password" placeholder="Neues Passwort (mind. 10 Zeichen)" value={next} onChange={(e) => setNext(e.target.value)} />
        <input className="input" type="password" autoComplete="new-password" placeholder="Neues Passwort wiederholen" value={again} onChange={(e) => setAgain(e.target.value)} />
        {next && !strong && <span className="small orange">Noch {10 - next.length} Zeichen.</span>}
        {again && !match && <span className="small red">Die beiden stimmen nicht überein.</span>}
        <Button variant="primary" type="submit" busy={busy} disabled={!cur || !strong || !match} style={{ alignSelf: "flex-start" }}>
          Ändern
        </Button>
      </form>
    </Card>
  );
}

function SecurityCard({ recoveryLeft }: { recoveryLeft: number }) {
  return (
    <Card>
      <CardHead title="Zwei-Faktor-Anmeldung" icon={<ShieldCheck />} />
      <KV
        rows={[
          ["Status", <Badge tone="green" icon={<ShieldCheck />}>aktiv</Badge>],
          ["Wiederherstellungscodes", <span className={recoveryLeft <= 2 ? "orange" : ""}>{recoveryLeft} von 8 übrig</span>],
        ]}
      />
      <p className="small muted" style={{ margin: "14px 0 0" }}>
        Beim Anmelden fragt Helm nach dem 6-stelligen Code aus deiner Authenticator-App (Google Authenticator, 2FAS, Aegis oder 1Password). Ohne Handy
        geht auch einer deiner Wiederherstellungscodes, jeder genau einmal.
      </p>
      <p className="small muted" style={{ margin: "10px 0 0" }}>Neues Handy? Der Owner setzt deine 2FA im Team-Bereich zurück, beim nächsten Login richtest du sie in einer Minute neu ein.</p>
      {recoveryLeft <= 2 && (
        <div className="mt">
          <Banner tone="orange">Nur noch wenige Codes. Der Owner kann deine 2FA zurücksetzen; beim nächsten Login bekommst du neue Codes.</Banner>
        </div>
      )}
    </Card>
  );
}

function SessionsCard() {
  const { data, reload } = useApi<SessionRow[]>("/api/auth/sessions");
  return (
    <Card className="flush">
      <div style={{ padding: "18px 20px 4px" }}>
        <CardHead
          title="Angemeldete Geräte"
          icon={<Smartphone />}
          right={
            (data?.length ?? 0) > 1 ? (
              <Button size="sm" variant="ghost" onClick={async () => (await attempt(() => post("/api/auth/sessions/revoke-others"), "Andere Geräte abgemeldet")) && reload()}>
                Alle anderen abmelden
              </Button>
            ) : undefined
          }
        />
      </div>
      <div className="list">
        {(data ?? []).map((s) => (
          <div key={s.id} className="list-item">
            <div className="grow">
              <div className="title row" style={{ gap: 8 }}>
                {device(s.ua)}
                {s.current && <Badge tone="green">dieses Gerät</Badge>}
              </div>
              <div className="sub">
                aktiv {ago(s.lastSeen)} · {maskIp(s.ip)}
              </div>
            </div>
            {!s.current && (
              <Button size="sm" variant="ghost" onClick={async () => (await attempt(() => del(`/api/auth/sessions/${s.id}`), "Abgemeldet")) && reload()}>
                Abmelden
              </Button>
            )}
          </div>
        ))}
      </div>
    </Card>
  );
}

function AppCard() {
  const [apk, setApk] = useState<boolean | null>(null);
  useEffect(() => {
    if (__DEMO__) return setApk(true);
    fetch("/aetherion-helm.apk", { method: "HEAD" }).then((r) => setApk(r.ok), () => setApk(false));
  }, []);
  return (
    <Card>
      <CardHead title="Helm aufs Handy" icon={<Smartphone />} />
      {isNative ? (
        <p className="small muted" style={{ margin: 0 }}>Du nutzt schon die App. Updates der Oberfläche kommen automatisch, ohne neue APK.</p>
      ) : (
        <div className="col" style={{ gap: 12 }}>
          <div className="small muted">
            <b>Android:</b> die App installieren. Sie meldet sich hier an und aktualisiert sich selbst.
          </div>
          <a className="btn primary" href="/aetherion-helm.apk" style={{ alignSelf: "flex-start", pointerEvents: apk ? undefined : "none", opacity: apk ? 1 : 0.5 }}>
            <Download /> Android-App laden
          </a>
          {apk === false && <span className="tiny faint">Die APK liegt noch nicht auf dem Server; der Deploy legt sie hier ab.</span>}
          <div className="small muted">
            <b>iPhone:</b> in Safari auf Teilen tippen, dann „Zum Home-Bildschirm“. Helm läuft dann wie eine App, im Vollbild.
          </div>
        </div>
      )}
    </Card>
  );
}

interface AlertCfg {
  tpsWarn: number;
  tpsCrit: number;
  msptWarn: number;
  heapWarn: number;
  cpuWarn: number;
  memWarn: number;
  diskWarn: number;
}

function AlertSettings() {
  const { data, reload } = useApi<{ discordWebhook: string; discordSet: boolean; alerts: AlertCfg }>("/api/settings");
  const [hook, setHook] = useState("");
  const [t, setT] = useState<AlertCfg | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (data) setT(data.alerts);
  }, [data]);
  if (!data || !t) return null;
  const num = (k: keyof AlertCfg, label: string, unit: string, pctMode = false, step = 1) => (
    <Field label={label}>
      <div className="row">
        <input
          className="input"
          type="number"
          step={step}
          value={pctMode ? Math.round(t[k] * 100) : t[k]}
          onChange={(e) => setT({ ...t, [k]: pctMode ? Number(e.target.value) / 100 : Number(e.target.value) })}
          style={{ maxWidth: 110 }}
        />
        <span className="small faint">{unit}</span>
      </div>
    </Field>
  );
  return (
    <>
      <Card>
        <CardHead title="Discord-Benachrichtigungen" icon={<Bell />} />
        <div className="col" style={{ gap: 12 }}>
          <span className="small muted">Kritische Warnungen und Server-Abstürze gehen zusätzlich in einen Discord-Kanal. Webhook in Discord: Kanal-Einstellungen → Integrationen → Webhooks.</span>
          <input className="input mono" placeholder={data.discordSet ? data.discordWebhook : "https://discord.com/api/webhooks/…"} value={hook} onChange={(e) => setHook(e.target.value)} />
          <div className="row wrap" style={{ gap: 8 }}>
            <Button
              variant="primary"
              size="sm"
              disabled={!hook.trim()}
              onClick={async () => {
                if (await attempt(() => put("/api/settings", { discordWebhook: hook.trim() }), "Webhook gespeichert")) {
                  setHook("");
                  reload();
                }
              }}
            >
              Speichern
            </Button>
            {data.discordSet && (
              <>
                <Button size="sm" icon={<Send />} onClick={() => attempt(() => post("/api/settings/discord-test"), "Testnachricht geschickt")}>Test senden</Button>
                <Button size="sm" variant="ghost" onClick={async () => (await attempt(() => put("/api/settings", { discordWebhook: "" }), "Webhook entfernt")) && reload()}>Entfernen</Button>
              </>
            )}
          </div>
        </div>
      </Card>
      <Card>
        <CardHead title="Schwellen für Warnungen" icon={<SlidersHorizontal />} />
        <div className="grid g2" style={{ gap: 14 }}>
          {num("tpsWarn", "TPS Warnung unter", "TPS", false, 0.5)}
          {num("tpsCrit", "TPS kritisch unter", "TPS", false, 0.5)}
          {num("msptWarn", "Tick-Zeit über", "ms")}
          {num("heapWarn", "Heap über", "%", true)}
          {num("cpuWarn", "Host-CPU über", "%", true)}
          {num("memWarn", "Host-RAM über", "%", true)}
          {num("diskWarn", "Platte über", "%", true)}
        </div>
        <Button
          variant="primary"
          size="sm"
          busy={busy}
          className="mt"
          onClick={async () => {
            setBusy(true);
            if (await attempt(() => put("/api/settings", { alerts: t }), "Schwellen gespeichert")) reload();
            setBusy(false);
          }}
        >
          Speichern
        </Button>
      </Card>
    </>
  );
}
