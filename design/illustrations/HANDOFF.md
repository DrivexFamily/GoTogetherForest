# 일러스트 인수인계 정리

사용자가 2026-11-03 제공한 [전체 이미지 설명서](references/2026-11-03-design-handoff/전체_이미지_설명서.txt)를 저장된 그림과 대조한 기록이다. 원문은 바이트를 변경하지 않고 보존했다. 새 그림 제작이나 앱 구현은 진행하지 않았다.

## 일러스트 제작 방향

- 밝고 귀여운 독창적인 3D 동화풍. 상용 게임의 구체적인 캐릭터나 UI를 복제하지 않는다.
- 시작은 건물·텐트·나무가 없는 넓은 섬. 바다·해변·폭포·언덕·부두 위치를 유지하면서 식물이 하나씩 추가된다.
- 나무 성장 4단계: 새싹 → 어린 묘목 → 작은 나무 → 성장 완료.
- 나무·동물·차량은 초기 1~2종 공개, 나머지는 실루엣·물음표·자물쇠로 표시한다.
- 캐릭터 꾸미기는 남녀 각각 6메뉴, 총 12개 독립 가로형 화면이 목표다. 현재 1024×1024 개별 이미지 6종과 비교판은 참고 자료이며 이 목표의 완성본은 아니다.
- 상단 이동 진행 HUD는 본 화면을 가리지 않도록 구성하고, 차량 선택에 따라 외형을 바꿀 수 있게 한다.
- 가족 탑승 표현은 성인 2명·아이 3명까지, 전원 차량 내부의 안전한 자세를 기준으로 검수한다.
- My Forest는 가칭이다. 이미지 속 예시 수치·글자·차량 모델·로고를 확정 데이터로 사용하지 않는다.
- 주행 중에는 정지 그림과 제한된 갱신, 꾸미기·해금 연출·이모션은 안전한 주차 상태에서 사용하도록 개발 담당과 맞춘다.

## 기존 개발 문서와 구분할 사항

| 항목 | 기존 저장소 | 이번 디자인 설명서 |
| --- | --- | --- |
| 이름 | 같이가숲 확정 기록 | 프로젝트명 미정, My Forest 가칭 |
| 핵심 경험 | 진행 화면 중심, 숲은 재방문 장치 | 섬 성장·수집·해금 중심 |
| 화풍 | SVG 레이어·손그림·크레파스 | 밝은 3D 동화풍 |
| 숲 규모 | 꾸미기 6~9자리 계획 | 장기적으로 나무 약 100그루 이상 |
| 보상 | 효율 개선 기반 나무, 거리 기반 새싹 | 주행 포인트와 아이템 해금 |

이번 문서는 그림의 제작 의도와 수정 방향을 설명하는 사용자 제공 인수인계 자료로 반영한다. 앱 이름, 보상 산식, MVP 범위 등 개발 기획 전체가 변경됐다고 단정하거나 `docs/decisions.md`를 덮어쓰지 않는다. Claude Code가 앱 통합 시 두 문서의 차이를 확인할 수 있도록 이 기록을 제공한다.

## 번호와 수량의 한계

원문은 서두에서 주요 디자인 30개·개별 분리 6개·참고 3개를 언급하지만 본문 번호는 01~38이다. 현재 보유한 39개 파일/고유 이미지 33개와는 집계 기준이 다르다. 숫자만으로 누락 수를 계산하지 않는다. 파일명 일치가 없는 대응은 시각적 후보이며 생성 순서나 원본 계보가 검증된 것은 아니다.

## 설명서 번호 ↔ 저장 파일

| 설명서 번호 | 저장 ID와 파일 | 대조 결과 |
| --- | --- | --- |
| 01 | — | 해당 초기 탐색 이미지의 원본을 현재 파일에서 확정하지 못함 |
| 02 | — | 해당 초기 탐색 이미지의 원본을 현재 파일에서 확정하지 못함 |
| 03 | — | 해당 초기 탐색 이미지의 원본을 현재 파일에서 확정하지 못함 |
| 04 | — | 해당 초기 탐색 이미지의 원본을 현재 파일에서 확정하지 못함 |
| 05 | — | 해당 초기 탐색 이미지의 원본을 현재 파일에서 확정하지 못함 |
| 06 | — | 해당 초기 탐색 이미지의 원본을 현재 파일에서 확정하지 못함 |
| 07 | [ILL-037 · 처음 시작하는 아늑한 섬 기지.png](<originals/2026-11-03/04-island-and-plants/처음 시작하는 아늑한 섬 기지.png>) | 시각적 대응 후보: 시작 UI와 텐트 포함 |
| 08 | [ILL-002 · 나무를 심을 섬의 시작점.png](<originals/2026-11-03/03-vehicles-journey-and-forest/나무를 심을 섬의 시작점.png>) | 시각적 대응 후보: 나무 없는 기본 섬 |
| 09 | [ILL-013 · 나무가 자란 행복한 섬^[1].png](<originals/2026-11-03/02-forest-and-customization/나무가 자란 행복한 섬^[1].png>) | 시각적 대응 후보: 여러 나무가 자란 섬 |
| 10 | [ILL-021 · 귀여운 식물 성장과 꽃 도감.png](<originals/2026-11-03/02-forest-and-customization/귀여운 식물 성장과 꽃 도감.png>) | 시각적 대응 후보: 성장 4단계와 꽃 |
| 11 | [ILL-038 · 알록달록 나무와 꽃 도감.png](<originals/2026-11-03/04-island-and-plants/알록달록 나무와 꽃 도감.png>) | 시각적 대응 후보: 나무·식물 전체 컬렉션 |
| 12 | [ILL-011 · 아기자기한 섬 생활 캐릭터 꾸미기 UI.png](<originals/2026-11-03/03-vehicles-journey-and-forest/아기자기한 섬 생활 캐릭터 꾸미기 UI.png>) | 시각적 대응 후보: 종합 꾸미기 |
| 13 | [ILL-022 · 귀여운 캐릭터 꾸미기 UI 가이드.png](<originals/2026-11-03/02-forest-and-customization/귀여운 캐릭터 꾸미기 UI 가이드.png>) | 시각적 대응 후보: 6메뉴 비교판 |
| 14 | [ILL-027 · 01_헤어스타일.png](<originals/2026-11-03/01-character-options/01_헤어스타일.png>) | 파일명 일치; ILL-026과 동일 바이트 |
| 15 | [ILL-029 · 02_의상.png](<originals/2026-11-03/01-character-options/02_의상.png>) | 파일명 일치; ILL-028과 동일 바이트 |
| 16 | [ILL-031 · 03_가방_소품.png](<originals/2026-11-03/01-character-options/03_가방_소품.png>) | 파일명 일치; ILL-030과 동일 바이트 |
| 17 | [ILL-033 · 04_얼굴_표정.png](<originals/2026-11-03/01-character-options/04_얼굴_표정.png>) | 파일명 일치; ILL-032과 동일 바이트 |
| 18 | [ILL-035 · 05_피부톤.png](<originals/2026-11-03/01-character-options/05_피부톤.png>) | 파일명 일치; ILL-034과 동일 바이트 |
| 19 | [ILL-025 · 06_이모션.png](<originals/2026-11-03/01-character-options/06_이모션.png>) | 파일명 일치; ILL-036과 동일 바이트 |
| 20 | [ILL-004 · 내 캐릭터 꾸미기_ 숲속 스타일링 UI.png](<originals/2026-11-03/03-vehicles-journey-and-forest/내 캐릭터 꾸미기_ 숲속 스타일링 UI.png>) | 시각적 대응 후보: 가로형 종합 화면 |
| 21 | [ILL-024 · 귀여운 캐릭터 꾸미기 화면.png](<originals/2026-11-03/02-forest-and-customization/귀여운 캐릭터 꾸미기 화면.png>) | 시각적 대응 후보: 가로형 종합 화면 |
| 22 | [ILL-023 · 귀여운 캐릭터 꾸미기 UI 모음.png](<originals/2026-11-03/02-forest-and-customization/귀여운 캐릭터 꾸미기 UI 모음.png>) | 시각적 대응 후보: 다중 화면 비교판 |
| 23 | [ILL-003 · 남녀 캐릭터 꾸미기 UI 12종 세트.png](<originals/2026-11-03/03-vehicles-journey-and-forest/남녀 캐릭터 꾸미기 UI 12종 세트.png>) | 시각적 대응 후보: 12화면 비교판 |
| 24 | [ILL-039 · 차량 연동 MY FOREST 숲속 모험.png](<originals/2026-11-03/04-island-and-plants/차량 연동 MY FOREST 숲속 모험.png>) | 이름 표기 차이 및 시각적 내용 대조 |
| 25 | [ILL-005 · 마법의 숲섬과 귀여운 친구들.png](<originals/2026-11-03/03-vehicles-journey-and-forest/마법의 숲섬과 귀여운 친구들.png>) | 시각적 대응 후보: 완성형 숲과 동물 |
| 26 | [ILL-010 · 숲에 찾아오는 동물 친구들.png](<originals/2026-11-03/03-vehicles-journey-and-forest/숲에 찾아오는 동물 친구들.png>) | 시각적 대응 후보: 동물 카드 도감 |
| 27 | [ILL-006 · 마이 포레스트로 떠나는 즐거운 여행.png](<originals/2026-11-03/03-vehicles-journey-and-forest/마이 포레스트로 떠나는 즐거운 여행.png>) | 시각적 대응 후보: My Forest 명칭 있는 배너 |
| 28 | [ILL-007 · 숲길을 달리는 행복한 내비게이션.png](<originals/2026-11-03/03-vehicles-journey-and-forest/숲길을 달리는 행복한 내비게이션.png>) | 시각적 대응 후보: 명칭 없는 배너 |
| 29 | [ILL-009 · 숲속 여행 HUD 배너.png](<originals/2026-11-03/03-vehicles-journey-and-forest/숲속 여행 HUD 배너.png>) | 상단 HUD 대응 후보; ILL-008·012와의 세부 수정 순서는 미확인 |
| 30 | [ILL-008 · 숲속 가족 여행 HUD 배너.png](<originals/2026-11-03/03-vehicles-journey-and-forest/숲속 가족 여행 HUD 배너.png>) | 가족 탑승 배너 대응 후보; 인원과 자세의 세부 검수 필요 |
| 31 | [ILL-001 · 알록달록 귀여운 차량 선택 화면.png](<originals/2026-11-03/03-vehicles-journey-and-forest/알록달록 귀여운 차량 선택 화면.png>) | 시각적 대응 후보: 차량 선택 |
| 32 | [ILL-017 · image-gen-1(4).png](<originals/2026-11-03/02-forest-and-customization/image-gen-1(4).png>) | 시각적 대응 후보: 나무 잠금 도감 |
| 33 | [ILL-018 · image-gen-2.png](<originals/2026-11-03/02-forest-and-customization/image-gen-2.png>) | 시각적 대응 후보: 동물 잠금 도감 |
| 34 | [ILL-019 · image-gen-3.png](<originals/2026-11-03/02-forest-and-customization/image-gen-3.png>) | 시각적 대응 후보: 차량 잠금 도감 |
| 35 | [ILL-020 · image-gen-4.png](<originals/2026-11-03/02-forest-and-customization/image-gen-4.png>) | 시각적 대응 후보: 해금 설명 |
| 36 | [ILL-015 · AC706AE0-8080-4B24-92D2-B691AF3124CF.jpeg](<originals/2026-11-03/02-forest-and-customization/AC706AE0-8080-4B24-92D2-B691AF3124CF.jpeg>) | JPEG 파일명 일치 |
| 37 | [ILL-016 · BAED079E-CBBB-4DCB-91DD-F12B0DD5A9E3.jpeg](<originals/2026-11-03/02-forest-and-customization/BAED079E-CBBB-4DCB-91DD-F12B0DD5A9E3.jpeg>) | JPEG 파일명 일치 |
| 38 | [ILL-014 · 40273966-4D00-4275-B3DE-5DAF25F82B80.jpeg](<originals/2026-11-03/02-forest-and-customization/40273966-4D00-4275-B3DE-5DAF25F82B80.jpeg>) | JPEG 파일명 일치 |

ILL-012(여행 진행 안내 UI) 및 ILL-021/ILL-015, ILL-013/ILL-016, ILL-002/ILL-014처럼 유사한 장면의 다른 파일은 모두 보존한다. 시각적으로 유사해도 SHA-256이 다르면 중복으로 삭제하지 않는다.
