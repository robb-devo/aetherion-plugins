import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Ban, LogOut, Megaphone, MicOff, Moon, Search, Server, Sun, Terminal } from "lucide-react";
import { api } from "../lib/api";
import { t } from "../lib/i18n";
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
      const el = e.target as HTMLElement;
      const typing = el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
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
      out.push({ id: "p:" + n.id, group: t("Pages"), label: n.label, sub: n.hint, icon: <I />, run: () => go(n.id), keywords: n.hint });
    }
    for (const s of snap?.servers ?? me?.servers ?? []) {
      out.push({
        id: "s:" + s.id,
        group: t("Servers"),
        label: s.name,
        sub: t(s.role),
        icon: <Server />,
        run: () => go("servers", s.id),
        keywords: s.id + " server",
      });
      if (can(me, "console.view"))
        out.push({
          id: "c:" + s.id,
          group: t("Servers"),
          label: t("{server} console", { server: s.name }),
          icon: <Terminal />,
          run: () => go("servers", s.id, "console"),
          keywords: "console log command befehl " + s.id,
        });
    }
    if (can(me, "chat.send"))
      out.push({ id: "a:broadcast", group: t("Actions"), label: t("Message everyone"), sub: t("In-game broadcast"), icon: <Megaphone />, run: () => openSheet({ kind: "broadcast" }), keywords: "broadcast announcement chat ankündigung" });
    if (can(me, "players.ban"))
      out.push({ id: "a:ban", group: t("Actions"), label: t("Ban player…"), sub: t("By name, even when offline"), icon: <Ban />, run: () => openSheet({ kind: "punish", type: "ban" }), keywords: "ban sperren" });
    if (can(me, "players.mute"))
      out.push({ id: "a:mute", group: t("Actions"), label: t("Mute player…"), icon: <MicOff />, run: () => openSheet({ kind: "punish", type: "mute" }), keywords: "mute silence stumm" });
    out.push({
      id: "a:theme",
      group: t("Actions"),
      label: resolved() === "dark" ? t("Light mode") : t("Dark mode"),
      icon: resolved() === "dark" ? <Sun /> : <Moon />,
      run: () => setTheme(resolved() === "dark" ? "light" : "dark"),
      keywords: "theme appearance design light dark hell dunkel",
    });
    out.push({ id: "a:logout", group: t("Actions"), label: t("Sign out"), icon: <LogOut />, run: () => logout(), keywords: "logout sign out abmelden" });

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
        group: t("Players"),
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
      <div className="glass palette" role="dialog" aria-label={t("Search")}>
        <div className="palette-input">
          <Search />
          <input
            autoFocus
            placeholder={t("Players, servers, pages, actions…")}
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
          {entries.length === 0 && <div className="empty"><b>{t("No results")}</b><span>{t("Try a player name or “console”.")}</span></div>}
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
          <span><kbd>↑</kbd> <kbd>↓</kbd> {t("to select")}</span>
          <span><kbd>↵</kbd> {t("to open")}</span>
          <span><kbd>⌘</kbd> <kbd>K</kbd> {t("anywhere")}</span>
        </div>
      </div>
    </div>
  );
}
