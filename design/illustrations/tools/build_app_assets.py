#!/usr/bin/env python3
"""
같이가숲 — GPT 일러스트를 앱용 WebP로 바꾸는 스크립트(Claude Code 담당, 결정 #29).

원본은 고치지 않는다. 원본 → (오리기) → 가장자리 정리 → 크기 줄이기 → WebP 저장.
화질·색감 유지 규칙(#29-⑦):
  - 원본보다 크게 늘리지 않는다(줄이기만, LANCZOS).
  - 색을 바꾸지 않는다. 바꾸는 것은 알파(투명도)뿐이다:
      · GPT 투명 PNG는 몸통 알파가 253 근처(거의 불투명)라 어두운 바탕에서 아주 살짝 비친다 → 248 이상은 255로.
      · 10 이하의 희미한 찌꺼기(배경 잔여)는 0으로.
  - WebP 품질 92(눈으로 차이가 보이지 않는 수준), 메타데이터 없음.

실행(저장소 루트에서):
  python3 design/illustrations/tools/build_app_assets.py
  # 여자아이 오리기(rembg)를 다시 하려면 이미지 처리 가상환경에서:
  #   <venv>/bin/python design/illustrations/tools/build_app_assets.py --cutout
결과:
  core/ui/src/main/res/drawable-nodpi/ggf_pic_*.webp
  design/illustrations/derived/2026-11-03/*.png  (Claude Code가 오린 투명 PNG 원본 크기)
파생 그림(나무·동물·섬·핀 등)은 먼저 extract_assets.py로 derived/에 만든 뒤 이 스크립트를 돌린다.
"""
from __future__ import annotations

import argparse
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
EXPORTS = os.path.join(ROOT, "design/illustrations/exports/2026-11-03")
ORIGINALS = os.path.join(ROOT, "design/illustrations/originals/2026-11-03")
DERIVED = os.path.join(ROOT, "design/illustrations/derived/2026-11-03")
RES = os.path.join(ROOT, "core/ui/src/main/res/drawable-nodpi")

QUALITY = 92

# 차 안 가족 4장은 같은 상자로 잘라 차 위치를 똑같이 맞춘다(포즈가 바뀔 때 차가 흔들리지 않게).
CAR_STAGES = [
    ("GGF-CAR-FAMILY-EXCITED-R4-1-v1.png", "ggf_pic_car_start"),
    ("GGF-CAR-FAMILY-CURIOUS-R4-2-v1.png", "ggf_pic_car_lookout"),
    ("GGF-CAR-FAMILY-WAVE-R4-3-v1.png", "ggf_pic_car_wave"),
    ("GGF-CAR-FAMILY-CLAP-R4-4-v1.png", "ggf_pic_car_almost"),
]
CAR_EMPTY = ("GGF-CAR-FAMILY-SUV-R3-v1.png", "ggf_pic_car_empty")
PEOPLE = [
    ("GGF-FAMILY-MOTHER-WAVE-R2-v1.png", "ggf_pic_mother"),
    ("GGF-FAMILY-FATHER-WAVE-R2-v1.png", "ggf_pic_father"),
]
# 여자아이: 원본 ILL-027(01_헤어스타일.png) 왼쪽 캐릭터를 오린 것(파생, Claude Code 제작)
GIRL_SRC = os.path.join(ORIGINALS, "01-character-options/01_헤어스타일.png")
GIRL_BOX = (0.08, 0.15, 0.52, 0.92)  # 가로·세로 비율(왼, 위, 오, 아래)
GIRL_DERIVED = os.path.join(DERIVED, "GGF-DERIVED-CHILD-GIRL-STAND-from-ILL-027-v1.png")

CAR_MAX_W = 960     # 앱 진행 길의 차는 화면에서 최대 약 400dp → 고해상도 화면까지 넉넉히
PERSON_MAX_H = 900  # 서 있는 사람


def clean_alpha(im: Image.Image) -> Image.Image:
    im = im.convert("RGBA")
    r, g, b, a = im.split()
    a = a.point(lambda v: 255 if v >= 248 else (0 if v <= 10 else v))
    return Image.merge("RGBA", (r, g, b, a))


def alpha_box(im: Image.Image, threshold: int = 24):
    a = im.getchannel("A").point(lambda v: 255 if v > threshold else 0)
    return a.getbbox()


def pad_box(box, size, pad):
    l, t, r, b = box
    w, h = size
    return (max(0, l - pad), max(0, t - pad), min(w, r + pad), min(h, b + pad))


def shrink(im: Image.Image, max_w: int | None = None, max_h: int | None = None) -> Image.Image:
    w, h = im.size
    k = 1.0
    if max_w and w > max_w:
        k = min(k, max_w / w)
    if max_h and h > max_h:
        k = min(k, max_h / h)
    if k >= 1.0:
        return im  # 늘리지 않는다
    return im.resize((round(w * k), round(h * k)), Image.LANCZOS)


def save_webp(im: Image.Image, name: str) -> None:
    os.makedirs(RES, exist_ok=True)
    path = os.path.join(RES, name + ".webp")
    im.save(path, "WEBP", quality=QUALITY, method=6, exact=True)
    print(f"{name}.webp  {im.size[0]}x{im.size[1]}  {os.path.getsize(path) // 1024}KB")


def build_cars() -> None:
    ims = [clean_alpha(Image.open(os.path.join(EXPORTS, f))) for f, _ in CAR_STAGES]
    boxes = [alpha_box(im) for im in ims]
    union = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
    union = pad_box(union, ims[0].size, 8)
    for im, (_, name) in zip(ims, CAR_STAGES):
        save_webp(shrink(im.crop(union), max_w=CAR_MAX_W), name)
    empty = clean_alpha(Image.open(os.path.join(EXPORTS, CAR_EMPTY[0])))
    save_webp(shrink(empty.crop(pad_box(alpha_box(empty), empty.size, 8)), max_w=CAR_MAX_W), CAR_EMPTY[1])
    print("차 안 가족 공통 상자:", union)


def build_people() -> None:
    for f, name in PEOPLE:
        im = clean_alpha(Image.open(os.path.join(EXPORTS, f)))
        save_webp(shrink(im.crop(pad_box(alpha_box(im), im.size, 6)), max_h=PERSON_MAX_H), name)


def cutout_girl() -> None:
    from rembg import new_session, remove  # 이미지 처리 가상환경에서만

    src = Image.open(GIRL_SRC).convert("RGB")
    w, h = src.size
    l, t, r, b = GIRL_BOX
    crop = src.crop((int(w * l), int(h * t), int(w * r), int(h * b)))
    out = remove(crop, session=new_session("isnet-anime"))
    out = defringe(out)
    os.makedirs(DERIVED, exist_ok=True)
    out.save(GIRL_DERIVED)
    print("오림:", GIRL_DERIVED, out.size)


def defringe(im: Image.Image) -> Image.Image:
    """
    오린 가장자리의 회색 테두리(헤일로)를 없앤다(2026-11-03 Mac 검증: 다크 모드에서 머리 둘레가 보임).
    - 반투명 가장자리 픽셀의 색을 가장 가까운 안쪽(불투명) 픽셀 색으로 바꾼다 → 배경 회색이 섞이지 않음.
    - 아주 옅은 바깥 가장자리(알파 60 이하)는 투명으로. 안쪽 픽셀 색은 그대로(색 보정 없음).
    """
    import numpy as np
    from scipy import ndimage as ndi

    a = np.asarray(im.convert("RGBA")).copy()
    alpha = a[..., 3].astype(np.float32)
    inside = alpha >= 240
    _, (iy, ix) = ndi.distance_transform_edt(~inside, return_indices=True)
    edge = (alpha > 0) & ~inside
    a[..., :3][edge] = a[..., :3][iy[edge], ix[edge]]
    alpha = np.clip((alpha - 60.0) / (240.0 - 60.0), 0, 1) * 255.0
    a[..., 3] = np.where(inside, 255, alpha).astype(np.uint8)
    return Image.fromarray(a, "RGBA")


def build_girl() -> None:
    if not os.path.exists(GIRL_DERIVED):
        sys.exit("여자아이 오린 그림이 없습니다. --cutout으로 먼저 만드세요.")
    im = clean_alpha(Image.open(GIRL_DERIVED))
    save_webp(shrink(im.crop(pad_box(alpha_box(im), im.size, 6)), max_h=PERSON_MAX_H), "ggf_pic_child_girl")


# Claude Code가 오린 파생 그림(extract_assets.py 결과) → 앱 WebP. (파생 이름, 앱 이름, 최대 가로, 최대 세로)
DERIVED_SPRITES = [
    ("GGF-TREE-1-SPROUT-v1.png", "ggf_pic_tree_1", None, None),
    ("GGF-TREE-2-SEEDLING-v1.png", "ggf_pic_tree_2", None, None),
    ("GGF-TREE-3-SMALL-v1.png", "ggf_pic_tree_3", None, None),
    ("GGF-TREE-4-FULL-v1.png", "ggf_pic_tree_4", None, None),
    ("GGF-ANIMAL-SQUIRREL-NOPAD-v1.png", "ggf_pic_animal_squirrel", None, None),  # 풀 받침 지움(build_nopad)
    ("GGF-ANIMAL-RABBIT-NOPAD-v1.png", "ggf_pic_animal_rabbit", None, None),
    ("GGF-ANIMAL-BIRD-v1.png", "ggf_pic_animal_bird", None, None),
    ("GGF-LOCK-PADLOCK-v1.png", "ggf_pic_padlock", None, None),
    ("GGF-ROAD-SPROUT-v1.png", "ggf_pic_road_sprout", None, None),
    ("GGF-ROAD-PIN-START-v1.png", "ggf_pic_pin_start", None, None),
    ("GGF-ROAD-PIN-GOAL-v1.png", "ggf_pic_pin_goal", None, None),
]
DERIVED_SCENES = [
    # 숲 섬(예전 ILL-002, ggf_pic_forest_island)은 2026-11-04부터 큰 맵(R17·R19)이라 stitch_large_maps.py가 만든다.
    # 여기서 다시 만들면 큰 맵을 예전 작은 그림으로 덮어쓰므로 빼 둔다.
    ("GGF-JOURNEY-BG-v1.png", "ggf_pic_journey_bg", None),  # 흐린 바탕이라도 앱에서 늘어나지 않게 원본 크기 그대로
    ("GGF-ARRIVAL-HOUSE-v1.png", "ggf_pic_arrival_scene", None),
]
# 잠긴 동물 실루엣: 동물 알파 모양에 GPT 잠금 도감(ILL-018)의 갈색을 칠한다. `?`와 자물쇠는 앱이 따로 얹는다.
LOCKED_BROWN = (128, 104, 60)
LOCKED = [
    ("GGF-ANIMAL-SQUIRREL-NOPAD-v1.png", "ggf_pic_locked_squirrel"),
    ("GGF-ANIMAL-RABBIT-NOPAD-v1.png", "ggf_pic_locked_rabbit"),
    ("GGF-ANIMAL-BIRD-v1.png", "ggf_pic_locked_bird"),
]


def build_derived() -> None:
    for f, name, mw, mh in DERIVED_SPRITES:
        im = clean_alpha(Image.open(os.path.join(DERIVED, f)))
        save_webp(shrink(im, max_w=mw, max_h=mh), name)
    for f, name, mw in DERIVED_SCENES:
        im = Image.open(os.path.join(DERIVED, f)).convert("RGB")
        save_webp(shrink(im, max_w=mw), name)
    for f, name in LOCKED:
        a = clean_alpha(Image.open(os.path.join(DERIVED, f))).getchannel("A")
        sil = Image.new("RGBA", a.size, LOCKED_BROWN + (255,))
        sil.putalpha(a)
        save_webp(sil, name)


# ---------------------------------------------------------------------------
# 결정 #31 나무·동물 도감. 앱(app-dev)과 웹 미리보기(web/forest)는 derived/catalog.json을 읽는다.
#   derived : extract_assets.py가 만든 투명 PNG(None이면 오리기 품질 미달 → 앱 파일을 만들지 않음)
#   webp    : 앱 그림 이름. 이미 있는 그림(T01, A01~A03)은 복사본을 만들지 않고 기존 이름을 가리킨다.
#   locked  : 잠긴 실루엣(알파 모양 + LOCKED_BROWN). `?`·자물쇠는 앱이 얹는다.
#   ok      : 크게 확대해 밝은·어두운 바탕에서 가장자리·이웃 그림·이름표 글자를 확인했는지(2026-11-03 Claude Code)
#   price   : 해금 가격(★, decisions #31). 0이면 처음부터 열림. 값의 기준은 decisions.md와 앱 코드다.
# ---------------------------------------------------------------------------
DEX_TREES = [
    dict(id="T01", name="참나무", derived="GGF-TREE-4-FULL-v1.png", webp="ggf_pic_tree_4", locked="ggf_pic_locked_tree_t01", source="ILL-021", price=0, ok=True, existing=True),
    dict(id="T02", name="벚나무", derived="GGF-TREE-T02-CHERRY-v1.png", webp="ggf_pic_tree_t02", locked="ggf_pic_locked_tree_t02", source="ILL-038", price=0, ok=True),
    dict(id="T03", name="사과나무", derived="GGF-TREE-T03-APPLE-v1.png", webp="ggf_pic_tree_t03", locked="ggf_pic_locked_tree_t03", source="ILL-038", price=200, ok=True),
    dict(id="T04", name="단풍나무", derived="GGF-TREE-T04-MAPLE-v1.png", webp="ggf_pic_tree_t04", locked="ggf_pic_locked_tree_t04", source="ILL-038", price=300, ok=True),
    dict(id="T05", name="소나무", derived="GGF-TREE-T05-PINE-v1.png", webp="ggf_pic_tree_t05", locked="ggf_pic_locked_tree_t05", source="ILL-038", price=500, ok=True),
    dict(id="T06", name="은행나무", derived="GGF-TREE-T06-GINKGO-v1.png", webp="ggf_pic_tree_t06", locked="ggf_pic_locked_tree_t06", source="ILL-038", price=700, ok=True),
    dict(id="T07", name="귤나무", derived="GGF-TREE-T07-TANGERINE-v1.png", webp="ggf_pic_tree_t07", locked="ggf_pic_locked_tree_t07", source="ILL-038", price=1000, ok=True),
    dict(id="T08", name="자작나무", derived="GGF-TREE-T08-BIRCH-v1.png", webp="ggf_pic_tree_t08", locked="ggf_pic_locked_tree_t08", source="ILL-038", price=1300, ok=True),
    dict(id="T09", name="동백나무", derived="GGF-TREE-T09-CAMELLIA-v1.png", webp="ggf_pic_tree_t09", locked="ggf_pic_locked_tree_t09", source="ILL-038", price=1700, ok=True),
    dict(id="T10", name="대나무", derived="GGF-TREE-T10-BAMBOO-v1.png", webp="ggf_pic_tree_t10", locked="ggf_pic_locked_tree_t10", source="ILL-038", price=2200, ok=True),
]
DEX_ANIMALS = [
    dict(id="A01", name="다람쥐", derived="GGF-ANIMAL-SQUIRREL-NOPAD-v1.png", webp="ggf_pic_animal_squirrel", locked="ggf_pic_locked_squirrel", source="ILL-018", price=0, ok=True, existing=True),
    dict(id="A02", name="토끼", derived="GGF-ANIMAL-RABBIT-NOPAD-v1.png", webp="ggf_pic_animal_rabbit", locked="ggf_pic_locked_rabbit", source="ILL-018", price=0, ok=True, existing=True),
    dict(id="A03", name="파랑새", derived="GGF-ANIMAL-BIRD-v1.png", webp="ggf_pic_animal_bird", locked="ggf_pic_locked_bird", source="ILL-010", price=200, ok=True, existing=True),
    dict(id="A04", name="사슴", derived="GGF-ANIMAL-A04-DEER-v1.png", webp="ggf_pic_animal_a04", locked="ggf_pic_locked_animal_a04", source="ILL-010", price=400, ok=True),
    dict(id="A05", name="여우", derived="GGF-ANIMAL-A05-FOX-v1.png", webp="ggf_pic_animal_a05", locked="ggf_pic_locked_animal_a05", source="ILL-010", price=600, ok=True),
    dict(id="A06", name="너구리", derived="GGF-ANIMAL-A06-RACCOON-v1.png", webp="ggf_pic_animal_a06", locked="ggf_pic_locked_animal_a06", source="ILL-010", price=800, ok=True),
    dict(id="A07", name="고슴도치", derived="GGF-ANIMAL-A07-HEDGEHOG-v1.png", webp="ggf_pic_animal_a07", locked="ggf_pic_locked_animal_a07", source="ILL-010", price=1000, ok=True),
    dict(id="A08", name="부엉이", derived="GGF-ANIMAL-A08-OWL-v1.png", webp="ggf_pic_animal_a08", locked="ggf_pic_locked_animal_a08", source="ILL-010", price=1300, ok=True),
    dict(id="A09", name="오리", derived="GGF-ANIMAL-A09-DUCK-v1.png", webp="ggf_pic_animal_a09", locked="ggf_pic_locked_animal_a09", source="ILL-010", price=1600, ok=True),
    dict(id="A10", name="수달", derived="GGF-ANIMAL-A10-OTTER-v1.png", webp="ggf_pic_animal_a10", locked="ggf_pic_locked_animal_a10", source="ILL-010", price=2000, ok=True),
]
# 항목별 메모(catalog.json의 note). 품질 미달이면 그 이유를, 통과해도 쓸 때 알아야 할 점을 적는다.
_SMALL_TREE = "ILL-038 도감 그림 크기 그대로(줄이지도 늘리지도 않음). 화면에서 이보다 크게 그리면 흐려진다 → 크게 쓰려면 GPT 요청 R12"
_SMALL_ANIMAL = "ILL-010 도감 카드 크기 그대로. 화면에서 이보다 크게 그리면 흐려진다 → 크게 쓰려면 GPT 요청 R13"
DEX_NOTES: dict[str, str] = {
    "A01": "ILL-018 잠금 도감에서 오린 것. 발밑 풀 받침은 지웠다(2026-11-04, 저녁 맵에서 낮 색 풀이 튀어서. build_nopad)",
    "A02": "ILL-018 잠금 도감에서 오린 것. 발밑 풀 받침과 옆 꽃은 지웠다(2026-11-04, build_nopad)",
    "T01": "성장 4단계(ILL-021)의 다 자란 나무. 다른 나무보다 그림이 크다(숲에서는 키를 맞춰 그린다)",
    **{t: _SMALL_TREE for t in ("T02", "T03", "T04", "T05", "T06", "T08")},
    **{t: _SMALL_TREE + ". 도감 2줄째 나무라 1줄째보다 키가 작게 그려져 있다" for t in ("T07", "T09", "T10")},
    "A04": _SMALL_ANIMAL,
    "A05": _SMALL_ANIMAL + ". 엎드려 자는 모습",
    "A06": _SMALL_ANIMAL + ". 발밑 풀은 지웠고 배 아래 초록빛은 원본 명암",
    "A07": _SMALL_ANIMAL,
    "A08": _SMALL_ANIMAL + ". 앉아 있던 나뭇가지는 빼고 몸만(서 있는 모습)",
    "A09": _SMALL_ANIMAL + ". 어미 오리만(새끼 오리·물은 뺌). 몸 아래가 둥글어 물 위에 뜬 모습",
    "A10": _SMALL_ANIMAL + ". 물에 잠긴 아래가 수면 높이에서 잘려 앉은 모습처럼 보임",
}
CATALOG = os.path.join(ROOT, "design/illustrations/derived/catalog.json")


def webp_size(name: str) -> tuple[int, int]:
    with Image.open(os.path.join(RES, name + ".webp")) as im:
        return im.size


def save_locked(src_png: str, name: str) -> None:
    a = clean_alpha(Image.open(os.path.join(DERIVED, src_png))).getchannel("A")
    sil = Image.new("RGBA", a.size, LOCKED_BROWN + (255,))
    sil.putalpha(a)
    save_webp(sil, name)


def build_dex() -> None:
    """도감 그림(WebP)을 만들고 catalog.json을 쓴다. 이미 있는 그림은 다시 만들지 않는다."""
    import json

    out = {
        "about": "같이가숲 나무·동물 도감 그림 목록(결정 #31). design/illustrations/tools/build_app_assets.py --dex 가 만든다. "
                 "webp·locked_webp는 core/ui/src/main/res/drawable-nodpi/의 리소스 이름(확장자 없음). "
                 "ok=false인 항목은 앱 그림이 없다(webp=null) → GPT 요청 R12(나무)·R13(동물)로 받는다. "
                 "잠긴 실루엣의 `?`와 자물쇠(ggf_pic_padlock)는 앱이 얹는다. price는 참고값이며 기준은 decisions.md #31이다.",
        "locked_color": "#%02X%02X%02X" % LOCKED_BROWN,
        "trees": [],
        "animals": [],
    }
    for key, table in (("trees", DEX_TREES), ("animals", DEX_ANIMALS)):
        for d in table:
            ok = d["ok"] and os.path.exists(os.path.join(DERIVED, d["derived"]))
            if ok and not d.get("existing"):
                im = clean_alpha(Image.open(os.path.join(DERIVED, d["derived"])))
                save_webp(im, d["webp"])  # 오린 크기 그대로(원본보다 작아서 줄이지도 늘리지도 않음)
            if ok and not os.path.exists(os.path.join(RES, d["locked"] + ".webp")):
                save_locked(d["derived"], d["locked"])
            w, h = webp_size(d["webp"]) if ok else (None, None)
            out[key].append(dict(
                id=d["id"], name=d["name"],
                webp=d["webp"] if ok else None,
                locked_webp=d["locked"] if ok else None,
                width=w, height=h,
                source=d["source"],
                ok=bool(ok),
                price=d["price"],
                starts_open=d["price"] == 0,
                derived_png=f"2026-11-03/{d['derived']}" if ok else None,
                note=DEX_NOTES.get(d["id"], ""),
            ))
    with open(CATALOG, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("catalog:", CATALOG)


WEB_FOREST = os.path.join(ROOT, "web/forest")


def build_web_forest() -> None:
    """웹 미리보기(web/forest/)에 앱과 같은 WebP를 복사하고, catalog.json을 index.html 안에 넣는다(서버 없이 열리게)."""
    import json
    import re
    import shutil

    assets = os.path.join(WEB_FOREST, "assets")
    os.makedirs(assets, exist_ok=True)
    with open(CATALOG, encoding="utf-8") as f:
        cat = json.load(f)
    # 자물쇠, 별 배지(R15), 자라는 단계 그림(새싹·묘목·작은 나무). 숲 맵 4장(R17 큰 맵)은 stitch_large_maps.py가 넣는다
    names = {"ggf_pic_padlock", "ggf_pic_star_badge", "ggf_pic_tree_1", "ggf_pic_tree_2", "ggf_pic_tree_3"}
    for d in cat["trees"] + cat["animals"]:
        names.update(n for n in (d["webp"], d["locked_webp"]) if n)
    for n in sorted(names):
        shutil.copyfile(os.path.join(RES, n + ".webp"), os.path.join(assets, n + ".webp"))
    # 잠금 `?`(ILL-018에서 오린 크림색 물음표). 앱은 글자로 얹고, 웹은 같은 그림을 쓴다.
    # derived의 `?`는 오른쪽 끝에 이웃 실루엣 가장자리(크림색 조각)가 붙어 있어, 오른쪽 끝에 닿은 덩어리는 뺀다.
    import numpy as np
    from scipy import ndimage as ndi

    q = np.asarray(clean_alpha(Image.open(os.path.join(DERIVED, "GGF-LOCK-QMARK-v1.png")))).copy()
    lbl, _ = ndi.label(q[..., 3] > 0, structure=np.ones((3, 3)))
    edge_ids = set(np.unique(lbl[:, -1])) - {0}
    q[np.isin(lbl, list(edge_ids)), 3] = 0
    q = Image.fromarray(q, "RGBA")
    q = q.crop(q.getchannel("A").getbbox())
    q.save(os.path.join(assets, "ggf_lock_qmark.webp"), "WEBP", quality=QUALITY, method=6, exact=True)
    # 머리의 앱 아이콘(R14): 적응형 아이콘 두 층을 겹쳐 런처가 보이는 가운데 72dp만 자른다(앱과 같은 그림)
    xx = os.path.join(APP_RES, "mipmap-xxhdpi")
    icon = Image.alpha_composite(
        Image.open(os.path.join(xx, "ic_launcher_background.webp")).convert("RGBA"),
        Image.open(os.path.join(xx, "ic_launcher_foreground.webp")).convert("RGBA"),
    )
    side = icon.size[0]
    cut = round(side * 18 / ICON_CANVAS_DP)  # 108dp 중 바깥 18dp씩
    icon.crop((cut, cut, side - cut, side - cut)).convert("RGB").save(
        os.path.join(assets, "ggf_app_icon.webp"), "WEBP", quality=QUALITY, method=6)
    keep = ("id", "name", "webp", "locked_webp", "width", "height", "price", "ok")
    slim = {k: [{f: d[f] for f in keep} for d in cat[k]] for k in ("trees", "animals")}
    page = os.path.join(WEB_FOREST, "index.html")
    with open(page, encoding="utf-8") as f:
        html = f.read()
    data = json.dumps(slim, ensure_ascii=False, separators=(",", ":"))
    html, n = re.subn(r"/\*CATALOG-START\*/.*?/\*CATALOG-END\*/", lambda _: f"/*CATALOG-START*/{data}/*CATALOG-END*/", html, flags=re.S)
    if n != 1:
        sys.exit("web/forest/index.html에서 CATALOG 자리를 찾지 못했습니다")
    with open(page, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"web/forest: 그림 {len(names) + 2}장 복사·생성, 도감 목록 넣음")


# ---------------------------------------------------------------------------
# GPT 납품 2026-11-04: R14 앱 아이콘, R15 별 배지, R16 저녁 맵(--r14)
# ---------------------------------------------------------------------------
EXPORTS_1104 = os.path.join(ROOT, "design/illustrations/exports/2026-11-04")
APP_RES = os.path.join(ROOT, "app/src/main/res")
# 낮 맵(R9~R11)과 같은 크기·구도의 저녁 그림 → 다크 모드 숲 맵. 낮 그림과 어긋남 0px(경계선 상관으로 확인, 2026-11-04)
EVENING_MAPS = [
    ("GGF-MAP-COAST-CABLECAR-EVENING-R16-v1.png", "ggf_pic_map_coast_evening"),
    ("GGF-MAP-PARK-EVENING-R16-v1.png", "ggf_pic_map_park_evening"),
    ("GGF-MAP-RIVER-MEADOW-EVENING-R16-v1.png", "ggf_pic_map_river_evening"),
]
STAR_BADGE = ("GGF-ICON-STAR-R15-v1.png", "ggf_pic_star_badge")
STAR_MAX = 160  # 화면에서 최대 약 48dp → 고해상도 화면까지 넉넉히
# 적응형 아이콘: 108dp 캔버스, 런처가 가운데 지름 72dp 안쪽만 보이고, 지름 66dp 원은 어떤 모양으로 잘려도 남는다.
# 그림(알파 128 이상)을 감싸는 가장 작은 원이 지름 ICON_ART_DP가 되게 자른다.
ICON_CANVAS_DP = 108
ICON_ART_DP = 60
ICON_DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def enclosing_circle(alpha, threshold: int = 128):
    """그림 픽셀을 모두 감싸는 (거의) 가장 작은 원의 중심·반지름. 볼록 껍질 점들로 중심을 좁혀 간다."""
    import numpy as np

    ys, xs = np.nonzero(np.asarray(alpha) >= threshold)
    pts = np.stack([xs, ys], 1).astype(np.float64)
    cx, cy = (xs.min() + xs.max()) / 2.0, (ys.min() + ys.max()) / 2.0
    step = max(xs.max() - xs.min(), ys.max() - ys.min()) / 4.0
    best = (np.sqrt(((pts - (cx, cy)) ** 2).sum(1)).max(), cx, cy)
    while step > 0.5:
        improved = False
        for dx in (-step, 0, step):
            for dy in (-step, 0, step):
                x, y = best[1] + dx, best[2] + dy
                r = np.sqrt(((pts - (x, y)) ** 2).sum(1)).max()
                if r < best[0]:
                    best, improved = (r, x, y), True
        if not improved:
            step /= 2.0
    return best[1], best[2], best[0]


def save_mipmap(im: Image.Image, name: str) -> None:
    """적응형 아이콘 층 하나를 밀도별 mipmap WebP로(108dp). 원본보다 크게 늘리지 않는다."""
    for qual, k in ICON_DENSITIES.items():
        px = round(ICON_CANVAS_DP * k)
        if px > im.size[0]:
            sys.exit(f"{name}: {qual} {px}px는 원본 {im.size[0]}px보다 커서 늘려야 합니다")
        d = os.path.join(APP_RES, f"mipmap-{qual}")
        os.makedirs(d, exist_ok=True)
        path = os.path.join(d, name + ".webp")
        im.resize((px, px), Image.LANCZOS).save(path, "WEBP", quality=QUALITY, method=6, exact=True)
        print(f"mipmap-{qual}/{name}.webp  {px}x{px}  {os.path.getsize(path) // 1024}KB")


# 다람쥐·토끼 발밑 풀 받침 지우기(2026-11-04 Mac 검증: 다크 모드 저녁 맵에서 낮 색 연두 받침이 패치처럼 튐).
# 다른 동물(A03~A10)처럼 몸만 남긴다. (원본 오린 그림, 받침 없는 그림, 노란 연두를 지울 기준: g ≥ r × 이 값)
NOPAD = [
    ("GGF-ANIMAL-SQUIRREL-v1.png", "GGF-ANIMAL-SQUIRREL-NOPAD-v1.png", 0.85),  # 받침 가장자리가 노란 연두
    ("GGF-ANIMAL-RABBIT-v1.png", "GGF-ANIMAL-RABBIT-NOPAD-v1.png", 0.97),  # 흰 털의 따뜻한 그늘을 지우지 않게 좁게
]
NOPAD_BAND = 0.6  # 그림 아래 40%에서만 지운다(위쪽 털·귀는 건드리지 않음)


def remove_grass_pad(src: str, dst: str, yellow_ratio: float) -> None:
    """
    아래 띠의 초록·노란 연두 픽셀을 투명으로 → 몸통(가장 큰 덩어리)에 붙지 않은 조각(꽃·잎)을 지움 →
    발끝 아래 1~3px의 올리브색(받침 그림자와 털이 섞인 것)을 지움 → 가장자리 정리(defringe). 색은 바꾸지 않는다.
    """
    import numpy as np
    from scipy import ndimage as ndi

    im = np.asarray(Image.open(os.path.join(DERIVED, src)).convert("RGBA")).astype(np.float32)
    h, w = im.shape[:2]
    r, g, b = (im[..., i] / 255.0 for i in range(3))
    mx, mn = np.maximum(np.maximum(r, g), b), np.minimum(np.minimum(r, g), b)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    band = (np.arange(h)[:, None] >= h * NOPAD_BAND).repeat(w, 1)
    grass = ((g > r * 1.05) & (g > b * 1.05) & (sat > 0.18)) | ((g >= r * yellow_ratio) & (g > b * 1.2) & (sat > 0.3))
    alpha = im[..., 3].copy()
    alpha[grass & band] = 0
    lbl, n = ndi.label(alpha > 40, structure=np.ones((3, 3)))
    sizes = ndi.sum(np.ones_like(alpha), lbl, range(1, n + 1))
    alpha[~ndi.binary_dilation(lbl == np.argmax(sizes) + 1, iterations=2)] = 0
    olive = (g >= r * 0.78) & (b < g * 0.45) & band
    for x in range(w):
        ys = np.nonzero(alpha[:, x] > 10)[0]
        y = ys.max() if len(ys) else -1
        for _ in range(3):
            if y < 0 or alpha[y, x] <= 10 or not olive[y, x]:
                break
            alpha[y, x] = 0
            y -= 1
    im[..., 3] = alpha
    out = defringe(Image.fromarray(im.astype(np.uint8), "RGBA"))
    out = out.crop(alpha_box(out))
    out.save(os.path.join(DERIVED, dst))
    print("받침 지움:", dst, out.size)


def build_nopad() -> None:
    for src, dst, ratio in NOPAD:
        remove_grass_pad(src, dst, ratio)
    for f, name, mw, mh in DERIVED_SPRITES:
        if "NOPAD" in f:
            save_webp(shrink(clean_alpha(Image.open(os.path.join(DERIVED, f))), max_w=mw, max_h=mh), name)
    for f, name in LOCKED:
        if "NOPAD" in f:
            save_locked(f, name)


def build_r14_r16() -> None:
    # R16 저녁 맵은 2026-11-04 R17 큰 맵(지형이 다름)으로 바뀌어 앱에 넣지 않는다. 큰 맵의 저녁 그림은 R18을 기다린다
    # (EVENING_MAPS는 기록으로 남김).
    # R15: 알파 10 이하의 바깥 점(배경 잔여)을 지우고, 별에 맞춰 자른 뒤 줄인다.
    star = clean_alpha(Image.open(os.path.join(EXPORTS_1104, STAR_BADGE[0])))
    star = star.crop(pad_box(alpha_box(star, 10), star.size, 4))
    save_webp(shrink(star, max_w=STAR_MAX, max_h=STAR_MAX), STAR_BADGE[1])
    # R14: 전경(차·나무·언덕)을 감싸는 원이 지름 60dp가 되는 정사각형을 전경·배경에서 같이 자른다.
    fg = clean_alpha(Image.open(os.path.join(EXPORTS_1104, "GGF-APP-ICON-FG-R14-v1.png")))
    bg = Image.open(os.path.join(EXPORTS_1104, "GGF-APP-ICON-BG-R14-v1.png")).convert("RGB")
    cx, cy, r = enclosing_circle(fg.getchannel("A"))
    side = 2 * r * ICON_CANVAS_DP / ICON_ART_DP
    box = tuple(round(v) for v in (cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2))
    if box[0] < 0 or box[1] < 0 or box[2] > fg.size[0] or box[3] > fg.size[1]:
        sys.exit(f"아이콘 자르기 상자 {box}가 원본 밖으로 나갑니다")
    print(f"아이콘: 그림 원 중심 ({cx:.0f}, {cy:.0f}) 지름 {2 * r:.0f}px → 자르기 {box} ({box[2] - box[0]}px = 108dp)")
    save_mipmap(fg.crop(box), "ic_launcher_foreground")
    save_mipmap(bg.crop(box), "ic_launcher_background")


# GPT R44 끝말잇기 아이콘(결정 #35): 남은 시간 문장 옆. R7 노래 아이콘(256px 안)과 같은 크기로.
# GPT 그림에 알파 1~15의 희미한 점이 캔버스 끝까지 퍼져 있어 15 이하는 0으로 지운 뒤 자른다(색은 그대로).
WORDCHAIN_ICON = ("design/illustrations/exports/2026-11-06/GGF-ICON-WORDCHAIN-R44-v1.png", "ggf_pic_icon_wordchain")
SENTENCE_ICON_MAX = 256


def build_r44() -> None:
    src, name = WORDCHAIN_ICON
    im = clean_alpha(Image.open(os.path.join(ROOT, src)))
    r, g, b, a = im.split()
    a = a.point(lambda v: 0 if v <= 15 else v)
    im = Image.merge("RGBA", (r, g, b, a))
    im = im.crop(pad_box(alpha_box(im, threshold=15), im.size, 4))
    save_webp(shrink(im, max_w=SENTENCE_ICON_MAX, max_h=SENTENCE_ICON_MAX), name)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cutout", action="store_true", help="여자아이를 원본에서 다시 오린다(rembg 필요)")
    ap.add_argument("--dex", action="store_true", help="나무·동물 도감 그림과 catalog.json만 만든다")
    ap.add_argument("--r14", action="store_true", help="GPT R14 앱 아이콘·R15 별 배지·R16 저녁 맵, 받침 없는 다람쥐·토끼, 도감 목록·웹 그림")
    ap.add_argument("--r44", action="store_true", help="GPT R44 끝말잇기 아이콘만")
    args = ap.parse_args()
    if args.r44:
        build_r44()
        return
    if args.dex:
        build_dex()
        build_web_forest()
        return
    if args.r14:
        build_r14_r16()
        build_nopad()
        build_dex()
        build_web_forest()
        return
    if args.cutout:
        cutout_girl()
    build_cars()
    build_people()
    build_girl()
    build_derived()
    build_dex()
    build_web_forest()


if __name__ == "__main__":
    main()
