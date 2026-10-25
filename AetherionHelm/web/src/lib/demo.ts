/*
 * Demo backend for the static preview build (vite --mode demo). It answers every API call the app makes with
 * believable network data and streams live snapshots, chat and console lines, so the whole panel can be clicked
 * through without a gateway. Nothing here runs in the real build: api.ts and live.ts only call into it behind
 * __DEMO__, and the module has no top-level side effects, so it is tree-shaken away.
 */
import type { Alert, AuditRow, ConsoleLine, HelmEvent, Item, OnlinePlayer, Punishment, Report, Server, Snapshot } from "./types";

const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;
const GB = 1024 ** 3;
const MB = 1024 ** 2;

// ------------------------------------------------------------------ helpers

function rng(seed: number) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function hash(s: string) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return h >>> 0;
}

function uuidOf(name: string) {
  const r = rng(hash(name.toLowerCase()));
  const hex = Array.from({ length: 32 }, () => Math.floor(r() * 16).toString(16)).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-a${hex.slice(17, 20)}-${hex.slice(20, 32)}`;
}

const pick = <T,>(r: () => number, a: readonly T[]) => a[Math.floor(r() * a.length)];
const jitter = (v: number, f: number) => v * (1 + (Math.random() * 2 - 1) * f);
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

function fail(status: number, message: string): never {
  throw Object.assign(new Error(message), { status, data: { error: message } });
}

/** Relative network activity over the day: quiet at night, busy in the evening. */
function dayCurve(ts: number) {
  const d = new Date(ts);
  const h = d.getHours() + d.getMinutes() / 60;
  const wknd = d.getDay() === 0 || d.getDay() === 6 ? 1.18 : 1;
  const evening = Math.exp(-((h - 20.5) ** 2) / 9);
  const afternoon = 0.55 * Math.exp(-((h - 16) ** 2) / 6);
  const night = h < 7 ? 0.22 : 0.3;
  return clamp((night + evening + afternoon) * wknd, 0.2, 1.2);
}

// ------------------------------------------------------------------ static network

const SERVERS = [
  { id: "proxy", name: "Proxy", role: "Velocity-Proxy", kind: "velocity", xmx: "2G", share: 0 },
  { id: "hub", name: "Hub", role: "Lobby", kind: "paper", xmx: "4G", share: 0.16 },
  { id: "mmor", name: "MMO-R", role: "Hauptwelt", kind: "paper", xmx: "12G", share: 0.5 },
  { id: "mmod", name: "MMO-D", role: "Dungeons", kind: "paper", xmx: "6G", share: 0.15 },
  { id: "mmoc", name: "MMO-C", role: "Exile & Story", kind: "paper", xmx: "6G", share: 0.1 },
  { id: "mmoi", name: "MMO-I", role: "Inseln", kind: "paper", xmx: "6G", share: 0.09 },
] as const;
const PAPER = SERVERS.filter((s) => s.kind === "paper");

const WORLDS: Record<string, { name: string; env: string; chunks: number; entities: number; tiles: number }[]> = {
  hub: [{ name: "hub", env: "normal", chunks: 412, entities: 186, tiles: 940 }],
  mmor: [
    { name: "world", env: "normal", chunks: 3820, entities: 2410, tiles: 5120 },
    { name: "aetherion_eldervale", env: "normal", chunks: 2140, entities: 1630, tiles: 3380 },
    { name: "world_nether", env: "nether", chunks: 610, entities: 420, tiles: 260 },
    { name: "world_the_end", env: "the_end", chunks: 180, entities: 96, tiles: 40 },
  ],
  mmod: [
    { name: "dungeon_krypta", env: "normal", chunks: 520, entities: 880, tiles: 1430 },
    { name: "dungeon_glutkern", env: "nether", chunks: 340, entities: 610, tiles: 720 },
  ],
  mmoc: [{ name: "aetherion_exile", env: "normal", chunks: 1240, entities: 690, tiles: 1810 }],
  mmoi: [{ name: "islands", env: "normal", chunks: 1680, entities: 1210, tiles: 4620 }],
};

const BASE: Record<string, { mspt: number; heap: number; rss: number; cpu: number; threads: number }> = {
  proxy: { mspt: 0, heap: 0.42 * GB, rss: 1.1 * GB, cpu: 0.012, threads: 64 },
  hub: { mspt: 6.2, heap: 1.6 * GB, rss: 3.3 * GB, cpu: 0.021, threads: 58 },
  mmor: { mspt: 24.5, heap: 7.4 * GB, rss: 10.6 * GB, cpu: 0.094, threads: 96 },
  mmod: { mspt: 46, heap: 3.9 * GB, rss: 5.3 * GB, cpu: 0.061, threads: 74 },
  mmoc: { mspt: 14.8, heap: 3.1 * GB, rss: 4.7 * GB, cpu: 0.032, threads: 66 },
  mmoi: { mspt: 17.2, heap: 3.4 * GB, rss: 5.0 * GB, cpu: 0.036, threads: 68 },
};

const NAMES = [
  "A3therion", "Shuminite", "Kxlli", "NachtFalke", "LunaVxl", "Bl4ckout", "Zephyr_", "MoosMaus", "Tobi2k", "FeuerFuchs",
  "xSkyline", "Jxnas", "Emmi_", "KekseKiller", "Ravenclaw_", "Pixelpaul", "Nordlicht", "Sturmwind", "Dragonslayer99", "Miraculix",
  "Fynnix", "Leonidas_", "Kuchenmonster", "Mr_Mango", "VoidWalker", "Frostbyte", "HexeLilly", "Bambusbaer", "ItsMaxi", "Enderjunge",
  "Glitzerkeks", "Turbotim", "Waldgeist", "Lavalampe", "Schattenklinge", "Kruemel", "Netherfox", "SilentSam", "Blobfisch", "OrangeJuice",
  "Magmarix", "Vanillekipferl", "Juniper", "Wolkenreiter", "PlatinPaul", "Sonnenblume", "Eisbaer_", "Kalle_", "Mondkind", "Funkenflug",
  "Ruebennase", "TheRealJonas", "NoobMaster_", "Seeadler", "Kirschkern", "Polarstern", "Donnerkeil", "Nebelkraehe", "Kristallin", "Pfannkuchen",
  "SchokoLeon", "Gewitterhexe", "BaronVonBlock", "Wirbelwind", "Lichtblick", "Kaktusjack", "Zuckerwatte", "Grimmbart", "Feenstaub", "Schmiedemeister",
] as const;

const CHAT = [
  "hat jemand bock auf krypta? brauchen noch nen heiler", "gg", "wo kauft man die mithril spitzhacke", "server läuft heute richtig smooth",
  "boss spawnt gleich am eldervale tor", "tausche 3 ätherkristalle gegen glutkern-erz", "danke shumi!", "wie komm ich auf die inseln?",
  "kann wer bei der quest 'der verlorene schmied' helfen", "lol", "brb kurz essen", "wer hat meine kiste geplündert 😭", "nice drop!!",
  "ist das event heute abend?", "jo bin dabei", "welches level braucht man für glutkern?", "eldervale ist so schön geworden", "hab endlich die ätherklinge",
  "suche gilde, bin lvl 34", "wtb verzauberte bücher", "omg der neue boss ist krank", "wo ist der schmied in eldervale", "ich farm noch kurz holz",
  "jemand online der handeln will?", "haha", "die musik im hub ist so gut", "wann kommt der nächste dungeon?", "mein pet ist verschwunden?",
  "bin gleich wieder da", "ty", "geht jemand mit in den exile?", "hab 2k gold für das schwert", "das texture pack ist mega", "gn8 leute",
  "moin", "servus", "wie macht man die insel größer", "wer hat die meisten quests?", "kann man die rüstung färben?", "frag mal im discord",
];
const COMMANDS = [
  "/warp eldervale", "/is home", "/party invite {o}", "/ah", "/quests", "/spawn", "/msg {o} bin gleich da", "/dungeon join krypta", "/guild chat treffen am tor",
  "/trade {o}", "/home base", "/bal", "/pay {o} 250", "/is visit {o}", "/pet summon", "/kit daily", "/report {o} spammt im chat", "/warp markt",
];
const DEATHS = [
  "{n} wurde von einem Eldervale-Wächter erschlagen", "{n} ist in Lava geschwommen", "{n} ist aus zu großer Höhe gefallen", "{n} wurde von einem Skelett erschossen",
  "{n} wurde von der Krypta-Königin zerrissen", "{n} ist ertrunken", "{n} wurde von {o} besiegt", "{n} wurde von einem Glutgolem zerquetscht",
];
const CLIENTS = ["1.21.1", "1.21.1", "1.21.1", "1.21.4", "1.21.3", "1.21.1 · Lunar", "1.21.1 · Fabric", "1.21.4 · Badlion"];

// ------------------------------------------------------------------ the demo world

interface DPlayer {
  uuid: string;
  name: string;
  firstSeen: number;
  lastSeen: number;
  playtime: number;
  sessions: number;
  lastServer: string;
  client: string;
  lastIp: string;
  level: number;
}

interface DOnline extends OnlinePlayer {
  joined: number;
  x: number;
  y: number;
  z: number;
}

interface State {
  t0: number;
  players: DPlayer[];
  byUuid: Map<string, DPlayer>;
  online: Map<string, DOnline>;
  events: HelmEvent[];
  nextEvent: number;
  punishments: Punishment[];
  reports: Report[];
  audit: AuditRow[];
  alerts: Alert[];
  alertHistory: { id: number; key: string; level: string; title: string; detail?: string; server?: string; since: number; resolved?: number }[];
  notes: Map<string, { id: number; byUser: string; ts: number; text: string }[]>;
  team: any[];
  settings: any;
  srv: Record<string, { state: string; uptimeFrom: number; heap: number; gc: number; gcTime: number; power?: { op: string; since: number; by: string } }>;
  peak: number;
  unique: number;
  console: Record<string, ConsoleLine[]>;
}

let S: State | null = null;
const st = () => (S ??= build());

function build(): State {
  const r = rng(7);
  const now = Date.now();
  const players: DPlayer[] = NAMES.map((name, i) => {
    const first = now - (i < 2 ? 64 : 2 + Math.floor(r() * 58)) * DAY - Math.floor(r() * DAY);
    const pt = (i < 2 ? 220 : 4 + r() * 140) * HOUR;
    return {
      uuid: uuidOf(name),
      name,
      firstSeen: first,
      lastSeen: now - Math.floor(r() ** 2 * 6 * DAY),
      playtime: pt,
      sessions: Math.max(3, Math.round(pt / (HOUR * (0.8 + r() * 1.6)))),
      lastServer: pick(r, ["mmor", "mmor", "mmor", "hub", "mmod", "mmoc", "mmoi"]),
      client: pick(r, CLIENTS),
      lastIp: `${pick(r, ["203.0.113", "198.51.100", "192.0.2"])}.${1 + Math.floor(r() * 250)}`,
      level: i < 2 ? 60 : Math.floor(3 + r() * 52),
    };
  });
  const byUuid = new Map(players.map((p) => [p.uuid, p]));

  const want = Math.round(clamp(netAt(now), 12, 56));
  const online = new Map<string, DOnline>();
  const pool = [...players.slice(2)].sort(() => r() - 0.5);
  const chosen = [players[1], ...pool.slice(0, want - 1)];
  for (const p of chosen) {
    const sv = p.name === "Shuminite" ? "mmor" : weighted(r);
    const o: DOnline = {
      uuid: p.uuid,
      name: p.name,
      server: sv,
      ping: Math.round(14 + r() ** 2 * 90),
      client: p.client,
      world: pick(r, WORLDS[sv]).name,
      gamemode: p.name === "Shuminite" ? "creative" : "survival",
      health: Math.round(8 + r() * 12),
      level: p.level,
      afk: r() < 0.12 ? Math.round(120 + r() * 1500) : 0,
      joined: now - Math.floor(r() * 3 * HOUR),
      x: Math.round(-1500 + r() * 3000),
      y: Math.round(62 + r() * 40),
      z: Math.round(-1500 + r() * 3000),
    };
    online.set(p.uuid, o);
    p.lastSeen = now;
    p.lastServer = sv;
  }

  const s: State = {
    t0: now,
    players,
    byUuid,
    online,
    events: [],
    nextEvent: 1,
    punishments: [],
    reports: [],
    audit: [],
    alerts: [],
    alertHistory: [],
    notes: new Map(),
    team: [],
    settings: {
      discordWebhook: "https://discord.com/api/webhooks/…/••••••••",
      discordSet: true,
      alerts: { tpsWarn: 17, tpsCrit: 12, msptWarn: 50, heapWarn: 0.9, cpuWarn: 0.9, memWarn: 0.92, diskWarn: 0.9 },
    },
    srv: {},
    peak: peakToday(now),
    unique: Math.round(peakToday(now) * 1.9 + 6),
    console: {},
  };
  for (const d of SERVERS) {
    const up = d.id === "mmod" ? 5 * HOUR + 12 * MIN : d.id === "proxy" ? 3 * DAY + 4 * HOUR : 19 * HOUR + Math.floor(r() * 4 * HOUR);
    s.srv[d.id] = { state: "online", uptimeFrom: now - up, heap: BASE[d.id].heap, gc: Math.round(up / 9000), gcTime: Math.round(up / 420) };
  }
  seedHistory(s, r);
  return s;
}

function weighted(r: () => number) {
  let x = r();
  for (const p of PAPER) {
    if ((x -= p.share) <= 0) return p.id;
  }
  return "mmor";
}

function nameFor(s: State, uuid: string) {
  return s.byUuid.get(uuid)?.name ?? "?";
}

function addEvent(s: State, e: Omit<HelmEvent, "id">) {
  const ev = { ...e, id: s.nextEvent++ } as HelmEvent;
  s.events.unshift(ev);
  if (s.events.length > 4000) s.events.length = 4000;
  return ev;
}

function seedHistory(s: State, r: () => number) {
  const now = s.t0;
  const names = s.players.map((p) => p.name);
  const evs: Omit<HelmEvent, "id">[] = [];
  for (let t = now - 6 * HOUR; t < now - 20_000; t += 6_000 + r() * 22_000) {
    const p = s.players[2 + Math.floor(r() * (s.players.length - 2))];
    const other = pick(r, names);
    const sv = weighted(r);
    const x = r();
    if (x < 0.5) evs.push({ ts: t, kind: "chat", server: sv, uuid: p.uuid, name: p.name, text: pick(r, CHAT) });
    else if (x < 0.6) evs.push({ ts: t, kind: "command", server: sv, uuid: p.uuid, name: p.name, text: pick(r, COMMANDS).replace("{o}", other) });
    else if (x < 0.7) evs.push({ ts: t, kind: "join", server: "proxy", uuid: p.uuid, name: p.name, text: `${p.name} hat das Netzwerk betreten` });
    else if (x < 0.78) evs.push({ ts: t, kind: "quit", server: sv, uuid: p.uuid, name: p.name, text: `${p.name} hat das Netzwerk verlassen` });
    else if (x < 0.92) {
      const to = PAPER.map((x) => x.id).filter((x) => x !== sv)[Math.floor(r() * 4)];
      evs.push({ ts: t, kind: "switch", server: to, uuid: p.uuid, name: p.name, text: `${p.name} · ${serverLabel(sv)} → ${serverLabel(to)}` });
    } else evs.push({ ts: t, kind: "death", server: sv, uuid: p.uuid, name: p.name, text: pick(r, DEATHS).replace("{n}", p.name).replace("{o}", other) });
  }
  evs.push({ ts: now - 5 * HOUR - 12 * MIN, kind: "server", server: "mmod", name: "Robb", text: "MMO-D neu gestartet" });
  evs.push({ ts: now - 5 * HOUR - 11 * MIN, kind: "server", server: "mmod", text: "MMO-D ist wieder online" });
  evs.push({ ts: now - 2 * HOUR - 40 * MIN, kind: "staffchat", name: "Shuminite", text: "Heute 20 Uhr Boss-Event am Eldervale-Tor, kommt alle!" });
  evs.sort((a, b) => a.ts - b.ts);
  for (const e of evs) addEvent(s, e);

  // moderation
  const P = (n: string) => s.players.find((p) => p.name === n)!;
  const pun = (n: string, type: Punishment["type"], reason: string, by: string, ago: number, len: number | null, revoked?: [string, number]) => {
    const p = P(n);
    s.punishments.push({
      id: s.punishments.length + 1,
      uuid: p.uuid,
      name: p.name,
      type,
      reason,
      byUser: by,
      created: now - ago,
      expires: len == null ? null : now - ago + len,
      revokedBy: revoked?.[0] ?? null,
      revokedAt: revoked ? now - revoked[1] : null,
    });
  };
  pun("NoobMaster_", "ban", "X-Ray-Mod (Erze im Glutkern)", "Shuminite", 2 * DAY, null);
  pun("Blobfisch", "ban", "Duping mit Shulkerboxen", "Robb", 9 * DAY, null);
  pun("Kaktusjack", "ban", "Beleidigungen nach Verwarnung", "Shuminite", 20 * HOUR, 7 * DAY);
  pun("OrangeJuice", "mute", "Spam im Handelschat", "Shuminite", 3 * HOUR, 24 * HOUR);
  pun("TheRealJonas", "mute", "Werbung für anderen Server", "Robb", 26 * HOUR, 3 * DAY);
  pun("Turbotim", "warn", "Caps im Chat", "Shuminite", 50 * MIN, null);
  pun("Kxlli", "kick", "AFK-Farm am Spawn", "Shuminite", 4 * HOUR, null);
  pun("Lavalampe", "mute", "Spam", "Shuminite", 3 * DAY, 2 * HOUR, ["Shuminite", 3 * DAY - 40 * MIN]);
  pun("Grimmbart", "ban", "Griefing in Eldervale", "Robb", 6 * DAY, 3 * DAY);
  pun("Mr_Mango", "warn", "Fremde Kisten geöffnet", "Robb", 30 * HOUR, null);

  const rep = (from: string, to: string, reason: string, sv: string, ago: number, done?: [string, string, number]) => {
    const a = P(from);
    const b = P(to);
    s.reports.push({
      id: s.reports.length + 1,
      ts: now - ago,
      server: sv,
      reporterUuid: a.uuid,
      reporterName: a.name,
      targetUuid: b.uuid,
      targetName: b.name,
      reason,
      status: done ? "resolved" : "open",
      resolvedBy: done?.[0],
      resolution: done?.[1],
      resolvedAt: done ? now - done[2] : undefined,
    });
  };
  rep("MoosMaus", "Netherfox", "fliegt im Survival über die Inseln", "mmoi", 14 * MIN);
  rep("Emmi_", "Bl4ckout", "beleidigt im Chat seit 10 Minuten", "mmor", 41 * MIN);
  rep("Pixelpaul", "OrangeJuice", "spammt den Handelschat voll", "mmor", 3 * HOUR + 5 * MIN, ["Shuminite", "Für 24 Std. gemutet", 3 * HOUR]);
  rep("Nordlicht", "NoobMaster_", "findet jedes Erz direkt, x-ray?", "mmod", 2 * DAY + 2 * HOUR, ["Shuminite", "Mit Spectator geprüft, gebannt", 2 * DAY]);
  rep("Seeadler", "Kirschkern", "hat meine Insel-Kiste geklaut", "mmoi", 4 * DAY, ["Robb", "War ein geteilter Insel-Zugang, geklärt", 4 * DAY - HOUR]);
  s.reports.reverse();

  // team
  s.team = [
    { id: 1, username: "robb", display: "Robb", role: "owner", roleLabel: "Owner", mcName: "A3therion", totp: true, created: now - 64 * DAY, lastLogin: now - 2 * MIN, online: true },
    { id: 2, username: "shuminite", display: "Shuminite", role: "coowner", roleLabel: "Co-Owner", mcName: "Shuminite", totp: true, created: now - 64 * DAY, lastLogin: now - 18 * MIN, online: true },
  ];

  // audit trail
  const au = (user: string, action: string, ago: number, target?: string, detail?: string) =>
    s.audit.push({ id: 0, ts: now - ago, user, action, target, detail, ip: user === "Robb" ? "203.0.113.24" : "198.51.100.71" });
  au("Robb", "team.created", 64 * DAY, "Shuminite", "Rolle Co-Owner");
  au("Robb", "player.ban", 9 * DAY, "Blobfisch", "dauerhaft · Duping mit Shulkerboxen");
  au("Robb", "player.ban", 6 * DAY, "Grimmbart", "3 Tage · Griefing in Eldervale");
  au("Robb", "report.resolved", 4 * DAY - HOUR, "#5", "War ein geteilter Insel-Zugang, geklärt");
  au("Shuminite", "player.unmute", 3 * DAY - 40 * MIN, "Lavalampe");
  au("Shuminite", "player.ban", 2 * DAY, "NoobMaster_", "dauerhaft · X-Ray-Mod (Erze im Glutkern)");
  au("Shuminite", "report.resolved", 2 * DAY, "#4", "Mit Spectator geprüft, gebannt");
  au("Robb", "player.mute", 26 * HOUR, "TheRealJonas", "3 Tage · Werbung für anderen Server");
  au("Robb", "player.warn", 30 * HOUR, "Mr_Mango", "Fremde Kisten geöffnet");
  au("Shuminite", "player.ban", 20 * HOUR, "Kaktusjack", "7 Tage · Beleidigungen nach Verwarnung");
  au("Robb", "settings.changed", 19 * HOUR, undefined, "Discord-Webhook gesetzt");
  au("Robb", "login", 7 * HOUR, undefined, "Chrome auf Windows");
  au("Robb", "server.restart", 5 * HOUR + 12 * MIN, "MMO-D", "nach Dungeon-Update");
  au("Robb", "console.command", 5 * HOUR + 2 * MIN, "MMO-D", "spark profiler start --timeout 120");
  au("Shuminite", "login", 4 * HOUR + 30 * MIN, undefined, "Helm-App auf Android");
  au("Shuminite", "player.kick", 4 * HOUR, "Kxlli", "AFK-Farm am Spawn");
  au("Shuminite", "player.mute", 3 * HOUR, "OrangeJuice", "24 Std. · Spam im Handelschat");
  au("Shuminite", "report.resolved", 3 * HOUR, "#3", "Für 24 Std. gemutet");
  au("Shuminite", "chat.broadcast", 2 * HOUR + 40 * MIN, "alle", "Heute 20 Uhr Boss-Event am Eldervale-Tor, kommt alle!");
  au("Shuminite", "player.inventory", 75 * MIN, "Netherfox");
  au("Shuminite", "player.note", 72 * MIN, "Netherfox", "Hat 6 Elytren nach 2 Tagen Spielzeit, beobachten.");
  au("Shuminite", "player.warn", 50 * MIN, "Turbotim", "Caps im Chat");
  au("Shuminite", "console.command", 22 * MIN, "MMO-R", "co inspect");
  au("Robb", "login.failed", 9 * MIN, undefined, "falscher 2FA-Code");
  au("Robb", "login", 8 * MIN, undefined, "Safari auf iOS");
  s.audit.sort((a, b) => a.ts - b.ts).forEach((a, i) => (a.id = i + 1));
  s.audit.reverse();

  s.notes.set(P("Netherfox").uuid, [{ id: 1, byUser: "Shuminite", ts: now - 72 * MIN, text: "Hat 6 Elytren nach 2 Tagen Spielzeit, beobachten." }]);
  s.notes.set(P("NoobMaster_").uuid, [
    { id: 2, byUser: "Shuminite", ts: now - 2 * DAY - 30 * MIN, text: "Geht im Glutkern immer direkt zu Diamanten, Spectator läuft mit." },
    { id: 3, byUser: "Shuminite", ts: now - 2 * DAY, text: "Bestätigt: X-Ray-Texturen im Screenshot. Gebannt." },
  ]);

  // alerts: one live warning so the panel shows what it looks like
  s.alerts = [
    {
      key: "mspt:mmod",
      level: "warn",
      title: "MMO-D: Tick-Zeit erhöht",
      detail: "Ø über 45 ms seit einigen Minuten. Meist ein voller Dungeon-Run in der Krypta.",
      server: "mmod",
      since: now - 7 * MIN,
    },
  ];
  s.alertHistory = [
    { id: 4, key: "mspt:mmod", level: "warn", title: "MMO-D: Tick-Zeit erhöht", detail: "Ø über 45 ms", server: "mmod", since: now - 7 * MIN },
    { id: 3, key: "down:mmod", level: "crit", title: "MMO-D ist nicht erreichbar", detail: "Neustart durch Robb", server: "mmod", since: now - 5 * HOUR - 12 * MIN, resolved: now - 5 * HOUR - 11 * MIN },
    { id: 2, key: "heap:mmor", level: "warn", title: "MMO-R: Heap über 90 %", detail: "GC hat aufgeräumt", server: "mmor", since: now - 26 * HOUR, resolved: now - 26 * HOUR + 4 * MIN },
    { id: 1, key: "tps:mmor", level: "warn", title: "MMO-R: TPS unter 17", detail: "Boss-Event mit 41 Spielern", server: "mmor", since: now - 2 * DAY - 3 * HOUR, resolved: now - 2 * DAY - 3 * HOUR + 9 * MIN },
  ];
}

function serverLabel(id?: string) {
  return SERVERS.find((s) => s.id === id)?.name ?? id ?? "–";
}

// ------------------------------------------------------------------ live numbers

function playersOn(s: State, id: string) {
  let n = 0;
  for (const o of s.online.values()) if (o.server === id) n++;
  return n;
}

function serverNow(s: State, id: string): Server {
  const d = SERVERS.find((x) => x.id === id)!;
  const b = BASE[id];
  const sv = s.srv[id];
  const now = Date.now();
  const up = sv.state === "online";
  const n = id === "proxy" ? s.online.size : playersOn(s, id);
  const load = 0.55 + n / 30;
  if (!up) {
    return { id, name: d.name, role: d.role, kind: d.kind, state: sv.state as Server["state"], agent: false, xmx: d.xmx, players: 0, power: sv.power };
  }
  // heap climbs, then a GC drops it
  sv.heap += b.heap * (0.004 + Math.random() * 0.01);
  const heapMax = parseInt(d.xmx) * GB;
  if (sv.heap > b.heap * 1.22 || sv.heap > heapMax * 0.93) {
    sv.heap = b.heap * (0.78 + Math.random() * 0.06);
    sv.gc++;
    sv.gcTime += 30 + Math.random() * 60;
  }
  const base: Server = {
    id,
    name: d.name,
    role: d.role,
    kind: d.kind,
    state: "online",
    agent: true,
    agentVersion: "1.0.0",
    pid: 1000 + (hash(id) % 60000),
    xmx: d.xmx,
    players: n,
    maxPlayers: id === "proxy" ? 500 : 150,
    heapUsed: Math.round(sv.heap),
    heapCommitted: Math.round(heapMax * 0.86),
    heapMax,
    cpu: jitter(b.cpu * load, 0.12),
    threads: b.threads + Math.round(Math.random() * 4),
    uptime: now - sv.uptimeFrom,
    gcCount: sv.gc,
    gcTime: Math.round(sv.gcTime),
    rss: Math.round(jitter(b.rss, 0.004)),
    hostCpu: undefined,
    power: sv.power,
  };
  if (d.kind === "velocity") {
    base.backends = PAPER.map((p) => ({
      name: p.id,
      id: p.id,
      connected: playersOn(s, p.id),
      reachable: s.srv[p.id].state === "online",
      latency: s.srv[p.id].state === "online" ? Math.round(jitter(p.id === "mmod" ? 3 : 1.4, 0.4) * 10) / 10 : undefined,
      version: "Paper 1.21.1",
      online: playersOn(s, p.id),
      max: 150,
      checked: now - Math.floor(Math.random() * 9000),
    }));
    return base;
  }
  const mspt = clamp(jitter(b.mspt * (0.75 + n / 40), 0.1), 2, 120);
  const ticks = Array.from({ length: 60 }, () => {
    const spike = Math.random() < 0.03 ? 2.2 + Math.random() * 1.5 : 1;
    return Math.round(clamp(jitter(mspt, 0.22) * spike, 1, 160) * 10) / 10;
  });
  const tps1 = mspt <= 50 ? 20 - Math.random() * 0.04 : clamp(1000 / mspt, 5, 20);
  const worlds = WORLDS[id].map((w) => {
    const here = [...s.online.values()].filter((o) => o.server === id && o.world === w.name).length;
    return {
      ...w,
      players: here,
      chunks: Math.round(jitter(w.chunks * (0.7 + here / 12), 0.03)),
      entities: Math.round(jitter(w.entities * (0.75 + here / 14), 0.04)),
      tiles: w.tiles,
      time: Math.floor(((now / 50) % 24000) + hash(w.name) % 4000) % 24000,
      storm: w.name === "world" && new Date().getHours() % 5 === 0,
    };
  });
  return {
    ...base,
    tps: [Math.round(tps1 * 100) / 100, Math.round(Math.min(20, tps1 + 0.02) * 100) / 100, 19.97],
    mspt: Math.round(mspt * 10) / 10,
    msptP95: Math.round(Math.max(...ticks.slice().sort((a, b) => a - b).slice(0, 57)) * 10) / 10,
    msptMax: Math.max(...ticks),
    ticks,
    chunks: worlds.reduce((a, w) => a + w.chunks, 0),
    entities: worlds.reduce((a, w) => a + w.entities, 0),
    tiles: worlds.reduce((a, w) => a + w.tiles, 0),
    worlds,
  };
}

function hostNow(s: State) {
  const n = s.online.size;
  const cpu = clamp(jitter(0.12 + n * 0.0062, 0.14), 0.05, 0.98);
  const memTotal = 62.6 * GB;
  const rssSum = Object.values(BASE).reduce((a, b) => a + b.rss, 0);
  return {
    cpu,
    iowait: jitter(0.006, 0.5),
    steal: 0,
    memTotal,
    memUsed: Math.round(rssSum + 2.9 * GB + Math.random() * 0.2 * GB),
    memCached: Math.round(21.4 * GB),
    swapTotal: 4 * GB,
    swapUsed: Math.round(0.21 * GB),
    load: [Math.round(cpu * 16 * 1.08 * 100) / 100, Math.round(cpu * 16 * 1.02 * 100) / 100, Math.round(cpu * 16 * 0.97 * 100) / 100],
    rx: Math.round(jitter(260_000 + n * 21_000, 0.18)),
    tx: Math.round(jitter(620_000 + n * 64_000, 0.15)),
    rxTotal: 8.1e12,
    txTotal: 21.7e12,
    diskRead: Math.round(jitter(1.4 * MB, 0.6)),
    diskWrite: Math.round(jitter(4.8 * MB, 0.5)),
    diskBusy: jitter(0.04, 0.5),
    diskTotal: Math.round(937 * GB),
    diskUsed: Math.round(318.6 * GB),
    uptime: 41 * DAY + 6 * HOUR + (Date.now() - s.t0),
    cores: 16,
    procs: 412,
  };
}

function snapshot(s: State): Snapshot {
  const servers = SERVERS.map((d) => serverNow(s, d.id));
  return {
    ts: Date.now(),
    servers,
    network: { online: s.online.size, peakToday: Math.max(s.peak, s.online.size), uniqueToday: s.unique, maxPlayers: 500 },
    players: [...s.online.values()].map(({ joined, x, y, z, ...o }) => o),
    host: hostNow(s),
    alerts: s.alerts,
    openReports: s.reports.filter((r) => r.status === "open").length,
  };
}

// ------------------------------------------------------------------ history series

const RANGES: Record<string, number> = { "1h": HOUR, "6h": 6 * HOUR, "24h": DAY, "7d": 7 * DAY, "30d": 30 * DAY };

function buckets(range: string) {
  const len = RANGES[range] ?? 6 * HOUR;
  const b = Math.max(30_000, Math.floor(len / 180 / 30_000) * 30_000);
  const now = Date.now();
  const out: number[] = [];
  for (let t = Math.floor((now - len) / b) * b; t <= now; t += b) out.push(t);
  return { ts: out, b };
}

function peakToday(now: number) {
  const start = new Date(now).setHours(0, 0, 0, 0);
  let m = 0;
  for (let t = start; t <= now; t += 5 * MIN) m = Math.max(m, netAt(t));
  return Math.round(m * 1.08);
}

function netAt(t: number) {
  return 58 * dayCurve(t) * (1 + 0.08 * Math.sin(t / 2.7e6)) * (0.94 + 0.06 * Math.sin(t / 7.1e5));
}

function networkHistory(range: string) {
  const s = st();
  const { ts, b } = buckets(range);
  const k = clamp(s.online.size / Math.max(1, netAt(Date.now())), 0.85, 1.2);
  return ts.map((t) => {
    const v = Math.max(0, netAt(t) * k);
    return { t, players: Math.round(v * 100) / 100, playersMax: Math.round(v * (1 + Math.min(0.35, b / DAY))) };
  });
}

function serverHistory(id: string, range: string) {
  const s = st();
  const d = SERVERS.find((x) => x.id === id)!;
  const b = BASE[id];
  const { ts } = buckets(range);
  const share = id === "proxy" ? 1 : d.share;
  const k = clamp(s.online.size / Math.max(1, netAt(Date.now())), 0.85, 1.2);
  const r = rng(hash(id + range));
  return ts.map((t) => {
    const n = netAt(t) * k * share;
    const restartGap = id === "mmod" && Math.abs(t - (s.t0 - 5 * HOUR - 11.5 * MIN)) < 90_000;
    if (restartGap) return { t, players: null, playersMax: null, tps: null, tpsMin: null, mspt: null, msptMax: null, heap: null, cpu: null, rss: null, entities: null, chunks: null };
    const mspt = b.mspt * (0.7 + n / 40) * (0.9 + r() * 0.2);
    const spike = r() < 0.03 ? 1.5 + r() : 1;
    const ents = WORLDS[id]?.reduce((a, w) => a + w.entities, 0) ?? 0;
    return {
      t,
      players: Math.round(n * 100) / 100,
      playersMax: Math.round(n * 1.25),
      tps: id === "proxy" ? null : Math.round(Math.min(20, 1000 / Math.max(50, mspt)) * 100) / 100,
      tpsMin: id === "proxy" ? null : Math.round(Math.min(20, 1000 / Math.max(50, mspt * spike * 1.4)) * 100) / 100,
      mspt: id === "proxy" ? null : Math.round(mspt * 100) / 100,
      msptMax: id === "proxy" ? null : Math.round(mspt * spike * 1.35 * 100) / 100,
      heap: Math.round(b.heap * (0.97 + 0.06 * Math.sin(t / 3.6e6) + r() * 0.04)),
      cpu: Math.round(b.cpu * (0.55 + n / 30) * (0.9 + r() * 0.2) * 10000) / 10000,
      rss: Math.round(b.rss * (0.985 + r() * 0.02)),
      entities: id === "proxy" ? null : Math.round(ents * (0.75 + n / 14) * (0.95 + r() * 0.1)),
      chunks: id === "proxy" ? null : Math.round((WORLDS[id]?.reduce((a, w) => a + w.chunks, 0) ?? 0) * (0.7 + n / 12) * (0.97 + r() * 0.06)),
    };
  });
}

function hostHistory(range: string) {
  const s = st();
  const { ts } = buckets(range);
  const k = clamp(s.online.size / Math.max(1, netAt(Date.now())), 0.85, 1.2);
  const r = rng(hash("host" + range));
  const rssSum = Object.values(BASE).reduce((a, b) => a + b.rss, 0);
  return ts.map((t) => {
    const n = netAt(t) * k;
    const cpu = clamp((0.12 + n * 0.0062) * (0.9 + r() * 0.2), 0.04, 0.95);
    const backup = new Date(t).getHours() === 4 && new Date(t).getMinutes() < 25;
    return {
      t,
      cpu: Math.round((backup ? cpu + 0.18 : cpu) * 10000) / 10000,
      mem: Math.round(rssSum + 2.9 * GB + r() * 0.3 * GB),
      swap: Math.round(0.2 * GB),
      rx: Math.round((260_000 + n * 21_000) * (0.85 + r() * 0.3)),
      tx: Math.round((620_000 + n * 64_000) * (0.85 + r() * 0.3)),
      load1: Math.round(cpu * 16 * (0.95 + r() * 0.15) * 100) / 100,
      diskRead: Math.round((backup ? 180 : 1.4) * MB * (0.6 + r() * 0.8)),
      diskWrite: Math.round((backup ? 95 : 4.8) * MB * (0.6 + r() * 0.8)),
      players: Math.round(n * 100) / 100,
      playersMax: Math.round(n * 1.2),
    };
  });
}

// ------------------------------------------------------------------ detail pages

const PLUGINS = [
  ["AetherionCore", "2.14.0"], ["AetherionItems", "1.9.3"], ["AetherionQuests", "1.12.1"], ["BossEngine", "1.6.0"], ["AetherMobs", "1.4.2"],
  ["AetherionGuilds", "1.3.0"], ["AetherionHelm", "1.0.0"], ["LuckPerms", "5.4.141"], ["Multiverse-Core", "4.3.14"], ["WorldEdit", "7.3.6"],
  ["WorldGuard", "7.0.12"], ["CoreProtect", "22.4"], ["PlaceholderAPI", "2.11.6"], ["Vault", "1.7.3"], ["spark", "1.10.109"], ["Chunky", "1.4.16"],
] as const;

function serverDetail(id: string) {
  const s = st();
  const d = SERVERS.find((x) => x.id === id);
  if (!d) fail(404, "Server unbekannt");
  const live = serverNow(s, id);
  const proxy = d.kind === "velocity";
  const plugins = proxy
    ? [["AetherionProxy", "1.8.0"], ["aetherionhelm", "1.0.0"], ["luckperms", "5.4.141"], ["spark", "1.10.109"]]
    : PLUGINS.filter(([n]) => !(id === "hub" && (n === "AetherionItems" || n === "AetherionQuests")) && !(n === "AetherMobs" && id !== "mmor") && !(n === "AetherionGuilds" && id !== "mmor" && id !== "mmoi"));
  const worldBytes: Record<string, number> = {};
  for (const w of WORLDS[id] ?? []) worldBytes[w.name] = Math.round(w.chunks * 1.9 * MB * (1 + (hash(w.name) % 40) / 100));
  const total = Object.values(worldBytes).reduce((a, b) => a + b, 0) + 1.3 * GB;
  return {
    ...live,
    info: {
      software: proxy ? "Velocity 3.4.0-SNAPSHOT (git-6d1f4a2b-b512)" : "Paper version 1.21.1-133-ver/1.21.1@7d16a2c (MC: 1.21.1)",
      mc: proxy ? undefined : "1.21.1",
      java: proxy ? "25" : "21.0.5",
      jvm: proxy ? "OpenJDK 64-Bit Server VM 25+36" : "OpenJDK 64-Bit Server VM 21.0.5+11-LTS",
      cpus: 16,
      jvmArgs: [`-Xms${d.xmx}`, `-Xmx${d.xmx}`, "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200", "-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=8M", "-Dusing.aikars.flags=https://mcflags.emc.gs"],
      viewDistance: proxy ? undefined : id === "hub" ? 6 : 10,
      simulationDistance: proxy ? undefined : id === "hub" ? 4 : 6,
      onlineMode: proxy,
      whitelist: proxy ? undefined : false,
      motd: proxy ? "§b§lAetherion §8» §7Das MMO-Abenteuer §8| §d1.21.1" : `Aetherion ${d.name}`,
      startedAt: s.srv[id].uptimeFrom,
      plugins: plugins.map(([name, version]) => ({ name, version, enabled: true })),
      backends: proxy ? PAPER.map((p) => ({ name: p.id, id: p.id })) : undefined,
      tryOrder: proxy ? ["hub"] : undefined,
      compressionThreshold: proxy ? 256 : undefined,
    },
    disk: proxy ? { bytes: 210 * MB } : { bytes: Math.round(total), worlds: worldBytes },
    watchdog:
      id === "mmod"
        ? [
            `${stampOf(s.t0 - 5 * HOUR - 12 * MIN)} Neustart angefordert von Robb`,
            `${stampOf(s.t0 - 5 * HOUR - 12 * MIN + 20_000)} Server gestoppt (sauber, 18 s)`,
            `${stampOf(s.t0 - 5 * HOUR - 11 * MIN)} Server gestartet, Done nach 41 s`,
          ]
        : [],
  };
}

function stampOf(ts: number) {
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${p(d.getDate())}.${p(d.getMonth() + 1)}. ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function hostDetail() {
  const s = st();
  const h = hostNow(s);
  const servers = SERVERS.map((d) => serverNow(s, d.id));
  const r = rng(Math.floor(Date.now() / 3000));
  const perCore = Array.from({ length: 16 }, (_, i) => clamp(h.cpu * (0.5 + r() * 1.1) + (i === 3 ? 0.18 : 0), 0.01, 1));
  const processes = [
    ...servers.map((v, i) => ({ pid: v.pid ?? 1000 + i, name: "java", user: "minecraft", cpu: v.cpu ?? 0, rss: v.rss ?? 0, threads: v.threads ?? 0, started: s.srv[v.id].uptimeFrom, server: v.id })),
    { pid: 812, name: "java (helm-gateway)", user: "helm", cpu: 0.004, rss: 182 * MB, threads: 31, started: s.t0 - 19 * HOUR },
    { pid: 790, name: "cloudflared", user: "cloudflared", cpu: 0.002, rss: 38 * MB, threads: 14, started: s.t0 - 41 * DAY },
    { pid: 701, name: "nginx", user: "www-data", cpu: 0.001, rss: 14 * MB, threads: 2, started: s.t0 - 41 * DAY },
    { pid: 1, name: "systemd", user: "root", cpu: 0.0003, rss: 12 * MB, threads: 1, started: s.t0 - 41 * DAY },
    { pid: 455, name: "systemd-journald", user: "root", cpu: 0.0006, rss: 64 * MB, threads: 1, started: s.t0 - 41 * DAY },
    { pid: 980, name: "sshd", user: "root", cpu: 0.0001, rss: 8 * MB, threads: 1, started: s.t0 - 41 * DAY },
    { pid: 611, name: "md2_raid1", user: "root", cpu: 0.0008, rss: 0, threads: 1, started: s.t0 - 41 * DAY },
    { pid: 1201, name: "fail2ban-server", user: "root", cpu: 0.0004, rss: 46 * MB, threads: 4, started: s.t0 - 41 * DAY },
  ].map((p) => ({ ...p, cpu: jitter(p.cpu, 0.1) }));
  return {
    ...h,
    perCore,
    interfaces: [{ name: "enp41s0", rx: h.rx, tx: h.tx, rxTotal: h.rxTotal, txTotal: h.txTotal }],
    disks: [
      { name: "nvme0n1", read: h.diskRead / 2, write: h.diskWrite, busy: h.diskBusy },
      { name: "nvme1n1", read: h.diskRead / 2, write: h.diskWrite, busy: h.diskBusy * 0.96 },
    ],
    filesystems: [
      { mount: "/", device: "/dev/md2", type: "ext4", total: h.diskTotal, used: h.diskUsed, free: h.diskTotal - h.diskUsed },
      { mount: "/boot", device: "/dev/md1", type: "ext3", total: 1006 * MB, used: 212 * MB, free: 794 * MB },
    ],
    temps: [
      { chip: "k10temp", label: "Tctl", c: Math.round(jitter(54 + h.cpu * 30, 0.02)), crit: 95 },
      { chip: "nvme", label: "nvme0 Composite", c: 41, crit: 84 },
      { chip: "nvme", label: "nvme1 Composite", c: 43, crit: 84 },
    ],
    static: {
      cpuModel: "AMD Ryzen 7 7700 8-Core Processor",
      threads: 16,
      cores: 8,
      mhz: 3800,
      hostname: "aetherion",
      kernel: "6.8.0-45-generic",
      os: "Ubuntu 24.04.1 LTS",
      vendor: "Hetzner",
      product: "AX52",
      board: "ASRockRack B665D4U-1L",
      java: "21.0.5 · 25",
      disks: [
        { name: "nvme0n1", model: "SAMSUNG MZVL21T0HCLR-00B00", size: 1024 * GB, ssd: true },
        { name: "nvme1n1", model: "SAMSUNG MZVL21T0HCLR-00B00", size: 1024 * GB, ssd: true },
      ],
      nics: [{ name: "enp41s0", speed: 1000, state: "up" }],
      raid: [
        { name: "md2", state: "clean", level: "raid1", devices: ["nvme0n1p3", "nvme1n1p3"], map: "[UU]", degraded: false },
        { name: "md1", state: "clean", level: "raid1", devices: ["nvme0n1p2", "nvme1n1p2"], map: "[UU]", degraded: false },
      ],
    },
    ctl: {
      sizes: {
        servers: Object.fromEntries(
          SERVERS.map((d) => [d.id, { bytes: (serverDetail(d.id).disk.bytes as number) ?? 0, worlds: (serverDetail(d.id).disk as any).worlds }]),
        ),
        backups: { bytes: Math.round(142.3 * GB), count: 14, latest: todayAt(4, 0), latestName: `snap-${dateTag(todayAt(4, 0))}` },
      },
      watchdog: [],
    },
    processes,
    gatewayUptime: Date.now() - (s.t0 - 19 * HOUR),
  };
}

function todayAt(h: number, m: number) {
  const d = new Date();
  d.setHours(h, m, 0, 0);
  if (d.getTime() > Date.now()) d.setDate(d.getDate() - 1);
  return d.getTime();
}

function dateTag(ts: number) {
  const d = new Date(ts);
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}-0400`;
}

function playerRow(s: State, p: DPlayer) {
  const o = s.online.get(p.uuid);
  return {
    uuid: p.uuid,
    name: p.name,
    firstSeen: p.firstSeen,
    lastSeen: o ? Date.now() : p.lastSeen,
    lastServer: o?.server ?? p.lastServer,
    playtime: p.playtime + (o ? Date.now() - o.joined : 0),
    sessions: p.sessions,
    online: !!o,
    server: o?.server,
    lastIp: p.lastIp,
    client: p.client,
  };
}

function playerDetail(uuid: string) {
  const s = st();
  const p = s.byUuid.get(uuid);
  if (!p) fail(404, "Spieler nicht gefunden");
  const o = s.online.get(uuid);
  const r = rng(hash(uuid));
  const now = Date.now();
  const sessions: { start: number; end: number | null }[] = [];
  let t = o ? o.joined : p.lastSeen;
  if (o) sessions.push({ start: o.joined, end: null });
  for (let i = 0; i < 24; i++) {
    const end = t - (2 + r() * 20) * HOUR;
    const start = end - (0.3 + r() * 3) * HOUR;
    if (start < p.firstSeen) break;
    sessions.push({ start, end });
    t = start;
  }
  const activity = Array.from({ length: 7 }, (_, d) =>
    Array.from({ length: 24 }, (_, h) => {
      const base = Math.exp(-((h - 20) ** 2) / 8) * (d >= 5 ? 1.3 : 1) + 0.25 * Math.exp(-((h - 15) ** 2) / 5);
      return Math.round(base * (0.4 + r()) * 55);
    }),
  );
  const ip = p.lastIp;
  const alt = s.players.find((q) => q.uuid !== uuid && hash(q.uuid) % 23 === hash(uuid) % 23);
  return {
    uuid,
    name: p.name,
    record: playerRow(s, p),
    live: o
      ? {
          ...o,
          x: o.x,
          y: o.y,
          z: o.z,
          food: 14 + Math.floor(r() * 7),
          maxHealth: 20,
          op: p.name === "Shuminite",
          flying: o.gamemode === "creative",
          brand: o.client?.includes("Fabric") ? "fabric" : o.client?.includes("Lunar") ? "lunarclient:v2.18.3" : "vanilla",
          locale: "de_de",
          ip,
          protocol: o.client?.startsWith("1.21.4") ? 769 : 767,
          host: "play.example.net",
        }
      : undefined,
    punishments: s.punishments.filter((x) => x.uuid === uuid).sort((a, b) => b.created - a.created),
    notes: s.notes.get(uuid) ?? [],
    ips: [
      { ip, first: p.firstSeen, last: o ? now : p.lastSeen, accounts: alt ? 2 : 1 },
      ...(r() < 0.4 ? [{ ip: `192.0.2.${1 + Math.floor(r() * 250)}`, first: p.firstSeen, last: p.firstSeen + 3 * DAY, accounts: 1 }] : []),
    ],
    alts: alt ? [{ uuid: alt.uuid, name: alt.name, lastSeen: alt.lastSeen }] : [],
    sessions,
    activity,
    events: s.events.filter((e) => e.uuid === uuid).slice(0, 120),
  };
}

// ------------------------------------------------------------------ inventories

const txt = (text: string, color?: string, extra?: Record<string, unknown>) => ({ text, color, italic: false, ...extra });

function inventory(uuid: string) {
  const s = st();
  const p = s.byUuid.get(uuid);
  const o = s.online.get(uuid);
  if (!p || !o) fail(409, "Spieler ist gerade nicht online");
  const r = rng(hash(uuid) ^ 0x51);
  const it = (slot: number, id: string, count = 1, extra: Partial<Item> = {}): Item => ({ slot, id, count, ...extra });
  const ench = (...e: [string, number][]) => e.map(([id, lvl]) => ({ id, lvl }));
  const strong = p.level > 25;
  const metal = strong ? "netherite" : "diamond";
  const inventory: Item[] = [
    it(0, `${metal}_sword`, 1, {
      name: [txt("Ätherklinge", "#b48cff", { bold: true })],
      lore: [
        txt("Geschmiedet in den Tiefen von Eldervale.", "gray"),
        txt(""),
        txt("+14 Angriffsschaden", "green"),
        txt("+8 % Kritische Trefferchance", "green"),
        txt("Sockel: ", "gray", { extra: [txt("◆ Glutstein", "gold"), txt("  ◇ leer", "dark_gray")] }),
        txt(""),
        txt("EPISCH · WAFFE", "light_purple", { bold: true }),
      ],
      ench: ench(["sharpness", 5], ["looting", 3], ["unbreaking", 3], ["mending", 1]),
      glint: true,
      cmd: 10021,
      rarity: "epic",
      dmg: Math.floor(r() * 400),
      maxDmg: strong ? 2031 : 1561,
    }),
    it(1, `${metal}_pickaxe`, 1, { ench: ench(["efficiency", 5], ["fortune", 3], ["unbreaking", 3], ["mending", 1]), glint: true, dmg: Math.floor(r() * 1200), maxDmg: strong ? 2031 : 1561 }),
    it(2, "bow", 1, { ench: ench(["power", 5], ["infinity", 1], ["flame", 1]), glint: true, dmg: 120, maxDmg: 384 }),
    it(3, "golden_carrot", 41),
    it(4, "ender_pearl", 16),
    it(5, "enchanted_golden_apple", 3, { rarity: "epic" }),
    it(6, "firework_rocket", 64),
    it(7, "torch", 48),
    it(8, "cobblestone", 64),
    it(9, "arrow", 1),
    it(10, "experience_bottle", 23),
    it(11, "oak_log", 64),
    it(12, "oak_log", 37),
    it(13, "diamond", 12 + Math.floor(r() * 30)),
    it(14, "emerald", 24),
    it(15, "amethyst_shard", 18, { name: [txt("Ätherkristall", "aqua")], lore: [txt("Rohstoff · Selten", "dark_aqua")], cmd: 3011, rarity: "rare" }),
    it(16, "blaze_rod", 9),
    it(18, "purple_shulker_box", 1, {
      name: [txt("Dungeon-Vorrat", "light_purple")],
      contents: [
        it(0, "golden_apple", 16),
        it(1, "cooked_beef", 64),
        it(2, "splash_potion", 3, { name: [txt("Trank der Heilung", "red")] }),
        it(3, "totem_of_undying", 2, { rarity: "uncommon" }),
        it(4, "ender_pearl", 16),
        it(5, "obsidian", 32),
        it(6, "end_crystal", 4),
        it(13, "netherite_ingot", 2),
      ],
    }),
    it(19, "map", 1, { name: [txt("Karte von Eldervale", "gold")] }),
    it(20, "compass", 1),
    it(22, "name_tag", 2),
    it(24, "echo_shard", 5),
    it(26, "spyglass", 1),
    it(27, "water_bucket", 1),
    it(28, "shield", 1, { ench: ench(["unbreaking", 3], ["mending", 1]), glint: true, dmg: 40, maxDmg: 336 }),
    it(31, "book", 3),
    it(33, "enchanted_book", 1, { ench: ench(["protection", 4]), glint: true, rarity: "uncommon" }),
    it(35, "bread", 12),
  ].filter((x) => (x.slot ?? 0) < 9 || r() > 0.08);
  const armor: (Item | null)[] = [
    it(39, `${metal}_helmet`, 1, { ench: ench(["protection", 4], ["respiration", 3], ["unbreaking", 3], ["mending", 1]), glint: true, dmg: 80, maxDmg: strong ? 407 : 363 }),
    p.name === "Shuminite" || r() < 0.3
      ? it(38, "elytra", 1, { ench: ench(["unbreaking", 3], ["mending", 1]), glint: true, dmg: 210, maxDmg: 432 })
      : it(38, `${metal}_chestplate`, 1, { ench: ench(["protection", 4], ["unbreaking", 3], ["mending", 1]), glint: true, dmg: 140, maxDmg: strong ? 592 : 528 }),
    it(37, `${metal}_leggings`, 1, { ench: ench(["protection", 4], ["swift_sneak", 3], ["mending", 1]), glint: true, dmg: 60, maxDmg: strong ? 555 : 495 }),
    it(36, `${metal}_boots`, 1, { ench: ench(["feather_falling", 4], ["depth_strider", 3], ["soul_speed", 2], ["mending", 1]), glint: true, dmg: 30, maxDmg: strong ? 481 : 429 }),
  ];
  const enderchest: Item[] = [
    it(0, "shulker_box", 1, { contents: [it(0, "diamond_block", 18), it(1, "emerald_block", 9), it(2, "gold_block", 24)] }),
    it(1, "nether_star", 2, { rarity: "uncommon" }),
    it(2, "beacon", 1, { rarity: "rare" }),
    it(3, "heart_of_the_sea", 1, { rarity: "uncommon" }),
    it(4, "netherite_ingot", 6),
    it(9, "trident", 1, { ench: ench(["loyalty", 3], ["impaling", 5], ["channeling", 1]), glint: true, dmg: 10, maxDmg: 250, rarity: "epic" }),
    it(10, "mace", 1, { ench: ench(["density", 5], ["wind_burst", 2]), glint: true, rarity: "epic", maxDmg: 500, dmg: 12 }),
    it(13, "music_disc_pigstep", 1, { rarity: "rare" }),
    it(18, "player_head", 1, { name: [txt("Kopf von A3therion", "yellow")], rarity: "uncommon" }),
    it(22, "dragon_breath", 7),
    it(26, "experience_bottle", 64),
  ];
  return {
    uuid,
    name: p.name,
    inventory,
    armor,
    offhand: it(40, "totem_of_undying", 1, { rarity: "uncommon" }),
    enderchest,
    selected: 0,
    health: o.health ?? 20,
    maxHealth: 20,
    food: 18,
    saturation: 4.6,
    level: p.level,
    exp: 0.37,
    gamemode: o.gamemode ?? "survival",
    world: o.world ?? "world",
    x: o.x + 0.5,
    y: o.y,
    z: o.z + 0.5,
    effects: [
      { type: "speed", amp: 1, dur: 3 * 60 * 20 + 40 * 20 },
      { type: "night_vision", amp: 0, dur: 7 * 60 * 20 },
      ...(strong ? [{ type: "resistance", amp: 0, dur: 1_000_001 }] : []),
    ],
  };
}

// ------------------------------------------------------------------ entities radar

function entities(id: string) {
  const r = rng(hash(id) ^ Math.floor(Date.now() / 60_000));
  const types = [
    ["item", 0.22], ["zombie", 0.1], ["skeleton", 0.08], ["villager", 0.07], ["cow", 0.06], ["armor_stand", 0.06], ["item_display", 0.06],
    ["text_display", 0.05], ["chicken", 0.05], ["sheep", 0.04], ["experience_orb", 0.04], ["spider", 0.03], ["creeper", 0.03], ["bat", 0.02],
    ["interaction", 0.02], ["wolf", 0.01],
  ] as const;
  const worlds = (WORLDS[id] ?? []).map((w) => {
    const total = Math.round(w.entities * (0.9 + r() * 0.2));
    return { name: w.name, entities: total, chunks: w.chunks, tiles: w.tiles, types: types.map(([type, f]) => ({ type, count: Math.round(total * f * (0.6 + r() * 0.8)) })).sort((a, b) => b.count - a.count) };
  });
  const all = new Map<string, number>();
  for (const w of worlds) for (const t of w.types) all.set(t.type, (all.get(t.type) ?? 0) + t.count);
  const hot = Array.from({ length: 8 }, (_, i) => {
    const w = worlds[i % worlds.length];
    const top = pick(r, ["item", "villager", "zombie", "armor_stand", "cow", "item_display"]);
    const n = Math.round((i === 0 ? 260 : 140 / (i + 1)) * (0.8 + r() * 0.4)) + 18;
    return { world: w?.name ?? "world", x: Math.round(-80 + r() * 160), z: Math.round(-80 + r() * 160), entities: n, tiles: Math.round(r() * 90), top: `${top} ×${Math.round(n * (0.5 + r() * 0.4))}` };
  }).sort((a, b) => b.entities - a.entities);
  return { worlds, types: [...all.entries()].map(([type, count]) => ({ type, count })).sort((a, b) => b.count - a.count), hot };
}

// ------------------------------------------------------------------ console

const SRC = ["AetherionCore", "AetherionQuests", "BossEngine", "AetherMobs", "AetherionItems", "CoreProtect", "LuckPerms", "Multiverse-Core"];

function consoleLine(s: State, id: string): ConsoleLine {
  const r = Math.random;
  const ps = [...s.online.values()].filter((o) => o.server === id);
  const p = ps.length ? pick(r, ps) : null;
  const now = Date.now();
  if (id === "proxy") {
    const q = pick(r, [...s.online.values()]);
    return pick(r, [
      { ts: now, lvl: "INFO", msg: `[connected player] ${q?.name ?? "Spieler"} (/${s.byUuid.get(q?.uuid ?? "")?.lastIp ?? "0.0.0.0"}:5${Math.floor(r() * 9999)}) has connected` },
      { ts: now, lvl: "INFO", msg: `[server connection] ${q?.name ?? "Spieler"} -> ${q?.server ?? "hub"} has connected` },
      { ts: now, lvl: "INFO", src: "AetherionProxy", msg: `Resource-Pack an ${q?.name ?? "Spieler"} geschickt (sha1 9f3c…e21a)` },
      { ts: now, lvl: "INFO", msg: `[connected player] ${q?.name ?? "Spieler"} has disconnected` },
    ]);
  }
  const x = r();
  if (x < 0.25 && p) return { ts: now, lvl: "INFO", msg: `<${p.name}> ${pick(r, CHAT)}` };
  if (x < 0.4 && p) return { ts: now, lvl: "INFO", msg: `${p.name} issued server command: ${pick(r, COMMANDS).replace("{o}", pick(r, NAMES))}` };
  if (x < 0.5 && p) return { ts: now, lvl: "INFO", src: "AetherionQuests", msg: `${p.name} hat „${pick(r, ["Der verlorene Schmied", "Glut im Kern", "Echo der Krypta", "Die Wächter von Eldervale", "Inselträume"])}“ abgeschlossen (+${Math.round(120 + r() * 600)} XP)` };
  if (x < 0.58) return { ts: now, lvl: "INFO", src: "AetherMobs", msg: `Despawn-Sweep: ${Math.round(8 + r() * 40)} Mobs entfernt, ${Math.round(400 + r() * 900)} aktiv` };
  if (x < 0.64 && p) return { ts: now, lvl: "INFO", src: "BossEngine", msg: `Eldervale-Wächter: ${p.name} verursacht ${Math.round(400 + r() * 2400)} Schaden (Phase ${1 + Math.floor(r() * 3)})` };
  if (x < 0.7) return { ts: now, lvl: "INFO", src: "CoreProtect", msg: `Daten gespeichert (${Math.round(200 + r() * 900)} Einträge)` };
  if (x < 0.74) return { ts: now, lvl: "WARN", msg: `Can't keep up! Is the server overloaded? Running ${Math.round(2050 + r() * 900)}ms or ${Math.round(41 + r() * 20)} ticks behind` };
  if (x < 0.77) return { ts: now, lvl: "WARN", src: "AetherionItems", msg: `Unbekannte Item-ID 'aetherion:frostsplitter' in Kiste bei ${Math.round(r() * 900)}, 64, ${Math.round(r() * 900)} übersprungen` };
  if (x < 0.8 && p) return { ts: now, lvl: "INFO", msg: `${p.name} lost connection: Disconnected` };
  if (x < 0.85) return { ts: now, lvl: "INFO", src: "AetherionCore", msg: `Autosave: ${WORLDS[id]?.length ?? 1} Welten in ${Math.round(80 + r() * 300)} ms` };
  if (x < 0.88) return { ts: now, lvl: "ERROR", src: pick(r, SRC), msg: "Could not pass event EntityDamageByEntityEvent to AetherMobs v1.4.2\njava.lang.NullPointerException: Cannot invoke \"org.bukkit.entity.LivingEntity.getHealth()\" because \"target\" is null\n\tat de.aetherion.mobs.combat.DamageListener.onHit(DamageListener.java:88)" };
  return { ts: now, lvl: "INFO", msg: p ? `${p.name} hat ${pick(r, ["[Ätherklinge]", "[Glutkern-Erz]", "[Krypta-Schlüssel]", "[Ätherkristall]"])} erhalten` : "Saving the game (this may take a moment!)" };
}

function consoleBacklog(s: State, id: string) {
  if (!s.console[id]) {
    const lines: ConsoleLine[] = [];
    const now = Date.now();
    const sv = s.srv[id];
    const boot: [string, string?][] =
      id === "proxy"
        ? [["Booting up Velocity 3.4.0-SNAPSHOT..."], ["Loading localizations..."], ["Loaded 4 plugins"], ["Listening on /0.0.0.0:25565"], ["Done (1,92s)!"]]
        : [
            ["Starting minecraft server version 1.21.1"],
            ["Loading properties"],
            ["This server is running Paper version 1.21.1-133-ver/1.21.1@7d16a2c (MC: 1.21.1)"],
            ["Server Ping Player Sample Count: 12"],
            ["Using 4 threads for Netty based IO"],
            [`Preparing level "${WORLDS[id][0].name}"`],
            ["AetherionCore 2.14.0 aktiviert, 38 Module geladen", "AetherionCore"],
            ["Verbunden mit dem Gateway, Server-ID " + id, "AetherionHelm"],
            [`Done (${(18 + Math.random() * 24).toFixed(3)}s)! For help, type "help"`],
          ];
    boot.forEach(([msg, src], i) => lines.push({ ts: sv.uptimeFrom + i * 1400, lvl: "INFO", src, msg }));
    for (let i = 160; i > 0; i--) lines.push({ ...consoleLine(s, id), ts: now - i * 7_000 - Math.random() * 3000 });
    s.console[id] = lines;
  }
  return s.console[id];
}

// ------------------------------------------------------------------ API

let auditId = 1000;
let punishId = 100;

function audit(s: State, action: string, target?: string, detail?: string) {
  const row: AuditRow = { id: ++auditId, ts: Date.now(), user: "Robb", ip: "203.0.113.24", action, target, detail };
  s.audit.unshift(row);
  pending.push({ t: "audit", data: row });
}

const pending: any[] = [];

function q(path: string) {
  const i = path.indexOf("?");
  return { p: i < 0 ? path : path.slice(0, i), q: new URLSearchParams(i < 0 ? "" : path.slice(i + 1)) };
}

const ME = () => ({
  user: { id: 1, username: "robb", display: "Robb", role: "owner", roleLabel: "Owner", mcName: "A3therion", totp: true },
  enroll: false,
  perms: [
    "view", "players.view", "players.inventory", "players.kick", "players.mute", "players.ban", "players.message", "players.send", "notes.write", "chat.send",
    "console.view", "console.command", "server.power", "host.view", "audit.view", "reports.manage", "settings.manage", "team.manage",
  ],
  recoveryLeft: 8,
  version: "1.0.0 · Vorschau",
  servers: SERVERS.map(({ id, name, role, kind }) => ({ id, name, role, kind })),
});

const ROLES = [
  { id: "owner", label: "Owner", perms: ME().perms },
  { id: "coowner", label: "Co-Owner", perms: ME().perms.filter((p) => p !== "team.manage") },
  { id: "admin", label: "Admin", perms: ME().perms.filter((p) => p !== "team.manage" && p !== "settings.manage") },
  { id: "moderator", label: "Moderator", perms: ["view", "players.view", "players.inventory", "players.kick", "players.mute", "players.ban", "players.message", "players.send", "notes.write", "chat.send", "console.view", "reports.manage", "audit.view"] },
  { id: "supporter", label: "Supporter", perms: ["view", "players.view", "players.message", "notes.write", "chat.send", "reports.manage"] },
];

let loggedIn = true;

export async function demoApi(path: string, method: string, body: any): Promise<any> {
  await new Promise((res) => setTimeout(res, 90 + Math.random() * 160));
  const s = st();
  const { p, q: qs } = q(path);
  const seg = p.split("/").filter(Boolean).slice(1); // drop "api"
  const [a, b, c] = seg;
  const now = Date.now();

  // --- auth
  if (a === "auth") {
    if (b === "me") return loggedIn ? ME() : fail(401, "Nicht angemeldet");
    if (b === "logout") return (loggedIn = false), { ok: true };
    if (b === "login") {
      if (!body?.username || !body?.password) fail(400, "Benutzername und Passwort eingeben");
      return { status: "totp", ticket: "demo" };
    }
    if (b === "totp") {
      if (!/^\d{6}$|^[a-z0-9]{4}-?[a-z0-9]{4}$/i.test(body?.code ?? "")) fail(401, "Der Code stimmt nicht");
      loggedIn = true;
      return { status: "ok" };
    }
    if (b === "password") return { ok: true };
    if (b === "sessions") {
      if (c === "revoke-others") return { ok: true };
      if (c) return { ok: true };
      return [
        { id: 31, created: now - 8 * MIN, lastSeen: now, ip: "203.0.113.24", ua: navigator.userAgent, state: "ok", current: true },
        { id: 27, created: now - 2 * DAY, lastSeen: now - 7 * HOUR, ip: "203.0.113.24", ua: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0 Safari/537.36", state: "ok", current: false },
        { id: 22, created: now - 5 * DAY, lastSeen: now - 26 * HOUR, ip: "198.51.100.140", ua: "Mozilla/5.0 (Linux; Android 15; Pixel 8; wv) AetherionHelm/1.0", state: "ok", current: false },
      ];
    }
    if (b === "enroll") return c === "start" ? { secret: "JBSWY3DPEHPK3PXP", uri: "otpauth://totp/Aetherion%20Helm:robb?secret=JBSWY3DPEHPK3PXP&issuer=Aetherion%20Helm" } : { codes: [] };
  }

  // --- network
  if (a === "network" && b === "history") return networkHistory(qs.get("range") ?? "24h");
  if (a === "host") return b === "history" ? hostHistory(qs.get("range") ?? "6h") : hostDetail();

  if (a === "servers" && b) {
    if (!c) return serverDetail(b);
    if (c === "history") return serverHistory(b, qs.get("range") ?? "6h");
    if (c === "entities") return entities(b);
    if (c === "complete") return complete(qs.get("line") ?? "");
    if (c === "command") {
      const cmd = String(body?.command ?? "");
      audit(s, "console.command", serverLabel(b), cmd);
      setTimeout(() => pushConsole(b, commandReply(s, b, cmd)), 300);
      return { ok: true };
    }
    if (c === "save") {
      audit(s, "server.save", serverLabel(b));
      setTimeout(() => pushConsole(b, [{ ts: Date.now(), lvl: "INFO", msg: "Saved the game" }]), 600);
      return { ok: true };
    }
    if (c === "power") return power(s, b, String(body?.action));
  }

  // --- players
  if (a === "search") {
    const term = (qs.get("q") ?? "").toLowerCase();
    return { players: s.players.filter((x) => x.name.toLowerCase().includes(term)).slice(0, 8).map((x) => ({ uuid: x.uuid, name: x.name, online: s.online.has(x.uuid) })) };
  }
  if (a === "players" && !b) {
    const term = (qs.get("q") ?? "").toLowerCase();
    const rows = s.players.map((x) => playerRow(s, x));
    const hit = rows.filter((x) => x.name.toLowerCase().includes(term)).sort((x, y) => (y.lastSeen ?? 0) - (x.lastSeen ?? 0));
    return {
      results: hit.slice(0, Number(qs.get("limit") ?? 80)),
      total: hit.length,
      newest: [...rows].sort((x, y) => (y.firstSeen ?? 0) - (x.firstSeen ?? 0)).slice(0, 8),
      top: [...rows].sort((x, y) => (y.playtime ?? 0) - (x.playtime ?? 0)).slice(0, 8),
    };
  }
  if (a === "players" && b) {
    if (!c) return playerDetail(b);
    if (c === "inventory") {
      audit(s, "player.inventory", nameFor(s, b));
      return inventory(b);
    }
    if (c === "notes") {
      const list = s.notes.get(b) ?? [];
      const n = { id: Date.now(), byUser: "Robb", ts: Date.now(), text: String(body?.text ?? "") };
      s.notes.set(b, [n, ...list]);
      audit(s, "player.note", nameFor(s, b), n.text);
      return n;
    }
    if (c === "message") {
      audit(s, "player.message", nameFor(s, b), body?.text);
      emit(s, { ts: now, kind: "staffmsg", uuid: b, name: nameFor(s, b), text: `Robb → ${nameFor(s, b)}: ${body?.text}` });
      return { ok: true };
    }
    if (c === "send") {
      const o = s.online.get(b);
      if (!o) fail(409, "Spieler ist nicht online");
      const from = o.server;
      o.server = body?.server;
      o.world = WORLDS[o.server!]?.[0].name;
      audit(s, "player.send", o.name, `${serverLabel(from)} → ${serverLabel(o.server)}`);
      emit(s, { ts: now, kind: "switch", server: o.server, uuid: b, name: o.name, text: `${o.name} · ${serverLabel(from)} → ${serverLabel(o.server)}` });
      return { ok: true };
    }
    if (["ban", "mute", "kick", "warn"].includes(c)) {
      if (method === "DELETE") return revoke(s, c, b);
      return punish(s, c as Punishment["type"], b, body?.reason, body?.duration);
    }
  }
  if (a === "punish") {
    const t = s.players.find((x) => x.name.toLowerCase() === String(body?.target ?? "").toLowerCase());
    if (!t) fail(404, `Keinen Spieler „${body?.target}“ gefunden. Er muss mindestens einmal online gewesen sein.`);
    punish(s, body.type, t.uuid, body.reason, body.duration);
    return { name: t.name };
  }
  if (a === "punishments") {
    const active = s.punishments.filter((x) => (x.type === "ban" || x.type === "mute") && !x.revokedAt && (!x.expires || x.expires > now)).sort((x, y) => y.created - x.created);
    return { active, recent: [...s.punishments].sort((x, y) => y.created - x.created).slice(0, 60) };
  }

  // --- activity
  if (a === "activity") {
    const kinds = qs.get("kinds")?.split(",").filter(Boolean);
    const before = Number(qs.get("before") ?? Infinity);
    const server = qs.get("server");
    return s.events.filter((e) => e.id < before && (!kinds || kinds.includes(e.kind)) && (!server || e.server === server)).slice(0, Number(qs.get("limit") ?? 150));
  }
  if (a === "chat" && b === "stats") {
    const out = [];
    const h0 = Math.floor(now / HOUR) * HOUR;
    for (let t = h0 - 23 * HOUR; t <= h0; t += HOUR) out.push({ t, n: Math.round(netAt(t) * 9 * (0.85 + Math.random() * 0.3)) });
    return out;
  }
  if (a === "chat") {
    audit(s, "chat.broadcast", body?.server ? serverLabel(body.server) : "alle", body?.text);
    emit(s, { ts: now, kind: "staffchat", server: body?.server, name: "Robb", text: String(body?.text ?? "") });
    return { ok: true };
  }

  // --- reports
  if (a === "reports") {
    if (b && c === "resolve") {
      const r = s.reports.find((x) => x.id === Number(b));
      if (r) Object.assign(r, { status: "resolved", resolvedBy: "Robb", resolvedAt: now, resolution: body?.resolution });
      audit(s, "report.resolved", `#${b}`, body?.resolution);
      return { ok: true };
    }
    const status = qs.get("status");
    return s.reports.filter((r) => !status || r.status === status);
  }

  // --- alerts
  if (a === "alerts") {
    if (c === "ack") {
      const al = s.alerts.find((x) => x.key === decodeURIComponent(b));
      if (al) al.ackedBy = "Robb";
      audit(s, "alert.ack", al?.title);
      return { ok: true };
    }
    return { history: s.alertHistory };
  }

  // --- audit
  if (a === "audit") {
    const before = Number(qs.get("before") ?? Infinity);
    const action = qs.get("action");
    const user = qs.get("user");
    return s.audit.filter((r) => r.id < before && (!action || r.action.startsWith(action)) && (!user || r.user === user)).slice(0, Number(qs.get("limit") ?? 150));
  }

  // --- team
  if (a === "team") {
    if (!b && method === "GET") return { users: s.team, roles: ROLES };
    if (!b && method === "POST") {
      const role = ROLES.find((r) => r.id === body?.role) ?? ROLES[4];
      const u = { id: s.team.length + 1, username: body.username, display: body.display || body.username, role: role.id, roleLabel: role.label, mcName: body.mcName, totp: false, created: now, online: false };
      s.team.push(u);
      audit(s, "team.created", u.display, `Rolle ${role.label}`);
      return { id: u.id, password: demoPassword() };
    }
    const u = s.team.find((x) => x.id === Number(b));
    if (!u) fail(404, "Konto nicht gefunden");
    if (c === "reset-password") return audit(s, "team.reset-password", u.display), { password: demoPassword() };
    if (c === "reset-2fa") return (u.totp = false), audit(s, "team.reset-2fa", u.display), { ok: true };
    if (method === "DELETE") {
      if (u.role === "owner") fail(400, "Das Owner-Konto kann nicht entfernt werden");
      s.team = s.team.filter((x) => x !== u);
      audit(s, "team.removed", u.display);
      return { ok: true };
    }
    if (method === "PATCH") {
      const role = ROLES.find((r) => r.id === body?.role) ?? ROLES.find((r) => r.id === u.role)!;
      Object.assign(u, { display: body.display || u.display, role: role.id, roleLabel: role.label, mcName: body.mcName });
      audit(s, "team.updated", u.display, `Rolle ${role.label}`);
      return { ok: true };
    }
  }

  // --- settings
  if (a === "settings") {
    if (b === "discord-test") return { ok: true };
    if (method === "PUT") {
      if (body?.discordWebhook !== undefined) {
        s.settings.discordSet = !!body.discordWebhook;
        s.settings.discordWebhook = body.discordWebhook ? "https://discord.com/api/webhooks/…/••••••••" : "";
      }
      if (body?.alerts) s.settings.alerts = body.alerts;
      audit(s, "settings.changed", undefined, body?.alerts ? "Schwellen geändert" : "Discord-Webhook geändert");
      return { ok: true };
    }
    return s.settings;
  }

  fail(404, `Gibt es in der Vorschau nicht: ${p}`);
}

function demoPassword() {
  const A = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from({ length: 16 }, (_, i) => (i % 5 === 4 ? "-" : A[Math.floor(Math.random() * A.length)])).join("");
}

function punish(s: State, type: Punishment["type"], uuid: string, reason?: string, duration?: number) {
  const name = nameFor(s, uuid);
  const now = Date.now();
  const row: Punishment = { id: ++punishId, uuid, name, type, reason, byUser: "Robb", created: now, expires: duration ? now + duration : null, revokedBy: null, revokedAt: null };
  s.punishments.push(row);
  const what = { ban: "gebannt", mute: "gemutet", kick: "gekickt", warn: "verwarnt" }[type];
  audit(s, `player.${type}`, name, [duration ? fmtDur(duration) : type === "ban" || type === "mute" ? "dauerhaft" : "", reason].filter(Boolean).join(" · "));
  emit(s, { ts: now, kind: "mod", uuid, name, text: `${what} von Robb${reason ? ": " + reason : ""}` });
  if (type === "ban" || type === "kick") {
    const o = s.online.get(uuid);
    if (o) {
      s.online.delete(uuid);
      emit(s, { ts: now, kind: "quit", server: o.server, uuid, name, text: `${name} hat das Netzwerk verlassen` });
    }
  }
  return { id: row.id };
}

function revoke(s: State, type: string, uuid: string) {
  const now = Date.now();
  for (const p of s.punishments) if (p.uuid === uuid && p.type === type && !p.revokedAt) Object.assign(p, { revokedBy: "Robb", revokedAt: now });
  const name = nameFor(s, uuid);
  audit(s, type === "ban" ? "player.unban" : "player.unmute", name);
  emit(s, { ts: now, kind: "mod", uuid, name, text: `${type === "ban" ? "entbannt" : "entmutet"} von Robb` });
  return { ok: true };
}

function fmtDur(ms: number) {
  if (ms >= DAY) return `${Math.round(ms / DAY)} T.`;
  if (ms >= HOUR) return `${Math.round(ms / HOUR)} Std.`;
  return `${Math.round(ms / MIN)} Min.`;
}

function power(s: State, id: string, action: string) {
  const sv = s.srv[id];
  const label = { start: "gestartet", stop: "gestoppt", restart: "neu gestartet", kill: "hart beendet" }[action] ?? action;
  audit(s, `server.${action}`, serverLabel(id));
  emit(s, { ts: Date.now(), kind: "server", server: id, name: "Robb", text: `${serverLabel(id)} ${label}` });
  const op = action === "start" ? "starting" : action === "restart" ? "restarting" : "stopping";
  sv.power = { op: action, since: Date.now(), by: "Robb" };
  sv.state = op;
  const kicked = () => {
    for (const o of [...s.online.values()]) if (o.server === id) o.server = "hub";
  };
  if (action === "stop" || action === "kill") {
    setTimeout(() => {
      kicked();
      sv.state = "stopped";
      sv.power = undefined;
      emit(s, { ts: Date.now(), kind: "server", server: id, text: `${serverLabel(id)} ist gestoppt` });
    }, action === "kill" ? 900 : 6000);
  } else {
    if (action === "restart") setTimeout(kicked, 3000);
    setTimeout(() => {
      sv.state = "online";
      sv.power = undefined;
      sv.uptimeFrom = Date.now();
      sv.heap = BASE[id].heap * 0.6;
      s.console[id] = [];
      emit(s, { ts: Date.now(), kind: "server", server: id, text: `${serverLabel(id)} ist wieder online` });
    }, action === "start" ? 9000 : 14000);
  }
  return { ok: true };
}

const CMDS = ["say", "tp", "give", "gamemode", "kick", "list", "tps", "mspt", "spark", "co", "lp", "mv", "weather", "time", "whitelist", "save-all", "plugins", "aetherion", "quests", "boss", "effect", "clear", "invsee", "msg", "broadcast"];

function complete(line: string): string[] {
  const parts = line.split(" ");
  if (parts.length <= 1) return CMDS.filter((c) => c.startsWith(parts[0].replace(/^\//, ""))).slice(0, 12);
  const last = parts[parts.length - 1].toLowerCase();
  if (["tp", "kick", "give", "gamemode", "msg", "invsee", "effect", "clear"].includes(parts[0])) {
    if (parts[0] === "gamemode" && parts.length === 2) return ["survival", "creative", "adventure", "spectator"].filter((x) => x.startsWith(last));
    return [...st().online.values()].map((o) => o.name).filter((n) => n.toLowerCase().startsWith(last)).slice(0, 12);
  }
  if (parts[0] === "time") return ["set", "add", "query"].filter((x) => x.startsWith(last));
  if (parts[0] === "weather") return ["clear", "rain", "thunder"].filter((x) => x.startsWith(last));
  if (parts[0] === "spark") return ["profiler", "tps", "health", "gc", "heapsummary"].filter((x) => x.startsWith(last));
  return [];
}

function commandReply(s: State, id: string, cmd: string): ConsoleLine[] {
  const now = Date.now();
  const c = cmd.replace(/^\//, "").trim();
  const name = c.split(" ")[0];
  const L = (msg: string, lvl = "INFO"): ConsoleLine => ({ ts: now, lvl, msg });
  if (name === "list") {
    const ps = [...s.online.values()].filter((o) => id === "proxy" || o.server === id).map((o) => o.name);
    return [L(`There are ${ps.length} of a max of 150 players online: ${ps.join(", ")}`)];
  }
  if (name === "tps") {
    const v = serverNow(s, id);
    return [L(`TPS from last 1m, 5m, 15m: ${(v.tps ?? [20, 20, 20]).map((x) => (x >= 18 ? "§a" : "§e") + x.toFixed(2)).join(", ")}`)];
  }
  if (name === "mspt") {
    const v = serverNow(s, id);
    return [L("Server tick times (avg/min/max) from last 5s, 10s, 1m:"), L(`◴ ${v.mspt}/${(v.mspt! * 0.6).toFixed(1)}/${v.msptMax}, ${v.mspt}/${(v.mspt! * 0.55).toFixed(1)}/${v.msptMax}`)];
  }
  if (name === "say" || name === "broadcast") return [L(`[Server] ${c.slice(name.length + 1)}`)];
  if (name === "plugins" || name === "pl") return [L(`Server Plugins (${PLUGINS.length}):`), L(PLUGINS.map(([n]) => n).join(", "))];
  if (name === "save-all") return [L("Saving the game (this may take a moment!)"), L("Saved the game")];
  if (name === "spark") return [L("[⚡] Der Profiler läuft jetzt. Ergebnis in 30 s unter spark.lucko.me/…")];
  if (name === "help") return [L("--------- Help: Index (1/42) ---------"), L("Use /help [n] to get page n of help.")];
  if (CMDS.includes(name)) return [L(`Befehl ausgeführt: /${c}`)];
  return [L(`Unknown command. Type "/help" for help.`, "WARN")];
}

// ------------------------------------------------------------------ live stream

let handler: ((m: any) => void) | null = null;
let consoleOf: () => string | null = () => null;
let lastConsole: string | null = null;

function emit(s: State, e: Omit<HelmEvent, "id">) {
  const ev = addEvent(s, e);
  handler?.({ t: "event", data: ev });
}

function pushConsole(id: string, lines: ConsoleLine[]) {
  const s = st();
  const log = consoleBacklog(s, id);
  log.push(...lines);
  if (log.length > 1500) log.splice(0, log.length - 1500);
  if (consoleOf() === id) handler?.({ t: "console", server: id, lines });
}

function tickPlayers(s: State) {
  const r = Math.random;
  const x = r();
  const on = [...s.online.values()];
  const want = Math.round(clamp(netAt(Date.now()), 12, 56));
  const now = Date.now();
  if (x < 0.22 && on.length) {
    const o = pick(r, on);
    const p = s.byUuid.get(o.uuid)!;
    emit(s, { ts: now, kind: "chat", server: o.server, uuid: o.uuid, name: o.name, text: pick(r, CHAT) });
    if (o.afk) o.afk = 0;
    p.lastSeen = now;
  } else if (x < 0.28 && on.length) {
    const o = pick(r, on);
    emit(s, { ts: now, kind: "command", server: o.server, uuid: o.uuid, name: o.name, text: pick(r, COMMANDS).replace("{o}", pick(r, on).name) });
  } else if (x < 0.32 && on.length > want - 4) {
    const o = pick(r, on.filter((v) => v.name !== "Shuminite"));
    if (!o) return;
    s.online.delete(o.uuid);
    const p = s.byUuid.get(o.uuid)!;
    p.lastSeen = now;
    p.playtime += now - o.joined;
    emit(s, { ts: now, kind: "quit", server: o.server, uuid: o.uuid, name: o.name, text: `${o.name} hat das Netzwerk verlassen` });
  } else if (x < 0.37 && on.length < want + 4) {
    const off = s.players.filter((p) => !s.online.has(p.uuid) && p.name !== "A3therion" && !s.punishments.some((b) => b.uuid === p.uuid && b.type === "ban" && !b.revokedAt && (!b.expires || b.expires > now)));
    if (!off.length) return;
    const p = pick(r, off);
    const sv = "hub";
    s.online.set(p.uuid, { uuid: p.uuid, name: p.name, server: sv, ping: Math.round(14 + r() ** 2 * 90), client: p.client, world: "hub", gamemode: "survival", health: 20, level: p.level, afk: 0, joined: now, x: 0, y: 64, z: 0 });
    p.lastSeen = now;
    p.sessions++;
    s.unique++;
    emit(s, { ts: now, kind: "join", server: "proxy", uuid: p.uuid, name: p.name, text: `${p.name} hat das Netzwerk betreten` });
  } else if (x < 0.45 && on.length) {
    const o = pick(r, on);
    const from = o.server;
    const to = weighted(r);
    if (to === from || s.srv[to].state !== "online") return;
    o.server = to;
    o.world = pick(r, WORLDS[to]).name;
    emit(s, { ts: now, kind: "switch", server: to, uuid: o.uuid, name: o.name, text: `${o.name} · ${serverLabel(from)} → ${serverLabel(to)}` });
  } else if (x < 0.48 && on.length) {
    const o = pick(r, on);
    emit(s, { ts: now, kind: "death", server: o.server, uuid: o.uuid, name: o.name, text: pick(r, DEATHS).replace("{n}", o.name).replace("{o}", pick(r, on).name) });
  }
  for (const o of on) {
    o.ping = clamp(Math.round(jitter(o.ping ?? 40, 0.08)), 8, 240);
    if (o.afk) o.afk += 2;
  }
  s.peak = Math.max(s.peak, s.online.size);
}

export function demoLive(handle: (m: any) => void, getConsole: () => string | null) {
  handler = handle;
  consoleOf = getConsole;
  const s = st();
  const snap = () => handle({ t: "snap", data: snapshot(s) });
  // prefill the 5-minute live charts so they are not empty on first paint
  const back = 60;
  for (let i = back; i > 0; i--) {
    const d = snapshot(s);
    d.ts = Date.now() - i * 2000;
    handle({ t: "snap", data: d });
  }
  snap();
  setInterval(() => {
    tickPlayers(s);
    snap();
    while (pending.length) handle(pending.shift());
  }, 2000);
  setInterval(() => {
    const id = getConsole();
    if (!id) {
      lastConsole = null;
      return;
    }
    if (id !== lastConsole) {
      lastConsole = id;
      handle({ t: "console", server: id, reset: true, lines: consoleBacklog(s, id).slice(-400) });
      return;
    }
    if (s.srv[id]?.state !== "online") return;
    if (Math.random() < 0.55) pushConsole(id, [consoleLine(s, id)]);
  }, 700);
}
