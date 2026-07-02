export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(text || 'Request failed');
  }
  return text ? (JSON.parse(text) as T) : ({} as T);
}
