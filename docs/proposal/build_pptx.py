#!/usr/bin/env python3
"""기획 PPT를 5판에서 고침표(docs/proposal/pptx_patch.json)대로 다시 만든다.

    python3 docs/proposal/build_pptx.py                       # docs/proposal/GoTogetherForest_proposal_draft.pptx
    python3 docs/proposal/build_pptx.py --out X.pptx --report
    python3 docs/proposal/build_pptx.py --preview-gpt PNG     # GPT 자리 위치 미리 보기(아무 그림이나 넣어 봄, 커밋 금지)

- 바탕: git에 있는 5판(고침표의 base.commit). 그래서 몇 번을 돌려도 같은 결과가 나온다.
- 화면 캡처(design/screens/…)는 11/13에 다시 찍은 뒤 이 스크립트를 돌리면 새 그림이 들어간다.
- 필요: Python 3, python-pptx, Pillow, git. 그림은 줄이기만 하고 늘리지 않는다(결정 #29-⑦).
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image
from pptx import Presentation
from pptx.util import Emu, Inches

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_docx import ROOT, first_existing, load_rgb, resolve, shrink  # noqa: E402

PATCH = ROOT / "docs/proposal/pptx_patch.json"
OUT = ROOT / "docs/proposal/GoTogetherForest_proposal_draft.pptx"
EMU_PER_IN = 914400


def base_bytes(spec: dict) -> bytes:
    return subprocess.check_output(["git", "-C", str(ROOT), "show", f"{spec['commit']}:{spec['path']}"])


def choose(item: dict, preview: str | None) -> tuple[str, str | None]:
    if item.get("gpt"):
        if preview:
            return "gpt(미리보기)", preview
        if item.get("gpt_ok"):
            r = resolve(item["gpt"][:1])
            if r:
                return "gpt", r[0]
    if item.get("now"):
        r = first_existing(item["now"])
        if r:
            return "now", r[0]
    return "none", None


def fitted(path: str, box_w: int, box_h: int, fit: str) -> bytes:
    """상자 비율에 맞춘 JPEG. cover: 가운데를 잘라 채움, contain: 흰 여백."""
    im = load_rgb(path)
    ratio = box_w / box_h
    w, h = im.size
    if fit == "cover":
        if w / h > ratio:
            nw = round(h * ratio)
            im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
        else:
            nh = round(w / ratio)
            im = im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
    else:
        if w / h > ratio:
            cw, ch = w, round(w / ratio)
        else:
            cw, ch = round(h * ratio), h
        canvas = Image.new("RGB", (cw, ch), "white")
        canvas.paste(im, ((cw - w) // 2, (ch - h) // 2))
        im = canvas
    im = shrink(im, 2000)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=90, subsampling=0, optimize=True)
    return buf.getvalue()


def replace_picture(slide, shape, data: bytes):
    """같은 자리·크기의 그림으로 바꾼다(도형 이름과 순서 유지)."""
    left, top, width, height, name = shape.left, shape.top, shape.width, shape.height, shape.name
    el = shape._element
    blip = el.find(".//{http://schemas.openxmlformats.org/drawingml/2006/main}blip")
    old_rid = blip.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}embed") if blip is not None else None
    pic = slide.shapes.add_picture(io.BytesIO(data), left, top, width, height)
    pic.name = name
    el.addprevious(pic._element)  # 원래 겹침 순서 자리로
    el.getparent().remove(el)
    if old_rid:
        slide.part.drop_rel(old_rid)  # 쓰지 않게 된 옛 그림은 파일에서 뺀다


def set_paragraph(par, text: str):
    runs = par.runs
    runs[0].text = text
    for r in runs[1:]:
        r._r.getparent().remove(r._r)


def delete_slide(prs, index0: int):
    sldIdLst = prs.slides._sldIdLst
    sid = sldIdLst[index0]
    prs.part.drop_rel(sid.rId)
    sldIdLst.remove(sid)


def build(out: Path, edition: str, preview: str | None) -> list[str]:
    spec = json.loads(PATCH.read_text(encoding="utf-8"))
    prs = Presentation(io.BytesIO(base_bytes(spec["base"])))
    slides = list(prs.slides)
    rep: list[str] = []

    for t in spec["text"]:
        s = slides[t["slide"] - 1]
        new = t["new"].replace("{edition}", edition)
        hit = 0
        for sh in s.shapes:
            if not sh.has_text_frame:
                continue
            for par in sh.text_frame.paragraphs:
                if par.runs and "".join(r.text for r in par.runs).strip() == t["old"]:
                    set_paragraph(par, new)
                    hit += 1
        if hit != 1:
            rep.append(f"글자 {t['slide']}장: '{t['old'][:30]}…' {hit}곳 일치(1곳이어야 함) → 고침표 확인")

    short = edition.split(" (")[0]
    for r in spec.get("replace_all", []):
        n = 0
        layouts = [l for m in prs.slide_masters for l in m.slide_layouts]
        for s in slides + layouts + list(prs.slide_masters):
            for sh in s.shapes:
                if sh.has_text_frame:
                    for par in sh.text_frame.paragraphs:
                        for run in par.runs:
                            if r["old"] in run.text:
                                run.text = run.text.replace(r["old"], r["new"].replace("{edition_short}", short))
                                n += 1
        rep.append(f"모든 장 '{r['old']}' → {n}곳")

    for p in spec["pictures"]:
        s = slides[p["slide"] - 1]
        shape = next((sh for sh in s.shapes if sh.shape_type == 13 and sh.name == p["name"]), None)
        if shape is None:
            rep.append(f"그림 {p['slide']}장 '{p['name']}': 도형을 못 찾음")
            continue
        kind, path = choose(p, preview)
        if not path:
            rep.append(f"그림 {p['slide']}장 '{p['name']}': 5판 그림 그대로")
            continue
        replace_picture(s, shape, fitted(path, shape.width, shape.height, p.get("fit", "contain")))
        rep.append(f"그림 {p['slide']}장 '{p['name']}': {kind} ← {os.path.relpath(path, ROOT)}")

    for a in spec.get("add", []):
        kind, path = choose(a, preview)
        if not path:
            rep.append(f"추가 {a['slide']}장 '{a['id']}': 그림 없음 → 넣지 않음")
            continue
        x, y, w, h = (Inches(v) for v in a["box_in"])
        pic = slides[a["slide"] - 1].shapes.add_picture(io.BytesIO(fitted(path, w, h, a.get("fit", "cover"))), x, y, w, h)
        pic.name = a["id"]
        rep.append(f"추가 {a['slide']}장 '{a['id']}': {kind} ← {os.path.relpath(path, ROOT)}")

    for dsh in spec.get("delete_shapes", []):
        s = slides[dsh["slide"] - 1]
        hit = [sh for sh in s.shapes if sh.name == dsh["name"]]
        for sh in hit:
            sh._element.getparent().remove(sh._element)
        rep.append(f"도형 지움 {dsh['slide']}장 '{dsh['name']}': {len(hit)}개")

    for n in sorted(spec.get("delete_slides", []), reverse=True):
        delete_slide(prs, n - 1)
        rep.append(f"{n}장 지움")

    cp = prs.core_properties
    cp.author = cp.last_modified_by = "DriveXFamily"
    cp.title = f"같이가숲 기획안 {edition}"
    prs.save(out)
    return rep


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(OUT))
    ap.add_argument("--edition", default=None, help="표지의 판 표기(기본: proposal.md 상태 줄)")
    ap.add_argument("--report", action="store_true")
    ap.add_argument("--preview-gpt", default=None, help="GPT 자리에 대신 넣어 볼 그림(위치 확인용)")
    a = ap.parse_args()
    md = (ROOT / "docs/proposal.md").read_text(encoding="utf-8")
    m = re.search(r"상태: [^0-9]*(\d+판)\((\d{4})-(\d{2})-(\d{2})\)", md)
    edition = a.edition or (f"{m.group(1)} ({m.group(2)}.{int(m.group(3))}.{int(m.group(4))})" if m else "초안")
    rep = build(Path(a.out), edition, a.preview_gpt)
    print(f"만듦: {os.path.relpath(a.out, ROOT)} (표지: 기획안 {edition})")
    if a.report:
        print("\n".join("  " + r for r in rep))


if __name__ == "__main__":
    sys.exit(main())
