import { useEffect, useRef, useState } from "react";
import { Bell, ChevronRight, LayoutGrid, LogOut, Menu as MenuIcon, MessagesSquare, Search, Server, Users } from "lucide-react";
import { api } from "./lib/api";
import { t } from "./lib/i18n";
import { live, seedEvents } from "./lib/live";
import { useRoute, go, href } from "./lib/router";
import { can, logout, refreshMe, session } from "./lib/session";
import { createStore, useStore } from "./lib/store";
import type { HelmEvent } from "./lib/types";
import { tap } from "./lib/native";
import { GlobalSheets } from "./components/actions";
import { HelmMark } from "./components/Logo";
import { NAV, navLabel, openPalette, ui } from "./components/nav";
import { Sheet, Toasts } from "./components/overlay";
import { Palette } from "./components/Palette";
import { Avatar, Dot, IconButton, cx } from "./components/ui";
import { Login } from "./pages/Login";
import { Overview } from "./pages/Overview";
import { Servers } from "./pages/Servers";
import { ServerPage } from "./pages/Server";
import { Host } from "./pages/Host";
import { Players } from "./pages/Players";
import { PlayerPage } from "./pages/Player";
import { Activity } from "./pages/Activity";
import { Reports } from "./pages/Reports";
import { Punishments } from "./pages/Punishments";
import { Audit } from "./pages/Audit";
import { Alerts } from "./pages/Alerts";
import { Team } from "./pages/Team";
import { Settings } from "./pages/Settings";

/** Detail pages name themselves here, so the top bar can show "Players › Notch". */
export const crumb = createStore<{ page: string; label: string } | null>(null);
export function useCrumb(page: string, label?: string) {
  useEffect(() => {
    if (!label) return;
    crumb.set({ page, label });
    return () => crumb.set(null);
  }, [page, label]);
}

export function Backdrop() {
  return (
    <div className="backdrop" aria-hidden>
      <div className="blob b1" />
      <div className="blob b2" />
      <div className="blob b3" />
      <div className="blob b4" />
      <div className="grain" />
      <div className="vignette" />
    </div>
  );
}

export function App() {
  const phase = useStore(session, (s) => s.phase);
  useEffect(() => {
    refreshMe();
  }, []);
  return (
    <>
      <Backdrop />
      {phase === "loading" ? <Splash /> : phase === "ready" ? <Shell /> : <Login />}
      <Toasts />
    </>
  );
}

function Splash() {
  return (
    <div className="auth">
      <div className="auth-logo" style={{ animation: "pulse-soft 1.6s ease-in-out infinite" }}>
        <HelmMark size={34} />
      </div>
    </div>
  );
}

function Page() {
  const r = useRoute();
  switch (r.page) {
    case "overview":
      return <Overview />;
    case "servers":
      return r.parts[0] ? <ServerPage id={r.parts[0]} tab={r.parts[1]} /> : <Servers />;
    case "host":
      return <Host />;
    case "players":
      return r.parts[0] ? <PlayerPage uuid={r.parts[0]} tab={r.parts[1]} /> : <Players />;
    case "activity":
      return <Activity />;
    case "reports":
      return <Reports />;
    case "punishments":
      return <Punishments />;
    case "audit":
      return <Audit />;
    case "alerts":
      return <Alerts />;
    case "team":
      return <Team />;
    case "settings":
      return <Settings />;
    default:
      return <Overview />;
  }
}

function Shell() {
  const route = useRoute();
  const scroller = useRef<HTMLDivElement>(null);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    api<HelmEvent[]>("/api/activity?limit=120").then(seedEvents).catch(() => {});
  }, []);

  useEffect(() => {
    scroller.current?.scrollTo({ top: 0 });
    document.title = `${navLabel(route.page)} · Aetherion Helm`;
  }, [route.page, route.parts[0]]);

  return (
    <div className="shell">
      <Sidebar />
      <main className="main">
        <div className="scroller" ref={scroller} onScroll={(e) => setScrolled((e.target as HTMLElement).scrollTop > 6)}>
          <Topbar scrolled={scrolled} />
          <Page key={route.page + "/" + (route.parts[0] ?? "")} />
        </div>
      </main>
      <TabBar />
      <Palette />
      <GlobalSheets />
      <MoreSheet />
    </div>
  );
}

function useBadges() {
  const reports = useStore(live, (s) => s.snap?.openReports ?? 0);
  const alerts = useStore(live, (s) => (s.snap?.alerts ?? []).filter((a) => !a.ackedBy && a.level !== "info").length);
  return { reports, alerts } as Record<string, number>;
}

function Sidebar() {
  const me = useStore(session, (s) => s.me)!;
  const route = useRoute();
  const badges = useBadges();
  const conn = useStore(live, (s) => s.conn);
  return (
    <aside className="glass sidebar">
      <a className="brand" href={href("overview")}>
        <span className="brand-mark">
          <HelmMark />
        </span>
        <div>
          <div className="brand-name">Aetherion Helm</div>
          <div className="brand-sub">{t("Staff console")}</div>
        </div>
      </a>
      <nav className="nav">
        {NAV.map((g) => {
          const items = g.items.filter((i) => !i.perm || can(me, i.perm));
          if (!items.length) return null;
          return (
            <div className="nav-group" key={g.group}>
              <div className="nav-label">{g.group}</div>
              {items.map((i) => {
                const I = i.icon;
                const count = badges[i.id];
                return (
                  <a key={i.id} href={href(i.id)} className={cx("nav-item", route.page === i.id && "active")} title={i.label}>
                    <I />
                    <span>{i.label}</span>
                    {count ? <span className="count">{count}</span> : null}
                  </a>
                );
              })}
            </div>
          );
        })}
      </nav>
      <a className="me-card" href={href("settings")} title={t("Settings")}>
        <Avatar name={me.user.mcName ?? me.user.display} uuid={me.user.mcName} size="sm" />
        <div className="who">
          <b>{me.user.display}</b>
          <span className="row" style={{ gap: 6 }}>
            <Dot tone={conn === "live" ? "green" : conn === "connecting" ? "yellow" : "red"} />
            {me.user.roleLabel}
          </span>
        </div>
      </a>
    </aside>
  );
}

function Topbar({ scrolled }: { scrolled: boolean }) {
  const route = useRoute();
  const c = useStore(crumb, (s) => s);
  const conn = useStore(live, (s) => s.conn);
  const online = useStore(live, (s) => s.snap?.network.online);
  const alerts = useStore(live, (s) => (s.snap?.alerts ?? []).filter((a) => !a.ackedBy && a.level !== "info").length);
  const detail = c && c.page === route.page ? c.label : null;
  return (
    <header className={cx("topbar", scrolled && "scrolled")}>
      <div className="crumbs">
        {detail ? (
          <>
            <a href={href(route.page)}>{navLabel(route.page)}</a>
            <ChevronRight size={14} />
            <b>{detail}</b>
          </>
        ) : (
          <b className={scrolled ? "" : "hide-m"} style={{ opacity: scrolled ? 1 : 0.0001, transition: "opacity 200ms" }}>{navLabel(route.page)}</b>
        )}
      </div>
      <div className="spacer" />
      {__DEMO__ && (
        <span className="demo-pill" title={t("This preview runs on made-up sample data. The real panel shows your network.")}>
          {t("Preview with sample data")}
        </span>
      )}
      <span className="live-pill" title={conn === "live" ? t("Live connection") : conn === "connecting" ? t("Connecting…") : t("Connection lost. Reconnecting…")}>
        <Dot tone={conn === "live" ? "green" : conn === "connecting" ? "yellow" : "red"} pulse={conn !== "offline"} />
        <span className="lp-text">{conn === "live" ? "Live" : conn === "connecting" ? t("Connecting") : "Offline"}</span>
        {online != null && <span className="num" style={{ color: "var(--text)" }}>· {online}</span>}
      </span>
      <button className="search-trigger hide-m" onClick={() => openPalette()}>
        <Search />
        <span>{t("Search…")}</span>
        <kbd>⌘K</kbd>
      </button>
      <IconButton label={t("Search")} className="show-m" onClick={() => openPalette()}>
        <Search />
      </IconButton>
      <IconButton label={t("Alerts")} onClick={() => go("alerts")} dot={alerts > 0}>
        <Bell />
      </IconButton>
    </header>
  );
}

const TABS = [
  { id: "overview", label: t("Overview"), icon: LayoutGrid },
  { id: "servers", label: t("Servers"), icon: Server },
  { id: "players", label: t("Players"), icon: Users, perm: "players.view" },
  { id: "activity", label: "Chat", icon: MessagesSquare },
];

function TabBar() {
  const me = useStore(session, (s) => s.me)!;
  const route = useRoute();
  const badges = useBadges();
  const inTabs = TABS.some((tab) => tab.id === route.page);
  const moreCount = (badges.reports ?? 0) + (badges.alerts ?? 0);
  return (
    <nav className="glass tabbar">
      {TABS.filter((tab) => !tab.perm || can(me, tab.perm)).map((tab) => {
        const I = tab.icon;
        return (
          <button
            key={tab.id}
            className={route.page === tab.id ? "on" : ""}
            onClick={() => {
              tap("light");
              go(tab.id);
            }}
          >
            <I />
            {tab.label}
          </button>
        );
      })}
      <button
        className={!inTabs ? "on" : ""}
        onClick={() => {
          tap("light");
          ui.set((s) => ({ ...s, sheet: { kind: "more" } }));
        }}
      >
        <MenuIcon />
        {t("More")}
        {moreCount > 0 && <span className="count">{moreCount}</span>}
      </button>
    </nav>
  );
}

function MoreSheet() {
  const open = useStore(ui, (s) => s.sheet?.kind === "more");
  const me = useStore(session, (s) => s.me)!;
  const badges = useBadges();
  if (!open) return null;
  const close = () => ui.set((s) => ({ ...s, sheet: null }));
  const items = NAV.flatMap((g) => g.items).filter((i) => !TABS.some((tab) => tab.id === i.id) && (!i.perm || can(me, i.perm)));
  return (
    <Sheet title={t("More")} onClose={close}>
      <div className="list glass" style={{ borderRadius: 18, background: "var(--well)" }}>
        {items.map((i) => {
          const I = i.icon;
          return (
            <button
              key={i.id}
              className="list-item inset"
              onClick={() => {
                close();
                go(i.id);
              }}
            >
              <span className="icon-tile sm accent">
                <I />
              </span>
              <div className="grow">
                <div className="title">{i.label}</div>
                <div className="sub">{i.hint}</div>
              </div>
              {badges[i.id] ? <span className="badge red">{badges[i.id]}</span> : null}
              <ChevronRight className="chev" />
            </button>
          );
        })}
      </div>
      <div className="list glass" style={{ borderRadius: 18, background: "var(--well)" }}>
        <div className="list-item">
          <Avatar name={me.user.mcName ?? me.user.display} uuid={me.user.mcName} size="sm" />
          <div className="grow">
            <div className="title">{me.user.display}</div>
            <div className="sub">{me.user.roleLabel}</div>
          </div>
          <button className="btn sm tinted red" onClick={() => logout()}>
            <LogOut /> {t("Sign out")}
          </button>
        </div>
      </div>
    </Sheet>
  );
}
