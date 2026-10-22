import { useSyncExternalStore } from "react";

export interface Route {
  page: string;
  parts: string[];
  query: URLSearchParams;
}

function parse(): Route {
  const h = location.hash.replace(/^#\/?/, "");
  const [path, qs] = h.split("?");
  const parts = path.split("/").filter(Boolean).map(decodeURIComponent);
  return { page: parts[0] || "overview", parts: parts.slice(1), query: new URLSearchParams(qs || "") };
}

let current = parse();
const listeners = new Set<() => void>();
window.addEventListener("hashchange", () => {
  current = parse();
  listeners.forEach((l) => l());
});

export function useRoute(): Route {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => current,
  );
}

export function href(...parts: (string | number)[]) {
  return "#/" + parts.map((p) => encodeURIComponent(String(p))).join("/");
}

export function go(...parts: (string | number)[]) {
  const h = href(...parts);
  if (location.hash !== h) location.hash = h;
}

export function back(fallback: string) {
  if (history.length > 1) history.back();
  else location.hash = fallback;
}
