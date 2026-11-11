# FusedLocation 1.1.3 와 TextToSpeech 3.3.1 (둘 다 "준비 중")

기준: `docs/pleos-dev-guide.md` 3-4, 3-5, 4-5장. My Project에서 두 SDK는 "준비 중(Coming Soon)"이다. 공모전 설명회에서는 "권한 선언만으로 사용 가능"이라고 했다. 그래서 쓰되, **없을 때도 앱이 그대로 동작**하게 만든다. 11/15 MVP에서는 둘 다 필수가 아니다.

## FusedLocation 1.1.3

- 의존성: `implementation("ai.pleos.playground:FusedLocation:1.1.3")` (실제로 쓸 때만 추가)
- 권한: `pleos.car.permission.FUSED_LOCATION` — dangerous 권한이라 **런타임 요청**이 필요하다. 요청 화면은 주차 중에만.
- 흐름: `FusedLocation(ctx)` → `isSupported()` → 권한 → `initialize()` → `getLastKnownLocation()`(null 가능) 또는 `registerFusedLocationCallback(listener)` → unregister → `release()`
- 리스너: `onChanged(LocationData?)`(null 가능), `onError(Exception)` 둘 다 구현해야 한다.
- `LocationData`: latitude, longitude, altitude, **heading**, confidence, timestampMillis. **속도 필드는 없다**(차속은 CarPropertyManager).
- 갱신 주기 파라미터가 없다. 관찰상 약 1초.
- `addProximityAlert()`(ENTER/EXIT): 랜드마크 접근 감지(랜드마크는 시안만, 창밖동화와 겹침 검토 대기).
- `showLocationAccessBlockedPopup()`: 위치 접근이 막혔을 때 안내 팝업.
- Android 표준 위치 권한의 동작은 보장되지 않는다. 표준 LocationManager에 기대지 않는다.
- 좌/우 계산: heading과 POI 방위각의 차이로 판단한다.
- 위치 공유(PT 단계)는 정확한 좌표를 보내지 않는다. 기기 안에서 약 5km 격자로 반올림한 값만, 보호자가 주차 중에 켰을 때만 보낸다(decisions.md #8).
- 에뮬레이터 위치 주입: `adb shell appops set --user current <패키지명> android:mock_location allow` 후 `adb emu geo fix <경도> <위도>`(경도가 먼저).

## TextToSpeech 3.3.1 (2026-11-01 사용 시작: core:voice `PleosTtsVoiceOutput`)

- 의존성: `implementation("ai.pleos.playground:TextToSpeech:3.3.1")` (core:voice). 전이 의존: `ai.pleos.playground.security:universal-access:0.6.0`(네이티브 `libuacore.so` 등, APK 크기 증가), gson, androidx.core
- 권한: `pleos.car.permission.TTS_SERVICE`(모든 함수 `@RequiresPermission`, 없으면 `SecurityException`). aar이 `android.permission.INTERNET`을 병합 매니페스트에 넣는다
- 모드: `TextToSpeech(context, Mode.ON_DEVICE)`(`Mode` = SERVER, ON_DEVICE, HYBRID). ON_DEVICE는 네트워크·앱 등록이 필요 없고 에뮬레이터에서 소리 확인(외부 실측). SERVER는 열쇠 없이 `onError UNKNOWN`
- 순서(javap 확인, 외부 실측에서 소리가 난 순서): `initialize(OnInitializeListener)` → `onSuccess()` **뒤에** `addEventListener(EventListener)` → `setAudioAttributes(AudioAttributes)` → `speak(text, isFinal = true)` → `stop()` → `removeEventListener()` → `release()`. 먼저 `addEventListener`를 부르면 `lateinit property tts` 예외
- `setLocale(Locale)`은 부르지 않는다(2026-11-01). SDK가 `Locale.toLanguageTag()`(`Locale.KOREAN` → `"ko"`)를 그대로 엔진에 넘기는데, 이것을 넣은 순서에서 에뮬레이터 `onError(ON_DEVICE_ENGINE_ERROR)`가 났다(원인 미확인). 기본 언어로 한국어 소리가 났다(외부 실측)
- `ON_DEVICE_ENGINE_ERROR`: 앱은 리스너 해제 → `release` → 새 객체로 `initialize`해 한 번만 다시 말한다. 그래도 같으면 `adb shell am force-stop ai.pleos.playground.caas` 후 다시(사람)
- `EventListener`: `onStart`, `onReady`, `onDone`, `onError(ErrorType)`, `onStop`, `onUpdatedRms(Double)`. `OnInitializeListener`: `onSuccess`, `onFailure(ErrorType)`
- 소리가 안 나면 `adb shell am force-stop ai.pleos.playground.caas`(음성 서비스 재시작)
- **Client Secret은 저장소에 커밋하지 않는다.**
- 이 앱에서의 용도: 랜드마크 음성(시안만), 아이용 짧은 문구 읽기(선택). 소리는 주차 중에만이라는 결정(#7)과 충돌하지 않게, 주행 중 음성은 따로 결정이 필요하다.

## 오디오 포커스 (소리를 내는 모든 기능)

- 재생 전 `AudioFocusRequest`를 반드시 요청한다.
- 포커스 이벤트 처리: LOSS → 중지, LOSS_TRANSIENT → 일시정지, CAN_DUCK → 볼륨 감쇄.
- 안내 음성은 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`(Pleos 가이드). 용도는 `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` + `CONTENT_TYPE_SPEECH`를 쓴다(외부 실측에서 소리 확인). `USAGE_ANNOUNCEMENT`는 AAOS 시스템 용도라 일반 앱이 쓰기 어렵다. 숲 동물 소리도 #7에 따라 `GAIN_TRANSIENT_MAY_DUCK`.
- 음성 앞에 150~200ms 묵음을 둔다.
- Suspend(IGN OFF) 전에 재생 중지·포커스 반납·상태 저장.
- 음소거 버튼을 둔다. 소리 에셋은 직접 제작 또는 CC0, `docs/assets-license.md`에 기록.

## SDK가 없을 때

| 상황 | 앱의 동작 |
| --- | --- |
| FusedLocation `isSupported()` false / 권한 거부 | 위치 기반 기능(대략 위치 공유, 랜드마크) 숨김. 진행률 공유는 그대로 |
| TTS 초기화 실패 | 음성 없이 텍스트만 |
| 둘 다 없음 | 진행 화면·숲은 정상 동작 |
