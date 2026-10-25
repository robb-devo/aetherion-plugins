import { useMemo, useState } from "react";
import { Clock, Gavel, Moon, Search, Sparkles, Trophy, Users, Wifi } from "lucide-react";
import { useApi } from "../lib/api";
import { live } from "../lib/live";
import { useStore } from "../lib/store";
import { can, serverName, session } from "../lib/session";
import { href } from "../lib/router";
import { getPref, setPref } from "../lib/prefs";
import { ago, duration, n0, short, pingTone } from "../lib/format";
import type { PlayerRow } from "../lib/types";
import { openSheet } from "../components/nav";
import { Avatar, Badge, Button, Card, CardHead, Empty, PageHead, Segmented, Skeleton, useDebounced, useTick } from "../components/ui";
import { toneColor } from "./Overview";
import { t } from "../lib/i18n";

export function Players() {
  const me = useStore(session, (s) => s.me)!;
  const [mode, setMode] = useState<"online" | "all">(() => getPref("players.mode", "online"));
  const online = useStore(live, (s) => s.snap?.players.length ?? 0);
  return (
    <div className="page">
      <PageHead
        title={t("Players")}
        sub={mode === "online" ? t("{n} online now", { n: online }) : t("Everyone who has ever been on the network")}
        actions={
          <>
            <Segmented
              value={mode}
              onChange={(m) => {
                setMode(m);
                setPref("players.mode", m);
              }}
              options={[
                { value: "online", label: "Online", icon: <Wifi /> },
                { value: "all", label: t("All"), icon: <Users /> },
              ]}
            />
            {can(me, "players.ban") && (
              <Button icon={<Gavel />} onClick={() => openSheet({ kind: "punish", type: "ban" })}>
                {t("Punish by name")}
              </Button>
            )}
          </>
        }
      />
      {mode === "online" ? <OnlineList /> : <AllPlayers />}
    </div>
  );
}

function OnlineList() {
  const me = useStore(session, (s) => s.me);
  const players = useStore(live, (s) => s.snap?.players ?? EMPTY);
  const servers = useStore(live, (s) => s.snap?.servers ?? []);
  const [q, setQ] = useState("");
  const [server, setServer] = useState("");
  const [sort, setSort] = useState<"name" | "server" | "ping">("server");

  const list = useMemo(() => {
    const l = players.filter((p) => (!server || p.server === server) && (!q || p.name.toLowerCase().includes(q.toLowerCase())));
    return [...l].sort((a, b) =>
      sort === "ping" ? (b.ping ?? 0) - (a.ping ?? 0) : sort === "server" ? (a.server ?? "").localeCompare(b.server ?? "") || a.name.localeCompare(b.name) : a.name.localeCompare(b.name),
    );
  }, [players, q, server, sort]);

  const counts = useMemo(() => {
    const c: Record<string, number> = {};
    for (const p of players) if (p.server) c[p.server] = (c[p.server] ?? 0) + 1;
    return c;
  }, [players]);

  return (
    <>
      <div className="row wrap mb" style={{ gap: 10 }}>
        <div className="input-icon grow" style={{ maxWidth: 360 }}>
          <Search />
          <input className="input" placeholder={t("Filter by name")} value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div className="dur-presets">
          <button className={"chip" + (server === "" ? " on" : "")} onClick={() => setServer("")}>
            {t("All")}{" "}<span className="faint">{players.length}</span>
          </button>
          {servers
            .filter((s) => s.kind === "paper")
            .map((s) => (
              <button key={s.id} className={"chip" + (server === s.id ? " on" : "")} onClick={() => setServer(s.id)}>
                {s.name} <span className="faint">{counts[s.id] ?? 0}</span>
              </button>
            ))}
        </div>
        <span style={{ marginLeft: "auto" }}>
          <Segmented
            value={sort}
            onChange={setSort}
            options={[
              { value: "server", label: "Server" },
              { value: "name", label: "Name" },
              { value: "ping", label: "Ping" },
            ]}
          />
        </span>
      </div>
      {list.length === 0 ? (
        <Card>
          <Empty icon={<Moon />} title={players.length ? t("No one matches") : t("No one is online right now")} text={players.length ? t("Adjust the filters.") : t("As soon as someone joins, they show up here live.")} />
        </Card>
      ) : (
        <div className="grid g-auto-sm">
          {list.map((p) => (
            <a key={p.uuid} href={href("players", p.uuid)} className="glass card interactive pad-s" style={{ padding: 14 }}>
              <div className="row">
                <Avatar uuid={p.uuid} name={p.name} online />
                <div className="grow">
                  <div className="strong ellipsis">{p.name}</div>
                  <div className="tiny faint ellipsis">
                    {serverName(me, p.server)}
                    {p.world ? ` · ${p.world}` : ""}
                  </div>
                </div>
                <span className="tiny num" style={{ color: toneColor(pingTone(p.ping)) }}>
                  {p.ping != null && p.ping > 0 ? `${p.ping} ms` : ""}
                </span>
              </div>
              <div className="row wrap mt-s" style={{ gap: 6 }}>
                {p.client && <Badge outline>{p.client}</Badge>}
                {p.gamemode && p.gamemode !== "survival" && <Badge tone="accent">{p.gamemode}</Badge>}
                {p.afk ? <Badge tone="yellow">AFK {short(p.afk * 1000)}</Badge> : null}
                {p.level != null && p.level > 0 && <Badge>Lvl {p.level}</Badge>}
              </div>
            </a>
          ))}
        </div>
      )}
    </>
  );
}

function AllPlayers() {
  const me = useStore(session, (s) => s.me);
  const [q, setQ] = useState("");
  const dq = useDebounced(q.trim(), 220);
  const { data, loading } = useApi<{ results: PlayerRow[]; total: number; newest?: PlayerRow[]; top?: PlayerRow[] }>(`/api/players?q=${encodeURIComponent(dq)}&limit=80`);
  useTick(30000);
  return (
    <>
      <div className="row wrap mb">
        <div className="input-icon grow" style={{ maxWidth: 480 }}>
          <Search />
          <input className="input lg" placeholder={data ? t("Search {n} players (name or UUID)", { n: n0(data.total) }) : t("Search players")} value={q} onChange={(e) => setQ(e.target.value)} autoFocus />
        </div>
      </div>
      {!dq && data?.newest && (
        <div className="grid g2 mb">
          <Card>
            <CardHead title={t("New")} icon={<Sparkles />} />
            <div className="list" style={{ margin: "0 -16px -8px" }}>
              {data.newest.map((p) => (
                <a key={p.uuid} href={href("players", p.uuid)} className="list-item inset">
                  <Avatar uuid={p.uuid} name={p.name} size="sm" />
                  <span className="grow strong small">{p.name}</span>
                  <span className="tiny faint">{ago(p.firstSeen)}</span>
                </a>
              ))}
            </div>
          </Card>
          <Card>
            <CardHead title={t("Most playtime")} icon={<Trophy />} />
            <div className="list" style={{ margin: "0 -16px -8px" }}>
              {(data.top ?? []).map((p, i) => (
                <a key={p.uuid} href={href("players", p.uuid)} className="list-item inset">
                  <span className="num faint small" style={{ width: 14 }}>{i + 1}</span>
                  <Avatar uuid={p.uuid} name={p.name} size="sm" />
                  <span className="grow strong small">{p.name}</span>
                  <span className="tiny faint num">{duration(p.playtime, 1)}</span>
                </a>
              ))}
            </div>
          </Card>
        </div>
      )}
      <Card className="flush">
        {loading && !data ? (
          <div className="col" style={{ padding: 16 }}>
            {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} h={36} />)}
          </div>
        ) : !data?.results.length ? (
          <Empty icon={<Search />} title={t("No matches")} text={t("Only players who have been online since Helm started are listed. For everyone else use “Punish by name”.")} />
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>{t("Players")}</th>
                  <th>{t("Last seen")}</th>
                  <th className="r hide-m">{t("Playtime")}</th>
                  <th className="r hide-m">{t("Sessions")}</th>
                  <th className="hide-m">{t("First join")}</th>
                </tr>
              </thead>
              <tbody>
                {data.results.map((p) => (
                  <tr key={p.uuid} className="clickable" onClick={() => (location.hash = href("players", p.uuid))}>
                    <td>
                      <div className="row">
                        <Avatar uuid={p.uuid} name={p.name} size="sm" online={p.online} />
                        <div>
                          <div className="strong">{p.name}</div>
                          <div className="tiny faint">{p.online ? `online · ${serverName(me, p.server)}` : p.lastServer ? serverName(me, p.lastServer) : ""}</div>
                        </div>
                      </div>
                    </td>
                    <td className="small">{p.online ? <Badge tone="green">jetzt</Badge> : ago(p.lastSeen)}</td>
                    <td className="r num hide-m">
                      <span className="row" style={{ justifyContent: "flex-end", gap: 6 }}>
                        <Clock size={13} className="faint" />
                        {duration(p.playtime, 2)}
                      </span>
                    </td>
                    <td className="r num hide-m">{p.sessions ?? 0}</td>
                    <td className="small faint hide-m">{ago(p.firstSeen)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  );
}

const EMPTY: never[] = [];
