// 짧은 연결 코드(12글자) → 가족 ID 표(architecture.md 19-3 "QR을 못 찍을 때"). 서버에 하나만 있다(이름 "dir").
// 코드 자체는 두지 않고 SHA-256 해시만 둔다. 5분 뒤 또는 한 번 쓰면 지운다.
import { DurableObject } from "cloudflare:workers";
import { json, readJson, fail, isB64u } from "./util";

interface Entry { familyId: string; expiresAt: number }

export class PairingDirectory extends DurableObject {
  async fetch(req: Request): Promise<Response> {
    const path = new URL(req.url).pathname;
    const b = await readJson(req, 1024).catch(() => null);
    if (!b || !isB64u(b.hash, 43, 43)) return fail("bad_request");
    const key = `s:${b.hash}`;
    const now = Date.now();
    if (path === "/put") {
      if (!isB64u(b.familyId, 22, 22) || typeof b.expiresAt !== "number") return fail("bad_request");
      await this.ctx.storage.put(key, { familyId: b.familyId, expiresAt: b.expiresAt } satisfies Entry);
      await this.sweep(now);
      return json({});
    }
    if (path === "/take") {
      const e = await this.ctx.storage.get<Entry>(key);
      if (!e) return fail("not_found");
      await this.ctx.storage.delete(key); // 한 번만
      if (now > e.expiresAt) return fail("expired");
      return json({ familyId: e.familyId });
    }
    return fail("not_found");
  }

  /** 끝난 항목 정리(쓸 때마다 조금씩) */
  private async sweep(now: number) {
    const all = await this.ctx.storage.list<Entry>({ prefix: "s:", limit: 256 });
    const old = [...all.entries()].filter(([, e]) => e.expiresAt < now).map(([k]) => k);
    if (old.length) await this.ctx.storage.delete(old.slice(0, 128));
  }
}
