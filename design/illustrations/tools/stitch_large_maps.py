#!/usr/bin/env python3
"""
같이가숲 — GPT R17 큰 숲 맵 조각 16장을 이어 앱·웹용 맵 4장으로 만든다(Claude Code 담당, 결정 #32).

GPT 납품(main 2e71da1): 장소마다 1536×1024 조각 4장(TL·TR·BL·BR). 겹침 128px로 놓으면 2944×1920.
다만 겹치는 띠가 같은 픽셀이 아니다(GPT 보고: 평균 차이 9~24, 해안선·길·잔디 색이 어긋남).

잇는 방법(모핑 섞기):
  1. 겹침 띠(128px)마다 두 조각의 모양 차이를 구한다.
     - 띠를 따라 160px 구간마다 "같은 것을 확실히 그렸을 때만"(맞췄을 때 차이가 0.6배 이하) 어긋난 양을 잰다.
     - 남은 차이는 광학 흐름(OpenCV DIS)으로 구하고, 잔디 무늬 같은 잔 흐름은 버린다(가우스 18px).
  2. 띠를 지나며 한쪽 모양에서 다른 쪽 모양으로 조금씩 옮겨 가며 섞는다(S자 비율). 해안선·절벽·길이 두 겹이나
     계단 없이 이어진다. 띠 밖의 픽셀은 조각 그대로다.
     모양을 맞춘 뒤에도 크게 다른 곳(한쪽 조각에만 있는 돌·꽃·가로등)은 섞지 않고, 두 조각이 가장 비슷한 선을 따라
     한쪽 것을 통째로 쓴다(±5px만 섞음). 반투명하게 두 겹으로 비치지 않는다(blend_weights, 2026-11-04 Mac 검증 반영).
  3. 양옆 32px씩 잘라 2880×1920(정확히 3:2)으로 만든다(GPT 보고서 제안). 그리드 30×20칸, 칸 96px.
화질·색감 규칙(#29-⑦): 늘리지 않고(크기 그대로), 색 보정·필터를 쓰지 않는다. 바뀌는 것은 겹침 띠의 섞기뿐이다.

알려진 흠(2026-11-04): 섬 오른쪽 해안선에 작은 홈, 공원 왼쪽 길 턱 안쪽 선의 짧은 꺾임. 바닷가 오른쪽 산책로는
TR v2(GPT 고침)로 끝까지 이어진다. 그 이음새의 가로등은 머리가 띠 안에 있어 잘렸던 것을 아래 조각 것으로 살렸다(KEEP_B, 2026-11-05).
저녁 조각: R18 v1은 조각마다 밝기·색이 많이 달라(가운데 잔디 밝기 차 9~28, 낮은 2~7) 쓰지 않았고,
R18 v3(장소마다 TL 기준으로 조명을 맞춤, 밝기 차 3.7~5.1)를 쓴다. 낮과 윤곽 어긋남 0.3px 이하로 같은 칸 지도를 쓴다.
기본 섬 위쪽(R19, 2026-11-05): 예전 섬(ILL-002)처럼 높은 고원과 큰 폭포·연못을 그린 TL·TR로 바꿨다(낮 v2·저녁 v4).
아래 조각(BL·BR)과의 띠는 같은 픽셀이 아니지만(평균 차 12~15) 위 섞기로 잇는다. 고원 윗선이 TL·TR 사이에서 조금 굽는다.
main()은 낮 맵(ggf_pic_*)과 저녁 맵(ggf_pic_*_evening)을 함께 만든다.

실행(저장소 루트에서, numpy·opencv-python-headless·Pillow가 있는 파이썬):
  <venv>/bin/python design/illustrations/tools/stitch_large_maps.py
결과:
  core/ui/src/main/res/drawable-nodpi/ggf_pic_{forest_island,map_coast,map_park,map_river}.webp (2880×1920, WebP 92)
  같은 이름 + _evening (저녁 맵, 다크 모드)
  web/forest/assets/ 같은 이름(웹 미리보기)
  --preview 폴더를 주면 이어 붙인 PNG와 1/3 크기 미리보기도 저장한다.
"""
from __future__ import annotations

import argparse
import os
import shutil

import cv2
import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
EXPORTS = os.path.join(ROOT, "design/illustrations/exports/2026-11-04")
RES = os.path.join(ROOT, "core/ui/src/main/res/drawable-nodpi")
WEB_ASSETS = os.path.join(ROOT, "web/forest/assets")
QUALITY = 92

OVERLAP = 128
CROP_X = 32  # 2944 → 2880
MAPS = {
    "ISLAND": "ggf_pic_forest_island",
    "COAST": "ggf_pic_map_coast",
    "PARK": "ggf_pic_map_park",
    "RIVER": "ggf_pic_map_river",
}

_dis = cv2.DISOpticalFlow_create(cv2.DISOPTICAL_FLOW_PRESET_MEDIUM)


# 조각 파일: 낮은 R17 v1, 저녁은 R18 v3. 바닷가 오른쪽 위(TR) 낮은 v2(산책로를 끝까지 이음).
# 기본 섬 위쪽 두 장(TL·TR)은 R19(2026-11-05): 높은 고원·큰 폭포·연못. 낮 R17 v2 + 저녁 R18 v4 한 쌍으로만 바꾼다.
TILE_OVERRIDES = {
    ("COAST", "TR", "day"): "GGF-MAP-COAST-LARGE-R17-v2-TR.png",
    ("ISLAND", "TL", "day"): "GGF-MAP-ISLAND-LARGE-R17-v2-TL.png",
    ("ISLAND", "TR", "day"): "GGF-MAP-ISLAND-LARGE-R17-v2-TR.png",
    ("ISLAND", "TL", "evening"): "GGF-MAP-ISLAND-LARGE-EVENING-R18-v4-TL.png",
    ("ISLAND", "TR", "evening"): "GGF-MAP-ISLAND-LARGE-EVENING-R18-v4-TR.png",
}


def tile_file(place: str, tile: str, variant: str) -> str:
    if (place, tile, variant) in TILE_OVERRIDES:
        return TILE_OVERRIDES[(place, tile, variant)]
    if variant == "evening":
        # R18 v3(2026-11-04 밤): 장소마다 TL 저녁 조각을 기준으로 나머지 3조각의 조명을 맞춤. 바닷가 TR은 낮 TR v2 기준
        return f"GGF-MAP-{place}-LARGE-EVENING-R18-v3-{tile}.png"
    return f"GGF-MAP-{place}-LARGE-R17-v1-{tile}.png"


def load(place: str, tile: str, variant: str = "day") -> np.ndarray:
    path = os.path.join(EXPORTS, tile_file(place, tile, variant))
    im = Image.open(path).convert("RGB")
    if im.size != (1536, 1024):
        raise SystemExit(f"{path}: 1536×1024가 아님({im.size})")
    return np.asarray(im).astype(np.float32)


def along_shift(ga: np.ndarray, gb: np.ndarray) -> np.ndarray:
    """띠를 따라 구간(160px, 32px 간격)마다 b가 a보다 띠 방향으로 얼마나 어긋났나. 확실할 때만, 아니면 0"""
    h = ga.shape[0]
    length, step, reach = 160, 32, 96
    ys, ds = [], []
    for y0 in range(0, h - length + 1, step):
        a = ga[y0:y0 + length]
        best_d, best_dy, d0 = None, 0, None
        for dy in range(-reach, reach + 1, 2):
            if y0 + dy < 0 or y0 + dy + length > h:
                continue
            d = float(np.abs(a - gb[y0 + dy:y0 + dy + length]).mean())
            if dy == 0:
                d0 = d
            if best_d is None or d < best_d:
                best_d, best_dy = d, dy
        strong = d0 is not None and d0 - best_d > 6.0 and best_d < 0.6 * d0
        ys.append(y0 + length / 2)
        ds.append(best_dy if strong else 0.0)
    dy = np.interp(np.arange(h), ys, ds).astype(np.float32)
    return cv2.GaussianBlur(dy[:, None], (0, 0), 24)[:, 0]


def flow(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    ga = cv2.cvtColor(a.astype(np.uint8), cv2.COLOR_RGB2GRAY)
    gb = cv2.cvtColor(b.astype(np.uint8), cv2.COLOR_RGB2GRAY)
    h, w = ga.shape
    init = np.zeros((h, w, 2), np.float32)
    init[..., 1] = along_shift(ga.astype(np.float32), gb.astype(np.float32))[:, None]
    xs, ys = np.meshgrid(np.arange(w, dtype=np.float32), np.arange(h, dtype=np.float32))
    gb0 = cv2.remap(gb, xs + init[..., 0], ys + init[..., 1], cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT)
    f = init + _dis.calc(ga, gb0, None)
    f[..., 0] = cv2.GaussianBlur(f[..., 0], (0, 0), 18)
    f[..., 1] = cv2.GaussianBlur(f[..., 1], (0, 0), 18)
    return f


def morph_band(a: np.ndarray, b: np.ndarray, keep_b: tuple = ()) -> np.ndarray:
    """같은 자리를 그린 두 띠(h × 128)를 왼쪽 a → 오른쪽 b로 모양을 옮겨 가며 섞는다. keep_b: [KEEP_B] 구간"""
    h, w, _ = a.shape
    f = flow(a, b)
    t = (np.arange(w) + 0.5) / w
    t = t * t * (3 - 2 * t)
    tt = np.broadcast_to(t[None, :], (h, w)).astype(np.float32)
    xs, ys = np.meshgrid(np.arange(w, dtype=np.float32), np.arange(h, dtype=np.float32))
    wa = cv2.remap(a, xs - tt * f[..., 0], ys - tt * f[..., 1], cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT)
    wb = cv2.remap(b, xs + (1 - tt) * f[..., 0], ys + (1 - tt) * f[..., 1], cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT)
    weight = keep_from_b(blend_weights(wa, wb, tt), keep_b)
    return wa * (1 - weight[..., None]) + wb * weight[..., None]


# 띠를 따라 이 구간(띠 방향 좌표, 2944 기준)은 b 조각 것을 통째로 쓴다. 한쪽 조각에만 있는 물체가 띠 밖으로 이어져
# 자르는 선으로는 반만 남는 곳(2026-11-05 Mac 검증: 바닷가 오른쪽 가로등이 머리 없이 기둥만 보임 — 기둥은 아래 조각,
# 머리는 띠 안). 바다 물결·길·꽃이 두 조각에서 다 달라 "다른 곳" 덩어리가 띠 전체로 이어져 자동으로는 못 가른다.
# 띠의 a쪽 가장자리 KEEP_B_START px은 a → b로 넘어가는 자리로 남긴다.
KEEP_B = {
    ("COAST", "v"): ((2828, 2878),),  # 위 조각(TR)과 아래 조각(BR) 사이, 오른쪽 산책로 가로등 하나의 폭만
}
KEEP_B_START, KEEP_B_FEATHER = 12, 3.0  # 넓게 잡으면 아래 조각의 난간이 흐리게 비친다


def keep_from_b(weight: np.ndarray, spans: tuple) -> np.ndarray:
    if not spans:
        return weight
    h, w = weight.shape
    mask = np.zeros((h, w), np.float32)
    for r0, r1 in spans:
        mask[r0:r1, KEEP_B_START:] = 1.0
    mask = cv2.GaussianBlur(mask, (0, 0), KEEP_B_FEATHER)
    return np.maximum(weight, mask)


# 섞기 비율: 잔디처럼 두 조각이 비슷한 곳은 띠 전체에서 천천히(S자), 한쪽 조각에만 있는 돌·꽃·가로등처럼
# 모양을 맞춘 뒤에도 크게 다른 곳은 섞지 않고 "두 조각이 가장 비슷한 선"을 따라 한쪽 것을 통째로 쓴다(좁게 ±CUT_FEATHER).
# 그래야 반투명하게 두 겹으로 비치지 않는다(2026-11-04 Mac 검증: 강가 돌·꽃, 바닷가 가로등).
CUT_FEATHER = 5.0
DIFF_LOW, DIFF_HIGH = 28.0, 70.0


def blend_weights(wa: np.ndarray, wb: np.ndarray, tt: np.ndarray) -> np.ndarray:
    h, w = tt.shape
    diff = np.abs(wa - wb).sum(axis=2).astype(np.float32)
    # 자르는 선: 위에서 아래로 한 줄씩, 이웃 칸으로만 옮겨 가며 차이 합이 가장 작은 길(가운데를 조금 더 좋아함)
    cost = cv2.GaussianBlur(diff, (0, 0), 2) + 0.15 * np.abs(np.arange(w, dtype=np.float32) - w / 2)[None, :]
    acc = cost.copy()
    back = np.zeros((h, w), np.int32)
    for y in range(1, h):
        prev = acc[y - 1]
        cand = np.stack([np.r_[np.inf, prev[:-1]], prev, np.r_[prev[1:], np.inf]])
        k = np.argmin(cand, axis=0)
        back[y] = k - 1
        acc[y] += cand[k, np.arange(w)]
    path = np.zeros(h, np.int32)
    path[-1] = int(np.argmin(acc[-1]))
    for y in range(h - 1, 0, -1):
        path[y - 1] = path[y] + back[y, path[y]]
    x = np.arange(w, dtype=np.float32)[None, :] + 0.5
    t_cut = np.clip((x - (path[:, None] + 0.5) + CUT_FEATHER) / (2 * CUT_FEATHER), 0, 1)
    t_cut = t_cut * t_cut * (3 - 2 * t_cut)
    # 얼마나 다른가(0 = 비슷함 → 천천히 섞기, 1 = 다름 → 자르기). 갑자기 바뀌지 않게 넓게 흐린다
    k = np.clip((cv2.GaussianBlur(diff, (0, 0), 10) - DIFF_LOW) / (DIFF_HIGH - DIFF_LOW), 0, 1)
    k = cv2.GaussianBlur(k, (0, 0), 6)
    return (tt * (1 - k) + t_cut * k).astype(np.float32)


def join_h(a: np.ndarray, b: np.ndarray, keep_b: tuple = ()) -> np.ndarray:
    h, w, _ = a.shape
    out = np.zeros((h, 2 * w - OVERLAP, 3), np.float32)
    out[:, :w - OVERLAP] = a[:, :w - OVERLAP]
    out[:, w:] = b[:, OVERLAP:]
    out[:, w - OVERLAP:w] = morph_band(a[:, w - OVERLAP:], b[:, :OVERLAP], keep_b)
    return out


def join_v(a: np.ndarray, b: np.ndarray, keep_b: tuple = ()) -> np.ndarray:
    t = join_h(np.transpose(a, (1, 0, 2)).copy(), np.transpose(b, (1, 0, 2)).copy(), keep_b)
    return np.transpose(t, (1, 0, 2))


def stitch(place: str, variant: str = "day") -> Image.Image:
    top = join_h(load(place, "TL", variant), load(place, "TR", variant))
    bottom = join_h(load(place, "BL", variant), load(place, "BR", variant))
    full = join_v(top, bottom, KEEP_B.get((place, "v"), ()))
    im = Image.fromarray(np.clip(full + 0.5, 0, 255).astype(np.uint8))
    assert im.size == (2944, 1920), im.size
    return im.crop((CROP_X, 0, im.width - CROP_X, im.height))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="이어 붙인 PNG와 미리보기를 저장할 폴더")
    args = ap.parse_args()
    for place, name in MAPS.items():
        for variant, suffix in (("day", ""), ("evening", "_evening")):
            im = stitch(place, variant)
            assert im.size == (2880, 1920), im.size
            dst = os.path.join(RES, name + suffix + ".webp")
            im.save(dst, "WEBP", quality=QUALITY, method=6)
            shutil.copyfile(dst, os.path.join(WEB_ASSETS, name + suffix + ".webp"))
            if args.preview:
                os.makedirs(args.preview, exist_ok=True)
                im.save(os.path.join(args.preview, f"{place}{suffix}-2880x1920.png"))
                im.resize((im.width // 3, im.height // 3), Image.LANCZOS).save(os.path.join(args.preview, f"{place}{suffix}-small.jpg"), quality=82)
            print(f"{place} {variant}: {im.size[0]}×{im.size[1]} → {os.path.relpath(dst, ROOT)} ({os.path.getsize(dst) // 1024}KB)")


if __name__ == "__main__":
    main()
