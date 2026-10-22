// German UI strings, keyed by the English source text. One module per area of the app; all are merged here.
const mods = import.meta.glob<{ default: Record<string, string> }>("./*.ts", { eager: true });

export const de: Record<string, string> = Object.assign(
  {},
  ...Object.entries(mods)
    .filter(([path]) => !path.endsWith("/index.ts"))
    .map(([, m]) => m.default),
);
