import { createStore } from "./store";

export type ToastKind = "ok" | "err" | "info";
export interface Toast {
  id: number;
  text: string;
  kind: ToastKind;
  out?: boolean;
}

export const toasts = createStore<Toast[]>([]);
let seq = 0;

export function toast(text: string, kind: ToastKind = "ok", ms = kind === "err" ? 5200 : 3200) {
  const id = ++seq;
  toasts.set((t) => [...t.slice(-3), { id, text, kind }]);
  setTimeout(() => toasts.set((t) => t.map((x) => (x.id === id ? { ...x, out: true } : x))), ms);
  setTimeout(() => toasts.set((t) => t.filter((x) => x.id !== id)), ms + 260);
}

/** Runs an action, toasts the outcome; returns whether it worked. */
export async function attempt(fn: () => Promise<unknown>, ok?: string): Promise<boolean> {
  try {
    await fn();
    if (ok) toast(ok, "ok");
    return true;
  } catch (e) {
    toast((e as Error).message || "Das hat nicht geklappt.", "err");
    return false;
  }
}
