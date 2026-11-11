#!/usr/bin/env python3
"""
같이가숲 — 지역별 랜드마크 도감(결정 #40) 앱 자료 만들기(Claude Code).

1. core/atlas/.../LandmarkData.kt : 500곳 ID·이름·시도·시군구(GPT U03 분류) + 도착 판정 좌표(docs/landmarks/landmark-coordinates.json)
2. --images : 랜드마크 그림(GPT U02 활성 원본, main 브랜치)을 앱용 WebP로(늘리기 금지·색 그대로·알파 16 미만 지움·긴 변 384px)
   → feature/atlas/src/main/res/drawable-nodpi/ggf_lm_<id>.webp, LandmarkArtData.kt

원본은 작업 브랜치에 합치지 않고 git object(origin/main)에서 바로 읽는다(결정 #39: 작업 브랜치는 main을 통째로 합치지 않음).
실행(저장소 루트): python3 design/illustrations/tools/build_landmark_assets.py [--images] [--ref origin/main]
"""
from __future__ import annotations
import argparse, csv, io, json, os, re, subprocess

# 시·군·구가 비어 있는 곳(여러 구·시에 걸친 하천·다리·산, 예: 청계천·인천대교·무등산·한라산). 빈 이름 카드가 생기지 않게
# (2026-11-08 Mac 확인 2-5)
MULTI_SUBREGION = "여러 동네"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
EXP = "design/illustrations/exports/2026-11-06"
COORDS = os.path.join(ROOT, "docs/landmarks/landmark-coordinates.json")
KT_DATA = os.path.join(ROOT, "core/atlas/src/main/kotlin/app/familyjourney/core/atlas/LandmarkData.kt")
RES = os.path.join(ROOT, "feature/atlas/src/main/res/drawable-nodpi")
KT_ART = os.path.join(ROOT, "feature/atlas/src/main/kotlin/app/familyjourney/feature/atlas/LandmarkArtData.kt")
REGION = {"서울": "SEOUL", "부산": "BUSAN", "대구": "DAEGU", "인천": "INCHEON", "광주": "GWANGJU", "대전": "DAEJEON", "울산": "ULSAN",
          "세종": "SEJONG", "경기": "GYEONGGI", "강원": "GANGWON", "충북": "CHUNGBUK", "충남": "CHUNGNAM", "전북": "JEONBUK",
          "전남": "JEONNAM", "경북": "GYEONGBUK", "경남": "GYEONGNAM", "제주": "JEJU"}
SIDE = 384


def git_show(ref: str, path: str) -> bytes:
    return subprocess.run(["git", "-C", ROOT, "show", f"{ref}:{path}"], check=True, capture_output=True).stdout


def radius_for(name: str) -> int:
    """도착으로 인정하는 반경(m): 넓은 산·섬·국립공원·해변은 넓게"""
    if re.search(r"(산|섬|도$|국립공원|해수욕장|해변|해안|오름|봉$|령$|고개|반도)", name):
        return 5000
    if re.search(r"(공원|계곡|호수|호$|저수지|수목원|숲|폭포|습지|마을|항$|포구|댐)", name):
        return 3000
    return 1500


def load(ref: str):
    cls = json.loads(git_show(ref, f"{EXP}/U03-subregion-classification.json"))
    rows = {r["id"]: r for r in csv.DictReader(io.StringIO(git_show(ref, f"{EXP}/U02-MODELS-completed-landmarks.csv").decode("utf-8-sig")))}
    coords = {}
    if os.path.exists(COORDS):
        coords = {c["id"]: c for c in json.load(open(COORDS, encoding="utf-8"))["landmarks"]}
    out = []
    for it in cls["items"]:
        r = rows.get(it["id"])
        if not r:
            continue
        c = coords.get(it["id"])
        out.append(dict(id=it["id"], name=r["name_ko"], region=REGION[it["region"]], sub=it.get("subregion") or MULTI_SUBREGION, file=r["file"],
                        lat=c.get("lat") if c else None, lon=c.get("lon") if c else None))
    return out


def kstr(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def write_data(items) -> None:
    lines = ["package app.familyjourney.core.atlas", "",
             "// 만든 파일: design/illustrations/tools/build_landmark_assets.py가 덮어쓴다. 손으로 고치지 않는다.",
             "// 이름·시도·시군구: GPT U02·U03(표시용 분류). 좌표: docs/landmarks/landmark-coordinates.json(출처·라이선스 기록). 좌표 없음 = 도착으로 얻을 수 없음", "",
             "internal val landmarkData: List<Landmark> = listOf("]
    for x in items:
        pt = f"GeoPoint({x['lat']}, {x['lon']})" if x["lat"] is not None else "null"
        lines.append(f"    Landmark({kstr(x['id'])}, {kstr(x['name'])}, Region.{x['region']}, {kstr(x['sub'])}, {pt}, {radius_for(x['name'])}),")
    lines += [")", ""]
    open(KT_DATA, "w", encoding="utf-8").write("\n".join(lines))
    print(f"LandmarkData.kt {len(items)}곳, 좌표 {sum(1 for x in items if x['lat'] is not None)}곳")


def write_images(items, ref: str) -> None:
    from PIL import Image
    os.makedirs(RES, exist_ok=True)
    names = []
    total = 0
    for x in items:
        im = Image.open(io.BytesIO(git_show(ref, f"{EXP}/{x['file']}"))).convert("RGBA")
        r, g, b, a = im.split()
        a = a.point(lambda v: 0 if v < 16 else (255 if v >= 248 else v))
        im = Image.merge("RGBA", (r, g, b, a))
        box = a.getbbox()
        if box:
            im = im.crop(box)
        if max(im.size) > SIDE:  # 줄이기만
            im.thumbnail((SIDE, SIDE), Image.LANCZOS)
        name = "ggf_lm_" + x["id"].lower()
        path = os.path.join(RES, name + ".webp")
        im.save(path, "WEBP", quality=88, method=6, exact=True)
        total += os.path.getsize(path)
        names.append((x["id"], name))
    lines = ["package app.familyjourney.feature.atlas", "",
             "// 만든 파일: design/illustrations/tools/build_landmark_assets.py --images가 덮어쓴다. 손으로 고치지 않는다.", "",
             "internal val landmarkArtData: Map<String, Int> = mapOf("]
    lines += [f"    {kstr(i)} to R.drawable.{n}," for i, n in names]
    lines += [")", ""]
    open(KT_ART, "w", encoding="utf-8").write("\n".join(lines))
    print(f"그림 {len(names)}장, 합계 {total / 1e6:.1f}MB")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images", action="store_true")
    ap.add_argument("--ref", default="origin/main")
    a = ap.parse_args()
    items = load(a.ref)
    write_data(items)
    if a.images:
        write_images(items, a.ref)


if __name__ == "__main__":
    main()
