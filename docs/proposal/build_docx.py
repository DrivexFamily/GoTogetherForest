#!/usr/bin/env python3
"""기획서 DOCX(접수양식 02 앱 프로젝트 소개 1~8번)를 docs/proposal.md에서 만든다.

    python3 docs/proposal/build_docx.py            # 초안: docs/proposal/GoTogetherForest_proposal_02.docx
    python3 docs/proposal/build_docx.py --out X.docx --report

- 원문: docs/proposal.md의 "## 1." ~ "## 8." (머리말 목록, "제출 전 확인할 것", "11/13 반영할 것"은 넣지 않음)
- 그림: docs/proposal/figures.json(그림 자리표). GPT 그림(검수 통과) → 지금 그림 → "그림 자리" 상자
- 필요: Python 3, python-docx, Pillow. 글꼴은 맑은 고딕으로 지정(문서를 여는 PC 글꼴로 그려짐)
- 그림은 줄이기만 하고 늘리지 않는다(결정 #29-⑦). 색 보정 없음.
경로는 모두 저장소 루트 기준이다(저장소 이름을 쓰지 않음).
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
import tempfile
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Mm, Pt, RGBColor
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs/proposal.md"
FIGS = ROOT / "docs/proposal/figures.json"
OUT = ROOT / "docs/proposal/GoTogetherForest_proposal_02.docx"
FONT = "Malgun Gothic"
GREEN = RGBColor(0x2B, 0x77, 0x4A)
GREY = RGBColor(0x66, 0x66, 0x66)
TAG_COLORS = {"11/15 구현": "2B774A", "PT 단계": "C77700", "확장 계획": "5A6ACF"}
MAX_PX = 2000  # 넣는 그림의 가장 긴 변(줄이기만 함)

# ---------------------------------------------------------------- 그림


def newest(pattern: str) -> str | None:
    hits = glob.glob(str(ROOT / pattern))
    if not hits:
        return None

    def key(p: str):
        m = re.search(r"-v(\d+)\.", p)
        return (int(m.group(1)) if m else 0, p)

    return max(hits, key=key)


def resolve(paths: list[str]) -> list[str] | None:
    """모든 경로가 있으면 실제 경로 목록, 하나라도 없으면 None."""
    out = []
    for p in paths:
        hit = newest(p) if "*" in p else (str(ROOT / p) if (ROOT / p).exists() else None)
        if not hit:
            return None
        out.append(hit)
    return out or None


def first_existing(paths: list[str]) -> list[str] | None:
    for p in paths:
        r = resolve([p])
        if r:
            return r
    return None


def pick(fig: dict) -> tuple[str, list[str] | None, str | None]:
    """(종류, 파일들, 이어 붙이기 방식). 종류: gpt / now / none."""
    mode = fig.get("compose")
    if fig.get("gpt") and fig.get("gpt_ok"):
        r = resolve(fig["gpt"])
        if r:
            return "gpt", r, mode
    if fig.get("now"):
        r = resolve(fig["now"]) if mode else first_existing(fig["now"])
        if r:
            return "now", r, mode
    if fig.get("now_fallback"):
        r = first_existing(fig["now_fallback"])
        if r:
            return "now", r, None
    if fig.get("now_grid"):
        r = resolve(fig["now_grid"])
        if r:
            return "now", r, "grid"
    return "none", None, None


def load_rgb(path: str) -> Image.Image:
    im = Image.open(path)
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA")
        bg = Image.new("RGB", im.size, "white")
        bg.paste(im, mask=im.split()[-1])
        return bg
    return im.convert("RGB")


def shrink(im: Image.Image, limit: int = MAX_PX) -> Image.Image:
    w, h = im.size
    s = min(1.0, limit / max(w, h))
    return im if s >= 1.0 else im.resize((round(w * s), round(h * s)), Image.LANCZOS)


def compose(paths: list[str], mode: str) -> Image.Image:
    ims = [load_rgb(p) for p in paths]
    gap = 24
    if mode == "grid" and len(ims) == 4:
        w = min(i.width for i in ims)
        ims = [i.resize((w, round(i.height * w / i.width)), Image.LANCZOS) if i.width != w else i for i in ims]
        h = max(i.height for i in ims)
        out = Image.new("RGB", (w * 2 + gap, h * 2 + gap), "white")
        for k, i in enumerate(ims):
            out.paste(i, ((k % 2) * (w + gap), (k // 2) * (h + gap)))
        return out
    h = min(i.height for i in ims)
    ims = [i.resize((round(i.width * h / i.height), h), Image.LANCZOS) if i.height != h else i for i in ims]
    out = Image.new("RGB", (sum(i.width for i in ims) + gap * (len(ims) - 1), h), "white")
    x = 0
    for i in ims:
        out.paste(i, (x, 0))
        x += i.width + gap
    return out


def render(fig: dict, files: list[str], mode: str | None, work: Path) -> Path:
    im = compose(files, mode) if mode and len(files) > 1 else load_rgb(files[0])
    im = shrink(im)
    # 문서 용량을 줄이려 고화질 JPEG로 넣는다(색 보정 없음, 결정 #29-⑦)
    out = work / f"{fig['id']}.jpg"
    im.save(out, quality=90, subsampling=0, optimize=True)
    return out


# ---------------------------------------------------------------- 글자 모양


def set_font(run, size=None, bold=None, color=None, mono=False):
    name = "Consolas" if mono else FONT
    run.font.name = name
    rpr = run._element.get_or_add_rPr()
    fonts = rpr.find(qn("w:rFonts"))
    if fonts is None:
        fonts = OxmlElement("w:rFonts")
        rpr.append(fonts)
    for a in ("w:ascii", "w:hAnsi", "w:cs"):
        fonts.set(qn(a), name)
    fonts.set(qn("w:eastAsia"), FONT)
    if size:
        run.font.size = Pt(size)
    if bold is not None:
        run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color


INLINE = re.compile(r"(\*\*[^*]+\*\*|`[^`]+`|\[(?:11/15 구현|PT 단계|확장 계획)\])")


def add_inline(par, text: str, size=10, bold=False, color=None):
    for part in INLINE.split(text):
        if not part:
            continue
        if part.startswith("**") and part.endswith("**"):
            set_font(par.add_run(part[2:-2]), size, True, color)
        elif part.startswith("`") and part.endswith("`"):
            set_font(par.add_run(part[1:-1]), size - 1, bold, color, mono=True)
        elif part.startswith("[") and part[1:-1] in TAG_COLORS:
            r = par.add_run(" " + part[1:-1] + " ")
            set_font(r, size - 1, True, RGBColor.from_string("FFFFFF"))
            shade(r, TAG_COLORS[part[1:-1]])
        else:
            set_font(par.add_run(part), size, bold, color)


def shade(run, hex_color: str):
    rpr = run._element.get_or_add_rPr()
    sh = OxmlElement("w:shd")
    sh.set(qn("w:val"), "clear")
    sh.set(qn("w:color"), "auto")
    sh.set(qn("w:fill"), hex_color)
    rpr.append(sh)


def cell_shade(cell, hex_color: str):
    tcpr = cell._element.get_or_add_tcPr()
    sh = OxmlElement("w:shd")
    sh.set(qn("w:val"), "clear")
    sh.set(qn("w:color"), "auto")
    sh.set(qn("w:fill"), hex_color)
    tcpr.append(sh)


def spacing(par, before=0, after=4, line=1.25):
    pf = par.paragraph_format
    pf.space_before = Pt(before)
    pf.space_after = Pt(after)
    pf.line_spacing = line


# ---------------------------------------------------------------- 문서 조각


class Builder:
    def __init__(self, work: Path):
        self.doc = Document()
        self.work = work
        self.report: list[str] = []
        sec = self.doc.sections[0]
        sec.page_width, sec.page_height = Mm(210), Mm(297)
        for side in ("left_margin", "right_margin", "top_margin", "bottom_margin"):
            setattr(sec, side, Mm(20))
        st = self.doc.styles["Normal"]
        st.font.name = FONT
        st.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
        st.font.size = Pt(10)
        self.footer()

    def footer(self):
        p = self.doc.sections[0].footer.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_font(p.add_run("같이가숲 · 02 앱 프로젝트 소개 · "), 8, color=GREY)
        for code in ("PAGE",):
            r = p.add_run()
            set_font(r, 8, color=GREY)
            for t, v in (("begin", None), (None, code), ("end", None)):
                if t:
                    e = OxmlElement("w:fldChar")
                    e.set(qn("w:fldCharType"), t)
                else:
                    e = OxmlElement("w:instrText")
                    e.set(qn("xml:space"), "preserve")
                    e.text = v
                r._element.append(e)

    def heading(self, text: str, level: int):
        size = {1: 15, 2: 12.5, 3: 11}[level]
        p = self.doc.add_paragraph()
        spacing(p, before={1: 16, 2: 10, 3: 6}[level], after=4)
        p.paragraph_format.keep_with_next = True
        add_inline(p, text, size=size, bold=True, color=GREEN if level == 1 else None)
        if level == 1:
            bottom_border(p)
        return p

    def para(self, text: str, size=10, bold=False, color=None, align=None):
        p = self.doc.add_paragraph()
        spacing(p)
        if align:
            p.alignment = align
        add_inline(p, text, size=size, bold=bold, color=color)
        return p

    def bullet(self, text: str, level=0, number: str | None = None):
        p = self.doc.add_paragraph()
        spacing(p, after=2)
        pf = p.paragraph_format
        pf.left_indent = Mm(6 + 6 * level)
        pf.first_line_indent = Mm(-4)
        set_font(p.add_run((number + " ") if number else "• "), 10, bool(number))
        add_inline(p, text)
        return p

    def code(self, lines: list[str]):
        t = self.doc.add_table(rows=1, cols=1)
        c = t.cell(0, 0)
        cell_shade(c, "F2F5F3")
        c.paragraphs[0].text = ""
        for k, line in enumerate(lines):
            p = c.paragraphs[0] if k == 0 else c.add_paragraph()
            spacing(p, after=0, line=1.1)
            set_font(p.add_run(line), 9, mono=True)
        self.doc.add_paragraph()

    def table(self, rows: list[list[str]]):
        head, body = rows[0], rows[1:]
        t = self.doc.add_table(rows=len(rows), cols=len(head))
        t.style = "Table Grid"
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        for i, row in enumerate(rows):
            for j in range(len(head)):
                txt = row[j] if j < len(row) else ""
                c = t.cell(i, j)
                c.paragraphs[0].text = ""
                p = c.paragraphs[0]
                spacing(p, after=0, line=1.15)
                add_inline(p, txt, size=9, bold=(i == 0))
                if i == 0:
                    cell_shade(c, "E3EFE7")
        gap = self.doc.add_paragraph()
        spacing(gap, after=2)

    def figure(self, fig: dict):
        kind, files, mode = pick(fig)
        if kind == "none":
            if fig.get("optional"):
                self.report.append(f"{fig['id']}: 그림 없음 → 넣지 않음(선택)")
                return
            self.placeholder(fig)
            self.report.append(f"{fig['id']}: 그림 없음 → '그림 자리' 상자")
            return
        img = render(fig, files, mode, self.work)
        p = self.doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        spacing(p, before=4, after=0)
        p.paragraph_format.keep_with_next = True
        with Image.open(img) as im:
            w_px, h_px = im.size
        width = fig.get("width_mm", 160)
        # 세로로 긴 그림이 한 쪽을 넘지 않게
        if h_px / w_px * width > 200:
            width = 200 * w_px / h_px
        p.add_run().add_picture(str(img), width=Mm(width))
        cap = self.para(("그림. " + fig["caption"]) + ("" if kind == "gpt" else ""), size=8.5, color=GREY,
                        align=WD_ALIGN_PARAGRAPH.CENTER)
        spacing(cap, after=8)
        rel = [os.path.relpath(f, ROOT) for f in files]
        self.report.append(f"{fig['id']}: {'GPT' if kind == 'gpt' else '지금 그림'} ← {', '.join(rel)}")

    def placeholder(self, fig: dict):
        t = self.doc.add_table(rows=1, cols=1)
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        t.style = "Table Grid"
        c = t.cell(0, 0)
        cell_shade(c, "F4F4F4")
        p = c.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        spacing(p, before=18, after=18)
        src = ", ".join(os.path.basename(g) for g in fig.get("gpt", [])) or "그림"
        add_inline(p, f"그림 자리 · {fig['caption']}\n({src} 납품·검수 뒤 들어감)", size=9, color=GREY)
        self.doc.add_paragraph()

    def cover(self, figs_cover: list[dict], edition: str):
        p = self.para("차량용 앱 개발 공모전 · 접수양식 02 앱 프로젝트 소개", size=9, color=GREY)
        p = self.doc.add_paragraph()
        spacing(p, before=6, after=2)
        set_font(p.add_run("같이가숲 "), 24, True, GREEN)
        set_font(p.add_run("Go Together Forest"), 14, False, GREY)
        self.para("**아이에게는 \"언제 도착해?\"의 답을, 가족에게는 함께 키운 숲을.**", size=12)
        for f in figs_cover:
            self.figure(f)
        self.table([
            ["항목", "내용"],
            ["팀명", "DriveXFamily"],
            ["공모 분야", "패밀리·동승자 케어, 인카 엔터테인먼트"],
            ["개발 플랫폼", "Pleos Connect (에뮬레이터 v2.1.0, Android Automotive API 34), Pleos Playground SDK"],
            ["구현 시점 표기", "[11/15 구현] 1차 제출에 동작 · [PT 단계] 12/3 PT 전 구현 · [확장 계획] 탑재 후 확장"],
            ["판", edition],
        ])
        self.para("항목 번호와 제목은 접수양식 02 \"앱 프로젝트 소개\"의 1~8번과 같습니다. 수치 옆 (가정)은 검증 전 추정치입니다.",
                  size=9, color=GREY)


def bottom_border(par):
    ppr = par._element.get_or_add_pPr()
    b = OxmlElement("w:pBdr")
    e = OxmlElement("w:bottom")
    for k, v in (("w:val", "single"), ("w:sz", "8"), ("w:space", "2"), ("w:color", "2B774A")):
        e.set(qn(k), v)
    b.append(e)
    ppr.append(b)


# ---------------------------------------------------------------- 원문 읽기


def body_lines(md: str) -> list[str]:
    start = md.index("\n## 1. ")
    end = md.index("\n## 제출 전 확인할 것")
    text = md[start:end].rstrip()
    text = re.sub(r"\n---\s*$", "", text)
    return text.split("\n")[1:]


def split_cells(line: str) -> list[str]:
    return [c.strip() for c in line.strip().strip("|").split("|")]


def build(out: Path, edition: str, work: Path) -> list[str]:
    md = SRC.read_text(encoding="utf-8")
    figs = json.loads(FIGS.read_text(encoding="utf-8"))["figures"]
    by_anchor = [f for f in figs if f.get("anchor")]
    by_heading = {f["heading"]: f for f in figs if f.get("heading") and not f.get("at_end")}
    at_end = {f["heading"]: f for f in figs if f.get("heading") and f.get("at_end")}
    b = Builder(work)
    b.cover([f for f in figs if f.get("heading") == "@cover"], edition)

    lines = body_lines(md)
    i = 0
    pending_end: list[dict] = []  # 지금 장이 끝날 때 넣을 그림
    used: set[str] = set()

    def flush_end():
        while pending_end:
            f = pending_end.pop(0)
            b.figure(f)
            used.add(f["id"])

    def after_block(block: list[str]):
        for f in by_anchor:
            if f["id"] not in used and any(l.strip().startswith(f["anchor"]) for l in block):
                b.figure(f)
                used.add(f["id"])

    while i < len(lines):
        line = lines[i]
        s = line.strip()
        if not s or s == "---":
            i += 1
            continue
        m = re.match(r"^(#{2,3}) (.*)$", s)
        if m:
            if len(m.group(1)) == 2:
                flush_end()
            level = len(m.group(1)) - 1
            b.heading(m.group(2), level)
            key = f"{m.group(1)} {m.group(2)}"
            for h, f in by_heading.items():
                if key.startswith(h) and f["id"] not in used:
                    b.figure(f)
                    used.add(f["id"])
            for h, f in at_end.items():
                if key.startswith(h):
                    pending_end.append(f)
            i += 1
            continue
        if s.startswith("```"):
            j = i + 1
            while j < len(lines) and not lines[j].strip().startswith("```"):
                j += 1
            b.code(lines[i + 1:j])
            i = j + 1
            continue
        if s.startswith("|"):
            j = i
            rows = []
            while j < len(lines) and lines[j].strip().startswith("|"):
                if not re.match(r"^\|\s*:?-+", lines[j].strip()):
                    rows.append(split_cells(lines[j]))
                j += 1
            b.table(rows)
            after_block(lines[i:j])
            i = j
            continue
        mb = re.match(r"^(\s*)- (.*)$", line)
        mn = re.match(r"^(\s*)(\d+)\. (.*)$", line)
        if mb or mn:
            j = i
            while j < len(lines) and (re.match(r"^\s*- ", lines[j]) or re.match(r"^\s*\d+\. ", lines[j])):
                l2 = lines[j]
                mb2 = re.match(r"^(\s*)- (.*)$", l2)
                mn2 = re.match(r"^(\s*)(\d+)\. (.*)$", l2)
                if mb2:
                    b.bullet(mb2.group(2), level=len(mb2.group(1)) // 2)
                else:
                    b.bullet(mn2.group(3), level=len(mn2.group(1)) // 2, number=mn2.group(2) + ".")
                after_block([l2])
                j += 1
            i = j
            continue
        if re.match(r"^\*\*[^*]+\*\*$", s):
            p = b.heading(s.strip("*"), 3)
            i += 1
            continue
        b.para(s)
        after_block([s])
        i += 1
    flush_end()

    for f in figs:
        if f["id"] not in used and f.get("heading") != "@cover":
            b.report.append(f"{f['id']}: 자리를 못 찾음(anchor/heading 확인) — {f.get('anchor') or f.get('heading')}")

    cp = b.doc.core_properties
    cp.author = "DriveXFamily"
    cp.last_modified_by = "DriveXFamily"
    cp.title = "같이가숲 기획서 02 앱 프로젝트 소개"
    cp.comments = "차량용 앱 개발 공모전 접수양식 02 항목 1~8"
    b.doc.save(out)
    return b.report


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(OUT))
    ap.add_argument("--edition", default=None, help="표지의 판 표기(기본: proposal.md 상태 줄)")
    ap.add_argument("--report", action="store_true", help="그림 자리마다 무엇을 넣었는지 출력")
    a = ap.parse_args()
    md = SRC.read_text(encoding="utf-8")
    m = re.search(r"상태: ([^(]+\(\d{4}-\d{2}-\d{2}\))", md)
    edition = a.edition or (m.group(1).strip() if m else "초안")
    with tempfile.TemporaryDirectory() as tmp:
        report = build(Path(a.out), edition, Path(tmp))
    print(f"만듦: {os.path.relpath(a.out, ROOT)}")
    if a.report:
        print("\n".join("  " + r for r in report))


if __name__ == "__main__":
    sys.exit(main())
