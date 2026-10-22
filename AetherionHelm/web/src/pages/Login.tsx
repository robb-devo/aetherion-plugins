import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import { ArrowLeft, Check, Copy, Download, Eye, EyeOff, KeyRound, Lock, ShieldCheck, Smartphone, User } from "lucide-react";
import { ApiError, post } from "../lib/api";
import { logout, refreshMe, session } from "../lib/session";
import { useStore } from "../lib/store";
import { tap } from "../lib/native";
import { toast } from "../lib/toast";
import { HelmMark } from "../components/Logo";
import { Banner, Button, Switch, cx } from "../components/ui";

export function Login() {
  const phase = useStore(session, (s) => s.phase);
  return <div className="auth">{phase === "enroll" ? <Enroll /> : <SignIn />}</div>;
}

function Otp({ onDone, disabled, shake }: { onDone: (code: string) => void; disabled?: boolean; shake?: number }) {
  const [d, setD] = useState<string[]>(Array(6).fill(""));
  const refs = useRef<(HTMLInputElement | null)[]>([]);
  useEffect(() => {
    if (shake) {
      setD(Array(6).fill(""));
      setTimeout(() => refs.current[0]?.focus(), 50);
    }
  }, [shake]);
  const set = (i: number, v: string) => {
    const digits = v.replace(/\D/g, "");
    if (digits.length > 1) {
      // pasted or autofilled: spread over the boxes
      const next = [...d];
      for (let k = 0; k < digits.length && i + k < 6; k++) next[i + k] = digits[k];
      setD(next);
      const full = next.join("");
      refs.current[Math.min(5, i + digits.length)]?.focus();
      if (full.length === 6) onDone(full);
      return;
    }
    const next = [...d];
    next[i] = digits;
    setD(next);
    if (digits && i < 5) refs.current[i + 1]?.focus();
    if (next.every((x) => x)) onDone(next.join(""));
  };
  return (
    <div className={cx("otp", shake ? "shake" : "")} key={shake}>
      {d.map((v, i) => (
        <span key={i} style={{ display: "contents" }}>
          {i === 3 && <span className="gap" />}
          <input
            ref={(el) => (refs.current[i] = el)}
            value={v}
            inputMode="numeric"
            autoComplete={i === 0 ? "one-time-code" : "off"}
            maxLength={i === 0 ? 6 : 1}
            autoFocus={i === 0}
            disabled={disabled}
            aria-label={`Ziffer ${i + 1}`}
            onChange={(e) => set(i, e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Backspace" && !d[i] && i > 0) refs.current[i - 1]?.focus();
              if (e.key === "ArrowLeft" && i > 0) refs.current[i - 1]?.focus();
              if (e.key === "ArrowRight" && i < 5) refs.current[i + 1]?.focus();
            }}
            onFocus={(e) => e.target.select()}
          />
        </span>
      ))}
    </div>
  );
}

function SignIn() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [remember, setRemember] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ticket, setTicket] = useState<string | null>(null);
  const [recovery, setRecovery] = useState(false);
  const [rc, setRc] = useState("");
  const [shake, setShake] = useState(0);
  const shakeRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = shakeRef.current;
    if (!shake || !el) return;
    el.classList.remove("shake");
    void el.offsetWidth;
    el.classList.add("shake");
  }, [shake]);

  const submit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!username || !password) return;
    setBusy(true);
    setError(null);
    try {
      const r = await post<{ status: string; ticket?: string }>("/api/auth/login", { username: username.trim(), password, remember });
      if (r.status === "totp" && r.ticket) {
        setTicket(r.ticket);
        tap("light");
      } else {
        await refreshMe();
      }
    } catch (err) {
      tap("error");
      setError((err as ApiError).message);
      setShake((s) => s + 1);
    } finally {
      setBusy(false);
    }
  };

  const second = async (code: string) => {
    if (!ticket) return;
    setBusy(true);
    setError(null);
    try {
      await post("/api/auth/totp", { ticket, code });
      tap("success");
      await refreshMe();
    } catch (err) {
      const e = err as ApiError;
      tap("error");
      if (e.data?.status === "expired") {
        setTicket(null);
        setPassword("");
      } else if (e.data?.ticket) setTicket(e.data.ticket);
      setError(e.message);
      setShake((s) => s + 1);
    } finally {
      setBusy(false);
    }
  };

  if (ticket) {
    return (
      <div className="glass strong auth-card">
        <div className="auth-logo">
          <ShieldCheck />
        </div>
        <h1>Bestätigen</h1>
        <p className="lead">{recovery ? "Gib einen deiner Wiederherstellungscodes ein. Jeder Code geht nur einmal." : "Gib den 6-stelligen Code aus deiner Authenticator-App ein."}</p>
        {recovery ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              second(rc.trim());
            }}
            className="col"
          >
            <input className="input lg mono" style={{ textAlign: "center", letterSpacing: "0.12em" }} placeholder="XXXX-XXXX" value={rc} onChange={(e) => setRc(e.target.value.toUpperCase())} autoFocus />
            <Button variant="primary" size="lg" block busy={busy} type="submit">Anmelden</Button>
          </form>
        ) : (
          <Otp onDone={second} disabled={busy} shake={shake} />
        )}
        {error && <div className="mt"><Banner tone="red">{error}</Banner></div>}
        <div className="row between mt">
          <Button variant="ghost" size="sm" icon={<ArrowLeft />} onClick={() => { setTicket(null); setError(null); }}>Zurück</Button>
          <Button variant="ghost" size="sm" icon={recovery ? <Smartphone /> : <KeyRound />} onClick={() => { setRecovery(!recovery); setError(null); }}>
            {recovery ? "Authenticator-Code" : "Wiederherstellungscode"}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <form className="glass strong auth-card" onSubmit={submit}>
      <div className="auth-logo">
        <HelmMark size={34} />
      </div>
      <h1>Aetherion Helm</h1>
      <p className="lead">Melde dich mit deinem Staff-Konto an.</p>
      <div className="col" style={{ gap: 12 }} ref={shakeRef}>
        <div className="input-icon">
          <User />
          <input className="input lg" placeholder="Benutzername" autoComplete="username" autoCapitalize="off" spellCheck={false} value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </div>
        <div className="input-icon" style={{ position: "relative" }}>
          <Lock />
          <input className="input lg" type={show ? "text" : "password"} placeholder="Passwort" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} style={{ paddingRight: 46 }} />
          <button type="button" className="icon-btn sm" style={{ position: "absolute", right: 8, top: 9 }} onClick={() => setShow(!show)} aria-label={show ? "Passwort verbergen" : "Passwort zeigen"}>
            {show ? <EyeOff /> : <Eye />}
          </button>
        </div>
        <label className="row between" style={{ padding: "4px 2px" }}>
          <span className="muted small">Angemeldet bleiben (30 Tage)</span>
          <Switch on={remember} onChange={setRemember} label="Angemeldet bleiben" />
        </label>
        {error && <Banner tone="red">{error}</Banner>}
        <Button variant="primary" size="lg" block busy={busy} type="submit" disabled={!username || !password}>
          Weiter
        </Button>
      </div>
      <p className="legal">Geschützt mit Zwei-Faktor-Anmeldung. Jede Aktion wird protokolliert.</p>
    </form>
  );
}

function Enroll() {
  const me = useStore(session, (s) => s.me);
  const [step, setStep] = useState<0 | 1 | 2>(0);
  const [secret, setSecret] = useState<string | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [codes, setCodes] = useState<string[] | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [shake, setShake] = useState(0);

  useEffect(() => {
    post<{ secret: string; uri: string }>("/api/auth/enroll/start")
      .then(async (r) => {
        setSecret(r.secret);
        setQr(await QRCode.toDataURL(r.uri, { margin: 0, width: 360, errorCorrectionLevel: "M", color: { dark: "#0b0b14", light: "#ffffff" } }));
      })
      .catch((e) => setError((e as Error).message));
  }, []);

  const finish = async (code: string) => {
    setBusy(true);
    setError(null);
    try {
      const r = await post<{ codes: string[] }>("/api/auth/enroll/finish", { code });
      tap("success");
      setCodes(r.codes);
      setStep(2);
    } catch (e) {
      tap("error");
      setError((e as Error).message);
      setShake((s) => s + 1);
    } finally {
      setBusy(false);
    }
  };

  const copyCodes = () => {
    navigator.clipboard?.writeText(codes!.join("\n")).then(() => toast("Codes kopiert"), () => toast("Kopieren nicht erlaubt", "err"));
  };
  const downloadCodes = () => {
    const text = `Aetherion Helm – Wiederherstellungscodes für ${me?.user.username}\nJeder Code funktioniert genau einmal.\n\n${codes!.join("\n")}\n`;
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([text], { type: "text/plain" }));
    a.download = `helm-codes-${me?.user.username}.txt`;
    a.click();
    setSaved(true);
  };

  return (
    <div className="glass strong auth-card" style={{ width: "min(460px, 100%)" }}>
      <div className="steps">
        {[0, 1, 2].map((i) => <i key={i} className={i <= step ? "on" : ""} />)}
      </div>
      {step === 0 && (
        <>
          <h1>Willkommen, {me?.user.display}</h1>
          <p className="lead">Einmal kurz absichern. Scanne den Code mit einer Authenticator-App wie Google Authenticator, Authy oder 1Password.</p>
          <div className="qr">{qr ? <img src={qr} alt="QR-Code für die Authenticator-App" /> : <div className="skel" style={{ width: "100%", height: "100%" }} />}</div>
          <p className="faint small" style={{ textAlign: "center", margin: "16px 0 8px" }}>Kein Scanner? Gib den Schlüssel von Hand ein:</p>
          <div className="secret" onClick={() => secret && navigator.clipboard?.writeText(secret).then(() => toast("Schlüssel kopiert"))}>
            {secret ? secret.replace(/(.{4})/g, "$1 ").trim() : "…"}
          </div>
          {error && <div className="mt"><Banner tone="red">{error}</Banner></div>}
          <div className="mt">
            <Button variant="primary" size="lg" block onClick={() => setStep(1)} disabled={!secret}>Ich hab ihn gescannt</Button>
          </div>
          <div className="row" style={{ justifyContent: "center", marginTop: 10 }}>
            <Button variant="ghost" size="sm" onClick={() => logout()}>Abmelden</Button>
          </div>
        </>
      )}
      {step === 1 && (
        <>
          <h1>Code eingeben</h1>
          <p className="lead">Die App zeigt jetzt „Aetherion Helm“ mit einem 6-stelligen Code. Tipp ihn hier ein.</p>
          <Otp onDone={finish} disabled={busy} shake={shake} />
          {error && <div className="mt"><Banner tone="red">{error}</Banner></div>}
          <div className="row" style={{ justifyContent: "center", marginTop: 16 }}>
            <Button variant="ghost" size="sm" icon={<ArrowLeft />} onClick={() => setStep(0)}>QR-Code nochmal zeigen</Button>
          </div>
        </>
      )}
      {step === 2 && codes && (
        <>
          <h1>Fast fertig</h1>
          <p className="lead">Das sind deine Wiederherstellungscodes, falls das Handy mal weg ist. Bewahr sie sicher auf, sie werden nur jetzt angezeigt.</p>
          <div className="codes">
            {codes.map((c) => <span key={c}>{c}</span>)}
          </div>
          <div className="row mt" style={{ gap: 8 }}>
            <Button className="grow" icon={<Copy />} onClick={() => { copyCodes(); setSaved(true); }}>Kopieren</Button>
            <Button className="grow" icon={<Download />} onClick={downloadCodes}>Als Datei</Button>
          </div>
          <label className="row mt" style={{ cursor: "pointer" }}>
            <Switch on={saved} onChange={setSaved} label="Gespeichert" />
            <span className="small muted">Ich habe die Codes sicher abgelegt.</span>
          </label>
          <div className="mt">
            <Button variant="primary" size="lg" block icon={<Check />} disabled={!saved} onClick={() => refreshMe()}>Los geht's</Button>
          </div>
        </>
      )}
    </div>
  );
}
