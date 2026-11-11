// 캐릭터 시트 PNG 만들기 (라이트·다크)
//
// build-assets.mjs의 그림 정의를 그대로 불러와 한 장의 시트 HTML을 만들고,
// Playwright(Chromium)로 찍어 design/characters/ 아래에 PNG로 저장한다.
//
// 실행: node design/characters/tools/render-sheet.mjs   (저장소 루트에서)
// Playwright 위치가 다르면 PLAYWRIGHT_MODULE 환경 변수로 경로를 준다.

import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { buildAll, toSvg } from "./build-assets.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CHAR_DIR = path.resolve(HERE, "..");
const require = createRequire(import.meta.url);

function loadPlaywright() {
  const candidates = [process.env.PLAYWRIGHT_MODULE, "playwright", "/opt/node-tools/node_modules/playwright"].filter(Boolean);
  for (const c of candidates) {
    try {
      return require(c);
    } catch {
      // 다음 후보
    }
  }
  throw new Error("Playwright를 찾지 못했습니다. PLAYWRIGHT_MODULE 환경 변수로 경로를 알려 주세요.");
}

// 주차 중 포즈의 정지 장면. core:ui PoseMath.kt의 stillTransforms와 같은 값이다.
export const PARK_POSES = {
  wave: {
    guardian_a: { armR: { rot: -105 } },
    child: { armR: { rot: -105 } },
    guardian_b: { armL: { rot: 105 } },
  },
  cheer: {
    guardian_a: { armL: { rot: 105 }, armR: { rot: -105 } },
    child: { armL: { rot: 105 }, armR: { rot: -105 } },
    guardian_b: { armL: { rot: 105 }, armR: { rot: -105 } },
  },
  run: {
    guardian_a: { legL: { rot: 22 }, legR: { rot: -22 }, armL: { rot: -28 }, armR: { rot: 28 }, bob: { ty: -3 } },
    child: { legL: { rot: 22 }, legR: { rot: -22 }, armL: { rot: -28 }, armR: { rot: 28 }, bob: { ty: -3 } },
    guardian_b: { legL: { rot: 22 }, legR: { rot: -22 }, armL: { rot: -28 }, armR: { rot: 28 }, bob: { ty: -3 } },
  },
};

const FLAG_IDLE = { light: "#FFFFFF", dark: "#8A958F" };

const arts = Object.fromEntries(buildAll().map((a) => [a.name, a]));
const uri = (svg) => `data:image/svg+xml;base64,${Buffer.from(svg).toString("base64")}`;

function img(name, theme, { poses, overrides, h } = {}) {
  const art = arts[name];
  const height = h ?? art.h;
  return `<img alt="${art.title}" src="${uri(toSvg(art, { theme, poses, overrides }))}" style="height:${height}px;width:auto">`;
}

function tile(inner, label, sub, cls = "") {
  return `<figure class="tile"><div class="art ${cls}">${inner}</div><figcaption><b>${label}</b><code>${sub}</code></figcaption></figure>`;
}

function family(theme, poseName) {
  const p = poseName ? PARK_POSES[poseName] : {};
  return `<div class="fam">${img("guardian_a", theme, { poses: p.guardian_a, h: 170 })}${img("child", theme, { poses: p.child, h: 134 })}${img("guardian_b", theme, { poses: p.guardian_b, h: 170 })}</div>`;
}

function sheet(theme) {
  const t = theme;
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>같이가숲 캐릭터 시트</title>
<style>
:root {
  --paper: #F5FAF3; --card: #FFFFFF; --ink: #1D2A22; --ink-2: #4B5C51; --line: #D3E2D4; --forest: #2B774A; --scene: #DCEFFC; --hill: #C3E5B4; --road: #D8D4C8;
}
:root[data-theme="dark"] {
  --paper: #0F1713; --card: #17221C; --ink: #E3EEE6; --ink-2: #A8BBAE; --line: #2A3B31; --forest: #6FCB8E; --scene: #13283A; --hill: #1E3B29; --road: #343B38;
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--paper); color: var(--ink); font-family: "NanumSquareRound", "Noto Sans CJK KR", sans-serif; }
.wrap { width: 1600px; padding: 40px 48px 48px; display: grid; gap: 28px; }
header { display: flex; align-items: baseline; gap: 20px; }
h1 { margin: 0; font-size: 40px; color: var(--forest); }
header p { margin: 0; font-size: 18px; color: var(--ink-2); }
h2 { margin: 0 0 12px; font-size: 22px; }
h2 small { font-weight: 400; color: var(--ink-2); font-size: 16px; margin-left: 10px; }
.row { display: grid; gap: 16px; }
.c4 { grid-template-columns: repeat(4, 1fr); }
.c3 { grid-template-columns: repeat(3, 1fr); }
.c5 { grid-template-columns: repeat(5, 1fr); }
.tile { margin: 0; background: var(--card); border: 1px solid var(--line); border-radius: 18px; padding: 16px 16px 12px; display: grid; gap: 10px; }
.art { display: flex; align-items: flex-end; justify-content: center; min-height: 190px; border-radius: 12px; background: linear-gradient(var(--scene) 0 72%, var(--hill) 72%); padding: 12px; }
.art.road { background: linear-gradient(var(--scene) 0 70%, var(--road) 70%); }
.fam { display: flex; align-items: flex-end; gap: 0; }
.fam img + img { margin-left: -14px; }
figcaption { display: flex; flex-direction: column; gap: 2px; font-size: 17px; }
figcaption code { font-family: "IBM Plex Mono", monospace; font-size: 13px; color: var(--ink-2); }
.small .art { min-height: 120px; }
</style></head>
<body><div class="wrap" data-theme="${t}">
<header><h1>같이가숲 캐릭터 시트</h1><p>${t === "dark" ? "다크 테마" : "라이트 테마"} · 직접 그린 SVG 레이어 · 손그림 선(필터 없음)</p></header>

<section><h2>가족 <small>2등신 · 보호자 A·B와 아이(머리 위 새싹). 주차 중에만 움직이는 포즈의 정지 장면</small></h2>
<div class="row c4">
${tile(family(t), "기본 서 있기", "ggf_guardian_a · ggf_child · ggf_guardian_b")}
${tile(family(t, "wave"), "손 흔들기 (출발)", "FamilyPose.ParkWave")}
${tile(family(t, "cheer"), "축하 (도착)", "FamilyPose.ParkCheer")}
${tile(family(t, "run"), "달리기 (휴게소)", "FamilyPose.ParkRun")}
</div></section>

<section><h2>주행 중 정지 포즈 <small>창 안의 가족 + 지붕 화분이 단계마다 자람. 전환 효과 없이 그림만 바뀜</small></h2>
<div class="row c4">
${tile(img("car_stage0", t, { h: 176 }), "0~24% 출발! 만세", "ggf_car_stage0 · DriveStart", "road")}
${tile(img("car_stage25", t, { h: 176 }), "25~49% 창밖 보기(창은 닫힘)", "ggf_car_stage25 · DriveLookOut", "road")}
${tile(img("car_stage50", t, { h: 176 }), "50~74% 손 흔들기", "ggf_car_stage50 · DriveWave", "road")}
${tile(img("car_stage75", t, { h: 176 }), "75~100% 거의 다 왔어(손뼉)", "ggf_car_stage75 · DriveAlmost", "road")}
</div></section>

<section><h2>숲과 동물 <small>다음 나무는 새싹 10개로 자람 · 동물은 누적 나무 수로 해금</small></h2>
<div class="row c5" style="grid-template-columns: repeat(6, 1fr)">
${tile(img("plant_sprout", t, { h: 70 }), "1단계 새싹", "ggf_plant_sprout")}
${tile(img("plant_sapling", t, { h: 120 }), "2단계 어린 나무", "ggf_plant_sapling")}
${tile(img("plant_tree", t, { h: 170 }), "3단계 나무", "ggf_plant_tree")}
${tile(img("animal_squirrel", t, { h: 110 }), "다람쥐", "ggf_animal_squirrel")}
${tile(img("animal_rabbit", t, { h: 120 }), "토끼", "ggf_animal_rabbit")}
${tile(img("animal_bird", t, { h: 80 }), "새", "ggf_animal_bird")}
</div></section>

<section><h2>길 위의 것 <small>목적지 집, 10%마다 깃발(지난 깃발은 빨강, 남은 깃발은 흰색·다크에서는 회색), 남은 시간 아이콘</small></h2>
<div class="row c5 small">
${tile(img("house", t, { h: 120 }), "집 (목적지)", "ggf_house")}
${tile(img("flag", t, { h: 90 }), "지난 깃발", "ggf_flag · flagCloth=빨강")}
${tile(img("flag", t, { h: 90, overrides: { flagCloth: FLAG_IDLE[t] } }), "남은 깃발", "ggf_flag · flagCloth=흰색(다크: 회색)")}
${tile(img("icon_cartoon", t, { h: 72 }), "만화 편수 (20분 이상)", "ggf_icon_cartoon")}
${tile(img("icon_song", t, { h: 72 }), "노래 곡수 (20분 이하)", "ggf_icon_song")}
</div></section>
</div></body></html>`;
}

const outFile = (theme) => path.join(CHAR_DIR, theme === "dark" ? "character-sheet-dark.png" : "character-sheet.png");

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { chromium } = loadPlaywright();
  const browser = await chromium.launch();
  try {
    for (const theme of ["light", "dark"]) {
      const page = await browser.newPage({ viewport: { width: 1600, height: 900 }, deviceScaleFactor: 1 });
      await page.setContent(sheet(theme).replace("<html lang=\"ko\">", `<html lang="ko" data-theme="${theme}">`), { waitUntil: "load" });
      await page.screenshot({ path: outFile(theme), fullPage: true });
      await page.close();
      console.log(`ok  ${path.relative(process.cwd(), outFile(theme))}`);
    }
  } finally {
    await browser.close();
  }
}
