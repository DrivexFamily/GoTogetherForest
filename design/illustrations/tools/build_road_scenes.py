#!/usr/bin/env python3
"""
같이가숲 — 진행 화면 풍경 길 그림(GPT R20, 테마 여럿 × 넓은·정사각·세로 × 낮·저녁)을 한꺼번에 검수하고 앱에 넣는 도구
(Claude Code 담당, 결정 #33).

1. 검수(기본): 낮 그림마다 길 가운데 선을 따고(trace_road.py), 아래를 확인해 확인 그림과 요약을 만든다.
   - 길 덩어리 수(1이 정상), 길 폭이 고른지, 실제 화면 크기에서 길이 잘리지 않는지(앱의 roadFits와 같은 계산)
   - 저녁 그림: 낮 길 선의 점이 저녁 그림의 길 위에 몇 %나 있는지(같은 구도인지)
   - 한눈에 보는 모음 그림(테마 × 모양, 낮 그림 위에 딴 선)
2. --apply: 통과한 그림을 앱에 넣는다.
   - core/ui/src/main/res/drawable-nodpi/ggf_pic_roadscene_<테마키>_<모양>[_evening].webp (늘리기·색 보정 없음, WebP 90)
   - core/ui/src/main/kotlin/app/familyjourney/core/ui/RoadSceneData.kt (만든 파일: 테마·그림 크기·길 선·길 폭)

목록(JSON): [{"theme": "숲길", "key": "forest", "shape": "wide|square|tall", "day": "경로.png", "evening": "경로.png 또는 null",
           "color": "beige|gray"(길 색, 바닷길·사막길은 gray),
           "hints": [[출발 x, y], [도착 x, y]](선택: 길과 같은 색 보도·광장이 붙은 그림에서 출발·도착 근처 점)}, ...]
목록 순서가 앱의 테마 순서다(첫 테마는 데모에서 씀).

실행(저장소 루트에서, trace_road.py와 같은 파이썬):
  <venv>/bin/python design/illustrations/tools/build_road_scenes.py 목록.json --out <검수 폴더> [--apply]
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import trace_road as tr  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
RES = os.path.join(ROOT, "core/ui/src/main/res/drawable-nodpi")
KOTLIN = os.path.join(ROOT, "core/ui/src/main/kotlin/app/familyjourney/core/ui/RoadSceneData.kt")
QUALITY = 90

# 진행 화면에서 길 그림이 놓이는 실제 자리(px, 클라우드 화면 캡처 기준). 모양마다 이 자리들에서 길이 잘리지 않아야 한다
SCREENS = {
    "wide": [(1618, 900), (1500, 760)],
    "square": [(777, 900), (900, 900)],
    "tall": [(560, 900), (720, 880)],
}
START = {"wide": "left", "square": "top-left", "tall": "top-left"}
# 길 가운데 선이 화면 가장자리에서 (길 폭 절반 × 이 값)만큼은 안쪽이어야 한다. 굽이 바깥 가장자리가 몇 px 잘리는 것은 괜찮다(앱 RoadSceneSpec와 같음)
EDGE_KEEP = 0.66


def crop_offset(points: np.ndarray, pw: int, ph: int, half_w: float, w: float, h: float):
    """앱 RoadSceneSpec.cover와 같은 계산: 비율 지켜 가득 채우는 배율 k, 그리고 길(양옆 길 폭 절반 + 여유)이 다 보이도록
    가운데에서 가장 덜 옮긴 잘라 보기 자리. 길이 보이는 범위보다 크면 가운데 그대로(그때는 잘린다). (left, top, k)"""
    k = max(w / pw, h / ph)
    vw, vh = w / k, h / k
    pad = half_w * EDGE_KEEP + 2  # 검사 여유보다 2px 더(경계에 딱 붙어 반올림으로 갈리지 않게)
    def axis(lo, hi, view, full):
        # 잘라야 하면 길 범위의 가운데를 보이는 자리 가운데에(앱 RoadSceneSpec.cover와 같음)
        c = (lo + hi) / 2 - view / 2 if hi - lo <= view else (full - view) / 2
        return min(max(c, 0.0), full - view)
    cx = axis(points[:, 0].min() - pad, points[:, 0].max() + pad, vw, pw)
    cy = axis(points[:, 1].min() - pad, points[:, 1].max() + pad, vh, ph)
    return -cx * k, -cy * k, k


def fits(points: np.ndarray, pw: int, ph: int, half_w: float, w: float, h: float) -> bool:
    """앱 roadFits와 같은 계산: 비율 지켜 가득 채우고(길이 보이게 옮긴 자리) 길(양옆 길 폭 절반 포함)이 자리 안에 있나"""
    left, top, k = crop_offset(points, pw, ph, half_w, w, h)
    x = left + points[:, 0] * k
    y = top + points[:, 1] * k
    m = half_w * EDGE_KEEP * k
    return bool((x >= m).all() and (x <= w - m).all() and (y >= m).all() and (y <= h - m).all())


def rdp(pts: np.ndarray, eps: float) -> np.ndarray:
    """Ramer–Douglas–Peucker: 모양을 eps px 안으로 지키며 점을 줄인다"""
    if len(pts) < 3:
        return pts
    a, b = pts[0], pts[-1]
    ab = b - a
    n = np.hypot(*ab) or 1.0
    d = np.abs(ab[0] * (pts[:, 1] - a[1]) - ab[1] * (pts[:, 0] - a[0])) / n
    i = int(np.argmax(d))
    if d[i] <= eps:
        return np.stack([a, b])
    return np.concatenate([rdp(pts[: i + 1], eps)[:-1], rdp(pts[i:], eps)])


def on_road(line: np.ndarray, width: float, picture: str) -> float:
    """저녁 그림처럼 밝기가 다른 그림에서도 같은 자리에 길이 있는지: 선 위 점마다 길 가운데 색과 길 양옆(폭의 0.9배 바깥) 색이
    둘 다 뚜렷이 다르면(RGB 거리 15 넘게) 길 위로 본다. 그런 점의 비율을 돌려준다(같은 구도면 0.85 이상)"""
    a = np.asarray(Image.open(picture).convert("RGB")).astype(np.float32)
    a = cv2.GaussianBlur(a, (0, 0), 2)
    h, w, _ = a.shape
    pts = line[::3]
    d = np.gradient(line, axis=0)[::3]
    nrm = np.stack([-d[:, 1], d[:, 0]], 1)
    nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-6)
    def at(q):
        x = np.clip(q[:, 0].round().astype(int), 0, w - 1)
        y = np.clip(q[:, 1].round().astype(int), 0, h - 1)
        return a[y, x]
    c = at(pts)
    s1 = at(pts + nrm * width * 0.9)
    s2 = at(pts - nrm * width * 0.9)
    ok = (np.linalg.norm(c - s1, axis=1) > 15) & (np.linalg.norm(c - s2, axis=1) > 15)
    return float(ok.mean())


def check(entry: dict, out: str) -> dict:
    im, keep, line, width, parts = tr.trace(entry["day"], START[entry["shape"]], entry.get("color", "beige"), entry.get("hints"))
    pw, ph = im.size
    dist = cv2.distanceTransform(keep, cv2.DIST_L2, 5)
    xs = np.clip(line[:, 0].round().astype(int), 0, pw - 1)
    ys = np.clip(line[:, 1].round().astype(int), 0, ph - 1)
    widths = dist[ys, xs] * 2
    spread = float(np.std(widths) / max(1.0, np.median(widths)))
    screens = {f"{w}x{h}": fits(line, pw, ph, width / 2, w, h) for w, h in SCREENS[entry["shape"]]}
    evening = None
    if entry.get("evening"):
        ev = Image.open(entry["evening"])
        # 크기가 몇 px만 다르면(GPT 저녁 편집에서 1~2px 달라짐) 선을 그 비율로 옮겨 비교한다. 앱은 저녁 그림을 낮 크기로 그린다
        same = abs(ev.size[0] - pw) <= 3 and abs(ev.size[1] - ph) <= 3
        ev_line = line * np.array([ev.size[0] / pw, ev.size[1] / ph], np.float32)
        evening = {"size_same": same, "size": list(ev.size), "on_road": round(on_road(ev_line, width, entry["evening"]), 3) if same else 0.0,
                   "day_on_road": round(on_road(line, width, entry["day"]), 3)}
    issues, notes = [], []
    if parts != 1:
        notes.append(f"길 조각 {parts}개를 이음")
    if spread > 0.3:
        notes.append(f"길 폭 고르지 않음({spread:.2f})")
    if width * (1024 / min(pw, ph)) < 40:
        issues.append(f"길이 너무 가늘게 잡힘({width:.0f}px): 길 색 기준 확인")
    cut = [k for k, ok in screens.items() if not ok]
    if cut and len(cut) == len(screens):
        issues.append("모든 대표 화면에서 길이 잘림: " + ", ".join(cut))
    elif cut:
        notes.append(", ".join(cut) + " 화면에서는 잘려 다른 모양 그림을 씀")
    if evening and not evening["size_same"]:
        issues.append(f"저녁 그림 크기가 낮과 3px 넘게 다름({evening['size']})")
    elif evening and evening["on_road"] < min(0.85, evening["day_on_road"] - 0.05):
        issues.append(f"저녁 그림의 길 자리가 낮과 다름(길 위 {evening['on_road']:.0%}, 낮 {evening['day_on_road']:.0%})")
    name = f"{entry['key']}-{entry['shape']}"
    tr.check_picture(im, line, width).save(os.path.join(out, name + "-day.png"))
    if entry.get("evening") and evening and evening["size_same"]:
        tr.check_picture(Image.open(entry["evening"]), ev_line, width).save(os.path.join(out, name + "-evening.png"))
    return {
        "theme": entry["theme"], "key": entry["key"], "shape": entry["shape"], "size": [pw, ph], "parts": parts,
        "roadWidth": round(width, 1), "widthSpread": round(spread, 3), "screens": screens, "evening": evening,
        "issues": issues, "notes": notes, "line": line.round(1).tolist(),
    }


def sheet(results: list[dict], out: str, variant: str) -> None:
    shapes = ["wide", "square", "tall"]
    themes = list(dict.fromkeys(r["key"] for r in results))
    cell_h = 260
    widths = {"wide": 420, "square": 260, "tall": 174}
    W = sum(widths.values()) + 40
    canvas = Image.new("RGB", (W + 160, len(themes) * (cell_h + 26) + 10), "white")
    d = ImageDraw.Draw(canvas)
    for r_i, key in enumerate(themes):
        y = 10 + r_i * (cell_h + 26)
        theme = next(r["theme"] for r in results if r["key"] == key)
        d.text((8, y + cell_h // 2), key, fill=(0, 0, 0))
        x = 160
        for s in shapes:
            r = next((r for r in results if r["key"] == key and r["shape"] == s), None)
            p = os.path.join(out, f"{key}-{s}-{variant}.png")
            if r and os.path.exists(p):
                t = Image.open(p).convert("RGB")
                t.thumbnail((widths[s], cell_h))
                canvas.paste(t, (x, y))
                mark = "OK" if not r["issues"] else "CHECK: " + "; ".join(r["issues"])[:60]
                d.text((x, y + cell_h + 4), mark, fill=(0, 120, 0) if not r["issues"] else (200, 0, 0))
            x += widths[s] + 10
        _ = theme
    canvas.save(os.path.join(out, f"sheet-{variant}.jpg"), quality=85)


# 진행 화면의 대표 자리(넓은 화면, 반 화면, 좁은 창, 세로 화면). 테마마다 어느 그림이 쓰이는지(앱 pickScene과 같은 계산) 표로 낸다
APP_SCREENS = {"넓은 화면 1618x900": (1618, 900), "반 화면 777x900": (777, 900), "좁은 창 560x900": (560, 900), "세로 화면 720x880": (720, 880)}
MAX_ASPECT_GAP = 1.6


def coverage(results: list[dict]) -> dict:
    table = {}
    for key in dict.fromkeys(r["key"] for r in results):
        row = {}
        for label, (w, h) in APP_SCREENS.items():
            best, best_gap = None, None
            for r in results:
                if r["key"] != key or r["issues"]:
                    continue
                pw, ph = r["size"]
                a = (pw / ph) / (w / h)
                if max(a, 1 / a) > MAX_ASPECT_GAP:
                    continue
                if not fits(np.array(r["line"], np.float32), pw, ph, r["roadWidth"] / 2, w, h):
                    continue
                gap = abs(np.log(a))
                if best is None or gap < best_gap:
                    best, best_gap = r["shape"], gap
            row[label] = best or "앱이 그린 길"
        table[key] = row
    return table


def write_kotlin(results: list[dict]) -> None:
    lines = [
        "package app.familyjourney.core.ui",
        "",
        "// 만든 파일: design/illustrations/tools/build_road_scenes.py --apply (손으로 고치지 않는다).",
        "// 진행 화면 풍경 길 그림(GPT R20, 결정 #33): 테마 · 그림 크기 · 길 가운데 선(그림 px, 출발 → 도착, 1px 안으로 줄인 점) · 길 폭.",
        "",
        "internal val roadSceneData: List<RoadScene> = listOf(",
    ]
    for r in results:
        pts = rdp(np.array(r["line"], np.float32), 1.0)
        flat = ", ".join(f"{v:.1f}f" for xy in pts for v in xy)
        base = f"ggf_pic_roadscene_{r['key']}_{r['shape']}"
        ev = f"R.drawable.{base}_evening" if r.get("evening") else "null"
        lines.append(
            f'    RoadScene("{r["theme"]}", RoadSceneSpec({r["size"][0]}, {r["size"][1]}, floatArrayOf({flat}), {r["roadWidth"]}f), '
            f"R.drawable.{base}, {ev}),"
        )
    lines += [")", ""]
    open(KOTLIN, "w", encoding="utf-8").write("\n".join(lines))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("manifest")
    ap.add_argument("--out", required=True)
    ap.add_argument("--apply", action="store_true", help="문제없는 그림을 앱에 넣고 RoadSceneData.kt를 만든다")
    a = ap.parse_args()
    entries = json.load(open(a.manifest, encoding="utf-8"))
    os.makedirs(a.out, exist_ok=True)
    results = []
    for e in entries:
        r = check(e, a.out)
        r["dayPath"], r["eveningPath"] = e["day"], e.get("evening")
        results.append(r)
        print(f"{e['key']:>10} {e['shape']:<6} {r['size']} 폭 {r['roadWidth']:.0f} 화면 {r['screens']} 저녁 {r['evening']} → {'OK' if not r['issues'] else r['issues']}")
    sheet(results, a.out, "day")
    sheet(results, a.out, "evening")
    json.dump([{k: v for k, v in r.items() if k != "line"} for r in results], open(os.path.join(a.out, "summary.json"), "w"), ensure_ascii=False, indent=1)
    cov = coverage(results)
    json.dump(cov, open(os.path.join(a.out, "coverage.json"), "w"), ensure_ascii=False, indent=1)
    print("\n테마별로 쓰이는 그림:")
    for key, row in cov.items():
        print(f"  {key:>10}: " + " | ".join(f"{k.split()[0]} {v}" for k, v in row.items()))
    if a.apply:
        good = [r for r in results if not r["issues"]]
        for r in good:
            base = f"ggf_pic_roadscene_{r['key']}_{r['shape']}"
            Image.open(r["dayPath"]).convert("RGB").save(os.path.join(RES, base + ".webp"), "WEBP", quality=QUALITY, method=6)
            if r.get("evening"):
                Image.open(r["eveningPath"]).convert("RGB").save(os.path.join(RES, base + "_evening.webp"), "WEBP", quality=QUALITY, method=6)
        write_kotlin(good)
        print(f"앱에 넣음: {len(good)}/{len(results)}장(낮 기준), {os.path.relpath(KOTLIN, ROOT)}")


if __name__ == "__main__":
    main()
