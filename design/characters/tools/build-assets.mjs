// 같이가숲 캐릭터 에셋 생성기
//
// 한 곳(이 파일)에 그림을 정의하고 세 가지를 만든다.
//   1) design/characters/svg/*.svg            : 레이어(그룹)로 나뉜 SVG 원본
//   2) core/ui/src/main/res/drawable/ggf_*.xml : Android VectorDrawable
//   3) core/ui/.../ArtRoleTokens.kt             : 역할(role)별 라이트/다크 색
//
// 손그림 느낌은 SVG 필터 없이 "조금씩 흔들린 선"으로 낸다(VectorDrawable은 필터를 못 쓴다).
// 흔들림은 고정 시드 난수라서 몇 번을 다시 돌려도 같은 그림이 나온다.
//
// 실행: node design/characters/tools/build-assets.mjs   (저장소 루트에서)

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CHAR_DIR = path.resolve(HERE, "..");
const REPO = path.resolve(CHAR_DIR, "..", "..");
const SVG_DIR = path.join(CHAR_DIR, "svg");
const RES_DIR = path.join(REPO, "core/ui/src/main/res/drawable");
const KT_FILE = path.join(REPO, "core/ui/src/main/kotlin/app/familyjourney/core/ui/ArtRoleTokens.kt");

// ---------------------------------------------------------------------------
// 1. 역할(role)별 색. [라이트, 다크]
//    path 이름 = 역할 이름. Compose는 이 이름으로 테마 색·꾸미기 색을 덮어쓴다.
// ---------------------------------------------------------------------------
export const ROLES = {
  // 선 역할: 스스로 선 색을 가진다
  line: ["#2B2622", "#121714"], // 바깥선
  limb: ["#2B2622", "#CDBFB0"], // 서 있는 캐릭터 팔다리·깃대 (다크에서는 밝게 해서 어두운 배경에 묻히지 않게)
  feature: ["#2B2622", "#1E1915"], // 눈·입 (두 테마 모두 어둡게)
  shade: ["#2B2622", "#000000"], // 크레파스 빗금(투명도 낮게)
  leafShade: ["#2B774A", "#2A6B45"], // 줄기, 잎맥, 잎 빗금
  highlight: ["#FFFFFF", "#FFFFFF"], // 유리 반사
  spoke: ["#F7F7F2", "#C9CFCB"], // 바퀴살
  motion: ["#2B2622", "#1E1915"], // 손 흔드는 표시(움직이지 않는 선)
  // 채움 역할: 바깥선은 line 색
  shadow: ["#2B2622", "#000000"],
  skin: ["#F6CFAA", "#E8C29D"],
  cheek: ["#E9786A", "#DE6E61"],
  shine: ["#FFFFFF", "#FFFFFF"],
  hairA: ["#3E2C22", "#4E382B"],
  hairB: ["#8A4E2B", "#9A5E3A"],
  hairKid: ["#4A3326", "#5A4031"],
  shirtA: ["#3F92D2", "#5BA4DC"],
  shirtB: ["#D25A49", "#DE7262"],
  shirtKid: ["#F0AE34", "#EDB34C"],
  shoe: ["#5B4A3F", "#6B5A4E"],
  carBody: ["#E3604F", "#E06A5C"],
  bumper: ["#D9DCD6", "#AEB6B1"],
  glass: ["#DDEEFB", "#AFCDE3"],
  tire: ["#2B2622", "#1A1E1B"],
  hub: ["#F7F7F2", "#C9CFCB"],
  lamp: ["#F0AE34", "#F2BE57"],
  tailLamp: ["#D25A49", "#E07A6A"],
  leaf: ["#6BB85A", "#62B354"],
  leafDark: ["#2B774A", "#3E8E5C"],
  trunk: ["#B7713D", "#A9683A"],
  soil: ["#A97B52", "#8A6142"],
  pot: ["#D9774E", "#C86A45"],
  squirrel: ["#B7713D", "#C27E4A"],
  belly: ["#F3E2C8", "#E2D0B4"],
  acorn: ["#C98A4B", "#C08245"],
  acornCap: ["#6E4A2E", "#7A5536"],
  rabbit: ["#F6F3EC", "#E6E1D8"],
  rabbitInner: ["#F2A9A0", "#E2958B"],
  bird: ["#3F92D2", "#6BB3EA"],
  birdWing: ["#2C76B3", "#4E97CF"],
  birdBelly: ["#DDEEFB", "#C3DCEE"],
  beak: ["#F0AE34", "#F2BE57"],
  wall: ["#FFF0CC", "#E9D8AC"],
  roof: ["#D25A49", "#DE7262"],
  door: ["#B7713D", "#A9683A"],
  window: ["#DDEEFB", "#F4D88C"], // 밤에는 창에 불이 켜진다
  heart: ["#E86A5A", "#F08676"],
  flagCloth: ["#D25A49", "#F08676"],
  sparkle: ["#F0AE34", "#F2BE57"],
  screen: ["#3F92D2", "#5BA4DC"],
};
export const SELF_STROKE = new Set(["line", "limb", "feature", "shade", "leafShade", "highlight", "spoke", "motion"]);

// ---------------------------------------------------------------------------
// 2. 손그림 도형 도구
// ---------------------------------------------------------------------------
const R1 = (n) => {
  const v = Math.round(n * 10) / 10;
  return Object.is(v, -0) ? "0" : String(v);
};
const P = (p) => `${R1(p[0])},${R1(p[1])}`;

function rng(seed) {
  let s = seed >>> 0 || 1;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}
let rnd = rng(1);
const jitter = (amp) => (rnd() - 0.5) * 2 * amp;

function rot(p, c, deg) {
  const a = (deg * Math.PI) / 180;
  const x = p[0] - c[0];
  const y = p[1] - c[1];
  return [c[0] + x * Math.cos(a) - y * Math.sin(a), c[1] + x * Math.sin(a) + y * Math.cos(a)];
}

/** 닫힌 Catmull-Rom 곡선 → 3차 베지어 */
function closedSmooth(pts) {
  const n = pts.length;
  let d = `M${P(pts[0])}`;
  for (let i = 0; i < n; i++) {
    const p0 = pts[(i - 1 + n) % n];
    const p1 = pts[i];
    const p2 = pts[(i + 1) % n];
    const p3 = pts[(i + 2) % n];
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += `C${P(c1)} ${P(c2)} ${P(p2)}`;
  }
  return d + "Z";
}

/** 열린 Catmull-Rom 곡선 */
function openSmooth(pts) {
  if (pts.length === 2) return `M${P(pts[0])}L${P(pts[1])}`;
  const n = pts.length;
  let d = `M${P(pts[0])}`;
  for (let i = 0; i < n - 1; i++) {
    const p0 = pts[Math.max(0, i - 1)];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[Math.min(n - 1, i + 2)];
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += `C${P(c1)} ${P(c2)} ${P(p2)}`;
  }
  return d;
}

/** 살짝 찌그러진 타원(손으로 그린 동그라미) */
function blob(cx, cy, rx, ry, { wob = 0.035, n = 10, deg = 0 } = {}) {
  const start = rnd() * Math.PI;
  const pts = [];
  for (let i = 0; i < n; i++) {
    const a = start + (2 * Math.PI * i) / n;
    const k = 1 + jitter(wob);
    pts.push(rot([cx + rx * k * Math.cos(a), cy + ry * k * Math.sin(a)], [cx, cy], deg));
  }
  return closedSmooth(pts);
}

/** 정확한 타원(눈처럼 작은 것) */
function ellipse(cx, cy, rx, ry = rx) {
  return `M${R1(cx - rx)},${R1(cy)}A${R1(rx)},${R1(ry)} 0 1,0 ${R1(cx + rx)},${R1(cy)}A${R1(rx)},${R1(ry)} 0 1,0 ${R1(cx - rx)},${R1(cy)}Z`;
}

/** 점들을 살짝 흔든 뒤 부드럽게 이은 닫힌 모양 */
function shape(pts, amp = 0.6) {
  return closedSmooth(pts.map((p) => [p[0] + jitter(amp), p[1] + jitter(amp)]));
}

/** 모서리를 둥글린 다각형(창문, 화분처럼 모서리가 있는 것) */
function rpoly(pts, r = 4, amp = 0.35) {
  const q = pts.map((p) => [p[0] + jitter(amp), p[1] + jitter(amp)]);
  const n = q.length;
  const cut = (a, b, dist) => {
    const dx = b[0] - a[0];
    const dy = b[1] - a[1];
    const len = Math.hypot(dx, dy) || 1;
    const t = Math.min(dist, len / 2) / len;
    return [a[0] + dx * t, a[1] + dy * t];
  };
  let d = "";
  for (let i = 0; i < n; i++) {
    const prev = q[(i - 1 + n) % n];
    const cur = q[i];
    const next = q[(i + 1) % n];
    const a = cut(cur, prev, r);
    const b = cut(cur, next, r);
    d += (i === 0 ? `M${P(a)}` : `L${P(a)}`) + `Q${P(cur)} ${P(b)}`;
  }
  return d + "Z";
}

/** 흔들린 선 */
function stroke(pts, amp = 0.35) {
  return openSmooth(pts.map((p, i) => (i === 0 || i === pts.length - 1 ? p : [p[0] + jitter(amp), p[1] + jitter(amp)])));
}

/** 여러 조각 선을 한 path로 */
const multi = (...ds) => ds.join("");

/** 타원 안의 크레파스 빗금. side>0이면 오른쪽 아래(그늘 쪽)만 */
function hatch(cx, cy, rx, ry, { deg = -35, gap = 5, frac = 0.72, side = 1 } = {}) {
  const a = (deg * Math.PI) / 180;
  const dir = [Math.cos(a), Math.sin(a)];
  const nor = [-dir[1], dir[0]];
  const R = Math.max(rx, ry) * frac;
  let d = "";
  for (let t = -R; t <= R; t += gap) {
    if (side > 0 && t < -R * 0.15) continue;
    // 법선 방향으로 t만큼 옮긴 직선이 축소 타원과 만나는 구간
    const ox = cx + nor[0] * t;
    const oy = cy + nor[1] * t;
    const A = (dir[0] / (rx * frac)) ** 2 + (dir[1] / (ry * frac)) ** 2;
    const B = 2 * (((ox - cx) * dir[0]) / (rx * frac) ** 2 + ((oy - cy) * dir[1]) / (ry * frac) ** 2);
    const C = ((ox - cx) / (rx * frac)) ** 2 + ((oy - cy) / (ry * frac)) ** 2 - 1;
    const disc = B * B - 4 * A * C;
    if (disc <= 0) continue;
    const s1 = (-B - Math.sqrt(disc)) / (2 * A);
    const s2 = (-B + Math.sqrt(disc)) / (2 * A);
    const shrink = 0.12 + rnd() * 0.18;
    const m1 = s1 + (s2 - s1) * shrink;
    const m2 = s2 - (s2 - s1) * (0.1 + rnd() * 0.15);
    if (m2 - m1 < 2) continue;
    const p1 = [ox + dir[0] * m1, oy + dir[1] * m1];
    const p2 = [ox + dir[0] * m2, oy + dir[1] * m2];
    const mid = [(p1[0] + p2[0]) / 2 + jitter(0.6), (p1[1] + p2[1]) / 2 + jitter(0.6)];
    d += `M${P(p1)}Q${P(mid)} ${P(p2)}`;
  }
  return d;
}

/** 외곽선을 한 번 더 그은 듯한 짧은 호(크레파스로 덧칠한 느낌) */
function retrace(cx, cy, rx, ry, from, to, off = 1.3) {
  const pts = [];
  const steps = 6;
  for (let i = 0; i <= steps; i++) {
    const a = ((from + ((to - from) * i) / steps) * Math.PI) / 180;
    const k = off + jitter(0.5);
    pts.push([cx + (rx + k) * Math.cos(a), cy + (ry + k) * Math.sin(a)]);
  }
  return openSmooth(pts);
}

/** 네 갈래 반짝이 */
function sparkle(cx, cy, s) {
  const k = s * 0.2;
  return `M${R1(cx)},${R1(cy - s)}Q${R1(cx + k)},${R1(cy - k)} ${R1(cx + s)},${R1(cy)}Q${R1(cx + k)},${R1(cy + k)} ${R1(cx)},${R1(cy + s)}Q${R1(cx - k)},${R1(cy + k)} ${R1(cx - s)},${R1(cy)}Q${R1(cx - k)},${R1(cy - k)} ${R1(cx)},${R1(cy - s)}Z`;
}

// ---------------------------------------------------------------------------
// 3. 노드 만들기
//    F: 채운 모양(+바깥선), S: 선만, G: 그룹(움직일 관절, 바꿔 낄 레이어)
// ---------------------------------------------------------------------------
const F = (role, d, o = {}) => {
  if (SELF_STROKE.has(role) && role !== "feature") throw new Error(`fill role expected: ${role}`);
  return { k: "p", role, d, fill: true, outline: o.outline ?? true, sw: o.sw ?? 2.4, fa: o.fa, sa: o.sa };
};
const S = (role, d, o = {}) => {
  if (!SELF_STROKE.has(role)) throw new Error(`stroke role expected: ${role}`);
  return { k: "p", role, d, fill: false, stroke: true, sw: o.sw ?? 2.4, sa: o.sa };
};
const G = (name, children, o = {}) => ({ k: "g", name, children: children.flat(), ...o });

// ---------------------------------------------------------------------------
// 4. 얼굴·머리 부품 (머리 중심 cx,cy, 반지름 R 기준. 서 있는 그림과 차 안 그림이 같이 쓴다)
// ---------------------------------------------------------------------------
const sw = (s, base = 2.4) => base * Math.max(0.7, Math.sqrt(s));

function eyes(cx, cy, R, base, { kind = "open", dx = 10, dy = 4, look = [0, 0] } = {}) {
  const s = R / base;
  if (kind === "happy") {
    return [
      S("feature", multi(
        `M${P([cx - (dx + 3.4) * s, cy + (dy + 1) * s])}Q${P([cx - dx * s, cy + (dy - 3.4) * s])} ${P([cx - (dx - 3.4) * s, cy + (dy + 1) * s])}`,
        `M${P([cx + (dx - 3.4) * s, cy + (dy + 1) * s])}Q${P([cx + dx * s, cy + (dy - 3.4) * s])} ${P([cx + (dx + 3.4) * s, cy + (dy + 1) * s])}`,
      ), { sw: sw(s, 2.3) }),
    ];
  }
  const ex = look[0] * s;
  const ey = look[1] * s;
  return [
    F("feature", multi(ellipse(cx - dx * s + ex, cy + dy * s + ey, 2.8 * s, 3.6 * s), ellipse(cx + dx * s + ex, cy + dy * s + ey, 2.8 * s, 3.6 * s)), { outline: false }),
    F("shine", multi(ellipse(cx - dx * s + ex + 1 * s, cy + (dy - 1.3) * s + ey, 1.05 * s), ellipse(cx + dx * s + ex + 1 * s, cy + (dy - 1.3) * s + ey, 1.05 * s)), { outline: false }),
  ];
}

function cheeks(cx, cy, R, base, dx = 18, dy = 12) {
  const s = R / base;
  return F("cheek", multi(ellipse(cx - dx * s, cy + dy * s, 5 * s, 3.2 * s), ellipse(cx + dx * s, cy + dy * s, 5 * s, 3.2 * s)), { outline: false, fa: 0.5 });
}

function mouth(cx, cy, R, base, kind = "smile", dy = 13) {
  const s = R / base;
  if (kind === "open") {
    return F("feature", `M${P([cx - 5.5 * s, cy + (dy - 1) * s])}Q${P([cx, cy + (dy + 8) * s])} ${P([cx + 5.5 * s, cy + (dy - 1) * s])}Z`, { outline: false });
  }
  if (kind === "o") return F("feature", ellipse(cx, cy + (dy + 1.5) * s, 2.6 * s, 3.2 * s), { outline: false });
  return S("feature", `M${P([cx - 5 * s, cy + dy * s])}Q${P([cx, cy + (dy + 5) * s])} ${P([cx + 5 * s, cy + dy * s])}`, { sw: sw(s, 2.2) });
}

/** 아이 머리: 둥근 얼굴 + 앞머리 + 머리 위 새싹(같이가숲 아이의 표시) */
function kidHead(cx, cy, R, { eyesKind = "open", look = [0, 0], mouthKind = "smile" } = {}) {
  const base = 30;
  const s = R / base;
  const at = (x, y) => [cx + x * s, cy + y * s];
  const hair = [
    [-30, 2], [-29, -14], [-20, -24], [-8, -28.5], [8, -28.5], [20, -24], [29, -14], [30, 2],
    [24, -6], [18, -8.5], [12, -5], [6, -11], [0, -6], [-6, -11], [-12, -5], [-18, -8.5], [-24, -6],
  ].map(([x, y]) => at(x, y));
  return [
    G("head", [
      F("skin", blob(cx, cy, R, R * 0.9, { wob: 0.025, n: 12 }), { sw: sw(s) }),
      F("hairKid", shape(hair, 0.5 * s), { sw: sw(s) }),
      S("leafShade", stroke([at(0, -27), at(0.6, -33), at(1.2, -39)]), { sw: sw(s, 3) }),
      G("sprout", [
        F("leaf", blob(...at(-6.5, -40), 7.2 * s, 3.8 * s, { deg: -30, n: 8, wob: 0.03 }), { sw: sw(s, 2) }),
        F("leaf", blob(...at(7.5, -43), 8.2 * s, 4.2 * s, { deg: 24, n: 8, wob: 0.03 }), { sw: sw(s, 2) }),
      ]),
      S("line", retrace(cx, cy, R, R * 0.9, 200, 250), { sw: sw(s, 1.1), sa: 0.45 }),
      ...eyes(cx, cy, R, base, { kind: eyesKind, look }),
      cheeks(cx, cy, R, base),
      mouth(cx, cy, R, base, mouthKind),
    ]),
  ];
}

/** 보호자 A: 짧은 옆가르마 머리, 동그란 안경 */
function guardianAHead(cx, cy, R, { mouthKind = "smile" } = {}) {
  const base = 33;
  const s = R / base;
  const at = (x, y) => [cx + x * s, cy + y * s];
  const hair = [
    [-33.5, 1], [-32, -18], [-23, -29.5], [-9, -35.5], [9, -35], [23, -29], [32.5, -17], [33.5, -1],
    [27, -11], [19, -17], [5, -16], [-9, -20.5], [-19, -17.5], [-27, -9],
  ].map(([x, y]) => at(x, y));
  return [
    G("head", [
      F("skin", multi(blob(...at(-32, 5), 6 * s, 6.5 * s, { n: 7 }), blob(...at(32, 5), 6 * s, 6.5 * s, { n: 7 })), { sw: sw(s) }),
      F("skin", blob(cx, cy, R, R * 0.92, { wob: 0.022, n: 12 }), { sw: sw(s) }),
      F("hairA", shape(hair, 0.5 * s), { sw: sw(s) }),
      S("line", retrace(cx, cy, R, R * 0.92, 205, 245), { sw: sw(s, 1.1), sa: 0.45 }),
      ...eyes(cx, cy, R, base, { dx: 12, dy: 4 }),
      S("line", multi(ellipse(...at(-12, 3.5), 8.6 * s), ellipse(...at(12, 3.5), 8.6 * s), `M${P(at(-3.4, 2))}Q${P(at(0, 0.6))} ${P(at(3.4, 2))}`), { sw: sw(s, 2) }),
      cheeks(cx, cy, R, base, 22, 15),
      mouth(cx, cy, R, base, mouthKind, 17),
    ]),
  ];
}

/** 보호자 B: 뒤로 내린 머리 + 정수리 똥머리 */
function guardianBHead(cx, cy, R, { mouthKind = "open" } = {}) {
  const base = 32;
  const s = R / base;
  const at = (x, y) => [cx + x * s, cy + y * s];
  const bangs = [
    [-31.5, -1], [-29, -18], [-19, -28.5], [-5, -32.5], [9, -31.5], [21, -26], [29.5, -16], [31.5, -1],
    [23, -12], [11, -16.5], [1, -13], [-7, -18.5], [-17, -14], [-25, -7],
  ].map(([x, y]) => at(x, y));
  return [
    G("hairBack", [
      F("hairB", blob(...at(0, 10), 35.5 * s, 38 * s, { wob: 0.03, n: 11 }), { sw: sw(s) }),
      F("hairB", blob(...at(0, -40), 11 * s, 10.5 * s, { n: 8 }), { sw: sw(s) }),
    ]),
    G("head", [
      F("skin", blob(cx, cy, R, R * 0.92, { wob: 0.022, n: 12 }), { sw: sw(s) }),
      F("hairB", shape(bangs, 0.5 * s), { sw: sw(s) }),
      F("shirtB", blob(...at(0, -30.5), 6.5 * s, 3.2 * s, { n: 7 }), { sw: sw(s, 1.8) }),
      S("line", retrace(cx, cy, R, R * 0.92, 300, 335), { sw: sw(s, 1.1), sa: 0.45 }),
      ...eyes(cx, cy, R, base, { dx: 11, dy: 4 }),
      cheeks(cx, cy, R, base, 20, 14),
      mouth(cx, cy, R, base, mouthKind, 14),
    ]),
  ];
}

// ---------------------------------------------------------------------------
// 5. 서 있는 캐릭터 (2등신). 관절 그룹: bob(몸 전체), armL/armR, legL/legR
// ---------------------------------------------------------------------------
function leg(name, hip, foot, shoeAt, s = 1) {
  return G(name, [
    S("limb", stroke([hip, [(hip[0] + foot[0]) / 2 + jitter(0.4), (hip[1] + foot[1]) / 2], foot]), { sw: 6.2 * s }),
    F("shoe", blob(shoeAt[0], shoeAt[1], 7.5 * s, 4.6 * s, { n: 8 }), { sw: 2.2 }),
  ], { px: hip[0], py: hip[1] });
}
function arm(name, shoulder, hand, s = 1) {
  return G(name, [
    S("limb", stroke([shoulder, [(shoulder[0] + hand[0]) / 2 + jitter(0.4), (shoulder[1] + hand[1]) / 2], hand]), { sw: 6 * s }),
    F("skin", blob(hand[0], hand[1] + 1.5, 5.2 * s, 5.2 * s, { n: 7 }), { sw: 2.2 }),
  ], { px: shoulder[0], py: shoulder[1] });
}

function child() {
  rnd = rng(11);
  const body = [[46, 89], [60, 87.5], [74, 89], [80, 104], [82.5, 121], [60, 124.5], [37.5, 121], [40, 104]];
  return {
    name: "child", w: 120, h: 150, title: "아이",
    nodes: [
      F("shadow", ellipse(60, 146, 27, 3.6), { outline: false, fa: 0.14 }),
      G("bob", [
        leg("legL", [52, 118], [50, 139], [47, 142]),
        leg("legR", [68, 118], [70, 139], [73, 142]),
        arm("armL", [45, 95], [34, 116]),
        arm("armR", [75, 95], [86, 116]),
        G("body", [
          F("shirtKid", shape(body, 0.5)),
          S("shade", hatch(62, 107, 17, 14, { gap: 4.6 }), { sw: 1.6, sa: 0.16 }),
          F("leaf", blob(54, 106, 5.2, 2.8, { deg: -30, n: 7 }), { sw: 1.6 }),
          S("leafShade", stroke([[50, 108.5], [58, 103.5]]), { sw: 1.1, sa: 0.8 }),
        ]),
        ...kidHead(60, 58, 30),
      ]),
    ],
  };
}

function guardianA() {
  rnd = rng(23);
  const body = [[44, 104], [65, 101], [86, 104], [93, 124], [95.5, 145], [65, 149], [34.5, 145], [37, 124]];
  return {
    name: "guardian_a", w: 130, h: 190, title: "보호자 A",
    nodes: [
      F("shadow", ellipse(65, 186, 31, 4), { outline: false, fa: 0.14 }),
      G("bob", [
        leg("legL", [56, 144], [53, 177], [50, 181], 1.05),
        leg("legR", [74, 144], [77, 177], [80, 181], 1.05),
        arm("armL", [42, 110], [30, 138], 1.05),
        arm("armR", [88, 110], [100, 138], 1.05),
        G("body", [
          F("shirtA", shape(body, 0.5)),
          S("shade", hatch(68, 126, 22, 18, { gap: 5 }), { sw: 1.7, sa: 0.16 }),
          S("line", stroke([[65, 104], [65, 146]]), { sw: 1.4, sa: 0.4 }),
          S("line", rpoly([[72, 118], [84, 118], [83.5, 129], [72.5, 129]], 2), { sw: 1.6, sa: 0.55 }),
        ]),
        ...guardianAHead(65, 64, 33),
      ]),
    ],
  };
}

function guardianB() {
  rnd = rng(37);
  const body = [[45, 104], [65, 101], [85, 104], [92, 126], [99, 149], [65, 153], [31, 149], [38, 126]];
  return {
    name: "guardian_b", w: 130, h: 190, title: "보호자 B",
    nodes: [
      F("shadow", ellipse(65, 186, 31, 4), { outline: false, fa: 0.14 }),
      G("bob", [
        leg("legL", [56, 147], [53, 177], [50, 181], 1.05),
        leg("legR", [74, 147], [77, 177], [80, 181], 1.05),
        arm("armL", [43, 110], [31, 138], 1.05),
        arm("armR", [87, 110], [99, 138], 1.05),
        ...guardianBHead(65, 64, 32).slice(0, 1),
        G("body", [
          F("shirtB", shape(body, 0.5)),
          S("shade", hatch(68, 128, 22, 19, { gap: 5 }), { sw: 1.7, sa: 0.16 }),
          S("line", stroke([[40, 133], [65, 136], [92, 133]]), { sw: 1.5, sa: 0.45 }),
        ]),
        ...guardianBHead(65, 64, 32).slice(1),
      ]),
    ],
  };
}

// ---------------------------------------------------------------------------
// 6. 가족 차 (옆모습, 오른쪽으로 달림). 창 안에 가족이 보인다. 창은 닫혀 있다(유리 반사).
//    지붕 화분은 진행 단계마다 자란다: 0% 도토리 → 25% 새싹 → 50% 큰 새싹 → 75% 어린 나무
//    stage: 0, 25, 50, 75
// ---------------------------------------------------------------------------
function car(stage) {
  rnd = rng(50 + stage);
  // 창을 크게: 아이 얼굴이 차 그림에서 가장 잘 보이는 것이 되도록 한다
  const bodyPts = [
    [26, 152], [15, 143], [12, 126], [14, 112], [24, 104], [38, 100], [44, 76], [54, 56], [70, 42], [96, 37],
    [146, 37], [166, 40], [182, 52], [198, 82], [210, 92], [224, 98], [231, 108], [232, 126], [229, 142], [218, 152],
    [180, 153], [120, 153], [60, 153],
  ];
  const rearWin = [[52, 99], [56, 75], [66, 57], [84, 45.5], [126, 45.5], [126, 99]];
  const frontWin = [[134, 45.5], [160, 45.5], [174, 55], [190, 84], [193, 99], [134, 99]];
  rnd = rng(500);
  const rearWinD = rpoly(rearWin, 7, 0.2);
  const frontWinD = rpoly(frontWin, 7, 0.2);
  rnd = rng(50 + stage);

  const plant = {
    0: [
      F("acorn", blob(113, 20, 4.6, 5.4, { n: 8 }), { sw: 1.7 }),
      F("acornCap", blob(113, 15.6, 5.8, 2.9, { n: 8 }), { sw: 1.7 }),
      S("line", stroke([[113, 13], [114, 10]]), { sw: 1.7 }),
    ],
    25: [
      S("leafShade", stroke([[113, 26], [113.3, 21], [113, 16]]), { sw: 2.4 }),
      F("leaf", blob(107.5, 15, 5.6, 3, { deg: -28, n: 8 }), { sw: 1.6 }),
      F("leaf", blob(118.5, 13, 6.2, 3.2, { deg: 25, n: 8 }), { sw: 1.6 }),
    ],
    50: [
      S("leafShade", stroke([[113, 26], [113.6, 17], [113.4, 8]]), { sw: 2.6 }),
      F("leaf", blob(110, 19, 4.6, 2.4, { deg: -35, n: 7 }), { sw: 1.5 }),
      F("leaf", blob(105, 9.5, 8.2, 4.1, { deg: -25, n: 8 }), { sw: 1.8 }),
      F("leaf", blob(121.5, 6.5, 8.6, 4.3, { deg: 22, n: 8 }), { sw: 1.8 }),
    ],
    75: [
      S("leafShade", stroke([[113, 26], [113.3, 16], [113, 8]]), { sw: 3 }),
      F("leaf", blob(105.5, 9.5, 7.2, 6.4, { n: 8 }), { sw: 1.8 }),
      F("leaf", blob(121, 8.5, 7.6, 6.6, { n: 8 }), { sw: 1.8 }),
      F("leaf", blob(113, 2.5, 8.2, 6.4, { n: 8 }), { sw: 1.8 }),
      S("leafShade", hatch(117, 7, 9, 6, { gap: 3, frac: 0.8 }), { sw: 1.1, sa: 0.5 }),
      F("sparkle", sparkle(113, -3.5, 3), { sw: 1.3 }),
    ],
  }[stage];

  // 아이 팔 (창 안). 어깨 (96,100)·(112,100)
  const handAt = (x, y, r = 4) => F("skin", blob(x, y, r, r, { n: 7 }), { sw: 1.8 });
  const armLine = (pts) => S("line", stroke(pts, 0.2), { sw: 3.8 });
  let kidArms;
  let kidFace;
  let outside = [];
  if (stage === 0) {
    // 출발! 만세
    kidFace = { eyesKind: "open", mouthKind: "open" };
    kidArms = [
      G("kidArmL", [armLine([[96, 100], [89.5, 84], [85.5, 70]]), handAt(85, 66.5)], { px: 96, py: 100 }),
      G("kidArm", [armLine([[112, 100], [118, 84], [121.5, 70]]), handAt(122, 66.5)], { px: 112, py: 100 }),
    ];
  } else if (stage === 25) {
    // 창밖 보기: 두 손을 닫힌 유리에 대고 바깥(위쪽)을 본다
    kidFace = { eyesKind: "open", look: [2.5, -2.5], mouthKind: "o" };
    kidArms = [
      G("kidArmL", [armLine([[96, 101], [91, 94], [88, 89.5]]), handAt(87, 87.5, 4.6)], { px: 96, py: 101 }),
      G("kidArm", [armLine([[112, 101], [117, 94], [120, 89.5]]), handAt(121, 87.5, 4.6)], { px: 112, py: 101 }),
    ];
  } else if (stage === 50) {
    // 절반! 손 흔들기 (흔드는 표시는 움직이지 않는 선)
    kidFace = { eyesKind: "open", mouthKind: "open" };
    kidArms = [
      G("kidArm", [armLine([[112, 100], [118, 83], [121, 67.5]]), handAt(121.5, 63.5, 4.3)], { px: 112, py: 100 }),
    ];
    outside = [S("motion", multi("M115.4,50.5Q112.4,56 115.4,61.5", "M129,51.5Q132,57 129,62.5"), { sw: 2, sa: 0.8 })];
  } else {
    // 거의 다 왔어! 손뼉, 눈웃음
    kidFace = { eyesKind: "happy", mouthKind: "open" };
    kidArms = [
      G("kidArmL", [armLine([[96, 102], [99, 97.5], [101.5, 94.5]]), handAt(101.5, 93, 4.2)], { px: 96, py: 102 }),
      G("kidArm", [armLine([[112, 102], [109, 97.5], [106.5, 94.5]]), handAt(106.5, 92.5, 4.2)], { px: 112, py: 102 }),
    ];
  }

  const kidHeadNodes = kidHead(104, 75, 17.5, kidFace);
  const bNodes = guardianBHead(71, 80, 15, { mouthKind: "smile" });
  const aNodes = guardianAHead(157, 77, 15.5);

  const sparkles = stage === 75
    ? [F("sparkle", multi(sparkle(34, 52, 7), sparkle(208, 58, 6), sparkle(160, 22, 4.5)), { sw: 1.6 })]
    : [];

  return {
    name: `car_stage${stage}`, w: 240, h: 206,
    title: { 0: "가족 차 · 0% 출발(만세)", 25: "가족 차 · 25% 창밖 보기", 50: "가족 차 · 50% 손 흔들기", 75: "가족 차 · 75% 거의 다 왔어(손뼉)" }[stage],
    nodes: [
      G("car", [
        F("shadow", ellipse(124, 170, 104, 5), { outline: false, fa: 0.14 }),
        G("roofPot", [
          S("limb", multi(stroke([[80, 47], [113, 46.6], [146, 47]]), "M88,47L88,52M138,47L138,52"), { sw: 3 }),
          F("pot", rpoly([[101, 30], [125, 30], [122, 46], [104, 46]], 2.5)),
          F("pot", rpoly([[98, 25.5], [128, 25.5], [128, 31.5], [98, 31.5]], 2.5)),
          F("soil", blob(113, 25.8, 11, 2.3, { n: 8 }), { sw: 1.6 }),
          G("plant", plant),
        ], { ty: -14 }),
        G("body", [
          F("carBody", shape(bodyPts, 0.45)),
          S("line", retrace(122, 95, 74, 58, 232, 300, 1.6), { sw: 1.1, sa: 0.4 }),
          S("shade", hatch(124, 142, 92, 9, { deg: -62, gap: 6, frac: 0.95, side: 0 }), { sw: 1.7, sa: 0.13 }),
          S("line", stroke([[130, 103], [130.5, 124], [130, 146]]), { sw: 1.5, sa: 0.4 }),
          S("line", multi(stroke([[106, 112], [116, 112]]), stroke([[148, 112], [158, 112]])), { sw: 3, sa: 0.75 }),
          F("leaf", blob(84, 127, 7.2, 3.8, { deg: -30, n: 8 }), { sw: 1.6 }),
          F("leaf", blob(94.5, 123.5, 5.2, 2.8, { deg: 22, n: 7 }), { sw: 1.5 }),
          S("leafShade", multi(stroke([[78.5, 130], [89, 124]]), stroke([[91, 125], [98, 122]])), { sw: 1.1, sa: 0.8 }),
          F("lamp", blob(224, 112, 4.6, 6, { n: 7 }), { sw: 2 }),
          F("tailLamp", blob(15.5, 121, 3.6, 7, { n: 7 }), { sw: 2 }),
          F("bumper", rpoly([[7, 136], [22, 136], [22, 144], [7, 144]], 3)),
          F("bumper", rpoly([[220, 134], [235, 134], [235, 142], [220, 142]], 3)),
        ]),
        G("rearWindow", [
          F("glass", rearWinD, { outline: false }),
          G("guardianB", [
            F("shirtB", blob(71, 101, 17, 10, { n: 8 }), { sw: 1.9 }),
            ...bNodes,
          ]),
          G("kid", [
            F("shirtKid", blob(104, 101, 17, 10, { n: 8 }), { sw: 1.9 }),
            ...kidHeadNodes,
            ...kidArms,
          ]),
          S("highlight", multi(stroke([[55.5, 96], [58, 88]]), stroke([[120.5, 49.5], [123.5, 55]])), { sw: 2.6, sa: 0.75 }),
        ], { clip: rearWinD }),
        G("frontWindow", [
          F("glass", frontWinD, { outline: false }),
          G("guardianA", [
            F("shirtA", blob(155, 101, 17, 10, { n: 8 }), { sw: 1.9 }),
            ...aNodes,
            S("line", ellipse(181, 90, 3.4, 10), { sw: 2.6 }),
            S("line", stroke([[164, 97], [171, 93.5], [177.5, 90.5]]), { sw: 3.4 }),
            F("skin", blob(178.5, 90.5, 3.4, 3.4, { n: 7 }), { sw: 1.7 }),
          ]),
          S("highlight", stroke([[177, 60], [184, 73]]), { sw: 2.6, sa: 0.7 }),
        ], { clip: frontWinD }),
        S("line", multi(rearWinD, frontWinD), { sw: 2.4 }),
        ...outside,
        G("wheelR", wheel(66, 156), { px: 66, py: 156 }),
        G("wheelF", wheel(186, 156), { px: 186, py: 156 }),
        ...sparkles,
      ], { ty: 24 }),
    ],
  };
}

function wheel(cx, cy) {
  return [
    F("tire", blob(cx, cy, 19, 19, { wob: 0.02, n: 10 })),
    F("hub", ellipse(cx, cy, 8), { sw: 1.6 }),
    S("spoke", `M${cx - 6.5},${cy}L${cx + 6.5},${cy}M${cx},${cy - 6.5}L${cx},${cy + 6.5}`, { sw: 2 }),
    F("tire", ellipse(cx, cy, 2.4), { outline: false }),
  ];
}

// ---------------------------------------------------------------------------
// 7. 숲: 새싹 → 어린 나무 → 나무 (3단계)
// ---------------------------------------------------------------------------
function sprout() {
  rnd = rng(61);
  return {
    name: "plant_sprout", w: 60, h: 64, title: "1단계 새싹",
    nodes: [
      F("soil", shape([[13, 60], [22, 53], [30, 51.5], [38, 53], [47, 60]], 0.4), { sw: 2 }),
      S("leafShade", stroke([[30, 54], [29.6, 44], [30.4, 35]]), { sw: 3 }),
      G("leaves", [
        F("leaf", blob(20.5, 33, 10.5, 5.6, { deg: -28, n: 9 }), { sw: 2.2 }),
        F("leaf", blob(40, 28.5, 11.5, 6.1, { deg: 25, n: 9 }), { sw: 2.2 }),
        S("leafShade", multi(stroke([[12.5, 37], [27, 30]]), stroke([[31.5, 32.5], [48.5, 24.5]])), { sw: 1.2, sa: 0.75 }),
      ], { px: 30, py: 36 }),
    ],
  };
}

function sapling() {
  rnd = rng(67);
  return {
    name: "plant_sapling", w: 80, h: 110, title: "2단계 어린 나무",
    nodes: [
      F("soil", shape([[15, 106], [28, 97], [40, 95], [52, 97], [65, 106]], 0.4), { sw: 2 }),
      F("trunk", shape([[36.5, 101], [37.5, 78], [36, 60], [40, 54], [44, 60], [42.5, 78], [43.5, 101]], 0.3), { sw: 2.2 }),
      F("leaf", blob(29, 79, 7, 3.6, { deg: -32, n: 8 }), { sw: 2 }),
      G("crown", [
        F("leaf", blob(27, 54, 15, 12, { n: 10 })),
        F("leaf", blob(53, 50, 16, 12.5, { n: 10 })),
        F("leaf", blob(40, 34, 17.5, 14, { n: 10 })),
        S("leafShade", multi(hatch(53, 52, 14, 10, { gap: 3.6 }), hatch(41, 37, 15, 11, { gap: 3.6 })), { sw: 1.3, sa: 0.45 }),
      ], { px: 40, py: 60 }),
    ],
  };
}

function tree() {
  rnd = rng(71);
  return {
    name: "plant_tree", w: 140, h: 170, title: "3단계 나무",
    nodes: [
      F("soil", shape([[30, 166], [50, 156], [70, 153.5], [90, 156], [110, 166]], 0.4), { sw: 2.2 }),
      F("trunk", shape([[60, 161], [62, 126], [56, 104], [62, 100], [68, 110], [73, 99], [80, 103], [76, 126], [80, 161]], 0.4)),
      S("shade", multi(stroke([[66, 156], [67, 136]]), stroke([[73, 150], [72.5, 128]])), { sw: 1.4, sa: 0.3 }),
      G("crown", [
        F("leaf", blob(38, 88, 26, 23, { n: 11 })),
        F("leaf", blob(102, 88, 26, 23, { n: 11 })),
        F("leaf", blob(70, 96, 24, 20, { n: 11 })),
        F("leaf", blob(48, 58, 28, 25, { n: 11 })),
        F("leaf", blob(93, 57, 27, 24, { n: 11 })),
        F("leaf", blob(70, 36, 30, 26, { n: 11 })),
        S("leafShade", multi(
          hatch(102, 90, 22, 18, { gap: 4.2 }),
          hatch(93, 60, 22, 19, { gap: 4.2 }),
          hatch(72, 98, 18, 14, { gap: 4.2 }),
        ), { sw: 1.4, sa: 0.42 }),
        F("leafDark", multi(ellipse(56, 44, 3.4), ellipse(84, 72, 3), ellipse(42, 80, 2.8), ellipse(98, 46, 2.6)), { outline: false, fa: 0.5 }),
        S("line", retrace(70, 36, 30, 26, 200, 260), { sw: 1.1, sa: 0.4 }),
      ], { px: 70, py: 110 }),
    ],
  };
}

// ---------------------------------------------------------------------------
// 8. 숲 동물 3종 (해금 순서: 다람쥐 → 토끼 → 새)
// ---------------------------------------------------------------------------
function squirrel() {
  rnd = rng(81);
  const tail = [[38, 88], [22, 84], [12, 70], [10, 52], [16, 34], [28, 22], [42, 20], [50, 28], [46, 38.5], [36, 40], [30, 50], [32, 64], [40, 74], [47, 82]];
  return {
    name: "animal_squirrel", w: 100, h: 100, title: "다람쥐 (도토리를 든)",
    nodes: [
      F("shadow", ellipse(54, 96, 30, 3.6), { outline: false, fa: 0.14 }),
      G("tail", [
        F("squirrel", shape(tail, 0.5)),
        S("shade", hatch(22, 56, 9, 22, { deg: -70, gap: 4.4, side: 0 }), { sw: 1.5, sa: 0.18 }),
        S("line", stroke([[24, 44], [30, 33], [40, 30]]), { sw: 1.4, sa: 0.45 }),
      ], { px: 40, py: 86 }),
      G("bob", [
        F("squirrel", blob(57, 74, 18, 20, { n: 10 })),
        F("belly", blob(63, 78, 10, 13, { n: 9 }), { outline: false }),
        F("squirrel", shape([[57, 37], [59.5, 23], [67, 34]], 0.3), { sw: 2.2 }),
        F("squirrel", blob(66, 46, 15.5, 13.8, { n: 10 })),
        F("rabbitInner", shape([[59.8, 34.5], [60.6, 27.5], [64.5, 34]], 0.2), { outline: false, fa: 0.8 }),
        F("belly", blob(77, 50, 6.4, 5.2, { n: 8 }), { outline: false }),
        F("feature", ellipse(82.4, 48, 2.1), { outline: false }),
        F("feature", ellipse(70, 44, 2.4, 3.1), { outline: false }),
        F("shine", ellipse(71, 42.8, 0.95), { outline: false }),
        F("cheek", ellipse(72.5, 53, 3.2, 2.2), { outline: false, fa: 0.5 }),
        F("squirrel", blob(64, 93, 8, 3.8, { n: 8 }), { sw: 2.2 }),
        G("acornHold", [
          F("acorn", blob(74, 73, 5.6, 6.6, { n: 8 }), { sw: 2 }),
          F("acornCap", blob(74, 67, 6.8, 3.3, { n: 8 }), { sw: 2 }),
          S("line", stroke([[74, 64], [75.5, 60.5]]), { sw: 2 }),
          F("squirrel", multi(blob(67.5, 71, 3.4, 3.4, { n: 7 }), blob(80.5, 71, 3.4, 3.4, { n: 7 })), { sw: 2 }),
        ]),
      ]),
    ],
  };
}

function rabbit() {
  rnd = rng(89);
  return {
    name: "animal_rabbit", w: 90, h: 110, title: "토끼 (한쪽 귀가 접힌)",
    nodes: [
      F("shadow", ellipse(46, 106, 28, 3.6), { outline: false, fa: 0.14 }),
      G("bob", [
        G("ears", [
          F("rabbit", blob(37, 22, 7.2, 20, { deg: -8, n: 10 })),
          F("rabbitInner", blob(37.3, 23, 3, 13, { deg: -8, n: 8 }), { outline: false }),
          F("rabbit", blob(60, 25, 7, 18, { deg: 38, n: 10 })),
          F("rabbitInner", blob(59.6, 25.6, 2.9, 11.5, { deg: 38, n: 8 }), { outline: false }),
        ], { px: 46, py: 40 }),
        F("rabbit", blob(70, 92, 7, 7, { n: 8 }), { sw: 2.2 }),
        F("rabbit", blob(46, 82, 24, 22, { n: 11 })),
        S("shade", hatch(52, 86, 18, 16, { gap: 4.4 }), { sw: 1.5, sa: 0.14 }),
        F("rabbit", blob(46, 53, 20.5, 17.5, { n: 11 })),
        S("line", retrace(46, 53, 20.5, 17.5, 205, 250), { sw: 1.1, sa: 0.45 }),
        F("feature", multi(ellipse(39, 52, 2.4, 3.1), ellipse(54, 52, 2.4, 3.1)), { outline: false }),
        F("shine", multi(ellipse(39.9, 50.8, 0.95), ellipse(54.9, 50.8, 0.95)), { outline: false }),
        F("rabbitInner", blob(46.5, 58, 2.8, 2.1, { n: 7 }), { outline: false }),
        S("feature", "M42.4,61.6Q44.4,63.8 46.5,60.6Q48.6,63.8 50.6,61.6", { sw: 1.6 }),
        F("cheek", multi(ellipse(33.5, 60, 3.6, 2.4), ellipse(59.5, 60, 3.6, 2.4)), { outline: false, fa: 0.5 }),
        F("rabbit", multi(blob(38, 99, 6, 4.6, { n: 8 }), blob(53, 99, 6, 4.6, { n: 8 })), { sw: 2.2 }),
        F("rabbit", blob(66, 102, 9, 4.4, { n: 8 }), { sw: 2.2 }),
      ]),
    ],
  };
}

function bird() {
  rnd = rng(97);
  return {
    name: "animal_bird", w: 80, h: 70, title: "새 (정수리 깃털 두 가닥)",
    nodes: [
      F("shadow", ellipse(38, 67, 18, 2.6), { outline: false, fa: 0.14 }),
      S("limb", "M34,57L33,64.5M42,57L43,64.5M30.5,65L35.5,65M39.5,65L45.5,65", { sw: 2.2 }),
      G("bob", [
        F("birdWing", shape([[18, 40], [5, 32], [7, 42], [3, 49], [16, 47]], 0.3), { sw: 2.2 }),
        F("bird", multi(
          `M40,23Q36,13 43,14Q42,18 44,22Z`,
          `M44,23Q46,12 52,17Q48,19 48,23Z`,
        ), { sw: 2 }),
        F("bird", blob(38, 40, 22, 19, { n: 11 })),
        F("birdBelly", blob(44, 47, 12.5, 10, { n: 9 }), { outline: false }),
        S("line", retrace(38, 40, 22, 19, 200, 250), { sw: 1.1, sa: 0.45 }),
        G("wing", [
          F("birdWing", blob(29, 42, 12.5, 8, { deg: -18, n: 9 }), { sw: 2.2 }),
          S("line", multi(stroke([[22, 44], [30, 41]]), stroke([[24, 48], [32, 45]])), { sw: 1.3, sa: 0.5 }),
        ], { px: 36, py: 38 }),
        F("feature", ellipse(48.5, 34, 2.5, 3), { outline: false }),
        F("shine", ellipse(49.4, 32.8, 0.95), { outline: false }),
        F("cheek", ellipse(52.5, 41.5, 3, 2), { outline: false, fa: 0.5 }),
        F("beak", rpoly([[58, 35.5], [68.5, 39], [58, 43]], 1.6, 0.1), { sw: 2 }),
      ]),
    ],
  };
}

// ---------------------------------------------------------------------------
// 9. 집(목적지), 깃발 이정표, 남은 시간 아이콘
// ---------------------------------------------------------------------------
function house() {
  rnd = rng(103);
  return {
    name: "house", w: 130, h: 120, title: "집 (목적지)",
    nodes: [
      F("shadow", ellipse(65, 116, 50, 3.6), { outline: false, fa: 0.14 }),
      F("door", rpoly([[86, 24], [98, 24], [98, 48], [86, 48]], 2)),
      F("wall", rpoly([[22, 56], [108, 56], [108, 113], [22, 113]], 5)),
      S("shade", hatch(84, 92, 22, 18, { gap: 5 }), { sw: 1.6, sa: 0.12 }),
      F("roof", rpoly([[10, 63], [65, 15], [120, 63]], 7)),
      S("shade", hatch(78, 46, 26, 12, { deg: -50, gap: 5.5 }), { sw: 1.8, sa: 0.18 }),
      F("heart", "M65,52.5C59,47.5 57,43 61,40.5C63.2,39.2 64.6,40.6 65,42C65.4,40.6 66.8,39.2 69,40.5C73,43 71,47.5 65,52.5Z", { sw: 1.8 }),
      F("door", rpoly([[54, 113], [54, 86], [65, 79], [76, 86], [76, 113]], 4)),
      F("feature", ellipse(71, 99, 1.8), { outline: false }),
      F("window", multi(rpoly([[30, 72], [48, 72], [48, 89], [30, 89]], 3), rpoly([[83, 72], [101, 72], [101, 89], [83, 89]], 3))),
      S("line", "M39,72.5L39,88.5M30.5,80.5L47.5,80.5M92,72.5L92,88.5M83.5,80.5L100.5,80.5", { sw: 1.8 }),
    ],
  };
}

function flag() {
  rnd = rng(107);
  return {
    name: "flag", w: 44, h: 70, title: "깃발 이정표 (10%마다)",
    nodes: [
      F("shadow", ellipse(20, 65, 9, 3), { outline: false, fa: 0.18 }),
      S("limb", stroke([[20, 65], [20.4, 38], [20, 11]], 0.25), { sw: 3 }),
      F("flagCloth", shape([[21, 12], [31, 13.5], [39.5, 16.5], [34.5, 21], [40, 26.5], [30, 28], [21, 29]], 0.35), { sw: 2.2 }),
      F("lamp", ellipse(20, 8.5, 3.2), { sw: 1.8 }),
    ],
  };
}

function iconCartoon() {
  rnd = rng(113);
  return {
    name: "icon_cartoon", w: 48, h: 48, title: "아이콘 · 만화(편)",
    nodes: [
      S("limb", "M18,13L13,5M30,13L35,5", { sw: 2.4 }),
      F("wall", rpoly([[5, 13], [43, 13], [43, 39], [5, 39]], 5)),
      F("screen", rpoly([[10, 17.5], [38, 17.5], [38, 34.5], [10, 34.5]], 3), { sw: 1.8 }),
      F("shine", "M20.5,21.5L29.5,26L20.5,30.5Z", { outline: false }),
      S("limb", "M14,39L12,44M34,39L36,44", { sw: 2.4 }),
    ],
  };
}

function iconSong() {
  rnd = rng(127);
  return {
    name: "icon_song", w: 48, h: 48, title: "아이콘 · 노래(곡)",
    nodes: [
      S("limb", "M17,35L17,10L38,6L38,31", { sw: 2.6 }),
      S("limb", "M17,15L38,11", { sw: 4 }),
      F("screen", multi(blob(12.5, 35.5, 6, 4.8, { deg: -18, n: 8 }), blob(33.5, 31.5, 6, 4.8, { deg: -18, n: 8 }))),
    ],
  };
}

// ---------------------------------------------------------------------------
// 10. 내보내기
// ---------------------------------------------------------------------------
function colorOf(role, theme) {
  const c = ROLES[role];
  if (!c) throw new Error(`unknown role ${role}`);
  return theme === "dark" ? c[1] : c[0];
}

function svgTransform(g) {
  const px = g.px ?? 0;
  const py = g.py ?? 0;
  const tx = g.tx ?? 0;
  const ty = g.ty ?? 0;
  const r = g.rot ?? 0;
  if (!r && !tx && !ty) return "";
  if (!r) return ` transform="translate(${R1(tx)} ${R1(ty)})"`;
  return ` transform="translate(${R1(tx + px)} ${R1(ty + py)}) rotate(${R1(r)}) translate(${R1(-px)} ${R1(-py)})"`;
}

function toSvg(art, { theme = "light", poses = {}, overrides = {} } = {}) {
  const color = (role, th) => overrides[role] ?? colorOf(role, th);
  let clipN = 0;
  const defs = [];
  const used = new Map();
  const uid = (name) => {
    const k = (used.get(name) ?? 0) + 1;
    used.set(name, k);
    return k === 1 ? name : `${name}-${k}`;
  };
  const prefix = `${art.name}-${theme}`;
  const walk = (nodes, depth) => nodes.filter((n) => n.k === "g" || n.d).map((n) => {
    const ind = "  ".repeat(depth);
    if (n.k === "g") {
      const pose = poses[n.name] || {};
      const g = { ...n, rot: (n.rot ?? 0) + (pose.rot ?? 0), ty: (n.ty ?? 0) + (pose.ty ?? 0), tx: (n.tx ?? 0) + (pose.tx ?? 0) };
      let clip = "";
      if (n.clip) {
        const id = `${prefix}-clip${++clipN}`;
        defs.push(`<clipPath id="${id}"><path d="${n.clip}"/></clipPath>`);
        clip = ` clip-path="url(#${id})"`;
      }
      return `${ind}<g id="${uid(n.name)}"${svgTransform(g)}>\n${clip ? `${ind}  <g${clip}>\n` : ""}${walk(n.children, depth + (clip ? 2 : 1))}\n${clip ? `${ind}  </g>\n` : ""}${ind}</g>`;
    }
    const attrs = [`data-role="${n.role}"`];
    if (n.fill) {
      attrs.push(`fill="${color(n.role, theme)}"`);
      if (n.fa != null) attrs.push(`fill-opacity="${n.fa}"`);
      if (n.outline) {
        attrs.push(`stroke="${color(n.role === "feature" ? "feature" : "line", theme)}"`, `stroke-width="${R1(n.sw)}"`, `stroke-linejoin="round"`, `stroke-linecap="round"`);
        if (n.sa != null) attrs.push(`stroke-opacity="${n.sa}"`);
      }
    } else {
      attrs.push(`fill="none"`, `stroke="${color(n.role, theme)}"`, `stroke-width="${R1(n.sw)}"`, `stroke-linejoin="round"`, `stroke-linecap="round"`);
      if (n.sa != null) attrs.push(`stroke-opacity="${n.sa}"`);
    }
    return `${ind}<path ${attrs.join(" ")} d="${n.d}"/>`;
  }).join("\n");
  const body = walk(art.nodes, 1);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${art.w} ${art.h}" width="${art.w}" height="${art.h}">\n  <title>${art.title}</title>\n${defs.length ? `  <defs>${defs.join("")}</defs>\n` : ""}${body}\n</svg>\n`;
}

function toVectorDrawable(art) {
  const color = colorOf;
  const walk = (nodes, depth) => nodes.filter((n) => n.k === "g" || n.d).map((n) => {
    const ind = "    ".repeat(depth);
    if (n.k === "g") {
      const a = [`android:name="${n.name}"`];
      if (n.px != null) a.push(`android:pivotX="${R1(n.px)}"`);
      if (n.py != null) a.push(`android:pivotY="${R1(n.py)}"`);
      if (n.rot) a.push(`android:rotation="${R1(n.rot)}"`);
      if (n.tx) a.push(`android:translateX="${R1(n.tx)}"`);
      if (n.ty) a.push(`android:translateY="${R1(n.ty)}"`);
      const clip = n.clip ? `${ind}    <clip-path android:name="clip" android:pathData="${n.clip}" />\n` : "";
      return `${ind}<group ${a.join(" ")}>\n${clip}${walk(n.children, depth + 1)}\n${ind}</group>`;
    }
    const a = [`android:name="${n.role}"`, `android:pathData="${n.d}"`];
    if (n.fill) {
      a.push(`android:fillColor="${color(n.role, "light")}"`);
      if (n.fa != null) a.push(`android:fillAlpha="${n.fa}"`);
      if (n.outline) {
        a.push(`android:strokeColor="${color(n.role === "feature" ? "feature" : "line", "light")}"`, `android:strokeWidth="${R1(n.sw)}"`, `android:strokeLineJoin="round"`, `android:strokeLineCap="round"`);
        if (n.sa != null) a.push(`android:strokeAlpha="${n.sa}"`);
      }
    } else {
      a.push(`android:strokeColor="${color(n.role, "light")}"`, `android:strokeWidth="${R1(n.sw)}"`, `android:strokeLineJoin="round"`, `android:strokeLineCap="round"`);
      if (n.sa != null) a.push(`android:strokeAlpha="${n.sa}"`);
    }
    return `${ind}<path\n${ind}    ${a.join(`\n${ind}    `)} />`;
  }).join("\n");
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- 자동 생성: design/characters/tools/build-assets.mjs (직접 고치지 마세요). ${art.title} -->
<!-- path 이름 = 색 역할. Compose(core:ui GgfArt)가 이름으로 테마 색·꾸미기 색을 덮어씁니다. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:name="ggf_${art.name}"
    android:width="${art.w}dp"
    android:height="${art.h}dp"
    android:viewportWidth="${art.w}"
    android:viewportHeight="${art.h}">
${walk(art.nodes, 1)}
</vector>
`;
}

function toKotlin() {
  const hex = (h) => `0xFF${h.slice(1).toUpperCase()}`;
  const entries = (i) => Object.entries(ROLES).map(([k, v]) => `        "${k}" to ${hex(v[i])},`).join("\n");
  return `// 자동 생성: design/characters/tools/build-assets.mjs (직접 고치지 마세요)
package app.familyjourney.core.ui

/**
 * 그림(VectorDrawable)의 색 역할 표.
 *
 * drawable 안의 path 이름이 역할 이름이다(예: "skin", "carBody").
 * [GgfArt]는 이 표에서 라이트/다크 색을 골라 path 색을 덮어쓴다.
 * [selfStroke]에 든 역할은 선 색을 스스로 갖고, 나머지 채움 역할의 바깥선은 "line" 색을 쓴다.
 */
internal object ArtRoleTokens {
    val light: Map<String, Long> = mapOf(
${entries(0)}
    )

    val dark: Map<String, Long> = mapOf(
${entries(1)}
    )

    val selfStroke: Set<String> = setOf(${[...SELF_STROKE].map((s) => `"${s}"`).join(", ")})
}
`;
}

export const ARTS = [
  guardianA, guardianB, child,
  () => car(0), () => car(25), () => car(50), () => car(75),
  sprout, sapling, tree,
  squirrel, rabbit, bird,
  house, flag,
  iconCartoon, iconSong,
];

export function buildAll() {
  return ARTS.map((f) => f());
}
export { toSvg };

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  fs.mkdirSync(SVG_DIR, { recursive: true });
  fs.mkdirSync(RES_DIR, { recursive: true });
  for (const art of buildAll()) {
    fs.writeFileSync(path.join(SVG_DIR, `${art.name}.svg`), toSvg(art));
    fs.writeFileSync(path.join(RES_DIR, `ggf_${art.name}.xml`), toVectorDrawable(art));
    console.log(`ok  ${art.name}`);
  }
  fs.writeFileSync(KT_FILE, toKotlin());
  console.log(`ok  ${path.relative(REPO, KT_FILE)}`);
}
