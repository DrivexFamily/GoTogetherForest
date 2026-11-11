#!/usr/bin/env python3
"""같이가숲 광고 영상 60초(가로 1920×1080, 30fps) 만들기 — 결정 #47, 대본 docs/pt/ad-script.md.

재료
- 일러스트: GPT 납품 main `design/illustrations/exports/2026-11-10/ad/`(S1~S5, friend-forest, SCREEN-CORNERS.json).
  작업 브랜치에는 없으므로 실행 전에 한 폴더로 꺼내 `--art`로 넘긴다:
    for f in S1 S2 S3 S4 S5 friend-forest; do git show origin/main:design/illustrations/exports/2026-11-10/ad/$f.png > ART/$f.png; done
    git show origin/main:design/illustrations/exports/2026-11-10/ad/SCREEN-CORNERS.json > ART/corners.json
- 앱 화면: 실제 앱 캡처(`design/screens/app/`, `design/screens/phone/`). 초록 화면 자리에 원근 맞춰 끼운다.
- 배경음: `design/audio/ggf_ad_bgm_60.wav`(tools/media/make_bgm.py, 정확히 60초)
- 글꼴: Pretendard(SIL OFL 1.1) — `--font-dir`에 Pretendard-Bold.otf, Pretendard-SemiBold.otf

움직임은 정지 그림을 천천히 밀고 당기는 것(켄 번스)과 장면 사이 겹쳐 바꾸기뿐이다.
실행: python3 tools/media/make_ad.py --art ART --font-dir FONTS --out design/video/ggf_ad_60.mp4
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from dataclasses import dataclass, field

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1920, 1080, 30
DUR = 60.0
XF = 0.6  # 장면 겹침(초)
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def rel(*p):
    return os.path.join(ROOT, *p)


# ---------------- 그림 준비 ----------------

def perspective_coeffs(dst, src):
    """PIL PERSPECTIVE용: 출력 좌표(dst) → 입력 좌표(src)"""
    a = []
    for (x, y), (u, v) in zip(dst, src):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y])
    A = np.array(a, dtype=float)
    b = np.array([c for uv in src for c in uv], dtype=float)
    return np.linalg.solve(A, b).tolist()


def cover(img: Image.Image, w: int, h: int, box=None) -> Image.Image:
    """비율을 지키며 w×h를 꽉 채우게 잘라 맞춘다(필요하면 먼저 box로 자름)"""
    if box:
        img = img.crop(box)
    r = max(w / img.width, h / img.height)
    s = img.resize((max(w, round(img.width * r)), max(h, round(img.height * r))), Image.LANCZOS)
    x = (s.width - w) // 2
    y = (s.height - h) // 2
    return s.crop((x, y, x + w, y + h))


def contain(img: Image.Image, w: int, h: int, fill=(14, 15, 17)) -> Image.Image:
    """비율을 지키며 w×h 안에 통째로 넣고 남는 곳은 짙은 중립색(잘리거나 늘어나지 않게)"""
    r = min(w / img.width, h / img.height)
    s = img.resize((max(1, round(img.width * r)), max(1, round(img.height * r))), Image.LANCZOS)
    out = Image.new("RGB", (w, h), fill)
    out.paste(s, ((w - s.width) // 2, (h - s.height) // 2))
    return out


def put_on_screen(scene: Image.Image, corners, content: Image.Image, fit: str = "cover") -> Image.Image:
    """초록 화면 자리에 앱 화면을 원근 맞춰 넣는다. 화면을 가린 손가락은 그대로 남긴다."""
    tl, tr, br, bl = corners
    cw = int(round(max(np.hypot(tr[0] - tl[0], tr[1] - tl[1]), np.hypot(br[0] - bl[0], br[1] - bl[1]))))
    ch = int(round(max(np.hypot(bl[0] - tl[0], bl[1] - tl[1]), np.hypot(br[0] - tr[0], br[1] - tr[1]))))
    # cover: 화면을 꽉 채움(앱 한 화면). contain: 실제 Pleos 전체 화면처럼 운전 정보·내비를 자르지 않음(GPT 요청 11-11)
    src_img = contain(content, cw * 2, ch * 2) if fit == "contain" else cover(content, cw * 2, ch * 2)
    coeffs = perspective_coeffs([tl, tr, br, bl], [(0, 0), (src_img.width, 0), (src_img.width, src_img.height), (0, src_img.height)])
    warped = src_img.transform(scene.size, Image.PERSPECTIVE, coeffs, Image.BICUBIC)
    # 초록 정도로 매트(부드러운 경계)
    s = np.asarray(scene.convert("RGB")).astype(np.float32)
    green = s[..., 1] - np.maximum(s[..., 0], s[..., 2])
    matte = np.clip((green - 50) / 70, 0, 1)
    poly = Image.new("L", scene.size, 0)
    ImageDraw.Draw(poly).polygon([tuple(c) for c in corners], fill=255)
    poly = poly.filter(ImageFilter.MaxFilter(7))
    matte *= np.asarray(poly).astype(np.float32) / 255
    matte = np.asarray(Image.fromarray((matte * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))).astype(np.float32) / 255
    w = np.asarray(warped.convert("RGB")).astype(np.float32)
    # 경계의 초록 번짐 덜기
    edge = (matte > 0.02) & (matte < 0.98)
    s2 = s.copy()
    s2[..., 1] = np.where(edge, np.minimum(s[..., 1], np.maximum(s[..., 0], s[..., 2]) + 10), s[..., 1])
    out = s2 * (1 - matte[..., None]) + w * matte[..., None]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def app_slide(bg: Image.Image, capture: Image.Image, scale=0.70) -> Image.Image:
    """흐린 장면 위에 앱 화면을 둥근 모서리·그림자와 함께(차 화면처럼)"""
    base = bg.convert("RGB").resize((W, H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(22))
    base = Image.blend(base, Image.new("RGB", (W, H), (18, 24, 20)), 0.45)
    ch = int(H * scale)
    cw = int(capture.width * ch / capture.height)
    if cw > W * 0.86:
        cw = int(W * 0.86)
        ch = int(capture.height * cw / capture.width)
    cap = capture.convert("RGB").resize((cw, ch), Image.LANCZOS)
    bezel = 14
    x = (W - cw) // 2
    y = (H - ch) // 2 - 70  # 아래 자막 자리를 비운다
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((x - bezel + 10, y - bezel + 24, x + cw + bezel + 10, y + ch + bezel + 24), 40, fill=150)
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    base.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), shadow)
    frame = Image.new("L", (W, H), 0)
    ImageDraw.Draw(frame).rounded_rectangle((x - bezel, y - bezel, x + cw + bezel, y + ch + bezel), 40, fill=255)
    base.paste(Image.new("RGB", (W, H), (22, 22, 24)), (0, 0), frame)
    m = Image.new("L", (cw, ch), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, cw - 1, ch - 1), 28, fill=255)
    base.paste(cap, (x, y), m)
    return base


# ---------------- 글자 ----------------

class Fonts:
    def __init__(self, d):
        self.bold = lambda s: ImageFont.truetype(os.path.join(d, "Pretendard-Bold.otf"), s)
        self.semi = lambda s: ImageFont.truetype(os.path.join(d, "Pretendard-SemiBold.otf"), s)
        self.black = lambda s: ImageFont.truetype(os.path.join(d, "Pretendard-ExtraBold.otf"), s)
        self.medium = lambda s: ImageFont.truetype(os.path.join(d, "Pretendard-Medium.otf"), s)


HEART = "\u2665"
HEART_RGB = (236, 92, 104)


def text_layer(text, font, y_center, size=(W, H), fill=(255, 255, 255), shadow=True) -> Image.Image:
    # 글꼴(Pretendard)에 그림 문자 ❤️가 없으므로 ♥로 바꿔 부드러운 빨강으로 그린다(빈 네모 방지)
    text = text.replace("\u2764\ufe0f", HEART).replace("\u2764", HEART).replace("\ufe0f", "")
    if HEART in text:
        return _text_layer_heart(text, font, y_center, size, fill, shadow)
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (size[0] - tw) // 2 - bbox[0]
    y = y_center - th // 2 - bbox[1]
    if shadow:
        sh = Image.new("RGBA", size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).text((x, y + 3), text, font=font, fill=(0, 0, 0, 170))
        layer = Image.alpha_composite(layer, sh.filter(ImageFilter.GaussianBlur(6)))
        d = ImageDraw.Draw(layer)
    d.text((x, y), text, font=font, fill=fill + (255,))
    return layer


def _text_layer_heart(text, font, y_center, size, fill, shadow) -> Image.Image:
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x0 = (size[0] - tw) // 2 - bbox[0]
    y = y_center - th // 2 - bbox[1]
    if shadow:
        sh = Image.new("RGBA", size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).text((x0, y + 3), text, font=font, fill=(0, 0, 0, 170))
        layer = Image.alpha_composite(layer, sh.filter(ImageFilter.GaussianBlur(6)))
        d = ImageDraw.Draw(layer)
    x = x0
    for part in text.split(HEART):
        if part:
            d.text((x, y), part, font=font, fill=fill + (255,))
            x += d.textlength(part, font=font)
    # 하트 자리 다시 계산해 색칠
    x = x0
    pieces = text.split(HEART)
    for i, part in enumerate(pieces):
        x += d.textlength(part, font=font)
        if i < len(pieces) - 1:
            d.text((x, y), HEART, font=font, fill=HEART_RGB + (255,))
            x += d.textlength(HEART, font=font)
    return layer


def bottom_band() -> Image.Image:
    a = np.zeros((H, W), np.float32)
    ys = np.arange(H)
    a[:] = np.clip((ys - H * 0.62) / (H * 0.38), 0, 1)[:, None] ** 1.4 * 0.55
    band = np.zeros((H, W, 4), np.uint8)
    band[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(band, "RGBA")


def concept_badge(f: Fonts) -> Image.Image:
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    font = f.semi(26)
    t = "콘셉트 장면"
    b = d.textbbox((0, 0), t, font=font)
    pw, ph = b[2] - b[0] + 36, b[3] - b[1] + 20
    x, y = W - pw - 40, 40
    d.rounded_rectangle((x, y, x + pw, y + ph), ph // 2, fill=(0, 0, 0, 120))
    d.text((x + 18 - b[0], y + 10 - b[1]), t, font=font, fill=(255, 255, 255, 235))
    return layer


# ---------------- 장면 ----------------

class VideoSource:
    """영상 조각(Sora 등)을 앞에서부터 차례로 읽는다. 조각이 장면보다 짧으면 마지막 화면에서 멈춘다."""

    def __init__(self, path: str):
        self.path = path
        self.proc = None
        self.index = -1
        self.last = None

    def frame(self, t_rel: float) -> Image.Image:
        want = max(0, int(t_rel * FPS))
        if self.proc is None:
            self.proc = subprocess.Popen(
                ["ffmpeg", "-loglevel", "error", "-i", self.path, "-vf",
                 f"fps={FPS},scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H}", "-f", "rawvideo",
                 "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
        while self.index < want:
            buf = self.proc.stdout.read(W * H * 3)
            if len(buf) < W * H * 3:
                break  # 조각 끝: 마지막 화면 유지
            self.last = Image.frombytes("RGB", (W, H), buf)
            self.index += 1
        return self.last


@dataclass
class Shot:
    img: Image.Image
    start: float
    end: float
    zoom: tuple = (1.0, 1.06)
    focus: tuple = (0.5, 0.5)  # 확대 중심(0~1)
    pan: tuple = (0.0, 0.0)  # 끝에서 이동(화면 비율)
    concept: bool = False
    subs: list = field(default_factory=list)  # (시작, 끝, 글자, 종류)
    band: bool = True
    video: VideoSource | None = None


def kb_frame(shot: Shot, t: float) -> Image.Image:
    if shot.video is not None:
        fr = shot.video.frame(t - shot.start)
        if fr is not None:
            return fr
    p = (t - shot.start) / (shot.end - shot.start)
    p = min(max(p, 0.0), 1.0)
    e = p * p * (3 - 2 * p) * 0.35 + p * 0.65  # 살짝 부드럽게
    z = shot.zoom[0] + (shot.zoom[1] - shot.zoom[0]) * e
    cw, ch = W / z, H / z
    cx = shot.focus[0] * W + shot.pan[0] * W * e
    cy = shot.focus[1] * H + shot.pan[1] * H * e
    x0 = min(max(cx - cw / 2, 0), W - cw)
    y0 = min(max(cy - ch / 2, 0), H - ch)
    return shot.img.resize((W, H), Image.BICUBIC, box=(x0, y0, x0 + cw, y0 + ch))


def build(art, font_dir):
    f = Fonts(font_dir)
    corners = {sc["file"]: {s["id"]: s["delivery"]["corners_tl_tr_br_bl"] for s in sc["screens"]}
               for sc in json.load(open(os.path.join(art, "corners.json")))["scenes"]}
    load = lambda n: Image.open(os.path.join(art, n)).convert("RGB")
    cap = lambda *p: Image.open(rel("design", "screens", *p)).convert("RGB")

    s1, s2, s3, s4, s5, ff = (load(n) for n in ("S1.png", "S2.png", "S3.png", "S4.png", "S5.png", "friend-forest.png"))
    tab = cap("phone", "phone-forest-tablet.png")
    phone = cap("phone", "phone-forest-phone.png")
    m2 = cap("app", "forest-m2-25-trees-full.png")
    m2_map = m2.crop((40, 130, 1230, 1140))
    s3c = put_on_screen(s3, corners["S3.png"]["tablet"], tab)
    s4c = put_on_screen(s4, corners["S4.png"]["left-boy-tablet"], m2_map)
    s4c = put_on_screen(s4c, corners["S4.png"]["center-main-girl-tablet"], tab)
    s4c = put_on_screen(s4c, corners["S4.png"]["right-friend-phone"], phone)
    s5c = put_on_screen(s5, corners["S5.png"]["boy-phone"], phone)

    drive = app_slide(s1, cap("app", "journey-driving-40-full.png"))
    group = app_slide(s2, cap("app", "atlas-group-celebration-full.png"))
    arrive = app_slide(s2, cap("app", "arrival-result-full.png"))
    island = cap("app", "forest-island-full.png").crop((40, 130, 1230, 1140))
    end_bg = cover(island, W, H).filter(ImageFilter.GaussianBlur(6))
    end_bg = Image.blend(end_bg, Image.new("RGB", (W, H), (255, 255, 255)), 0.30)
    # 글자 뒤 부드러운 흰 판
    panel = Image.new("L", (W, H), 0)
    ImageDraw.Draw(panel).rounded_rectangle((W // 2 - 620, 330, W // 2 + 620, 790), 60, fill=220)
    panel = panel.filter(ImageFilter.GaussianBlur(18))
    end_bg.paste(Image.new("RGB", (W, H), (250, 252, 246)), (0, 0), panel)

    S, T = "sub", "talk"
    shots = [
        Shot(s1, 0.0, 5.4, (1.0, 1.07), (0.68, 0.45), subs=[(0.9, 5.0, "“아빠, 언제 도착해?”", T)]),
        Shot(drive, 5.4, 13.2, (1.0, 1.035), subs=[(5.9, 9.3, "이제 차 화면이 대신 답해요", S), (9.5, 12.9, "운전 중엔 멈춘 그림으로, 안전하게", S)]),
        Shot(s2, 13.2, 16.8, (1.04, 1.04), (0.45, 0.5), pan=(0.04, 0.0), subs=[(13.6, 16.6, "창밖으로 지나온 곳들은", S)]),
        Shot(group, 16.8, 20.8, (1.0, 1.03), subs=[(17.1, 20.6, "아이의 여행 도감에 차곡차곡", S)]),
        Shot(arrive, 20.8, 25.6, (1.0, 1.03), subs=[(21.1, 25.4, "부드럽게 달린 만큼, 아이의 숲이 자라요", S)]),
        Shot(s3c, 25.6, 35.0, (1.0, 1.10), (0.56, 0.62), concept=True,
             subs=[(26.1, 30.2, "집에서도, 아이가 가꾸는 숲", S), (30.4, 34.8, "오늘 심은 나무를 매일 보러 가요", S)]),
        Shot(s4c, 35.0, 40.6, (1.08, 1.0), (0.5, 0.55), concept=True, subs=[(35.4, 40.4, "“우리 숲에 놀러 와!”", T)]),
        Shot(ff, 40.6, 45.2, (1.0, 1.06), (0.42, 0.62), concept=True, subs=[(40.9, 45.0, "친구 숲에도 놀러 가요", S)]),
        Shot(s5c, 45.2, 52.0, (1.0, 1.08), (0.62, 0.5), concept=True,
             subs=[(45.6, 48.6, "“엄마, 우리 차엔 이거 없어?”", T), (48.8, 51.8, "Pleos 차에서 시작한 숲이, 친구에게로", S)]),
        Shot(end_bg, 52.0, 60.0, (1.0, 1.04), band=False),
    ]
    return f, shots


def end_card() -> Image.Image:
    island = Image.open(rel("design", "screens", "app", "forest-island-full.png")).convert("RGB").crop((40, 130, 1230, 1140))
    bg = cover(island, W, H).filter(ImageFilter.GaussianBlur(6))
    bg = Image.blend(bg, Image.new("RGB", (W, H), (255, 255, 255)), 0.30)
    panel = Image.new("L", (W, H), 0)
    ImageDraw.Draw(panel).rounded_rectangle((W // 2 - 620, 330, W // 2 + 620, 790), 60, fill=220)
    panel = panel.filter(ImageFilter.GaussianBlur(18))
    bg.paste(Image.new("RGB", (W, H), (250, 252, 246)), (0, 0), panel)
    return bg


def build_from_json(art, font_dir, shots_path):
    """GPT가 넘긴 SHOTS.json으로 장면 목록을 만든다(드라마형 광고, claude-drama-20261111-28).

    한 장면: {"img": "D01.png" | "app:<design/screens 아래 파일 이름>" | "end",
              "start", "end", "zoom": [a, b], "focus": [x, y], "pan": [dx, dy], "concept": bool,
              "screens": {"<SCREEN-CORNERS의 화면 id>": "app:<파일 이름>"},
              "bg": "D01.png"(앱 화면 장면의 흐린 배경, 없으면 바로 앞 그림),
              "subs": [[시작, 끝, "글자", "talk"|"sub"]]}  — 시각은 모두 영상 기준 절대 초.
    """
    f = Fonts(font_dir)
    spec = json.load(open(shots_path))
    items = spec["shots"] if isinstance(spec, dict) else spec
    corners_path = next((p for p in (os.path.join(art, "SCREEN-CORNERS.json"), os.path.join(art, "corners.json")) if os.path.exists(p)), None)
    corners = {}
    if corners_path:
        for sc in json.load(open(corners_path))["scenes"]:
            corners[sc["file"]] = {sc2["id"]: sc2["delivery"]["corners_tl_tr_br_bl"] for sc2 in sc.get("screens", [])}

    def capture(name: str) -> Image.Image:
        name = name.split(":", 1)[1] if name.startswith("app:") else name
        if not name.endswith(".png"):
            name += ".png"
        for sub in ("app", "phone"):
            p = rel("design", "screens", sub, name)
            if os.path.exists(p):
                return Image.open(p).convert("RGB")
        raise FileNotFoundError(name)

    def forest_map(img: Image.Image) -> Image.Image:
        return img.crop((40, 130, 1230, 1140)) if img.size == (1682, 1210) else img

    shots, last_art = [], None
    for it in items:
        src = it["img"]
        video = None
        clip = it.get("clip")
        if clip and os.path.exists(os.path.join(art, clip)):
            # 움직이는 조각이 있으면 그것을 쓰고, 없으면 아래 정지 그림(합성 포함)으로
            video = VideoSource(os.path.join(art, clip))
        if src == "end":
            img = end_card()
        elif src.startswith("app:"):
            bg_name = it.get("bg")
            bg = Image.open(os.path.join(art, bg_name)).convert("RGB") if bg_name else (last_art or end_card())
            img = app_slide(bg, capture(src))
        else:
            img = Image.open(os.path.join(art, src)).convert("RGB")
            for sid, content in (it.get("screens") or {}).items():
                # 값은 "app:이름" 또는 {"src": "app:이름", "fit": "contain"|"cover"}. Pleos 전체 화면은 기본 contain
                fit = None
                if isinstance(content, dict):
                    fit = content.get("fit")
                    content = content["src"]
                cap_img = capture(content)
                if "forest-" in content and "phone-" not in content:
                    cap_img = forest_map(cap_img)
                fit = fit or ("contain" if "pleos-wide" in content else "cover")
                img = put_on_screen(img, corners[src][sid], cap_img, fit)
            last_art = img
        shots.append(Shot(
            img, float(it["start"]), float(it["end"]), tuple(it.get("zoom", (1.0, 1.05))), tuple(it.get("focus", (0.5, 0.5))),
            tuple(it.get("pan", (0.0, 0.0))), bool(it.get("concept", False)),
            [tuple(x) for x in it.get("subs", [])], band=src != "end", video=video,
        ))
    if items[-1]["img"] != "end":
        start = shots[-1].end
        shots.append(Shot(end_card(), start, start + 7.0, (1.0, 1.04), band=False))
    return f, shots


def render(art, font_dir, out, audio, shots_path=None, dur=None, style="bold"):
    global DUR
    f, shots = build_from_json(art, font_dir, shots_path) if shots_path else build(art, font_dir)
    DUR = float(dur) if dur else shots[-1].end
    end_t = shots[-1].start
    band = bottom_band()
    badge = concept_badge(f)
    quiet = style == "quiet"  # 자동차 가족 광고처럼: 작고 조용한 1인칭 자막, 옅은 아래 그늘
    if quiet:
        arr = np.asarray(band).copy()
        arr[..., 3] = (arr[..., 3] * 0.55).astype(np.uint8)
        band = Image.fromarray(arr, "RGBA")
    # quiet: 1080p 기준 36px Medium(참고 광고처럼 작은 흰 글씨, GPT 요청 11-11)
    sub_font, talk_font = (f.medium(36), f.medium(36)) if quiet else (f.bold(56), f.bold(66))
    cache = {}

    def label_layer(text):
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        d.text((64, H - 92), text, font=f.semi(28), fill=(255, 255, 255, 220))
        return layer

    def sub_img(text, kind):
        key = (text, kind)
        if key not in cache:
            if kind == "label":
                cache[key] = label_layer(text)
            else:
                y = (1000 if quiet else 958) if kind == "sub" else (1000 if quiet else 950)
                cache[key] = text_layer(text, talk_font if kind == "talk" else sub_font, y)
        return cache[key]

    title = text_layer("같이가숲", f.black(150), 470, fill=(31, 92, 52), shadow=False)
    line = text_layer("함께 달린 길이, 아이의 숲이 됩니다", f.bold(58), 625, fill=(40, 52, 44), shadow=False)
    small = text_layer("Pleos Connect 앱  ·  차량용 앱 개발 공모전 출품작", f.semi(30), 735, fill=(70, 84, 74), shadow=False)

    def shot_frame(sh: Shot, t: float) -> Image.Image:
        im = kb_frame(sh, t).convert("RGBA")
        if sh.band:
            im = Image.alpha_composite(im, band)
        if sh.concept:
            im = Image.alpha_composite(im, badge)
        for a, b, text, kind in sh.subs:
            if a - 0.35 <= t <= b + 0.35:
                al = min(1.0, (t - (a - 0.35)) / 0.35, ((b + 0.35) - t) / 0.35)
                layer = sub_img(text, kind)
                if al < 1:
                    arr = np.asarray(layer).copy()
                    arr[..., 3] = (arr[..., 3] * al).astype(np.uint8)
                    layer = Image.fromarray(arr, "RGBA")
                im = Image.alpha_composite(im, layer)
        if sh is shots[-1]:
            for layer, appear in ((title, end_t + 0.6), (line, end_t + 1.4), (small, end_t + 2.2)):
                al = min(1.0, max(0.0, (t - appear) / 0.8))
                if al > 0:
                    arr = np.asarray(layer).copy()
                    arr[..., 3] = (arr[..., 3] * al).astype(np.uint8)
                    im = Image.alpha_composite(im, Image.fromarray(arr, "RGBA"))
        return im.convert("RGB")

    os.makedirs(os.path.dirname(out), exist_ok=True)
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
           "-i", "-", "-i", audio, "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-preset", "slow", "-crf", "19",
           "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-c:a", "aac", "-b:a", "192k", "-t", str(DUR), out]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    n = int(DUR * FPS)
    for i in range(n):
        t = i / FPS
        cur = next(s for s in shots if s.start <= t < s.end or s is shots[-1])
        idx = shots.index(cur)
        frame = shot_frame(cur, t)
        # 다음 장면과 겹쳐 바꾸기(장면 끝 XF초)
        if idx + 1 < len(shots) and t > cur.end - XF:
            nxt = shots[idx + 1]
            a = (t - (cur.end - XF)) / XF
            frame = Image.blend(frame, shot_frame(nxt, max(t, nxt.start)), a * a * (3 - 2 * a))
        # 처음 0.5초 밝아지기, 마지막 0.8초 어두워지기
        if t < 0.5:
            frame = Image.blend(Image.new("RGB", (W, H)), frame, t / 0.5)
        if t > DUR - 0.8:
            frame = Image.blend(frame, Image.new("RGB", (W, H)), (t - (DUR - 0.8)) / 0.8)
        proc.stdin.write(frame.tobytes())
        if i % 300 == 0:
            print(f"{t:.0f}s", flush=True)
    proc.stdin.close()
    return proc.wait()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--art", required=True)
    ap.add_argument("--font-dir", required=True)
    ap.add_argument("--out", default=rel("design", "video", "ggf_ad_60.mp4"))
    ap.add_argument("--audio", default=rel("design", "audio", "ggf_ad_bgm_60.wav"))
    ap.add_argument("--shots", help="GPT SHOTS.json(드라마형). 없으면 60초 첫 편집본")
    ap.add_argument("--dur", type=float, help="영상 길이(초). 없으면 마지막 장면 끝")
    ap.add_argument("--style", choices=["bold", "quiet"], default="bold", help="quiet: 작고 조용한 1인칭 자막(감동판)")
    a = ap.parse_args()
    return render(a.art, a.font_dir, a.out, a.audio, a.shots, a.dur, a.style)


if __name__ == "__main__":
    sys.exit(main())
