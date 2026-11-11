#!/usr/bin/env python3
"""PNG 용량 줄이기(무손실). 화면 캡처(design/screens/app)를 저장소에 넣기 전에 한 번 돌린다.

- 알파가 모두 255(불투명)인 그림만 RGB로 바꾼다(보이는 픽셀은 같다).
- zlib 최고 압축으로 다시 쓰고, 다시 읽어 픽셀이 같은지 확인한다. 다르면 원본을 그대로 둔다.
- 필요: Python 3 + Pillow. 사용: python3 tools/cloud/shrink-png.py design/screens/app
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops


def shrink(path: Path) -> tuple[int, int]:
    before = path.stat().st_size
    with Image.open(path) as im:
        im.load()
        original = im.convert("RGBA")
    opaque = original.getchannel("A").getextrema() == (255, 255)
    out = original.convert("RGB") if opaque else original
    tmp = path.with_suffix(".tmp.png")
    out.save(tmp, optimize=True)
    with Image.open(tmp) as again:
        same = ImageChops.difference(again.convert("RGBA"), original).getbbox() is None
    if same and tmp.stat().st_size < before:
        tmp.replace(path)
    else:
        tmp.unlink()
    return before, path.stat().st_size


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("사용: python3 tools/cloud/shrink-png.py <PNG 폴더>")
    total_before = total_after = 0
    for p in sorted(Path(sys.argv[1]).glob("*.png")):
        b, a = shrink(p)
        total_before += b
        total_after += a
        print(f"{p.name}: {b // 1024}KB -> {a // 1024}KB")
    print(f"합계: {total_before // 1024}KB -> {total_after // 1024}KB")


if __name__ == "__main__":
    main()
