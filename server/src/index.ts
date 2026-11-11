// 같이가숲 공유 서버 입구(Worker). 인증 헤더를 읽어 알맞은 Durable Object로 보낸다.
// API: docs/share-api.md, 설계: docs/architecture.md 19장. URL에는 토큰을 넣지 않는다.
import { FamilyRoom, INTERNAL_DEVICE, INTERNAL_KEY, INTERNAL_SHORT } from "./family";
import { PairingDirectory } from "./directory";
import { ShareRoom, INTERNAL_SHARE_KEY } from "./share";
import { SECURITY_HEADERS, fail, json, randomId, readJson, isB64u, sha256 } from "./util";
import { SHORT_CODE, parseDeviceAuth, parseShareAuth } from "./rules";

export { FamilyRoom, ShareRoom, PairingDirectory };

export interface Env {
  FAMILY: DurableObjectNamespace<FamilyRoom>;
  SHARE: DurableObjectNamespace<ShareRoom>;
  DIRECTORY: DurableObjectNamespace<PairingDirectory>;
}

/** 내부 헤더를 지우고(밖에서 흉내 내지 못하게) 필요한 것만 붙여 새 요청을 만든다 */
function forward(req: Request, extra: Record<string, string>, body?: string): Request {
  const h = new Headers();
  const ct = req.headers.get("Content-Type");
  if (ct) h.set("Content-Type", ct);
  for (const [k, v] of Object.entries(extra)) h.set(k, v);
  return new Request(req.url, { method: req.method, headers: h, body: body ?? (req.method === "GET" || req.method === "HEAD" ? undefined : req.body) });
}

const family = (env: Env, familyId: string) => env.FAMILY.get(env.FAMILY.idFromName(familyId));
const share = (env: Env, shareId: string) => env.SHARE.get(env.SHARE.idFromName(shareId));
const directory = (env: Env) => env.DIRECTORY.get(env.DIRECTORY.idFromName("dir"));
const internal = (path: string, body: unknown) => new Request(`https://internal${path}`, { method: "POST", body: JSON.stringify(body) });

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    const path = url.pathname;
    if (url.protocol !== "https:" && url.hostname !== "localhost" && url.hostname !== "127.0.0.1") return fail("forbidden");
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: SECURITY_HEADERS });

    // 가족 만들기(차, 처음): 가족 ID는 서버가 128비트 난수로 정한다
    if (req.method === "POST" && path === "/v1/families") {
      const familyId = randomId(16);
      const res = await family(env, familyId).fetch(forward(req, {}));
      if (res.status !== 201) return res;
      const { deviceId } = (await res.json()) as { deviceId: string };
      return json({ familyId, deviceId }, 201);
    }

    // 연결 코드 쓰기(폰, 아직 기기 키 승인 전): 본문의 familyId(QR에서 읽음)로 그 가족에게 보낸다
    if (req.method === "POST" && path === "/v1/pairings/claim") {
      const text = await req.text();
      if (text.length > 4096) return fail("too_large");
      let familyId: unknown;
      try { familyId = (JSON.parse(text) as Record<string, unknown>).familyId; } catch { return fail("bad_request"); }
      if (!isB64u(familyId, 22, 22)) return fail("bad_request");
      return family(env, familyId).fetch(forward(req, {}, text));
    }

    // 짧은 코드로 연결(폰, QR을 못 찍을 때): 표에서 가족을 찾아(한 번만) 그 가족에게 보낸다
    if (req.method === "POST" && path === "/v1/pairings/claim-short") {
      const text = await req.text();
      if (text.length > 4096) return fail("too_large");
      let body: Record<string, unknown>;
      try { body = JSON.parse(text) as Record<string, unknown>; } catch { return fail("bad_request"); }
      const code = typeof body.shortCode === "string" ? body.shortCode.toUpperCase().replace(/[^0-9A-Z]/g, "") : "";
      if (!SHORT_CODE.test(code)) return fail("bad_request");
      const hash = await sha256(code);
      const found = await directory(env).fetch(internal("/take", { hash }));
      if (!found.ok) return found.status === 410 ? fail("expired") : fail("not_found");
      const { familyId } = (await found.json()) as { familyId: string };
      const res = await family(env, familyId).fetch(forward(req, { [INTERNAL_SHORT]: hash }, JSON.stringify({ deviceKeyHash: body.deviceKeyHash, nameBlob: body.nameBlob })));
      if (res.status !== 201) return res;
      return json({ familyId, ...((await res.json()) as Record<string, unknown>) }, 201);
    }

    // 연결 코드 열기(차): 짧은 코드가 있으면 표에도 적는다
    if (req.method === "POST" && path === "/v1/pairings") {
      const auth = parseDeviceAuth(req.headers.get("Authorization"));
      if (!auth) return fail("unauthorized");
      const text = await req.text();
      if (text.length > 4096) return fail("too_large");
      const res = await family(env, auth.familyId).fetch(forward(req, { [INTERNAL_DEVICE]: auth.deviceId, [INTERNAL_KEY]: auth.key }, text));
      if (!res.ok) return res;
      const out = (await res.json()) as { expiresAt: number };
      let shortCodeHash: unknown;
      try { shortCodeHash = (JSON.parse(text) as Record<string, unknown>).shortCodeHash; } catch { /* 위에서 이미 검사됨 */ }
      if (isB64u(shortCodeHash, 43, 43)) await directory(env).fetch(internal("/put", { hash: shortCodeHash, familyId: auth.familyId, expiresAt: out.expiresAt }));
      return json(out);
    }

    // 공유 링크(#8)
    if (path === "/v1/shares" && req.method === "POST") {
      const shareId = randomId(16);
      const b = await readJson(req, 1024).catch(() => null);
      if (!b) return fail("bad_request");
      const res = await share(env, shareId).fetch(new Request(req.url, { method: "POST", body: JSON.stringify(b) }));
      return res.status === 201 ? json({ shareId }, 201) : res;
    }
    const sm = path.match(/^\/v1\/shares\/([A-Za-z0-9_-]{22})$/);
    if (sm) {
      const key = parseShareAuth(req.headers.get("Authorization"));
      if (req.method !== "GET" && !key) return fail("unauthorized");
      return share(env, sm[1]!).fetch(forward(req, key ? { [INTERNAL_SHARE_KEY]: key } : {}));
    }

    // 나머지는 기기 인증
    if (path.startsWith("/v1/")) {
      const auth = parseDeviceAuth(req.headers.get("Authorization"));
      if (!auth) return fail("unauthorized");
      return family(env, auth.familyId).fetch(forward(req, { [INTERNAL_DEVICE]: auth.deviceId, [INTERNAL_KEY]: auth.key }));
    }

    // 공유 링크 웹 화면(/s/<shareId>)은 PT 단계에 web/share/를 정적 자산으로 붙인다
    return fail("not_found");
  },
} satisfies ExportedHandler<Env>;
