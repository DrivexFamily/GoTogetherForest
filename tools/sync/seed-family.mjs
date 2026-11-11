#!/usr/bin/env node
// 시험용 가족 넣기(결정 #37): 차 없이도 폰 화면을 확인하려고, 서버에 예시 가족(숲 상태·우리 차 진행)을 만든다.
// 열쇠(기기 키·가족 열쇠)는 이 PC의 tools/sync/.seed/에만 남는다(git 제외). 저장소·로그에 남기지 않는다.
//
// 쓰는 법(저장소 루트, Node 22 이상):
//   node tools/sync/seed-family.mjs --server http://localhost:8787 create     # 가족 만들기 + 예시 숲(판 1) + 진행 40%
//   node tools/sync/seed-family.mjs --server <주소> qr                        # 폰 연결 QR 글자(ggf1…)와 짧은 코드를 찍어 줌(5분·1번)
//   node tools/sync/seed-family.mjs --server <주소> approve                   # 승인 대기 폰을 모두 승인(숲·진행·위치)
//   node tools/sync/seed-family.mjs --server <주소> status 75 [driving|resting|arrived]   # 우리 차 진행 바꾸기
//   node tools/sync/seed-family.mjs --server <주소> wipe                      # 서버의 시험용 가족과 .seed 지우기
// 서버 주소는 --server 또는 환경 변수 GGF_SYNC_URL. 로컬은 `cd server && npm run dev`(http://localhost:8787).
// 폰 에뮬레이터에서 로컬 서버를 볼 때는 앱에 http://10.0.2.2:8787 을 넣는다.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SEED = path.join(HERE, ".seed", "family.json");
const args = process.argv.slice(2);
const si = args.indexOf("--server");
const server = (si >= 0 ? args.splice(si, 2)[1] : process.env.GGF_SYNC_URL || "").replace(/\/+$/, "");
const [cmd = "help", ...rest] = args;
if (!server && cmd !== "help") die("서버 주소가 없어요: --server <주소> 또는 GGF_SYNC_URL");

const b64u = (buf) => Buffer.from(buf).toString("base64url");
const rand = (n) => b64u(crypto.getRandomValues(new Uint8Array(n)));
const sha256 = async (s) => b64u(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));

async function seal(familyKey, familyId, kind, text) {
  const key = await crypto.subtle.importKey("raw", Buffer.from(familyKey, "base64url"), "AES-GCM", false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: new TextEncoder().encode(`ggf1|${familyId}|${kind}`) }, key, new TextEncoder().encode(text));
  return { v: 1, iv: b64u(iv), ct: b64u(ct) };
}

const SHORT_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
const shortCode = () => Array.from(crypto.getRandomValues(new Uint8Array(12)), (b) => SHORT_ALPHABET[b % 32]).join("");

/** 가족 열쇠를 짧은 코드로 감싼다(core:sync ShortCode.wrapFamilyKey와 같음: PBKDF2-SHA256 10만 번 → AES-GCM) */
async function wrapWithShort(code, familyKey) {
  const base = await crypto.subtle.importKey("raw", new TextEncoder().encode(code), "PBKDF2", false, ["deriveKey"]);
  const key = await crypto.subtle.deriveKey({ name: "PBKDF2", hash: "SHA-256", salt: new TextEncoder().encode("ggf1-short-v1"), iterations: 100000 },
    base, { name: "AES-GCM", length: 256 }, false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: new TextEncoder().encode("ggf1|short|key") }, key, new TextEncoder().encode(familyKey));
  return { v: 1, iv: b64u(iv), ct: b64u(ct) };
}

async function call(method, p, body, auth) {
  const res = await fetch(server + p, {
    method,
    headers: { "Content-Type": "application/json", ...(auth ? { Authorization: auth } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* 빈 응답 */ }
  if (!res.ok && res.status !== 409) die(`${method} ${p} → ${res.status} ${json?.error ?? ""}`);
  return { status: res.status, json };
}

function load() {
  if (!fs.existsSync(SEED)) die("시험용 가족이 없어요. 먼저 create");
  return JSON.parse(fs.readFileSync(SEED, "utf8"));
}
const auth = (s) => `Device ${s.familyId}.${s.deviceId}.${s.deviceKey}`;

/** 예시 계정(core:account AccountCodec v2 형식, 별명은 평문 — 덩어리 전체를 가족 열쇠로 잠근다) */
function sampleAccount() {
  const now = Date.now();
  const month = new Date().toISOString().slice(0, 7);
  return {
    v: 2, id: rand(12), confirmedAt: now,
    wallet: { balance: 360, lifetime: 560, month, earned: 160 },
    mileage: { balance: 1200, lifetime: 1200, month, earned: 400 },
    unlocks: ["T03", "A04"],
    children: [
      { id: "seed-child-1", nick: "하늘이", sprouts: 118, tree: "T03", map: "M1",
        planted: [{ k: "T01", n: 6 }, { k: "T02", n: 3 }, { k: "T03", n: 2 }],
        decor: { "FLOWER-POT": 2, "WOODEN-BENCH": 1 }, createdAt: now },
      { id: "seed-child-2", nick: "바다", sprouts: 42, tree: "T02", map: "M1",
        planted: [{ k: "T01", n: 2 }, { k: "T02", n: 2 }], createdAt: now },
    ],
    lastRiders: ["seed-child-1", "seed-child-2"],
    trips: [],
  };
}

async function putStatus(s, pct, phase = "driving") {
  const blob = await seal(s.familyKey, s.familyId, "status", JSON.stringify({ p: phase, pct, min: phase === "arrived" ? 0 : Math.round((100 - pct) * 1.3), at: Date.now() }));
  // 예시 대략 위치: 서울 시청 근처를 5km 칸(0.05도)으로 맞춘 값
  const locationBlob = await seal(s.familyKey, s.familyId, "location", JSON.stringify({ lat: 37.55, lon: 127.0 }));
  await call("PUT", "/v1/status", { blob, locationBlob }, auth(s));
}

const commands = {
  async create() {
    if (fs.existsSync(SEED)) die("이미 있어요. 지우려면 wipe");
    const familyKey = rand(32);
    const deviceKey = rand(32);
    const nameBlob = await seal(familyKey, "pending", "name", "시험용 차");
    const { json } = await call("POST", "/v1/families", { deviceKeyHash: await sha256(deviceKey), nameBlob });
    const s = { server, familyId: json.familyId, deviceId: json.deviceId, deviceKey, familyKey };
    fs.mkdirSync(path.dirname(SEED), { recursive: true });
    fs.writeFileSync(SEED, JSON.stringify(s, null, 2), { mode: 0o600 });
    const blob = await seal(familyKey, s.familyId, "state", JSON.stringify(sampleAccount()));
    const r = await call("PUT", "/v1/state", { baseVersion: 0, blob }, auth(s));
    await putStatus(s, 40);
    console.log(`만들었어요: 숲 판 ${r.json.version}, 우리 차 진행 40%. 다음: qr → 폰에서 찍기 → approve`);
  },
  async qr() {
    const s = load();
    const code = rand(16);
    const short = shortCode();
    const { json } = await call("POST", "/v1/pairings", { codeHash: await sha256(code), shortCodeHash: await sha256(short), wrappedKey: await wrapWithShort(short, s.familyKey) }, auth(s));
    console.log("폰 연결(5분, 둘 중 하나를 한 번만). QR 글자는 '코드 붙여넣기'에, 짧은 코드는 '코드 입력'에:");
    console.log(`QR 글자:   ggf1.${s.familyId}.${code}.${s.familyKey}`);
    console.log(`짧은 코드: ${short.match(/.{4}/g).join("-")}`);
    console.log(`만료: ${new Date(json.expiresAt).toLocaleTimeString()}`);
  },
  async approve() {
    const s = load();
    const { json } = await call("GET", "/v1/pairings/pending", undefined, auth(s));
    if (!json.length) return console.log("승인 대기 폰이 없어요(폰에서 QR을 먼저 찍어 주세요)");
    for (const d of json) {
      await call("POST", `/v1/devices/${d.deviceId}/approve`, { roles: ["forest", "trip", "location"] }, auth(s));
      console.log(`승인했어요: 기기 ${d.deviceId}`);
    }
  },
  async status() {
    const s = load();
    const pct = Math.max(0, Math.min(100, Number(rest[0] ?? 40)));
    const phase = rest[1] ?? (pct >= 100 ? "arrived" : "driving");
    await putStatus(s, pct, phase);
    console.log(`우리 차 진행 ${pct}% (${phase})`);
  },
  async wipe() {
    const s = load();
    await call("DELETE", "/v1/families/me", undefined, auth(s));
    fs.rmSync(SEED);
    console.log("시험용 가족을 지웠어요");
  },
  help() {
    console.log(fs.readFileSync(fileURLToPath(import.meta.url), "utf8").split("\n").filter((l) => l.startsWith("//")).map((l) => l.slice(3)).join("\n"));
  },
};

function die(msg) { console.error(msg); process.exit(1); }
Promise.resolve().then(() => (commands[cmd] ?? commands.help)()).catch((e) => die(e.message));
