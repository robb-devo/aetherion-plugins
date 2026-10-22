import { createStore, useStore } from "./store";
import { api, onUnauthorized } from "./api";
import type { Me } from "./types";
import { startLive, stopLive } from "./live";

export type Phase = "loading" | "anon" | "enroll" | "ready";
export const session = createStore<{ phase: Phase; me: Me | null }>({ phase: "loading", me: null });

export async function refreshMe() {
  try {
    const me = await api<Me>("/api/auth/me");
    session.set({ phase: me.enroll ? "enroll" : "ready", me });
    if (!me.enroll) startLive();
  } catch {
    session.set({ phase: "anon", me: null });
  }
}

export async function logout() {
  try {
    await api("/api/auth/logout", { method: "POST", body: {} });
  } catch {
    // the cookie is cleared either way
  }
  stopLive();
  session.set({ phase: "anon", me: null });
  location.hash = "#/";
}

onUnauthorized(() => {
  if (session.get().phase === "ready") {
    stopLive();
    session.set({ phase: "anon", me: null });
  }
});

export function useMe() {
  return useStore(session, (s) => s.me);
}

export function can(me: Me | null, perm: string) {
  return !!me && me.perms.includes(perm);
}

export function useCan(perm: string) {
  return useStore(session, (s) => can(s.me, perm));
}

export function serverName(me: Me | null, id?: string | null) {
  if (!id) return "–";
  return me?.servers.find((s) => s.id === id)?.name ?? id;
}
