# 공유 서버 API 규격 (초안, 2026-11-06)

차 앱·폰 앱 ↔ 공유 서버(`server/`, Cloudflare Workers + Durable Objects)의 약속. 설계 배경은 `docs/architecture.md` 19장. 서버 구현: `server/src/`(2026-11-06 뼈대). 짧은 코드 연결(QR 없이)도 구현(2026-11-06).

## 공통

- 주소: `https://<서버>/v1/...` (HTTPS만). 본문은 JSON(UTF-8).
- 인증: `Authorization: Device <가족ID>.<기기ID>.<기기키>` 헤더(가족 ID 22글자, 기기 ID 11글자, 기기 키 43글자, 모두 base64url). **URL에 토큰을 넣지 않는다.** 기기 키는 기기가 만든 256비트 랜덤, 서버는 SHA-256 해시만 저장.
- QR 내용(서버를 거치지 않음): `ggf1.<가족ID>.<연결 코드 22글자>.<가족 열쇠 43글자>`. 주소가 아니라 브라우저로 열리지 않는다.
- 암호문(`blob`): 가족 열쇠(AES-256-GCM)로 암호화한 `{"v":1,"iv":"…","ct":"…"}`. 서버는 내용을 모른다. AAD(추가 인증 데이터)에 `familyId`와 종류(`state`/`request`/`status`)를 넣어 덩어리를 다른 곳에 옮겨 붙이지 못하게 한다.
- 오류: `{"error":"<코드>"}` + HTTP 상태. 코드: `unauthorized`, `forbidden`, `not_found`, `expired`, `conflict`, `too_large`, `rate_limited`, `bad_request`.
- 모든 응답: `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex`.

## 1. 가족·연결

| 메서드·경로 | 누가 | 본문 → 응답 | 설명 |
| --- | --- | --- | --- |
| `POST /v1/families` | 차(처음) | `{deviceKeyHash, nameBlob}` → `{familyId, deviceId}` | 가족과 차 기기를 만든다. 가족 ID는 서버가 128비트 난수로 정함. 가족 열쇠는 차 안에만 |
| `POST /v1/pairings` | 차 | `{codeHash, shortCodeHash?, wrappedKey?}` → `{expiresAt}` | 1회용 연결 코드 등록(5분). 코드는 차가 만들고 해시만 보냄. 짧은 코드도 함께 열면 서버 표에 적는다 |
| `POST /v1/pairings/claim` | 폰(인증 없음) | `{familyId, code, deviceKeyHash, nameBlob}` → `{deviceId, status:"pending"}` | 코드 확인 후 바로 지움. 5번 틀리면 코드 폐기, 차가 다시 만들어야 함 |
| `POST /v1/pairings/claim-short` | 폰(인증 없음) | `{shortCode, deviceKeyHash, nameBlob}` → `{familyId, deviceId, status:"pending", wrappedKey}` | 짧은 코드(12글자, 헷갈리는 0·1·O·I 없음, "-"·소문자 허용)로 연결. 서버 표(`PairingDirectory`)에서 가족을 찾아 한 번만 쓴다. `wrappedKey`는 코드에서 만든 열쇠(PBKDF2-SHA256 10만 번, salt `ggf1-short-v1`, AES-GCM AAD `ggf1|short|key`)로 감싼 가족 열쇠 — 서버는 코드 해시만 알아 풀 수 없다(단, 코드가 짧아 QR보다 약함: 시연용) |
| `PUT /v1/me/name` | 차·폰(승인 전도) | `{nameBlob}` → `{}` | 이 기기 표시 이름 바꾸기(짧은 코드로 연결한 폰이 열쇠를 받은 뒤) |
| `GET /v1/pairings/pending` | 차 | → `[{deviceId, nameBlob, claimedAt}]` | 승인 대기 폰 |
| `POST /v1/devices/{id}/approve` | 차 | `{roles:["forest","trip","location"], wrappedKey?}` → `{}` | 보호자가 차 화면에서 [연결]을 누른 뒤. `wrappedKey`는 글자 코드로 연결할 때만 |
| `GET /v1/me` | 폰 | → `{status:"pending"|"approved", roles, wrappedKey?}` | 승인 대기 중 조회 |
| `GET /v1/devices` | 차 | → `[{deviceId, nameBlob, roles, lastSeenDate}]` | 연결된 기기 목록 |
| `DELETE /v1/devices/{id}` | 차(아무 기기) · 폰(자기만) | → `{}` | 바로 끊기 |
| `DELETE /v1/families/me` | 차 | → `{}` | 가족 기록 전체 삭제 |

## 2. 숲 상태 — 판 번호 맞추기 (권한 `forest`, 차는 늘, 결정 #37 ⑥)

가족 계정(★·마일리지·아이·숲·꾸미기) 전체를 가족 열쇠로 암호화한 덩어리 하나와 판 번호를 둔다. 차·폰 어느 쪽이든 같은 Kotlin 규칙(`core:account`)으로 바꾼 뒤 **본 판 번호**를 붙여 올린다.

| 메서드·경로 | 누가 | 본문 → 응답 | 설명 |
| --- | --- | --- | --- |
| `GET /v1/state?since=<판>` | 차·폰 | → `{version, blob}`, 아직 없으면 `{version:0, blob:null}`, 바뀐 것 없으면 304 | |
| `PUT /v1/state` | 차·폰 | `{baseVersion, blob}` → `{version}` | 서버 판 = `baseVersion`일 때만 받고 판을 1 올린다. 아니면 **409** `{error:"conflict", version, blob}`(최신 판) → 올린 쪽이 최신 판에 같은 동작을 다시 적용해 또 올린다 |

- 처음 올릴 때 `baseVersion`은 0.
- AAD 종류는 `state`. 덩어리 안 형식은 `core:sync`의 `FamilySnapshot`(계정 JSON, 별명 평문 — 덩어리 전체가 암호문이라).

## 3. 우리 차 진행 (권한 `trip`, 위치는 `location`)

| 메서드·경로 | 누가 | 본문 → 응답 | 설명 |
| --- | --- | --- | --- |
| `PUT /v1/status` | 차 | `{blob, locationBlob?, expiresAt}` → `{expiresAt}` | 진행률·남은 시간·단계. 대략 위치(5km 격자)는 따로 암호화해 `location` 권한 폰에만 준다. 도착 또는 24시간 뒤 알람으로 삭제 |
| `GET /v1/status` | 폰 | → `{blob, locationBlob?, updatedAt}` 또는 404 | |
| `DELETE /v1/status` | 차 | → `{}` | 위치 보내기 멈추기: 바로 삭제 |

## 4. 기다리는 가족용 공유 링크 (결정 #8, 앱 설치 없음)

가족 열쇠가 없는 웹이라 **암호화하지 않은 최소 정보**(진행률·남은 시간·보호자가 정한 짧은 이름·선택 시 5km 격자)만 쓴다.

| 메서드·경로 | 누가 | 설명 |
| --- | --- | --- |
| `POST /v1/shares` | 차 | `{writeKeyHash, displayName?}` → `{shareId}`(128비트 랜덤, 읽기 전용) |
| `PUT /v1/shares/{shareId}` | 차 (`Authorization: Share <쓰기키>`) | `{progress, remainingMin, phase, grid?}` |
| `GET /v1/shares/{shareId}` | 웹 | 마지막 상태. 도착·24시간·중단 뒤에는 `410 expired` |
| `DELETE /v1/shares/{shareId}` | 차 | 바로 삭제 |

웹 주소: `https://<서버>/s/<shareId>`(공유 ID만 주소에 있음).

## 5. 갱신 주기와 크기

- 숲 상태: 바뀔 때 바로(판 번호 맞추기). 폰은 앱이 앞에 있을 때 15초마다 `since`로 확인, 차는 주차 중 숲 화면이 열려 있으면 15초, 아니면 60초.
- 우리 차 진행: 여행 중 **5초마다**(결정 #37 ⑧). 폰은 앱이 앞에 있을 때 5초마다 조회하고 "N초 전 갱신"을 보인다. 백그라운드에서는 멈춘다.
- 크기 상한: 상태 256KB, 진행 8KB, 기기 9대(차 포함).
