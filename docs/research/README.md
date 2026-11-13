# 최신 정보 재점검 (2026-11-11)

마스터 지시: 공모전 사이트와 Pleos Playground가 많이 바뀐 것 같으니, **이전에 파악한 원본은 지우지 말고** 새로 정리해 바뀐 점을 확인.

| 문서 | 내용 |
| --- | --- |
| [2026-11-11-contest-site.md](2026-11-11-contest-site.md) | 공모전 사이트(공지 5건, FAQ 22문항, 접수 양식) + 이전 기록과 41개 항목 비교 |
| [2026-11-11-pleos-portal.md](2026-11-11-pleos-portal.md) | Pleos 개발자 포털·문서(SDK 버전·상태, API 단위, 규정) + 10/31 기록과 39개 항목 비교 |

이전 원본(그대로 둠): `CLAUDE.md` 1장, `docs/pleos-dev-guide.md`(2026-10-31 조사), `docs/sdk-capabilities.md`.

## 바뀐 것 중 우리에게 중요한 것

**공모전**
1. 심사용 계정 Collaborator 초대 **필요 없음** → 접수 메일에 **저장소 주소 + 최종 커밋 번호**, 마감 후 **11/16(월)까지 Public 전환, 11/23까지 유지**(안 하면 개발 산출물 가산점 없음). 제출 뒤 커밋 기록을 바꾸지 않는다.
2. README 필수 항목 추가: **에뮬레이터 버전과 화면 규격**(반영함: 2560×1440).
3. **문서에 공개되지 않은 SDK는 제공되지 않음**(설명회의 "미공개 SDK 제공 예정"과 다름) → 차량 제어(공조·창문 등)는 기획서의 향후 계획으로만.
4. "준비 중" SDK도 에뮬레이터에서 사용 가능(Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation).
5. 운영사무국은 기술 질문(DO 선언 등)에 답하지 않음. 이번 심사는 App Market 입점 심사가 아님.
6. 양식은 DOCX·PDF 모두 가능, 전자서명·서명 이미지 인정, 항목·순서만 지키면 꾸밈 자유, 추가 자료(PPT·PDF·HTML) 첨부 가능, 영상 분량 제한 없음.
7. 2차 PT는 에뮬레이터 시연(실차 없음), 기준·일정은 본선 팀에 따로 안내.
8. 그대로: 일정(11/15 23:59 마감 등), 메일 제목 형식, 심사 4항목, 상금, 기획서 항목 1~8, APK 파일명 규칙.

**Pleos**
1. SDK 버전·에뮬레이터(v2.1.0)는 **그대로**(릴리스 노트 2026-10-19 한 건). 코드 고정 전에 올릴 버전 없음. LLM SDK는 끝나고 Gleo Interface로 바뀜.
2. **트립 거리 단위가 m**로 문서에 적혀 있음 → 우리 가정(km)을 고침(`PleosVehicleConfig.tripKmPerUnit = 0.001`). 에뮬레이터는 이 값을 주지 않아 시연 영향 없음.
3. NaviHelper 단위 문서로 확인: `remainDistance` m, `remainTime` 초, `RouteStartInfo.distance` m → 진행률 계산식 그대로 맞음.
4. **확인 필요:** `DrivingInfo.destination.distance`(DrivingState.distance) 설명이 "출발점부터"로 적혀 있음. 우리 앱은 남은 거리로 씀 → Mac 실제 내비 로그로 `DestinationInfo.remainDistance`와 비교(실제 내비 시험에서는 진행률이 맞게 움직였음).
5. 주행 규제 해제 기준이 문서에 생김(InfoLINK 문서, 북미 외): 시동 끔, 또는 5km/h 이하 + P단·주차브레이크·Auto Hold 중 하나 → 우리 정차·휴게소 판단의 근거로 인용 가능.
6. 여전히 문서에 없음: DO 선언 방법, Kids 카테고리 주행 중 허용 범위, 조수석 창 규칙.
7. 선루프 제어는 Connect SDK에 없음(다른 플랫폼 API에만) → 기획서에 Connect 기능으로 쓰지 않음.
