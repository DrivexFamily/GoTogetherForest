# core:sync — 가족 동기화 공용 모듈 (결정 #37)

차 앱(`feature:link`)과 폰 앱(`phone/`)이 같이 쓴다. Pleos SDK를 직접 쓰지 않는다. 설계는 `docs/architecture.md` 19장, 서버 규격은 `docs/share-api.md`.

## 폰 화면이 부르는 공개 API

```kotlin
// 1) 한 번만 만든다(Application/컨테이너)
val storage = SyncStorage.create(context)                 // Keystore로 잠근 DataStore 파일 "family_sync"
val client  = HttpSyncClient(BuildConfig.SYNC_SERVER_URL)  // https만
val link    = PhoneLink(client, storage.secrets)

// 2) 연결(QR 화면)
val qr = PairingQr.parse(scannedText) ?: /* "같이가숲 QR이 아니에요" */
when (val r = link.claim(qr, phoneName = "엄마 폰")) {     // SyncResult<LinkCredentials>
    is SyncResult.Ok -> /* 승인 대기 화면 */
    is SyncResult.Failed -> /* r.status 404·410: 코드가 끝났어요(차에서 새 QR), 403: 코드가 달라요 */
    SyncResult.Offline -> /* 인터넷 연결을 확인해 주세요 */
    is SyncResult.Conflict -> Unit
}
link.status(creds)          // SyncResult<MyStatus(approved, roles)> — 승인 대기 중 3초마다
link.credentials()          // 저장된 연결 정보(없으면 연결 화면)

// 3) 숲·상점·보관함: 차와 같은 AccountRepository 창구
val sync = FamilySync(client, creds, storage.snapshots)
val repo: AccountRepository = SyncedAccountRepository(sync, allowCarOnly = false)
repo.account            // Flow<GuardianAccount?> — 숲 화면은 지금 차 화면과 같은 모델
repo.buyDecor(childId, item); repo.saveSpots(childId, map, spots); repo.unlock(item); repo.chooseTree(..); repo.chooseMap(..)
sync.state              // StateFlow<SyncState(account, version, pending, problem, lastSyncedAtEpochMs)>
sync.refresh()          // 앱이 앞에 있을 때 15초마다, 화면을 열 때 한 번

// 4) 우리 차 진행(trip 권한)
link.carStatus(creds)   // SyncResult<CarStatusView?(status: CarStatus(phase, progressPercent, remainingMinutes, measuredAtEpochMs), location: LocationGrid?)>
                        // 앱이 앞에 있을 때 5초마다. null이면 "지금은 달리는 중이 아니에요"

// 5) 이 폰 연결 끊기
link.leave(creds); sync.clearLocal()
```

- `SyncProblem`: `OFFLINE`(기기 안에는 바로 적용, 나중에 올림) · `UNLINKED`(끊김 → 연결 화면) · `FORBIDDEN`(권한 없음) · `BUSY`(잠시 뒤 다시) · `UNREADABLE`(열쇠가 다름) · `SERVER`.
- 폰에서는 `allowCarOnly = false`: 보호자 확인·아이 추가·여행 보상·초기화는 바꾸지 않는다(차에서만).
- ★가 모자라면 그 기기에서 바로 `NotEnough`. 다른 기기가 먼저 썼으면 다시 적용한 결과(`NotEnough` 등)를 돌려준다.
- 화면 맨 위 "운전 중인 분은 사용하지 마세요"(#20).

## 차가 부르는 API (`feature:link`)

`CarLink`(가족 만들기·새 QR·승인 대기·승인·기기 목록·끊기·모두 지우기), `CarStatusSender`(진행 5초), `SyncedAccountRepository(sync, allowCarOnly = true)`.

## 파일

| 파일 | 내용 |
| --- | --- |
| `FamilyCipher.kt` | 가족 열쇠 AES-256-GCM, AAD `ggf1|가족ID|종류`, `SealedBlob` |
| `PairingQr.kt` | QR 내용 `ggf1.<가족ID>.<코드>.<가족 열쇠>` |
| `LinkCredentials.kt` | 이 기기의 연결 정보, `DeviceRole` |
| `SyncStorage.kt` | Keystore로 잠근 DataStore(연결 정보·마지막 판) |
| `SyncClient.kt` | 서버 호출(HttpURLConnection), `SyncResult` |
| `AccountOp.kt` | 계정을 바꾸는 동작(409 뒤 다시 적용), 규칙은 core:account 그대로 |
| `FamilySync.kt` | 받아서-적용-올리기, 판 번호 맞추기 |
| `SyncedAccountRepository.kt` | 동기화되는 `AccountRepository` |
| `CarStatus.kt` | 우리 차 진행·대략 위치(5km 칸) 형식 |
| `FamilyLink.kt` | `CarLink`·`PhoneLink`·`CarStatusSender` |

## 알아 둘 것

- `core:account` → `core:trip` → `core:vehicle`(Pleos SDK, implementation)이라 폰 APK에도 Pleos 클래스가 따라 들어간다(호출하지 않으면 동작에는 영향 없음). 폰 APK 크기가 문제면 나중에 core:trip의 vehicle 의존을 나눈다.
- 테스트(`src/test`)는 작성만 했다(팀 규칙). 이름은 영어(이 환경에서 한글 메서드 이름이 클래스 파일 경로 오류를 낸다), 설명은 주석.
