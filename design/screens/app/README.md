# 앱 화면 캡처 (`:screens`)

에뮬레이터 없이 클라우드(JVM)에서 찍은 **실제 앱 화면**입니다. 앱의 Compose 화면 코드(`JourneyScreen`, `ForestScreen`, `ArrivalResultScreen`)를 그대로 그리고, 화면에 넣는 값도 앱이 쓰는 모델(`TripState`, `GuardianAccount` → `ForestUiMapper`)로 만들었습니다. 렌더러는 Paparazzi(Apache-2.0)이고, 그 안의 layoutlib은 Android Studio 미리보기와 같은 그리기 엔진입니다. 구조 설명은 `docs/architecture.md` 18장에 있습니다.

**모든 화면은 아래 명령으로 다시 찍습니다.** 저장소에는 기획서·발표에 쓰는 12장만 넣고(아래 표의 ●), 나머지는 찍어도 이 폴더의 `.gitignore`가 git에 넣지 않습니다.

## 다시 찍기

저장소 루트에서 실행합니다.

```bash
# 1) 찍기(약 1분, 첫 실행은 Paparazzi·layoutlib 약 160MB를 내려받아 더 걸림). 이 폴더의 PNG를 덮어씀
./gradlew -Pscreens=true :screens:testDebugUnitTest

# 2) (선택, 커밋 전 권장) 무손실로 용량 줄이기. Python 3 + Pillow 필요. 픽셀은 같고 약 40% 줄어듦
python3 tools/cloud/shrink-png.py design/screens/app
```

- `-Pscreens=true`가 없으면 `:screens` 모듈이 빌드에 아예 들어가지 않습니다. 평소 빌드·테스트·APK에는 영향이 없습니다.
- 찍을 화면과 상태: `screens/src/test/kotlin/app/familyjourney/screens/` (`JourneyShots.kt`, `ForestShots.kt`, 상태는 `ShotSamples.kt`, 창 크기는 `ShotKit.kt`의 `ShotWindow`).
- 저장소에 넣을 그림을 바꾸려면 `.gitignore`의 `!파일이름` 줄을 고칩니다.
- 클라우드에서 Maven Central이 "429 Too Many Requests"로 막히면 몇 분 뒤 다시 실행합니다(이때 Gradle 오류는 `"path" is null`처럼 엉뚱하게 보일 수 있음).

## 창 크기 (dp, 160dpi라 PNG 픽셀 = dp)

| 이름 | 크기 | 배치 | 근거 |
| --- | --- | --- | --- |
| `full` 전체 앱 영역 | 1682×1210 | 진행 화면 Expanded. 숲·도착 결과는 두 칸 [그림 \| 정보] | 에뮬레이터 앱 창 실측 `frame=[853,90,2535,1300]` (`docs/emulator-capabilities.md` 11장) |
| `half` 반 화면 1:1 | 841×1210 | 진행 화면 **Expanded**(841 ≥ 840). 숲·도착 결과는 한 칸(창이 세로로 김) | 실측의 반. App Market 반 화면 스크린샷 규격 840×1212와 거의 같음 |
| `narrow` 2:1 좁은 쪽 | 560×1210 | Compact(600dp 미만), 한 칸 | 실측의 1/3, qa M3에서 잰 560dp |
| `portrait` 세로 | 720×1200 | Medium(600~839dp), 한 칸 | 실제 세로 앱 창 dp는 미확인. core:ui 미리보기의 "세로 전체" 가정 |

숲 그림은 어느 창에서든 맵 그림 비율 그대로입니다(잘리지 않음). 규칙은 `docs/architecture.md` 13-7 "배치".

## 화면 목록 (35장, ● = 저장소에 있음)

모두 라이트, 이름 끝이 `-dark`면 다크입니다. 주차 중 화면은 짧은 움직임이 끝난 뒤의 모습입니다. 예시 이름 "하늘이"는 가상의 별명입니다. 데모 1장을 빼면 모두 실차 모드라 "데모 주행" 배지가 없습니다.

### 진행 화면 (feature:journey `JourneyScreen`) — 16장

| 파일 | 상태 |
| --- | --- |
| `journey-driving-40-full.png` ●, `-half.png` ●, `-half-dark.png` ●, `-narrow.png`, `-portrait.png` | 주행 중 40%, 남은 40분 → "만화 2편 보면 도착해요"(좁은 창은 "만화 2편 남았어요"). 정지 그림, [크게]만 |
| `journey-rest-half.png` ●, `-full.png`, `-narrow.png`, `-portrait.png`, `-half-dark.png` | 휴게소(주행 제한 꺼짐 + 기어 P) 50%, "휴게소에서 쉬는 중", "지금까지 새싹 24개가 자랐어요", [소리 끄기] |
| `journey-arrived-half.png` ●, `-full.png`, `-narrow.png`, `-portrait.png`, `-half-dark.png` | 도착 + 주차: "도착했어요!", "운전해 줘서 고마워요", [숲 보러 가기] |
| `journey-demo-driving-40-half.png` | 데모 주행: "데모 주행" 배지 + 화면 아래 접힌 발표자 패널 |

### 아이의 숲 (feature:forest `ForestScreen`) — 12장

| 파일 | 상태 |
| --- | --- |
| `forest-island-full.png` ●, `forest-island-portrait.png` ●, `-half.png`, `-narrow.png`, `-full-dark.png` | 기본 섬, 나무 11그루(참나무 9·벚나무 2) + 새싹 8개, 다람쥐·토끼. 보유 360★ · 모은 560★, 지금 심는 나무 사과나무. 2026-11-04부터 숲 그리드(decisions.md #32): 맵을 크게 보기로 찍힘(좁은 창·세로는 전체 보기로 시작), 새싹 점은 맵 아래 글줄 |
| `forest-tree-book-full.png` ● | 나무 도감. 참나무·벚나무·사과나무 열림, 나머지 7종 잠김 |
| `forest-map-page-full.png` ● | 숲 맵 쪽. 기본 섬만 열림(케이블카 바닷가 2,000★, 분수 공원 5,000★, 강가 들판 10,000★) |
| `forest-m2-25-trees-full.png` ●, `forest-m3-25-trees-full.png`, `forest-m4-25-trees-full.png`, 같은 이름 `-dark` 3장(`forest-m2-25-trees-full-dark.png` ●) | 맵 M2 케이블카 바닷가·M3 분수 공원·M4 강가 들판, 나무 25그루(7종) + 새싹 3개, 동물 10종 모두. 다크는 저녁 맵(GPT R16). **배치 확인용 값**(모은 13,000★인 가족 가정) |
| `forest-driving-still-full.png`, `-narrow.png` | 주차 전(주행 중)의 숲: 정지 숲 그림 한 장 + "차가 서면 숲을 볼 수 있어요" |

### 도착 결과 (feature:forest `ArrivalResultScreen`) — 4장

| 파일 | 상태 |
| --- | --- |
| `arrival-result-full.png` ●, `-half.png`, `-narrow.png`, `-portrait.png` | 400km 중 부드럽게 360km(90%), 전기차 ×1.5 → 새싹 54개(나무 5그루 + 새싹 4개), +360★, 보유·모은 560★, 이번 여행 전비 |

## 알아 둘 것

- **글꼴:** 한글은 layoutlib에 들어 있는 Noto Sans CJK로 그려집니다(네모 □ 없음, 2026-11-04 확인). 실제 차는 시스템 글꼴 Asta Sans라 글자 모양이 조금 다릅니다.
- **다크 모드 진행 화면:** 길 뒤 숲길 그림이 없는 것은 앱 설계입니다(`ProgressRoad`가 다크에서는 배경 그림을 그리지 않음).
- **그림:** 앱의 GPT 그림(`core/ui/src/main/res/drawable-nodpi/ggf_pic_*.webp`)을 앱이 그린 크기 그대로 씁니다. 그림 파일은 바꾸지 않았습니다. 출처는 `docs/assets-license.md`.
