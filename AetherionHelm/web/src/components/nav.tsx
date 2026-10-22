import { BellRing, Cpu, Flag, Gavel, LayoutGrid, MessagesSquare, ScrollText, Server, Settings, ShieldCheck, Users, type LucideIcon } from "lucide-react";
import { createStore } from "../lib/store";

export interface NavItem {
  id: string;
  label: string;
  icon: LucideIcon;
  perm?: string;
  hint?: string;
}

export const NAV: { group: string; items: NavItem[] }[] = [
  {
    group: "Live",
    items: [
      { id: "overview", label: "Übersicht", icon: LayoutGrid, hint: "Netzwerk auf einen Blick" },
      { id: "servers", label: "Server", icon: Server, hint: "TPS, Konsole, Steuerung" },
      { id: "host", label: "Hetzner", icon: Cpu, perm: "host.view", hint: "CPU, RAM, Platten, Netz" },
    ],
  },
  {
    group: "Community",
    items: [
      { id: "players", label: "Spieler", icon: Users, perm: "players.view", hint: "Suchen, Profile, Inventare" },
      { id: "activity", label: "Chat & Aktivität", icon: MessagesSquare, hint: "Live-Chat, Befehle, Joins" },
      { id: "reports", label: "Meldungen", icon: Flag, hint: "/report aus dem Spiel" },
      { id: "punishments", label: "Strafen", icon: Gavel, perm: "players.view", hint: "Bans und Mutes" },
    ],
  },
  {
    group: "Team",
    items: [
      { id: "alerts", label: "Warnungen", icon: BellRing, hint: "Was gerade nicht stimmt" },
      { id: "audit", label: "Protokoll", icon: ScrollText, perm: "audit.view", hint: "Wer hat was gemacht" },
      { id: "team", label: "Team", icon: ShieldCheck, hint: "Konten und Rollen" },
      { id: "settings", label: "Einstellungen", icon: Settings, hint: "Konto, Sicherheit, Alerts" },
    ],
  },
];

export const ALL_NAV = NAV.flatMap((g) => g.items);
export const navLabel = (id: string) => ALL_NAV.find((n) => n.id === id)?.label ?? id;

export type GlobalSheet = null | { kind: "broadcast"; server?: string } | { kind: "punish"; type?: "ban" | "mute"; name?: string } | { kind: "more" };
export const ui = createStore<{ palette: boolean; sheet: GlobalSheet }>({ palette: false, sheet: null });
export const openSheet = (sheet: GlobalSheet) => ui.set((s) => ({ ...s, sheet }));
export const openPalette = (palette = true) => ui.set((s) => ({ ...s, palette }));
