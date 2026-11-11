#!/usr/bin/env python3
"""
같이가숲 파생 그림 만들기: GPT 원본 그림 → 자르기 → 배경 지우기 → 투명 PNG.
(앱용 WebP는 build_app_assets.py가 이 PNG에서 만든다.)

원칙(design/illustrations/REQUESTS.md 0장·5장, decisions.md #29):
- 원본 파일(originals/)은 읽기만 한다. 결과는 derived/2026-11-03/에 새로 쓴다(exports/는 ChatGPT 납품 폴더).
- 원본보다 크게 늘리지 않는다(이 스크립트에는 확대 단계가 없다).
- 색 보정·필터·채도 변경을 하지 않는다. 불투명 픽셀은 원본 RGB 그대로이고,
  가장자리 반투명 픽셀만 배경색을 빼서(언믹싱) 흰 테두리·찌꺼기가 남지 않게 한다.
- 결과 파일에는 메타데이터(EXIF·ICC·텍스트)를 넣지 않는다.
- 금지사항 3가지(베끼기, 몸 내밀기 등 안전, 실제 로고)가 있는 부분은 자르지 않는다.
  예: 주행 배너 ILL-006~009·012의 차와 탑승 장면은 쓰지 않고, 배경·이정표·핀만 쓴다.

실행(저장소 루트에서, Pillow·numpy·opencv·scipy·rembg 필요):
    python design/illustrations/tools/extract_assets.py            # 전부
    python design/illustrations/tools/extract_assets.py KID-STAND  # 몇 개만

배경 지우기 방법(METHODS):
- cardkey : 크림색 카드 위의 그림. 카드 테두리 띠에서 배경색 면(2차식)을 맞추고, 가장자리에서 이어진
            배경만 지운다(닫힌 안쪽의 흰 옷·눈은 그대로). 가장자리 알파는 "배경색 ↔ 안쪽 색" 사이의
            비율로 정하고 배경색을 뺀다. 이모션 카드(ILL-025), 도감 카드(ILL-018)에 쓴다.
- model   : rembg 분할 모델(isnet-anime, isnet-general-use). 풀밭처럼 배경이 복잡할 때 쓴다.
            모델 알파를 단단하게(lo~hi 구간만 부드럽게) 만들고, 반투명 가장자리 색은 가장 가까운
            안쪽 불투명 픽셀 색으로 바꿔 배경 풀색이 번지지 않게 한다.
- lumakey : 갈색 실루엣 위의 크림색 `?`처럼 두 색만 있는 그림. 밝기 비율로 알파를 정한다.
- scene   : 배경 그림. 배경을 지우지 않고 자르기만 한다(필요하면 작은 부분만 같은 그림의 풀밭으로 덮음).
- silhouette : 이미 오린 그림의 알파 모양에 ILL-018 잠금 실루엣의 갈색을 칠하고 `?`를 얹는다(Claude Code 제작).

model 방식의 추가 옵션(2026-11-03, 나무·동물 도감 T02~T10·A04~A10):
- model="birefnet-general" : 밝은 잎(벚꽃·은행잎)도 놓치지 않는 분할 모델. 느려서 마스크를 ~/.cache/ggf-extract/에 저장해 쓴다.
- decontam   : 맨 바깥 1px의 배경 물듦(하늘색·풀색)을 안쪽 색으로, 알파는 안쪽↔배경 색 비율로.
- purge_hue  : (색상 시작°, 끝°, 겹 수) 가장자리에 붙은 노란 꽃·풀 조각을 바깥에서 벗긴다.
- green_bottom : (겹 수, 원본 y) 그 줄 아래에서 "초록 > 빨강"인 발밑 풀만 벗긴다.
- erase(+erase_greenish) : 원본 좌표 다각형 안을 지운다(풀빛 픽셀만 지우기 선택, 갇힌 구멍은 되돌림).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[1]  # design/illustrations
ORIG = ROOT / "originals" / "2026-11-03"
OUT = ROOT / "derived" / "2026-11-03"

# 원본 ILL 번호 → 파일(CATALOG.md)
SOURCES = {
    "ILL-002": "03-vehicles-journey-and-forest/나무를 심을 섬의 시작점.png",
    "ILL-009": "03-vehicles-journey-and-forest/숲속 여행 HUD 배너.png",
    "ILL-010": "03-vehicles-journey-and-forest/숲에 찾아오는 동물 친구들.png",
    "ILL-012": "03-vehicles-journey-and-forest/알록달록 귀여운 여행 진행 안내 UI.png",
    "ILL-018": "02-forest-and-customization/image-gen-2.png",
    "ILL-019": "02-forest-and-customization/image-gen-3.png",
    "ILL-021": "02-forest-and-customization/귀여운 식물 성장과 꽃 도감.png",
    "ILL-025": "01-character-options/06_이모션.png",
    "ILL-027": "01-character-options/01_헤어스타일.png",
    "ILL-038": "04-island-and-plants/알록달록 나무와 꽃 도감.png",
}

# ILL-018 잠금 실루엣의 갈색(사슴·여우 실루엣 여러 곳의 중앙값, 원본에서 직접 뽑음)
LOCKED_BROWN = (128, 104, 60)

# ---------------------------------------------------------------------------
# 파생 그림 목록. box = (왼쪽, 위, 오른쪽, 아래) 원본 픽셀 좌표.
# ---------------------------------------------------------------------------
ASSETS: list[dict] = [
    # C01 아이 서 있기: 헤어스타일 화면 왼쪽 큰 아이. 발밑 돌 받침은 자르는 상자 밖(발끝 아래 1px)
    dict(id="KID-STAND", cand="C01", title="아이 서 있기", src="ILL-027", box=(160, 330, 505, 858),
         method="model", model="isnet-anime", lo=0.25, hi=0.6, keep="largest",
         use="홈·아이 고르기 화면 아바타, 숲 화면의 아이"),
    # C02~C06 아이 표정: 이모션 화면(1/3) 카드. 카드 아래 글자 라벨은 상자 밖.
    dict(id="KID-EMO-START", cand="C02", title="아이 표정 출발(신나!)", src="ILL-025", box=(509, 208, 737, 376),
         method="cardkey", keep="all", use="진행 화면 0~24%(출발)"),
    dict(id="KID-EMO-LOOK", cand="C03", title="아이 표정 창밖 보기(아하!)", src="ILL-025", box=(762, 449, 986, 624),
         method="cardkey", keep="all", use="진행 화면 25~49%(창밖 보기)"),
    dict(id="KID-EMO-WAVE", cand="C04", title="아이 표정 손 흔들기(안녕!)", src="ILL-025", box=(283, 236, 470, 376),
         method="cardkey", keep="largest", use="진행 화면 50~74%(손 흔들기). 선택 표시(노란 체크)는 떼어 냄"),
    dict(id="KID-EMO-ALMOST", cand="C05", title="아이 표정 거의 다 왔어(박수!)", src="ILL-025", box=(755, 208, 986, 376),
         method="cardkey", keep="all", use="진행 화면 75~99%(거의 다 왔어)"),
    dict(id="KID-EMO-ARRIVE", cand="C06", title="아이 표정 도착(좋아!)", src="ILL-025", box=(270, 465, 487, 624),
         method="cardkey", keep="all", use="도착 화면. '만세'는 원본에 없어 GPT 추가 요청"),
    # C07 숲 배경: 빈 섬. 섬 위의 작은 아이·느낌표·그림자를 같은 줄의 풀밭으로 덮는다.
    dict(id="FOREST-ISLAND", cand="C07", title="숲 배경(빈 섬)", src="ILL-002", box=(0, 0, 1672, 941),
         method="scene", erase_poly=[(276, 590), (330, 470), (345, 440), (455, 395), (525, 400), (525, 480),
                                     (480, 560), (480, 600), (425, 640), (300, 640), (272, 615)],
         use="아이의 숲, 도착 결과의 숲 그림(나무·동물을 앱이 심음)"),
    # C08~C11 나무 4단계(같은 나무의 성장). 풀밭 배경이라 분할 모델을 쓴다.
    dict(id="TREE-1-SPROUT", cand="C08", title="나무 1단계 새싹", src="ILL-021", box=(158, 343, 327, 444),
         method="model", model="isnet-general-use", lo=0.3, hi=0.65, keep="all", min_comp=200,
         add_soil=(158, 395, 327, 444), use="숲: 새싹 1~2개째 자리"),
    dict(id="TREE-2-SEEDLING", cand="C09", title="나무 2단계 어린 묘목", src="ILL-021", box=(466, 256, 700, 444),
         method="model", model="isnet-general-use", lo=0.3, hi=0.65, keep="largest",
         add_soil=(540, 410, 700, 444), use="숲: 새싹 3~5개째 자리"),
    dict(id="TREE-3-SMALL", cand="C10", title="나무 3단계 작은 나무", src="ILL-021", box=(780, 150, 1045, 445),
         method="model", model="isnet-general-use", lo=0.4, hi=0.7, keep="largest", use="숲: 새싹 6~9개째 자리"),
    dict(id="TREE-4-FULL", cand="C11", title="나무 4단계 나무", src="ILL-021", box=(1080, 18, 1482, 450),
         method="model", model="isnet-general-use", lo=0.4, hi=0.7, keep="largest", use="숲: 다 자란 나무(새싹 10개 = 1그루)"),
    # C12~C14 동물 3종
    dict(id="ANIMAL-SQUIRREL", cand="C12", title="동물 다람쥐", src="ILL-018", box=(424, 247, 626, 418),
         method="cardkey", keep="largest", tol_bg=11.0, use="숲 동물(나무 3그루)"),
    # 토끼: 흰 털이 크림색 카드와 비슷하다. 분할 모델은 토끼를 놓쳐서 카드 키를 쓰되, 가장자리 색은 안쪽 털 색으로
    # 채우고(edge_inside, 검은 점 방지) 맨 위 4줄은 비운다(clear_top, 위 "해금 완료" 띠 그림자). 2026-11-03
    dict(id="ANIMAL-RABBIT", cand="C13", title="동물 토끼", src="ILL-018", box=(196, 247, 384, 418),
         method="cardkey", keep="largest", tol_bg=11.0, edge_inside=True, clear_top=7, union_model="isnet-general-use",
         use="숲 동물(나무 10그루)"),
    dict(id="ANIMAL-BIRD", cand="C14", title="동물 새(파랑새)", src="ILL-010", box=(298, 478, 452, 609),
         method="model", model="isnet-general-use", lo=0.35, hi=0.65, keep="largest", use="숲 동물(나무 25그루)"),
    # C15 잠금: `?` 글자 모양과 자물쇠는 ILL-018에서 오리고, 실루엣은 C12~C14의 알파로 만든다.
    dict(id="LOCK-QMARK", cand="C15", title="잠금 물음표", src="ILL-018", box=(712, 296, 768, 356),
         method="lumakey", dark=LOCKED_BROWN, use="잠긴 동물 실루엣 위 `?`"),
    dict(id="LOCK-PADLOCK", cand="C15", title="잠금 자물쇠", src="ILL-018", box=(722, 400, 786, 463),
         method="cardkey", keep="largest", use="잠긴 동물 칸의 자물쇠"),
    dict(id="LOCKED-SQUIRREL", cand="C15", title="잠긴 다람쥐", base="ANIMAL-SQUIRREL", method="silhouette",
         use="숲 화면: 다음에 올 동물"),
    dict(id="LOCKED-RABBIT", cand="C15", title="잠긴 토끼", base="ANIMAL-RABBIT", method="silhouette",
         use="숲 화면: 다음에 올 동물"),
    dict(id="LOCKED-BIRD", cand="C15", title="잠긴 새", base="ANIMAL-BIRD", method="silhouette",
         use="숲 화면: 다음에 올 동물"),
    # C16 진행 막대 꾸밈: 위쪽 HUD 배너(ILL-009)의 핀·새싹(아래에 글자 라벨이 없다).
    dict(id="ROAD-SPROUT", cand="C16", title="진행 막대 새싹 이정표", src="ILL-009", box=(757, 143, 848, 210),
         method="model", model="isnet-general-use", lo=0.35, hi=0.7, keep="largest", use="진행 막대 10%마다"),
    dict(id="ROAD-PIN-START", cand="C16", title="출발 핀", src="ILL-009", box=(487, 104, 598, 238),
         method="model", model="isnet-general-use", lo=0.35, hi=0.7, keep="largest", use="진행 막대 왼쪽 끝(출발)"),
    dict(id="ROAD-PIN-GOAL", cand="C16", title="목적지 핀", src="ILL-009", box=(1687, 101, 1798, 228),
         method="model", model="isnet-general-use", lo=0.35, hi=0.7, keep="largest", use="진행 막대 오른쪽 끝(목적지)"),
    # C17 로고 없는 차: 차량 도감 '기본 자동차'. 보닛의 작은 엠블럼 모양 점(약 6×5px)은 주변 보닛 색으로 메운다.
    dict(id="CAR-BASIC", cand="C17", title="로고 없는 차(기본 자동차)", src="ILL-019", box=(196, 272, 394, 404),
         method="model", model="isnet-general-use", lo=0.4, hi=0.7, keep="largest",
         retouch=[(240, 339, 251, 350)], mirror=True,
         use="진행 막대 위의 우리 차(오른쪽을 보게 좌우 뒤집음)"),
    # C18 진행 화면 배경: 주행 배너(ILL-009) 아래 흐린 숲. 위쪽 HUD 틀은 상자 밖.
    dict(id="JOURNEY-BG", cand="C18", title="진행 화면 배경(흐린 숲)", src="ILL-009", box=(0, 276, 2172, 724),
         method="scene", use="진행 화면 바탕(주행 중 정지 그림)"),
    # C19 도착지 장면: ILL-012 오른쪽 집·사슴·토끼. 안내 말풍선(왼쪽 위)과 목적지 핀(오른쪽 아래)은 상자 밖.
    dict(id="ARRIVAL-HOUSE", cand="C19", title="도착지 장면(숲속 집)", src="ILL-012", box=(1460, 0, 1996, 466),
         method="scene", use="도착 화면, 진행 막대 오른쪽 끝 장면"),
    # ---- 결정 #31 나무 도감 T02~T10(T01 참나무는 TREE-4-FULL). ILL-038 '나무 종류' 풀밭 위 그림.
    # 이름표(크림색 띠)는 밑동 바로 아래(1줄 표 252·441행)부터라 상자 아래를 그 2줄 위에서 끊는다.
    # 분할 모델은 birefnet-general(isnet-general-use는 벚꽃·은행잎을 배경으로 지움, 2026-11-03 비교).
    dict(id="TREE-T02-CHERRY", cand="T02", title="나무 도감 벚나무", src="ILL-038", box=(360, 66, 530, 250),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, use="나무 도감·숲(처음부터 열림)"),
    dict(id="TREE-T03-APPLE", cand="T03", title="나무 도감 사과나무", src="ILL-038", box=(1330, 76, 1492, 250),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, use="나무 도감·숲(200★)"),
    dict(id="TREE-T04-MAPLE", cand="T04", title="나무 도감 단풍나무", src="ILL-038", box=(526, 66, 696, 250),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, use="나무 도감·숲(300★)"),
    dict(id="TREE-T05-PINE", cand="T05", title="나무 도감 소나무", src="ILL-038", box=(210, 60, 364, 250),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, use="나무 도감·숲(500★)"),
    # 은행나무: 오른쪽 아래 흐린 잎 하나가 풀밭과 섞여 연두색으로 보여 지운다.
    dict(id="TREE-T06-GINKGO", cand="T06", title="나무 도감 은행나무", src="ILL-038", box=(692, 66, 852, 250),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True,
         erase=[[(818, 176), (848, 172), (848, 200), (818, 200)]], erase_greenish=True, use="나무 도감·숲(700★)"),
    dict(id="TREE-T07-TANGERINE", cand="T07", title="나무 도감 귤나무", src="ILL-038", box=(24, 288, 174, 439),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, use="나무 도감·숲(1,000★)"),
    dict(id="TREE-T08-BIRCH", cand="T08", title="나무 도감 자작나무", src="ILL-038", box=(850, 70, 1003, 250),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, use="나무 도감·숲(1,300★)"),
    dict(id="TREE-T09-CAMELLIA", cand="T09", title="나무 도감 동백나무", src="ILL-038", box=(1092, 290, 1238, 439),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, use="나무 도감·숲(1,700★)"),
    dict(id="TREE-T10-BAMBOO", cand="T10", title="나무 도감 대나무", src="ILL-038", box=(1388, 288, 1526, 439),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, use="나무 도감·숲(2,200★)"),
    # ---- 결정 #31 동물 도감 A04~A10(A01~A03은 위 다람쥐·토끼·새). ILL-010 카드(흐린 숲 사진 바탕)라 분할 모델.
    # 카드 아래 이름표는 상자 밖(이름표 위 끝 375·376·641·925행).
    dict(id="ANIMAL-A04-DEER", cand="A04", title="동물 도감 사슴", src="ILL-010", box=(546, 152, 748, 375),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, purge_hue=(45, 170, 4), use="동물 도감·숲(400★)"),
    dict(id="ANIMAL-A05-FOX", cand="A05", title="동물 도감 여우", src="ILL-010", box=(790, 196, 1010, 374),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, purge_hue=(45, 170, 4),
         green_bottom=(10, 350), use="동물 도감·숲(600★)"),
    dict(id="ANIMAL-A06-RACCOON", cand="A06", title="동물 도감 너구리", src="ILL-010", box=(1040, 172, 1262, 375),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, purge_hue=(45, 170, 4),
         green_bottom=(10, 352), use="동물 도감·숲(800★)"),
    dict(id="ANIMAL-A07-HEDGEHOG", cand="A07", title="동물 도감 고슴도치", src="ILL-010", box=(1296, 196, 1494, 375),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, purge_hue=(45, 170, 4),
         green_bottom=(10, 350), use="동물 도감·숲(1,000★)"),
    dict(id="ANIMAL-A08-OWL", cand="A08", title="동물 도감 부엉이", src="ILL-010", box=(60, 450, 225, 615),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, use="동물 도감·숲(1,300★)"),
    dict(id="ANIMAL-A09-DUCK", cand="A09", title="동물 도감 오리", src="ILL-010", box=(28, 725, 262, 905),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, purge_hue=(70, 170, 3), use="동물 도감·숲(1,600★)"),
    dict(id="ANIMAL-A10-OTTER", cand="A10", title="동물 도감 수달", src="ILL-010", box=(535, 726, 735, 905),
         method="model", model="birefnet-general", lo=0.3, hi=0.7, keep="largest", decontam=True, use="동물 도감·숲(2,000★)"),
]


# ---------------------------------------------------------------------------
# 공통 함수
# ---------------------------------------------------------------------------
def load_rgb(ill: str) -> np.ndarray:
    with Image.open(ORIG / SOURCES[ill]) as im:
        return np.array(im.convert("RGB"))


def to_lab(rgb: np.ndarray) -> np.ndarray:
    lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB).astype(np.float32)
    lab[..., 0] *= 100.0 / 255.0
    lab[..., 1:] -= 128.0
    return lab


def lab_to_rgb(lab: np.ndarray) -> np.ndarray:
    l8 = lab.copy()
    l8[..., 0] *= 255.0 / 100.0
    l8[..., 1:] += 128.0
    return cv2.cvtColor(np.clip(l8, 0, 255).astype(np.uint8), cv2.COLOR_LAB2RGB).astype(np.float32)


def fit_bg(lab: np.ndarray, ring: np.ndarray) -> np.ndarray:
    """테두리 띠 픽셀로 배경색 면(x, y의 2차식)을 맞춘다. 튀는 픽셀은 3번 걸러 낸다."""
    h, w = ring.shape
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    xx /= w
    yy /= h
    basis = np.stack([np.ones_like(xx), xx, yy, xx * xx, yy * yy, xx * yy], -1)
    sel = ring.copy()
    field = lab
    for _ in range(4):
        coef, *_ = np.linalg.lstsq(basis[sel], lab[sel], rcond=None)
        field = basis @ coef
        sel = ring & (np.linalg.norm(lab - field, axis=-1) < 5)
    return field


def pick_components(mask: np.ndarray, keep: str, min_comp: int = 60, strength: np.ndarray | None = None) -> np.ndarray:
    lbl, n = ndi.label(mask, structure=np.ones((3, 3)))
    if n == 0:
        return mask
    idx = range(1, n + 1)
    sizes = np.asarray(ndi.sum(mask, lbl, idx))
    big = int(np.argmax(sizes)) + 1
    if keep == "largest":
        ids = [big]
    else:
        means = np.asarray(ndi.mean(strength, lbl, idx)) if strength is not None else np.full(n, 99.0)
        # 작은 조각과 흐린 조각(카드 모서리 선, 배경 반짝이)은 버린다. 가장 큰 조각은 늘 남긴다.
        ids = [i + 1 for i in range(n) if i + 1 == big or (sizes[i] >= min_comp and means[i] >= 25)]
    return np.isin(lbl, ids)


def nearest_inside(rgb: np.ndarray, inside: np.ndarray) -> np.ndarray:
    """각 픽셀에서 가장 가까운 안쪽(불투명) 픽셀의 색."""
    _, (iy, ix) = ndi.distance_transform_edt(~inside, return_indices=True)
    return rgb[iy, ix]


def cardkey(rgb: np.ndarray, keep: str = "all", tol_bg: float = 5.0, band: float = 2.5, min_comp: int = 60,
            edge_inside: bool = False):
    lab = to_lab(rgb)
    h, w = lab.shape[:2]
    ring = np.zeros((h, w), bool)
    ring[:2] = ring[-2:] = True
    ring[:, :2] = ring[:, -2:] = True
    blab = fit_bg(lab, ring)
    bg_rgb = lab_to_rgb(blab)
    dist_bg = np.linalg.norm(lab - blab, axis=-1)
    cand = dist_bg < tol_bg
    lbl, _ = ndi.label(cand, structure=np.ones((3, 3)))
    touch = set(np.unique(np.concatenate([lbl[0], lbl[-1], lbl[:, 0], lbl[:, -1]]))) - {0}
    fg = ~np.isin(lbl, list(touch))
    fg = pick_components(fg, keep, min_comp, strength=dist_bg)
    dist = ndi.distance_transform_edt(fg)
    inside = fg & (dist > band)
    rgbf = rgb.astype(np.float32)
    f_est = nearest_inside(rgbf, inside)
    edge = fg & ~inside
    cb = rgbf - bg_rgb
    fb = f_est - bg_rgb
    den = (fb * fb).sum(-1)
    a_proj = np.clip((cb * fb).sum(-1) / np.maximum(den, 1e-3), 0, 1)
    a_ramp = np.clip(dist / 2.0, 0, 1)
    alpha = fg.astype(np.float32)
    a_edge = np.where(den < 15 ** 2, a_ramp, a_proj)  # 안쪽 색이 배경과 비슷하면(흰 옷) 거리로
    alpha[edge] = a_edge[edge]
    out = rgbf.copy()
    a3 = alpha[..., None]
    unmixed = np.clip((rgbf - (1 - a3) * bg_rgb) / np.maximum(a3, 1e-3), 0, 255)
    if edge_inside:
        out[edge] = f_est[edge]  # 흰 털처럼 바탕과 비슷한 색은 언믹싱하면 어두운 점이 생긴다 → 안쪽 색 그대로
    else:
        m1 = edge & (alpha >= 0.25)
        out[m1] = unmixed[m1]
        m2 = edge & (alpha < 0.25)
        out[m2] = f_est[m2]
    return out.astype(np.uint8), alpha


_SESSIONS: dict = {}
# 모델 마스크 캐시(저장소 밖). birefnet-general은 그림 한 장에 약 1분·메모리 약 10GB라 다시 돌릴 때 캐시를 쓴다.
# 키는 자른 그림의 바이트와 모델 이름이라, 상자를 바꾸면 자동으로 새로 계산한다.
CACHE = Path.home() / ".cache" / "ggf-extract"


def model_alpha(rgb: np.ndarray, model: str) -> np.ndarray:
    import hashlib

    key = hashlib.sha1(rgb.tobytes() + str(rgb.shape).encode() + model.encode()).hexdigest()
    cached = CACHE / f"{model}-{key}.npy"
    if cached.exists():
        return np.load(cached)
    from rembg import new_session, remove  # 무거워서 필요할 때만 불러온다

    if model not in _SESSIONS:
        _SESSIONS.clear()  # 모델은 한 번에 하나만 메모리에 둔다(birefnet + isnet을 함께 올리면 메모리 부족)
        import gc

        gc.collect()
        _SESSIONS[model] = new_session(model)
    res = remove(Image.fromarray(rgb), session=_SESSIONS[model], only_mask=True)
    out = np.asarray(res, dtype=np.float32) / 255.0
    CACHE.mkdir(parents=True, exist_ok=True)
    np.save(cached, out)
    return out


def soil_mask(rgb: np.ndarray) -> np.ndarray:
    """흙 무더기(갈색): 풀밭(노랑~초록)과 색상이 확실히 다르다."""
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    m = (h >= 3) & (h <= 22) & (s >= 70) & (v >= 50) & (v <= 215)
    m = ndi.binary_opening(m, iterations=1)
    m = ndi.binary_closing(m, iterations=2)
    return m


def model_cut(rgb: np.ndarray, a: dict):
    raw = model_alpha(rgb, a["model"])
    lo, hi = a.get("lo", 0.3), a.get("hi", 0.65)
    alpha = np.clip((raw - lo) / (hi - lo), 0, 1)
    if "add_soil" in a:
        x0, y0, x1, y1 = a["add_soil"]
        bx, by = a["box"][0], a["box"][1]
        sub = np.zeros(alpha.shape, bool)
        sub[y0 - by:y1 - by, x0 - bx:x1 - bx] = True
        soil = soil_mask(rgb) & sub
        # 줄기와 이어진 흙만(멀리 떨어진 갈색 점은 버림)
        alpha = np.maximum(alpha, soil.astype(np.float32))
    solid = alpha >= 0.5
    solid = pick_components(solid, a.get("keep", "largest"), a.get("min_comp", 60))
    # 모델이 망설인 작은 구멍(200px 미만, 알파 평균 0.15 이상)만 메운다.
    # 다리 사이·가지 사이처럼 모델이 분명히 배경이라고 본 구멍은 그대로 둔다.
    holes = ndi.binary_fill_holes(solid) & ~solid
    hl, hn = ndi.label(holes)
    if hn:
        idx = range(1, hn + 1)
        sizes = np.asarray(ndi.sum(holes, hl, idx))
        means = np.asarray(ndi.mean(alpha, hl, idx))
        small = np.isin(hl, [i + 1 for i in range(hn) if sizes[i] < 200 and means[i] >= 0.15])
        solid |= small
    # 고른 조각 둘레 2px 밖은 지우고, 안쪽 2px 너머는 완전히 불투명하게(반투명 얼룩 없음)
    alpha[~ndi.binary_dilation(solid, iterations=2)] = 0
    alpha[ndi.distance_transform_edt(solid) >= 2] = 1.0
    if a.get("purge_hue"):
        alpha = purge_edge_hue(rgb, alpha, *a["purge_hue"])
    if a.get("green_bottom"):
        # 발밑 풀(초록 > 빨강)만 벗긴다. 올리브빛 털(빨강 ≥ 초록)은 남는다. (depth, 원본 y부터)
        # 색상 범위로 지우면 너구리 배 아래처럼 초록빛이 비친 털까지 지워져서 이 기준을 쓴다(2026-11-03 비교).
        depth, y_from = a["green_bottom"]
        c = rgb.astype(np.int16)
        bad = (c[..., 1] > c[..., 0] + 2) & (c[..., 1] - c[..., 2] > 30)
        bad[: max(0, y_from - a["box"][1])] = False
        alpha = peel(alpha, bad, depth)
    # 반투명 가장자리 색 = 가까운 안쪽 색(풀색이 번지지 않게)
    rgbf = rgb.astype(np.float32)
    if a.get("decontam"):
        return decontaminate(rgbf, alpha, a.get("edge_px", 1.5))
    inside = alpha >= 0.999
    est = nearest_inside(rgbf, inside)
    out = np.where((alpha < 0.999)[..., None], est, rgbf)
    return out.astype(np.uint8), alpha


def purge_edge_hue(rgb: np.ndarray, alpha: np.ndarray, h_lo: float, h_hi: float, depth: int = 4) -> np.ndarray:
    """가장자리에 붙어 온 풀·노란 꽃 조각을 지운다(동물 도감용, 2026-11-03).

    색상(도, 0~360)이 h_lo~h_hi이고 채도가 있는 픽셀 중 바깥에서 한 겹씩 벗겨 들어가며 depth겹까지만 지운다.
    동물 몸 안쪽의 같은 색(예: 오리 새끼의 노랑)은 바깥과 이어지지 않으면 남는다.
    """
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)
    hue = hsv[..., 0].astype(np.float32) * 2
    bad = (hue >= h_lo) & (hue <= h_hi) & (hsv[..., 1] >= 90) & (hsv[..., 2] >= 120)  # 어두운 발굽·그림자는 남김
    return peel(alpha, bad, depth)


def peel(alpha: np.ndarray, bad: np.ndarray, depth: int) -> np.ndarray:
    """바깥과 맞닿은 bad 픽셀을 한 겹씩 depth겹까지 지우고, 가장 큰 덩어리만 남긴다."""
    alpha = alpha.copy()
    for _ in range(depth):
        outside = alpha <= 0.004
        rim = (alpha > 0.004) & ndi.binary_dilation(outside, structure=np.ones((3, 3)))
        hit = rim & bad
        if not hit.any():
            break
        alpha[hit] = 0
    keep = pick_components(alpha > 0.004, "largest", 60)
    return np.where(keep, alpha, 0).astype(np.float32)


def decontaminate(rgbf: np.ndarray, alpha: np.ndarray, edge_px: float = 1.5):
    """가장자리 1~2px의 배경 물듦(하늘색·풀색 테두리)을 없앤다(2026-11-03, 나무·동물 도감용).

    분할 모델이 불투명으로 본 맨 바깥 픽셀도 원본에서는 "그림 색 + 배경 색"이 섞여 있다. 그래서
    - 색: 맨 바깥 edge_px 안의 픽셀은 그보다 안쪽(깨끗한) 픽셀 중 가장 가까운 색으로 바꾼다.
    - 알파: 같은 자리의 바깥 배경 색(2px 밖의 원본 색)과 안쪽 색 사이에서 원본 색이 어디쯤인지로 정한다
      (카드 키와 같은 원리). 안쪽 색과 배경 색이 비슷하면(초록 잎 ↔ 풀밭) 알파는 그대로 둔다.
    안쪽 픽셀의 색은 원본 그대로다(색 보정 없음).
    """
    solid = alpha >= 0.5
    d_in = ndi.distance_transform_edt(solid)
    core = d_in > edge_px
    ring = (alpha > 0) & ~core
    f = nearest_inside(rgbf, core)
    clean_bg = ndi.distance_transform_edt(~solid) >= 3
    b = nearest_inside(rgbf, clean_bg)
    fb = f - b
    den = (fb * fb).sum(-1)
    a_proj = np.clip(((rgbf - b) * fb).sum(-1) / np.maximum(den, 1e-3), 0, 1)
    use = ring & (den > 28.0 ** 2)
    alpha = alpha.copy()
    alpha[use] = np.minimum(alpha[use], np.clip((a_proj[use] - 0.1) / 0.8, 0, 1))
    out = np.where(ring[..., None], f, rgbf)
    return out.astype(np.uint8), alpha


def lumakey(rgb: np.ndarray, dark: tuple[int, int, int]):
    lab = to_lab(rgb)
    l_dark = to_lab(np.array([[dark]], np.uint8))[0, 0, 0]
    l = lab[..., 0]
    l_light = np.percentile(l, 99)
    alpha = np.clip((l - l_dark - 4) / (l_light - l_dark - 8), 0, 1)
    solid = pick_components(alpha > 0.5, "all", 20)
    alpha[~ndi.binary_dilation(solid, iterations=2)] = 0
    rgbf = rgb.astype(np.float32)
    d = np.array(dark, np.float32)
    a3 = alpha[..., None]
    un = np.clip((rgbf - (1 - a3) * d) / np.maximum(a3, 1e-3), 0, 255)
    out = np.where(a3 > 0.02, un, rgbf)
    return out.astype(np.uint8), alpha


def patch_from_same_rows(img: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """mask 자리를 같은 높이(같은 빛)의 깨끗한 풀밭으로 덮는다(OpenCV seamlessClone).

    오른쪽으로 옮겨 가며 후보 자리를 찾고, 꽃·돌·그림자처럼 풀색이 아닌 픽셀이 가장 적은 곳을 고른다.
    """
    hsv = cv2.cvtColor(img, cv2.COLOR_RGB2HSV)
    h, s, v = hsv[..., 0].astype(int), hsv[..., 1].astype(int), hsv[..., 2].astype(int)
    grass = (h >= 26) & (h <= 46) & (s >= 120) & (v >= 140)
    ys, xs = np.nonzero(mask)
    best = None
    for dx in range(160, 900, 4):
        if xs.max() + dx >= img.shape[1]:
            break
        bad = 1.0 - grass[ys, xs + dx].mean()
        if best is None or bad < best[0] - 1e-4:
            best = (bad, dx)
    _, dx = best
    shifted = np.roll(img, -dx, axis=1)
    m8 = (ndi.binary_dilation(mask, iterations=6) * 255).astype(np.uint8)
    y0, y1 = ys.min(), ys.max()
    x0, x1 = xs.min(), xs.max()
    center = (int((x0 + x1) / 2), int((y0 + y1) / 2))
    # seamlessClone은 mask 경계 상자 가운데에 붙인다
    yy, xx = np.nonzero(m8)
    center = (int((xx.min() + xx.max()) / 2), int((yy.min() + yy.max()) / 2))
    res = cv2.seamlessClone(shifted, img, m8, center, cv2.NORMAL_CLONE)
    print(f"    풀밭 덮기: 오른쪽 {dx}px 자리의 풀밭(풀 아닌 픽셀 {best[0]*100:.1f}%)")
    return res


def trim(rgb: np.ndarray, alpha: np.ndarray, pad: int = 2):
    ys, xs = np.nonzero(alpha > 0.004)
    y0, y1 = max(ys.min() - pad, 0), min(ys.max() + 1 + pad, alpha.shape[0])
    x0, x1 = max(xs.min() - pad, 0), min(xs.max() + 1 + pad, alpha.shape[1])
    return rgb[y0:y1, x0:x1], alpha[y0:y1, x0:x1], (x0, y0)


def save(rgb: np.ndarray, alpha: np.ndarray | None, name: str) -> dict:
    """메타데이터 없이 PNG로 저장한다."""
    OUT.mkdir(parents=True, exist_ok=True)
    if alpha is None:
        im = Image.fromarray(rgb, "RGB")
    else:
        a8 = np.round(alpha * 255).astype(np.uint8)
        rgb = rgb.copy()
        rgb[a8 == 0] = 0  # 완전 투명 픽셀의 숨은 색을 지운다(용량·찌꺼기)
        im = Image.fromarray(np.dstack([rgb, a8]), "RGBA")
    png = OUT / f"GGF-{name}-v1.png"
    im.save(png, optimize=True)  # pnginfo를 주지 않으므로 텍스트·EXIF·ICC가 들어가지 않는다
    return dict(png=str(png.relative_to(ROOT)), size=list(im.size), png_bytes=png.stat().st_size)


# ---------------------------------------------------------------------------
def build(a: dict, made: dict) -> dict:
    print(f"- {a['id']} ({a.get('src', a.get('base'))}, {a['method']})")
    if a["method"] == "silhouette":
        base = made[a["base"]]
        alpha = base["alpha"]
        sil = np.empty(alpha.shape + (3,), np.uint8)
        sil[:] = LOCKED_BROWN
        q_rgb, q_a = made["LOCK-QMARK"]["rgb"], made["LOCK-QMARK"]["alpha"]
        # `?`는 원본 크기 그대로(늘리지 않음), 실루엣 무게중심보다 조금 위에 얹는다
        cy, cx = ndi.center_of_mass(alpha > 0.5)
        qh, qw = q_a.shape
        y0 = int(round(cy - qh * 0.62))
        x0 = int(round(cx - qw / 2))
        y0 = min(max(y0, 0), alpha.shape[0] - qh)
        x0 = min(max(x0, 0), alpha.shape[1] - qw)
        region = (slice(y0, y0 + qh), slice(x0, x0 + qw))
        qa = (q_a * (alpha[region] > 0.5))[..., None]  # 실루엣 밖으로 삐져나가지 않게
        sil[region] = np.round(q_rgb * qa + sil[region] * (1 - qa)).astype(np.uint8)
        info = save(sil, alpha, a["id"])
        info.update(method="silhouette", base=a["base"])
        made[a["id"]] = dict(rgb=sil, alpha=alpha)
        return info

    src = load_rgb(a["src"])
    x0, y0, x1, y1 = a["box"]
    crop = src[y0:y1, x0:x1].copy()
    if a["method"] == "scene":
        if "erase_poly" in a:
            mask = np.zeros(src.shape[:2], np.uint8)
            cv2.fillPoly(mask, [np.array(a["erase_poly"], np.int32)], 1)
            src2 = patch_from_same_rows(src, mask.astype(bool))
            crop = src2[y0:y1, x0:x1]
        info = save(crop, None, a["id"])
        made[a["id"]] = dict(rgb=crop, alpha=None)
        info.update(method="scene")
        return info
    for rx0, ry0, rx1, ry1 in a.get("retouch", []):
        # 아주 작은 자리만(엠블럼 모양 점) 주변 색으로 메운다
        m = np.zeros(crop.shape[:2], np.uint8)
        m[ry0 - y0:ry1 - y0, rx0 - x0:rx1 - x0] = 255
        crop = cv2.inpaint(crop, m, 3, cv2.INPAINT_TELEA)
    if a["method"] == "cardkey":
        # tol_bg: 카드 바탕의 밝은 번짐(가운데가 밝음)까지 바탕으로 보는 허용치. 상자는 위 "해금 완료" 띠를 빼고 자른다(2026-11-03 메인 조정)
        rgb, alpha = cardkey(crop, keep=a.get("keep", "all"), tol_bg=a.get("tol_bg", 5.0), edge_inside=a.get("edge_inside", False))
        if a.get("union_model"):
            # 흰 몸이 바탕과 비슷해 카드 키가 목 같은 곳을 바탕으로 잘못 지울 때: 분할 모델이 몸으로 본 곳을 더한다.
            # 더한 곳의 색은 원본 그대로(언믹싱 없음). 가장자리만 부드럽게.
            raw = model_alpha(crop, a["union_model"])
            body = ndi.binary_fill_holes(raw > 0.5) & ndi.binary_fill_holes(alpha > 0.5) | (raw > 0.85)
            body = ndi.binary_fill_holes(body | (alpha > 0.5))
            add = body & (alpha < 0.99)
            alpha = np.where(add, 1.0, alpha).astype(np.float32)
            rgb = np.where(add[..., None], crop, rgb)
            alpha = pick_components(alpha > 0.004, "largest", 60).astype(np.float32) * alpha
        if a.get("clear_top"):
            alpha[: a["clear_top"]] = 0
    elif a["method"] == "model":
        rgb, alpha = model_cut(crop, a)
    elif a["method"] == "lumakey":
        rgb, alpha = lumakey(crop, a["dark"])
    else:
        raise ValueError(a["method"])
    if a.get("erase"):
        # 손으로 정한 다각형(원본 좌표) 안을 지운다: 부엉이 발밑 나뭇가지, 흐린 잎처럼 모델이 함께 잡은 것.
        m = np.zeros(alpha.shape, np.uint8)
        for poly in a["erase"]:
            cv2.fillPoly(m, [np.array([(px - x0, py - y0) for px, py in poly], np.int32)], 1)
        m = m.astype(bool)
        if a.get("erase_greenish"):
            # 다각형 안에서도 풀색이 섞인 픽셀(초록 ≥ 빨강)만 지운다 → 노란 잎 모양 그대로, 곧은 자국 없음
            cr = crop.astype(np.int16)
            m &= cr[..., 1] >= cr[..., 0] - 4
        cut = np.where(m, 0.0, alpha).astype(np.float32)
        # 안쪽에 갇힌 구멍(잎의 밝은 반사)은 되돌린다. 바깥과 이어진 곳만 지운다.
        holes = ndi.binary_fill_holes(cut > 0.004) & (cut <= 0.004)
        alpha = np.where(holes, alpha, cut).astype(np.float32)
        alpha = pick_components(alpha > 0.004, "largest", 60).astype(np.float32) * alpha
    rgb, alpha, (ox, oy) = trim(rgb, alpha, pad=0 if a["id"].startswith("LOCK-QMARK") else 2)
    if a.get("mirror"):
        rgb, alpha = rgb[:, ::-1].copy(), alpha[:, ::-1].copy()
    info = save(rgb, alpha, a["id"])
    info.update(method=a["method"], model=a.get("model"), trimmed_origin=[int(x0 + ox), int(y0 + oy)])
    made[a["id"]] = dict(rgb=rgb, alpha=alpha)
    return info


def main(argv: list[str]) -> None:
    only = set(argv)
    made: dict = {}
    manifest = []
    for a in ASSETS:
        need = not only or a["id"] in only or a.get("base") in only
        # 실루엣은 바탕 그림과 `?`가 있어야 한다
        if not need and not any(b.get("base") == a["id"] and (b["id"] in only) for b in ASSETS) and a["id"] != "LOCK-QMARK":
            continue
        info = build(a, made)
        entry = {k: v for k, v in a.items() if k in ("id", "cand", "title", "src", "box", "method", "model", "base", "use", "mirror", "retouch")}
        entry.update(info)
        manifest.append(entry)
        print(f"    → {info['png']} {info['size'][0]}×{info['size'][1]}, {info['png_bytes'] / 1024:.0f}KB")
    if not only:
        (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
        total = sum(m["png_bytes"] for m in manifest)
        print(f"PNG 합계 {total / 1024 / 1024:.2f}MB")


if __name__ == "__main__":
    main(sys.argv[1:])
