#!/usr/bin/env python3
"""
같이가숲 — 숲 꾸미기 소품 그림(GPT R27·R43)을 앱에 넣는 스크립트(결정 #38, 숲 꾸미기 상점 세션 담당).

GPT 파일 이름: GGF-DECO-<코드>-R27-v<번호>.png, GGF-DECO-<코드>-R43-v<번호>.png
  코드는 core/account DecorCatalog.kt의 소품 코드(예: WOODEN-BENCH). GPT가 다른 영문 이름을 쓰면 ALIASES에 한 줄 더한다.
  같은 소품이 여러 버전이면 번호가 가장 큰 것을 쓴다.

하는 일(화질·색감 규칙 #29-⑦은 build_app_assets.py와 같다: 늘리지 않음, 색은 그대로, 알파만 정리, WebP 92)
  1) 알파 정리(clean_alpha, 알파 16 미만은 지움) → 투명 여백을 잘라냄(알파 16 이상 상자 + 4px)
  2) 긴 변 384px까지 줄이기만 한다(맵 큰 보기의 칸이 96px, 2×2 소품이 약 210px. 상점 카드 96dp도 넉넉히).
     늘리지 않는다.
  3) --apply면 core/ui/src/main/res/drawable-nodpi/ggf_pic_decor_<코드 소문자_밑줄>.webp와
     core/ui/.../DecorArtData.kt(그림이 있는 소품 목록)를 쓴다. 없으면 찾은 목록만 보인다.

검수에 합격한 것만 넣는다: R27은 차수 전체(ACCEPTED), R43은 소품 코드마다(ACCEPTED_CODES).
총괄 PM이 검수 결과를 알려 주면 ACCEPTED_CODES에 더하고, 다시 요청한 소품은 REJECTED에.

실행(저장소 루트에서):
  python3 design/illustrations/tools/build_forest_decor.py                 # 찾기만
  python3 design/illustrations/tools/build_forest_decor.py --apply         # 앱에 넣기
  python3 design/illustrations/tools/build_forest_decor.py --sheet /tmp/decor.png   # 모아 보기 그림
"""
from __future__ import annotations

import argparse
import glob
import os
import re
import sys

from PIL import Image, ImageDraw, ImageOps

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_app_assets import QUALITY, RES, ROOT, alpha_box, clean_alpha, pad_box, shrink  # noqa: E402

CATALOG_KT = os.path.join(ROOT, "core/account/src/main/kotlin/app/familyjourney/core/account/DecorCatalog.kt")
DATA_KT = os.path.join(ROOT, "core/ui/src/main/kotlin/app/familyjourney/core/ui/DecorArtData.kt")
EXPORTS = os.path.join(ROOT, "design/illustrations/exports")

# 검수 합격한 차수(총괄 PM이 알려 줌). R27 12종: 2026-11-05 합격(차수 전체)
ACCEPTED = {"R27"}
# R43은 소품마다 검수한다(GPT가 카테고리별로 나눠 납품). 합격한 코드만 넣는다
ACCEPTED_CODES = {
    # 1차 돌·바위 5종(main 097c2d8, 11-06 합격)
    "PEBBLE-PILE", "MOSS-ROCK", "ROUND-BOULDER", "CRYSTAL-ROCK", "STONE-CAIRN",
    # 2차 울타리 6·불빛 5·동물 집 5종(main 2ca65ba, 11-06 합격. 울타리 모서리는 v2 요청 중)
    "WOOD-FENCE-HORIZONTAL", "WOOD-FENCE-VERTICAL", "WOOD-FENCE-GATE", "WHITE-FLOWER-FENCE", "LOW-STONE-WALL", "LIVING-HEDGE-FENCE",
    "SAFE-CAMPFIRE", "CAMPING-LANTERN", "FIREFLY-JAR", "STRING-LIGHTS", "VINTAGE-STREETLAMP",
    "RABBIT-BURROW", "ACORN-BOX", "BEEHIVE", "BIRD-FEEDER", "DUCK-HOUSE",
    # 3차 집·오두막 5·쉼터·놀이 7·꽃·식물 7·물 5·계절·명절 8(main 65519f4, 11-06 32종 모두 합격)
    "LOG-CABIN", "TREEHOUSE", "FAIRY-MUSHROOM-CABIN", "SMALL-BARN", "MINI-LIGHTHOUSE",
    "GAZEBO", "HAMMOCK", "SLIDE", "SEESAW", "SANDBOX", "KITE-POLE", "STUMP-SEATS",
    "SQUARE-FLOWERBED", "SUNFLOWER-CLUSTER", "TULIP-ROW", "MUSHROOM-CLUSTER", "ROUND-BUSH", "REED-CLUSTER", "CLOVER-PATCH",
    "LILY-POND", "STONE-FOUNTAIN", "WELL", "WATERWHEEL", "BIRDBATH",
    "SNOWMAN", "HALLOWEEN-PUMPKINS", "DECORATED-FIR", "LOTUS-LANTERNS", "SONGPYEON-MOON", "SPRING-FLOWER-BASKET", "SNOW-SLED", "AUTUMN-LEAF-PILE",
    # 4차 길 6종 중 5종(d38ace1, 11-06 합격). 나무 데크(WOOD-DECK-TILE)는 v2 요청 중이라 넣지 않는다
    "STONE-PATH-TILE", "DIRT-PATH-TILE", "BRICK-PATH-TILE", "PETAL-PATH-TILE", "SMALL-WOOD-BRIDGE",
    # 울타리 모서리 v2(L자, c1a7f96 합격). 가장 큰 버전(v2)을 쓴다
    "WOOD-FENCE-CORNER",
    # 나무 데크 v2(cb6cb30 합격, 띠·이음새 없음). 가장 큰 버전(v2)을 쓴다. 이로써 R43 60종 모두
    "WOOD-DECK-TILE",
}
# 합격했다가 다시 요청 중인 것(새 버전이 와서 합격하면 여기서 뺀다)
REJECTED: set[str] = set()
# GPT 파일 이름의 영문 → 목록 코드(이름이 다를 때만)
ALIASES: dict[str, str] = {}

# DecorCatalog.kt의 카테고리 글자 → DecorCategory.code(이어 놓기·묶음에 씀)
GROUPS = {"R": "rock", "F": "fence", "P": "path", "H": "house", "S": "play", "L": "light", "G": "plant", "W": "water", "A": "animal", "E": "season"}

# 좌우로 뒤집어 만드는 소품: 새 코드 → 원본 코드(뒤집기는 색 필터가 아니라 같은 그림의 거울상)
MIRRORS = {"WOOD-FENCE-CORNER-R": "WOOD-FENCE-CORNER"}

# 이어 놓는 나무 울타리(가로·세로·모서리)의 이음점(Mac 검증 E3, 2026-11-06).
# GPT 세 장은 원근·비례가 조금씩 달라(모서리 기둥이 짧고 굵음) 칸 폭에 각각 맞추면 모서리가 작게 그려져 틈이 생겼다.
# 그래서 그림마다 "기둥 밑동 한 점"을 칸의 정한 자리에 맞추고, 크기는 원본 픽셀 비례로 정한다.
#   코드: (칸/원본px, 기준점 x, y(원본px, 알파 16 기준 상자 안), 칸 안 목표점 x, y(칸 단위, 기준 칸 왼쪽 위가 0,0))
#   - 가로: 왼쪽 기둥 밑동 → 칸 왼쪽 끝 x=0, y=0.75. 두 기둥 사이 608px = 1칸(이웃 가로와 기둥을 같이 씀)
#   - 세로: 앞 기둥 밑동 → x=0, y=0.615. 앞·뒤 기둥 사이 565px = 1칸 깊이(위아래로 이어 놓으면 기둥이 겹침)
#   - 모서리(└): 가로대 높이가 가로 울타리와 같도록 1.15/608. 오른쪽 팔 끝 기둥 → x=1, y=0.75
#     (오른쪽 칸 가로 울타리의 왼쪽 기둥과 같은 자리). 뒤 기둥이 위 칸 세로 울타리의 앞 기둥과 거의 같은 자리에 온다
#   - 모서리(┘, 뒤집은 것): 왼쪽 팔 끝 기둥 → x=0, y=0.75. 위로 잇는 세로 울타리는 오른쪽 칸(c+1, r-1)에 놓는다
FENCE_SCALE = 1 / 608
FITS = {
    "WOOD-FENCE-HORIZONTAL": (FENCE_SCALE, 92, 470, 0.0, 0.75),
    "WOOD-FENCE-VERTICAL": (1 / 565, 113, 875, 0.0, 0.615),
    "WOOD-FENCE-CORNER": (1.15 * FENCE_SCALE, 835, 930, 1.0, 0.75),
    "WOOD-FENCE-CORNER-R": (1.15 * FENCE_SCALE, -835, 930, 0.0, 0.75),  # 음수 x = 뒤집은 그림에서 오른쪽 끝부터
}

MAX_SIDE = 384
PAD = 4

NAME = re.compile(r"GGF-DECO-([A-Z0-9-]+?)-(R27|R43)-v(\d+)\.png$")
ITEM = re.compile(
    r'd\("([A-Z0-9-]+)",\s*"([^"]+)",\s*(\w),\s*[\d_]+,\s*(\d),\s*(\d),\s*([\d.]+)f(.*?)\)', re.S
)


def catalog() -> list[dict]:
    """DecorCatalog.kt의 소품(목록 순서 그대로)"""
    text = open(CATALOG_KT, encoding="utf-8").read()
    out = []
    for code, label, cat, w, h, height, rest in ITEM.findall(text):
        rnd = re.search(r'round = "(R\d+)"', rest)
        out.append({
            "code": code, "label": label, "group": GROUPS[cat], "w": int(w), "h": int(h), "height": float(height),
            "flat": "flat = true" in rest, "round": rnd.group(1) if rnd else "R43",
        })
    return out


def collect() -> dict[str, tuple[str, str]]:
    """코드 → (차수, 파일). 같은 코드는 가장 큰 버전"""
    best: dict[str, tuple[int, str, str]] = {}
    for path in glob.glob(os.path.join(EXPORTS, "**", "GGF-DECO-*.png"), recursive=True):
        m = NAME.search(os.path.basename(path))
        if not m:
            continue
        code = ALIASES.get(m.group(1), m.group(1))
        v = int(m.group(3))
        if code not in best or v > best[code][0]:
            best[code] = (v, m.group(2), path)
    return {c: (r, p) for c, (_, r, p) in best.items()}


def res_name(code: str) -> str:
    return "ggf_pic_decor_" + code.lower().replace("-", "_")


# GPT R43 그림은 알파 1~15의 희미한 점이 캔버스 가장자리까지 퍼져 있다(총괄 PM 검수 11-06).
# 알파 16 미만은 지우고(완전 투명) 알파 16 기준으로 자른다. 색은 건드리지 않는다.
FAINT_ALPHA = 16


def prepare(path: str, code: str = "", mirror: bool = False) -> tuple[Image.Image, str | None]:
    """(앱에 넣을 그림, 이음점 Kotlin 식 또는 None)"""
    im = clean_alpha(Image.open(path))
    r, g, b, a = im.split()
    a = a.point(lambda v: 0 if v < FAINT_ALPHA else v)
    im = Image.merge("RGBA", (r, g, b, a))
    if mirror:
        im = ImageOps.mirror(im)
    tight = alpha_box(im, FAINT_ALPHA - 1)
    box = pad_box(tight, im.size, PAD)
    cropped = im.crop(box)
    fit = None
    if code in FITS:
        k, x, y, cx, cy = FITS[code]
        tw = tight[2] - tight[0]
        if x < 0:
            x = tw + x  # 뒤집은 그림: 원본 상자의 x를 거울로
        bw, bh = cropped.size
        ref_x = (x + tight[0] - box[0]) / bw
        ref_y = (y + tight[1] - box[1]) / bh
        fit = f"DecorFit({bw * k:.4f}f, {ref_x:.4f}f, {ref_y:.4f}f, {cx}f, {cy}f)"
    return shrink(cropped, MAX_SIDE, MAX_SIDE), fit


def kt_float(v: float) -> str:
    return f"{v:.2f}f".replace(".00f", ".0f")


def write_data(rows: list[tuple[dict, int, int, str | None]]) -> None:
    lines = [
        "package app.familyjourney.core.ui",
        "",
        "// 만든 파일: design/illustrations/tools/build_forest_decor.py --apply 가 덮어쓴다. 손으로 고치지 않는다.",
        "// 숲 꾸미기 소품 그림(GPT R27·R43, 결정 #38). 그림이 있는 소품만 있다(없는 소품은 상점에 보이지 않음).",
        "// 칸·높이는 core/account DecorCatalog.kt에서 읽었다. 크기는 투명 여백을 잘라낸 WebP 크기.",
        "",
        "internal val decorArtData: List<DecorArt> = listOf(",
    ]
    for it, w, h, fit in rows:
        fp = {(1, 1): "Footprint.One", (2, 1): "Footprint.Wide", (2, 2): "Footprint.Square"}[(it["w"], it["h"])]
        lines.append(
            f'    DecorArt("{it["code"]}", "{it["label"]}", "{it["group"]}", R.drawable.{res_name(it["code"])}, {w}, {h}, {fp}, '
            f'{kt_float(it["height"])}, flat = {str(it["flat"]).lower()}' + (f", fit = {fit}" if fit else "") + "),"
        )
    lines.append(")")
    lines.append("")
    open(DATA_KT, "w", encoding="utf-8").write("\n".join(lines))
    print(f"썼음: {os.path.relpath(DATA_KT, ROOT)} ({len(rows)}종)")


def sheet(rows: list[tuple[dict, Image.Image]], out: str) -> None:
    cell = 200
    cols = 8
    n = len(rows)
    img = Image.new("RGBA", (cols * cell, max(1, (n + cols - 1) // cols) * (cell + 24)), (205, 230, 185, 255))
    d = ImageDraw.Draw(img)
    for i, (it, im) in enumerate(rows):
        t = im.copy()
        t.thumbnail((cell - 16, cell - 16))
        x = (i % cols) * cell
        y = (i // cols) * (cell + 24)
        img.alpha_composite(t, (x + (cell - t.width) // 2, y + (cell - t.height) // 2))
        d.text((x + 6, y + cell + 4), it["code"][:24], fill=(40, 40, 40, 255))
    img.save(out)
    print(f"모아 보기: {out}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--sheet")
    args = ap.parse_args()

    items = catalog()
    found = collect()
    known = {it["code"] for it in items}
    for code in sorted(set(found) - known):
        print(f"! 목록에 없는 코드: {code} ({found[code][1]}) → ALIASES에 더하거나 DecorCatalog.kt에 소품을 더하세요")

    rows: list[tuple[dict, int, int, str | None]] = []
    pics: list[tuple[dict, Image.Image]] = []
    for it in items:
        src = MIRRORS.get(it["code"], it["code"])
        if src not in found:
            continue
        rnd, path = found[src]
        if (rnd not in ACCEPTED and src not in ACCEPTED_CODES) or src in REJECTED:
            print(f"- 검수 전/불합격: {it['code']} ({rnd})")
            continue
        im, fit = prepare(path, it["code"], mirror=it["code"] in MIRRORS)
        pics.append((it, im))
        if args.apply:
            os.makedirs(RES, exist_ok=True)
            dst = os.path.join(RES, res_name(it["code"]) + ".webp")
            im.save(dst, "WEBP", quality=QUALITY, method=6, exact=True)
            print(f"{res_name(it['code'])}.webp  {im.width}x{im.height}  {os.path.getsize(dst) // 1024}KB")
        rows.append((it, im.width, im.height, fit))
    missing = [it["code"] for it in items if MIRRORS.get(it["code"], it["code"]) not in found]
    print(f"그림 있음 {len(rows)} / 목록 {len(items)} (그림 없음 {len(missing)})")
    if args.apply:
        write_data(rows)
    if args.sheet:
        sheet(pics, args.sheet)


if __name__ == "__main__":
    main()
