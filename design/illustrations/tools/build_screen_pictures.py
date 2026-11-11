#!/usr/bin/env python3
"""
같이가숲 — GPT 화면 그림(R22·R32·R36·R39·R42)을 앱에 넣는 스크립트("앱 개발·화면 그림 연결" 세션 담당).

GPT 파일 이름: GGF-<이름>-v<번호>.png. <이름>은 core/ui ScreenPictures.kt의 ScreenArt gptName
(예: ARRIVAL-GRANDMA-R22, GUIDE-ONBOARD-1-R32, ICON-HOME-R39). 같은 이름이 여러 버전이면 번호가 가장 큰 것.
R39 아이콘은 요청문에 영문 이름을 정하지 않았으므로 ALIASES의 비슷한 이름도 받는다. 모르는 GGF-…-R22/R32/R36/R39/R42 파일은 알린다.

하는 일(화질·색감 규칙 #29-⑦은 build_app_assets.py와 같다: 늘리지 않음, 색은 그대로, 알파만 정리, WebP 92)
  - 바탕 그림(도착지·휴게소, 불투명 1536×1024): 자르지 않고 폭 1536까지(줄이기만).
  - 투명 그림(서 있는 가족·안내·보상·장식·아이콘): 알파 정리 → 알파 상자 + 여백으로 자르기 → 종류별 최대 크기까지 줄이기.
  - --apply면 core/ui/src/main/res/drawable-nodpi/ggf_pic_screen_<이름>.webp와
    core/ui/.../ScreenPictureData.kt(들어온 그림 목록)를 쓴다. 없으면 검사 결과만.

실행(저장소 루트에서):
  python3 design/illustrations/tools/build_screen_pictures.py            # exports/ 전체에서 찾아 검사만
  python3 design/illustrations/tools/build_screen_pictures.py --apply    # 앱에 넣기
  python3 design/illustrations/tools/build_screen_pictures.py --sheet /tmp/screen.png   # 모아 보기
"""
from __future__ import annotations

import argparse
import glob
import os
import re
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_app_assets import QUALITY, RES, ROOT, alpha_box, clean_alpha, pad_box, shrink  # noqa: E402

UI = os.path.join(ROOT, "core/ui/src/main/kotlin/app/familyjourney/core/ui")
ARTS_KT = os.path.join(UI, "ScreenPictures.kt")
DATA_KT = os.path.join(UI, "ScreenPictureData.kt")
EXPORTS = os.path.join(ROOT, "design/illustrations/exports")

REQUESTS = ("R22", "R32", "R36", "R39", "R42")
FILE = re.compile(r"^GGF-(.+-(?:" + "|".join(REQUESTS) + r"))-v(\d+)\.png$")

# R39 아이콘: GPT가 붙일 수 있는 다른 이름 → gptName
ALIASES = {
    "ICON-TREE-R39": "ICON-FOREST-R39",
    # GPT 네 번째 묶음(2026-11-05)이 붙인 이름
    "ICON-TREE-CATALOG-R39": "ICON-TREE-BOOK-R39",
    "ICON-ANIMAL-CATALOG-R39": "ICON-ANIMAL-BOOK-R39",
    "ICON-FAMILY-CAR-R39": "ICON-CAR-R39",
    "ICON-ROAD-SCENERY-R39": "ICON-ROAD-R39",
    "ICON-TREEBOOK-R39": "ICON-TREE-BOOK-R39",
    "ICON-TREE-GUIDE-R39": "ICON-TREE-BOOK-R39",
    "ICON-ANIMALBOOK-R39": "ICON-ANIMAL-BOOK-R39",
    "ICON-ANIMAL-GUIDE-R39": "ICON-ANIMAL-BOOK-R39",
    "ICON-PAW-BOOK-R39": "ICON-ANIMAL-BOOK-R39",
    "ICON-FOREST-MAP-R39": "ICON-MAP-R39",
    "ICON-MY-CAR-R39": "ICON-CAR-R39",
    "ICON-ROAD-THEME-R39": "ICON-ROAD-R39",
    "ICON-ROAD-SCENE-R39": "ICON-ROAD-R39",
    "ICON-SOUNDON-R39": "ICON-SOUND-ON-R39",
    "ICON-UNMUTE-R39": "ICON-SOUND-ON-R39",
    "ICON-SOUNDOFF-R39": "ICON-SOUND-OFF-R39",
    "ICON-MUTE-R39": "ICON-SOUND-OFF-R39",
    "ICON-ZOOM-R39": "ICON-BIGGER-R39",
    "ICON-MAGNIFIER-R39": "ICON-BIGGER-R39",
    "ICON-BIG-R39": "ICON-BIGGER-R39",
    "ICON-LARGER-R39": "ICON-BIGGER-R39",
    "ICON-WAVE-HOUSE-R39": "ICON-SHARE-R39",
    "ICON-X-R39": "ICON-CLOSE-R39",
}

# (최대 폭, 최대 높이, 바탕 그림인가) — gptName 앞부분으로 정한다
ICON_MAX = 192  # 48dp × xxxhdpi(4배)
KINDS = [
    ("ARRIVAL-", (1536, None, True)),
    ("REST-AREA", (1536, None, True)),
    ("FAMILY-STAND-", (None, 900, False)),   # 서 있는 사람과 같은 키(build_app_assets.PERSON_MAX_H)
    ("GUIDE-", (720, 720, False)),
    ("ICON-", (ICON_MAX, ICON_MAX, False)),
    ("REWARD-", (512, 512, False)),
    ("CELEBRATE-BALLOONS", (640, 640, False)),
    ("CELEBRATE-", (1536, None, False)),
]
PAD = 8


def arts() -> dict[str, str]:
    """ScreenPictures.kt의 ScreenArt: gptName → enum 이름(목록 순서 유지)"""
    text = open(ARTS_KT, encoding="utf-8").read()
    return {gpt: name for name, gpt in re.findall(r'^\s+(\w+)\("([A-Z0-9-]+-R\d+)"', text, re.M)}


def kind(gpt: str):
    for prefix, k in KINDS:
        if gpt.startswith(prefix):
            return k
    raise ValueError(gpt)


def collect(dirs: list[str], known: dict[str, str]) -> tuple[dict[str, str], list[str]]:
    best: dict[str, tuple[int, str]] = {}
    unknown: list[str] = []
    for d in dirs:
        for path in glob.glob(os.path.join(d, "**", "*.png"), recursive=True):
            m = FILE.match(os.path.basename(path))
            if not m:
                continue
            gpt = ALIASES.get(m.group(1), m.group(1))
            if gpt not in known:
                unknown.append(os.path.relpath(path, ROOT))
                continue
            v = int(m.group(2))
            if gpt not in best or v > best[gpt][0]:
                best[gpt] = (v, path)
    return {g: p for g, (_, p) in best.items()}, unknown


def build(gpt: str, path: str) -> tuple[Image.Image, list[str]]:
    max_w, max_h, backdrop = kind(gpt)
    notes: list[str] = []
    im = Image.open(path)
    if backdrop:
        im = im.convert("RGB")
        if im.size[0] < im.size[1]:
            notes.append(f"{gpt}: 가로 그림이어야 함({im.size[0]}x{im.size[1]})")
        return shrink(im, max_w=max_w, max_h=max_h), notes
    im = clean_alpha(im)
    box = alpha_box(im)
    if box is None:
        return im, [f"{gpt}: 그림이 비어 있음"]
    a = im.getchannel("A")
    corners = [a.getpixel(p) for p in ((0, 0), (im.size[0] - 1, 0), (0, im.size[1] - 1), (im.size[0] - 1, im.size[1] - 1))]
    if min(corners) > 200:
        notes.append(f"{gpt}: 투명 배경이 아님(네 귀퉁이가 불투명) — GPT에 투명 PNG로 다시 요청할지 판단")
    return shrink(im.crop(pad_box(box, im.size, PAD)), max_w=max_w, max_h=max_h), notes


def res_name(gpt: str) -> str:
    return "ggf_pic_screen_" + gpt.lower().replace("-", "_")


def write_kotlin(entries: list[tuple[str, int, int]], enum_of: dict[str, str]) -> None:
    lines = [
        "package app.familyjourney.core.ui",
        "",
        "// 만든 파일: design/illustrations/tools/build_screen_pictures.py --apply 가 덮어쓴다. 손으로 고치지 않는다.",
        "// GPT R22·R32·R36·R39·R42 화면 그림. 크기는 WebP 실제 크기.",
        "",
        "internal val screenPictureData: List<ScreenPicture> = listOf(",
    ]
    for gpt, w, h in entries:
        lines.append(f"    ScreenPicture(ScreenArt.{enum_of[gpt]}, R.drawable.{res_name(gpt)}, {w}, {h}),")
    lines.append(")")
    open(DATA_KT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(f"썼음: {os.path.relpath(DATA_KT, ROOT)} ({len(entries)}장)")


def sheet(pics: list[tuple[str, Image.Image]], path: str) -> None:
    cell = 256
    cols = 6
    rows = max(1, (len(pics) + cols - 1) // cols)
    img = Image.new("RGB", (cols * cell, rows * (cell + 20)), (236, 244, 236))
    from PIL import ImageDraw

    d = ImageDraw.Draw(img)
    for i, (gpt, im) in enumerate(pics):
        x0, y0 = (i % cols) * cell, (i // cols) * (cell + 20)
        t = im.copy()
        t.thumbnail((cell - 8, cell - 8))
        img.paste(t, (x0 + 4, y0 + 4), t if t.mode == "RGBA" else None)
        d.text((x0 + 4, y0 + cell), gpt, fill=(20, 40, 20))
    img.save(path)
    print(f"모아 보기: {path}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dirs", nargs="*", default=[EXPORTS], help="GPT 그림을 찾을 폴더(하위 폴더 포함)")
    ap.add_argument("--apply", action="store_true", help="WebP와 ScreenPictureData.kt를 쓴다")
    ap.add_argument("--sheet", help="모아 보기 PNG 경로")
    args = ap.parse_args()

    enum_of = arts()
    found, unknown = collect(args.dirs, enum_of)
    for u in unknown:
        print("  ⚠ ScreenArt에 없는 이름(넣지 않음, ALIASES나 ScreenPictures.kt에 더할지 판단):", u)
    entries: list[tuple[str, int, int]] = []
    pics: list[tuple[str, Image.Image]] = []
    for gpt in enum_of:  # ScreenArt 순서대로
        if gpt not in found:
            continue
        im, notes = build(gpt, found[gpt])
        for n in notes:
            print("  ⚠", n)
        print(f"{gpt}: {im.size[0]}x{im.size[1]}  ← {os.path.relpath(found[gpt], ROOT)}")
        entries.append((gpt, im.size[0], im.size[1]))
        pics.append((gpt, im))
        if args.apply:
            os.makedirs(RES, exist_ok=True)
            im.save(os.path.join(RES, res_name(gpt) + ".webp"), "WEBP", quality=QUALITY, method=6, exact=True)
    print(f"넣을 수 있는 그림 {len(entries)}장 / 목록 {len(enum_of)}장")
    if args.sheet and pics:
        sheet(pics, args.sheet)
    if args.apply:
        write_kotlin(entries, enum_of)


if __name__ == "__main__":
    main()
