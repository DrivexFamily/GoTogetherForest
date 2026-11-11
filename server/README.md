# server — 같이가숲 공유 서버

가족 폰 연결·숲 동기화·우리 차 진행(결정 #37)과 기다리는 가족용 공유 링크(결정 #8)를 맡는 서버다.
**Cloudflare Workers + Durable Objects(SQLite)**. 설계는 `docs/architecture.md` 19장, API는 `docs/share-api.md`.

## 구조

| 파일 | 내용 |
| --- | --- |
| `src/index.ts` | 입구(Worker). 인증 헤더(`Device …`/`Share …`)를 읽어 알맞은 Durable Object로 보낸다. 밖에서 온 내부 헤더는 지운다 |
| `src/family.ts` | `FamilyRoom`: 가족마다 하나. 기기 목록(키 해시·권한), 1회용 연결 코드, 숲 상태 암호문(판 번호 맞추기: 본 판이 맞을 때만 받고 아니면 409 + 최신 판), 진행 상태 암호문, 만료 알람 |
| `src/share.ts` | `ShareRoom`: 공유 링크마다 하나. 진행률·남은 시간·단계·선택적 5km 격자. 도착·중단·24시간이면 삭제 |
| `src/rules.ts` | 크기·기간 상한, 권한, 헤더·본문 검사(순수 함수) |
| `src/util.ts` | 응답·보안 헤더, 난수, SHA-256, base64url |
| `test/` | `rules.test.ts`(순수 함수), `flow.test.ts`(실제 Durable Object로 연결→승인→숲 상태 판 번호→진행 상태→끊기, 공유 링크) |

서버는 **숲·★ 규칙을 모른다**. 차·폰이 같은 Kotlin 규칙(`core:account`)으로 바꾼 뒤 올리고, 서버는 판 번호만 맞춘다(결정 #37 ⑥). 가족 데이터는 가족 열쇠로 암호화된 덩어리라 서버는 내용을 읽지 못한다.
저장하는 것: 기기 키·연결 코드의 SHA-256 해시, 암호문, 판 번호, 날짜 단위 마지막 접속. 로그에는 ID·키·본문을 남기지 않는다(`observability` 꺼짐).

## 실행

```bash
cd server
npm install            # .npmrc의 legacy-peer-deps=true 필요(vitest 선택 의존 문제)
npm run check          # 타입 검사(올리기 전 기본 확인)
npm test               # 단위 테스트(팀원이 시킬 때만)
npm run dev            # 로컬 서버 http://localhost:8787
```

## 배포 (Mac 세션이 팀원 Cloudflare 계정으로)

계정 ID·API 토큰은 저장소에 넣지 않는다(`wrangler.toml`에 `account_id`를 쓰지 않음). 실명이 아닌 중립 이름의 계정을 쓴다(블라인드 원칙).

1. **계정 준비(팀원, 한 번):** Cloudflare 무료 계정을 만든다. 대시보드 → Workers & Pages에 처음 들어가면 `workers.dev` 하위 이름을 정하라고 한다. 중립 이름(예: `acorn-ggf`)으로 정한다. 이 이름이 주소에 보인다.
2. **로그인(Mac, 한 번):**
   ```bash
   cd ~/Projects/familyjourney && git pull --no-rebase origin claude/lucid-babbage-a6quds
   cd server && npm install
   npx wrangler login        # 브라우저가 열리면 팀원 계정으로 "Allow"
   npx wrangler whoami       # 계정 이름이 보이면 성공
   ```
3. **타입 검사:** `npm run check` (아무것도 안 나오면 통과).
4. **배포:** `npm run deploy`. 처음 배포 때 Durable Object 이전(`v1`, SQLite)이 함께 적용된다. 끝에 `https://ggf-sync.<하위 이름>.workers.dev`가 나온다.
5. **확인:** `curl -i https://ggf-sync.<하위 이름>.workers.dev/v1/state` → `401 {"error":"unauthorized"}`와 `cache-control: no-store`가 보이면 서버가 살아 있다.
6. **앱에 주소 알리기:** 나온 주소를 총괄 PM에게 알린다. 앱은 `gradle.properties`의 `sync.serverUrl`로 읽는다(저장소에는 주소만, 비밀값 없음).
7. **시험용 가족 넣기(선택):** `tools/sync/seed-family.mjs` 사용법 참고(키는 내 PC의 `.seed/`에만 남는다).

다시 배포할 때는 4만 하면 된다. 서버를 지우려면 `npx wrangler delete`(모든 가족 기록이 지워진다).
