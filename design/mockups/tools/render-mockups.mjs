// 11/15 기획서용 화면 시안 만들기 (HTML → PNG)
//
// 실행(저장소 루트에서): node design/mockups/tools/render-mockups.mjs
//   - design/mockups/svg/        새로 그린 그림 원본(꽃, 랜드마크 스티커, 머리 모양 레이어, QR 무늬)
//   - design/mockups/html/       시안 HTML(브라우저로 열어 볼 수 있음) + assets/(캐릭터 SVG, 생성물)
//   - design/mockups/png/        기획서·PPT에 넣을 PNG, 00-contact-sheet.png(한 장에 모아 보기)
// 환경 변수: PLAYWRIGHT_MODULE(Playwright 경로), MOCKUP_SCALE(PNG 배율, 기본 1.5), MOCKUP_ONLY(쉼표로 고른 파일 이름만)

import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import { pathToFileURL } from "node:url";
import {
  HERE, HTML_DIR, SVG_DIR, PNG_DIR, FONT_DIR, TOKENS, CAR, PHONE, TABLET,
  art, artSize, friendCar, carStage, roadSvg, resetAssets, contrast,
  flowerSvg, landmarkTowerSvg, landmarkHanRiverSvg, landmarkGeumRiverSvg, landmarkHanbitSvg, fakeQrSvg, kidHairLayer,
  icon, carScreen, phone, tablet, mini, sheetHtml, chip, blob,
} from "./lib.mjs";

const require = createRequire(import.meta.url);
const W = CAR.app - 48; // 반 화면 패널 안쪽 너비(552)

// ---------------------------------------------------------------------------
// 0. 새로 그린 그림 원본 저장
// ---------------------------------------------------------------------------
const NEW_ART = {
  flower_red: flowerSvg("#E86A5A", "#F0AE34", 7),
  flower_yellow: flowerSvg("#F0AE34", "#B7713D", 17),
  landmark_namsan_tower: landmarkTowerSvg(),
  landmark_han_river: landmarkHanRiverSvg(),
  landmark_geum_river: landmarkGeumRiverSvg(),
  landmark_hanbit_tower: landmarkHanbitSvg(),
  qr_pattern: fakeQrSvg(),
};
function hairOriginal(kind, part, title) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 150" width="120" height="150"><title>${title}</title>${kidHairLayer(kind, "#4A3326", { part })}</svg>`;
}
Object.assign(NEW_ART, {
  hair_kid_pigtails_front: hairOriginal("pigtails", "front", "아이 머리 모양 · 양갈래(머리 위 레이어)"),
  hair_kid_long_back: hairOriginal("long", "back", "아이 머리 모양 · 긴 머리(머리 뒤 레이어)"),
  hair_kid_curly_back: hairOriginal("curly", "back", "아이 머리 모양 · 곱슬(머리 뒤 레이어)"),
});
function writeNewArt() {
  fs.mkdirSync(SVG_DIR, { recursive: true });
  for (const [name, svg] of Object.entries(NEW_ART)) fs.writeFileSync(path.join(SVG_DIR, `${name}.svg`), svg + "\n");
}
const newArt = (name) => `../svg/${name}.svg`;

// ---------------------------------------------------------------------------
// 1. 작은 부품
// ---------------------------------------------------------------------------
const imgH = (href, h, extra = "") => `<img src="${href}" height="${h}" alt="" style="display:block;height:${h}px;width:auto" ${extra}>`;
function placeArt(href, name, h, x, bottom) {
  const [aw, ah] = artSize(name);
  const w = (aw * h) / ah;
  return `<image href="${href}" x="${(x - w / 2).toFixed(1)}" y="${(bottom - h).toFixed(1)}" width="${w.toFixed(1)}" height="${h.toFixed(1)}"/>`;
}
const notesBlock = (items, where, extra = "") => `<h2>이 시안에서 볼 것</h2><ul>${items.map((i) => `<li>${i}</li>`).join("")}</ul>${extra}<p class="where">${where}</p>`;

/** 반 화면 진행 화면(주행 중, Medium 규칙). 다른 주차 전용 화면이 "주행 중이 되면" 돌아오는 곳 */
function progressPanel(theme, { pct = 62, sentence = "만화 두 편 반 보면 도착해요", detail = "약 48분 남음", cars = null, sentIcon = "icon_cartoon" } = {}) {
  const roadH = CAR.h - 48 - 100 - 52 - 30 - 48;
  const carList = cars ?? [{ pct, href: art(`car_stage${carStage(pct)}`, theme) }];
  return `<div class="panel">
<div class="row"><div class="num" style="font-size:96px">${pct}<small>%</small></div><div class="grow"></div><span class="btn sm">크게</span></div>
<div class="row" style="gap:12px">${sentIcon ? imgH(art(sentIcon, theme), 50) : ""}<span class="kid" style="font-size:38px">${sentence}</span></div>
<div class="sub">${detail}</div>
<div class="roadbox" style="height:${roadH}px">${roadSvg(theme, W, roadH, { pct, cars: carList })}</div>
</div>`;
}
const drivingMini = (theme, caption) => mini(progressPanel(theme), caption);

/** 숲 장면(552×330 좌표). slots: [{x,y,item}] item 없으면 빈 자리 */
const FOREST_ITEMS = {
  tree: { name: "plant_tree", h: 150 },
  sapling: { name: "plant_sapling", h: 96 },
  squirrel: { name: "animal_squirrel", h: 64 },
  rabbit: { name: "animal_rabbit", h: 74 },
  bird: { name: "animal_bird", h: 46 },
};
const FOREST_SLOTS = [
  { x: 96, y: 186, item: "tree" }, { x: 276, y: 176, item: "tree" }, { x: 458, y: 186, item: "sapling" },
  { x: 58, y: 262, item: "flower_red" }, { x: 204, y: 262, item: "squirrel" }, { x: 352, y: 258, item: null }, { x: 494, y: 264, item: "flower_yellow" },
  { x: 140, y: 318, item: "rabbit" }, { x: 404, y: 318, item: null },
];
function forestScene(theme, w, h, { highlightEmpty = true, slots = FOREST_SLOTS } = {}) {
  const t = TOKENS[theme];
  let s = `<svg viewBox="0 0 552 330" width="${w}" height="${h}" aria-hidden="true" style="display:block">`
    + `<rect width="552" height="330" rx="20" fill="${t.sceneSky}"/>`
    + `<circle cx="490" cy="54" r="26" fill="${t.sun}" opacity="${theme === "dark" ? 0.75 : 1}"/>`
    + `<path d="${blob(130, 60, 44, 16, 3, { n: 9 })}" fill="#FFFFFF" opacity="${theme === "dark" ? 0.12 : 0.9}"/>`
    + `<path d="${blob(330, 42, 34, 13, 4, { n: 9 })}" fill="#FFFFFF" opacity="${theme === "dark" ? 0.1 : 0.85}"/>`
    + `<path d="M0 168 C 110 120, 230 150, 310 132 S 470 118, 552 150 L 552 330 L 0 330 Z" fill="${t.hillBack}"/>`
    + `<path d="M0 232 C 150 206, 380 214, 552 226 L 552 310 Q 552 330 532 330 L 20 330 Q 0 330 0 310 Z" fill="${t.hillFront}"/>`;
  for (const sl of slots) {
    if (!sl.item) {
      s += `<ellipse cx="${sl.x}" cy="${sl.y - 6}" rx="38" ry="13" fill="${t.card}" fill-opacity=".55" stroke="${t.forest}" stroke-width="3" stroke-dasharray="7 6"/>`;
      if (highlightEmpty) s += `<circle cx="${sl.x}" cy="${sl.y - 38}" r="20" fill="${t.forest}"/><path d="M${sl.x - 9} ${sl.y - 38}h18M${sl.x} ${sl.y - 47}v18" stroke="${t.onForest}" stroke-width="4" stroke-linecap="round"/>`;
      continue;
    }
    s += `<ellipse cx="${sl.x}" cy="${sl.y - 4}" rx="30" ry="8" fill="${t.ink}" fill-opacity=".07"/>`;
    if (sl.item.startsWith("flower")) {
      s += `<image href="${newArt(sl.item)}" x="${sl.x - 22}" y="${sl.y - 60}" width="45" height="60"/>`;
    } else {
      const it = FOREST_ITEMS[sl.item];
      s += placeArt(art(it.name, theme), it.name, it.h, sl.x, sl.y + 2);
    }
  }
  return s + "</svg>";
}

/** 머리 모양을 덧그린 아이(서 있는 그림). box: 그림 높이 */
function kidWithHair(theme, hair, h, { shirt = null, crop = null } = {}) {
  const t = TOKENS[theme];
  const hairColor = theme === "dark" ? "#5A4031" : "#4A3326";
  const href = art("child", theme, shirt ? { overrides: { shirtKid: shirt } } : {});
  const vb = crop ?? "0 0 120 150";
  const [, , vw, vh] = vb.split(" ").map(Number);
  const w = (vw * h) / vh;
  return `<svg viewBox="${vb}" width="${w.toFixed(1)}" height="${h}" aria-hidden="true" style="display:block;overflow:${crop ? "hidden" : "visible"}">`
    + kidHairLayer(hair, hairColor, { part: "back" })
    + `<image href="${href}" x="0" y="0" width="120" height="150"/>`
    + kidHairLayer(hair, hairColor, { part: "front" })
    + `</svg>`;
}
/** 차 안 아이에게도 같은 머리 모양(양갈래만 앞 레이어로 덧그릴 수 있음) */
function carWithHair(theme, hair, h, overrides) {
  const href = art("car_stage75", theme, { overrides });
  const hairColor = theme === "dark" ? "#5A4031" : "#4A3326";
  const w = (240 * h) / 206;
  return `<svg viewBox="0 0 240 206" width="${w.toFixed(1)}" height="${h}" aria-hidden="true" style="display:block">`
    + `<image href="${href}" x="0" y="0" width="240" height="206"/>`
    + kidHairLayer(hair, hairColor, { part: "front", cx: 104, cy: 99, R: 17.5 })
    + `</svg>`;
}

function swatch(color, label, on, size = 48) {
  return `<div class="sw-item${on ? " on" : ""}"><span class="sw-dot" style="width:${size}px;height:${size}px;background:${color}">${on ? icon("check", size * 0.55, { sw: 3.2 }) : ""}</span><span>${label}</span></div>`;
}

const SHIRTS = [["#F0AE34", "노랑"], ["#3F92D2", "하늘"], ["#6BB85A", "초록"], ["#D25A49", "빨강"], ["#EE8FA8", "분홍"]];
const CAR_COLORS = [["#E3604F", "빨강"], ["#3F92D2", "하늘"], ["#F0AE34", "노랑"], ["#6BB85A", "초록"], ["#F2F2EC", "하양"]];
const HAIRS = [["basic", "기본"], ["pigtails", "양갈래"], ["long", "긴 머리"], ["curly", "곱슬"]];

const EXTRA_CSS = `
.sw-item{display:flex;flex-direction:column;align-items:center;gap:6px;font-size:17px;font-weight:700;color:var(--ink);min-width:64px;}
.sw-dot{border-radius:50%;border:3px solid var(--line);display:grid;place-items:center;color:#1D2A22;}
.sw-item.on .sw-dot{border-color:var(--forest);box-shadow:0 0 0 3px var(--paper),0 0 0 6px var(--forest);}
.opt-label{font-size:20px;font-weight:800;color:var(--ink);min-width:92px;}
.tile{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;border:2.5px solid var(--line);border-radius:16px;background:var(--card);font-size:17px;font-weight:700;color:var(--ink);}
.tile.on{border:3px solid var(--forest);background:var(--leaf-soft);}
.tile.lock{opacity:.55;}
.who{display:flex;align-items:center;gap:8px;border:2.5px solid var(--line);border-radius:999px;padding:4px 16px 4px 4px;font-size:19px;font-weight:800;background:var(--card);min-height:52px;}
.who.on{border-color:var(--forest);background:var(--leaf-soft);}
.who .av{width:42px;height:42px;border-radius:50%;background:var(--sky-soft);overflow:hidden;display:grid;place-items:center;}
.dl{display:grid;grid-template-columns:104px 1fr;gap:10px 14px;padding:16px 18px;font-size:18px;line-height:1.45;}
.dl dt{font-weight:800;color:var(--ink);} .dl dd{margin:0;color:var(--ink);font-weight:600;}
.toggle{width:84px;height:48px;border-radius:999px;background:var(--line);position:relative;flex:none;border:2px solid var(--ink2);}
.toggle::after{content:"";position:absolute;left:5px;top:5px;width:34px;height:34px;border-radius:50%;background:var(--card);border:2px solid var(--ink2);}
.toggle.on{background:var(--forest);border-color:var(--forest);} .toggle.on::after{left:41px;border-color:var(--forest);}
.consent{display:flex;align-items:center;gap:12px;padding:10px 12px 10px 18px;}
.consent .what{flex:1;min-width:0;font-size:19px;font-weight:800;line-height:1.35;}
.consent .what small{display:block;font-size:16px;font-weight:600;color:var(--ink2);}
.step{display:flex;gap:12px;align-items:flex-start;font-size:19px;line-height:1.4;font-weight:700;}
.step i{font-style:normal;flex:none;width:34px;height:34px;border-radius:50%;background:var(--leaf-soft);color:var(--ink);display:grid;place-items:center;font-weight:800;font-size:18px;}
.code{font-size:34px;font-weight:800;letter-spacing:.08em;font-variant-numeric:tabular-nums;color:var(--ink);}
.list-row{display:flex;align-items:center;gap:12px;padding:11px 0;border-top:2px solid var(--line);font-size:18px;font-weight:700;}
.list-row:first-child{border-top:0;}
.list-row .pts{margin-left:auto;font-variant-numeric:tabular-nums;font-weight:800;}
.bar{height:16px;border-radius:999px;background:var(--leaf-soft);overflow:hidden;} .bar i{display:block;height:100%;background:var(--forest);border-radius:999px;}
.stk{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;border-radius:18px;background:var(--card);border:2.5px solid var(--line);position:relative;font-size:18px;font-weight:800;}
.stk.q{border-style:dashed;border-color:var(--ink2);background:transparent;color:var(--ink2);}
.stk.q b{font-size:52px;line-height:1;color:var(--ink2);}
.stk .badge{position:absolute;top:8px;left:8px;font-size:15px;padding:3px 10px;}
.tabs{display:flex;gap:10px;}
.tab{min-height:48px;padding:0 20px;border-radius:999px;display:inline-flex;align-items:center;font-size:19px;font-weight:800;border:2.5px solid var(--line);color:var(--ink);}
.tab.on{background:var(--forest);border-color:var(--forest);color:var(--on-forest);}
.sw{display:flex;flex-direction:column;gap:14px;padding:12px 20px 18px;}
.sw-brand{display:flex;align-items:center;gap:8px;font-size:16px;font-weight:800;color:var(--ink2);}
.webtabs{margin-top:auto;display:grid;grid-template-columns:repeat(4,1fr);border-top:2px solid var(--line);background:var(--card);flex:none;}
.webtabs span{display:flex;flex-direction:column;align-items:center;gap:4px;padding:10px 0 16px;font-size:14px;font-weight:800;color:var(--ink2);}
.webtabs span.on{color:var(--forest);}
.pending{display:flex;gap:12px;align-items:center;background:var(--sky-soft);border-radius:16px;padding:12px 14px;font-size:16px;font-weight:700;line-height:1.4;color:var(--ink);}
.pending b{display:block;font-size:17px;font-weight:800;}
.lmcard{display:flex;flex-direction:column;gap:14px;padding:20px;}
`;

// ---------------------------------------------------------------------------
// 2. 시안별 화면
// ---------------------------------------------------------------------------
const SHEETS = [];
const def = (o) => SHEETS.push(o);
const EYEBROW = "같이가숲 화면 시안 · 11/15 기획서용";

// 01 공유 링크 수신자 웹 -------------------------------------------------------
function coarseMap(theme, w = 306, h = 150) {
  const t = TOKENS[theme];
  let s = `<svg viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" aria-hidden="true" style="display:block;border-radius:14px"><rect width="${w}" height="${h}" fill="${t.map}"/>`
    + `<path d="M0 ${h * 0.62} C ${w * 0.3} ${h * 0.5}, ${w * 0.55} ${h * 0.86}, ${w} ${h * 0.7} L ${w} ${h * 0.82} C ${w * 0.55} ${h * 0.98}, ${w * 0.3} ${h * 0.62}, 0 ${h * 0.74} Z" fill="${t.mapWater}"/>`
    + `<path d="M${w * 0.08} ${h} C ${w * 0.3} ${h * 0.55}, ${w * 0.6} ${h * 0.5}, ${w * 0.92} 0" stroke="${t.mapRoad}" stroke-width="10" fill="none" stroke-linecap="round"/>`
    + `<path d="M0 ${h * 0.25} L ${w} ${h * 0.35}" stroke="${t.mapRoad}" stroke-width="6" fill="none"/>`;
  for (let x = 3; x < w; x += 50) s += `<path d="M${x} 0V${h}" stroke="${t.ink2}" stroke-opacity=".35" stroke-width="1.5" stroke-dasharray="4 5"/>`;
  for (let y = 0; y < h; y += 50) s += `<path d="M0 ${y}H${w}" stroke="${t.ink2}" stroke-opacity=".35" stroke-width="1.5" stroke-dasharray="4 5"/>`;
  s += `<rect x="153" y="50" width="50" height="50" fill="${t.forest}" fill-opacity=".22" stroke="${t.forest}" stroke-width="3.5"/>`
    + `<rect x="6" y="${h - 27}" width="98" height="20" rx="10" fill="${t.card}"/><path d="M14 ${h - 17}h50" stroke="${t.ink}" stroke-width="3"/><path d="M14 ${h - 21}v8M64 ${h - 21}v8" stroke="${t.ink}" stroke-width="2.5"/><text x="98" y="${h - 12.5}" font-size="12" font-weight="800" fill="${t.ink}" text-anchor="end">5km</text>`
    + `</svg>`;
  return s;
}
function shareBody(theme, loc) {
  const brand = `<div class="sw-brand">${imgH(art("plant_sprout", theme), 26)}같이가숲 · 기다리는 가족 보기</div>`;
  if (loc === "ended") {
    const fam = ["guardian_a", "child", "guardian_b"].map((n) => imgH(art(n, theme), n === "child" ? 92 : 116)).join("");
    return `<div class="sw" style="height:100%">${brand}
<div style="display:flex;align-items:flex-end;justify-content:center;gap:4px;margin-top:26px">${fam}${imgH(art("house", theme), 100, 'style="margin-left:6px"')}</div>
<h2 class="h2" style="font-size:28px;text-align:center;margin-top:8px">하늘이네가 도착했어요</h2>
<p class="sub" style="margin:0;text-align:center">공유가 끝나서 서버에 있던<br>마지막 상태도 지웠어요.</p>
<div class="card" style="padding:16px 18px;display:flex;gap:12px;align-items:center;margin-top:10px">${icon("lock", 30)}<div><div style="font-size:19px;font-weight:800">이 링크는 이제 열리지 않아요</div><div class="small">오후 1:00 도착 · 공유 끝</div></div></div>
<div class="small" style="text-align:center">보호자가 공유를 멈췄을 때나 24시간이 지났을 때도 이렇게 보여요.</div>
</div>`;
  }
  const head = `${brand}
<h2 class="h2" style="font-size:27px">하늘이네 차가 오고 있어요</h2>
<div class="row" style="gap:14px;align-items:flex-end"><div class="num" style="font-size:78px">62<small>%</small></div><div style="padding-bottom:10px"><div style="font-size:23px;font-weight:800">약 48분 남았어요</div><div class="small">2분 전 소식</div></div></div>
<div class="roadbox" style="height:100px">${roadSvg(theme, 350, 100, { pct: 62, cars: [{ pct: 62, href: art("car_stage50", theme) }] })}</div>`;
  const locCard = loc === "on"
    ? `<div class="card" style="padding:14px 16px;display:flex;flex-direction:column;gap:10px">
<div class="row" style="gap:8px">${icon("pin", 24)}<b style="font-size:19px">대략적인 위치</b><span class="grow"></span><span class="badge on">켜짐</span></div>
${coarseMap(theme)}
<div class="small" style="color:var(--ink)">초록 칸 안 어딘가에 있어요 (가로세로 약 5km)</div>
<div class="small">정확한 위치와 지나온 길은 보내지 않아요</div></div>`
    : `<div class="card" style="padding:16px;display:flex;flex-direction:column;gap:12px">
<div class="row" style="gap:8px">${icon("eyeOff", 24)}<b style="font-size:19px">대략적인 위치는 꺼져 있어요</b></div>
<div class="note-line" style="color:var(--ink);align-items:flex-start">${icon("check", 22, { sw: 3 })}<span><b>보이는 것</b><br>진행률, 남은 시간</span></div>
<div class="note-line" style="color:var(--ink);align-items:flex-start">${icon("x", 22, { sw: 3 })}<span><b>보내지 않는 것</b><br>위치, 지나온 길, 아이 정보</span></div>
<div class="small">보호자가 주차 중에 켜면 약 5km 칸으로만 보여요.</div></div>`;
  const expiry = `<div class="card" style="padding:14px 16px;display:flex;gap:12px;align-items:flex-start">${icon("clock", 26)}<div><div style="font-size:18px;font-weight:800;line-height:1.4">도착하면 이 링크는 저절로 닫혀요</div><div class="small">늦어도 내일 오전 9:00 만료 · 보호자가 언제든 멈출 수 있어요</div></div></div>`;
  return `<div class="sw">${head}${locCard}${expiry}</div>`;
}
def({
  file: "01-share-web",
  doc: "3-B",
  title: "공유 링크를 받은 가족의 웹 화면",
  chips: chip("plan", "PT 단계 구현") + chip("park", "폰 웹 · 앱 설치 없음"),
  render: (theme) => ({
    device: `<div style="display:flex;gap:30px">${phone({ body: shareBody(theme, "off"), bar: "같이가숲 · 공유 보기", label: "기본: 진행률과 남은 시간만" })}${phone({ body: shareBody(theme, "on"), bar: "같이가숲 · 공유 보기", label: "보호자가 대략 위치를 켰을 때" })}${phone({ body: shareBody(theme, "ended"), bar: "같이가숲 · 공유 보기", label: "도착·만료 뒤" })}</div>`,
    notes: notesBlock([
      "할머니처럼 기다리는 가족은 <b>링크만 열면</b> 진행률과 남은 시간을 봅니다. 앱을 깔지 않습니다.",
      "대략 위치는 보호자가 주차 중에 켤 때만 보냅니다. 차 안에서 <b>약 5km 칸</b>으로 뭉갠 뒤 보내고, 주소로 바꾸지 않습니다.",
      "공유 ID는 128비트 랜덤 읽기 전용입니다. <b>도착하면 바로, 늦어도 24시간 뒤</b> 닫히고 서버 기록을 지웁니다.",
      "주소창 자리에는 화면 이름만 그렸습니다. 링크에 이름이나 위치 같은 개인정보를 넣지 않습니다.",
      "지도 바탕은 시안용 그림입니다. 실제 지도 바탕은 PT 단계에서 정합니다.",
    ], "기획서: 3-B 내 차량 위치 공유 · 4 사용 시나리오(할머니가 링크로 확인) · 7 참고자료"),
  }),
});

// 02 공유 동의 (차, 주차 중) -----------------------------------------------------
def({
  file: "02-share-consent",
  doc: "3-B",
  title: "위치 공유 동의 화면",
  chips: chip("plan", "PT 단계 구현") + chip("park", "차 화면 · 주차 중에만"),
  render: (theme) => {
    const app = `<div class="panel" style="gap:12px">
<div class="row" style="gap:12px"><span style="width:48px;height:48px;border-radius:50%;background:var(--leaf-soft);display:grid;place-items:center;color:var(--forest)">${icon("link", 28)}</span><h2 class="h2" style="font-size:28px">가족에게 진행 상황을 보낼까요?</h2></div>
<div class="sub" style="margin-top:-4px">보호자가 읽고 직접 눌러 주세요.</div>
<dl class="card dl" style="margin:0">
<dt>보내는 것</dt><dd>진행률, 남은 시간, 공유 이름 "하늘이네"</dd>
<dt>쓰는 곳</dt><dd>기다리는 가족이 도착 시간을 알 수 있게</dd>
<dt>보관 기간</dt><dd>도착하면 바로 삭제, 늦어도 24시간<br>서버에는 마지막 상태 1개만</dd>
<dt>받는 사람</dt><dd>보호자가 링크를 보낸 사람(제3자 제공)</dd>
</dl>
<div class="card" style="display:flex;align-items:center;gap:12px;padding:12px 16px">${icon("pin", 28)}<div class="grow"><div style="font-size:19px;font-weight:800">대략 위치(약 5km 칸)도 보내기</div><div class="small">선택 · 처음에는 꺼져 있어요</div></div><span class="toggle"></span></div>
<div class="card consent"><div class="what">개인정보 수집·이용<small>위 내용으로 진행 정보를 모아요</small></div><span class="btn sm done">${icon("check", 22, { sw: 3 })}동의함</span></div>
<div class="card consent"><div class="what">제3자 제공<small>링크를 받은 가족이 볼 수 있어요</small></div><span class="btn sm">눌러서 동의</span></div>
<div class="note-line" style="margin-top:auto;font-size:17px">${icon("info", 22)}두 가지 모두 눌러야 링크를 만들어요. "공유 중단"을 누르면 바로 멈추고 지워요.</div>
<div class="row" style="gap:12px"><span class="btn ghost" style="flex:1">나중에</span><span class="btn primary off" style="flex:1.6">링크 만들기</span></div>
</div>`;
    return {
      device: carScreen(theme, { gear: "P", speed: 0, app }),
      notes: notesBlock([
        "항목·쓰는 곳·보관 기간·제3자 제공을 한 화면에 짧게 보여 줍니다.",
        "<b>버튼을 눌러야만</b> 동의가 됩니다. 미리 켜진 체크는 없고, 두 동의를 모두 눌러야 \"링크 만들기\"가 켜집니다.",
        "대략 위치는 따로 고르는 선택 항목이고 처음에는 꺼져 있습니다.",
        "아이 정보는 보내지 않습니다. 공유 이름은 보호자가 정합니다.",
      ], "기획서: 3-B 위치 공유 · 주행 안전 설계(동의는 주차 중)", drivingMini(theme, "주행이 시작되면 동의 화면은 바로 닫히고 진행 화면으로 돌아갑니다. 동의는 저장하지 않고, 주차하면 처음부터 다시 묻습니다.")),
    };
  },
});

// 03a 부모 폰 연결 QR (차, 주차 중) ---------------------------------------------
def({
  file: "03a-link-qr",
  doc: "3-B",
  title: "부모 폰 연결 (차 화면 QR)",
  chips: chip("plan", "PT 단계 구현") + chip("park", "차 화면 · 주차 중에만"),
  render: (theme) => {
    const app = `<div class="panel">
<div class="row" style="gap:12px"><span style="width:48px;height:48px;border-radius:50%;background:var(--leaf-soft);display:grid;place-items:center;color:var(--forest)">${icon("phone", 28)}</span><h2 class="h2">부모 폰을 연결해요</h2></div>
<div class="sub" style="margin-top:-6px">폰 카메라로 찍으면 앱 설치 없이 웹으로 열려요.</div>
<div class="row" style="gap:20px;align-items:flex-start">
<div class="card" style="padding:12px;border-radius:22px"><img src="${newArt("qr_pattern")}" width="212" height="212" alt="" style="display:block"></div>
<div style="display:flex;flex-direction:column;gap:16px;padding-top:6px" class="grow">
<div class="step"><i>1</i><span>폰 카메라로 QR 찍기</span></div>
<div class="step"><i>2</i><span>보호자 계정으로 로그인</span></div>
<div class="step"><i>3</i><span>이 화면에서 "연결" 누르기</span></div>
<div class="small" style="border-top:2px solid var(--line);padding-top:10px">연결하면 폰으로 아이 보기, 숲·캐릭터 꾸미기, 기록 보기를 할 수 있어요.</div>
</div></div>
<div class="card" style="padding:14px 18px;display:flex;align-items:center;gap:16px"><div class="grow"><div class="small">QR이 안 되면 코드로</div><div class="code">482 913</div></div><div class="note-line" style="font-size:16px;max-width:200px">${icon("refresh", 22)}3분마다 새 코드로 바뀌어요</div></div>
<div class="card" style="padding:12px 18px">
<div class="small" style="margin-bottom:4px">연결된 기기</div>
<div class="list-row" style="padding:4px 0">${icon("tablet", 26)}<span>뒷좌석 태블릿 · 아이 보기만</span><span class="btn sm ghost" style="margin-left:auto">끊기</span></div>
</div>
<div class="row" style="margin-top:auto"><span class="btn ghost" style="flex:1">닫기</span></div>
</div>`;
    return {
      device: carScreen(theme, { gear: "P", speed: 0, app }),
      notes: notesBlock([
        "주차 중 차 화면의 QR을 부모 폰으로 찍으면 <b>보호자 계정</b>이 연결됩니다.",
        "차 화면에서 한 번 더 \"연결\"을 눌러야 끝납니다. 차에 앉은 보호자만 연결할 수 있게 하려는 것입니다.",
        "코드는 몇 분마다 바뀌고, 연결된 기기는 여기서 언제든 끊을 수 있습니다.",
        "QR은 시안용 무늬이며 아무 정보도 들어 있지 않습니다.",
      ], "기획서: 3-B 부모 폰·태블릿 연동(#20) · 5 개발 계획(PT 단계)", drivingMini(theme, "주행 중에는 QR과 코드를 숨기고 진행 화면만 보여 줍니다. 연결은 주차 중에만 합니다.")),
    };
  },
});

// 03b 아이 보기 (태블릿 + 폰) ---------------------------------------------------
function webHeader(theme, title) {
  return `<div style="padding:4px 18px 0;display:flex;flex-direction:column;gap:10px">
<div class="warn">${icon("wheel", 24)}운전 중인 분은 사용하지 마세요</div>
<div class="row" style="gap:8px"><span style="width:10px;height:10px;border-radius:50%;background:var(--forest)"></span><span style="font-size:15px;font-weight:800;color:var(--ink2)">하늘이네 차와 연결됨</span></div>
<h2 class="h2" style="font-size:26px">${title}</h2></div>`;
}
function webTabs(active) {
  const tabs = [["tablet", "아이 보기"], ["leaf", "숲 꾸미기"], ["shirt", "캐릭터"], ["book", "기록"]];
  return `<nav class="webtabs">${tabs.map(([ic, l]) => `<span class="${l === active ? "on" : ""}">${icon(ic, 24)}${l}</span>`).join("")}</nav>`;
}
def({
  file: "03b-link-kid-view",
  doc: "3-B",
  title: "아이 보기 (뒷좌석 태블릿·부모 폰)",
  chips: chip("plan", "PT 단계 구현") + chip("drive", "주행 중에도 동승자가 봄 · 정지 그림"),
  render: (theme) => {
    const tw = TABLET.w - 64, troad = TABLET.h - 64 - 56 - 130 - 40;
    const tab = `<div style="padding:24px 32px 32px;display:flex;flex-direction:column;gap:18px;height:100%">
<div class="row" style="gap:10px"><span style="width:12px;height:12px;border-radius:50%;background:var(--forest)"></span><span style="font-size:18px;font-weight:800;color:var(--ink2)">하늘이네 차와 연결됨 · 보기 전용</span><span class="grow"></span><div class="warn" style="padding:8px 14px">${icon("wheel", 22)}운전 중인 분은 사용하지 마세요</div></div>
<div class="row" style="gap:24px"><div class="num" style="font-size:120px">62<small>%</small></div><div class="grow"><div class="row" style="gap:12px">${imgH(art("icon_cartoon", theme), 56)}<span class="kid" style="font-size:42px">만화 두 편 반 보면 도착해요</span></div><div class="sub" style="font-size:22px">약 48분 남음</div></div></div>
<div class="roadbox" style="height:${troad}px">${roadSvg(theme, tw, troad, { pct: 62, cars: [{ pct: 62, href: art("car_stage50", theme) }] })}</div>
</div>`;
    const pw = PHONE.w - 36, proad = 470;
    const ph = `${webHeader(theme, "아이 보기")}
<div style="padding:8px 18px 0;display:flex;flex-direction:column;gap:6px">
<div class="row" style="gap:12px"><div class="num" style="font-size:64px">62<small>%</small></div><div><div style="font-size:21px;font-weight:800;line-height:1.35">만화 두 편 반<br>보면 도착해요</div></div></div>
<div class="roadbox" style="height:${proad - 80}px">${roadSvg(theme, pw, proad - 80, { pct: 62, cars: [{ pct: 62, href: art("car_stage50", theme) }] })}</div>
</div>${webTabs("아이 보기")}`;
    return {
      device: `<div style="display:flex;gap:30px;align-items:flex-start">${tablet({ body: tab, label: "뒷좌석 태블릿(가로) · 웹에서 \"아이 보기\"" })}${phone({ body: ph, bar: "같이가숲 · 하늘이네 가족", label: "부모 폰 웹" })}</div>`,
      notes: notesBlock([
        "뒷좌석 아이는 태블릿으로 <b>차와 같은 진행 화면</b>을 봅니다. 웹이라 따로 앱을 깔지 않습니다.",
        "차 화면과 같은 그림, 같은 규칙입니다. 차가 달리는 동안에는 그림이 움직이지 않고 1%씩 바뀝니다.",
        "웹 맨 위에 늘 \"운전 중인 분은 사용하지 마세요\"를 둡니다.",
      ], "기획서: 3-B 부모 폰·태블릿 연동 · 3-A 진행 화면(같은 그림 재사용)"),
    };
  },
});

// 03c 폰에서 숲·캐릭터 꾸미기 ---------------------------------------------------
def({
  file: "03c-link-phone-decorate",
  doc: "3-B · 3-D",
  title: "폰으로 숲·캐릭터 꾸미기와 기록",
  chips: chip("plan", "PT 단계 구현") + chip("drive", "동승자가 주행 중에도 · 차에는 다음 정차 때"),
  render: (theme) => {
    const pending = (n) => `<div class="pending">${icon("refresh", 26)}<div><b>다음 정차 때 차 화면에 반영돼요</b>바꾼 것 ${n}개가 기다리고 있어요</div></div>`;
    const tray = [["plant_tree", "나무"], ["flower_red", "꽃"], ["animal_squirrel", "다람쥐"], ["animal_rabbit", "토끼"], ["animal_bird", "새"]].map(([n, l], i) => {
      const src = n.startsWith("flower") ? newArt(n) : art(n, theme);
      const lock = n === "animal_bird";
      return `<div class="tile${i === 1 ? " on" : ""}${lock ? " lock" : ""}" style="width:62px;height:76px;font-size:14px;position:relative">${imgH(src, 38)}<span>${l}</span>${lock ? `<span style="position:absolute;top:4px;right:4px">${icon("lock", 16, { sw: 2.6 })}</span>` : ""}</div>`;
    }).join("");
    const forest = `${webHeader(theme, "하늘이의 숲 꾸미기")}
<div style="padding:8px 18px 0;display:flex;flex-direction:column;gap:12px">
${forestScene(theme, PHONE.w - 36, Math.round((PHONE.w - 36) * 330 / 552))}
<div class="small" style="margin-top:-4px">꽃을 골랐어요. 빈 자리를 눌러 주세요.</div>
<div style="display:flex;justify-content:space-between">${tray}</div>
${pending(2)}
</div>${webTabs("숲 꾸미기")}`;
    const lab = (t) => `<span style="font-size:16px;font-weight:800;width:30px;flex:none">${t}</span>`;
    const hairTiles = HAIRS.map(([k, l]) => `<div class="tile${k === "pigtails" ? " on" : ""}" style="width:72px;height:74px;font-size:14px;gap:2px">${kidWithHair(theme, k, 46, { crop: "12 6 96 96" })}<span>${l}</span></div>`).join("");
    const sw = (list, on) => list.map(([c, l], i) => swatch(c, l, i === on, 34)).join("");
    const chr = `${webHeader(theme, "캐릭터 꾸미기")}
<div style="padding:8px 18px 0;display:flex;flex-direction:column;gap:12px">
<div class="card" style="display:flex;align-items:flex-end;justify-content:center;gap:18px;padding:10px 0 8px;background:var(--scene-sky);border:0">${kidWithHair(theme, "pigtails", 120, { shirt: "#6BB85A" })}${carWithHair(theme, "pigtails", 92, { shirtKid: "#6BB85A", carBody: "#3F92D2" })}</div>
<div class="row" style="gap:6px">${lab("머리")}<div style="display:flex;gap:6px">${hairTiles}</div></div>
<div class="row" style="gap:6px">${lab("옷")}<div class="row" style="gap:0;justify-content:space-between;flex:1">${sw(SHIRTS, 2)}</div></div>
<div class="row" style="gap:6px">${lab("차")}<div class="row" style="gap:0;justify-content:space-between;flex:1">${sw(CAR_COLORS, 1)}</div></div>
${pending(3)}
</div>${webTabs("캐릭터")}`;
    const trip = (d, name, km, sprouts, pts) => `<div class="list-row" style="align-items:flex-start;padding:10px 0"><span class="small" style="width:44px">${d}</span><span style="flex:1;min-width:0;line-height:1.4">${name}<br><span class="small">${km}</span></span><span style="text-align:right;line-height:1.4">새싹 ${sprouts}<br><span class="small">${pts}</span></span></div>`;
    const rec = `${webHeader(theme, "기록")}
<div style="padding:8px 18px 0;display:flex;flex-direction:column;gap:12px">
<div class="card" style="padding:14px 16px;display:flex;align-items:center;gap:14px">${imgH(art("plant_tree", theme), 72)}<div class="grow"><div style="font-size:19px;font-weight:800">하늘이의 숲</div><div class="small" style="color:var(--ink)">나무 12그루 · 새싹 4개</div><div class="row" style="gap:6px;margin-top:4px">${imgH(art("animal_squirrel", theme), 30)}${imgH(art("animal_rabbit", theme), 32)}<span class="small">다람쥐, 토끼가 왔어요</span></div></div></div>
<div class="card" style="padding:4px 16px">
${trip("11/1", "할머니 댁", "부드럽게 달린 구간 90%", 54, "+360P")}
${trip("10/26", "데모 주행", "데모 기록", 18, "0P")}
${trip("10/19", "바닷가", "부드럽게 달린 구간 95%", 61, "+410P")}
</div>
<div class="card" style="padding:14px 16px;display:flex;flex-direction:column;gap:8px"><div class="row"><span style="font-size:17px;font-weight:800">보호자 포인트</span><span class="grow"></span><span class="num" style="font-size:30px">5,240<small>P</small></span></div><div class="row" style="gap:8px"><span class="badge example" style="font-size:14px">제휴 시 · 예시</span><span class="small">5,000P부터 현대차 물품 구매권으로</span></div></div>
</div>${webTabs("기록")}`;
    return {
      device: `<div style="display:flex;gap:30px">${phone({ body: forest, bar: "같이가숲 · 하늘이네 가족", label: "숲 꾸미기" })}${phone({ body: chr, bar: "같이가숲 · 하늘이네 가족", label: "캐릭터 꾸미기" })}${phone({ body: rec, bar: "같이가숲 · 하늘이네 가족", label: "숲·포인트 기록" })}</div>`,
      notes: notesBlock([
        "동승자는 폰 웹으로 <b>주행 중에도</b> 숲과 캐릭터를 꾸밀 수 있습니다.",
        "바꾼 것은 바로 차 화면에 나오지 않고 <b>다음 정차 때</b> 반영됩니다. 운전 중에 차 화면 그림이 갑자기 바뀌지 않게 하려는 것입니다.",
        "웹 맨 위에 늘 \"운전 중인 분은 사용하지 마세요\"를 둡니다.",
        "기록에서는 여행별 부드럽게 달린 거리와 새싹, 보호자 포인트를 봅니다. 속도나 걸린 시간은 보여 주지 않습니다.",
        "새는 아직 잠겨 있습니다(누적 나무 25그루 예시). 숲 자리·꾸미기 부품은 차 화면 시안(04, 05)과 같은 그림입니다.",
      ], "기획서: 3-B 부모 폰·태블릿 연동 · 3-C 숲과 포인트 · 3-D 꾸미기"),
    };
  },
});

// 04 숲 꾸미기 (차, 주차 중) -----------------------------------------------------
def({
  file: "04-forest-decorate",
  doc: "3-D",
  title: "숲 꾸미기 (정해진 자리 9곳)",
  chips: chip("plan", "PT 단계 구현") + chip("park", "차 화면 · 주차 중에만"),
  render: (theme) => {
    const items = [["plant_tree", "나무"], ["flower_red", "꽃"], ["animal_squirrel", "다람쥐"], ["animal_rabbit", "토끼"], ["animal_bird", "새"], ["", "빼기"]];
    const tray = items.map(([n, l], i) => {
      if (!n) return `<div class="tile" style="width:82px;height:108px">${icon("minus", 40, { sw: 3 })}<span>${l}</span></div>`;
      const src = n.startsWith("flower") ? newArt(n) : art(n, theme);
      const lock = n === "animal_bird";
      return `<div class="tile${i === 1 ? " on" : ""}${lock ? " lock" : ""}" style="width:82px;height:108px;position:relative">${imgH(src, 56)}<span>${l}</span>${lock ? `<span style="position:absolute;top:6px;right:6px">${icon("lock", 20, { sw: 2.6 })}</span>` : ""}</div>`;
    }).join("");
    const app = `<div class="panel">
<div class="row"><h2 class="h2 grow">하늘이의 숲 꾸미기</h2><span class="badge offb">나무 12그루</span></div>
<div class="sub" style="margin-top:-6px">꽃을 골랐어요. 빈 자리를 눌러 주세요.</div>
${forestScene(theme, W, 330)}
<div style="display:flex;justify-content:space-between">${tray}</div>
<div class="row" style="gap:12px;margin-top:auto"><span class="btn ghost">${icon("speaker", 24)}소리 켜짐</span><span class="btn ghost" style="flex:1">처음 모습으로</span><span class="btn primary" style="flex:1.2">다 했어요</span></div>
</div>`;
    return {
      device: carScreen(theme, { gear: "P", speed: 0, app }),
      notes: notesBlock([
        "숲에는 <b>정해진 자리 9곳</b>이 있습니다. 끌어다 놓지 않고 \"고르기 → 자리 누르기\" 두 번 탭으로 놓습니다. 5~9세도 쉽게 할 수 있습니다.",
        "빈 자리는 점선과 +로 보여 줍니다. 놓을 수 있는 것은 나무·꽃·동물이고, 잠긴 동물은 자물쇠와 함께 흐리게 둡니다.",
        "버튼은 그림과 글자를 함께 두고, 터치 영역은 모두 48dp 이상입니다.",
        "동물 소리는 처음 놓을 때 한 번만, 주차 중에만 납니다. \"소리\" 버튼으로 끌 수 있습니다.",
      ], "기획서: 3-D 꾸미기 · 3-C 아이 계정의 숲", drivingMini(theme, "주행이 시작되면 꾸미기를 바로 닫고 진행 화면으로 돌아갑니다. 놓던 것은 저장해 두고 주차하면 이어서 합니다.")),
    };
  },
});

// 05 캐릭터 꾸미기 (차, 주차 중) -------------------------------------------------
def({
  file: "05-character-decorate",
  doc: "3-D",
  title: "캐릭터 꾸미기 (머리 모양·옷 색·차 색)",
  chips: chip("plan", "PT 단계 구현") + chip("park", "차 화면 · 주차 중에만"),
  render: (theme) => {
    const head = (name, crop) => `<span class="av"><svg viewBox="${crop}" width="42" height="42" aria-hidden="true"><image href="${art(name, theme)}" x="0" y="0" width="${artSize(name)[0]}" height="${artSize(name)[1]}"/></svg></span>`;
    const who = `<div class="row" style="gap:10px"><span class="who on">${head("child", "22 18 76 76")}하늘이</span><span class="who">${head("guardian_a", "20 22 90 90")}보호자 A</span><span class="who">${head("guardian_b", "16 12 98 98")}보호자 B</span></div>`;
    const preview = `<div style="display:flex;align-items:flex-end;justify-content:center;gap:40px;background:var(--scene-sky);border-radius:20px;height:204px;padding-bottom:10px;position:relative;overflow:hidden">
<div style="position:absolute;left:0;right:0;bottom:0;height:40px;background:var(--hill-front)"></div>
<div style="position:relative">${kidWithHair(theme, "pigtails", 176, { shirt: "#6BB85A" })}</div>
<div style="position:relative">${carWithHair(theme, "pigtails", 150, { shirtKid: "#6BB85A", carBody: "#3F92D2" })}</div></div>`;
    const hairTiles = HAIRS.map(([k, l]) => `<div class="tile${k === "pigtails" ? " on" : ""}" style="width:100px;height:100px">${kidWithHair(theme, k, 64, { crop: "12 6 96 96" })}<span>${l}</span></div>`).join("");
    const app = `<div class="panel" style="gap:14px">
${who}
${preview}
<div class="row" style="gap:12px"><span class="opt-label">머리 모양</span><div style="display:flex;gap:10px">${hairTiles}</div></div>
<div class="row" style="gap:12px"><span class="opt-label">옷 색</span><div class="row" style="gap:14px">${SHIRTS.map(([c, l], i) => swatch(c, l, i === 2)).join("")}</div></div>
<div class="row" style="gap:12px"><span class="opt-label">차 색</span><div class="row" style="gap:14px">${CAR_COLORS.map(([c, l], i) => swatch(c, l, i === 1)).join("")}</div></div>
<div class="row" style="gap:12px;margin-top:auto"><span class="btn ghost" style="flex:1">처음 모습으로</span><span class="btn primary" style="flex:1.4">이렇게 할래요</span></div>
</div>`;
    return {
      device: carScreen(theme, { gear: "P", speed: 0, app }),
      notes: notesBlock([
        "누구를 꾸밀지 고른 뒤 <b>머리 모양, 옷 색, 차 색</b>을 고릅니다. 고른 것은 위 그림에 바로 보입니다.",
        "그림은 레이어로 나뉘어 있어 색 역할(옷, 차 몸통)만 바꿔 칠하고, 머리 모양은 레이어를 더해 바꿉니다.",
        "색은 동그라미와 이름을 함께 두어 색만으로 구분하지 않게 했습니다.",
        "차 색은 우리 가족 차에만 적용됩니다. 지붕 화분과 문짝 잎 무늬는 그대로라 \"우리 차\"로 알아봅니다.",
      ], "기획서: 3-D 가족 캐릭터 꾸미기", drivingMini(theme, "주행 중에는 꾸미기를 닫습니다. 고른 모습은 다음 정차 때부터 진행 화면의 그림에 쓰입니다.")),
    };
  },
});

// 06 일행 모드 (차, 주행 중) -----------------------------------------------------
def({
  file: "06-group-mode",
  doc: "3-B · 6",
  title: "일행 모드 (여러 차를 한 길에)",
  chips: chip("plan", "확장 계획 · 2단계") + chip("drive", "차 화면 · 주행 중(정지 그림)"),
  render: (theme) => {
    const cars = [
      { pct: 62, href: art("car_stage50", theme), label: "우리", mine: true },
      { pct: 36, href: friendCar(theme, 25, "#3F92D2"), label: "콩이네", scale: 0.86 },
      { pct: 83, href: friendCar(theme, 75, "#F0AE34"), label: "삼촌네", scale: 0.86 },
    ];
    const app = progressPanel(theme, { pct: 62, detail: "일행 3대 · 모두 할머니 댁으로 가요", cars });
    const narrow = `<div class="panel" style="width:400px;padding:16px;gap:12px">
<div class="row"><div class="num" style="font-size:72px">62<small>%</small></div><span class="grow"></span><span class="badge offb" style="font-size:18px">${icon("people", 20)}일행 3대</span></div>
<div class="roadbox" style="height:${CAR.h - 32 - 76 - 12 - 12 - 46}px">${roadSvg(theme, 368, CAR.h - 32 - 76 - 12 - 12 - 46, { pct: 62, cars: [{ pct: 62, href: art("car_stage50", theme) }] })}</div>
<div class="kid" style="font-size:32px">만화 두 편 반 남았어요</div></div>`;
    return {
      device: carScreen(theme, { gear: "D", speed: 92, app, eta: "도착 13:00 · 52km" }),
      notes: notesBlock([
        "함께 출발한 차들을 <b>한 길 위에</b> 보여 줍니다. 각 차의 진행률을 같은 길에 놓기 때문에 출발지가 달라도 됩니다.",
        "<b>순위나 \"먼저 도착\" 표시는 없습니다.</b> 빠른 도착을 보상하지 않는 원칙(과속 미화 금지)을 지킵니다.",
        "주행 중 규칙 그대로입니다. 그림은 멈춰 있고, 차들은 1%씩 전환 효과 없이 옮겨집니다(최소 15초 간격).",
        "다른 차의 정보는 각 가족이 동의한 공유 링크(진행률만)에서 받습니다.",
      ], "기획서: 3-B 일행 모드(확장) · 6 발전 계획 2단계", mini(narrow.replace('style="width:400px', 'style="width:600px'), "2:1의 좁은 창에서는 우리 차만 남기고 \"일행 3대\" 표시로 줄입니다.")),
    };
  },
});

// 07 포인트 지갑·교환 (차, 주차 중) -----------------------------------------------
def({
  file: "07-points-exchange",
  doc: "3-C · 6",
  title: "보호자 포인트 지갑과 교환",
  chips: chip("plan", "교환은 확장 계획 · 제휴 시") + chip("park", "차 화면 · 주차 중에만"),
  render: (theme) => {
    const app = `<div class="panel" style="gap:14px">
<div class="row"><h2 class="h2 grow">포인트 지갑</h2><span class="badge offb">${icon("people", 18)}보호자</span></div>
<div class="card" style="padding:18px 20px;display:flex;flex-direction:column;gap:10px">
<div class="row" style="align-items:flex-end;gap:10px"><div class="num" style="font-size:68px">5,240<small>P</small></div><div class="small" style="padding-bottom:12px">하늘이네 가족</div></div>
<div class="bar"><i style="width:100%"></i></div>
<div class="row"><span class="small" style="color:var(--ink)">5,000P부터 바꿀 수 있어요</span><span class="grow"></span><span class="small">이번 달 +1,860P</span></div>
</div>
<div class="card" style="padding:16px 18px;border:3px dashed var(--sun);display:flex;flex-direction:column;gap:12px">
<div class="row" style="gap:14px"><span style="width:64px;height:64px;border-radius:18px;background:var(--leaf-soft);display:grid;place-items:center;color:var(--forest)">${icon("gift", 38)}</span>
<div class="grow"><div style="font-size:22px;font-weight:800;line-height:1.35">현대차 물품 구매권 5,000원</div><div class="small">5,000P로 바꾸기</div></div></div>
<div class="row"><span class="badge example">제휴 시 · 예시</span><span class="grow"></span><span class="btn sm primary">바꾸기</span></div>
</div>
<div class="card" style="padding:6px 18px">
<div class="list-row"><span class="small" style="width:52px">11/1</span><span>할머니 댁</span><span class="pts">+360P</span></div>
<div class="list-row"><span class="small" style="width:52px">10/26</span><span>데모 주행</span><span class="small">데모는 쌓이지 않아요</span><span class="pts">0P</span></div>
<div class="list-row"><span class="small" style="width:52px">10/19</span><span>바닷가</span><span class="pts">+410P</span></div>
</div>
<div class="small" style="margin-top:auto">부드럽게 달린 1km = 1P · 실제 차량 데이터로 판정한 거리만<br>한 달 3,000P까지(예시)</div>
</div>`;
    const confirm = `<div style="position:relative;width:600px;height:720px">${app}<div style="position:absolute;inset:0;background:#000;opacity:.45"></div>
<div class="card" style="position:absolute;left:0;right:0;bottom:0;border-radius:28px 28px 0 0;padding:30px 28px 32px;display:flex;flex-direction:column;gap:18px">
<span class="badge example" style="align-self:flex-start;font-size:20px">제휴 시 · 예시</span>
<h2 class="h2" style="font-size:32px">5,000P를 현대차 물품 구매권 5,000원으로 바꿀까요?</h2>
<div class="sub">바꾸면 남는 포인트: 240P</div>
<div class="row" style="gap:12px"><span class="btn ghost" style="flex:1">그만두기</span><span class="btn primary" style="flex:1">바꾸기</span></div>
</div></div>`;
    return {
      device: carScreen(theme, { gear: "P", speed: 0, app }),
      notes: notesBlock([
        "포인트는 아이가 아니라 <b>보호자 계정</b>에 쌓입니다. 부드럽게 달린 1km = 1P = 1원, 여행마다 한 번.",
        "5,000P부터 \"현대차 물품 구매권 5,000원\"으로 바꿀 수 있습니다(글자로만, 로고 없음).",
        "<b>\"제휴 시 · 예시\"</b>를 교환 항목과 확인 창에 늘 붙입니다. 실제 교환은 제휴 뒤에 열립니다.",
        "데모 주행은 0P, 한 달 상한 3,000P(예시). 지갑은 주차 중에만 열립니다.",
      ], "기획서: 3-C 보호자 포인트 · 6 발전 계획 3단계·사업 모델", mini(confirm, "\"바꾸기\"를 누르면 한 번 더 묻습니다. 이 확인 창에도 \"제휴 시 · 예시\"를 둡니다.")),
    };
  },
});

// 08a 랜드마크 지나갈 때 (차, 주행 중) --------------------------------------------
function landmarkPassPanel(theme) {
  const roadH = 150;
  return `<div class="panel">
<div class="row"><div class="num" style="font-size:72px">6<small>%</small></div><div class="grow"></div><span style="color:var(--ink2)">${icon("speaker", 34)}</span><span class="btn sm">크게</span></div>
<div class="card lmcard">
<div style="display:flex;align-items:center;justify-content:center;gap:22px">
<img src="${newArt("landmark_namsan_tower")}" width="200" height="200" alt="" style="display:block">
<div style="display:flex;flex-direction:column;align-items:center;gap:6px;color:var(--forest)">${icon("arrowRight", 76, { sw: 3 })}<span style="font-size:26px;font-weight:800;color:var(--ink)">오른쪽</span></div>
</div>
<div class="kid" style="font-size:34px">오른쪽에 남산서울타워가 보여요</div>
<div class="sub" style="font-size:24px;color:var(--ink)">서울에서 가장 높은 곳에 있는 탑이에요</div>
</div>
<div class="roadbox" style="height:${roadH}px;margin-top:auto">${roadSvg(theme, W, roadH, { pct: 6, cars: [{ pct: 6, href: art("car_stage0", theme) }] })}</div>
</div>`;
}
def({
  file: "08a-landmark-pass",
  doc: "3-E",
  title: "랜드마크를 지나갈 때",
  chips: chip("plan", "확장 계획 · 2단계") + chip("drive", "차 화면 · 주행 중(정지 그림)"),
  dark: true,
  render: (theme) => ({
    device: carScreen(theme, { gear: "D", speed: 74, app: landmarkPassPanel(theme), eta: "도착 13:00 · 337km" }),
    notes: notesBlock([
      "주행 중 규칙을 지킵니다. <b>정지 그림 한 장 + 글 3줄 이하</b>(\"오른쪽\" 표시 포함), 움직임·전환 효과 없음.",
      "문구는 <b>보기만</b> 권합니다. 창문을 열거나 몸을 내밀라는 말은 쓰지 않습니다.",
      "같은 문장을 차 스피커로 한 번 읽어 줍니다(스피커 표시). 오디오 포커스를 지키고 음소거할 수 있습니다.",
      "왼쪽/오른쪽은 내비 현재 위치와 진행 방향으로 계산합니다. 지나가면 원래 진행 화면으로 바로 돌아갑니다.",
      "그림은 직접 그린 단순 스티커입니다. 사진이나 로고는 쓰지 않습니다.",
    ], "기획서: 3-E 랜드마크 도감과 설명(#18) · 주행 안전 설계"),
  }),
});

// 08b 랜드마크 도감 (차, 주차 중) --------------------------------------------------
def({
  file: "08b-landmark-book",
  doc: "3-E",
  title: "랜드마크 도감 넘겨 보기",
  chips: chip("plan", "확장 계획 · 2단계") + chip("park", "차 화면 · 주차 중에만"),
  render: (theme) => {
    const got = [["landmark_namsan_tower", "남산서울타워", true], ["landmark_han_river", "한강", true], ["landmark_geum_river", "금강", true], ["landmark_hanbit_tower", "한빛탑", false]];
    const cells = got.map(([f, l, isNew]) => `<div class="stk" style="height:146px"><img src="${newArt(f)}" width="92" height="92" alt="">${l}${isNew ? '<span class="badge new">새로</span>' : ""}</div>`)
      .concat(Array.from({ length: 5 }, () => `<div class="stk q" style="height:146px"><b>?</b><span style="font-size:16px">아직 못 만났어요</span></div>`)).join("");
    const app = `<div class="panel">
<div class="row">${icon("book", 34)}<h2 class="h2 grow">하늘이의 도감</h2><span class="badge offb" style="font-size:18px">모은 곳 4 / 12</span></div>
<div class="tabs"><span class="tab">이번 여행 3곳</span><span class="tab on">전체</span></div>
<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:12px">${cells}</div>
<div class="row" style="gap:12px;margin-top:auto"><span class="btn">${icon("chevronL", 24, { sw: 3 })}이전 쪽</span><span class="grow" style="text-align:center;font-size:20px;font-weight:800">1 / 2쪽</span><span class="btn">다음 쪽${icon("chevronR", 24, { sw: 3 })}</span></div>
</div>`;
    return {
      device: carScreen(theme, { gear: "P", speed: 0, app }),
      notes: notesBlock([
        "지나간 랜드마크는 아이 프로필의 <b>도감에 스티커</b>로 쌓입니다. 주차 중에 넘겨 봅니다.",
        "아직 못 만난 곳은 \"?\" 자리로 남겨 둡니다. 같은 길을 다시 가도 못 모은 곳이 있어 다음 여행을 기다리게 됩니다.",
        "\"새로\" 표시와 \"이번 여행\" 탭으로 오늘 모은 곳을 구분합니다. 스티커를 누르면 짧은 설명을 다시 듣습니다.",
        "장소 이름은 글자로만 쓰고 그림은 직접 그렸습니다.",
      ], "기획서: 3-E 랜드마크 도감 · 6 Retention 근거(모으는 재미)", drivingMini(theme, "주행 중에는 도감을 닫고 진행 화면으로 돌아갑니다. 랜드마크 설명은 08a처럼 정지 화면으로만 나옵니다.")),
    };
  },
});

// ---------------------------------------------------------------------------
// 3. 글꼴(Asta Sans) 준비: 시안 PNG에만 쓰고 저장소에는 넣지 않는다(.font-cache는 git 제외)
// ---------------------------------------------------------------------------
function ensureFont() {
  const weights = [400, 600, 700, 800];
  if (weights.every((w) => fs.existsSync(path.join(FONT_DIR, `AstaSans-${w}.ttf`)))) return true;
  try {
    fs.mkdirSync(FONT_DIR, { recursive: true });
    const cssText = execFileSync("curl", ["-sSfL", "-m", "30", "https://fonts.googleapis.com/css2?family=Asta+Sans:wght@400;600;700;800&display=swap"], { encoding: "utf8" });
    for (const block of cssText.split("@font-face").slice(1)) {
      const w = /font-weight:\s*(\d+)/.exec(block)?.[1];
      const url = /url\((https:[^)]+\.ttf)\)/.exec(block)?.[1];
      if (w && url) execFileSync("curl", ["-sSfL", "-m", "120", url, "-o", path.join(FONT_DIR, `AstaSans-${w}.ttf`)]);
    }
    return weights.every((w) => fs.existsSync(path.join(FONT_DIR, `AstaSans-${w}.ttf`)));
  } catch (e) {
    console.warn(`경고: Asta Sans를 받지 못해 설치된 글꼴(나눔스퀘어라운드 등)로 그립니다. (${e.message.split("\n")[0]})`);
    return false;
  }
}

function loadPlaywright() {
  for (const c of [process.env.PLAYWRIGHT_MODULE, "playwright", "/opt/node-tools/node_modules/playwright"].filter(Boolean)) {
    try { return require(c); } catch { /* 다음 후보 */ }
  }
  throw new Error("Playwright를 찾지 못했습니다. PLAYWRIGHT_MODULE 환경 변수로 경로를 알려 주세요.");
}

// ---------------------------------------------------------------------------
// 4. 실행
// ---------------------------------------------------------------------------
function contactSheet(list) {
  const t = TOKENS.light;
  const items = list.map((s) => `<figure><div class="thumb"><img src="../png/${s.out}.png" alt=""></div><figcaption><b>${s.out.split("-")[0]}</b> ${s.title}${s.theme === "dark" ? " (다크)" : ""}<span>기획서 ${s.doc}</span></figcaption></figure>`).join("");
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>화면 시안 모아 보기</title><style>
${sheetCss()}
body{margin:0;background:${t.canvas};font-family:var(--font);color:${t.ink};}
.cs{box-sizing:border-box;width:1920px;padding:44px 48px;display:flex;flex-direction:column;gap:28px;}
.cs h1{margin:0;font-size:46px;color:${t.forest};}
.cs p{margin:-14px 0 0;font-size:22px;color:${t.ink2};}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:28px 28px;}
figure{margin:0;background:#FFFFFF;border:2px solid ${t.line};border-radius:18px;padding:12px;display:flex;flex-direction:column;gap:10px;}
.thumb{aspect-ratio:1.62;display:flex;align-items:center;justify-content:center;background:${t.canvas};border-radius:10px;overflow:hidden;}
.thumb img{max-width:100%;max-height:100%;display:block;}
figcaption{font-size:21px;font-weight:700;line-height:1.35;display:flex;align-items:baseline;gap:6px;}
figcaption span{margin-left:auto;font-size:17px;font-weight:700;color:${t.ink2};white-space:nowrap;}
figcaption b{color:${t.forest};margin-right:6px;}
</style></head><body><div class="cs"><h1>같이가숲 화면 시안 (11/15 기획서·PPT용)</h1>
<p>앱에는 아직 없고 기획서에 시안으로 넣는 화면입니다. 차 화면은 "운전자 정보 | 내비 | 같이가숲" 반 화면 배치, 폰은 웹 화면입니다. 모든 그림은 직접 그렸습니다.</p>
<div class="grid">${items}</div></div></body></html>`;
}
function sheetCss() {
  const has = fs.existsSync(path.join(FONT_DIR, "AstaSans-700.ttf"));
  return `${has ? [400, 700, 800].map((w) => `@font-face{font-family:"Asta Sans";font-weight:${w};src:url("../.font-cache/AstaSans-${w}.ttf") format("truetype");}`).join("") : ""}
:root{--font:"Asta Sans","NanumSquareRound","Noto Sans CJK KR",sans-serif;}`;
}

async function main() {
  ensureFont();
  resetAssets();
  writeNewArt();
  fs.mkdirSync(PNG_DIR, { recursive: true });
  const only = (process.env.MOCKUP_ONLY || "").split(",").map((s) => s.trim()).filter(Boolean);
  const jobs = [];
  for (const s of SHEETS) {
    for (const theme of s.dark ? ["light", "dark"] : ["light"]) {
      const out = theme === "dark" ? `${s.file}-dark` : s.file;
      const { device, notes } = s.render(theme);
      const html = sheetHtml(theme, { title: s.title, eyebrow: EYEBROW, chips: s.chips, device, notes }).replace("</style>", `${EXTRA_CSS}</style>`);
      fs.writeFileSync(path.join(HTML_DIR, `${out}.html`), html);
      jobs.push({ ...s, out, theme });
    }
  }
  fs.writeFileSync(path.join(HTML_DIR, "00-contact-sheet.html"), contactSheet(jobs));

  const { chromium } = loadPlaywright();
  const browser = await chromium.launch();
  const scale = Number(process.env.MOCKUP_SCALE || 1.5);
  try {
    for (const j of jobs) {
      if (only.length && !only.includes(j.out)) continue;
      const page = await browser.newPage({ viewport: { width: 1920, height: 1000 }, deviceScaleFactor: scale });
      await page.goto(pathToFileURL(path.join(HTML_DIR, `${j.out}.html`)).href, { waitUntil: "load" });
      await page.evaluate(() => document.fonts.ready);
      // 잘림 검사: 화면 틀 안에서 넘치는 것이 있으면 알린다
      const issues = await page.evaluate(() => {
        const out = [];
        for (const el of document.querySelectorAll(".panel, .pbody, .tscreen, .app")) {
          if (el.closest(".mini")) continue;
          if (el.scrollHeight > el.clientHeight + 1) out.push(`세로 넘침 ${el.className} ${el.scrollHeight}>${el.clientHeight}`);
          if (el.scrollWidth > el.clientWidth + 1) out.push(`가로 넘침 ${el.className} ${el.scrollWidth}>${el.clientWidth}`);
        }
        for (const el of document.querySelectorAll(".btn, .badge, .kid, .h2, .tile, .chip")) {
          if (el.closest(".mini")) continue;
          if (el.scrollWidth > el.clientWidth + 1) out.push(`글자 넘침 "${el.textContent.trim().slice(0, 20)}" ${el.scrollWidth}>${el.clientWidth}`);
        }
        // 접근성 측정: 글자 대비(실제 바탕색 기준), 가장 작은 글자, 터치 영역(차 48dp, 폰 44px)
        const parse = (c) => (c.match(/[\d.]+/g) || []).map(Number);
        const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
        const ratio = (a, c) => { const [x, y] = [lum(a), lum(c)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
        const bgOf = (el) => { for (let e = el; e; e = e.parentElement) { const c = parse(getComputedStyle(e).backgroundColor); if (c.length === 3 || (c.length === 4 && c[3] > 0.5)) return c.slice(0, 3); } return [255, 255, 255]; };
        let minR = 99, minFont = 99;
        for (const root of document.querySelectorAll(".app, .pscreen, .tscreen")) {
          const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
          for (let n; (n = walker.nextNode());) {
            const el = n.parentElement;
            if (!n.textContent.trim() || el.closest(".mini, .off, .lock, svg")) continue; // 작은 화면 설명, 꺼진 버튼, 잠긴 항목 제외
            const cs = getComputedStyle(el);
            const r = ratio(parse(cs.color).slice(0, 3), bgOf(el));
            if (r < 4.5) out.push(`대비 ${r.toFixed(2)}:1 "${n.textContent.trim().slice(0, 16)}"`);
            minR = Math.min(minR, r); minFont = Math.min(minFont, parseFloat(cs.fontSize));
          }
          const need = root.classList.contains("app") ? 48 : 44;
          for (const el of root.querySelectorAll(".btn, .tile, .who, .tab, .sw-item, .toggle, .webtabs span")) {
            if (el.closest(".mini")) continue;
            const bb = el.getBoundingClientRect();
            if (Math.min(bb.width, bb.height) < need) out.push(`터치 영역 ${Math.round(bb.width)}×${Math.round(bb.height)} "${el.textContent.trim().slice(0, 10)}"`);
          }
        }
        if (minFont < 12) out.push(`글자 ${minFont}px (12 미만)`);
        const fontOk = document.fonts.check('700 20px "Asta Sans"');
        return { out, fontOk, minR, minFont };
      });
      await page.screenshot({ path: path.join(PNG_DIR, `${j.out}.png`), fullPage: true });
      await page.close();
      console.log(`ok  png/${j.out}.png · 글자 대비 최저 ${issues.minR.toFixed(2)}:1 · 가장 작은 글자 ${issues.minFont}px${issues.fontOk ? "" : " (Asta Sans 없음)"}`);
      for (const i of issues.out) console.log(`    ! ${i}`);
    }
    if (!only.length || only.includes("00-contact-sheet")) {
      const page = await browser.newPage({ viewport: { width: 1920, height: 1000 }, deviceScaleFactor: 1 });
      await page.goto(pathToFileURL(path.join(HTML_DIR, "00-contact-sheet.html")).href, { waitUntil: "load" });
      await page.evaluate(() => document.fonts.ready);
      await page.screenshot({ path: path.join(PNG_DIR, "00-contact-sheet.png"), fullPage: true });
      await page.close();
      console.log("ok  png/00-contact-sheet.png");
    }
  } finally {
    await browser.close();
  }

  // 글자 대비 점검(시안에 쓴 글자색/바탕색 쌍)
  const pairs = [["ink", "paper"], ["ink", "card"], ["ink2", "paper"], ["ink2", "card"], ["forest", "paper"], ["forest", "card"], ["onForest", "forest"], ["ink", "leafSoft"], ["ink", "sunSoft"], ["ink", "skySoft"], ["ink", "berrySoft"], ["ink2", "sceneSky"], ["ink", "sceneSky"]];
  for (const theme of ["light", "dark"]) {
    const t = TOKENS[theme];
    const res = pairs.map(([a, b]) => [`${a}/${b}`, contrast(t[a], t[b])]);
    const bad = res.filter(([, r]) => r < 4.5);
    console.log(`대비(${theme}): 최저 ${Math.min(...res.map(([, r]) => r)).toFixed(2)}:1${bad.length ? " · 4.5 미만 " + bad.map(([k, r]) => `${k} ${r.toFixed(2)}`).join(", ") : " · 모두 4.5:1 이상"}`);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
