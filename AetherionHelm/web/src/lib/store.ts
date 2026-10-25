import { useSyncExternalStore } from "react";

type Listener = () => void;

export interface Store<T> {
  get(): T;
  set(next: T | ((prev: T) => T)): void;
  sub(l: Listener): () => void;
}

export function createStore<T>(initial: T): Store<T> {
  let state = initial;
  const listeners = new Set<Listener>();
  return {
    get: () => state,
    set(next) {
      const v = typeof next === "function" ? (next as (p: T) => T)(state) : next;
      if (Object.is(v, state)) return;
      state = v;
      listeners.forEach((l) => l());
    },
    sub(l) {
      listeners.add(l);
      return () => listeners.delete(l);
    },
  };
}

/** Selectors must return something already in the state (or a primitive), never a fresh object. */
export function useStore<T, U>(store: Store<T>, select: (s: T) => U): U {
  return useSyncExternalStore(store.sub, () => select(store.get()), () => select(store.get()));
}
