// 창 크기별 아이 진행 화면 모형(HTML) → PNG
//
// core:ui의 KidProgressPanel 배치 규칙(WindowSize.kt progressPanelSpec)과 길 계산(RoadGeometry.kt)을
// 브라우저에서 같은 숫자로 다시 그린 "모형"이다. 실제 앱 화면은 Android Studio 미리보기(Previews.kt)로 확인한다.
//
// 실행: node design/characters/tools/render-layouts.mjs   (저장소 루트에서)

import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { buildAll, toSvg } from "./build-assets.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CHAR_DIR = path.resolve(HERE, "..");
const require = createRequire(import.meta.url);

function loadPlaywright() {
  for (const c of [process.env.PLAYWRIGHT_MODULE, "playwright", "/opt/node-tools/node_modules/playwright"].filter(Boolean)) {
    try {
      return require(c);
    } catch {
      // 다음 후보
    }
  }
  throw new Error("Playwright를 찾지 못했습니다. PLAYWRIGHT_MODULE 환경 변수로 경로를 알려 주세요.");
}

const arts = Object.fromEntries(buildAll().map((a) => [a.name, a]));
const uri = (name, theme, overrides) => `data:image/svg+xml;base64,${Buffer.from(toSvg(arts[name], { theme, overrides })).toString("base64")}`;

const TOKENS = {
  light: { paper: "#F5FAF3", ink: "#1D2A22", ink2: "#4B5C51", forest: "#2B774A", demo: "#7D5FD0", onDemo: "#FFFFFF", road: "#D8D4C8", roadEdge: "#B9B4A6", roadLine: "#FFFFFF", trail: "#2B774A", nav: "#DDE3DF", navInk: "#5B6B61", flagIdle: "#FFFFFF" },
  dark: { paper: "#0F1713", ink: "#E3EEE6", ink2: "#A8BBAE", forest: "#6FCB8E", demo: "#A98FF2", onDemo: "#0F1713", road: "#343B38", roadEdge: "#56605B", roadLine: "#C9CFCB", trail: "#6FCB8E", nav: "#1B2420", navInk: "#8FA196", flagIdle: "#8A958F" },
};

function page(theme) {
  const t = TOKENS[theme];
  const img = {};
  for (const n of ["car_stage0", "car_stage25", "car_stage50", "car_stage75", "house", "flag", "icon_cartoon"]) img[n] = uri(n, theme);
  img.flagIdle = uri("flag", theme, { flagCloth: t.flagIdle });

  // [이름, 앱 영역 안 배치(옆 내비 너비), 같이가숲 창 너비, 높이, 퍼센트, 문구, 보조]
  const frames = [
    ["전체 앱 영역 1200×720 · Expanded", 0, 1200, 720, 40, "만화 두 편 보면 도착해요", "약 40분 남음"],
    ["반 화면 1:1 600×720 · Medium (기본: 내비 | 같이가숲)", 600, 600, 720, 40, "만화 두 편 보면 도착해요", "약 40분 남음"],
    ["2:1의 좁은 쪽 400×720 · Compact", 800, 400, 720, 62, "만화 한 편 보면 도착해요", "약 30분 남음"],
    ["세로 720×1200 · 구불구불한 길", 0, 720, 1200, 62, "만화 한 편 보면 도착해요", "약 30분 남음"],
  ];

  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>창 크기별 진행 화면</title>
<style>
* { box-sizing: border-box; }
body { margin: 0; background: ${theme === "dark" ? "#070B09" : "#E9EEEA"}; font-family: "NanumSquareRound", "Noto Sans CJK KR", sans-serif; color: ${t.ink}; }
.sheet { display: flex; flex-wrap: wrap; gap: 40px; padding: 40px; width: 2700px; align-items: flex-start; }
.sheet > h1 { width: 100%; margin: 0; font-size: 44px; color: ${t.forest}; }
.sheet > p { width: 100%; margin: -24px 0 0; font-size: 22px; color: ${t.ink2}; }
figure { margin: 0; display: grid; gap: 12px; }
figcaption { font-size: 26px; font-weight: 700; }
.screen { display: flex; border-radius: 20px; overflow: hidden; border: 3px solid ${theme === "dark" ? "#2A3B31" : "#C9D3CC"}; }
.nav { background: ${t.nav}; color: ${t.navInk}; display: grid; place-items: center; font-size: 30px; font-weight: 700; border-right: 3px dashed ${t.navInk}; }
.panel { background: ${t.paper}; display: flex; flex-direction: column; overflow: hidden; }
.row { display: flex; align-items: center; }
.num { font-weight: 800; color: ${t.forest}; white-space: nowrap; line-height: 1.08; font-variant-numeric: tabular-nums; }
.num small { font-size: 0.55em; }
.sent { display: flex; align-items: center; gap: 12px; min-width: 0; }
.sent span { font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; line-height: 1.5; }
.detail { font-size: 24px; font-weight: 600; color: ${t.ink2}; line-height: 1.5; }
.badge { background: ${t.demo}; color: ${t.onDemo}; font-size: 24px; font-weight: 800; border-radius: 999px; padding: 8px 20px; white-space: nowrap; }
.btn { border: 2px solid ${t.ink2}; color: ${t.ink}; border-radius: 999px; font-size: 24px; font-weight: 700; padding: 12px 22px; white-space: nowrap; }
.road { flex: 1; position: relative; min-height: 0; }
.road svg { position: absolute; inset: 0; width: 100%; height: 100%; overflow: hidden; }
.extras { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.topbar { display: flex; justify-content: flex-end; gap: 12px; align-items: center; }
</style></head><body><div class="sheet">
<h1>창 크기별 아이 진행 화면 (주행 중 · ${theme === "dark" ? "다크" : "라이트"})</h1>
<p>HTML 모형입니다. core:ui의 같은 규칙(progressPanelSpec, RoadGeometry)을 옮겨 그렸습니다. 글꼴은 이 PC에 있는 둥근 고딕으로 대신했습니다(앱은 시스템 글꼴 Asta Sans).</p>
${frames.map(([name, nav, w, h, pct, sent, detail]) => `
<figure><figcaption>${name}</figcaption>
<div class="screen">${nav ? `<div class="nav" style="width:${nav}px;height:${h}px">내비 (운전자)</div>` : ""}
<div class="panel" data-w="${w}" data-h="${h}" data-pct="${pct}" data-sent="${sent}" data-detail="${detail}" style="width:${w}px;height:${h}px"></div></div></figure>`).join("")}
</div>
<script>
const IMG = ${JSON.stringify(img)};
const T = ${JSON.stringify(t)};
const CAR_ASPECT = 240 / 206, CAR_GROUND = 197 / 206, HOUSE_ASPECT = 130 / 120, HOUSE_GROUND = 116 / 120, FLAG_ASPECT = 44 / 70;

function spec(w, h) {
  const W = w < 600 ? "Compact" : w < 840 ? "Medium" : "Expanded";
  const short = h < 480;
  if (W === "Compact") return { W, headerInRow: false, below: true, topBar: false, icon: false, detail: false, actions: false, pct: short ? 56 : 72, sent: short ? 28 : 32, pad: 16, gap: short ? 8 : 12 };
  if (W === "Medium") return { W, headerInRow: short, below: false, topBar: false, icon: true, detail: !short, actions: true, pct: short ? 72 : 96, sent: short ? 32 : 40, pad: 24, gap: short ? 12 : 16 };
  return { W, headerInRow: true, below: false, topBar: true, icon: true, detail: !short, actions: true, pct: short ? 88 : 120, sent: short ? 36 : 44, pad: 32, gap: short ? 12 : 20 };
}

// RoadGeometry.kt와 같은 계산
function line(x0, y0, x1, y1) { const len = Math.hypot(x1 - x0, y1 - y0); return { len, at: (d) => { const t = len ? Math.min(1, Math.max(0, d / len)) : 0; return { x: x0 + (x1 - x0) * t, y: y0 + (y1 - y0) * t, right: x1 >= x0 }; } }; }
function arc(cx, cy, r, s, sw, turnRight) { const len = Math.abs(sw) * Math.PI / 180 * r; return { len, at: (d) => { const t = len ? Math.min(1, Math.max(0, d / len)) : 0; const a = (s + sw * t) * Math.PI / 180; return { x: cx + r * Math.cos(a), y: cy + r * Math.sin(a), right: turnRight ? t < .5 : t >= .5 }; } }; }
function geom(segs) { const L = segs.reduce((a, s) => a + s.len, 0); return { L, at(f) { let d = Math.min(1, Math.max(0, f)) * L; for (const s of segs) { if (d <= s.len) return s.at(d); d -= s.len; } const s = segs[segs.length - 1]; return s.at(s.len); } }; }
function layout(w, h) {
  if (h >= w * 0.75) {
    const rows = Math.min(6, Math.max(2, Math.ceil(h / (w * 0.55)))); const band = h / rows, r = band / 2;
    let carH = band * 0.55, carW = carH * CAR_ASPECT; if (carW > w * 0.3) { carW = w * 0.3; carH = carW / CAR_ASPECT; }
    const roadW = Math.max(band * 0.085, 6), flagH = Math.min(band * 0.3, carH * 0.62), edge = Math.max(roadW / 2, flagH * FLAG_ASPECT * 0.6) + 2, left = r + edge, right = Math.max(left + 1, w - r - edge), firstY = band * 0.74;
    const houseH = carH * 0.9, houseW = houseH * HOUSE_ASPECT, lastRight = (rows - 1) % 2 === 0;
    const inset = lastRight ? Math.max(0, right - (w - houseW - carW * 0.3)) : Math.max(0, houseW + carW * 0.3 - left);
    const segs = [];
    for (let i = 0; i < rows; i++) { const y = firstY + i * band, gr = i % 2 === 0; const xs = gr ? left : right; let xe = gr ? right : left;
      if (i === rows - 1) xe = gr ? Math.max(xs + 1, xe - inset) : Math.min(xs - 1, xe + inset);
      segs.push(line(xs, y, xe, y)); if (i < rows - 1) segs.push(gr ? arc(right, y + r, r, -90, 180, true) : arc(left, y + r, r, 270, -180, false)); }
    const g = geom(segs), end = g.at(1), houseLeft = lastRight ? end.x + carW * 0.3 : end.x - carW * 0.3 - houseW;
    return { w, h, g, roadW, carW, carH, flagH, houseW, houseH, houseLeft, houseTop: end.y + roadW * 0.25 - houseH * HOUSE_GROUND, startX: Math.max(roadW / 2 + 1, left - carW * 0.45), endX: houseLeft + houseW * 0.5 };
  }
  const used = Math.min(h, w / 2.4), top = (h - used) / 2; let carH = used * 0.56, carW = carH * CAR_ASPECT; if (carW > w * 0.28) { carW = w * 0.28; carH = carW / CAR_ASPECT; }
  const roadW = Math.max(used * 0.12, 6), houseH = carH * 0.9, houseW = houseH * HOUSE_ASPECT, roadY = top + used * 0.8, x0 = carW * 0.5 + 2, x1 = Math.max(x0 + 1, w - houseW - carW * 0.3);
  return { w, h, g: geom([line(x0, roadY, x1, roadY)]), roadW, carW, carH, flagH: Math.min(used * 0.3, carH * 0.62), houseW, houseH, houseLeft: w - houseW, houseTop: roadY + roadW * 0.25 - houseH * HOUSE_GROUND, startX: roadW / 2 + 1, endX: w - houseW + houseW * 0.45 };
}
function pts(l, a, b) { const out = []; const n = Math.max(1, Math.ceil((b - a) * l.g.L / 4)); for (let i = 0; i <= n; i++) { const p = l.g.at(a + (b - a) * i / n); out.push(p.x.toFixed(1) + "," + p.y.toFixed(1)); } return out; }
function roadSvg(w, h, pct) {
  const l = layout(w, h), f = pct / 100, s = l.g.at(0), e = l.g.at(1);
  const all = [s.x === l.startX ? null : l.startX.toFixed(1) + "," + s.y.toFixed(1), ...pts(l, 0, 1), l.endX.toFixed(1) + "," + e.y.toFixed(1)].filter(Boolean).join(" ");
  const rest = [...pts(l, f, 1), l.endX.toFixed(1) + "," + e.y.toFixed(1)].join(" ");
  const passed = [l.startX.toFixed(1) + "," + s.y.toFixed(1), ...pts(l, 0, f)].join(" ");
  const rw = l.roadW;
  let svg = '<svg viewBox="0 0 ' + w + ' ' + h + '"><g fill="none" stroke-linecap="round" stroke-linejoin="round">'
    + '<polyline points="' + all + '" stroke="' + T.roadEdge + '" stroke-width="' + (rw + Math.max(3, rw * .22)) + '"/>'
    + '<polyline points="' + all + '" stroke="' + T.road + '" stroke-width="' + rw + '"/>'
    + '<polyline points="' + rest + '" stroke="' + T.roadLine + '" stroke-width="' + Math.max(2, rw * .14) + '" stroke-dasharray="' + rw * .55 + ' ' + rw * .75 + '"/>'
    + '<polyline points="' + passed + '" stroke="' + T.trail + '" stroke-width="' + rw * .42 + '"/></g>'
    + '<circle cx="' + l.startX + '" cy="' + s.y + '" r="' + rw * .42 + '" fill="' + T.trail + '"/>'
    + '<image href="' + IMG.house + '" x="' + l.houseLeft + '" y="' + l.houseTop + '" width="' + l.houseW + '" height="' + l.houseH + '"/>';
  const fw = l.flagH * FLAG_ASPECT;
  for (let m = 10; m < 100; m += 10) { const p = l.g.at(m / 100), bottom = p.y - rw / 2 - 1, top = bottom - l.flagH * 65 / 70;
    svg += '<image href="' + (pct >= m ? IMG.flag : IMG.flagIdle) + '" x="' + (p.x - fw * 20 / 44) + '" y="' + top + '" width="' + fw + '" height="' + l.flagH + '"/>'; }
  const p = l.g.at(f); const cx = Math.min(Math.max(0, p.x - l.carW / 2), w - l.carW), cy = Math.min(Math.max(0, p.y + rw * .15 - l.carH * CAR_GROUND), h - l.carH);
  const car = pct < 25 ? IMG.car_stage0 : pct < 50 ? IMG.car_stage25 : pct < 75 ? IMG.car_stage50 : IMG.car_stage75;
  const flip = p.right ? "" : ' transform="translate(' + (2 * cx + l.carW) + ' 0) scale(-1 1)"';
  svg += '<image href="' + car + '" x="' + cx + '" y="' + cy + '" width="' + l.carW + '" height="' + l.carH + '"' + flip + '/>';
  return svg + '</svg>';
}
function fit(el, max, min) { let s = max; el.style.fontSize = s + "px"; while (s > min && el.scrollWidth > el.clientWidth + 0.5) { s -= 2; el.style.fontSize = s + "px"; } }

for (const panel of document.querySelectorAll(".panel")) {
  const w = +panel.dataset.w, h = +panel.dataset.h, pct = +panel.dataset.pct, S = spec(w, h);
  panel.style.padding = S.pad + "px"; panel.style.gap = S.gap + "px";
  const sentence = (inRow) => '<div class="sentblock" style="min-width:0;' + (inRow ? 'flex:1' : '') + '"><div class="sent">' + (S.icon ? '<img src="' + IMG.icon_cartoon + '" style="height:' + Math.max(48, S.sent * 1.2) + 'px">' : '') + '<span data-max="' + S.sent + '">' + panel.dataset.sent + '</span></div>' + (S.detail ? '<div class="detail">' + panel.dataset.detail + '</div>' : '') + '</div>';
  const extras = '<span class="badge">데모 주행</span>' + (S.actions ? '<span class="btn">크게</span>' : '');
  let html = '';
  if (S.topBar) html += '<div class="topbar">' + extras + '</div>';
  html += '<div class="row" style="gap:20px"><div class="num" style="font-size:' + S.pct + 'px">' + pct + '<small>%</small></div>' + (S.headerInRow ? sentence(true) : '<div style="flex:1"></div>') + (S.topBar ? '' : '<div class="extras">' + extras + '</div>') + '</div>';
  if (!S.headerInRow && !S.below) html += sentence();
  html += '<div class="road"></div>';
  if (S.below) html += sentence();
  panel.innerHTML = html;
  for (const sp of panel.querySelectorAll(".sent span")) fit(sp, +sp.dataset.max, 24);
  const road = panel.querySelector(".road");
  road.innerHTML = roadSvg(road.clientWidth, road.clientHeight, pct);
}
</script></body></html>`;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { chromium } = loadPlaywright();
  const browser = await chromium.launch();
  try {
    for (const theme of ["light", "dark"]) {
      const p = await browser.newPage({ viewport: { width: 2780, height: 1000 }, deviceScaleFactor: 0.5 });
      await p.setContent(page(theme), { waitUntil: "load" });
      const out = path.join(CHAR_DIR, theme === "dark" ? "layout-sizes-dark.png" : "layout-sizes.png");
      await p.screenshot({ path: out, fullPage: true });
      await p.close();
      console.log(`ok  ${path.relative(process.cwd(), out)}`);
    }
  } finally {
    await browser.close();
  }
}
