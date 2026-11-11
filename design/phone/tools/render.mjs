// 폰 앱·가족 연결 시안 만들기 (HTML → PNG), 결정 #37, docs/architecture.md 19장
//
// 실행(저장소 루트): node design/phone/tools/render.mjs
//   - design/phone/html/   시안 HTML(브라우저로 열어 볼 수 있음) + assets/(앱 캡처·GPT 그림에서 자른 것)
//   - design/phone/png/    기획서·PPT에 넣을 PNG
// 글꼴: design/phone/.font-cache/AstaSans-{400,600,700,800}.ttf (git 제외, README 참고). 없으면 시스템 글꼴로 찍힌다.
// 환경 변수: MOCKUP_SCALE(PNG 배율, 기본 1.5), MOCKUP_ONLY(쉼표로 고른 파일 이름만)

import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath, pathToFileURL } from "node:url";

const require = createRequire(import.meta.url);
const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const HTML = path.join(ROOT, "html");
const PNG = path.join(ROOT, "png");
const A = (n) => `assets/${n}`;

// ---------------------------------------------------------------------------
// 공용 스타일(색은 core:ui Tokens.kt 라이트 값과 같음)
// ---------------------------------------------------------------------------
const CSS = `
${[400, 600, 700, 800].map((w) => `@font-face{font-family:"Asta Sans";font-weight:${w};src:url("../.font-cache/AstaSans-${w}.ttf") format("truetype"),local("Asta Sans");}`).join("\n")}
:root{--paper:#F5FAF3;--card:#FFFFFF;--ink:#1D2A22;--ink2:#4B5C51;--line:#D3E2D4;--forest:#2B774A;--on-forest:#FFFFFF;--leaf:#6BB85A;--leaf-soft:#E3F3DC;--sky:#3F92D2;--sky-text:#2A6FA8;--sky-soft:#DDEEFB;--sun:#F0AE34;--sun-soft:#FFF0CC;--berry-text:#B0402F;--berry-soft:#FBE2DD;--canvas:#E9EEEA;--bezel:#1E2421;--font:"Asta Sans","Noto Sans CJK KR",sans-serif;}
*{box-sizing:border-box;}
body{margin:0;background:var(--canvas);color:var(--ink);font-family:var(--font);word-break:keep-all;-webkit-font-smoothing:antialiased;}
.sheet{padding:40px 48px 44px;display:flex;flex-direction:column;gap:28px;}
.head{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;}
.head .eyebrow{margin:0 0 6px;font-size:20px;font-weight:700;color:var(--ink2);}
.head h1{margin:0;font-size:44px;line-height:1.2;font-weight:800;color:var(--forest);}
.chips{display:flex;gap:10px;flex-wrap:wrap;justify-content:flex-end;}
.chip{display:inline-flex;align-items:center;gap:8px;border-radius:999px;padding:8px 18px;font-size:20px;font-weight:700;background:var(--card);border:2px solid var(--line);white-space:nowrap;}
.chip .dot{width:12px;height:12px;border-radius:50%;}
.chip.park{background:var(--leaf-soft);border-color:var(--leaf-soft);} .chip.park .dot{background:var(--forest);}
.chip.phone{background:var(--sky-soft);border-color:var(--sky-soft);} .chip.phone .dot{background:var(--sky-text);}
.chip.plan{background:var(--sun-soft);border-color:var(--sun-soft);} .chip.plan .dot{background:var(--sun);}
.body{display:flex;gap:28px;align-items:flex-start;}
.flowrow{display:flex;gap:18px;align-items:center;}
.arrow{font-size:40px;font-weight:800;color:var(--forest);flex:none;}
.notes{flex:1;min-width:300px;display:flex;flex-direction:column;gap:16px;}
.notes h2{margin:0;font-size:24px;font-weight:800;}
.notes ul{margin:0;padding:0;list-style:none;display:flex;flex-direction:column;gap:12px;}
.notes li{font-size:19px;line-height:1.5;padding-left:22px;position:relative;}
.notes li::before{content:"";position:absolute;left:4px;top:.62em;width:9px;height:9px;border-radius:50%;background:var(--forest);}
.notes .where{font-size:17px;line-height:1.5;color:var(--ink2);border-top:2px solid var(--line);padding-top:14px;margin:0;}
figure{margin:0;display:flex;flex-direction:column;align-items:center;gap:12px;}
figcaption{font-size:19px;font-weight:700;color:var(--ink2);text-align:center;max-width:420px;}
figcaption b{color:var(--forest);}
/* 폰 */
.phone{border:14px solid var(--bezel);border-radius:56px;overflow:hidden;background:var(--bezel);}
.pscreen{width:390px;height:844px;background:var(--paper);display:flex;flex-direction:column;overflow:hidden;border-radius:42px;position:relative;}
.pstatus{height:42px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 30px 0 34px;font-size:16px;font-weight:800;}
.pst-r{display:flex;gap:5px;align-items:center;} .pst-r i{width:5px;height:12px;border-radius:2px;background:currentColor;} .pst-r b{width:24px;height:12px;border-radius:4px;border:2px solid currentColor;margin-left:4px;}
.warn{flex:none;margin:0 14px;padding:8px 12px;border-radius:12px;background:var(--sun-soft);font-size:15px;font-weight:800;text-align:center;}
.ptop{flex:none;display:flex;align-items:center;justify-content:space-between;padding:12px 20px 6px;}
.ptop h3{margin:0;font-size:24px;font-weight:800;}
.pbody{flex:1;min-height:0;overflow:hidden;display:flex;flex-direction:column;gap:12px;padding:6px 18px 12px;}
.tabs{flex:none;display:flex;border-top:1.5px solid var(--line);background:var(--card);}
.tabs span{flex:1;display:flex;flex-direction:column;align-items:center;gap:2px;padding:8px 0 14px;font-size:14px;font-weight:700;color:var(--ink2);}
.tabs span img{width:30px;height:30px;object-fit:contain;opacity:.55;}
.tabs span.on{color:var(--forest);} .tabs span.on img{opacity:1;}
.card{background:var(--card);border:1.5px solid var(--line);border-radius:20px;padding:14px 16px;}
.row{display:flex;align-items:center;gap:12px;}
.grow{flex:1;min-width:0;}
.num{font-weight:800;color:var(--forest);line-height:1;font-variant-numeric:tabular-nums;}
.num small{font-size:.5em;}
.t{font-size:17px;line-height:1.45;font-weight:600;color:var(--ink2);}
.tb{font-size:19px;line-height:1.4;font-weight:800;}
.btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:52px;padding:0 20px;border-radius:999px;font-size:19px;font-weight:800;border:2.5px solid var(--ink2);background:transparent;white-space:nowrap;}
.btn.primary{background:var(--forest);border-color:var(--forest);color:var(--on-forest);}
.btn.full{width:100%;}
.btn.sm{min-height:44px;font-size:17px;padding:0 14px;}
.pill{display:inline-flex;align-items:center;gap:6px;border-radius:999px;padding:4px 12px;font-size:15px;font-weight:800;white-space:nowrap;background:var(--leaf-soft);}
.pill.wait{background:var(--sun-soft);} .pill.off{background:#ECEFEC;color:var(--ink2);} .pill.sky{background:var(--sky-soft);}
.kids{display:flex;gap:8px;} .kids span{border:2px solid var(--line);border-radius:999px;padding:6px 14px;font-size:16px;font-weight:800;background:var(--card);} .kids span.on{border-color:var(--forest);background:var(--leaf-soft);}
.toast{position:absolute;left:18px;right:18px;bottom:96px;background:var(--ink);color:#fff;border-radius:16px;padding:12px 16px;font-size:16px;font-weight:700;line-height:1.45;}
.bar{height:16px;border-radius:999px;background:var(--line);overflow:hidden;} .bar i{display:block;height:100%;background:var(--forest);border-radius:999px;}
.switch{width:52px;height:30px;border-radius:999px;background:#C9D3CB;position:relative;flex:none;} .switch::after{content:"";position:absolute;left:3px;top:3px;width:24px;height:24px;border-radius:50%;background:#fff;}
.switch.on{background:var(--forest);} .switch.on::after{left:25px;}
/* 차 화면(같이가숲 반 화면 패널) */
.car{border:16px solid var(--bezel);border-radius:28px;overflow:hidden;background:var(--paper);}
.cpanel{width:600px;height:720px;padding:26px 28px;display:flex;flex-direction:column;gap:16px;position:relative;}
.cpanel h3{margin:0;font-size:32px;font-weight:800;}
.gear{position:absolute;right:20px;top:20px;}
.dim{position:absolute;inset:0;background:rgba(29,42,34,.45);}
.dialog{position:absolute;left:40px;right:40px;top:110px;background:var(--card);border-radius:28px;padding:26px;display:flex;flex-direction:column;gap:14px;}
.check{display:flex;align-items:center;gap:12px;font-size:20px;font-weight:700;border:2px solid var(--line);border-radius:16px;padding:10px 14px;}
.box{width:28px;height:28px;border-radius:8px;border:2.5px solid var(--ink2);flex:none;display:flex;align-items:center;justify-content:center;font-size:18px;font-weight:800;}
.box.on{background:var(--forest);border-color:var(--forest);color:#fff;}
/* 흐름 그림 */
.lane{background:var(--card);border:2px solid var(--line);border-radius:24px;padding:20px 22px;display:flex;flex-direction:column;gap:10px;}
.lane h4{margin:0;font-size:24px;font-weight:800;}
.lane .t{font-size:18px;}
`;

const status = `<div class="pstatus"><span>11:12</span><span class="pst-r"><i></i><i></i><b></b></span></div>`;
const WARN = `<div class="warn">운전 중인 분은 사용하지 마세요</div>`;
const tabs = (on) => `<div class="tabs">${[["home", "우리 차", "ggf_pic_screen_icon_car_r39.webp"], ["forest", "아이 숲", "ggf_pic_screen_icon_forest_r39.webp"], ["shop", "상점", "ggf_pic_screen_reward_star_pouch_r36.webp"], ["family", "가족 연결", "ggf_pic_screen_icon_share_r39.webp"]].map(([k, l, i]) => `<span class="${k === on ? "on" : ""}"><img src="${A(i)}" alt="">${l}</span>`).join("")}</div>`;
function phone({ title, right = "", body, tab = null, extra = "", warn = true }, caption) {
  return `<figure><div class="phone"><div class="pscreen">${status}${warn ? WARN : ""}
${title ? `<div class="ptop"><h3>${title}</h3>${right}</div>` : ""}<div class="pbody">${body}</div>${tab ? tabs(tab) : ""}${extra}</div></div>${caption ? `<figcaption>${caption}</figcaption>` : ""}</figure>`;
}
const car = (inner, caption) => `<figure><div class="car"><div class="cpanel">${inner}</div></div>${caption ? `<figcaption>${caption}</figcaption>` : ""}</figure>`;
const P = `<span class="pill gear">기어 P · 주차 중</span>`;
const star = (n) => `<span class="row" style="gap:6px"><img src="${A("ggf_pic_star_badge.webp")}" style="width:24px;height:24px" alt="">${n}</span>`;
const chip = (k, t) => `<span class="chip ${k}"><span class="dot"></span>${t}</span>`;
const notes = (items, where) => `<div class="notes"><h2>이 시안에서 볼 것</h2><ul>${items.map((i) => `<li>${i}</li>`).join("")}</ul><p class="where">${where}</p></div>`;
const page = (title, eyebrow, chips, body, width) => `<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>${title}</title><style>${CSS}</style></head>
<body><div class="sheet" style="width:${width}px"><div class="head"><div><p class="eyebrow">${eyebrow}</p><h1>${title}</h1></div><div class="chips">${chips.join("")}</div></div>
<div class="body">${body}</div></div></body></html>`;

// 숲 장면: 공원 맵 위에 나무·동물·소품(앱 그림 그대로, 늘리지 않음)
function forestScene({ h = 300, items = [], ghost = null, grid = false }) {
  const els = items.map(([src, x, y, w]) => `<img src="${A(src)}" style="position:absolute;left:${x}%;top:${y}%;width:${w}px;transform:translate(-50%,-100%)" alt="">`).join("");
  const g = grid ? `<div style="position:absolute;inset:6% 5%;background-image:linear-gradient(rgba(255,255,255,.35) 1.5px,transparent 1.5px),linear-gradient(90deg,rgba(255,255,255,.35) 1.5px,transparent 1.5px);background-size:28px 28px;"></div>` : "";
  const gh = ghost ? `<div style="position:absolute;left:${ghost[0]}%;top:${ghost[1]}%;width:64px;height:40px;transform:translate(-50%,-60%);border:3px dashed #fff;border-radius:12px;background:rgba(43,119,74,.35)"></div>` : "";
  return `<div style="position:relative;height:${h}px;border-radius:18px;overflow:hidden;background:url(${A("map-park.webp")}) center/cover">${g}${gh}${els}</div>`;
}
const FOREST_ITEMS = [
  ["ggf_pic_tree_1.webp", 22, 42, 66], ["ggf_pic_tree_2.webp", 40, 34, 66], ["ggf_pic_tree_3.webp", 62, 40, 66], ["ggf_pic_tree_t02.webp", 80, 52, 66],
  ["ggf_pic_tree_t04.webp", 30, 70, 66], ["ggf_pic_tree_1.webp", 72, 76, 66], ["ggf_pic_animal_rabbit.webp", 50, 62, 40], ["ggf_pic_animal_squirrel.webp", 86, 84, 36],
  ["deco-wooden-bench.webp", 50, 88, 56], ["deco-flower-pot.webp", 16, 86, 40], ["deco-mailbox.webp", 88, 30, 36],
];

// ---------------------------------------------------------------------------
// 시안
// ---------------------------------------------------------------------------
const sheets = {};

// 01 가족 폰 연결
sheets["01-phone-pair"] = page("가족 폰 연결: 차 화면 QR → 폰 → 차에서 승인", "폰 앱 시안 01 · 결정 #37 · 비밀번호 없이", [chip("park", "차 · 주차 중"), chip("phone", "가족 폰 앱")], `
<div class="flowrow">
${car(`<h3>가족 폰 연결</h3>${P}
<div class="row" style="gap:22px;align-items:flex-start"><img src="${A("qr_pattern.svg")}" style="width:250px;height:250px;background:#fff;border-radius:16px;padding:10px" alt="연결 QR">
<div class="grow" style="display:flex;flex-direction:column;gap:12px"><div class="tb" style="font-size:22px">같이가숲 폰 앱으로 찍어 주세요</div>
<div class="t" style="font-size:19px">이 QR은 <b>5분 동안 한 번만</b> 쓸 수 있어요. 비밀번호는 필요 없어요.</div>
<div class="card" style="padding:10px 14px"><div class="t" style="font-size:16px">카메라가 없으면 코드 입력</div><div class="tb" style="font-size:24px;letter-spacing:.04em;white-space:nowrap">K7QM-4RXP-2WDN</div></div></div></div>
<div class="t" style="font-size:18px">남은 시간 4:32 · 주행을 시작하면 이 화면은 닫히고 코드는 버려져요</div>
<div class="row"><span class="btn">닫기</span><span class="grow"></span><span class="btn">연결된 기기 2대</span></div>`, "① 차(주차 중) <b>설정 → 가족 폰 연결</b>")}
<span class="arrow">→</span>
${phone({ title: "차와 연결하기", body: `
<div style="position:relative;height:420px;border-radius:22px;overflow:hidden;background:#2B332E">
<img src="${A("qr_pattern.svg")}" style="position:absolute;left:50%;top:48%;width:200px;transform:translate(-50%,-50%) rotate(-4deg);background:#fff;padding:8px;border-radius:10px" alt="">
<div style="position:absolute;left:50%;top:48%;width:250px;height:250px;transform:translate(-50%,-50%);border:4px solid #fff;border-radius:24px"></div>
<div style="position:absolute;left:0;right:0;bottom:16px;text-align:center;color:#fff;font-size:18px;font-weight:800">차 화면의 QR을 네모 안에 맞춰 주세요</div></div>
<div class="t">QR에는 우리 가족만 여는 열쇠가 들어 있어요. 열쇠는 서버로 보내지 않아요.</div>
<span class="btn full">코드로 입력하기</span>` }, "② 폰 앱이 QR을 찍음")}
<span class="arrow">→</span>
${car(`<h3>가족 폰 연결</h3>${P}<div class="dim"></div>
<div class="dialog"><div class="tb" style="font-size:26px">'엄마 폰'을 연결할까요?</div>
<div class="t" style="font-size:18px">이 폰에서 할 수 있는 일을 골라 주세요. 나중에 바꿀 수 있어요.</div>
<div class="check"><span class="box on">✓</span>아이 숲 보기·꾸미기·상점</div>
<div class="check"><span class="box on">✓</span>우리 차 진행·남은 시간 보기</div>
<div class="check"><span class="box"></span>대략 위치 보기 <span class="t" style="font-size:16px">(약 5km 칸)</span></div>
<div class="t" style="font-size:16px">아이 숲과 별명이 이 폰으로 가요. 보호자만 승인할 수 있어요.</div>
<div class="row"><span class="btn">거절</span><span class="grow"></span><span class="btn primary">연결</span></div></div>`, "③ 차에서 보호자가 <b>[연결]</b>을 눌러야 끝")}
<span class="arrow">→</span>
${phone({ title: "연결됐어요", body: `
<div class="card" style="display:flex;flex-direction:column;align-items:center;gap:10px;padding:22px 16px">
<img src="${A("ggf_pic_car_start.webp")}" style="width:260px" alt=""><div class="tb" style="font-size:24px">우리 가족 차와 연결됐어요</div>
<div class="t" style="text-align:center">이 폰: 엄마 폰</div></div>
<div class="card"><div class="tb">이 폰에서 할 수 있는 일</div>
<div class="t">✓ 아이 숲 보기·꾸미기·상점<br>✓ 우리 차 진행·남은 시간<br>— 대략 위치(보호자가 차에서 켤 수 있어요)</div></div>
<span class="btn primary full">시작하기</span>` }, "④ 폰 연결 완료")}
</div>`, 2420);
sheets["01-phone-pair"] = sheets["01-phone-pair"].replace("</div></body>", `<div style="width:2324px">${notes([
  "<b>비밀번호·이메일·전화번호를 받지 않아요.</b> 차 화면의 QR(5분, 한 번만)을 찍고, 차에서 보호자가 한 번 더 눌러야 연결돼요. QR 사진을 누가 찍어 가도 차에서 승인하지 않으면 연결되지 않아요.",
  "QR에는 <b>가족 열쇠</b>가 함께 들어 있어요. 숲·별명·위치는 이 열쇠로 잠가서 서버에 보내므로 서버도 내용을 읽지 못해요(종단 간 암호화).",
  "연결·승인 화면은 <b>주차 중에만</b> 열려요. 주행이 시작되면 닫히고 코드는 버려져요.",
  "폰마다 할 수 있는 일을 보호자가 골라요. 대략 위치는 기본으로 꺼져 있어요(결정 #8).",
], "docs/architecture.md 19-3 · docs/share-api.md 1장 · 그림: 앱의 우리 차 그림(GPT R21), QR은 모양만 흉내 낸 그림")}</div></div></body>`);

// 02 우리 차 진행
const roadCard = (pct, line) => `<div class="card" style="padding:12px">
<div class="row"><span class="num" style="font-size:56px">${pct}<small>%</small></span><span class="tb grow" style="font-size:18px">${line}</span></div>
<img src="${A("road-40.webp")}" style="width:100%;border-radius:14px;margin-top:8px;display:block" alt="진행 길 그림"></div>`;
sheets["02-phone-trip"] = page("우리 차 진행 보기: 가족 폰에서", "폰 앱 시안 02 · 결정 #37·#8", [chip("phone", "가족 폰 앱"), chip("plan", "위치는 동의한 폰만")], `
${phone({ title: "우리 차", right: `<span class="pill sky">달리는 중</span>`, tab: "home", body: `
${roadCard(40, "약 1시간 20분 남았어요")}
<div class="card row"><div class="grow"><div class="tb">대략 위치</div><div class="t">보호자가 위치 보내기를 켜지 않았어요</div></div><span class="pill off">꺼짐</span></div>
<div class="t" style="font-size:15px">15초 전에 받음 · 목적지·경로는 보내지 않아요</div>` }, "기본: <b>진행률과 남은 시간만</b>")}
${phone({ title: "우리 차", right: `<span class="pill sky">달리는 중</span>`, tab: "home", body: `
<div class="card" style="padding:12px"><div class="row"><span class="num" style="font-size:44px">62<small>%</small></span><span class="tb grow">약 48분 남았어요</span></div><div class="bar" style="margin-top:10px"><i style="width:62%"></i></div></div>
<div class="card" style="padding:12px"><div class="tb" style="margin-bottom:8px">대략 위치 <span class="t">(약 5km 칸)</span></div>
<svg viewBox="0 0 320 220" width="100%" style="display:block;border-radius:12px;background:#E9EEE6">
<path d="M0 150 C80 120 140 170 220 120 S300 60 320 70" stroke="#CBE1EE" stroke-width="18" fill="none"/>
${Array.from({ length: 8 }, (_, i) => `<line x1="${i * 40}" y1="0" x2="${i * 40}" y2="220" stroke="#D3DDD2"/>`).join("")}${Array.from({ length: 6 }, (_, i) => `<line x1="0" y1="${i * 40}" x2="320" y2="${i * 40}" stroke="#D3DDD2"/>`).join("")}
<rect x="160" y="80" width="40" height="40" fill="rgba(43,119,74,.30)" stroke="#2B774A" stroke-width="3" rx="6"/>
<circle cx="180" cy="100" r="7" fill="#2B774A"/></svg>
<div class="t" style="font-size:15px;margin-top:6px">차가 이 칸 어딘가에 있어요. 정확한 자리는 보내지 않아요.</div></div>
<div class="t" style="font-size:15px">위치는 도착하거나 24시간이 지나면 지워져요</div>` }, "보호자가 켜고 이 폰에 허락했을 때만 <b>대략 위치</b>")}
${phone({ title: "우리 차", right: `<span class="pill">도착</span>`, tab: "home", body: `
<div class="card" style="display:flex;flex-direction:column;align-items:center;gap:10px;padding:20px 14px">
<img src="${A("ggf_pic_car_start.webp")}" style="width:250px" alt=""><div class="tb" style="font-size:24px">할머니 댁에 도착했어요</div>
<div class="t">오늘 하늘이 숲에 새싹 36개</div></div>
<div class="card row"><div class="grow"><div class="tb">위치 공유가 끝났어요</div><div class="t">서버의 진행·위치 정보는 지워졌어요</div></div></div>
<span class="btn full">하늘이 숲 보러 가기</span>` }, "도착하면 공유가 끝나고 서버에서 지워짐")}
${notes([
  "진행 화면은 차 앱과 같은 <b>진행 길 그림</b>(앱 캡처)을 써요. 아이도 폰으로 \"얼마나 남았지?\"를 볼 수 있어요.",
  "<b>기본은 진행률·남은 시간만</b> 보내요. 대략 위치는 보호자가 주차 중에 켜고, 폰마다 허락한 경우에만 약 5km 칸으로 보여요(차 안에서 반올림, 결정 #8).",
  "목적지 이름·경로·정확한 좌표·차량 정보는 폰으로 보내지 않아요.",
  "차가 15초~1% 단위로 보내고, 폰은 앱이 열려 있을 때만 15초마다 받아요. 도착 또는 24시간 뒤, 보호자가 멈추면 즉시 서버에서 지워요.",
  "앱이 없는 일행에게는 지금처럼 <b>공유 링크(웹)</b>를 보낼 수 있어요(기획서 시안 01).",
], "docs/architecture.md 19-4·19-8 · 그림: 앱 진행 화면 캡처(design/screens/app), 위치 칸은 직접 그린 그림")}`, 1920);

// 03 숲 보기·꾸미기
sheets["03-phone-forest"] = page("아이 숲 보기·꾸미기: 폰에서도", "폰 앱 시안 03 · 결정 #37·#38", [chip("phone", "가족 폰 앱"), chip("park", "차에는 다음 정차 때 반영")], `
${phone({ title: "하늘이의 숲", right: star("360"), tab: "forest", body: `
<div class="kids"><span class="on">하늘이</span><span>바다</span></div>
<img src="${A("forest-island.webp")}" style="width:100%;border-radius:18px;display:block" alt="숲 그림">
<div class="card"><div class="tb">나무 11그루 · 새싹 8개</div><div class="t">다음 나무까지 새싹 2개 · 지금 심는 나무: 사과나무</div></div>
<div class="row"><span class="btn primary grow">꾸미기</span><span class="btn">숲 맵</span></div>
<div class="t" style="font-size:15px">★는 차에서 부드럽게 달린 만큼 쌓여요(폰에서는 보기만)</div>` }, "숲 보기: 차와 <b>같은 숲 그림</b>")}
${phone({ title: "꾸미기", right: `<span class="btn sm primary">저장</span>`, tab: "forest", body: `
${forestScene({ h: 380, items: FOREST_ITEMS, ghost: [34, 52], grid: true })}
<div class="t">나무·동물·소품을 끌어서 옮겨요. 빈 땅에 놓을 수 있어요.</div>
<div class="card" style="padding:10px 12px"><div class="row"><span class="tb grow">보관함</span><span class="t">끌어서 숲에 놓기</span></div>
<div class="row" style="margin-top:8px;gap:10px">
${[["deco-swing.webp", 2], ["deco-garden-lantern.webp", 1], ["deco-picnic-mat.webp", 1], ["ggf_pic_tree_3.webp", 3]].map(([s, n]) => `<div style="position:relative;width:70px;height:62px;border:2px solid var(--line);border-radius:12px;display:flex;align-items:center;justify-content:center"><img src="${A(s)}" style="max-width:52px;max-height:48px" alt=""><span style="position:absolute;right:4px;bottom:2px;font-size:14px;font-weight:800">×${n}</span></div>`).join("")}</div></div>` }, "꾸미기: 끌어 놓기 + <b>보관함</b>(같은 것은 개수)")}
${phone({ title: "꾸미기", right: `<span class="pill wait">차 확인 기다리는 중</span>`, tab: "forest", body: `
${forestScene({ h: 380, items: [...FOREST_ITEMS, ["deco-swing.webp", 34, 56, 60]] })}
<div class="card"><div class="tb">저장했어요</div><div class="t">우리 차가 켜져 있으면 곧 반영돼요. 차 화면에는 <b>다음에 멈췄을 때</b> 보여요.</div></div>`, extra: `<div class="toast">다른 폰에서 바꾼 '공원' 숲을 덮었어요. 맵마다 마지막에 저장한 것이 남아요.</div>` }, "저장 → 차가 확인 → <b>맵마다 마지막 저장 우선</b>")}
${notes([
  "숲 그림·나무·동물·꾸미기 소품은 <b>차 앱과 같은 그림과 같은 숲 그리드</b>(core:ui)를 폰에서 그대로 써요.",
  "폰에서는 주행 중에도 동승자가 꾸밀 수 있어요. 대신 <b>차 화면의 숲은 다음 정차 때</b> 바뀌어 운전 중 차 화면이 갑자기 바뀌지 않아요(결정 #20).",
  "폰의 저장은 <b>요청</b>이고, ★·소품 개수·칸 규칙은 차가 확인해요. 폰은 같은 규칙으로 먼저 보여 주고, 차가 확인하면 확정돼요.",
  "두 사람이 같은 맵을 바꾸면 <b>맵마다 마지막 저장이 남아요</b>. 덮어쓴 경우 한 줄로 알려 줘요.",
  "아이 별명은 가족 열쇠로 잠가서 보내고, 서버는 읽지 못해요.",
], "docs/architecture.md 19-4·19-5 · 그림: 앱 숲 화면 캡처, 공원 맵·나무·동물(GPT), 소품 R27(GPT) · 배치는 예시")}`, 1920);

// 04 상점·보관함
const SHOP = [["deco-wooden-bench.webp", "나무 벤치", 30], ["deco-swing.webp", "그네", 60], ["deco-birdhouse-post.webp", "새집 기둥", 40], ["deco-mailbox.webp", "빨간 우체통", 40], ["deco-flower-pot.webp", "꽃 화분", 20], ["deco-mushroom-house.webp", "버섯 집", 80], ["deco-small-tent.webp", "작은 텐트", 70], ["deco-picnic-mat.webp", "피크닉 매트", 50], ["deco-small-windmill.webp", "작은 풍차", 90]];
const shopGrid = (sel) => `<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px">${SHOP.map(([s, n, p], i) => `<div class="card" style="padding:8px;display:flex;flex-direction:column;align-items:center;gap:4px;${i === sel ? "border:3px solid var(--forest);background:var(--leaf-soft)" : ""}"><img src="${A(s)}" style="height:66px;max-width:90px;object-fit:contain" alt=""><span style="font-size:15px;font-weight:800">${n}</span>${star(p).replace('class="row"', 'class="row" style="font-size:16px;font-weight:800"')}</div>`).join("")}</div>`;
sheets["04-phone-shop"] = page("숲 꾸미기 상점·보관함: 폰에서도", "폰 앱 시안 04 · 결정 #37·#38·#36", [chip("phone", "가족 폰 앱"), chip("plan", "가격은 예시")], `
${phone({ title: "상점", right: star("360"), tab: "shop", body: `
<div class="kids"><span class="on">쉼터</span><span>꽃</span><span>불빛</span><span>길</span><span>…</span></div>
${shopGrid(1)}
<div class="t" style="font-size:15px">★는 아이 숲 전용이에요. 어른 마일리지는 줄지 않아요.</div>` }, "상점: 카테고리별 소품, <b>★</b>로 사기")}
${phone({ title: "상점", right: star("360"), tab: "shop", body: `${shopGrid(1)}`, extra: `<div class="dim" style="border-radius:42px"></div>
<div class="dialog" style="left:18px;right:18px;top:250px;padding:22px"><div class="row"><img src="${A("deco-swing.webp")}" style="height:80px" alt=""><div class="grow"><div class="tb" style="font-size:22px">그네를 살까요?</div><div class="t">★ 60 → 남는 ★ 300</div></div></div>
<div class="t">우리 차가 확인하면 보관함에 들어가요. ★가 모자라면 사지 않고 알려 줘요.</div>
<div class="row"><span class="btn">그만두기</span><span class="grow"></span><span class="btn primary">사기</span></div></div>` }, "사기 확인: <b>차가 확인</b>하면 확정")}
${phone({ title: "보관함", right: star("300"), tab: "shop", body: `
<div class="card row"><span class="pill">새로 들어옴</span><span class="tb grow">그네 1개</span></div>
<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px">${[["deco-swing.webp", "그네", 2], ["deco-garden-lantern.webp", "정원 등불", 1], ["deco-picnic-mat.webp", "피크닉 매트", 1], ["deco-flower-pot.webp", "꽃 화분", 4], ["ggf_pic_tree_3.webp", "벚나무", 3], ["ggf_pic_animal_bird.webp", "파랑새", 1]].map(([s, n, c]) => `<div class="card" style="padding:8px;display:flex;flex-direction:column;align-items:center;gap:4px"><img src="${A(s)}" style="height:62px;max-width:86px;object-fit:contain" alt=""><span style="font-size:15px;font-weight:800">${n} ×${c}</span></div>`).join("")}</div>
<span class="btn primary full">숲에 놓으러 가기</span>
<div class="t" style="font-size:15px">차 화면 보관함과 같은 것이에요(한 가족, 한 보관함)</div>` }, "보관함: 같은 것은 <b>개수</b>, 차와 같은 내용")}
${notes([
  "상점·보관함은 숲 꾸미기 상점(결정 #38)과 <b>같은 소품·같은 가격·같은 보관함</b>이에요. 폰에서 사도 차에서 사도 결과가 같아요.",
  "★ 쓰기는 <b>차가 규칙대로 확정</b>해요. 두 폰이 동시에 사도 ★가 모자라면 나중 것은 거절돼서 ★가 음수가 되지 않아요.",
  "아이 ★와 어른 안전 운전 마일리지는 따로예요(결정 #36). 폰에서도 아이가 ★를 써도 마일리지는 줄지 않아요.",
  "가격·카테고리 이름은 상점 세션이 정하는 값으로 바뀔 수 있어요(시안 가격은 예시).",
], "docs/architecture.md 19-4·19-5 · 그림: 꾸미기 소품 R27(GPT), 나무·동물(GPT), ★ 배지·주머니(GPT R36)")}`, 1920);

// 05 연결 관리·동의
sheets["05-phone-manage"] = page("연결 관리·동의·중단", "폰 앱 시안 05 · 결정 #37·#8 · 개인정보", [chip("park", "차 · 주차 중"), chip("phone", "가족 폰 앱")], `
${car(`<h3>연결된 가족 폰</h3>${P}
${[["엄마 폰", "숲·상점 · 진행 · 위치", "오늘"], ["아빠 폰", "숲·상점 · 진행", "어제"], ["할머니 폰", "진행 · 위치", "3일 전"]].map(([n, r, d]) => `<div class="card row" style="padding:12px 16px"><div class="grow"><div class="tb" style="font-size:22px">${n}</div><div class="t">${r} · 마지막 접속 ${d}</div></div><span class="btn sm">바꾸기</span><span class="btn sm">끊기</span></div>`).join("")}
<div class="card row" style="padding:12px 16px"><div class="grow"><div class="tb" style="font-size:21px">대략 위치 보내기</div><div class="t">위치를 허락한 폰에만, 약 5km 칸</div></div><span class="switch on"></span></div>
<div class="row"><span class="btn primary">폰 더 연결하기</span><span class="grow"></span><span class="btn">모두 끊고 지우기</span></div>`, "차: 기기마다 <b>권한·끊기</b>, 위치 보내기 켜기/끄기")}
${car(`<h3>폰 연결 전에 확인해 주세요</h3>${P}
<div class="card" style="display:grid;grid-template-columns:120px 1fr;gap:10px 14px;font-size:18px;line-height:1.45;padding:16px 18px">
<b>보내는 것</b><span>아이 숲(나무·동물·꾸미기), ★, 아이 별명, 우리 차 진행률·남은 시간. 대략 위치는 따로 켤 때만</span>
<b>쓰는 곳</b><span>연결한 가족 폰에서 보기·꾸미기</span>
<b>두는 곳</b><span>공유 서버. 가족 열쇠로 잠가서 서버도 읽지 못해요</span>
<b>두는 기간</b><span>진행·위치는 도착 또는 24시간까지. 숲은 연결을 끊을 때까지(90일 안 쓰면 자동 삭제)</span>
<b>거부하면</b><span>차 앱은 지금처럼 그대로 쓸 수 있어요</span></div>
<div class="t" style="font-size:17px">아이 정보가 함께 가므로 보호자가 직접 눌러 주세요.</div>
<div class="row"><span class="btn">안 할래요</span><span class="grow"></span><span class="btn primary">동의하고 연결</span></div>`, "차: 처음 연결 때 <b>동의</b>(버튼을 눌러야 동의)")}
${phone({ title: "가족 연결", tab: "family", body: `
<div class="card"><div class="tb">우리 가족 차</div><div class="t">이 폰: 엄마 폰 · 연결된 폰 3대</div></div>
<div class="card"><div class="tb">이 폰에서 할 수 있는 일</div><div class="t">✓ 아이 숲 보기·꾸미기·상점<br>✓ 우리 차 진행·남은 시간<br>✓ 대략 위치<br>바꾸려면 차 화면(주차 중)에서 보호자가 바꿔요</div></div>
<div class="card"><div class="tb">내 정보</div><div class="t">이름·전화번호·이메일을 받지 않아요. 열쇠는 이 폰 안에 잠겨 있어요.</div></div>
<span class="btn full">개인정보 안내 보기</span>
<span class="btn full" style="border-color:var(--berry-text);color:var(--berry-text)">이 폰 연결 끊기</span>` }, "폰: 내 권한 보기, <b>스스로 연결 끊기</b>")}
${notes([
  "연결·권한·위치 켜기는 모두 <b>차 화면, 주차 중, 보호자</b>가 해요. 폰끼리 초대하지 않아요.",
  "동의 화면은 보내는 것·쓰는 곳·두는 곳·기간·거부권을 먼저 알려 주고, <b>버튼을 눌러야</b> 동의가 돼요(미리 켜진 체크 없음).",
  "<b>끊기</b>를 누르면 그 폰은 바로 서버에 들어올 수 없어요. <b>모두 끊고 지우기</b>는 서버의 가족 기록을 전부 지워요. 위치 보내기를 끄면 서버의 위치가 바로 지워져요.",
  "로그인 토큰은 주소(URL)에 넣지 않고, 열쇠는 폰·차의 Keystore에 잠가 둬요.",
], "docs/architecture.md 19-3·19-8 · 이름은 예시")}`, 2300);

// 06 연동 흐름 그림
const lane = (t, lines, color = "var(--line)") => `<div class="lane" style="border-color:${color}"><h4>${t}</h4>${lines.map((l) => `<div class="t">${l}</div>`).join("")}</div>`;
sheets["06-sync-flow"] = page("차 · 공유 서버 · 가족 폰은 이렇게 이어져요", "연동 흐름 그림 · 결정 #37 · docs/architecture.md 19장", [chip("park", "규칙은 차가 주인"), chip("phone", "서버는 내용을 못 읽음")], `
<div style="display:flex;flex-direction:column;gap:20px"><div style="display:grid;grid-template-columns:1fr 120px 1fr 120px 1fr;gap:0;align-items:stretch;width:1820px">
<div style="display:flex;flex-direction:column;gap:14px">
<div class="row"><img src="${A("ggf_pic_car_start.webp")}" style="width:200px" alt=""><h2 style="margin:0;font-size:34px;font-weight:800">차 (Pleos)</h2></div>
${lane("같이가숲 차 앱", ["★·소품·숲 칸 <b>규칙을 확정</b>하는 곳", "가족 열쇠를 Keystore에 보관", "주차 중: 연결 QR · 승인 · 동의"], "var(--forest)")}
${lane("보내는 것", ["숲 상태(바뀔 때)", "진행률·남은 시간(15초 또는 1%)", "대략 위치(켰을 때만, 5km 칸)"])}
</div>
<div style="display:flex;flex-direction:column;justify-content:center;align-items:center;gap:30px;font-size:18px;font-weight:800;color:var(--forest);text-align:center">
<div>잠긴 상태 →<br><span style="font-size:44px">⇢</span></div><div><span style="font-size:44px">⇠</span><br>← 폰 요청</div></div>
<div style="display:flex;flex-direction:column;gap:14px">
<div class="row" style="height:118px"><img src="${A("ggf_pic_screen_icon_share_r39.webp")}" style="height:90px" alt=""><h2 style="margin:0;font-size:34px;font-weight:800">공유 서버</h2></div>
${lane("가족마다 작은 방 하나", ["Cloudflare Workers + Durable Objects", "잠긴 숲 상태 1개 · 요청 대기열", "잠긴 진행 상태 1개(24시간)"], "var(--sky)")}
${lane("서버가 아는 것", ["가족 번호, 기기 키의 해시, 크기와 시각", "<b>별명·숲·위치는 못 읽음</b>(종단 간 암호화)", "주소에 토큰 없음 · HTTPS만"])}
</div>
<div style="display:flex;flex-direction:column;justify-content:center;align-items:center;gap:30px;font-size:18px;font-weight:800;color:var(--sky-text);text-align:center">
<div>→ 숲·진행 보기<br><span style="font-size:44px">⇢</span></div><div><span style="font-size:44px">⇠</span><br>꾸미기·사기 요청 ←</div></div>
<div style="display:flex;flex-direction:column;gap:14px">
<div class="row" style="height:118px"><img src="${A("ggf_pic_screen_icon_home_r39.webp")}" style="height:90px" alt=""><h2 style="margin:0;font-size:34px;font-weight:800">가족 폰</h2></div>
${lane("같이가숲 폰 앱", ["우리 차 진행·(허락 시) 대략 위치", "아이 숲 보기·꾸미기 · 상점·보관함", "같은 규칙으로 먼저 보여 주고 차가 확정"], "var(--sky-text)")}
${lane("여러 가족 폰", ["엄마·아빠·할머니 폰을 각각 연결", "폰마다 권한(숲 / 진행 / 위치)", "끊으면 바로 접속 불가"])}
</div></div>
<div style="width:1820px;display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-top:8px">
${[["1. 연결", "주차 중 차 화면 QR(5분·1번) → 폰이 찍음 → 차에서 보호자가 [연결]. QR 안의 가족 열쇠는 서버를 거치지 않아요."],
  ["2. 꾸미기", "폰에서 저장 → 서버 대기열(순서 번호) → 차가 규칙대로 적용 → 새 숲 상태를 올림 → 모든 폰에 보임."],
  ["3. 충돌", "★·소품 개수는 차가 확인(모자라면 거절). 같은 맵은 마지막 저장이 남고, 덮였으면 알려 줘요."],
  ["4. 멈추기", "위치 끄기·도착·24시간이면 진행·위치 삭제. 폰 끊기는 즉시. '모두 끊고 지우기'는 가족 기록 전체 삭제."]].map(([h, t]) => `<div class="lane"><h4>${h}</h4><div class="t">${t}</div></div>`).join("")}
</div></div>`, 1920);

// 00 모아 보기
const LIST = ["01-phone-pair", "02-phone-trip", "03-phone-forest", "04-phone-shop", "05-phone-manage", "06-sync-flow"];
sheets["00-contact-sheet"] = `<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>폰 앱 시안 모아 보기</title><style>${CSS}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:24px;} .grid img{width:100%;border-radius:12px;border:2px solid var(--line);background:#fff;display:block} .grid p{margin:6px 0 0;font-size:20px;font-weight:800}</style></head>
<body><div class="sheet" style="width:1920px"><div class="head"><div><p class="eyebrow">결정 #37 · 12/3 PT 전 구현 예정</p><h1>같이가숲 폰 앱 · 가족 연결 시안</h1></div></div>
<div class="grid">${LIST.map((n) => `<div><img src="../png/${n}.png" alt=""><p>${n.slice(0, 2)} ${{ "01": "가족 폰 연결", "02": "우리 차 진행 보기", "03": "아이 숲 보기·꾸미기", "04": "상점·보관함", "05": "연결 관리·동의", "06": "연동 흐름 그림" }[n.slice(0, 2)]}</p></div>`).join("")}</div></div></body></html>`;

// ---------------------------------------------------------------------------
// 쓰기와 찍기
// ---------------------------------------------------------------------------
fs.mkdirSync(PNG, { recursive: true });
const only = (process.env.MOCKUP_ONLY || "").split(",").filter(Boolean);
const scale = Number(process.env.MOCKUP_SCALE || 1.5);
const order = [...LIST, "00-contact-sheet"].filter((n) => !only.length || only.includes(n));
for (const n of order) fs.writeFileSync(path.join(HTML, `${n}.html`), sheets[n]);

const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const browser = await chromium.launch();
const pageObj = await browser.newPage({ viewport: { width: 2600, height: 1200 }, deviceScaleFactor: scale });
for (const n of order) {
  await pageObj.goto(pathToFileURL(path.join(HTML, `${n}.html`)).href);
  await pageObj.evaluate(() => document.fonts.ready);
  await pageObj.waitForLoadState("networkidle");
  const el = await pageObj.$(".sheet");
  await el.screenshot({ path: path.join(PNG, `${n}.png`) });
  const fontOk = await pageObj.evaluate(() => document.fonts.check('700 20px "Asta Sans"'));
  console.log(`ok  png/${n}.png${fontOk ? "" : " (Asta Sans 없음)"}`);
}
await browser.close();
