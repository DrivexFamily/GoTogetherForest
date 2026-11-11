# web

공유 링크를 받은 사람이 보는 웹 화면(PT 단계, share-backend 담당).

## forest/ — 별 숲 미리보기 (2026-11-03, 결정 #31·#20)

`web/forest/index.html`을 브라우저로 바로 연다(서버 필요 없음). 별 포인트(★)로 나무·동물을 열고 누적 ★로 숲 맵을 바꾸는 흐름을 보여 주는 폰 연동 미리보기다.

- 그림(`forest/assets/*.webp`)과 도감 목록은 `design/illustrations/tools/build_app_assets.py --dex`가 앱과 같은 파일로 복사·주입한다(직접 고치지 않는다).
- 상태(★, 열린 칸, 심은 나무)는 이 브라우저의 localStorage에만 둔다. "처음 상태"·"예시 가족" 버튼으로 되돌린다.
- 스크린샷: `design/illustrations/previews/web-forest/`.

