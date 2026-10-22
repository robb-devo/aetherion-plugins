export type ServerState = "online" | "starting" | "stopping" | "restarting" | "degraded" | "stopped" | "offline" | "unknown";

export interface World {
  name: string;
  env: string;
  chunks: number;
  entities: number;
  tiles: number;
  players: number;
  time?: number;
  storm?: boolean;
}

export interface BackendPing {
  name: string;
  id: string;
  connected: number;
  reachable?: boolean;
  latency?: number;
  version?: string;
  online?: number;
  max?: number;
  checked?: number;
}

export interface Server {
  id: string;
  name: string;
  role: string;
  kind: "paper" | "velocity";
  state: ServerState;
  agent: boolean;
  agentVersion?: string;
  pid?: number;
  xmx?: string;
  port?: number;
  power?: { op: string; since: number; by: string };
  players: number;
  maxPlayers?: number;
  tps?: number[];
  mspt?: number;
  msptP95?: number;
  msptMax?: number;
  ticks?: number[];
  heapUsed?: number;
  heapCommitted?: number;
  heapMax?: number;
  cpu?: number;
  threads?: number;
  uptime?: number;
  gcCount?: number;
  gcTime?: number;
  chunks?: number;
  entities?: number;
  tiles?: number;
  worlds?: World[];
  backends?: BackendPing[];
  rss?: number;
  hostCpu?: number;
}

export interface ServerDetail extends Server {
  info?: {
    software?: string;
    mc?: string;
    java?: string;
    jvm?: string;
    cpus?: number;
    jvmArgs?: string[];
    viewDistance?: number;
    simulationDistance?: number;
    onlineMode?: boolean;
    whitelist?: boolean;
    motd?: string;
    startedAt?: number;
    plugins?: { name: string; version: string; enabled: boolean }[];
    backends?: { name: string; id: string }[];
    tryOrder?: string[];
    compressionThreshold?: number;
  };
  disk?: { bytes?: number; worlds?: Record<string, number> };
  watchdog?: string[];
}

export interface OnlinePlayer {
  uuid: string;
  name: string;
  server?: string;
  ping?: number;
  client?: string;
  world?: string;
  gamemode?: string;
  health?: number;
  level?: number;
  afk?: number;
}

export interface HostSummary {
  cpu?: number;
  iowait?: number;
  steal?: number;
  memTotal?: number;
  memUsed?: number;
  memCached?: number;
  swapTotal?: number;
  swapUsed?: number;
  load?: number[];
  rx?: number;
  tx?: number;
  rxTotal?: number;
  txTotal?: number;
  diskRead?: number;
  diskWrite?: number;
  diskBusy?: number;
  diskTotal?: number;
  diskUsed?: number;
  uptime?: number;
  cores?: number;
  procs?: number;
  error?: string;
}

export interface Alert {
  key: string;
  level: "crit" | "warn" | "info" | string;
  title: string;
  detail?: string;
  server?: string;
  since: number;
  ackedBy?: string;
}

export interface Snapshot {
  ts: number;
  servers: Server[];
  network: { online: number; peakToday: number; uniqueToday: number; maxPlayers?: number };
  players: OnlinePlayer[];
  host: HostSummary;
  alerts: Alert[];
  openReports: number;
}

export interface HelmEvent {
  id: number;
  ts: number;
  kind: string;
  server?: string;
  uuid?: string;
  name?: string;
  text?: string;
}

export interface ConsoleLine {
  ts: number;
  lvl: string;
  src?: string;
  msg: string;
}

export interface Me {
  user: {
    id: number;
    username: string;
    display: string;
    role: string;
    roleLabel: string;
    mcName?: string;
    totp: boolean;
  };
  enroll: boolean;
  perms: string[];
  recoveryLeft: number;
  version: string;
  servers: { id: string; name: string; role: string; kind: string }[];
}

export interface PlayerRow {
  uuid: string;
  name: string;
  firstSeen?: number;
  lastSeen?: number;
  lastServer?: string;
  playtime?: number;
  sessions?: number;
  online?: boolean;
  server?: string;
  lastIp?: string;
  client?: string;
}

export interface Punishment {
  id: number;
  uuid?: string;
  name?: string;
  type: "ban" | "mute" | "kick" | "warn";
  reason?: string;
  byUser: string;
  created: number;
  expires?: number | null;
  revokedBy?: string | null;
  revokedAt?: number | null;
}

export interface PlayerDetail {
  uuid: string;
  name: string;
  record?: PlayerRow;
  live?: OnlinePlayer & {
    x?: number;
    y?: number;
    z?: number;
    food?: number;
    maxHealth?: number;
    op?: boolean;
    flying?: boolean;
    brand?: string;
    locale?: string;
    ip?: string;
    protocol?: number;
    host?: string;
  };
  punishments: Punishment[];
  notes: { id: number; byUser: string; ts: number; text: string }[];
  ips: { ip: string; first: number; last: number; accounts: number }[];
  alts: { uuid: string; name: string; lastSeen: number }[];
  sessions: { start: number; end: number | null }[];
  activity: number[][];
  events: HelmEvent[];
}

export interface Item {
  slot?: number;
  id: string;
  count: number;
  name?: unknown;
  lore?: unknown[];
  ench?: { id: string; lvl: number }[];
  glint?: boolean;
  cmd?: number;
  unbreakable?: boolean;
  rarity?: string;
  dmg?: number;
  maxDmg?: number;
  contents?: Item[];
}

export interface Inventory {
  uuid: string;
  name: string;
  inventory: Item[];
  armor: (Item | null)[];
  offhand?: Item;
  enderchest: Item[];
  selected: number;
  health: number;
  maxHealth: number;
  food: number;
  saturation: number;
  level: number;
  exp: number;
  gamemode: string;
  world: string;
  x: number;
  y: number;
  z: number;
  effects: { type: string; amp: number; dur: number }[];
}

export interface Report {
  id: number;
  ts: number;
  server?: string;
  reporterUuid?: string;
  reporterName?: string;
  targetUuid?: string;
  targetName?: string;
  reason: string;
  status: "open" | "resolved";
  resolvedBy?: string;
  resolvedAt?: number;
  resolution?: string;
}

export interface AuditRow {
  id: number;
  ts: number;
  user: string;
  ip?: string;
  action: string;
  target?: string;
  detail?: string;
}

export interface TeamUser {
  id: number;
  username: string;
  display: string;
  role: string;
  roleLabel: string;
  mcName?: string;
  totp: boolean;
  created: number;
  lastLogin?: number;
  online: boolean;
}

export interface HostDetail extends HostSummary {
  perCore?: number[];
  interfaces?: { name: string; rx?: number; tx?: number; rxTotal: number; txTotal: number }[];
  disks?: { name: string; read?: number; write?: number; busy?: number }[];
  filesystems?: { mount: string; device: string; type: string; total: number; used: number; free: number }[];
  temps?: { chip: string; label: string; c: number; crit?: number }[];
  static?: {
    cpuModel?: string;
    threads?: number;
    cores?: number;
    mhz?: number;
    hostname?: string;
    kernel?: string;
    os?: string;
    vendor?: string;
    product?: string;
    board?: string;
    java?: string;
    disks?: { name: string; model?: string; size: number; ssd: boolean }[];
    nics?: { name: string; speed?: number; state: string }[];
    raid?: { name: string; state: string; level: string; devices: string[]; map?: string; degraded?: boolean; sync?: string }[];
  };
  ctl?: {
    sizes?: { servers?: Record<string, { bytes?: number; worlds?: Record<string, number> }>; backups?: { bytes?: number; count?: number; latest?: number; latestName?: string } };
    watchdog?: string[];
  };
  processes?: { pid: number; name: string; user?: string; cpu: number; rss: number; threads: number; started?: number; server?: string }[];
  gatewayUptime?: number;
}

export type Point = Record<string, number | null> & { t: number };
