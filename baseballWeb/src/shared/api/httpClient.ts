export async function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const res = await fetch(url, { signal });
  if (!res.ok) {
    throw new Error(`요청 실패: ${res.status}`);
  }
  return res.json() as Promise<T>;
}
