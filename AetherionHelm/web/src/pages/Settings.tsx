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
import { lang, setLang, t, type Lang } from "../lib/i18n";

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
  if (!ua) return t("Unknown device");
  const os = /Android/i.test(ua) ? "Android" : /iPhone|iPad/i.test(ua) ? "iOS" : /Windows/i.test(ua) ? "Windows" : /Mac OS/i.test(ua) ? "macOS" : /Linux/i.test(ua) ? "Linux" : "";
  const app = /wv\)|AetherionHelm/i.test(ua) ? "Helm-App" : /Edg\//.test(ua) ? "Edge" : /Firefox/.test(ua) ? "Firefox" : /Chrome/.test(ua) ? "Chrome" : /Safari/.test(ua) ? "Safari" : "Browser";
  return [app, os].filter(Boolean).join(" auf ");
}

export function Settings() {
  const me = useStore(session, (s) => s.me)!;
  const pref = useStore(theme, (t) => t);
  return (
    <div className="page">
      <PageHead title={t("Settings")} />
      <div className="grid g2">
        <Card>
          <CardHead title={t("Account")} icon={<User />} />
          <div className="row" style={{ gap: 14 }}>
            <Avatar name={me.user.mcName ?? me.user.display} uuid={me.user.mcName} size="lg" />
            <div className="grow">
              <b style={{ fontSize: 17 }}>{me.user.display}</b>
              <div className="small faint">@{me.user.username}{me.user.mcName ? ` · ${me.user.mcName}` : ""}</div>
              <div className="mt-s"><Badge tone="accent">{me.user.roleLabel}</Badge></div>
            </div>
            <Button variant="tinted" tone="red" icon={<LogOut />} onClick={() => logout()}>{t("Sign out")}</Button>
          </div>
        </Card>
        <Card>
          <CardHead title={t("Appearance")} icon={<PaletteIcon />} />
          <Segmented<ThemePref>
            value={pref}
            onChange={setTheme}
            options={[
              { value: "dark", label: t("Dark"), icon: <Moon /> },
              { value: "light", label: t("Light"), icon: <Sun /> },
              { value: "system", label: "System", icon: <Monitor /> },
            ]}
          />
          <div className="mt-s">
            <Segmented<Lang>
              value={lang}
              onChange={setLang}
              options={[
                { value: "en", label: "English" },
                { value: "de", label: "Deutsch" },
              ]}
            />
          </div>
          <p className="small faint" style={{ marginBottom: 0 }}>{t("Tip:")}{" "}<kbd>⌘</kbd> <kbd>K</kbd> {t("or")} <kbd>/</kbd> {t("opens search anywhere.")}</p>
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
      <CardHead title={t("Change password")} icon={<KeyRound />} />
      <form
        className="col"
        style={{ gap: 12 }}
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          if (await attempt(() => post("/api/auth/password", { current: cur, next }), t("Password changed. Other devices were signed out."))) {
            setCur("");
            setNext("");
            setAgain("");
          }
          setBusy(false);
        }}
      >
        <input className="input" type="password" autoComplete="current-password" placeholder={t("Current password")} value={cur} onChange={(e) => setCur(e.target.value)} />
        <input className="input" type="password" autoComplete="new-password" placeholder={t("New password (at least 10 characters)")} value={next} onChange={(e) => setNext(e.target.value)} />
        <input className="input" type="password" autoComplete="new-password" placeholder={t("Repeat new password")} value={again} onChange={(e) => setAgain(e.target.value)} />
        {next && !strong && <span className="small orange">{t("Remaining")}{" "}{10 - next.length} {t("characters.")}</span>}
        {again && !match && <span className="small red">{t("The two don't match.")}</span>}
        <Button variant="primary" type="submit" busy={busy} disabled={!cur || !strong || !match} style={{ alignSelf: "flex-start" }}>
          {t("Change")}
        </Button>
      </form>
    </Card>
  );
}

function SecurityCard({ recoveryLeft }: { recoveryLeft: number }) {
  return (
    <Card>
      <CardHead title={t("Two-factor sign-in")} icon={<ShieldCheck />} />
      <KV
        rows={[
          ["Status", <Badge tone="green" icon={<ShieldCheck />}>aktiv</Badge>],
          [t("Recovery codes"), <span className={recoveryLeft <= 2 ? "orange" : ""}>{recoveryLeft} {t("of 8 left")}</span>],
        ]}
      />
      <p className="small muted" style={{ margin: "14px 0 0" }}>
        {t("When you sign in, Helm asks for the 6-digit code from your authenticator app (Google Authenticator, 2FAS, Aegis or 1Password). Without your phone, one of your recovery codes works too, each exactly once.")}
      </p>
      <p className="small muted" style={{ margin: "10px 0 0" }}>{t("New phone? The owner resets your 2FA under Team; at your next login you set it up again in a minute.")}</p>
      {recoveryLeft <= 2 && (
        <div className="mt">
          <Banner tone="orange">{t("Only a few codes left. The owner can reset your 2FA; at your next login you get new codes.")}</Banner>
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
          title={t("Signed-in devices")}
          icon={<Smartphone />}
          right={
            (data?.length ?? 0) > 1 ? (
              <Button size="sm" variant="ghost" onClick={async () => (await attempt(() => post("/api/auth/sessions/revoke-others"), t("Other devices signed out"))) && reload()}>
                {t("Sign out everywhere else")}
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
                {s.current && <Badge tone="green">{t("this device")}</Badge>}
              </div>
              <div className="sub">
                aktiv {ago(s.lastSeen)} · {maskIp(s.ip)}
              </div>
            </div>
            {!s.current && (
              <Button size="sm" variant="ghost" onClick={async () => (await attempt(() => del(`/api/auth/sessions/${s.id}`), t("Signed out"))) && reload()}>
                {t("Sign out")}
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
      <CardHead title={t("Helm on your phone")} icon={<Smartphone />} />
      {isNative ? (
        <p className="small muted" style={{ margin: 0 }}>{t("You're already using the app. Interface updates arrive automatically, no new APK needed.")}</p>
      ) : (
        <div className="col" style={{ gap: 12 }}>
          <div className="small muted">
            <b>Android:</b> {t("install the app. It signs in here and updates itself.")}
          </div>
          <a className="btn primary" href="/aetherion-helm.apk" style={{ alignSelf: "flex-start", pointerEvents: apk ? undefined : "none", opacity: apk ? 1 : 0.5 }}>
            <Download /> {t("Get the Android app")}
          </a>
          {apk === false && <span className="tiny faint">{t("The APK isn't on the server yet; the deploy puts it here.")}</span>}
          <div className="small muted">
            <b>iPhone:</b> {t("tap Share in Safari, then “Add to Home Screen”. Helm then runs like an app, full screen.")}
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
  const [cfg, setCfg] = useState<AlertCfg | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (data) setCfg(data.alerts);
  }, [data]);
  if (!data || !cfg) return null;
  const num = (k: keyof AlertCfg, label: string, unit: string, pctMode = false, step = 1) => (
    <Field label={label}>
      <div className="row">
        <input
          className="input"
          type="number"
          step={step}
          value={pctMode ? Math.round(cfg[k] * 100) : cfg[k]}
          onChange={(e) => setCfg({ ...cfg, [k]: pctMode ? Number(e.target.value) / 100 : Number(e.target.value) })}
          style={{ maxWidth: 110 }}
        />
        <span className="small faint">{unit}</span>
      </div>
    </Field>
  );
  return (
    <>
      <Card>
        <CardHead title={t("Discord notifications")} icon={<Bell />} />
        <div className="col" style={{ gap: 12 }}>
          <span className="small muted">{t("Critical alerts and server crashes also go to a Discord channel. Webhook in Discord: Channel settings → Integrations → Webhooks.")}</span>
          <input className="input mono" placeholder={data.discordSet ? data.discordWebhook : "https://discord.com/api/webhooks/…"} value={hook} onChange={(e) => setHook(e.target.value)} />
          <div className="row wrap" style={{ gap: 8 }}>
            <Button
              variant="primary"
              size="sm"
              disabled={!hook.trim()}
              onClick={async () => {
                if (await attempt(() => put("/api/settings", { discordWebhook: hook.trim() }), t("Webhook saved"))) {
                  setHook("");
                  reload();
                }
              }}
            >
              {t("Save")}
            </Button>
            {data.discordSet && (
              <>
                <Button size="sm" icon={<Send />} onClick={() => attempt(() => post("/api/settings/discord-test"), t("Test message sent"))}>{t("Send test")}</Button>
                <Button size="sm" variant="ghost" onClick={async () => (await attempt(() => put("/api/settings", { discordWebhook: "" }), t("Webhook removed"))) && reload()}>{t("Remove")}</Button>
              </>
            )}
          </div>
        </div>
      </Card>
      <Card>
        <CardHead title={t("Alert thresholds")} icon={<SlidersHorizontal />} />
        <div className="grid g2" style={{ gap: 14 }}>
          {num("tpsWarn", t("TPS warning below"), "TPS", false, 0.5)}
          {num("tpsCrit", t("TPS critical below"), "TPS", false, 0.5)}
          {num("msptWarn", t("Tick time above"), "ms")}
          {num("heapWarn", t("Heap above"), "%", true)}
          {num("cpuWarn", t("Host CPU above"), "%", true)}
          {num("memWarn", t("Host RAM above"), "%", true)}
          {num("diskWarn", t("Disk above"), "%", true)}
        </div>
        <Button
          variant="primary"
          size="sm"
          busy={busy}
          className="mt"
          onClick={async () => {
            setBusy(true);
            if (await attempt(() => put("/api/settings", { alerts: cfg }), t("Thresholds saved"))) reload();
            setBusy(false);
          }}
        >
          {t("Save")}
        </Button>
      </Card>
    </>
  );
}
