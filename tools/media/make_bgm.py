#!/usr/bin/env python3
"""같이가숲 BGM 두 곡 — 직접 작곡·합성(샘플·사운드폰트·외부 음원 없음, 결정 #47).

1) 광고용 "함께 달린 길" (60초, D장조 84 BPM)
   자동차 가족 광고에 흔한 결: 잔잔한 피아노 아르페지오로 시작 → 피아노 선율 + 부드러운 현악 → 고조(가벼운 박동·글로켄)
   → 끝 장면에서 풀리며 마무리. 특정 광고 음악의 선율을 쓰지 않은 새 곡.
2) 앱 숲 꾸미기용 "숲속 오후" (끊김 없는 고리, F장조 90 BPM 스윙)
   느긋한 생활 시뮬레이션 게임 배경음 같은 결: 둥근 일렉트릭 피아노 재즈 화음(maj7·9th) + 휘파람 같은 부드러운 선율
   + 둥근 베이스 + 브러시. 딱딱한 타악기 없이 부드럽게. 특정 게임 음악의 선율을 쓰지 않은 새 곡.

출력(design/audio/):
  ggf_ad_bgm_60.mp3 / .wav   광고용(정확히 60초)
  ggf_forest_bgm_loop.ogg    앱용 고리
실행: python3 tools/media/make_bgm.py  (numpy, scipy, ffmpeg)
"""
from __future__ import annotations

import os
import subprocess
import sys
import wave

import numpy as np
from scipy.signal import butter, sosfilt

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "design", "audio")
RNG = np.random.default_rng(20261110)


def hz(m: float) -> float:
    return 440.0 * 2 ** ((m - 69) / 12)


def lp(x, fc, order=2):
    return sosfilt(butter(order, fc, "low", fs=SR, output="sos"), x, axis=0)


def hp(x, fc, order=2):
    return sosfilt(butter(order, fc, "high", fs=SR, output="sos"), x, axis=0)


def bp(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], "band", fs=SR, output="sos"), x, axis=0)


class Track:
    def __init__(self, seconds: float):
        self.x = np.zeros((int(SR * seconds), 2))

    def add(self, sig, at, pan=0.0, gain=1.0):
        i = int(round(at * SR))
        if i >= len(self.x) or i < 0:
            return
        sig = np.asarray(sig)
        if sig.ndim == 1:
            l, r = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
            sig = np.stack([sig * l, sig * r], axis=1)
        j = min(len(self.x), i + len(sig))
        self.x[i:j] += gain * sig[: j - i]


# ---------------- 악기 ----------------

def piano(m, dur, vel=0.7, pedal=0.0):
    """덧셈 합성 피아노: 줄 2개(살짝 어긋난 음), 비조화 배음, 두 단계 감쇠, 해머 소리."""
    f = hz(m)
    ring = dur + pedal
    n = int(SR * min(ring + 0.5, 6.0))
    t = np.arange(n) / SR
    t1 = float(np.clip(4.0 * (262 / f) ** 0.55, 0.8, 7.0))
    bright = 0.55 + 0.45 * vel
    nmax = int(min(16, 7000 / f))
    y = np.zeros(n)
    B = 0.00025 * (f / 262) ** 0.5
    for k in range(1, nmax + 1):
        fk = k * f * np.sqrt(1 + B * k * k)
        a = (1 / k ** 1.1) * np.exp(-(k - 1) * (1 - bright) * 0.55)
        tk = t1 / (1 + 0.45 * (k - 1))
        e = 0.55 * np.exp(-t / (tk * 0.18)) + 0.45 * np.exp(-t / tk)
        for det in (-0.6, 0.6):  # 센트
            ph = RNG.uniform(0, 2 * np.pi)
            y += 0.5 * a * e * np.sin(2 * np.pi * fk * (1 + det / 1731) * t + ph)
    # 해머
    hn = int(SR * 0.02)
    ham = bp(RNG.normal(0, 1, hn), min(f * 2, 3000), min(f * 8, 9000)) * np.exp(-np.arange(hn) / (SR * 0.004))
    y[:hn] += 0.08 * vel * ham
    y *= np.clip(t / 0.003, 0, 1)
    # 건반에서 손을 떼면(페달 포함) 댐퍼
    y *= np.where(t < ring, 1.0, np.exp(-(t - ring) / 0.18))
    return 0.16 * vel * y


def strings(midis, dur, vel=0.5, attack=1.2, release=1.4):
    """현악 합주 패드: 음마다 살짝 어긋난 톱니파 5개 + 비브라토 → 저역통과."""
    n = int(SR * (dur + release + 0.2))
    t = np.arange(n) / SR
    out = np.zeros(n)
    for m in midis:
        f = hz(m)
        for v in range(5):
            det = (v - 2) * 0.0025
            vib = 1 + 0.0035 * np.sin(2 * np.pi * (4.8 + 0.3 * v) * t + RNG.uniform(0, 6.28))
            ph = np.cumsum(f * (1 + det) * vib) / SR + RNG.uniform(0, 1)
            out += 2 * (ph % 1.0) - 1
    out = lp(hp(out, 180), 1900, 2)
    env = np.clip(t / attack, 0, 1) ** 1.5 * np.where(t < dur, 1.0, np.exp(-(t - dur) / (release / 3)))
    return 0.018 * vel * out * env / max(1, len(midis)) ** 0.3


def glock(m, vel=0.6):
    n = int(SR * 2.5)
    t = np.arange(n) / SR
    f = hz(m)
    y = np.sin(2 * np.pi * f * t) * np.exp(-t / 1.1) + 0.2 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / 0.2)
    return 0.07 * vel * y * np.clip(t / 0.001, 0, 1)


def sub_bass(m, dur, vel=0.6):
    n = int(SR * (dur + 0.4))
    t = np.arange(n) / SR
    f = hz(m)
    y = np.sin(2 * np.pi * f * t) + 0.25 * np.sin(4 * np.pi * f * t) + 0.08 * np.sin(6 * np.pi * f * t)
    env = np.clip(t / 0.02, 0, 1) * np.exp(-t / 2.5) * np.where(t < dur, 1, np.exp(-(t - dur) / 0.08))
    return 0.22 * vel * y * env


def soft_kick(vel=0.5):
    n = int(SR * 0.4)
    t = np.arange(n) / SR
    f = 48 + 40 * np.exp(-t / 0.04)
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.16) * np.clip(t / 0.004, 0, 1)
    return 0.25 * vel * lp(y, 400)


def rhodes(m, dur, vel=0.5):
    """FM 일렉트릭 피아노(둥근 소리): 반송파·변조파 1:1, 변조 지수가 빨리 줄어듦 + 짧은 쇳소리."""
    f = hz(m)
    n = int(SR * min(dur + 0.8, 4.0))
    t = np.arange(n) / SR
    idx = (1.1 * vel + 0.3) * np.exp(-t / 0.35) + 0.25
    y = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * t))
    y += 0.12 * vel * np.sin(2 * np.pi * f * 7.0 * t) * np.exp(-t / 0.03)
    env = np.clip(t / 0.004, 0, 1) * np.exp(-t / 2.2) * np.where(t < dur, 1, np.exp(-(t - dur) / 0.25))
    y = y * env
    trem = 0.5 + 0.5 * np.sin(2 * np.pi * 3.2 * t)  # 좌우로 천천히 흔들림
    stereo = np.stack([y * (0.75 + 0.25 * trem), y * (1.0 - 0.25 * trem)], axis=1)
    return 0.09 * vel * stereo


def whistle(m, dur, vel=0.6):
    """휘파람·오카리나 같은 부드러운 선율: 사인 + 약한 배음 + 숨소리, 늦게 들어오는 비브라토."""
    f = hz(m)
    n = int(SR * (dur + 0.25))
    t = np.arange(n) / SR
    vib = 1 + 0.004 * np.clip((t - 0.2) / 0.3, 0, 1) * np.sin(2 * np.pi * 5.2 * t)
    ph = 2 * np.pi * np.cumsum(f * vib) / SR
    y = np.sin(ph) + 0.12 * np.sin(2 * ph) + 0.04 * np.sin(3 * ph)
    breath = bp(RNG.normal(0, 1, n), f * 0.9, min(f * 3, 8000)) * 0.05
    env = np.clip(t / 0.06, 0, 1) * np.where(t < dur, 1, np.exp(-(t - dur) / 0.07))
    return 0.15 * vel * (y + breath) * env


def brush(vel=0.5, length=0.18):
    n = int(SR * length)
    t = np.arange(n) / SR
    y = bp(RNG.normal(0, 1, n), 1800, 7000) * np.clip(t / 0.03, 0, 1) * np.exp(-t / (length / 3))
    return 0.05 * vel * y


# ---------------- 마무리 처리 ----------------

def reverb(x, seconds=2.4, wet=0.25, damp=5000):
    n = int(SR * seconds)
    t = np.arange(n) / SR
    ir = RNG.normal(0, 1, (n, 2)) * np.exp(-t / (seconds / 6))[:, None]
    ir = lp(ir, damp)
    ir[: int(SR * 0.018)] = 0
    ir /= np.sqrt((ir ** 2).sum(axis=0))
    size = 1 << int(np.ceil(np.log2(len(x) + n)))
    out = np.empty_like(x)
    for c in range(2):
        out[:, c] = np.fft.irfft(np.fft.rfft(x[:, c], size) * np.fft.rfft(ir[:, c], size), size)[: len(x)]
    return x + wet * out


def master(x, peak=0.89, mud=0.35, air=0.8):
    """저역 정리 + 탁한 중저역(150~400Hz) 덜기 + 또렷함(2.5kHz 위) 더하기 + 부드러운 눌림."""
    x = hp(x, 30)
    x = x - mud * bp(x, 150, 400) + air * hp(x, 2500)
    x = np.tanh(1.1 * x / np.max(np.abs(x))) / np.tanh(1.1)
    return x * peak / np.max(np.abs(x))


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


# ---------------- 1) 광고용 ----------------

AD_BPM = 84
AD_BEAT = 60 / AD_BPM
AD_BAR = 4 * AD_BEAT  # 21마디 = 60초

# 이름: (베이스, 피아노 아르페지오 음 4개, 현악 화음)
AD_CH = {
    "D": (38, [50, 57, 62, 64, 66], [62, 66, 69]),
    "A/C#": (37, [49, 57, 61, 64, 69], [61, 64, 69]),
    "Bm": (35, [47, 54, 59, 61, 62], [59, 62, 66]),
    "G": (31, [43, 50, 57, 59, 62], [59, 62, 67]),
    "A": (33, [45, 52, 57, 59, 61], [61, 64, 69]),
    "F#m": (30, [42, 49, 54, 57, 61], [57, 61, 66]),
    "Gmaj7": (31, [43, 50, 54, 57, 62], [59, 62, 66]),
    "Dadd9": (38, [50, 57, 62, 64, 69], [62, 66, 69, 76]),
}
AD_PROG = (["D", "A/C#", "Bm", "G"]  # 1~4 피아노만
           + ["D", "A/C#", "Bm", "G", "D", "A", "G", "A"]  # 5~12 선율 + 현악
           + ["G", "A", "F#m", "Bm", "G", "A"]  # 13~18 고조
           + ["D", "Gmaj7", "Dadd9"])  # 19~21 끝
AD_MEL = {
    5: [(0, 1.5, 78), (1.5, .5, 76), (2, 2, 74)],
    6: [(0, 1.5, 76), (1.5, .5, 74), (2, 2, 73)],
    7: [(0, 1, 74), (1, 1, 78), (2, 1.5, 83), (3.5, .5, 81)],
    8: [(0, 3, 79), (3, 1, 78)],
    9: [(0, 1.5, 78), (1.5, .5, 79), (2, 2, 81)],
    10: [(0, 1, 81), (1, 1, 83), (2, 2, 76)],
    11: [(0, 1.5, 79), (1.5, .5, 78), (2, 1, 76), (3, 1, 74)],
    12: [(0, 4, 76)],
    13: [(0, 1.5, 86), (1.5, .5, 85), (2, 2, 83)],
    14: [(0, 1.5, 85), (1.5, .5, 83), (2, 2, 81)],
    15: [(0, 1, 81), (1, 1, 85), (2, 2, 85)],
    16: [(0, 1, 86), (1, 1, 85), (2, 1, 83), (3, 1, 81)],
    17: [(0, 2, 83), (2, 1, 81), (3, 1, 79)],
    18: [(0, 2, 81), (2, 2, 76)],
    19: [(0, 4, 78)],
}


def render_ad():
    tr = Track(60 + 4)
    for i, name in enumerate(AD_PROG):
        bar = i + 1
        t0 = i * AD_BAR
        bass, arp, pad = AD_CH[name]
        final = bar == 21
        # 피아노 아르페지오(8분음, 페달 밟은 듯 이어짐). 처음엔 아주 여리게
        vel = 0.38 if bar <= 4 else (0.42 if bar <= 12 else 0.5)
        pattern = [0, 1, 2, 3, 4, 3, 2, 1] if not final else [0, 1, 2, 3, 4]
        for k, idx in enumerate(pattern):
            at = t0 + k * 0.5 * AD_BEAT
            tr.add(piano(arp[idx], 0.5 * AD_BEAT, vel * (1.0 if k % 4 == 0 else 0.8), pedal=AD_BAR - k * 0.5 * AD_BEAT),
                   at, pan=-0.25 + 0.1 * idx)
        # 선율(피아노, 한 옥타브 위 느낌 그대로)
        for b, d, m in AD_MEL.get(bar, []):
            tr.add(piano(m, d * AD_BEAT, 0.62 if bar < 13 else 0.7, pedal=0.3), t0 + b * AD_BEAT, pan=0.1)
            if bar >= 13:  # 고조: 한 옥타브 아래 겹침
                tr.add(piano(m - 12, d * AD_BEAT, 0.35, pedal=0.3), t0 + b * AD_BEAT, pan=0.05)
        # 현악·베이스
        if bar >= 5:
            sv = 0.55 if bar <= 12 else 0.85
            if final:
                sv = 0.7
            tr.add(strings(pad, AD_BAR * (1.6 if final else 1.0), sv), t0, pan=0.0)
            if bar >= 9:
                tr.add(strings([p - 12 for p in pad[:2]], AD_BAR, sv * 0.6), t0, pan=-0.3)
            tr.add(sub_bass(bass + 12, AD_BAR * (1.5 if final else 0.98), 0.55 if bar <= 12 else 0.7), t0)
        # 고조: 심장 박동 같은 부드러운 킥 + 글로켄
        if 13 <= bar <= 18:
            for b in (0, 2):
                tr.add(soft_kick(0.5 + 0.08 * (bar - 13)), t0 + b * AD_BEAT)
            tr.add(glock(AD_CH[name][2][-1] + 24, 0.5), t0, pan=-0.3)
            tr.add(glock(AD_CH[name][2][-2] + 24, 0.35), t0 + 2 * AD_BEAT, pan=0.3)
        if bar == 19:
            tr.add(glock(86, 0.55), t0, pan=-0.2)
        if final:
            tr.add(glock(90, 0.45), t0 + AD_BEAT, pan=0.2)
            tr.add(glock(93, 0.35), t0 + 2 * AD_BEAT, pan=-0.1)
    x = reverb(tr.x, 2.8, 0.32, 4500)
    x = master(x, mud=0.35, air=0.9)[: int(SR * 60)]
    fade = int(SR * 2.0)
    x[-fade:] *= (np.linspace(1, 0, fade) ** 1.5)[:, None]
    fin = int(SR * 0.4)
    x[:fin] *= np.linspace(0, 1, fin)[:, None]
    return x


# ---------------- 1-2) 드라마형 광고 84초 ----------------
# GPT MUSIC-REQUEST(2026-11-11): 무보컬 피아노·부드러운 현악·얇은 어쿠스틱 기타. 0~14초 성긴 피아노, 14~24 작은 발견,
# 24~52 따뜻하게 넓어짐, 52~65 조용한 기대, 65~69 덜어 냄, 69~77 가장 따뜻한 화음(강한 드럼 없음), 77~84 해소·페이드.
DR_BPM = 80
DR_BEAT = 60 / DR_BPM
DR_BAR = 4 * DR_BEAT  # 3초, 28마디 = 84초
DR_PROG = (["D", "A/C#", "Bm", "G", "A"]  # 1~5 (0~15초) 성긴 피아노
           + ["D", "A/C#", "G"]  # 6~8 작은 발견
           + ["D", "A/C#", "Bm", "G", "D", "A", "G", "A", "G"]  # 9~17 따뜻하게 넓어짐(~51초)
           + ["Bm", "F#m", "G", "D", "A"]  # 18~22 조용한 기대(~66초)
           + ["Gmaj7"]  # 23 덜어 냄
           + ["G", "A", "Bm", "Gmaj7"]  # 24~27 가장 따뜻하게(69~81초)
           + ["Dadd9"])  # 28 해소
DR_MEL = {
    6: [(2, 2, 78)], 7: [(2, 2, 76)], 8: [(0, 2, 74), (2, 2, 79)],
    9: AD_MEL[5], 10: AD_MEL[6], 11: AD_MEL[7], 12: AD_MEL[8], 13: AD_MEL[9], 14: AD_MEL[10], 15: AD_MEL[11], 16: AD_MEL[12],
    19: [(2, 2, 81)], 21: [(0, 4, 78)],
    24: [(0, 1.5, 83), (1.5, .5, 81), (2, 2, 79)], 25: [(0, 1.5, 81), (1.5, .5, 79), (2, 2, 76)],
    26: [(0, 1, 78), (1, 1, 81), (2, 2, 83)], 27: [(0, 3, 81), (3, 1, 78)], 28: [(0, 4, 78)],
}


def ks_pluck(m, dur, vel=0.5):
    """카플러스-스트롱 줄 뜯기(얇은 어쿠스틱 기타)"""
    f = hz(m)
    period = max(2, int(round(SR / f)))
    n = int(SR * min(dur + 0.6, 2.5))
    buf = lp(RNG.uniform(-1, 1, period), 3500)
    out = np.empty(n)
    b = buf.copy()
    idx = 0
    for i in range(n):
        v = b[idx]
        out[i] = v
        b[idx] = 0.996 * 0.5 * (v + b[(idx + 1) % period])
        idx = (idx + 1) % period
    t = np.arange(n) / SR
    return 0.16 * vel * out * np.clip(t / 0.002, 0, 1)


def render_drama():
    bars = len(DR_PROG)
    tr = Track(bars * DR_BAR + 5)
    for i, name in enumerate(DR_PROG):
        bar = i + 1
        t0 = i * DR_BAR
        bass, arp, pad = AD_CH[name]
        final = bar == bars
        thin = bar == 23
        sparse = bar <= 5
        warm = 24 <= bar <= 27
        # 피아노: 처음엔 반 박자마다가 아니라 한 박자마다 성기게, 덜어 내는 마디는 두 음만
        if sparse:
            pattern, step = [0, 2, 3, 4], 1.0
        elif thin:
            pattern, step = [0, 3], 2.0
        elif final:
            pattern, step = [0, 1, 2, 3, 4], 0.5
        else:
            pattern, step = [0, 1, 2, 3, 4, 3, 2, 1], 0.5
        vel = 0.34 if sparse or thin else (0.48 if warm else 0.42)
        for k, idx in enumerate(pattern):
            at = t0 + k * step * DR_BEAT
            tr.add(piano(arp[idx], step * DR_BEAT, vel * (1.0 if k == 0 else 0.82), pedal=DR_BAR - k * step * DR_BEAT),
                   at, pan=-0.25 + 0.1 * idx)
        for b, d, m in DR_MEL.get(bar, []):
            tr.add(piano(m, d * DR_BEAT, 0.66 if warm else 0.58, pedal=0.4), t0 + b * DR_BEAT, pan=0.1)
            if warm:
                tr.add(piano(m - 12, d * DR_BEAT, 0.3, pedal=0.4), t0 + b * DR_BEAT, pan=0.05)
        # 얇은 기타(작은 발견·넓어짐·기대): 8분음 아르페지오, 아주 작게
        if 6 <= bar <= 22 and not thin:
            for k, idx in enumerate([1, 2, 3, 2, 4, 2, 3, 2]):
                tr.add(ks_pluck(arp[idx] + 12, 0.5 * DR_BEAT, 0.35), t0 + k * 0.5 * DR_BEAT + 0.01, pan=0.45)
        # 현악·베이스: 9마디부터, 기대에서는 낮게, 덜어 내는 마디는 쉼, 따뜻한 마디에서 가장 넓게
        if bar >= 9 and not thin:
            sv = 0.85 if warm else (0.45 if 18 <= bar <= 22 else (0.7 if final else 0.6))
            tr.add(strings(pad, DR_BAR * (1.8 if final else 1.0), sv), t0)
            if warm or final:
                tr.add(strings([p - 12 for p in pad[:2]], DR_BAR * (1.8 if final else 1.0), sv * 0.6), t0, pan=-0.3)
            tr.add(sub_bass(bass + 12, DR_BAR * (1.6 if final else 0.98), 0.5 if not warm else 0.62), t0)
        if bar in (24, 26):
            tr.add(glock(AD_CH[name][2][-1] + 24, 0.35), t0 + 2 * DR_BEAT, pan=-0.2)
        if final:
            tr.add(glock(90, 0.4), t0 + DR_BEAT, pan=0.2)
    x = reverb(tr.x, 3.0, 0.34, 4500)
    x = master(x, mud=0.35, air=0.9)[: int(SR * 84)]
    fade = int(SR * 0.8)
    x[-fade:] *= (np.linspace(1, 0, fade) ** 1.5)[:, None]
    fin = int(SR * 0.4)
    x[:fin] *= np.linspace(0, 1, fin)[:, None]
    return x


# ---------------- 2) 앱 숲 꾸미기용 ----------------

APP_BPM = 90
APP_BEAT = 60 / APP_BPM
APP_BAR = 4 * APP_BEAT
SWING = 0.62  # 8분음 뒷박을 살짝 늦게


def sw(b):
    whole = np.floor(b)
    frac = b - whole
    return whole + (SWING if abs(frac - 0.5) < 1e-6 else frac)


APP_CH = {  # (베이스 근음, 5도/경과음, 루트 없는 화음)
    "Fmaj9": (41, 48, [57, 60, 64, 67]), "Am7": (45, 52, [55, 60, 64, 69]),
    "Bbmaj9": (46, 53, [57, 60, 62, 65]), "C9": (48, 43, [58, 62, 64, 67]),
    "D9": (50, 45, [60, 64, 66, 69]), "Gm9": (43, 50, [58, 62, 65, 69]),
    "Gm7": (43, 50, [58, 62, 65, 67]), "C7": (48, 43, [58, 64, 67, 70]),
}
APP_PROG = ["Fmaj9", "Am7", "Bbmaj9", "C9", "Fmaj9", "D9", "Gm9", "C9",
            "Bbmaj9", "Am7", "Gm7", "C7", "Am7", "D9", "Gm9", "C9"]
APP_MEL = [
    [(.5, .5, 72), (1, 1, 76), (2, .5, 74), (2.5, 1.5, 72)],
    [(0, 1, 69), (1, .5, 67), (1.5, .5, 69), (2, 2, 72)],
    [(.5, .5, 74), (1, 1, 77), (2, .5, 76), (2.5, 1, 74), (3.5, .5, 72)],
    [(0, 2, 74), (2.5, .5, 70), (3, 1, 67)],
    [(.5, .5, 72), (1, 1, 76), (2, .5, 79), (2.5, 1.5, 77)],
    [(0, 1, 78), (1, .5, 76), (1.5, .5, 74), (2, 2, 72)],
    [(0, 1.5, 70), (1.5, .5, 72), (2, 1, 74), (3, 1, 77)],
    [(0, 3, 76)],
    [(0, .5, 77), (.5, .5, 76), (1, 1, 74), (2, 2, 69)],
    [(0, .5, 76), (.5, .5, 74), (1, 1, 72), (2, 2, 67)],
    [(0, 1, 70), (1, 1, 74), (2, 1, 77), (3, 1, 79)],
    [(0, 2, 76), (2, 1, 72), (3, 1, 70)],
    [(0, 1.5, 72), (1.5, .5, 69), (2, 2, 72)],
    [(0, 1, 74), (1, .5, 78), (1.5, .5, 76), (2, 2, 72)],
    [(0, 1, 74), (1, 1, 72), (2, 1, 70), (3, 1, 69)],
    [(0, 2, 67)],
]
# 일렉 피아노 반주 리듬(박): 느긋하게 뒷박 위주
COMP = [(0, 1.2, 0.45), (1.5, 0.4, 0.35), (2.5, 1.0, 0.4), (3.5, 0.4, 0.3)]


def render_app():
    bars = len(APP_PROG)
    length = int(SR * bars * APP_BAR)
    tr = Track(bars * APP_BAR + 4)
    for i, name in enumerate(APP_PROG):
        t0 = i * APP_BAR
        root, fifth, voicing = APP_CH[name]
        for b, d, v in COMP:
            for s, m in enumerate(voicing):
                tr.add(rhodes(m, d * APP_BEAT, v), t0 + sw(b) * APP_BEAT + s * 0.008)
        # 베이스: 1박 근음, 3박 5도, 4박 뒷박에 다음 화음으로 가는 짧은 음
        nxt = APP_CH[APP_PROG[(i + 1) % bars]][0]
        tr.add(sub_bass(root, 1.8 * APP_BEAT, 0.55), t0)
        tr.add(sub_bass(fifth, 1.0 * APP_BEAT, 0.45), t0 + 2 * APP_BEAT)
        tr.add(sub_bass(nxt + (1 if nxt < root else -1), 0.4 * APP_BEAT, 0.3), t0 + sw(3.5) * APP_BEAT)
        # 브러시: 2·4박 쓸기 + 아주 작은 스윙 8분
        for b in (1, 3):
            tr.add(brush(0.6, 0.22), t0 + b * APP_BEAT, pan=0.3)
        for b in (0, 0.5, 1.5, 2, 2.5, 3.5):
            tr.add(brush(0.18, 0.06), t0 + sw(b) * APP_BEAT, pan=0.45)
        tr.add(soft_kick(0.22), t0)
        # 선율
        for b, d, m in APP_MEL[i]:
            tr.add(whistle(m, d * APP_BEAT * 0.95, 0.7), t0 + sw(b) * APP_BEAT, pan=0.08)
        if i in (7, 15):  # 구절 끝 반짝
            tr.add(glock(84, 0.3), t0 + 3 * APP_BEAT, pan=-0.3)
            tr.add(glock(88, 0.25), t0 + sw(3.5) * APP_BEAT, pan=0.3)
    x = reverb(tr.x, 1.8, 0.22, 5000)
    tail = x[length:]
    x = x[:length].copy()
    x[: len(tail)] += tail[: len(x)]  # 끝 울림을 앞에 겹쳐 끊김 없이 반복
    # 필터가 이음매에서 튀지 않게 세 번 이어 붙여 처리한 뒤 가운데 한 바퀴만 쓴다
    y = master(np.concatenate([x, x, x]), peak=0.7, mud=0.45, air=2.0)[length: 2 * length].copy()
    # 압축(ogg) 뒤에도 이음매에서 "틱"이 나지 않게 끝·처음 15ms를 아주 짧게 줄였다 키운다(2026-11-10 Mac B1)
    k = int(SR * 0.015)
    ramp = np.sin(np.linspace(0, np.pi / 2, k)) ** 2
    y[:k] *= ramp[:, None]
    y[-k:] *= ramp[::-1][:, None]
    return y


def main():
    os.makedirs(OUT, exist_ok=True)
    ad = render_ad()
    wav = os.path.join(OUT, "ggf_ad_bgm_60.wav")
    write_wav(wav, ad)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libmp3lame", "-q:a", "2",
                    os.path.join(OUT, "ggf_ad_bgm_60.mp3")], check=True)
    app = render_app()
    tmp = os.path.join(OUT, "ggf_forest_bgm_loop.wav")
    write_wav(tmp, app)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "5",
                    os.path.join(OUT, "ggf_forest_bgm_loop.ogg")], check=True)
    # 들어 보기용: 고리를 두 번 이어 붙인 mp3
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-stream_loop", "1", "-i", tmp, "-c:a", "libmp3lame", "-q:a", "2",
                    os.path.join(OUT, "ggf_forest_bgm_loop_x2_preview.mp3")], check=True)
    # 드라마형 84초(앞 두 곡의 난수 순서를 바꾸지 않게 마지막에)
    dr = render_drama()
    wav84 = os.path.join(OUT, "ggf_ad_bgm_84.wav")
    write_wav(wav84, dr)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav84, "-c:a", "libmp3lame", "-q:a", "2",
                    os.path.join(OUT, "ggf_ad_bgm_84.mp3")], check=True)
    print(f"ad {len(ad) / SR:.2f}s, app loop {len(app) / SR:.2f}s, drama {len(dr) / SR:.2f}s")


if __name__ == "__main__":
    sys.exit(main())
