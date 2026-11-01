/**
 * A tiny value store in localStorage that React can subscribe to (useSyncExternalStore). Changes in other tabs
 * arrive through the storage event. Storage may be missing (private mode): reads fall back, writes are ignored.
 */
export function localStore<T>(key: string, fallback: T) {
  const event = `vaabhi:${key}`;
  let cache: { raw: string | null; value: T } | undefined;

  function read(): T {
    let raw: string | null = null;
    try {
      raw = window.localStorage.getItem(key);
    } catch {
      return fallback;
    }
    if (cache?.raw === raw) return cache.value;
    let value = fallback;
    try {
      value = raw === null ? fallback : (JSON.parse(raw) as T);
    } catch {
      value = fallback;
    }
    cache = { raw, value };
    return value;
  }

  function write(value: T): void {
    try {
      window.localStorage.setItem(key, JSON.stringify(value));
    } catch {
      // storage unavailable: the change lasts for this page only
    }
    window.dispatchEvent(new Event(event));
  }

  function subscribe(onChange: () => void): () => void {
    const onStorage = (e: StorageEvent) => {
      if (e.key === key) onChange();
    };
    window.addEventListener(event, onChange);
    window.addEventListener("storage", onStorage);
    return () => {
      window.removeEventListener(event, onChange);
      window.removeEventListener("storage", onStorage);
    };
  }

  return { read, write, subscribe, serverValue: () => fallback };
}
