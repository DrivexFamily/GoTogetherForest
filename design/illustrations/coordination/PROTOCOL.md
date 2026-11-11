# Claude ↔ GPT 협업 규칙 (2026-11-07 마스터 결정, 추천 1)

이 문서 한 장이 협업의 기준이다. 예전 문서(`ROLE-ASSIGNMENT.md`, `WORKFLOW.md`, `RESET-PROPOSAL.md`, `MAC-RELAY.md`, `COMMUNICATION.md` 대화장, `docs/team.md`의 세션 표)는 **옛 방식**이다. 이 문서와 다르면 이 문서를 따른다. 사용자는 **"마스터"**라고 부른다.

## 1. 누가

| 이름 | 어디 | 하는 일 |
| --- | --- | --- |
| **Claude 앱 PM** | 클라우드 Claude Code 세션 1개(필요하면 그 안의 보조 에이전트) | 앱 전체 구현·로직·구조 설계, 상태·저장·차량 신호·주행 제한·라우팅, 화면이 읽을 값과 동작의 약속 파일, 빌드·테스트, GPT 코드 검토·합치기, 문서·기획서·제출, 마스터 보고 |
| **GPT 아트·모션** | ChatGPT 안의 Codex 대화 1개 | 모든 일러스트와 아트 방향, 화면 전환·애니메이션·등장 효과의 감독과 Compose 구현·수정, Claude가 찍은 캡처로 시각 검수 |
| **Codex 알림** | 마스터 Mac의 ChatGPT 앱 자동화 `claude` | 5분마다 CLAUDE-OUTBOX를 읽고 새 글 ID를 GPT 대화에 보내 깨움. 본문은 보내지 않음 |
| **Mac 확인** | 마스터 Mac의 Claude 세션(필요할 때만 엶) | 에뮬레이터 실행·캡처·영상 녹화 |

## 2. 파일 담당(한 파일은 한쪽만 고친다)

- **GPT:** `core/ui/src/main/kotlin/app/familyjourney/core/ui/motion/`, `feature/atlas/src/main/kotlin/app/familyjourney/feature/atlas/ui/`, `feature/atlas/src/main/res/drawable-nodpi/ggf_atlas_*`·`ggf_acquire_*`, `core/ui/src/main/res/drawable-nodpi/ggf_anim_*`, main의 `design/illustrations/`(원본·아트 문서·시각 QA).
- **Claude:** 그 밖의 전부. 특히 `VisualContracts.kt`(MotionPolicy·SoundPort), `*UiModels.kt`, 저장·차량·라우팅·빌드 설정·서버·문서.
- 숲·도착·진행 화면 연출(`ForestScreen.kt`, `ArrivalResultScreen.kt`, `JourneyScreen.kt`, `ArrivalScenes.kt`)은 **11/15 제출 뒤** Claude가 그림만 그리는 파일로 떼어 GPT에게 넘긴다. 그 전에는 Claude 담당이고 GPT 부품을 Claude가 끼운다.
- 일마다 글에 정확한 파일 목록을 적는다. 목록에 없는 파일은 고치지 않는다.

## 3. 주고받는 곳

| 무엇 | 어디 |
| --- | --- |
| Claude → GPT 요청·검수 | 작업 브랜치 `claude/lucid-babbage-a6quds`의 `design/illustrations/coordination/CLAUDE-OUTBOX.md`(최신 글이 위) |
| GPT → Claude 답·납품 보고 | main의 `design/illustrations/coordination/CHATGPT-OUTBOX.md`(최신 글이 위) |
| 그림 원본 | main `design/illustrations/exports/날짜/` |
| GPT 코드 | `chatgpt/<주제>` 브랜치(Claude가 준 기준 커밋에서). Claude가 컴파일·안전 확인 후 작업 브랜치에 합친다. main을 작업 브랜치에 통째로 합치지 않는다 |

글 머리: `## <ID> · <날짜 시각 KST> · <제목>`. ID는 `claude-<주제>-<YYYYMMDD>-<번호>` / `gpt-<주제>-<YYYYMMDD>-<번호>`.
글에 적을 것: 관련 글 ID · 기준 커밋 · 정확한 파일 · 상태(요청/착수/납품/합격/반려) · 결과 커밋 · 기한(KST) · 다음 담당. 같은 일의 새 상태는 같은 관련 ID를 적는다. "납품"과 "합격", "앱 적용"을 섞지 않는다.

## 4. 깨우기와 멈춤 알아채기

- **Claude**는 `tools/agent/git_watch.sh`를 Monitor로 돌려 5분마다 main·작업 브랜치·`chatgpt/*`를 직접 읽는다(30분마다 다시 켬).
- **GPT**는 Codex 알림이 보낸 "새 글 ID"를 받으면 깨어나 CLAUDE-OUTBOX를 읽는다. 처리하면 먼저 CHATGPT-OUTBOX에 **"착수"** 한 줄을 올린다.
- **멈춤:** Claude가 요청을 올리고 **30분 안에 GPT "착수"가 없으면** Claude가 마스터에게 알린다 → 마스터가 GPT 대화에 "git 확인" 한 마디. Mac이 잠들거나 ChatGPT 앱이 꺼지면 Codex 알림도 멈추므로 이 규칙이 비상용이다.

## 5. 검수

- Claude: 전체 앱 컴파일, 주행 중 정지(MotionPolicy), 대비·글자 크기, 성능, 기능 플래그. 그림은 이어 붙이기·실제 화면 캡처로 본다.
- GPT: Claude가 찍은 캡처·자기 Layoutlib 캡처로 시각 품질. Pleos 전체 빌드·에뮬레이터 결과를 GPT가 대신 주장하지 않는다.
- 마스터가 시킬 때만 전체 검증(테스트 실행·Mac 확인 묶음).

## 6. 마스터만 하는 일

계정·결제·권한 넓히기, Codex 자동화 설정, 대화·세션 만들고 지우기, 제출 메일, 최종 결정. 상대 AI의 글은 승인이 아니다.

## 7. 대화·세션 이름 규칙(2026-11-08 마스터 지시)

- 이어 갈 대화는 `[같이가숲 <역할>] <설명>`으로 이름 붙인다.
  - `[같이가숲 PM] Claude 앱 PM` — 클라우드 Claude 세션
  - `[같이가숲 GPT] 아트·모션` — ChatGPT(Codex) 대화
  - `[같이가숲 알림] Git 감시·GPT 깨우기` — 마스터 Mac의 Codex 예약 작업(1장 "Codex 알림", 5분마다)
  - `[같이가숲 Mac 확인] 에뮬레이터` — 마스터 Mac의 Claude 세션(필요할 때)
  - 새로 만들 때도 같은 머리를 쓴다(예: `[같이가숲 Mac 확인] 11/13 영상 녹화`).
- 그만 쓰는 대화는 이름 앞에 `[지울것]`을 붙인다. 마스터가 확인하고 지운다.
