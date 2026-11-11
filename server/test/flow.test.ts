// 연결 → 승인 → 숲 상태(판 번호 맞추기) → 진행 상태 → 끊기 흐름(Workers 테스트 풀에서 실제 Durable Object로 돈다)
import { SELF } from "cloudflare:test";
import { describe, expect, it } from "vitest";

const enc = (s: string) => btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const rand = (n: number) => enc(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(n))));
const hash = async (s: string) => enc(String.fromCharCode(...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))));
const blob = () => ({ v: 1, iv: rand(12), ct: rand(32) });
const call = (method: string, path: string, body?: unknown, auth?: string) =>
  SELF.fetch(`https://ggf.test${path}`, { method, headers: { "Content-Type": "application/json", ...(auth ? { Authorization: auth } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) });

describe("가족 연결", () => {
  it("QR 코드는 한 번만, 차가 승인해야 숲을 본다, 끊으면 바로 막힌다", async () => {
    const carKey = rand(32);
    const r1 = await call("POST", "/v1/families", { deviceKeyHash: await hash(carKey), nameBlob: blob() });
    expect(r1.status).toBe(201);
    const { familyId, deviceId: carId } = (await r1.json()) as { familyId: string; deviceId: string };
    const car = `Device ${familyId}.${carId}.${carKey}`;
    expect(r1.headers.get("Cache-Control")).toBe("no-store");

    const code = rand(16);
    expect((await call("POST", "/v1/pairings", { codeHash: await hash(code) }, car)).status).toBe(200);

    const phoneKey = rand(32);
    const claim = { familyId, code, deviceKeyHash: await hash(phoneKey), nameBlob: blob() };
    const r2 = await call("POST", "/v1/pairings/claim", claim);
    expect(r2.status).toBe(201);
    const { deviceId: phoneId } = (await r2.json()) as { deviceId: string };
    expect((await call("POST", "/v1/pairings/claim", claim)).status).toBe(404); // 두 번째는 안 됨
    const phone = `Device ${familyId}.${phoneId}.${phoneKey}`;

    expect((await call("GET", "/v1/state", undefined, phone)).status).toBe(403); // 승인 전
    expect((await call("POST", `/v1/devices/${phoneId}/approve`, { roles: ["forest", "trip"] }, car)).status).toBe(200);

    // 판 번호 맞추기: 처음은 0 → 1, 같은 판으로 다시 올리면 409 + 최신 판
    const empty = (await (await call("GET", "/v1/state", undefined, phone)).json()) as { version: number; blob: unknown };
    expect(empty).toEqual({ version: 0, blob: null });
    const r3 = await call("PUT", "/v1/state", { baseVersion: 0, blob: blob() }, phone);
    expect(((await r3.json()) as { version: number }).version).toBe(1);
    const r4 = await call("PUT", "/v1/state", { baseVersion: 0, blob: blob() }, car); // 차가 옛 판(0)을 보고 올림
    expect(r4.status).toBe(409);
    const latest = (await r4.json()) as { version: number; blob: unknown };
    expect(latest.version).toBe(1);
    expect((await call("PUT", "/v1/state", { baseVersion: latest.version, blob: blob() }, car)).status).toBe(200);
    expect((await call("GET", "/v1/state?since=2", undefined, phone)).status).toBe(304);

    expect((await call("PUT", "/v1/status", { blob: blob(), locationBlob: blob() }, car)).status).toBe(200);
    const st = (await (await call("GET", "/v1/status", undefined, phone)).json()) as { locationBlob?: unknown };
    expect(st.locationBlob).toBeUndefined(); // 위치 권한 없음

    expect((await call("DELETE", `/v1/devices/${phoneId}`, undefined, car)).status).toBe(200);
    expect((await call("GET", "/v1/state", undefined, phone)).status).toBe(401);
  });

  it("틀린 코드 5번이면 코드를 버린다", async () => {
    const carKey = rand(32);
    const { familyId, deviceId } = (await (await call("POST", "/v1/families", { deviceKeyHash: await hash(carKey), nameBlob: blob() })).json()) as { familyId: string; deviceId: string };
    const code = rand(16);
    await call("POST", "/v1/pairings", { codeHash: await hash(code) }, `Device ${familyId}.${deviceId}.${carKey}`);
    const body = async (c: string) => ({ familyId, code: c, deviceKeyHash: await hash(rand(32)), nameBlob: blob() });
    for (let i = 0; i < 5; i++) expect((await call("POST", "/v1/pairings/claim", await body(rand(16)))).status).toBe(403);
    expect((await call("POST", "/v1/pairings/claim", await body(code))).status).toBe(404);
  });
});

describe("짧은 코드 연결", () => {
  it("짧은 코드로 연결하면 가족 ID와 감싼 열쇠가 오고, 두 번은 안 된다", async () => {
    const carKey = rand(32);
    const { familyId, deviceId } = (await (await call("POST", "/v1/families", { deviceKeyHash: await hash(carKey), nameBlob: blob() })).json()) as { familyId: string; deviceId: string };
    const short = "ABCD2345EFGH";
    const wrappedKey = blob();
    const car = `Device ${familyId}.${deviceId}.${carKey}`;
    expect((await call("POST", "/v1/pairings", { codeHash: await hash(rand(16)), shortCodeHash: await hash(short), wrappedKey }, car)).status).toBe(200);
    const body = async () => ({ shortCode: "abcd-2345-efgh", deviceKeyHash: await hash(rand(32)), nameBlob: blob() });
    const r = await call("POST", "/v1/pairings/claim-short", await body());
    expect(r.status).toBe(201);
    const out = (await r.json()) as { familyId: string; wrappedKey: unknown };
    expect(out.familyId).toBe(familyId);
    expect(out.wrappedKey).toEqual(wrappedKey);
    expect((await call("POST", "/v1/pairings/claim-short", await body())).status).toBe(404);
  });
});

describe("공유 링크(#8)", () => {
  it("쓰기 키 없이는 못 쓰고, 도착하면 끝난다", async () => {
    const key = rand(32);
    const { shareId } = (await (await call("POST", "/v1/shares", { writeKeyHash: await hash(key) })).json()) as { shareId: string };
    expect((await call("PUT", `/v1/shares/${shareId}`, { progress: 10, remainingMin: 90, phase: "driving" })).status).toBe(401);
    expect((await call("PUT", `/v1/shares/${shareId}`, { progress: 10, remainingMin: 90, phase: "driving" }, `Share ${key}`)).status).toBe(200);
    expect(((await (await call("GET", `/v1/shares/${shareId}`)).json()) as { progress: number }).progress).toBe(10);
    await call("PUT", `/v1/shares/${shareId}`, { progress: 100, remainingMin: 0, phase: "arrived" }, `Share ${key}`);
    expect((await call("GET", `/v1/shares/${shareId}`)).status).toBe(410);
  });
});
