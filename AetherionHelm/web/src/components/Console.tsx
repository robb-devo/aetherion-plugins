import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { ArrowDown, Copy, CornerDownLeft, Eraser, Search } from "lucide-react";
import { api, post } from "../lib/api";
import { live, watchConsole } from "../lib/live";
import { useStore } from "../lib/store";
import { clockS } from "../lib/format";
import { legacy } from "../lib/mc";
import { toast } from "../lib/toast";
import type { ConsoleLine } from "../lib/types";
import { IconButton, Segmented, cx } from "./ui";

const history: Record<string, string[]> = {};

export function Console({ server, canSend, offline }: { server: string; canSend: boolean; offline?: boolean }) {
  const lines = useStore(live, (s) => (s.console.server === server ? s.console.lines : EMPTY));
  const [level, setLevel] = useState<"all" | "warn" | "error">("all");
  const [q, setQ] = useState("");
  const [clearedAt, setClearedAt] = useState(0);
  const [local, setLocal] = useState<ConsoleLine[]>([]);
  const [stick, setStick] = useState(true);
  const [input, setInput] = useState("");
  const [sugg, setSugg] = useState<string[]>([]);
  const [sel, setSel] = useState(0);
  const [hIdx, setHIdx] = useState(-1);
  const box = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const suggSeq = useRef(0);

  useEffect(() => {
    watchConsole(server);
    setLocal([]);
    setClearedAt(0);
    return () => watchConsole(null);
  }, [server]);

  const shown = useMemo(() => {
    const all = [...lines, ...local].filter((l) => l.ts > clearedAt).sort((a, b) => a.ts - b.ts);
    const ql = q.trim().toLowerCase();
    const f = all.filter((l) => {
      if (level === "warn" && l.lvl !== "WARN" && l.lvl !== "ERROR" && l.lvl !== "FATAL") return false;
      if (level === "error" && l.lvl !== "ERROR" && l.lvl !== "FATAL") return false;
      if (ql && !l.msg.toLowerCase().includes(ql)) return false;
      return true;
    });
    return f.length > 1200 ? f.slice(-1200) : f;
  }, [lines, local, level, q, clearedAt]);

  useLayoutEffect(() => {
    if (stick && box.current) box.current.scrollTop = box.current.scrollHeight;
  }, [shown, stick]);

  const onScroll = () => {
    const el = box.current;
    if (!el) return;
    setStick(el.scrollHeight - el.scrollTop - el.clientHeight < 40);
  };

  const complete = async (line: string) => {
    const my = ++suggSeq.current;
    try {
      const r = await api<string[]>(`/api/servers/${server}/complete?line=${encodeURIComponent(line)}`);
      if (my === suggSeq.current) {
        setSugg(r.slice(0, 30));
        setSel(0);
      }
    } catch {
      setSugg([]);
    }
  };

  const applySuggestion = (s: string) => {
    const parts = input.split(" ");
    parts[parts.length - 1] = s;
    setInput(parts.join(" ") + " ");
    setSugg([]);
    inputRef.current?.focus();
  };

  const send = async () => {
    const cmd = input.trim().replace(/^\//, "");
    if (!cmd) return;
    const h = (history[server] ??= []);
    if (h[h.length - 1] !== cmd) h.push(cmd);
    setHIdx(-1);
    setInput("");
    setSugg([]);
    setStick(true);
    setLocal((l) => [...l, { ts: Date.now(), lvl: "CMD", msg: "> " + cmd }]);
    try {
      await post(`/api/servers/${server}/command`, { command: cmd });
    } catch (e) {
      setLocal((l) => [...l, { ts: Date.now(), lvl: "ERROR", msg: (e as Error).message }]);
    }
  };

  const onKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (sugg.length) {
      if (e.key === "ArrowDown") { e.preventDefault(); setSel((s) => (s + 1) % sugg.length); return; }
      if (e.key === "ArrowUp") { e.preventDefault(); setSel((s) => (s - 1 + sugg.length) % sugg.length); return; }
      if (e.key === "Tab" || (e.key === "Enter" && sugg.length && input.split(" ").pop() !== sugg[sel])) {
        e.preventDefault();
        applySuggestion(sugg[sel]);
        return;
      }
      if (e.key === "Escape") { setSugg([]); return; }
    }
    if (e.key === "Tab") {
      e.preventDefault();
      complete(input);
      return;
    }
    if (e.key === "Enter") {
      send();
      return;
    }
    const h = history[server] ?? [];
    if (e.key === "ArrowUp" && h.length) {
      e.preventDefault();
      const i = hIdx < 0 ? h.length - 1 : Math.max(0, hIdx - 1);
      setHIdx(i);
      setInput(h[i]);
    } else if (e.key === "ArrowDown" && hIdx >= 0) {
      e.preventDefault();
      const i = hIdx + 1;
      if (i >= h.length) { setHIdx(-1); setInput(""); } else { setHIdx(i); setInput(h[i]); }
    }
  };

  const copy = () => {
    const text = shown.map((l) => `[${clockS(l.ts)} ${l.lvl}] ${l.msg}`).join("\n");
    navigator.clipboard?.writeText(text).then(() => toast(`${shown.length} Zeilen kopiert`), () => toast("Kopieren nicht erlaubt", "err"));
  };

  return (
    <div className="console">
      <div className="console-bar">
        <Segmented
          value={level}
          onChange={setLevel}
          options={[
            { value: "all", label: "Alles" },
            { value: "warn", label: "Warnungen" },
            { value: "error", label: "Fehler" },
          ]}
        />
        <div className="input-icon grow" style={{ maxWidth: 280 }}>
          <Search />
          <input className="input" style={{ height: 34, background: "rgba(255,255,255,0.05)" }} placeholder="Im Log suchen" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div className="row" style={{ marginLeft: "auto", gap: 2 }}>
          <IconButton label="Ansicht leeren" size="sm" onClick={() => { setClearedAt(Date.now()); setLocal([]); }}>
            <Eraser />
          </IconButton>
          <IconButton label="Kopieren" size="sm" onClick={copy}>
            <Copy />
          </IconButton>
        </div>
      </div>
      <div className="console-lines" ref={box} onScroll={onScroll}>
        {offline && shown.length === 0 && <div className="console-line"><span /><span className="msg" style={{ color: "rgba(235,238,255,.4)" }}>Der Server ist nicht verbunden. Sobald das Helm-Plugin läuft, erscheint hier sein Log.</span></div>}
        {shown.map((l, i) => (
          <div key={i} className={cx("console-line", l.lvl, l.lvl === "CMD" && "cmd")}>
            <span className="ts">{clockS(l.ts)}</span>
            <span className="msg">
              {l.src && l.src !== "MinecraftServer" && l.src !== "" && l.lvl !== "CMD" && <span className="src">[{l.src}] </span>}
              {l.msg.includes("§") ? legacy(l.msg) : l.msg}
            </span>
          </div>
        ))}
      </div>
      {!stick && (
        <button
          className="btn sm"
          style={{ position: "absolute", right: 24, bottom: canSend ? 72 : 20, zIndex: 3 }}
          onClick={() => setStick(true)}
        >
          <ArrowDown /> Neueste
        </button>
      )}
      {canSend && (
        <div className="console-input">
          {sugg.length > 0 && (
            <div className="console-suggest">
              {sugg.map((s, i) => (
                <div key={s + i} className={i === sel ? "sel" : ""} onPointerDown={(e) => { e.preventDefault(); applySuggestion(s); }}>
                  {s}
                </div>
              ))}
            </div>
          )}
          <span className="prompt">›</span>
          <input
            ref={inputRef}
            value={input}
            onChange={(e) => {
              setInput(e.target.value);
              if (sugg.length) complete(e.target.value);
            }}
            onKeyDown={onKey}
            placeholder={offline ? "Server nicht verbunden" : "Befehl eingeben · Tab ergänzt · ↑ Verlauf"}
            disabled={offline}
            spellCheck={false}
            autoCapitalize="off"
            autoComplete="off"
            autoCorrect="off"
          />
          <IconButton label="Senden" size="sm" onClick={send} disabled={offline || !input.trim()}>
            <CornerDownLeft />
          </IconButton>
        </div>
      )}
    </div>
  );
}

const EMPTY: ConsoleLine[] = [];
