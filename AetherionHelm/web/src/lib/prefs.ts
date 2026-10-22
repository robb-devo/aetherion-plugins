// Per-device conveniences (view mode, last range). Never anything that must survive.
export function getPref<T extends string>(key: string, def: T): T {
  try {
    return (localStorage.getItem("helm." + key) as T) || def;
  } catch {
    return def;
  }
}

export function setPref(key: string, v: string) {
  try {
    localStorage.setItem("helm." + key, v);
  } catch {
    // storage blocked
  }
}
