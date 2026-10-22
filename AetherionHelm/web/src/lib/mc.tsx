import type { CSSProperties, ReactNode } from "react";

// Minecraft text: legacy § codes (console, kick screens) and Adventure/vanilla JSON components (item names, lore).

export const MC_COLORS: Record<string, string> = {
  black: "#000000",
  dark_blue: "#0000AA",
  dark_green: "#00AA00",
  dark_aqua: "#00AAAA",
  dark_red: "#AA0000",
  dark_purple: "#AA00AA",
  gold: "#FFAA00",
  gray: "#AAAAAA",
  dark_gray: "#555555",
  blue: "#5555FF",
  green: "#55FF55",
  aqua: "#55FFFF",
  red: "#FF5555",
  light_purple: "#FF55FF",
  yellow: "#FFFF55",
  white: "#FFFFFF",
};
const CODE: Record<string, string> = {
  "0": "black", "1": "dark_blue", "2": "dark_green", "3": "dark_aqua", "4": "dark_red", "5": "dark_purple", "6": "gold", "7": "gray",
  "8": "dark_gray", "9": "blue", a: "green", b: "aqua", c: "red", d: "light_purple", e: "yellow", f: "white",
};

interface Style {
  color?: string;
  b?: boolean;
  i?: boolean;
  u?: boolean;
  s?: boolean;
  o?: boolean;
}

function css(st: Style, onDark: boolean): CSSProperties {
  const c = st.color ? MC_COLORS[st.color] ?? st.color : undefined;
  return {
    color: c && !onDark && (st.color === "white" || st.color === "gray") ? undefined : c,
    fontWeight: st.b ? 700 : undefined,
    fontStyle: st.i ? "italic" : undefined,
    textDecoration: [st.u && "underline", st.s && "line-through"].filter(Boolean).join(" ") || undefined,
    filter: st.o ? "blur(2px)" : undefined,
  };
}

/** "§aHallo §lWelt" → coloured spans. Also understands &x§r§r… hex sequences. */
export function legacy(text: string, onDark = true): ReactNode[] {
  const out: ReactNode[] = [];
  let st: Style = {};
  let buf = "";
  let k = 0;
  const flush = () => {
    if (buf) out.push(<span key={k++} style={css(st, onDark)}>{buf}</span>);
    buf = "";
  };
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if ((ch === "§" || ch === "§") && i + 1 < text.length) {
      const c = text[i + 1].toLowerCase();
      if (c === "x" && i + 13 < text.length) {
        let hex = "#";
        for (let j = 0; j < 6; j++) hex += text[i + 3 + j * 2];
        flush();
        st = { color: hex };
        i += 13;
        continue;
      }
      flush();
      if (CODE[c]) st = { color: CODE[c] };
      else if (c === "l") st = { ...st, b: true };
      else if (c === "o") st = { ...st, i: true };
      else if (c === "n") st = { ...st, u: true };
      else if (c === "m") st = { ...st, s: true };
      else if (c === "k") st = { ...st, o: true };
      else if (c === "r") st = {};
      i++;
      continue;
    }
    buf += ch;
  }
  flush();
  return out;
}

export function stripLegacy(text: string) {
  return text.replace(/§[0-9a-fk-orx]/gi, "");
}

/** Renders an Adventure/vanilla JSON text component. `base` carries the inherited style. */
export function component(c: unknown, base: Style = {}, key = "c"): ReactNode {
  if (c == null) return null;
  if (typeof c === "string") return <span key={key} style={css(base, true)}>{c}</span>;
  if (Array.isArray(c)) return c.map((x, i) => component(x, base, key + "." + i));
  const o = c as Record<string, any>;
  const st: Style = {
    color: o.color ?? base.color,
    b: o.bold ?? base.b,
    i: o.italic ?? base.i,
    u: o.underlined ?? base.u,
    s: o.strikethrough ?? base.s,
    o: o.obfuscated ?? base.o,
  };
  let text: ReactNode = null;
  if (typeof o.text === "string") text = o.text.includes("§") ? legacy(o.text) : o.text;
  else if (typeof o.translate === "string") text = o.fallback ?? prettyKey(o.translate);
  else if (o.keybind) text = o.keybind;
  else if (o.selector) text = o.selector;
  return (
    <span key={key} style={css(st, true)}>
      {text}
      {Array.isArray(o.extra) ? o.extra.map((x: unknown, i: number) => component(x, st, key + "." + i)) : null}
    </span>
  );
}

export function plain(c: unknown): string {
  if (c == null) return "";
  if (typeof c === "string") return stripLegacy(c);
  if (Array.isArray(c)) return c.map(plain).join("");
  const o = c as Record<string, any>;
  let s = typeof o.text === "string" ? stripLegacy(o.text) : typeof o.translate === "string" ? o.fallback ?? prettyKey(o.translate) : "";
  if (Array.isArray(o.extra)) s += o.extra.map(plain).join("");
  return s;
}

function prettyKey(k: string) {
  const last = k.split(".").pop() ?? k;
  return prettyId(last);
}

/** "netherite_sword" → "Netherite Sword" */
export function prettyId(id: string) {
  return id
    .replace(/^minecraft:/, "")
    .split("_")
    .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
    .join(" ");
}

const ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"];
export const roman = (n: number) => ROMAN[n] ?? String(n);

export const RARITY_COLOR: Record<string, string> = {
  common: "white",
  uncommon: "yellow",
  rare: "aqua",
  epic: "light_purple",
};

const EFFECT_DE: Record<string, string> = {
  speed: "Schnelligkeit", slowness: "Langsamkeit", haste: "Eile", mining_fatigue: "Abbaulähmung", strength: "Stärke",
  instant_health: "Direktheilung", instant_damage: "Direktschaden", jump_boost: "Sprungkraft", nausea: "Übelkeit",
  regeneration: "Regeneration", resistance: "Resistenz", fire_resistance: "Feuerresistenz", water_breathing: "Unterwasseratmung",
  invisibility: "Unsichtbarkeit", blindness: "Blindheit", night_vision: "Nachtsicht", hunger: "Hunger", weakness: "Schwäche",
  poison: "Vergiftung", wither: "Ausdörrung", health_boost: "Extraenergie", absorption: "Absorption", saturation: "Sättigung",
  glowing: "Leuchten", levitation: "Schwebekraft", luck: "Glück", unluck: "Pech", slow_falling: "Sanfter Fall",
  conduit_power: "Meereskraft", dolphins_grace: "Gunst des Delfins", bad_omen: "Böses Omen", hero_of_the_village: "Held des Dorfes",
  darkness: "Dunkelheit",
};
export const effectName = (id: string) => EFFECT_DE[id] ?? prettyId(id);

// ------------------------------------------------------------------ item icons

let icons: Record<string, string> | null = null;
let loading: Promise<Record<string, string>> | null = null;

/** 16×16 vanilla icons (base64 PNG) keyed by item id, loaded once on first use. */
export function loadIcons(): Promise<Record<string, string>> {
  if (icons) return Promise.resolve(icons);
  if (!loading) {
    loading = fetch((__DEMO__ ? "./" : "/") + "items.json")
      .then((r) => (r.ok ? r.json() : {}))
      .catch(() => ({}))
      .then((d) => (icons = d as Record<string, string>));
  }
  return loading;
}

const ALIAS: Record<string, string> = {
  player_head: "player_head",
  enchanted_golden_apple: "golden_apple",
  debug_stick: "stick",
  written_book: "written_book",
};

export function iconFor(id: string, set: Record<string, string> | null): string | null {
  if (!set) return null;
  const k = set[id] ?? set[ALIAS[id] ?? ""];
  return k ? "data:image/png;base64," + k : null;
}
