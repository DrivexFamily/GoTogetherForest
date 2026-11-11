#!/usr/bin/env python3
"""
같이가숲 — GPT R45 v2 테마 맵(10곳 × 낮·저녁 × 4조각, main design/illustrations/exports/2026-11-07)을
R17과 같은 방법(stitch_large_maps.py의 모핑 섞기)으로 이어 2880×1920 맵을 만든다. 늘리기·색 보정 없음.

조각은 main에서 바로 읽는다(작업 브랜치는 main을 통째로 합치지 않음, 결정 #39): git show origin/main:<경로>.
검수(2026-11-07, CLAUDE-OUTBOX claude-review-20261107-05): 낮·저녁 모두 합격 7곳 = AMUSEMENT FARM CAMPING SKY DINO HANOK SCHOOL.
CITY 낮·HARBOR 저녁·OASIS 저녁은 GPT v3로 고쳐 합격(main cc1d4b4, claude-merge-20261107-07): v3 조각이 있으면 v3를 쓴다.

실행(저장소 루트, numpy·opencv-python-headless·Pillow):
  <venv>/bin/python design/illustrations/tools/stitch_r45_maps.py --out <폴더> [--maps AMUSEMENT,FARM] [--webp]
  --webp: 앱 이름(ggf_pic_map_<소문자>[_evening].webp, WebP 92)으로 core/ui/src/main/res/drawable-nodpi/와 web/forest/assets/에 저장
"""
import argparse, io, os, shutil, subprocess, sys, importlib.util
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("stitch_large_maps", os.path.join(HERE, "stitch_large_maps.py"))
st = importlib.util.module_from_spec(spec); spec.loader.exec_module(st)

SRC = "design/illustrations/exports/2026-11-07"
PASSED = ["AMUSEMENT", "FARM", "CAMPING", "SKY", "DINO", "HANOK", "SCHOOL", "CITY", "HARBOR", "OASIS"]
# v3로 바뀐 조각(v2 원본은 main에 그대로 둠)
V3 = {("CITY", "BL", "day"), ("CITY", "BL", "evening"), ("HARBOR", "BL", "evening"), ("HARBOR", "BR", "evening"),
      ("OASIS", "TL", "evening"), ("OASIS", "TR", "evening"), ("OASIS", "BL", "evening"), ("OASIS", "BR", "evening")}


def load(place, tile, variant="day"):
    ver = "v3" if (place, tile, variant) in V3 else "v2"
    name = f"GGF-MAP-{place}-{tile}{'-EVENING' if variant == 'evening' else ''}-R45-{ver}.png"
    data = subprocess.run(["git", "-C", st.ROOT, "show", f"origin/main:{SRC}/{name}"], check=True, capture_output=True).stdout
    im = Image.open(io.BytesIO(data)).convert("RGB")
    if im.size != (1536, 1024):
        raise SystemExit(f"{name}: 1536×1024가 아님({im.size})")
    return np.asarray(im).astype(np.float32)


st.load = load


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--maps", default=",".join(PASSED))
    ap.add_argument("--webp", action="store_true")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    for place in a.maps.split(","):
        for variant, suffix in (("day", ""), ("evening", "_evening")):
            im = st.stitch(place, variant)
            assert im.size == (2880, 1920), im.size
            im.save(os.path.join(a.out, f"{place}{suffix}.png"))
            if a.webp:
                name = f"ggf_pic_map_{place.lower()}{suffix}.webp"
                dst = os.path.join(st.RES, name)
                im.save(dst, "WEBP", quality=st.QUALITY, method=6)
                shutil.copyfile(dst, os.path.join(st.WEB_ASSETS, name))
            print(place, variant, im.size, flush=True)


if __name__ == "__main__":
    main()
