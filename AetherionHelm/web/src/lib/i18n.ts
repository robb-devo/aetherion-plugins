// UI language. English is the default; German comes from the dictionaries in src/i18n/de, keyed by the English text.
// Switching language reloads the app, so t() can stay a plain function and formatters are built once per load.
import { de } from "../i18n/de";

export type Lang = "en" | "de";
const KEY = "helm.lang";

function read(): Lang {
  try {
    const v = localStorage.getItem(KEY);
    if (v === "en" || v === "de") return v;
  } catch {
    // storage blocked
  }
  return "en";
}

export const lang: Lang = read();
/** BCP 47 locale for Intl formatters: 24-hour clock in both languages. */
export const locale = lang === "de" ? "de-DE" : "en-GB";

document.documentElement.lang = lang;

/** t("{n} players online", { n: 4 }). Unknown keys fall back to the English text. */
export function t(text: string, vars?: Record<string, string | number>): string {
  const s = lang === "de" ? (de[text] ?? text) : text;
  return vars ? s.replace(/\{(\w+)\}/g, (m, k: string) => (k in vars ? String(vars[k]) : m)) : s;
}

export function setLang(l: Lang) {
  try {
    localStorage.setItem(KEY, l);
  } catch {
    // storage blocked
  }
  location.reload();
}
