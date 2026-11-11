// 같이가숲 화면 시안 공용 도구
//
// - 색 토큰: core/ui Tokens.kt와 같은 값(라이트/다크)
// - 캐릭터 그림: design/characters/tools/build-assets.mjs의 SVG를 그대로 쓴다(색만 바꿀 때는 역할 색 덮어쓰기)
// - 진행 길: design/characters/tools/render-layouts.mjs(= core:ui RoadGeometry.kt)와 같은 계산
// - 새로 그린 것(꽃, 랜드마크 스티커, QR 무늬, 지도 바탕, 선 아이콘)은 이 파일의 함수가 SVG로 만든다
//
// 실행은 render-mockups.mjs가 한다. 이 파일은 함수만 내보낸다.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildAll, toSvg } from "../../characters/tools/build-assets.mjs";

export const HERE = path.dirname(fileURLToPath(import.meta.url));
export const MOCK_DIR = path.resolve(HERE, "..");
export const HTML_DIR = path.join(MOCK_DIR, "html");
export const ASSET_DIR = path.join(HTML_DIR, "assets");
export const SVG_DIR = path.join(MOCK_DIR, "svg");
export const PNG_DIR = path.join(MOCK_DIR, "png");
export const FONT_DIR = path.join(MOCK_DIR, ".font-cache");

// ---------------------------------------------------------------------------
// 1. 색 토큰 (Tokens.kt 그대로 + 시안 틀 전용 색)
// ---------------------------------------------------------------------------
export const TOKENS = {
  light: {
    paper: "#F5FAF3", card: "#FFFFFF", ink: "#1D2A22", ink2: "#4B5C51", line: "#D3E2D4",
    forest: "#2B774A", onForest: "#FFFFFF", leaf: "#6BB85A", leafSoft: "#E3F3DC",
    sky: "#3F92D2", skyText: "#2A6FA8", skySoft: "#DDEEFB", sun: "#F0AE34", sunSoft: "#FFF0CC",
    berry: "#D25A49", berryText: "#B0402F", berrySoft: "#FBE2DD", demo: "#7D5FD0", onDemo: "#FFFFFF",
    sceneSky: "#DCEFFC", hillBack: "#C3E5B4", hillFront: "#9DD08A",
    road: "#D8D4C8", roadEdge: "#B9B4A6", roadLine: "#FFFFFF", trail: "#2B774A", flagIdle: "#FFFFFF",
    // 시안 틀(앱 밖)
    canvas: "#E9EEEA", canvasInk: "#1D2A22", canvasInk2: "#4B5C51", bezel: "#1E2421",
    drv: "#E6EAE7", drvInk: "#3E4A43", map: "#E9EEE6", mapBlock: "#DCE4DA", mapRoad: "#FFFFFF",
    mapRoute: "#4F86B8", mapWater: "#CBE1EE", mapPark: "#D2E6C9", pageNote: "#FFFFFF",
  },
  dark: {
    paper: "#0F1713", card: "#17221C", ink: "#E3EEE6", ink2: "#A8BBAE", line: "#2A3B31",
    forest: "#6FCB8E", onForest: "#0F1713", leaf: "#5DAE50", leafSoft: "#1C3222",
    sky: "#6BB3EA", skyText: "#6BB3EA", skySoft: "#172B3A", sun: "#F2BE57", sunSoft: "#3A2F15",
    berry: "#F08676", berryText: "#F08676", berrySoft: "#3C201C", demo: "#A98FF2", onDemo: "#0F1713",
    sceneSky: "#13283A", hillBack: "#1E3B29", hillFront: "#26502F",
    road: "#343B38", roadEdge: "#56605B", roadLine: "#C9CFCB", trail: "#6FCB8E", flagIdle: "#8A958F",
    canvas: "#070B09", canvasInk: "#E3EEE6", canvasInk2: "#A8BBAE", bezel: "#000000",
    drv: "#141A17", drvInk: "#B7C4BB", map: "#18201C", mapBlock: "#1F2924", mapRoad: "#3A4540",
    mapRoute: "#6E9FCC", mapWater: "#16303F", mapPark: "#1B3122", pageNote: "#111A15",
  },
};

/** WCAG 대비 비율 */
export function contrast(a, b) {
  const lum = (hex) => {
    const n = parseInt(hex.slice(1), 16);
    const ch = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v) => {
      const c = v / 255;
      return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2];
  };
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
}

// ---------------------------------------------------------------------------
// 2. 캐릭터 그림 (build-assets.mjs) → html/assets/*.svg
// ---------------------------------------------------------------------------
const ARTS = Object.fromEntries(buildAll().map((a) => [a.name, a]));
const written = new Set();

export function resetAssets() {
  fs.rmSync(ASSET_DIR, { recursive: true, force: true });
  fs.mkdirSync(ASSET_DIR, { recursive: true });
  written.clear();
}

/** 캐릭터 SVG 파일을 만들고 html/ 기준 상대 경로를 돌려준다. tag는 poses를 쓸 때 파일 이름 구분용 */
export function art(name, theme = "light", { overrides = {}, poses = {}, tag = "" } = {}) {
  if (!ARTS[name]) throw new Error(`unknown art ${name}`);
  const keys = Object.entries(overrides).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => `${k}${v.replace("#", "")}`);
  const file = [name, theme, tag, ...keys].filter(Boolean).join("-") + ".svg";
  if (!written.has(file)) {
    fs.writeFileSync(path.join(ASSET_DIR, file), toSvg(ARTS[name], { theme, overrides, poses }));
    written.add(file);
  }
  return `assets/${file}`;
}
export const artSize = (name) => [ARTS[name].w, ARTS[name].h];

/** 일행 차: 창 안의 우리 가족을 창 밖으로 옮겨(창 모양으로 잘려 안 보임) 짙은 유리만 남긴 차 */
export function friendCar(theme, stage, body) {
  const away = { ty: 400 };
  return art(`car_stage${stage}`, theme, {
    overrides: { carBody: body, glass: theme === "dark" ? "#5C7486" : "#9DB6C9" },
    poses: { guardianA: away, guardianB: away, kid: away },
    tag: "friend",
  });
}

export const carStage = (pct) => (pct < 25 ? 0 : pct < 50 ? 25 : pct < 75 ? 50 : 75);

// ---------------------------------------------------------------------------
// 3. 진행 길 (render-layouts.mjs와 같은 계산을 Node에서)
// ---------------------------------------------------------------------------
const CAR_ASPECT = 240 / 206, CAR_GROUND = 197 / 206, HOUSE_ASPECT = 130 / 120, HOUSE_GROUND = 116 / 120, FLAG_ASPECT = 44 / 70;
const f1 = (n) => n.toFixed(1);

function line(x0, y0, x1, y1) {
  const len = Math.hypot(x1 - x0, y1 - y0);
  return { len, at: (d) => { const t = len ? Math.min(1, Math.max(0, d / len)) : 0; return { x: x0 + (x1 - x0) * t, y: y0 + (y1 - y0) * t, right: x1 >= x0 }; } };
}
function arc(cx, cy, r, s, sw, turnRight) {
  const len = Math.abs(sw) * Math.PI / 180 * r;
  return { len, at: (d) => { const t = len ? Math.min(1, Math.max(0, d / len)) : 0; const a = (s + sw * t) * Math.PI / 180; return { x: cx + r * Math.cos(a), y: cy + r * Math.sin(a), right: turnRight ? t < 0.5 : t >= 0.5 }; } };
}
function geom(segs) {
  const L = segs.reduce((a, s) => a + s.len, 0);
  return { L, at(fr) { let d = Math.min(1, Math.max(0, fr)) * L; for (const s of segs) { if (d <= s.len) return s.at(d); d -= s.len; } const s = segs[segs.length - 1]; return s.at(s.len); } };
}
function roadLayout(w, h, carScale) {
  if (h >= w * 0.75) {
    const rows = Math.min(6, Math.max(2, Math.ceil(h / (w * 0.55)))); const band = h / rows, r = band / 2;
    let carH = band * 0.55 * carScale, carW = carH * CAR_ASPECT; if (carW > w * 0.3) { carW = w * 0.3; carH = carW / CAR_ASPECT; }
    const roadW = Math.max(band * 0.085, 6), flagH = Math.min(band * 0.3, carH * 0.62), edge = Math.max(roadW / 2, flagH * FLAG_ASPECT * 0.6) + 2, left = r + edge, right = Math.max(left + 1, w - r - edge), firstY = band * 0.74;
    const houseH = carH * 0.9, houseW = houseH * HOUSE_ASPECT, lastRight = (rows - 1) % 2 === 0;
    const inset = lastRight ? Math.max(0, right - (w - houseW - carW * 0.3)) : Math.max(0, houseW + carW * 0.3 - left);
    const segs = [];
    for (let i = 0; i < rows; i++) {
      const y = firstY + i * band, gr = i % 2 === 0; const xs = gr ? left : right; let xe = gr ? right : left;
      if (i === rows - 1) xe = gr ? Math.max(xs + 1, xe - inset) : Math.min(xs - 1, xe + inset);
      segs.push(line(xs, y, xe, y)); if (i < rows - 1) segs.push(gr ? arc(right, y + r, r, -90, 180, true) : arc(left, y + r, r, 270, -180, false));
    }
    const g = geom(segs), end = g.at(1), houseLeft = lastRight ? end.x + carW * 0.3 : end.x - carW * 0.3 - houseW;
    return { w, h, g, roadW, carW, carH, flagH, houseW, houseH, houseLeft, houseTop: end.y + roadW * 0.25 - houseH * HOUSE_GROUND, startX: Math.max(roadW / 2 + 1, left - carW * 0.45), endX: houseLeft + houseW * 0.5 };
  }
  const used = Math.min(h, w / 2.4), top = (h - used) / 2; let carH = used * 0.56 * carScale, carW = carH * CAR_ASPECT; if (carW > w * 0.28) { carW = w * 0.28; carH = carW / CAR_ASPECT; }
  const roadW = Math.max(used * 0.12, 6), houseH = carH * 0.9, houseW = houseH * HOUSE_ASPECT, roadY = top + used * 0.8, x0 = carW * 0.5 + 2, x1 = Math.max(x0 + 1, w - houseW - carW * 0.3);
  return { w, h, g: geom([line(x0, roadY, x1, roadY)]), roadW, carW, carH, flagH: Math.min(used * 0.3, carH * 0.62), houseW, houseH, houseLeft: w - houseW, houseTop: roadY + roadW * 0.25 - houseH * HOUSE_GROUND, startX: roadW / 2 + 1, endX: w - houseW + houseW * 0.45 };
}
function pts(l, a, b) { const out = []; const n = Math.max(1, Math.ceil((b - a) * l.g.L / 4)); for (let i = 0; i <= n; i++) { const p = l.g.at(a + (b - a) * i / n); out.push(f1(p.x) + "," + f1(p.y)); } return out; }

/**
 * 진행 길 SVG. 앱의 ProgressRoad와 같은 모양.
 * cars: [{ pct, href, label?, scale?, mine? }] 첫 번째가 지나온 길(초록)의 기준이다.
 */
export function roadSvg(theme, w, h, { pct, cars, flags = true, carScale = 1 }) {
  const t = TOKENS[theme];
  const l = roadLayout(w, h, carScale), fr = pct / 100, s = l.g.at(0), e = l.g.at(1);
  const all = [s.x === l.startX ? null : f1(l.startX) + "," + f1(s.y), ...pts(l, 0, 1), f1(l.endX) + "," + f1(e.y)].filter(Boolean).join(" ");
  const rest = [...pts(l, fr, 1), f1(l.endX) + "," + f1(e.y)].join(" ");
  const passed = [f1(l.startX) + "," + f1(s.y), ...pts(l, 0, fr)].join(" ");
  const rw = l.roadW;
  const flag = art("flag", theme), flagIdle = art("flag", theme, { overrides: { flagCloth: t.flagIdle } }), house = art("house", theme);
  let svg = `<svg class="roadsvg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" aria-hidden="true"><g fill="none" stroke-linecap="round" stroke-linejoin="round">`
    + `<polyline points="${all}" stroke="${t.roadEdge}" stroke-width="${f1(rw + Math.max(3, rw * 0.22))}"/>`
    + `<polyline points="${all}" stroke="${t.road}" stroke-width="${f1(rw)}"/>`
    + `<polyline points="${rest}" stroke="${t.roadLine}" stroke-width="${f1(Math.max(2, rw * 0.14))}" stroke-dasharray="${f1(rw * 0.55)} ${f1(rw * 0.75)}"/>`
    + `<polyline points="${passed}" stroke="${t.trail}" stroke-width="${f1(rw * 0.42)}"/></g>`
    + `<circle cx="${f1(l.startX)}" cy="${f1(s.y)}" r="${f1(rw * 0.42)}" fill="${t.trail}"/>`
    + `<image href="${house}" x="${f1(l.houseLeft)}" y="${f1(l.houseTop)}" width="${f1(l.houseW)}" height="${f1(l.houseH)}"/>`;
  if (flags) {
    const fw = l.flagH * FLAG_ASPECT;
    for (let m = 10; m < 100; m += 10) {
      const p = l.g.at(m / 100), bottom = p.y - rw / 2 - 1, top = bottom - l.flagH * 65 / 70;
      svg += `<image href="${pct >= m ? flag : flagIdle}" x="${f1(p.x - fw * 20 / 44)}" y="${f1(top)}" width="${f1(fw)}" height="${f1(l.flagH)}"/>`;
    }
  }
  // 뒤에 오는 차부터 그려서 앞선 차가 위에 오게
  const order = [...cars].sort((a, b) => a.pct - b.pct);
  for (const c of order) {
    const sc = c.scale ?? 1, cw = l.carW * sc, ch = l.carH * sc;
    const p = l.g.at(c.pct / 100);
    const cx = Math.min(Math.max(0, p.x - cw / 2), w - cw), cy = Math.min(Math.max(0, p.y + rw * 0.15 - ch * CAR_GROUND), h - ch);
    const flip = p.right ? "" : ` transform="translate(${f1(2 * cx + cw)} 0) scale(-1 1)"`;
    svg += `<image href="${c.href}" x="${f1(cx)}" y="${f1(cy)}" width="${f1(cw)}" height="${f1(ch)}"${flip}/>`;
    if (c.label) {
      const fs = c.labelSize ?? 20, lw = c.label.length * fs * 0.95 + fs * 1.3, lh = fs * 1.7;
      const lx = Math.min(Math.max(2, cx + cw / 2 - lw / 2), w - lw - 2), ly = Math.max(2, cy - lh + ch * 0.12);
      svg += `<rect x="${f1(lx)}" y="${f1(ly)}" width="${f1(lw)}" height="${f1(lh)}" rx="${f1(lh / 2)}" fill="${c.mine ? t.forest : t.card}" stroke="${c.mine ? t.forest : t.ink2}" stroke-width="2"/>`
        + `<text x="${f1(lx + lw / 2)}" y="${f1(ly + lh * 0.68)}" text-anchor="middle" font-size="${fs}" font-weight="800" fill="${c.mine ? t.onForest : t.ink}">${c.label}</text>`;
    }
  }
  return svg + "</svg>";
}

// ---------------------------------------------------------------------------
// 4. 손그림 도구 (고정 시드: 다시 만들어도 같은 그림)
// ---------------------------------------------------------------------------
function rng(seed) { let s = seed >>> 0; return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
const P = (p) => `${f1(p[0])},${f1(p[1])}`;
function closedSmooth(ps) {
  const n = ps.length; let d = `M${P(ps[0])}`;
  for (let i = 0; i < n; i++) {
    const p0 = ps[(i - 1 + n) % n], p1 = ps[i], p2 = ps[(i + 1) % n], p3 = ps[(i + 2) % n];
    d += `C${P([p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6])} ${P([p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6])} ${P(p2)}`;
  }
  return d + "Z";
}
function openSmooth(ps) {
  let d = `M${P(ps[0])}`;
  for (let i = 0; i < ps.length - 1; i++) {
    const p0 = ps[Math.max(0, i - 1)], p1 = ps[i], p2 = ps[i + 1], p3 = ps[Math.min(ps.length - 1, i + 2)];
    d += `C${P([p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6])} ${P([p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6])} ${P(p2)}`;
  }
  return d;
}
/** 조금 흔들린 타원 */
export function blob(cx, cy, rx, ry, seed, { n = 10, wob = 0.035, deg = 0 } = {}) {
  const r = rng(seed), a0 = deg * Math.PI / 180, ps = [];
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2, k = 1 + (r() - 0.5) * 2 * wob;
    const x = rx * k * Math.cos(a), y = ry * k * Math.sin(a);
    ps.push([cx + x * Math.cos(a0) - y * Math.sin(a0), cy + x * Math.sin(a0) + y * Math.cos(a0)]);
  }
  return closedSmooth(ps);
}
/** 꼭짓점을 조금 흔든 다각형(모서리 둥글게) */
export function shape(points, seed, amp = 0.6) {
  const r = rng(seed);
  return closedSmooth(points.map(([x, y]) => [x + (r() - 0.5) * amp * 2, y + (r() - 0.5) * amp * 2]));
}
export function wline(points, seed, amp = 0.4) {
  const r = rng(seed);
  return openSmooth(points.map(([x, y], i) => (i === 0 || i === points.length - 1 ? [x, y] : [x + (r() - 0.5) * amp * 2, y + (r() - 0.5) * amp * 2])));
}
/** 크레파스 결(옅은 빗금) */
function hatch(x0, y0, x1, y1, gap = 6) {
  let d = "";
  for (let x = x0 - (y1 - y0); x < x1; x += gap) d += `M${f1(Math.max(x0, x))},${f1(x < x0 ? y0 + (x0 - x) : y0)}L${f1(Math.min(x1, x + (y1 - y0)))},${f1(x + (y1 - y0) > x1 ? y1 - (x + (y1 - y0) - x1) : y1)}`;
  return d;
}

const LINE = "#2B2622";
const fillP = (d, fill, sw = 2.4, extra = "") => `<path d="${d}" fill="${fill}" stroke="${LINE}" stroke-width="${sw}" stroke-linejoin="round" stroke-linecap="round"${extra}/>`;
const strokeP = (d, color = LINE, sw = 2.4, extra = "") => `<path d="${d}" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linejoin="round" stroke-linecap="round"${extra}/>`;

// ---------------------------------------------------------------------------
// 5. 새로 그린 그림 (svg/ 원본으로도 저장)
// ---------------------------------------------------------------------------
/** 꽃 (숲 꾸미기용). petal: 꽃잎 색 */
export function flowerSvg(petal = "#E86A5A", center = "#F0AE34", seed = 7) {
  const petals = [0, 72, 144, 216, 288].map((a, i) => {
    const rad = (a - 90) * Math.PI / 180;
    return fillP(blob(30 + Math.cos(rad) * 9.5, 26 + Math.sin(rad) * 9.5, 7.6, 6.4, seed + i, { deg: a, n: 8 }), petal, 2);
  }).join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 80" width="60" height="80"><title>꽃</title>`
    + `<ellipse cx="30" cy="76" rx="15" ry="2.6" fill="${LINE}" fill-opacity=".14"/>`
    + strokeP(wline([[30, 76], [30.6, 58], [30, 36]], seed), "#2B774A", 3.2)
    + fillP(blob(21, 60, 8.5, 4, seed + 10, { deg: 28, n: 8 }), "#6BB85A", 1.8)
    + fillP(blob(39.5, 54, 8.5, 4, seed + 11, { deg: -28, n: 8 }), "#6BB85A", 1.8)
    + petals
    + fillP(blob(30, 26, 6, 6, seed + 20, { n: 8 }), center, 2)
    + strokeP("M27.5,24.5L28.5,25.5M32,27.5L33,28.5", LINE, 1.4, ' stroke-opacity=".5"')
    + `</svg>`;
}

/** 랜드마크 스티커 공통 틀(흰 테두리 스티커) */
function sticker(title, seed, inner) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120"><title>${title}</title>`
    + fillP(blob(60, 60, 56, 56, seed, { n: 14, wob: 0.02 }), "#FFFFFF", 2)
    + `<g>${inner}</g>`
    + `</svg>`;
}
const skyDisk = (seed) => `<path d="${blob(60, 60, 47, 47, seed + 1, { n: 14, wob: 0.015 })}" fill="#DDEEFB"/>`;

export function landmarkTowerSvg() {
  const s = 101;
  return sticker("남산서울타워", s, skyDisk(s)
    + fillP(shape([[16, 94], [32, 80], [48, 74], [62, 73], [78, 76], [94, 84], [104, 96], [60, 107], [22, 102]], s + 2, 0.8), "#9DD08A", 2.2)
    + strokeP(hatch(30, 82, 94, 102, 7), "#2B774A", 1.2, ' stroke-opacity=".35"')
    + fillP(shape([[56.5, 78], [63.5, 78], [62.4, 45], [57.6, 45]], s + 3, 0.3), "#F7F7F2", 2.2)
    + fillP(shape([[48.5, 45.5], [71.5, 45.5], [69.5, 36], [50.5, 36]], s + 4, 0.3), "#D9DCD6", 2.2)
    + strokeP("M51.5,40.8L68.5,40.8", LINE, 1.4, ' stroke-opacity=".55"')
    + fillP(shape([[54, 36], [66, 36], [63.5, 30.5], [56.5, 30.5]], s + 5, 0.3), "#F7F7F2", 2)
    + strokeP("M60,30.5L60,13", LINE, 2.6)
    + `<circle cx="60" cy="11.5" r="2.8" fill="#D25A49" stroke="${LINE}" stroke-width="1.6"/>`
    + fillP(shape([[50, 80], [70, 80], [70, 73.5], [50, 73.5]], s + 6, 0.3), "#FFF0CC", 2));
}
export function landmarkHanRiverSvg() {
  const s = 202;
  return sticker("한강", s, skyDisk(s)
    + fillP(shape([[14, 74], [106, 74], [104, 98], [60, 108], [16, 98]], s + 2, 0.5), "#3F92D2", 2.2)
    + strokeP("M26,86Q32,82 38,86T50,86M64,94Q70,90 76,94T88,94M44,100Q50,96 56,100", "#DDEEFB", 2.2)
    + fillP(shape([[10, 62], [110, 62], [110, 68], [10, 68]], s + 3, 0.2), "#E3604F", 2.2)
    + strokeP("M22,68Q36,40 50,68M52,68Q66,40 80,68M82,68Q96,40 110,68", "#E3604F", 4)
    + strokeP("M22,68Q36,40 50,68M52,68Q66,40 80,68M82,68Q96,40 110,68", LINE, 1.4, ' stroke-opacity=".6"')
    + strokeP("M36,54L36,68M66,54L66,68M96,54L96,68M29,60L29,68M43,60L43,68M59,60L59,68M73,60L73,68M89,60L89,68M103,60L103,68", LINE, 1.4, ' stroke-opacity=".55"')
    + strokeP("M20,68L20,80M50,68L50,82M80,68L80,82M110,68L110,78", LINE, 2.4));
}
export function landmarkGeumRiverSvg() {
  const s = 303;
  return sticker("금강", s, skyDisk(s)
    + fillP(shape([[12, 70], [34, 40], [52, 56], [72, 34], [100, 60], [108, 76], [12, 80]], s + 2, 0.7), "#6BB85A", 2.2)
    + strokeP(hatch(34, 48, 96, 76, 7), "#2B774A", 1.2, ' stroke-opacity=".35"')
    + fillP(shape([[12, 76], [108, 74], [106, 96], [60, 108], [14, 96]], s + 3, 0.6), "#9DD08A", 2.2)
    + fillP("M48,76C58,82 40,90 54,96C66,101 58,106 64,109L78,107C72,102 80,98 70,92C58,86 74,80 62,76Z", "#3F92D2", 2)
    + strokeP("M55,90Q58,88 61,90M62,100Q65,98 68,100", "#DDEEFB", 1.8)
    + fillP(blob(88, 28, 8, 8, s + 4, { n: 9 }), "#F0AE34", 2));
}
export function landmarkHanbitSvg() {
  const s = 404;
  return sticker("한빛탑", s, skyDisk(s)
    + fillP(shape([[14, 90], [106, 90], [104, 100], [60, 108], [16, 100]], s + 2, 0.4), "#9DD08A", 2.2)
    + fillP(shape([[48, 92], [72, 92], [66, 70], [54, 70]], s + 3, 0.4), "#F7F7F2", 2.2)
    + fillP(shape([[55, 72], [65, 72], [63, 40], [57, 40]], s + 4, 0.3), "#D9DCD6", 2.2)
    + fillP(blob(60, 34, 9, 9, s + 5, { n: 10 }), "#F0AE34", 2.2)
    + strokeP(`M44,38C44,30 76,30 76,38C76,46 44,46 44,38Z`, "#3F92D2", 3.2)
    + strokeP("M60,25L60,16", LINE, 2.4)
    + strokeP("M50,82L70,82M53,76L67,76", LINE, 1.4, ' stroke-opacity=".5"'));
}

/** 시안용 QR 무늬 (실제 QR이 아니고 아무 정보도 없다) */
export function fakeQrSvg(size = 232, modules = 29, seed = 2026) {
  const r = rng(seed), m = size / modules;
  const isFinder = (x, y) => (x < 8 && y < 8) || (x >= modules - 8 && y < 8) || (x < 8 && y >= modules - 8);
  const center = (x, y) => Math.abs(x - (modules - 1) / 2) <= 3 && Math.abs(y - (modules - 1) / 2) <= 3;
  let rects = "";
  for (let y = 0; y < modules; y++) for (let x = 0; x < modules; x++) {
    if (isFinder(x, y) || center(x, y)) continue;
    if (r() < 0.47) rects += `M${f1(x * m)},${f1(y * m)}h${f1(m)}v${f1(m)}h${f1(-m)}z`;
  }
  const finder = (fx, fy) => `<rect x="${f1((fx + 0.5) * m)}" y="${f1((fy + 0.5) * m)}" width="${f1(6 * m)}" height="${f1(6 * m)}" rx="${f1(m)}" fill="none" stroke="#1D2A22" stroke-width="${f1(m)}"/>`
    + `<rect x="${f1((fx + 2) * m)}" y="${f1((fy + 2) * m)}" width="${f1(3 * m)}" height="${f1(3 * m)}" rx="${f1(m * 0.6)}" fill="#1D2A22"/>`;
  const c = size / 2;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}"><title>시안용 QR 무늬(정보 없음)</title>`
    + `<rect width="${size}" height="${size}" fill="#FFFFFF"/>`
    + `<path d="${rects}" fill="#1D2A22"/>`
    + finder(0, 0) + finder(modules - 7, 0) + finder(0, modules - 7)
    + `<circle cx="${c}" cy="${c}" r="${f1(m * 3.4)}" fill="#FFFFFF" stroke="#2B774A" stroke-width="3"/>`
    + `<path d="M${c},${f1(c + m * 2.2)}L${c},${f1(c - m * 0.2)}" stroke="#2B774A" stroke-width="3" stroke-linecap="round"/>`
    + `<path d="${blob(c - m * 1.2, c - m * 1, m * 1.4, m * 0.75, 9, { deg: -30, n: 8 })}" fill="#6BB85A" stroke="#2B774A" stroke-width="2"/>`
    + `<path d="${blob(c + m * 1.3, c - m * 1.4, m * 1.6, m * 0.8, 10, { deg: 24, n: 8 })}" fill="#6BB85A" stroke="#2B774A" stroke-width="2"/>`
    + `</svg>`;
}

/** 머리 모양 바꾸기용 덧그림(아이 120×150 좌표). back: 머리 뒤, front: 머리 위 */
export function kidHairLayer(kind, color, { cx = 60, cy = 58, R = 30, part = "back" } = {}) {
  const s = R / 30, at = (x, y) => [cx + x * s, cy + y * s], sw = f1(2.4 * Math.max(0.7, Math.sqrt(s)));
  const fp = (d) => `<path d="${d}" fill="${color}" stroke="${LINE}" stroke-width="${sw}" stroke-linejoin="round"/>`;
  if (kind === "pigtails" && part === "front") {
    return fp(blob(...at(-29, -22), 10.5 * s, 10 * s, 31, { n: 9 })) + fp(blob(...at(29, -22), 10.5 * s, 10 * s, 32, { n: 9 }))
      + `<path d="${blob(...at(-21, -15), 3.2 * s, 3.2 * s, 33, { n: 7 })}" fill="#D25A49" stroke="${LINE}" stroke-width="${f1(1.6 * s)}"/>`
      + `<path d="${blob(...at(21, -15), 3.2 * s, 3.2 * s, 34, { n: 7 })}" fill="#D25A49" stroke="${LINE}" stroke-width="${f1(1.6 * s)}"/>`;
  }
  if (kind === "long" && part === "back") {
    return fp(shape([at(-31, -6), at(-30, -24), at(-14, -33), at(14, -33), at(30, -24), at(31, -6), at(33, 26), at(30, 42), at(14, 44), at(-14, 44), at(-30, 42), at(-33, 26)], 41, 0.5 * s));
  }
  if (kind === "curly" && part === "back") {
    let d = "";
    for (let i = 0; i <= 8; i++) {
      const a = (172 + i * 24.5) * Math.PI / 180;
      d += fp(blob(cx + Math.cos(a) * 30 * s, cy - 2 * s + Math.sin(a) * 29 * s, 8.4 * s, 8.4 * s, 50 + i, { n: 8 }));
    }
    return d;
  }
  return "";
}

// ---------------------------------------------------------------------------
// 6. 선 아이콘 (24×24, currentColor)
// ---------------------------------------------------------------------------
const ICONS = {
  speaker: "M4 9.5h3.5L12.5 5v14l-5-4.5H4z M16 9.2c1.6 1.6 1.6 4.4 0 6 M18.6 6.6c3 3 3 7.8 0 10.8",
  lock: "M6 11h12v9H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3",
  clock: "M12 3a9 9 0 1 0 0.01 0z M12 7.5V12l3 2",
  check: "M5 12.5l4.5 4.5L19 7.5",
  arrowRight: "M4 12h15 M13 6l6 6-6 6",
  chevronL: "M15 5l-7 7 7 7",
  chevronR: "M9 5l7 7-7 7",
  wheel: "M12 3a9 9 0 1 0 0.01 0z M12 9.5a2.5 2.5 0 1 0 0.01 0z M3.4 10.6l6.2 0.6 M14.4 11.2l6.2-0.6 M12 14.5V21",
  phone: "M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V4A1.5 1.5 0 0 1 8 2.5z M11 18.5h2",
  pin: "M12 21s-6-6.2-6-11a6 6 0 0 1 12 0c0 4.8-6 11-6 11z M12 8a2 2 0 1 0 0.01 0z",
  eyeOff: "M3 3l18 18 M10.6 6.2A9.8 9.8 0 0 1 12 6c5 0 9 6 9 6a15.8 15.8 0 0 1-3 3.6 M6.4 7.6A15 15 0 0 0 3 12s4 6 9 6a9 9 0 0 0 4-.9",
  refresh: "M20 11a8 8 0 1 0-2.3 5.7 M20 5v6h-6",
  x: "M6 6l12 12M18 6L6 18",
  gift: "M3.5 8h17v4h-17z M5 12h14v8.5H5z M12 8v12.5 M12 8C10 4 6.5 4.5 7.5 7.2 M12 8c2-4 5.5-3.5 4.5-0.8",
  link: "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1 M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1",
  people: "M9 4.8a3.2 3.2 0 1 0 0.01 0z M3 20c0-3.5 2.7-6 6-6s6 2.5 6 6 M17 6.4a2.6 2.6 0 1 0 0.01 0z M15.5 14.2c3 0 5.5 2 5.5 5.3",
  stop: "M7 7h10v10H7z",
  info: "M12 3a9 9 0 1 0 0.01 0z M12 11v5.5 M12 7.6v.2",
  book: "M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5z M4 20.5A2.5 2.5 0 0 0 6.5 23H20v-5",
  tablet: "M5 3h14a1.5 1.5 0 0 1 1.5 1.5v15A1.5 1.5 0 0 1 19 21H5a1.5 1.5 0 0 1-1.5-1.5v-15A1.5 1.5 0 0 1 5 3z M11 18h2",
  leaf: "M5 19C5 10 10 5 20 4c-1 10-6 15-15 15z M5 19l7-7",
  shirt: "M8 3.5L3.5 7l2.5 3.5L8 9.5V21h8V9.5l2 1L20.5 7 16 3.5c-.5 1.6-2 2.5-4 2.5s-3.5-.9-4-2.5z",
  car: "M3.5 15.5V12l2-5h13l2 5v3.5z M3.5 15.5h17 M7 18.5a1.5 1.5 0 1 0 0.01 0z M17 18.5a1.5 1.5 0 1 0 0.01 0z M6 12h12",
  sparkle: "M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M18 6l-2.5 2.5M8.5 15.5L6 18",
  home: "M4 11l8-7 8 7v9H4z M10 20v-5h4v5",
  minus: "M5 12h14",
  plus: "M12 5v14M5 12h14",
};
export function icon(name, size = 24, { sw = 2.2, cls = "" } = {}) {
  if (!ICONS[name]) throw new Error(`unknown icon ${name}`);
  return `<svg class="ic ${cls}" viewBox="0 0 24 24" width="${size}" height="${size}" aria-hidden="true"><path d="${ICONS[name]}" fill="none" stroke="currentColor" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
}

// ---------------------------------------------------------------------------
// 7. 화면 틀: 차 화면(운전자 띠 | 내비 | 같이가숲), 폰, 태블릿
// ---------------------------------------------------------------------------
export const CAR = { drv: 220, navi: 600, app: 600, h: 720 };

function naviSvg(theme) {
  const t = TOKENS[theme];
  const W = CAR.navi, H = CAR.h;
  let s = `<svg viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" aria-hidden="true"><rect width="${W}" height="${H}" fill="${t.map}"/>`;
  const blocks = [[30, 40, 150, 110], [210, 30, 120, 90], [370, 50, 190, 120], [40, 200, 120, 150], [200, 170, 140, 120], [420, 230, 150, 130], [30, 400, 160, 120], [240, 360, 110, 140], [400, 420, 170, 110], [60, 580, 130, 100], [380, 580, 180, 110]];
  for (const [x, y, w, h] of blocks) s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="14" fill="${t.mapBlock}"/>`;
  s += `<path d="M0 ${H * 0.3} C 160 ${H * 0.36}, 260 ${H * 0.2}, ${W} ${H * 0.27} L ${W} ${H * 0.34} C 260 ${H * 0.27}, 160 ${H * 0.43}, 0 ${H * 0.37} Z" fill="${t.mapWater}"/>`;
  s += `<rect x="200" y="540" width="150" height="120" rx="18" fill="${t.mapPark}"/>`;
  const roads = ["M0 170 L600 150", "M0 380 C 200 370, 380 400, 600 390", "M190 0 L180 720", "M370 0 C 380 260, 360 460, 380 720", "M0 560 L600 540"];
  for (const d of roads) s += `<path d="${d}" stroke="${t.mapRoad}" stroke-width="16" fill="none" stroke-linecap="round"/>`;
  s += `<path d="M300 720 L 300 640 C 300 560, 372 520, 372 440 L 372 300 C 372 220, 470 160, 600 156" stroke="${t.mapRoute}" stroke-width="12" fill="none" stroke-linecap="round" stroke-linejoin="round"/>`;
  s += `<path d="M300 596 l 22 44 l -22 -11 l -22 11 z" fill="${t.mapRoute}" stroke="${t.mapRoad}" stroke-width="4" stroke-linejoin="round"/>`;
  s += "</svg>";
  return s;
}

/** 차 화면 한 장. gear P/D, app = 600×720 패널 HTML */
export function carScreen(theme, { gear = "P", speed = 0, app, eta = "도착 13:00 · 128km" }) {
  const driving = gear !== "P";
  const gears = ["P", "R", "N", "D"].map((g) => `<span class="${g === gear ? "on" : ""}">${g}</span>`).join("");
  return `<figure class="carfig">
<div class="car">
  <div class="drv" style="width:${CAR.drv}px">
    <div class="drv-time">11:12</div>
    <div class="drv-gear">${gears}</div>
    <div class="drv-speed"><b>${speed}</b><span>km/h</span></div>
    <div class="drv-lamps"><i></i><i></i><i></i></div>
    <div class="drv-range">${driving ? "주행 중" : "정차 · P"}</div>
  </div>
  <div class="navi" style="width:${CAR.navi}px">${naviSvg(theme)}<div class="navi-eta">${eta}</div></div>
  <div class="app" style="width:${CAR.app}px;height:${CAR.h}px">${app}</div>
</div>
<figcaption class="regions"><span style="width:${CAR.drv}px">운전자 정보(고정)</span><span style="width:${CAR.navi}px">내비 (운전자 화면, 흐리게 표시)</span><span class="ours" style="width:${CAR.app}px">같이가숲 (반 화면)</span></figcaption>
</figure>`;
}

export const PHONE = { w: 390, h: 844 };
/** 세로 폰 틀. bar: 브라우저 막대 글자(주소 대신 화면 이름) */
export function phone({ body, bar = "같이가숲", label = "" }) {
  return `<figure class="devfig"><div class="phone"><div class="pscreen" style="width:${PHONE.w}px;height:${PHONE.h}px">
<div class="pstatus"><span>11:12</span><span class="pst-r"><i></i><i></i><b></b></span></div>
<div class="pbar">${icon("lock", 16, { sw: 2.4 })}<span>${bar}</span></div>
<div class="pbody">${body}</div>
</div></div>${label ? `<figcaption>${label}</figcaption>` : ""}</figure>`;
}

export const TABLET = { w: 940, h: 620 };
export function tablet({ body, label = "" }) {
  return `<figure class="devfig"><div class="tablet"><div class="tscreen" style="width:${TABLET.w}px;height:${TABLET.h}px">${body}</div></div>${label ? `<figcaption>${label}</figcaption>` : ""}</figure>`;
}

/** 600×720 패널을 절반 크기로 보여 주는 작은 화면(설명 칸용) */
export function mini(panelHtml, caption, scale = 0.5) {
  return `<div class="mini" style="width:${CAR.app * scale}px;height:${CAR.h * scale}px"><div class="mini-in" style="transform:scale(${scale})">${panelHtml}</div></div><p class="mini-cap">${caption}</p>`;
}

// ---------------------------------------------------------------------------
// 8. 시안 한 장(제목 띠 + 기기 + 설명 칸)
// ---------------------------------------------------------------------------
export function fontFaces() {
  const faces = [400, 600, 700, 800].map((w) => {
    const file = path.join(FONT_DIR, `AstaSans-${w}.ttf`);
    const src = fs.existsSync(file) ? `url("../.font-cache/AstaSans-${w}.ttf") format("truetype"), ` : "";
    return `@font-face{font-family:"Asta Sans";font-weight:${w};src:${src}local("Asta Sans");}`;
  });
  return faces.join("\n");
}

export function css(theme) {
  const t = TOKENS[theme];
  const vars = Object.entries(t).map(([k, v]) => `--${k.replace(/[A-Z]/g, (c) => "-" + c.toLowerCase())}:${v};`).join("");
  return `${fontFaces()}
:root{${vars}--font:"Asta Sans","NanumSquareRound","Noto Sans CJK KR",sans-serif;color-scheme:${theme};}
*{box-sizing:border-box;}
body{margin:0;background:var(--canvas);color:var(--canvas-ink);font-family:var(--font);word-break:keep-all;-webkit-font-smoothing:antialiased;}
.sheet{width:1920px;padding:40px 48px 44px;display:flex;flex-direction:column;gap:28px;}
.sheet-head{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;}
.sheet-head .eyebrow{margin:0 0 6px;font-size:20px;font-weight:700;color:var(--canvas-ink2);letter-spacing:.02em;}
.sheet-head h1{margin:0;font-size:44px;line-height:1.2;font-weight:800;color:var(--forest);text-wrap:balance;}
.chips{display:flex;gap:10px;flex-wrap:wrap;justify-content:flex-end;}
.chip{display:inline-flex;align-items:center;gap:8px;border-radius:999px;padding:8px 18px;font-size:20px;font-weight:700;background:var(--card);color:var(--ink);border:2px solid var(--line);white-space:nowrap;}
.chip.park{background:var(--leaf-soft);border-color:var(--leaf-soft);color:var(--ink);}
.chip.drive{background:var(--sky-soft);border-color:var(--sky-soft);color:var(--ink);}
.chip.plan{background:var(--sun-soft);border-color:var(--sun-soft);color:var(--ink);}
.chip .dot{width:12px;height:12px;border-radius:50%;background:currentColor;}
.chip.park .dot{background:var(--forest);} .chip.drive .dot{background:var(--sky-text);} .chip.plan .dot{background:var(--sun);}
.sheet-body{display:flex;gap:40px;align-items:flex-start;}
.notes{flex:1;min-width:0;display:flex;flex-direction:column;gap:18px;}
.notes h2{margin:0;font-size:24px;font-weight:800;color:var(--canvas-ink);}
.notes ul{margin:0;padding:0;list-style:none;display:flex;flex-direction:column;gap:12px;}
.notes li{font-size:19px;line-height:1.5;color:var(--canvas-ink);padding-left:22px;position:relative;}
.notes li::before{content:"";position:absolute;left:4px;top:.62em;width:9px;height:9px;border-radius:50%;background:var(--forest);}
.notes li b{font-weight:800;}
.notes .where{font-size:17px;line-height:1.5;color:var(--canvas-ink2);border-top:2px solid var(--line);padding-top:14px;margin:0;}
.mini{position:relative;overflow:hidden;border-radius:14px;border:3px solid var(--bezel);background:var(--paper);}
.mini-in{position:absolute;left:0;top:0;width:${CAR.app}px;height:${CAR.h}px;transform-origin:0 0;}
.mini-cap{margin:-8px 0 0;font-size:17px;line-height:1.45;color:var(--canvas-ink2);}
/* 차 화면 */
.carfig{margin:0;display:flex;flex-direction:column;gap:10px;}
.car{display:flex;height:${CAR.h}px;border:16px solid var(--bezel);border-radius:28px;box-sizing:content-box;overflow:hidden;background:var(--bezel);}
.drv{position:relative;background:var(--drv);color:var(--drv-ink);display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:28px 0 30px;border-right:3px solid var(--bezel);}
.drv::after,.navi::after{content:"";position:absolute;inset:0;background:var(--paper);opacity:.55;}
.drv-time{font-size:22px;font-weight:700;}
.drv-gear{display:flex;gap:10px;font-size:26px;font-weight:800;}
.drv-gear span{opacity:.45;} .drv-gear span.on{opacity:1;border-bottom:4px solid currentColor;}
.drv-speed{display:flex;flex-direction:column;align-items:center;line-height:1;}
.drv-speed b{font-size:96px;font-weight:800;font-variant-numeric:tabular-nums;}
.drv-speed span{font-size:22px;font-weight:700;margin-top:8px;}
.drv-lamps{display:flex;gap:12px;} .drv-lamps i{width:30px;height:30px;border-radius:9px;border:3px solid currentColor;opacity:.6;}
.drv-range{font-size:20px;font-weight:700;}
.navi{position:relative;border-right:3px solid var(--bezel);}
.navi svg{display:block;}
.navi-eta{position:absolute;left:24px;bottom:24px;background:var(--card);color:var(--ink);font-size:22px;font-weight:800;padding:12px 18px;border-radius:14px;}
.app{background:var(--paper);color:var(--ink);position:relative;overflow:hidden;}
.regions{display:flex;margin-left:16px;font-size:17px;font-weight:700;color:var(--canvas-ink2);}
.regions span{padding:6px 8px 0 0;border-top:3px solid var(--canvas-ink2);}
.regions span.ours{color:var(--forest);border-top-color:var(--forest);}
/* 폰·태블릿 */
.devfig{margin:0;display:flex;flex-direction:column;align-items:center;gap:12px;}
.devfig figcaption{font-size:19px;font-weight:700;color:var(--canvas-ink2);}
.phone{border:14px solid var(--bezel);border-radius:56px;overflow:hidden;background:var(--bezel);}
.pscreen{background:var(--paper);color:var(--ink);display:flex;flex-direction:column;overflow:hidden;border-radius:42px;}
.pstatus{height:44px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 30px 0 34px;font-size:16px;font-weight:800;}
.pst-r{display:flex;gap:5px;align-items:center;} .pst-r i{width:5px;height:12px;border-radius:2px;background:currentColor;} .pst-r i:first-child{height:8px;} .pst-r b{width:24px;height:12px;border-radius:4px;border:2px solid currentColor;margin-left:4px;}
.pbar{height:44px;flex:none;margin:0 14px;border-radius:12px;background:var(--card);border:1.5px solid var(--line);display:flex;align-items:center;justify-content:center;gap:8px;font-size:15px;font-weight:700;color:var(--ink2);}
.pbody{flex:1;min-height:0;overflow:hidden;display:flex;flex-direction:column;}
.tablet{border:22px solid var(--bezel);border-radius:40px;overflow:hidden;background:var(--bezel);}
.tscreen{background:var(--paper);color:var(--ink);overflow:hidden;border-radius:18px;display:flex;flex-direction:column;}
/* 앱 안 공용 부품 */
.ic{flex:none;display:block;}
.panel{width:${CAR.app}px;height:${CAR.h}px;padding:24px;display:flex;flex-direction:column;gap:16px;background:var(--paper);color:var(--ink);}
.row{display:flex;align-items:center;gap:16px;}
.grow{flex:1;min-width:0;}
.num{font-weight:800;color:var(--forest);line-height:1.04;font-variant-numeric:tabular-nums;white-space:nowrap;}
.num small{font-size:.55em;}
.kid{font-weight:800;line-height:1.35;color:var(--ink);}
.sub{font-size:20px;line-height:1.5;color:var(--ink2);font-weight:600;}
.small{font-size:17px;line-height:1.5;color:var(--ink2);font-weight:600;}
.h2{font-size:30px;font-weight:800;line-height:1.3;margin:0;}
.btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:56px;padding:0 24px;border-radius:999px;font-size:21px;font-weight:800;border:2.5px solid var(--ink2);color:var(--ink);background:transparent;white-space:nowrap;}
.btn.sm{min-height:48px;padding:0 18px;font-size:19px;}
.btn.primary{background:var(--forest);border-color:var(--forest);color:var(--on-forest);}
.btn.ghost{border-width:2px;}
.btn.done{background:var(--leaf-soft);border-color:var(--forest);color:var(--ink);}
.btn.off{opacity:.45;}
.badge{display:inline-flex;align-items:center;gap:6px;border-radius:999px;padding:5px 14px;font-size:17px;font-weight:800;white-space:nowrap;}
.badge.example{background:var(--sun-soft);color:var(--ink);border:2px dashed var(--sun);}
.badge.on{background:var(--leaf-soft);color:var(--ink);}
.badge.offb{background:var(--card);color:var(--ink2);border:2px solid var(--line);}
.badge.new{background:var(--berry-soft);color:var(--ink);}
.card{background:var(--card);border:2px solid var(--line);border-radius:20px;}
.note-line{display:flex;align-items:center;gap:10px;font-size:18px;line-height:1.45;color:var(--ink2);font-weight:600;}
.warn{display:flex;align-items:center;gap:10px;background:var(--sun-soft);color:var(--ink);border-radius:14px;padding:10px 14px;font-size:17px;font-weight:800;line-height:1.35;}
.drivechip{display:flex;align-items:center;gap:10px;background:var(--sky-soft);color:var(--ink);border-radius:14px;padding:12px 16px;font-size:22px;font-weight:800;}
.roadbox{position:relative;flex:none;}
.roadbox svg{display:block;}
`;
}

/** 시안 한 장 HTML */
export function sheetHtml(theme, { title, eyebrow, chips, device, notes }) {
  return `<!doctype html><html lang="ko" data-theme="${theme}"><head><meta charset="utf-8"><title>${title}</title>
<style>${css(theme)}</style></head><body><div class="sheet">
<header class="sheet-head"><div><p class="eyebrow">${eyebrow}</p><h1>${title}</h1></div><div class="chips">${chips}</div></header>
<div class="sheet-body">${device}<aside class="notes">${notes}</aside></div>
</div></body></html>`;
}

export const chip = (kind, text) => `<span class="chip ${kind}"><span class="dot"></span>${text}</span>`;
