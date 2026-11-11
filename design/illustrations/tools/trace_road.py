#!/usr/bin/env python3
"""
같이가숲 — 진행 화면 풍경 그림(GPT R20)에서 길의 가운데 선을 따는 도구(Claude Code 담당, 결정 #33).

그림에는 연한 베이지 길이 한 줄 그려져 있고, 앱은 그 위에 차·지나온 길·새싹을 얹는다. 앱이 차를 길 위에 두려면
출발에서 도착까지 길 가운데 선의 점 목록이 필요하다. 이 도구가 그 점 목록과 길 폭을 만든다.

방법:
  1. 길 색 고르기: 밝고(명도 높음) 채도가 낮은 베이지(빨강 ≥ 초록 ≥ 파랑). 기준은 아래 상수.
  2. 작은 틈 메우기(닫기)와 잡티 지우기(열기). 다리 난간 등으로 길이 끊기면 덩어리가 여럿 생긴다.
  3. 덩어리마다 뼈대(skeletonize)를 만들고, 뼈대에서 가장 긴 길(두 끝 사이)만 남긴다(가장자리 잔가지는 버림).
  4. 덩어리들을 출발 쪽부터 가까운 끝끼리 이어 한 줄로 만든다.
  5. 부드럽게(이동 평균) 다듬고 길이 기준으로 고르게 다시 찍는다. 길 폭은 거리 변환(가운데에서 가장자리까지)의 중앙값 × 2.

실행(저장소 루트에서, numpy·opencv-python-headless·Pillow·scikit-image가 있는 파이썬):
  <venv>/bin/python design/illustrations/tools/trace_road.py <그림.png> --start left|top-left [--out 폴더]
결과: <이름>.road.json(그림 크기·길 폭·점 목록), <이름>.road-check.png(그림 위에 딴 선과 1%·10% 자리 표시)
"""
from __future__ import annotations

import argparse
import json
import os
from collections import deque

import cv2
import numpy as np
from PIL import Image
from skimage.morphology import skeletonize

# 길 색: 명도(0~255) 하한, 채도(0~1) 상한, 빨강-파랑 차 범위(베이지는 빨강이 조금 더 큼)
ROAD_VALUE_MIN = 165
ROAD_SAT_MAX = 0.38
ROAD_RB_MIN, ROAD_RB_MAX = 4, 95
MIN_PART = 0.04  # 가장 큰 덩어리의 이 비율보다 작은 덩어리는 길이 아님
STEP = 6.0  # 다시 찍는 간격(px)
SMOOTH = 9  # 이동 평균 창(점 수)


def road_mask(rgb: np.ndarray, color: str = "beige") -> np.ndarray:
    """color: "beige"(연한 베이지 포장길) 또는 "gray"(회색 돌 포장길: 바닷길·사막길, R20 덧붙임)"""
    a = rgb.astype(np.float32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    sat = (mx - mn) / np.maximum(mx, 1)
    rb = r - b
    if color == "gray":
        m = (mx >= 150) & (mx <= 236) & (sat <= 0.10) & (np.abs(rb) <= 18)
    else:
        m = (mx >= ROAD_VALUE_MIN) & (sat <= ROAD_SAT_MAX) & (rb >= ROAD_RB_MIN) & (rb <= ROAD_RB_MAX) & (r >= g - 6) & (g >= b - 6)
    m = m.astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (15, 15)))
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
    return m


def _neighbors(y: int, x: int, sk: np.ndarray):
    h, w = sk.shape
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if dy == 0 and dx == 0:
                continue
            yy, xx = y + dy, x + dx
            if 0 <= yy < h and 0 <= xx < w and sk[yy, xx]:
                yield yy, xx


def _farthest(sk: np.ndarray, start: tuple[int, int]):
    prev = {start: None}
    q = deque([start])
    last = start
    while q:
        p = q.popleft()
        last = p
        for n in _neighbors(p[0], p[1], sk):
            if n not in prev:
                prev[n] = p
                q.append(n)
    return last, prev


def longest_path(sk: np.ndarray) -> np.ndarray:
    """뼈대에서 가장 긴 길(나무 지름): 아무 점에서 가장 먼 끝 → 그 끝에서 가장 먼 끝"""
    ys, xs = np.nonzero(sk)
    a, _ = _farthest(sk, (int(ys[0]), int(xs[0])))
    b, prev = _farthest(sk, a)
    path = []
    p = b
    while p is not None:
        path.append((p[1], p[0]))
        p = prev[p]
    return np.array(path[::-1], np.float32)


def resample(path: np.ndarray, step: float) -> np.ndarray:
    k = SMOOTH
    if len(path) > k:
        pad = np.pad(path, ((k // 2, k // 2), (0, 0)), mode="edge")
        ker = np.ones(k) / k
        path = np.stack([np.convolve(pad[:, 0], ker, "valid"), np.convolve(pad[:, 1], ker, "valid")], 1)
    d = np.r_[0, np.cumsum(np.linalg.norm(np.diff(path, axis=0), axis=1))]
    n = max(2, int(d[-1] // step) + 1)
    t = np.linspace(0, d[-1], n)
    return np.stack([np.interp(t, d, path[:, 0]), np.interp(t, d, path[:, 1])], 1)


def _ends(p: np.ndarray) -> np.ndarray:
    return np.stack([p[0], p[-1]])


def route(mask: np.ndarray, a: tuple[float, float], b: tuple[float, float]) -> np.ndarray:
    """길 덩어리 안에서 a → b로 가는 가장 짧은 길(길 가운데를 따르게 가장자리일수록 비싸게). 보도·광장처럼
    길과 같은 색이 붙어 있는 그림에서 출발·도착 근처 점을 주고 길 선을 딸 때 쓴다"""
    from skimage.graph import route_through_array
    dist = cv2.distanceTransform(mask.astype(np.uint8), cv2.DIST_L2, 5)
    cost = np.where(mask > 0, 1.0 / (1.0 + dist) ** 2, 1e6)
    ys, xs = np.nonzero(mask)
    def snap(p):
        i = int(np.argmin((xs - p[0]) ** 2 + (ys - p[1]) ** 2))
        return int(ys[i]), int(xs[i])
    path, _ = route_through_array(cost, snap(a), snap(b), fully_connected=True, geometric=True)
    return np.array([(x, y) for y, x in path], np.float32)


def trace(path_png: str, start: str, color: str = "beige", hints: list | None = None):
    """가장 큰 길 덩어리의 뼈대에서 시작해, 지금 길의 양 끝 가까이(길 폭의 3배 안)에 끝이 있는 덩어리만 이어 붙인다.
    멀리 떨어진 같은 색 덩어리(흰 건물·구름·돌)는 버린다."""
    im = Image.open(path_png).convert("RGB")
    rgb = np.asarray(im)
    m = road_mask(rgb, color)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(m, connectivity=8)
    if n < 2:
        raise SystemExit("길 색을 찾지 못함: ROAD_* 기준을 확인")
    order = sorted(range(1, n), key=lambda i: -stats[i, cv2.CC_STAT_AREA])
    biggest = stats[order[0], cv2.CC_STAT_AREA]
    main = lab == order[0]
    dist0 = cv2.distanceTransform(main.astype(np.uint8), cv2.DIST_L2, 5)
    chain_pts = longest_path(skeletonize(main))
    width0 = float(np.median(dist0[chain_pts[:, 1].astype(int), chain_pts[:, 0].astype(int)]) * 2)
    keep = main.astype(np.uint8)
    cands = []
    for i in order[1:]:
        if stats[i, cv2.CC_STAT_AREA] < MIN_PART * biggest:
            continue
        comp = lab == i
        sk = skeletonize(comp)
        if sk.sum() < 10:
            continue
        cands.append((i, comp, longest_path(sk)))
    parts = 1
    gap = max(40.0, width0 * 3)
    grown = True
    while grown and cands:
        grown = False
        for k, (i, comp, p) in enumerate(cands):
            e = _ends(p)
            dh = np.linalg.norm(e - chain_pts[0], axis=1)
            dt = np.linalg.norm(e - chain_pts[-1], axis=1)
            if dt.min() <= gap and dt.min() <= dh.min():
                p = p if dt[0] <= dt[1] else p[::-1]
                chain_pts = np.concatenate([chain_pts, p])
            elif dh.min() <= gap:
                p = p if dh[1] <= dh[0] else p[::-1]
                chain_pts = np.concatenate([p, chain_pts])
            else:
                continue
            keep |= comp.astype(np.uint8)
            parts += 1
            cands.pop(k)
            grown = True
            break
    if hints:
        # 출발·도착 근처 점을 받았으면 그 사이 가장 짧은 길(길 가운데)을 길 선으로 쓴다
        full = (m > 0)
        chain_pts = route(full, hints[0], hints[1])
        keep = full.astype(np.uint8)
    # 출발 쪽으로 방향 맞추기
    w, h = im.size
    anchor = np.array([0.0, h * 0.5]) if start == "left" else np.array([0.0, 0.0])
    if np.linalg.norm(chain_pts[-1] - anchor) < np.linalg.norm(chain_pts[0] - anchor):
        chain_pts = chain_pts[::-1]
    line = resample(chain_pts, STEP)
    dist = cv2.distanceTransform(keep, cv2.DIST_L2, 5)
    xs = np.clip(line[:, 0].round().astype(int), 0, im.width - 1)
    ys = np.clip(line[:, 1].round().astype(int), 0, im.height - 1)
    local = dist[ys, xs] * 2
    width = float(np.median(local))
    # 집으로 가는 좁은 오솔길이 길 끝에 붙어 있으면 뼈대가 그쪽으로 이어진다: 양 끝에서 폭이 길 폭의 70%보다 좁은 동안 잘라 낸다
    narrow = local < width * 0.7
    run = 5
    a0 = 0
    while a0 < len(line) - run and narrow[a0:a0 + run].any():
        a0 += 1
    b0 = len(line)
    while b0 > a0 + run and narrow[b0 - run:b0].any():
        b0 -= 1
    if b0 - a0 > len(line) * 0.5:
        line = line[a0:b0]
    # 길 끝(둥근 끝)에서는 뼈대가 가장자리 쪽으로 휘므로 양 끝에서 길 폭 절반만큼 잘라 낸다
    cut = max(1, int(width * 0.5 / STEP))
    if len(line) > 4 * cut:
        line = line[cut:-cut]
    return im, keep, line, width, parts


def check_picture(im: Image.Image, line: np.ndarray, width: float) -> Image.Image:
    from PIL import ImageDraw
    out = im.convert("RGB").copy()
    d = ImageDraw.Draw(out)
    pts = [tuple(p) for p in line]
    d.line(pts, fill=(230, 30, 30), width=3)
    seg = np.r_[0, np.cumsum(np.linalg.norm(np.diff(line, axis=0), axis=1))]
    for pc in range(0, 101):
        x = np.interp(seg[-1] * pc / 100, seg, line[:, 0])
        y = np.interp(seg[-1] * pc / 100, seg, line[:, 1])
        r = 7 if pc % 10 == 0 else 2
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255) if pc % 10 == 0 else (30, 30, 230))
    s, e = line[0], line[-1]
    d.ellipse([s[0] - 12, s[1] - 12, s[0] + 12, s[1] + 12], outline=(0, 160, 0), width=4)
    d.ellipse([e[0] - 12, e[1] - 12, e[0] + 12, e[1] + 12], outline=(220, 0, 0), width=4)
    d.text((10, 10), f"road width ~{width:.0f}px, points {len(line)}", fill=(0, 0, 0))
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("picture")
    ap.add_argument("--start", choices=["left", "top-left"], required=True, help="출발 끝이 있는 쪽")
    ap.add_argument("--out", default=".")
    ap.add_argument("--color", choices=["beige", "gray"], default="beige")
    a = ap.parse_args()
    im, _, line, width, parts = trace(a.picture, a.start, a.color)
    os.makedirs(a.out, exist_ok=True)
    base = os.path.join(a.out, os.path.splitext(os.path.basename(a.picture))[0])
    json.dump(
        {"picture": os.path.basename(a.picture), "width": im.width, "height": im.height, "roadWidth": round(width, 1),
         "points": [[round(float(x), 1), round(float(y), 1)] for x, y in line]},
        open(base + ".road.json", "w"), ensure_ascii=False,
    )
    check_picture(im, line, width).save(base + ".road-check.png")
    print(f"{a.picture}: 덩어리 {parts}, 점 {len(line)}, 길 폭 약 {width:.0f}px, 출발 {line[0].round()}, 도착 {line[-1].round()}")


if __name__ == "__main__":
    main()
