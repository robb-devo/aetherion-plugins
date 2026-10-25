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
import { t } from "../lib/i18n";

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
            aria-label={t("Digit {n}", { n: i + 1 })}
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
        <h1>{t("Confirm")}</h1>
        <p className="lead">{recovery ? t("Enter one of your recovery codes. Each code works only once.") : t("Enter the 6-digit code from your authenticator app.")}</p>
        {recovery ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              second(rc.trim());
            }}
            className="col"
          >
            <input className="input lg mono" style={{ textAlign: "center", letterSpacing: "0.12em" }} placeholder="XXXX-XXXX" value={rc} onChange={(e) => setRc(e.target.value.toUpperCase())} autoFocus />
            <Button variant="primary" size="lg" block busy={busy} type="submit">{t("Sign in")}</Button>
          </form>
        ) : (
          <Otp onDone={second} disabled={busy} shake={shake} />
        )}
        {error && <div className="mt"><Banner tone="red">{error}</Banner></div>}
        <div className="row between mt">
          <Button variant="ghost" size="sm" icon={<ArrowLeft />} onClick={() => { setTicket(null); setError(null); }}>{t("Back")}</Button>
          <Button variant="ghost" size="sm" icon={recovery ? <Smartphone /> : <KeyRound />} onClick={() => { setRecovery(!recovery); setError(null); }}>
            {recovery ? t("Authenticator code") : t("Recovery code")}
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
      <p className="lead">{t("Sign in with your staff account.")}</p>
      <div className="col" style={{ gap: 12 }} ref={shakeRef}>
        <div className="input-icon">
          <User />
          <input className="input lg" placeholder={t("Username")} autoComplete="username" autoCapitalize="off" spellCheck={false} value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </div>
        <div className="input-icon" style={{ position: "relative" }}>
          <Lock />
          <input className="input lg" type={show ? "text" : "password"} placeholder={t("Password")} autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} style={{ paddingRight: 46 }} />
          <button type="button" className="icon-btn sm" style={{ position: "absolute", right: 8, top: 9 }} onClick={() => setShow(!show)} aria-label={show ? t("Hide password") : t("Show password")}>
            {show ? <EyeOff /> : <Eye />}
          </button>
        </div>
        <label className="row between" style={{ padding: "4px 2px" }}>
          <span className="muted small">{t("Stay signed in (30 days)")}</span>
          <Switch on={remember} onChange={setRemember} label={t("Stay signed in")} />
        </label>
        {error && <Banner tone="red">{error}</Banner>}
        <Button variant="primary" size="lg" block busy={busy} type="submit" disabled={!username || !password}>
          {t("Continue")}
        </Button>
      </div>
      <p className="legal">{t("Protected by two-factor sign-in. Every action is logged.")}</p>
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
    navigator.clipboard?.writeText(codes!.join("\n")).then(() => toast(t("Codes copied")), () => toast(t("Copying not allowed"), "err"));
  };
  const downloadCodes = () => {
    const text = t("Aetherion Helm – recovery codes for {user}\nEach code works exactly once.\n\n{codes}\n", { user: me?.user.username ?? "", codes: codes!.join("\n") });
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
          <h1>{t("Welcome,")}{" "}{me?.user.display}</h1>
          <p className="lead">{t("One quick security step. Scan the code with an authenticator app like Google Authenticator, Authy or 1Password.")}</p>
          <div className="qr">{qr ? <img src={qr} alt={t("QR code for the authenticator app")} /> : <div className="skel" style={{ width: "100%", height: "100%" }} />}</div>
          <p className="faint small" style={{ textAlign: "center", margin: "16px 0 8px" }}>{t("No scanner? Enter the key by hand:")}</p>
          <div className="secret" onClick={() => secret && navigator.clipboard?.writeText(secret).then(() => toast(t("Key copied")))}>
            {secret ? secret.replace(/(.{4})/g, "$1 ").trim() : "…"}
          </div>
          {error && <div className="mt"><Banner tone="red">{error}</Banner></div>}
          <div className="mt">
            <Button variant="primary" size="lg" block onClick={() => setStep(1)} disabled={!secret}>{t("I've scanned it")}</Button>
          </div>
          <div className="row" style={{ justifyContent: "center", marginTop: 10 }}>
            <Button variant="ghost" size="sm" onClick={() => logout()}>{t("Sign out")}</Button>
          </div>
        </>
      )}
      {step === 1 && (
        <>
          <h1>{t("Enter code")}</h1>
          <p className="lead">{t("The app now shows “Aetherion Helm” with a 6-digit code. Type it in here.")}</p>
          <Otp onDone={finish} disabled={busy} shake={shake} />
          {error && <div className="mt"><Banner tone="red">{error}</Banner></div>}
          <div className="row" style={{ justifyContent: "center", marginTop: 16 }}>
            <Button variant="ghost" size="sm" icon={<ArrowLeft />} onClick={() => setStep(0)}>{t("Show QR code again")}</Button>
          </div>
        </>
      )}
      {step === 2 && codes && (
        <>
          <h1>{t("Almost done")}</h1>
          <p className="lead">{t("These are your recovery codes in case you lose your phone. Keep them somewhere safe; they are only shown now.")}</p>
          <div className="codes">
            {codes.map((c) => <span key={c}>{c}</span>)}
          </div>
          <div className="row mt" style={{ gap: 8 }}>
            <Button className="grow" icon={<Copy />} onClick={() => { copyCodes(); setSaved(true); }}>{t("Copy")}</Button>
            <Button className="grow" icon={<Download />} onClick={downloadCodes}>{t("As file")}</Button>
          </div>
          <label className="row mt" style={{ cursor: "pointer" }}>
            <Switch on={saved} onChange={setSaved} label={t("Saved")} />
            <span className="small muted">{t("I've stored the codes safely.")}</span>
          </label>
          <div className="mt">
            <Button variant="primary" size="lg" block icon={<Check />} disabled={!saved} onClick={() => refreshMe()}>{t("Let's go")}</Button>
          </div>
        </>
      )}
    </div>
  );
}
