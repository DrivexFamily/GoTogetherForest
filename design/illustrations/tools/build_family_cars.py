#!/usr/bin/env python3
"""
같이가숲 — GPT R21 현대·기아 차 그림을 앱에 넣는 스크립트(결정 #34, Claude Code 담당).

GPT 파일 이름(요청문 R21): GGF-CAR-<코드>-<포즈>-R21-v<번호>.png
  코드: core/ui FamilyCars.kt의 CarModel 코드(예: H-PALISADE, K-EV9)
  포즈: EMPTY(빈 차), FAMILY-START(출발), FAMILY-LOOKOUT(창밖 보기), FAMILY-WAVE(손 흔들기), FAMILY-ALMOST(거의 다 왔어)
  같은 차·같은 포즈가 여러 버전이면 번호가 가장 큰 것을 쓴다. EMPTY와 FAMILY-START가 다 있는 차만 넣는다.

하는 일(화질·색감 규칙 #29-⑦은 build_app_assets.py와 같다: 늘리지 않음, 색은 그대로, 알파만 정리, WebP 92)
  1) 같은 차의 그림을 모두 한 상자(알파 영역의 합 + 8px)로 잘라 포즈가 바뀌어도 차가 흔들리지 않게 한다.
     차 위치가 6px 넘게 어긋난 그림은 알린다(앱에서 포즈가 바뀔 때 차가 튐 → GPT에 다시 요청할지 판단).
  2) 폭 960px까지 줄인다(기존 가족 차와 같음).
  3) 바퀴가 땅에 닿는 높이(그림자는 빼고 거의 불투명한 가장 아래 줄)를 재어 groundRatio로 쓴다.
  4) --apply면 core/ui/src/main/res/drawable-nodpi/ggf_pic_car_<코드>_<포즈>.webp와
     core/ui/.../FamilyCarData.kt(차 목록)를 쓴다. 없으면 검사 결과와 모아 보기만 만든다.

실행(저장소 루트에서):
  python3 design/illustrations/tools/build_family_cars.py                 # exports/ 전체에서 찾아 검사만
  python3 design/illustrations/tools/build_family_cars.py --apply         # 앱에 넣기
  python3 design/illustrations/tools/build_family_cars.py --sheet /tmp/cars.png   # 모아 보기 그림
"""
from __future__ import annotations

import argparse
import glob
import os
import re
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_app_assets import QUALITY, RES, ROOT, alpha_box, clean_alpha, pad_box, shrink  # noqa: E402

UI = os.path.join(ROOT, "core/ui/src/main/kotlin/app/familyjourney/core/ui")
MODELS_KT = os.path.join(UI, "FamilyCars.kt")
DATA_KT = os.path.join(UI, "FamilyCarData.kt")
EXPORTS = os.path.join(ROOT, "design/illustrations/exports")

POSES = ["EMPTY", "FAMILY-START", "FAMILY-LOOKOUT", "FAMILY-WAVE", "FAMILY-ALMOST"]
RES_SUFFIX = {"EMPTY": "empty", "FAMILY-START": "start", "FAMILY-LOOKOUT": "lookout", "FAMILY-WAVE": "wave", "FAMILY-ALMOST": "almost"}
KT_PARAM = {"FAMILY-LOOKOUT": "lookOut", "FAMILY-WAVE": "wave", "FAMILY-ALMOST": "almost"}
NAME = re.compile(r"GGF-CAR-([HK]-[A-Z0-9]+)-(" + "|".join(POSES) + r")-R21-v(\d+)\.png$")

CAR_MAX_W = 960
PAD = 8
SHIFT_WARN_PX = 6


def models() -> dict[str, str]:
    """FamilyCars.kt의 CarModel: 코드 → enum 이름(목록 순서 유지)"""
    text = open(MODELS_KT, encoding="utf-8").read()
    return {code: name for name, code in re.findall(r'^\s+(\w+)\("([HK]-[A-Z0-9]+)"', text, re.M)}


def collect(dirs: list[str]) -> dict[str, dict[str, str]]:
    """코드 → 포즈 → 파일(가장 큰 버전)"""
    best: dict[tuple[str, str], tuple[int, str]] = {}
    for d in dirs:
        for path in glob.glob(os.path.join(d, "**", "*.png"), recursive=True):
            m = NAME.search(os.path.basename(path))
            if not m:
                continue
            key = (m.group(1), m.group(2))
            v = int(m.group(3))
            if key not in best or v > best[key][0]:
                best[key] = (v, path)
    out: dict[str, dict[str, str]] = {}
    for (code, pose), (_, path) in best.items():
        out.setdefault(code, {})[pose] = path
    return out


def solid_box(im: Image.Image):
    """거의 불투명(250 이상)한 부분의 상자: 차 몸통 위치 비교용(그림자·가장자리 제외)"""
    return im.getchannel("A").point(lambda v: 255 if v >= 250 else 0).getbbox()


def ground_ratio(im: Image.Image) -> float:
    """
    바퀴가 땅에 닿는 높이(위 0 → 아래 1).
    GPT 차 그림은 바퀴 아래 그림자도 불투명(회색 타원)이라, 아래에서 위로 올라가며 줄마다 불투명 픽셀 수를 본다:
    그림자 아래쪽에서 늘다가(그림자 가운데) 줄어들고(그림자 위 끝 = 바퀴 바닥), 다시 늘어난다(바퀴가 둥글게 넓어짐).
    그 사이의 가장 좁은 줄을 바퀴 바닥으로 본다. 아래 12% 안에서 그런 모양이 없으면(그림자가 반투명) 가장 아래 불투명 줄.
    """
    a = np.asarray(im.getchannel("A"))
    h, w = a.shape
    counts = (a >= 250).sum(axis=1).astype(float)
    counts = np.convolve(counts, np.ones(3) / 3, mode="same")  # 1px 흔들림 줄이기
    rows = np.where(counts > w * 0.01)[0]
    if len(rows) == 0:
        return 0.95
    last = int(rows[-1])
    up = list(range(last, int(h * 0.88), -1))
    i = 0
    while i + 1 < len(up) and counts[up[i + 1]] >= counts[up[i]]:
        i += 1
    j = i
    while j + 1 < len(up) and counts[up[j + 1]] <= counts[up[j]]:
        j += 1
    ground = up[j] if (i > 0 and j > i and j + 1 < len(up)) else last
    return float(ground + 1) / h


def build(code: str, files: dict[str, str]) -> tuple[dict | None, list[str], dict[str, Image.Image]]:
    notes: list[str] = []
    if "EMPTY" not in files or "FAMILY-START" not in files:
        return None, [f"{code}: 빈 차와 출발 그림이 다 있어야 넣는다(있는 것: {', '.join(sorted(files))})"], {}
    ims = {p: clean_alpha(Image.open(f)) for p, f in files.items()}
    sizes = {im.size for im in ims.values()}
    if len(sizes) != 1:
        notes.append(f"{code}: 캔버스 크기가 다름 {sorted(sizes)} — 왼쪽 위 기준으로 맞춤")
    canvas = max(sizes)
    boxes = [alpha_box(im) for im in ims.values()]
    union = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
    union = pad_box(union, canvas, PAD)
    ref = solid_box(ims["FAMILY-START"])
    for p, im in ims.items():
        sb = solid_box(im)
        if sb and ref:
            dx = max(abs(sb[0] - ref[0]), abs(sb[2] - ref[2]))
            dy = abs(sb[3] - ref[3])
            if max(dx, dy) > SHIFT_WARN_PX:
                notes.append(f"{code} {p}: 출발 그림과 차 위치가 {dx}px(가로)·{dy}px(바닥) 어긋남 — 포즈가 바뀔 때 차가 튈 수 있음")
    out = {p: shrink(im.crop(union), max_w=CAR_MAX_W) for p, im in ims.items()}
    w, h = out["FAMILY-START"].size
    # 같은 상자로 잘랐으니 바퀴 바닥은 같다. 그림자 모양 때문에 한 장에서 못 찾으면 값이 커지므로 가장 작은 값을 쓴다
    entry = {"code": code, "w": w, "h": h, "ground": min(ground_ratio(im) for im in out.values()), "poses": sorted(out)}
    return entry, notes, out


def res_name(code: str, pose: str) -> str:
    return f"ggf_pic_car_{code.lower().replace('-', '_')}_{RES_SUFFIX[pose]}"


def write_kotlin(entries: list[dict], enum_of: dict[str, str]) -> None:
    lines = [
        "package app.familyjourney.core.ui",
        "",
        "// 만든 파일: design/illustrations/tools/build_family_cars.py --apply 가 덮어쓴다. 손으로 고치지 않는다.",
        "// GPT R21 현대·기아 차 그림(결정 #34). 크기는 같은 차의 모든 그림을 자른 상자, groundRatio는 바퀴 바닥 높이.",
        "",
        "internal val familyCarData: List<FamilyCar> = listOf(",
    ]
    for e in entries:
        args = [
            f"CarModel.{enum_of[e['code']]}",
            str(e["w"]),
            str(e["h"]),
            f"{e['ground']:.4f}f",
            f"R.drawable.{res_name(e['code'], 'EMPTY')}",
            f"R.drawable.{res_name(e['code'], 'FAMILY-START')}",
        ]
        for pose, param in KT_PARAM.items():
            if pose in e["poses"]:
                args.append(f"{param} = R.drawable.{res_name(e['code'], pose)}")
        lines.append("    FamilyCar(" + ", ".join(args) + "),")
    lines.append(")")
    open(DATA_KT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(f"썼음: {os.path.relpath(DATA_KT, ROOT)} ({len(entries)}대)")


def sheet(results: list[tuple[dict, dict[str, Image.Image]]], path: str) -> None:
    """모아 보기: 차마다 한 줄(빈 차, 출발, 나머지 포즈). 바퀴 바닥 높이에 빨간 선"""
    cell_w, cell_h = 320, 200
    img = Image.new("RGB", (cell_w * len(POSES) + 140, max(1, len(results)) * cell_h), (236, 244, 236))
    d = ImageDraw.Draw(img)
    for row, (e, pics) in enumerate(results):
        y0 = row * cell_h
        d.text((8, y0 + 8), e["code"], fill=(20, 40, 20))
        for col, pose in enumerate(POSES):
            if pose not in pics:
                continue
            im = pics[pose].copy()
            im.thumbnail((cell_w - 10, cell_h - 10))
            x0 = 140 + col * cell_w
            img.paste(im, (x0, y0 + 5), im)
            gy = y0 + 5 + round(im.size[1] * e["ground"])
            d.line([(x0, gy), (x0 + im.size[0], gy)], fill=(220, 40, 40), width=1)
    img.save(path)
    print(f"모아 보기: {path}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dirs", nargs="*", default=[EXPORTS], help="GPT 그림을 찾을 폴더(하위 폴더 포함)")
    ap.add_argument("--apply", action="store_true", help="WebP와 FamilyCarData.kt를 쓴다")
    ap.add_argument("--sheet", help="모아 보기 PNG 경로")
    args = ap.parse_args()

    enum_of = models()
    found = collect(args.dirs)
    unknown = sorted(set(found) - set(enum_of))
    if unknown:
        print("FamilyCars.kt에 없는 코드(넣지 않음):", ", ".join(unknown))
    entries: list[dict] = []
    results: list[tuple[dict, dict[str, Image.Image]]] = []
    for code in enum_of:  # 목록 순서대로
        if code not in found:
            continue
        entry, notes, pics = build(code, found[code])
        for n in notes:
            print("  ⚠", n)
        if entry is None:
            continue
        print(f"{code}: {entry['w']}x{entry['h']} 바퀴 바닥 {entry['ground']:.3f} 포즈 {len(entry['poses'])}장")
        entries.append(entry)
        results.append((entry, pics))
        if args.apply:
            os.makedirs(RES, exist_ok=True)
            for pose, im in pics.items():
                im.save(os.path.join(RES, res_name(code, pose) + ".webp"), "WEBP", quality=QUALITY, method=6, exact=True)
    print(f"넣을 수 있는 차 {len(entries)}대 / 목록 {len(enum_of)}대")
    if args.sheet and results:
        sheet(results, args.sheet)
    if args.apply:
        write_kotlin(entries, enum_of)


if __name__ == "__main__":
    main()
