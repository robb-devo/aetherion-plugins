import { BellRing, Cpu, Flag, Gavel, LayoutGrid, MessagesSquare, ScrollText, Server, Settings, ShieldCheck, Users, type LucideIcon } from "lucide-react";
import { t } from "../lib/i18n";
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
      { id: "overview", label: t("Overview"), icon: LayoutGrid, hint: t("The network at a glance") },
      { id: "servers", label: t("Servers"), icon: Server, hint: t("TPS, console, controls") },
      { id: "host", label: "Hetzner", icon: Cpu, perm: "host.view", hint: t("CPU, RAM, disks, network") },
    ],
  },
  {
    group: "Community",
    items: [
      { id: "players", label: t("Players"), icon: Users, perm: "players.view", hint: t("Search, profiles, inventories") },
      { id: "activity", label: t("Chat & activity"), icon: MessagesSquare, hint: t("Live chat, commands, joins") },
      { id: "reports", label: t("Reports"), icon: Flag, hint: t("In-game /report") },
      { id: "punishments", label: t("Punishments"), icon: Gavel, perm: "players.view", hint: t("Bans and mutes") },
    ],
  },
  {
    group: "Team",
    items: [
      { id: "alerts", label: t("Alerts"), icon: BellRing, hint: t("What needs attention") },
      { id: "audit", label: t("Audit log"), icon: ScrollText, perm: "audit.view", hint: t("Who did what") },
      { id: "team", label: "Team", icon: ShieldCheck, hint: t("Accounts and roles") },
      { id: "settings", label: t("Settings"), icon: Settings, hint: t("Account, security, alerts") },
    ],
  },
];

export const ALL_NAV = NAV.flatMap((g) => g.items);
export const navLabel = (id: string) => ALL_NAV.find((n) => n.id === id)?.label ?? id;

export type GlobalSheet = null | { kind: "broadcast"; server?: string } | { kind: "punish"; type?: "ban" | "mute"; name?: string } | { kind: "more" };
export const ui = createStore<{ palette: boolean; sheet: GlobalSheet }>({ palette: false, sheet: null });
export const openSheet = (sheet: GlobalSheet) => ui.set((s) => ({ ...s, sheet }));
export const openPalette = (palette = true) => ui.set((s) => ({ ...s, palette }));
