import { createStore } from "./store";

export type ThemePref = "system" | "dark" | "light";
const KEY = "helm.theme";

function read(): ThemePref {
  try {
    const v = localStorage.getItem(KEY);
    if (v === "dark" || v === "light" || v === "system") return v;
  } catch {
    // storage blocked
  }
  return "dark";
}

export const theme = createStore<ThemePref>(read());

const mq = window.matchMedia("(prefers-color-scheme: light)");
export function resolved(p: ThemePref = theme.get()): "dark" | "light" {
  return p === "system" ? (mq.matches ? "light" : "dark") : p;
}

function apply() {
  const r = resolved();
  document.documentElement.dataset.theme = r;
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", r === "dark" ? "#05060b" : "#eef0f7");
}

export function setTheme(p: ThemePref) {
  try {
    localStorage.setItem(KEY, p);
  } catch {
    // storage blocked
  }
  theme.set(p);
  apply();
}

mq.addEventListener("change", apply);
apply();
