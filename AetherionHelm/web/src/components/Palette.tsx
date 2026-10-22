import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Ban, LogOut, Megaphone, MicOff, Moon, Search, Server, Sun, Terminal } from "lucide-react";
import { api } from "../lib/api";
import { go } from "../lib/router";
import { can, logout, session } from "../lib/session";
import { useStore } from "../lib/store";
import { resolved, setTheme } from "../lib/theme";
import { live } from "../lib/live";
import { ALL_NAV, openPalette, openSheet, ui } from "./nav";
import { Avatar, cx, useDebounced } from "./ui";

interface Entry {
  id: string;
  group: string;
  label: string;
  sub?: string;
  icon: ReactNode;
  run: () => void;
  keywords?: string;
}

function score(e: Entry, q: string) {
  if (!q) return 1;
  const hay = (e.label + " " + (e.keywords ?? "") + " " + (e.sub ?? "")).toLowerCase();
  const l = e.label.toLowerCase();
  if (l.startsWith(q)) return 3;
  if (l.includes(q)) return 2;
  if (hay.includes(q)) return 1;
  // loose subsequence match: "hetz" finds "Hetzner", "crsh" finds "Crash"
  let i = 0;
  for (const ch of hay) if (ch === q[i]) i++;
  return i === q.length ? 0.5 : 0;
}

export function Palette() {
  const open = useStore(ui, (s) => s.palette);
  useEffect(() => {
    const k = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      const typing = t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable);
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        openPalette(!ui.get().palette);
      } else if (e.key === "/" && !typing) {
        e.preventDefault();
        openPalette(true);
      }
    };
    window.addEventListener("keydown", k);
    return () => window.removeEventListener("keydown", k);
  }, []);
  return open ? createPortal(<PaletteBody />, document.body) : null;
}

function PaletteBody() {
  const me = useStore(session, (s) => s.me);
  const snap = useStore(live, (s) => s.snap);
  const [q, setQ] = useState("");
  const [sel, setSel] = useState(0);
  const [players, setPlayers] = useState<{ uuid: string; name: string; online: boolean }[]>([]);
  const dq = useDebounced(q.trim(), 160);
  const listRef = useRef<HTMLDivElement>(null);
  const close = () => openPalette(false);

  useEffect(() => {
    if (dq.length < 2 || !can(me, "players.view")) {
      setPlayers([]);
      return;
    }
    let alive = true;
    api<{ players: { uuid: string; name: string; online: boolean }[] }>(`/api/search?q=${encodeURIComponent(dq)}`)
      .then((r) => alive && setPlayers(r.players))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [dq, me]);

  const entries = useMemo(() => {
    const out: Entry[] = [];
    const ql = q.trim().toLowerCase();
    for (const n of ALL_NAV) {
      if (n.perm && !can(me, n.perm)) continue;
      const I = n.icon;
      out.push({ id: "p:" + n.id, group: "Seiten", label: n.label, sub: n.hint, icon: <I />, run: () => go(n.id), keywords: n.hint });
    }
    for (const s of snap?.servers ?? me?.servers ?? []) {
      out.push({
        id: "s:" + s.id,
        group: "Server",
        label: s.name,
        sub: s.role,
        icon: <Server />,
        run: () => go("servers", s.id),
        keywords: s.id + " server",
      });
      if (can(me, "console.view"))
        out.push({
          id: "c:" + s.id,
          group: "Server",
          label: `Konsole ${s.name}`,
          icon: <Terminal />,
          run: () => go("servers", s.id, "console"),
          keywords: "console log befehl " + s.id,
        });
    }
    if (can(me, "chat.send"))
      out.push({ id: "a:broadcast", group: "Aktionen", label: "Nachricht an alle", sub: "Broadcast ins Spiel", icon: <Megaphone />, run: () => openSheet({ kind: "broadcast" }), keywords: "broadcast chat ankündigung" });
    if (can(me, "players.ban"))
      out.push({ id: "a:ban", group: "Aktionen", label: "Spieler bannen …", sub: "auch offline, per Name", icon: <Ban />, run: () => openSheet({ kind: "punish", type: "ban" }), keywords: "ban sperren" });
    if (can(me, "players.mute"))
      out.push({ id: "a:mute", group: "Aktionen", label: "Spieler muten …", icon: <MicOff />, run: () => openSheet({ kind: "punish", type: "mute" }), keywords: "mute stumm" });
    out.push({
      id: "a:theme",
      group: "Aktionen",
      label: resolved() === "dark" ? "Helles Design" : "Dunkles Design",
      icon: resolved() === "dark" ? <Sun /> : <Moon />,
      run: () => setTheme(resolved() === "dark" ? "light" : "dark"),
      keywords: "theme design hell dunkel dark light",
    });
    out.push({ id: "a:logout", group: "Aktionen", label: "Abmelden", icon: <LogOut />, run: () => logout(), keywords: "logout" });

    const online = new Set((snap?.players ?? []).map((p) => p.uuid));
    const onlineMatches = ql.length >= 1 ? (snap?.players ?? []).filter((p) => p.name.toLowerCase().includes(ql)).slice(0, 6) : [];
    const seen = new Set<string>();
    const pl: Entry[] = [];
    for (const p of [...onlineMatches.map((p) => ({ uuid: p.uuid, name: p.name, online: true, server: p.server })), ...players]) {
      if (seen.has(p.uuid)) continue;
      seen.add(p.uuid);
      const srv = "server" in p ? (p as { server?: string }).server : undefined;
      pl.push({
        id: "u:" + p.uuid,
        group: "Spieler",
        label: p.name,
        sub: online.has(p.uuid) ? "online" + (srv ? " · " + srv : "") : "offline",
        icon: <Avatar uuid={p.uuid} name={p.name} size="sm" />,
        run: () => go("players", p.uuid),
      });
    }
    const ranked = out.map((e) => ({ e, s: score(e, ql) })).filter((x) => x.s > 0);
    if (ql) ranked.sort((a, b) => b.s - a.s);
    return [...pl.slice(0, 8), ...ranked.map((x) => x.e)];
  }, [q, me, snap, players]);

  useEffect(() => setSel(0), [q]);
  useEffect(() => {
    listRef.current?.querySelector(".sel")?.scrollIntoView({ block: "nearest" });
  }, [sel]);

  const run = (e?: Entry) => {
    if (!e) return;
    close();
    setTimeout(e.run, 0);
  };

  let lastGroup = "";
  return (
    <div className="scrim palette-scrim" onPointerDown={(e) => e.target === e.currentTarget && close()}>
      <div className="glass palette" role="dialog" aria-label="Suche">
        <div className="palette-input">
          <Search />
          <input
            autoFocus
            placeholder="Spieler, Server, Seiten, Aktionen …"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "ArrowDown") { e.preventDefault(); setSel((s) => Math.min(entries.length - 1, s + 1)); }
              else if (e.key === "ArrowUp") { e.preventDefault(); setSel((s) => Math.max(0, s - 1)); }
              else if (e.key === "Enter") { e.preventDefault(); run(entries[sel]); }
              else if (e.key === "Escape") close();
            }}
          />
          <kbd>esc</kbd>
        </div>
        <div className="palette-list" ref={listRef}>
          {entries.length === 0 && <div className="empty"><b>Nichts gefunden</b><span>Versuch einen Spielernamen oder „Konsole“.</span></div>}
          {entries.map((e, i) => {
            const head = e.group !== lastGroup ? <div className="palette-group">{e.group}</div> : null;
            lastGroup = e.group;
            return (
              <div key={e.id}>
                {head}
                <button className={cx("palette-item", i === sel && "sel")} onMouseMove={() => sel !== i && setSel(i)} onClick={() => run(e)}>
                  <span className="pi-icon">{e.icon}</span>
                  <span className="ellipsis">{e.label}</span>
                  {e.sub && <span className="pi-sub ellipsis">{e.sub}</span>}
                </button>
              </div>
            );
          })}
        </div>
        <div className="palette-foot hide-m">
          <span><kbd>↑</kbd> <kbd>↓</kbd> auswählen</span>
          <span><kbd>↵</kbd> öffnen</span>
          <span><kbd>⌘</kbd> <kbd>K</kbd> überall</span>
        </div>
      </div>
    </div>
  );
}
