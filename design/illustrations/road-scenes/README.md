# 진행 화면 풍경 길 그림 검수 기록 (GPT R20, 결정 #33)

GPT가 그린 풍경 길 그림 60장(테마 10 × 넓은·정사각·세로 × 낮·저녁, `design/illustrations/exports/2026-11-05/`)을
`design/illustrations/tools/build_road_scenes.py`로 검수하고 앱에 넣은 기록입니다. 앱에 들어간 그림은
`core/ui/src/main/res/drawable-nodpi/ggf_pic_roadscene_<테마>_<모양>[_evening].webp`, 길 선은 `core/ui/.../RoadSceneData.kt`(만든 파일)입니다.

| 파일 | 내용 |
| --- | --- |
| `R20-manifest.json` | 검수 목록: 테마 이름·키·모양·낮/저녁 원본, 길 색(`gray`: 바닷길·사막길), 출발·도착 점(`hints`, 길과 같은 색 보도·오솔길이 길 끝에 붙은 7장) |
| `R20-coverage.json` | 테마마다 대표 화면 4곳(넓은 화면·반 화면·좁은 창·세로 화면)에서 앱이 고르는 그림 |
| `R20-road-lines-day.jpg`, `-evening.jpg` | 낮 그림에서 딴 길 가운데 선(빨강, 흰 점 = 10%마다)을 낮·저녁 그림에 얹은 모음. 저녁 그림에도 같은 선이 길 위에 있어야 합니다 |

다시 만들기(저장소 루트, numpy·opencv-python-headless·Pillow·scikit-image가 있는 파이썬):

```bash
python design/illustrations/tools/build_road_scenes.py design/illustrations/road-scenes/R20-manifest.json --out /tmp/r20-check --apply
```

검수 결과(2026-11-05): 30쌍 모두 통과. 길 조각이 다리·그림자로 끊긴 그림은 가까운 조각을 이었고, 길 끝에 같은 색 오솔길·광장이
붙은 7장(도심 넓은·정사각, 시골농장 넓은, 호수 정사각·세로, 벚꽃 정사각·세로, 눈꽃 넓은)은 출발·도착 점을 주어 그 사이 길 가운데를 땄습니다.
저녁 그림 8쌍은 낮과 1~2px 크기가 달라 선을 그 비율로 옮겨 비교했고(앱은 저녁 그림을 낮 크기로 그림), 모두 길 위 비율 86~100%입니다.
그림 자체(색·크기)는 바꾸지 않았고 WebP 90으로만 줄였습니다(60장 약 25MB).
