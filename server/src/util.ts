// 공용 도우미: 응답·보안 헤더·난수·해시. 로그에는 ID·키·본문을 남기지 않는다.

/** 모든 응답에 붙이는 헤더(docs/share-api.md 공통) */
export const SECURITY_HEADERS: Record<string, string> = {
  "Cache-Control": "no-store",
  "Referrer-Policy": "no-referrer",
  "X-Robots-Tag": "noindex",
  "X-Content-Type-Options": "nosniff",
  "Strict-Transport-Security": "max-age=31536000",
};

export type ErrorCode =
  | "unauthorized" | "forbidden" | "not_found" | "expired" | "conflict"
  | "too_large" | "rate_limited" | "bad_request";

const STATUS: Record<ErrorCode, number> = {
  unauthorized: 401, forbidden: 403, not_found: 404, expired: 410, conflict: 409,
  too_large: 413, rate_limited: 429, bad_request: 400,
};

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...SECURITY_HEADERS, "Content-Type": "application/json; charset=utf-8" },
  });
}

export function fail(code: ErrorCode): Response {
  return json({ error: code }, STATUS[code]);
}

export class HttpError extends Error {
  constructor(readonly code: ErrorCode) { super(code); }
}

/** base64url(패딩 없음) */
export function b64u(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

const B64U = /^[A-Za-z0-9_-]+$/;

/** base64url 글자인지, 길이가 [min, max] 안인지 */
export function isB64u(v: unknown, min: number, max: number): v is string {
  return typeof v === "string" && v.length >= min && v.length <= max && B64U.test(v);
}

/** [bytes]바이트 안전한 난수의 base64url. 128비트 = 16바이트 → 22글자 */
export function randomId(bytes = 16): string {
  return b64u(crypto.getRandomValues(new Uint8Array(bytes)));
}

/** SHA-256 → base64url(43글자) */
export async function sha256(text: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return b64u(new Uint8Array(d));
}

/** 길이가 같은 두 글자를 시간이 일정하게 비교한다(해시끼리 비교할 때) */
export function sameText(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

/** 본문을 [maxBytes]까지만 읽어 JSON 객체로. 넘으면 too_large, 형식이 틀리면 bad_request */
export async function readJson(req: Request, maxBytes: number): Promise<Record<string, unknown>> {
  const declared = Number(req.headers.get("Content-Length") ?? "0");
  if (declared > maxBytes) throw new HttpError("too_large");
  const text = await req.text();
  if (new TextEncoder().encode(text).length > maxBytes) throw new HttpError("too_large");
  if (text.length === 0) return {};
  let v: unknown;
  try { v = JSON.parse(text); } catch { throw new HttpError("bad_request"); }
  if (typeof v !== "object" || v === null || Array.isArray(v)) throw new HttpError("bad_request");
  return v as Record<string, unknown>;
}
