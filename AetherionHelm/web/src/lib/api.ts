import { useCallback, useEffect, useRef, useState } from "react";
import { demoApi } from "./demo";

export class ApiError extends Error {
  status: number;
  data: any;
  constructor(status: number, message: string, data?: any) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

let unauthorized: () => void = () => {};
export function onUnauthorized(fn: () => void) {
  unauthorized = fn;
}

export async function api<T = any>(path: string, init: { method?: string; body?: unknown; signal?: AbortSignal } = {}): Promise<T> {
  const method = init.method ?? (init.body !== undefined ? "POST" : "GET");
  if (__DEMO__) return demoApi(path, method, init.body) as Promise<T>;
  let res: Response;
  try {
    res = await fetch(path, {
      method,
      headers: { "X-Helm": "1", ...(init.body !== undefined ? { "Content-Type": "application/json" } : {}) },
      body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
      credentials: "same-origin",
      signal: init.signal,
    });
  } catch (e) {
    if ((e as Error).name === "AbortError") throw e;
    throw new ApiError(0, "Keine Verbindung zum Panel. Prüfe deine Internetverbindung.");
  }
  let data: any = null;
  try {
    data = await res.json();
  } catch {
    // empty or non-JSON body
  }
  if (!res.ok) {
    if (res.status === 401 && !path.startsWith("/api/auth/")) unauthorized();
    throw new ApiError(res.status, data?.error || `Fehler ${res.status}`, data);
  }
  return data as T;
}

export const post = <T = any>(path: string, body: unknown = {}) => api<T>(path, { method: "POST", body });
export const put = <T = any>(path: string, body: unknown = {}) => api<T>(path, { method: "PUT", body });
export const patch = <T = any>(path: string, body: unknown = {}) => api<T>(path, { method: "PATCH", body });
export const del = <T = any>(path: string) => api<T>(path, { method: "DELETE", body: {} });

/** GET with loading/error state, optional polling, and stale-while-revalidate on path changes. */
export function useApi<T>(path: string | null, opts: { every?: number } = {}) {
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(!!path);
  const seq = useRef(0);

  const load = useCallback(
    async (quiet = false) => {
      if (!path) return;
      const my = ++seq.current;
      if (!quiet) setLoading(true);
      try {
        const d = await api<T>(path);
        if (my === seq.current) {
          setData(d);
          setError(null);
        }
      } catch (e) {
        if (my === seq.current && (e as Error).name !== "AbortError") setError(e as ApiError);
      } finally {
        if (my === seq.current) setLoading(false);
      }
    },
    [path],
  );

  useEffect(() => {
    load();
    if (!opts.every || !path) return;
    const id = setInterval(() => {
      if (document.visibilityState === "visible") load(true);
    }, opts.every);
    return () => clearInterval(id);
  }, [load, opts.every, path]);

  return { data, error, loading, reload: () => load(true), setData };
}
