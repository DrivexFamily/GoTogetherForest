// 기다리는 가족용 공유 링크(결정 #8). 공유 ID마다 하나. 마지막 상태 1개만, 도착·중단·24시간이면 지운다.
// 가족 열쇠가 없는 웹이 읽으므로 암호화하지 않은 최소 정보(진행률·남은 시간·단계·선택적 5km 격자)만 둔다.
import { DurableObject } from "cloudflare:workers";
import { HttpError, fail, json, readJson, sameText, sha256, isB64u } from "./util";
import { LIMITS, parseSharePayload, type SharePayload } from "./rules";

interface Share { writeKeyHash: string; displayName: string | null; createdAt: number; expiresAt: number; last?: SharePayload & { updatedAt: number } }

export const INTERNAL_SHARE_KEY = "x-ggf-share-key";

export class ShareRoom extends DurableObject {
  async fetch(req: Request): Promise<Response> {
    try {
      return await this.route(req);
    } catch (e) {
      if (e instanceof HttpError) return fail(e.code);
      console.error("share: 처리 실패", (e as Error).name);
      return json({ error: "internal" }, 500);
    }
  }

  private async route(req: Request): Promise<Response> {
    const s = await this.ctx.storage.get<Share>("share");
    if (req.method === "POST") {
      if (s) return fail("conflict");
      const b = await readJson(req, 1024);
      if (!isB64u(b.writeKeyHash, 43, 43)) return fail("bad_request");
      const name = b.displayName === undefined || b.displayName === null ? null : String(b.displayName).trim().slice(0, 12) || null;
      const now = Date.now();
      await this.ctx.storage.put("share", { writeKeyHash: b.writeKeyHash, displayName: name, createdAt: now, expiresAt: now + LIMITS.shareMaxMs } satisfies Share);
      await this.ctx.storage.setAlarm(now + LIMITS.shareMaxMs);
      return json({}, 201);
    }
    if (!s || Date.now() > s.expiresAt) return fail("expired"); // 없는 ID와 끝난 ID를 구분하지 않는다(ID 추측에 힌트를 주지 않음)
    if (req.method === "GET") {
      return json({ displayName: s.displayName, ...(s.last ?? { progress: 0, remainingMin: null, phase: "driving" }), expiresAt: s.expiresAt });
    }
    // 쓰기·삭제는 쓰기 키가 맞아야 한다
    const key = req.headers.get(INTERNAL_SHARE_KEY) ?? "";
    if (!sameText(s.writeKeyHash, await sha256(key))) return fail("unauthorized");
    if (req.method === "DELETE") { await this.wipe(); return json({}); }
    if (req.method === "PUT") {
      const p = parseSharePayload(await readJson(req, 1024));
      if (!p) return fail("bad_request");
      if (p.phase === "arrived") { await this.wipe(); return json({ ended: true }); } // 도착하면 만료(#8)
      s.last = { ...p, updatedAt: Date.now() };
      await this.ctx.storage.put("share", s);
      return json({});
    }
    return fail("not_found");
  }

  private async wipe() {
    await this.ctx.storage.deleteAlarm();
    await this.ctx.storage.deleteAll();
  }

  async alarm(): Promise<void> {
    await this.wipe();
  }
}
