// 가족마다 하나씩 생기는 Durable Object(architecture.md 19-7). 한 가족의 요청을 한 줄로 처리하므로
// 1회용 연결 코드 소비·요청 순서 번호가 겹치지 않는다. 저장하는 것은 모두 해시 또는 암호문이다.
import { DurableObject } from "cloudflare:workers";
import { HttpError, fail, json, readJson, randomId, sameText, sha256, isB64u } from "./util";
import { DAY, LIMITS, dayOf, parseBlob, parseRoles, statusExpiry, type Blob, type Role } from "./rules";

interface Meta { createdAt: number; lastSeenAt: number }
interface Device {
  id: string; keyHash: string; nameBlob: Blob; roles: Role[];
  car: boolean; approved: boolean; lastSeenDay: string;
  /** 글자 코드로 연결할 때만: 코드에서 만든 열쇠로 감싼 가족 열쇠(QR 연결이면 없음) */
  wrappedKey?: Blob;
}
interface Pairing { codeHash: string; expiresAt: number; fails: number; shortCodeHash?: string; wrappedKey?: Blob }
interface State { version: number; blob: Blob }
interface Status { blob: Blob; locationBlob?: Blob; updatedAt: number; expiresAt: number }

/** Worker가 붙여 보내는 내부 헤더(밖에서 온 요청은 Worker가 지운다) */
export const INTERNAL_DEVICE = "x-ggf-device";
export const INTERNAL_KEY = "x-ggf-key";
/** 짧은 코드로 온 연결(Worker가 표에서 찾은 뒤 붙인다): 짧은 코드의 해시 */
export const INTERNAL_SHORT = "x-ggf-short";

export class FamilyRoom extends DurableObject {
  private get store() { return this.ctx.storage; }

  async fetch(req: Request): Promise<Response> {
    try {
      return await this.route(req);
    } catch (e) {
      if (e instanceof HttpError) return fail(e.code);
      console.error("family: 처리 실패", (e as Error).name); // 내용·ID는 남기지 않는다
      return json({ error: "internal" }, 500);
    }
  }

  private async route(req: Request): Promise<Response> {
    const url = new URL(req.url);
    const path = url.pathname;
    const m = req.method;

    // 인증 없이 되는 두 가지: 가족 만들기(차), 연결 코드 쓰기(폰)
    if (m === "POST" && path === "/v1/families") return this.create(req);
    if (m === "POST" && path === "/v1/pairings/claim") return this.claim(req);

    const meta = await this.store.get<Meta>("meta");
    if (!meta) return fail("not_found");
    const dev = await this.authenticate(req);
    await this.touch(meta, dev);

    // 승인 전 폰이 할 수 있는 것: 내 상태 보기, 스스로 끊기
    if (m === "GET" && path === "/v1/me") return json({ status: dev.approved ? "approved" : "pending", roles: dev.roles, wrappedKey: dev.wrappedKey });
    if (m === "DELETE" && path === `/v1/devices/${dev.id}`) return this.removeDevice(dev.id);
    if (m === "PUT" && path === "/v1/me/name") return this.setName(dev, req);
    if (!dev.approved) return fail("forbidden");

    if (dev.car) {
      if (m === "POST" && path === "/v1/pairings") return this.openPairing(req);
      if (m === "GET" && path === "/v1/pairings/pending") return this.listDevices(false);
      if (m === "GET" && path === "/v1/devices") return this.listDevices(true);
      const approve = path.match(/^\/v1\/devices\/([A-Za-z0-9_-]{11})\/approve$/);
      if (m === "POST" && approve) return this.approve(approve[1]!, req);
      const del = path.match(/^\/v1\/devices\/([A-Za-z0-9_-]{11})$/);
      if (m === "DELETE" && del) return this.removeDevice(del[1]!);
      if (m === "DELETE" && path === "/v1/families/me") return this.wipe();
      if (m === "GET" && path === "/v1/state") return this.getState(url);
      if (m === "PUT" && path === "/v1/state") return this.putState(req);
      if (m === "PUT" && path === "/v1/status") return this.putStatus(req);
      if (m === "DELETE" && path === "/v1/status") { await this.store.delete("status"); return json({}); }
      return fail("not_found");
    }

    // 폰
    const can = (r: Role) => dev.roles.includes(r);
    if (m === "GET" && path === "/v1/state") return can("forest") ? this.getState(url) : fail("forbidden");
    if (m === "PUT" && path === "/v1/state") return can("forest") ? this.putState(req) : fail("forbidden");
    if (m === "GET" && path === "/v1/status") return can("trip") ? this.getStatus(can("location")) : fail("forbidden");
    return fail("not_found");
  }

  // ---- 인증 -------------------------------------------------------------
  private async authenticate(req: Request): Promise<Device> {
    const id = req.headers.get(INTERNAL_DEVICE) ?? "";
    const key = req.headers.get(INTERNAL_KEY) ?? "";
    const dev = await this.store.get<Device>(`dev:${id}`);
    if (!dev || !sameText(dev.keyHash, await sha256(key))) throw new HttpError("unauthorized");
    return dev;
  }

  /** 마지막 접속은 날짜만, 하루 한 번만 쓴다. 90일 미사용 삭제 알람을 미룬다 */
  private async touch(meta: Meta, dev: Device | null) {
    const now = Date.now();
    const today = dayOf(now);
    if (dev && dev.lastSeenDay !== today) { dev.lastSeenDay = today; await this.store.put(`dev:${dev.id}`, dev); }
    if (now - meta.lastSeenAt > DAY / 2) { meta.lastSeenAt = now; await this.store.put("meta", meta); await this.schedule(); }
  }

  // ---- 가족·연결 ---------------------------------------------------------
  private async create(req: Request): Promise<Response> {
    if (await this.store.get("meta")) return fail("conflict");
    const b = await readJson(req, 4096);
    const nameBlob = parseBlob(b.nameBlob, 512);
    if (!isB64u(b.deviceKeyHash, 43, 43) || !nameBlob) return fail("bad_request");
    const now = Date.now();
    const id = randomId(8);
    const car: Device = { id, keyHash: b.deviceKeyHash, nameBlob, roles: ["forest", "trip", "location"], car: true, approved: true, lastSeenDay: dayOf(now) };
    await this.store.put({ meta: { createdAt: now, lastSeenAt: now } satisfies Meta, [`dev:${id}`]: car });
    await this.schedule();
    return json({ deviceId: id }, 201);
  }

  /** 차: 새 1회용 코드 등록(코드는 차가 만들고 해시만 온다). 이전 코드는 버린다 */
  private async openPairing(req: Request): Promise<Response> {
    const b = await readJson(req, 2048);
    if (!isB64u(b.codeHash, 43, 43)) return fail("bad_request");
    const p: Pairing = { codeHash: b.codeHash, expiresAt: Date.now() + LIMITS.pairingMs, fails: 0 };
    // 짧은 코드(선택): 코드 해시와, 코드에서 만든 열쇠로 감싼 가족 열쇠(서버는 풀 수 없다)
    if (b.shortCodeHash !== undefined) {
      const wrappedKey = parseBlob(b.wrappedKey, 256);
      if (!isB64u(b.shortCodeHash, 43, 43) || !wrappedKey) return fail("bad_request");
      p.shortCodeHash = b.shortCodeHash;
      p.wrappedKey = wrappedKey;
    }
    await this.store.put("pair", p);
    return json({ expiresAt: p.expiresAt });
  }

  /** 폰: 코드 확인과 소비를 한 번에(이 객체는 요청을 하나씩 처리한다) */
  private async claim(req: Request): Promise<Response> {
    const b = await readJson(req, 4096);
    const nameBlob = parseBlob(b.nameBlob, 512);
    const shortHash = req.headers.get(INTERNAL_SHORT);
    if ((!shortHash && !isB64u(b.code, 22, 22)) || !isB64u(b.deviceKeyHash, 43, 43) || !nameBlob) return fail("bad_request");
    const meta = await this.store.get<Meta>("meta");
    const p = await this.store.get<Pairing>("pair");
    if (!meta || !p) return fail("not_found");
    if (Date.now() > p.expiresAt) { await this.store.delete("pair"); return fail("expired"); }
    const ok = shortHash ? !!p.shortCodeHash && sameText(p.shortCodeHash, shortHash) : sameText(p.codeHash, await sha256(b.code as string));
    if (!ok) {
      p.fails += 1;
      if (p.fails >= LIMITS.pairingFails) await this.store.delete("pair"); else await this.store.put("pair", p);
      return fail("forbidden");
    }
    await this.store.delete("pair"); // 한 번 쓰면 끝
    const devices = await this.store.list<Device>({ prefix: "dev:" });
    if (devices.size >= LIMITS.devices) return fail("conflict");
    const id = randomId(8);
    const dev: Device = { id, keyHash: b.deviceKeyHash, nameBlob, roles: [], car: false, approved: false, lastSeenDay: dayOf(Date.now()) };
    await this.store.put(`dev:${id}`, dev);
    // 짧은 코드로 온 폰은 가족 열쇠가 없으므로, 감싼 가족 열쇠를 함께 준다(코드를 아는 폰만 푼다)
    return json(shortHash ? { deviceId: id, status: "pending", wrappedKey: p.wrappedKey } : { deviceId: id, status: "pending" }, 201);
  }

  /** 이 기기의 표시 이름(가족 열쇠로 잠근 것)을 바꾼다. 짧은 코드로 연결한 폰이 열쇠를 받은 뒤 부른다 */
  private async setName(dev: Device, req: Request): Promise<Response> {
    const b = await readJson(req, 2048);
    const nameBlob = parseBlob(b.nameBlob, 512);
    if (!nameBlob) return fail("bad_request");
    dev.nameBlob = nameBlob;
    await this.store.put(`dev:${dev.id}`, dev);
    return json({});
  }

  private async approve(id: string, req: Request): Promise<Response> {
    const dev = await this.store.get<Device>(`dev:${id}`);
    if (!dev || dev.car) return fail("not_found");
    const b = await readJson(req, 2048);
    const roles = parseRoles(b.roles);
    if (!roles) return fail("bad_request");
    const wrappedKey = b.wrappedKey === undefined ? undefined : parseBlob(b.wrappedKey, 256);
    if (wrappedKey === null) return fail("bad_request");
    dev.roles = roles;
    dev.approved = true;
    if (wrappedKey) dev.wrappedKey = wrappedKey; else delete dev.wrappedKey;
    await this.store.put(`dev:${id}`, dev);
    return json({});
  }

  private async listDevices(approved: boolean): Promise<Response> {
    const all = await this.store.list<Device>({ prefix: "dev:" });
    const out = [...all.values()].filter((d) => !d.car && d.approved === approved)
      .map((d) => ({ deviceId: d.id, nameBlob: d.nameBlob, roles: d.roles, lastSeenDate: d.lastSeenDay }));
    return json(out);
  }

  private async removeDevice(id: string): Promise<Response> {
    const dev = await this.store.get<Device>(`dev:${id}`);
    if (!dev) return fail("not_found");
    if (dev.car) return this.wipe(); // 차가 자기를 지우면 가족 전체를 지운다
    await this.store.delete(`dev:${id}`);
    return json({});
  }

  /** 가족 기록 전체 삭제(모두 끊고 지우기, 90일 미사용) */
  private async wipe(): Promise<Response> {
    await this.store.deleteAlarm();
    await this.store.deleteAll();
    return json({});
  }

  // ---- 숲 상태(판 번호 맞추기, compare-and-swap, 결정 #37 ⑥) ------------------
  // 차·폰 어느 쪽이든 같은 Kotlin 규칙으로 바꾼 뒤 "본 판 번호"(baseVersion)를 붙여 올린다.
  // 서버의 판이 그 번호일 때만 받아 판을 1 올리고, 아니면 409와 최신 판을 돌려준다(올린 쪽이 다시 적용).
  private async putState(req: Request): Promise<Response> {
    const b = await readJson(req, LIMITS.stateBytes + 1024);
    const blob = parseBlob(b.blob, LIMITS.stateBytes);
    if (!blob || typeof b.baseVersion !== "number" || !Number.isInteger(b.baseVersion) || b.baseVersion < 0) return fail("bad_request");
    const cur = await this.store.get<State>("state");
    const curVersion = cur?.version ?? 0;
    if (b.baseVersion !== curVersion) return json({ error: "conflict", version: curVersion, blob: cur?.blob ?? null }, 409);
    const next: State = { version: curVersion + 1, blob };
    await this.store.put("state", next);
    return json({ version: next.version });
  }

  private async getState(url: URL): Promise<Response> {
    const s = await this.store.get<State>("state");
    if (!s) return json({ version: 0, blob: null });
    const since = Number(url.searchParams.get("since") ?? "-1");
    if (s.version <= since) return new Response(null, { status: 304, headers: { "Cache-Control": "no-store" } });
    return json(s);
  }

  // ---- 우리 차 진행 ------------------------------------------------------
  private async putStatus(req: Request): Promise<Response> {
    const b = await readJson(req, LIMITS.statusBytes * 2);
    const blob = parseBlob(b.blob, LIMITS.statusBytes);
    const locationBlob = b.locationBlob === undefined ? undefined : parseBlob(b.locationBlob, 1024);
    if (!blob || locationBlob === null) return fail("bad_request");
    const now = Date.now();
    const s: Status = { blob, locationBlob, updatedAt: now, expiresAt: statusExpiry(now, b.expiresAt) };
    await this.store.put("status", s);
    await this.schedule();
    return json({ expiresAt: s.expiresAt });
  }

  private async getStatus(withLocation: boolean): Promise<Response> {
    const s = await this.store.get<Status>("status");
    if (!s || Date.now() > s.expiresAt) return fail("not_found");
    return json({ blob: s.blob, locationBlob: withLocation ? s.locationBlob : undefined, updatedAt: s.updatedAt });
  }

  // ---- 알람: 진행 상태 만료, 90일 미사용 ------------------------
  private async schedule() {
    const meta = await this.store.get<Meta>("meta");
    if (!meta) return;
    const status = await this.store.get<Status>("status");
    const next = Math.min(meta.lastSeenAt + LIMITS.idleMs, status?.expiresAt ?? Infinity, Date.now() + DAY);
    await this.store.setAlarm(next);
  }

  async alarm(): Promise<void> {
    const meta = await this.store.get<Meta>("meta");
    if (!meta) return;
    const now = Date.now();
    if (now >= meta.lastSeenAt + LIMITS.idleMs) { await this.wipe(); return; }
    const status = await this.store.get<Status>("status");
    if (status && now >= status.expiresAt) await this.store.delete("status");
    await this.schedule();
  }
}
