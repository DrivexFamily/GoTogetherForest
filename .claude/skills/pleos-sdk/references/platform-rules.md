# Pleos 플랫폼 규칙 (주행 규제·화면·디자인·성능·매니페스트·보안·콘텐츠)

기준: `docs/pleos-dev-guide.md` 4장. 이 앱의 결정은 `docs/decisions.md`(#4 운전자 방해 방지)를 따른다.

## 목차

1. 주행 규제와 DO
2. 화면 구조
3. 디자인 가이드
4. 성능·자원
5. 매니페스트
6. 보안·개인정보
7. 콘텐츠
8. 앱 마켓 등록(참고)

---

## 1. 주행 규제와 DO

- **DO(Distraction Optimization)를 선언하지 않은 앱은 주차 중에만 열린다.**
- Pleos 문서에 DO 선언 방법이 없다(미확인). AAOS 표준은 Activity의 `<meta-data android:name="distractionOptimized" android:value="true"/>`. 운영 측 답변을 기다린다.
- 이 앱의 결정(#4): DO를 선언한다. 허용되지 않으면 매니페스트만 바꿔 주차 전용 앱으로 바꾼다. 그래서 DO 선언은 매니페스트 한 곳에 모아 둔다.
- DO를 선언해도 제한 상태를 직접 받아 기능별로 막아야 한다.

```kotlin
val uxm = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
val now = uxm.currentCarUxRestrictions            // 시작할 때 현재 값
uxm.registerListener { r ->                        // 바뀔 때마다
    val driving = r.isRequiresDistractionOptimization
    val noSetup = r.activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP != 0
    // StateFlow로 전달 → 화면이 주행/주차 상태를 고른다
}
// 정리: uxm.unregisterListener()
```

| 플래그 (`CarUxRestrictions`) | 값 | 북미 | 유럽 | 그 외(한국 추정) |
| --- | --- | --- | --- | --- |
| `UX_RESTRICTIONS_LIMIT_STRING_LENGTH` | 4 | 제한 | 제한 | 제한 |
| `UX_RESTRICTIONS_NO_KEYBOARD` | 8 | 제한 | 허용 | 허용 |
| `UX_RESTRICTIONS_NO_VIDEO` | 16 | 제한 | 제한 | 제한 |
| `UX_RESTRICTIONS_LIMIT_CONTENT` | 32 | 제한 | 제한 | 제한 |
| `UX_RESTRICTIONS_NO_SETUP` | 64 | 제한 | 제한 | 미디어 앱만 허용 |
| (참고) `UX_RESTRICTIONS_FULLY_RESTRICTED` | 511 | | | |

- QR 로그인은 모든 지역에서 주행 중 금지.
- 플랫폼이 지역 규정에 맞춰 계산해 준다. 앱에 지역 로직이나 속도 임계값을 넣지 않는다(임계값은 비공개).
- 이 앱의 주차 전용 기능(NO_SETUP 대상으로 본다): 공유 링크 생성·설정, 동의, 로그인·계정, 아이 프로필 생성, 캐릭터·숲 꾸미기, 숲 화면, 소리, 안전운전 판정 결과.
- 주차 전용 기능은 UX 제한 해제에 더해 기어 P(또는 주차브레이크)까지 확인한다(#4).
- 데모 재생과 실제 신호가 엇갈리면 **실제 제한이 이긴다**(#4).
- 주행 중 금지(내비 가이드 준용): 장식·반복 애니메이션, 움직이는 이미지, 자동 스크롤, 게임 요소, 전체화면 팝업, 3단계 초과 메뉴, 긴 리스트 스크롤, 동영상, 광고. 광고·게임 요소는 즉시 반려.
- 지역·플랫폼 확인: 시스템 속성 `product.sw.target_region`(KOR/EUR/USA/CAN), `Build.DEVICE`(CONNECT_S/L/W, 실차만).
- Connect-W 조수석 창: 조수석에서 실행한 앱은 조수석 창에서만 열린다. 조수석 전용 규제 완화 규칙은 문서에 없다 → **조수석이라고 제한을 완화하지 않는다**(#4).

## 2. 화면 구조

- 디스플레이: Connect-S(17", 14.6", 12.9"), Connect-W, Connect-W-Extended(주행 상태에 따라 높이 확장), Connect-WF(저해상도). px/dp/safe area는 미확인 → 에뮬레이터 preset으로 확인.
- 운전자 쪽 Driving view는 고정이고 앱이 가릴 수 없다. 앱 영역에 최대 2개 앱. 지도 위 오버레이는 불가.
- `screenOrientation` 미지정 → 세로 1창, `landscape` → 2창. **세로·가로 레이아웃을 모두 만든다.** 리사이즈 후에도 핵심 기능이 동작하고 상태가 유지되어야 한다.
- 창 비율: Connect-S는 앱 2개면 1:1, 1개면 2:0. Connect-W는 2:1 또는 1:2.
- 뒷좌석 디스플레이 언급은 없다.
- 백그라운드로 가면 애니메이션·화면 갱신·불필요한 센서 수집을 멈춘다. 프로세스 재생성에 대비해 상태를 저장한다.
- 레터박스·호환 모드 금지, safe area 준수.

## 3. 디자인 가이드

- 1~2초 안에 파악(Glanceability). 입력 반응 0.25초 이내(넘으면 처리 중 표시).
- 대비 4.5:1 이상. 핵심 텍스트 높이 5mm 이상, 보조 3mm 이상.
- 텍스트: 영문 기준 120자/24단어 이하, 한 줄 80자 이하, 3줄 이하.
- 폰트: 시스템 폰트 **Asta Sans**. "캐릭터가 강조된 폰트"는 지양 → 손그림 느낌은 그림으로만. 최소 12sp, 줄간격 1.5배.
- 아이콘: 48×48dp 그리드, 텍스트 라벨 병기. 앱 아이콘은 Adaptive Icon.
- 애니메이션은 상태 설명 목적으로만.
- Day/Night 자동 전환 → 라이트·다크 테마 모두.
- 그림자 L1~L3 토큰 제공. 색 hex·타입 스케일·최소 터치 크기는 미확인 → Figma 리소스 확인.
- 광고 금지.

## 4. 성능·자원

| 항목 | 기준 |
| --- | --- |
| CPU (주행 중, 비내비 앱) | **5% 미만** |
| CPU (정차 중) | 25% 이하 |
| RAM | 400MB 이하 |
| 터치 응답 | 0.25초 이하 |
| 앱 실행 / 콘텐츠 로딩 | 10초 이내 |
| 버튼 응답 | 2초 이내, 2초 넘는 작업은 진행 UI |

- 백그라운드 복귀 시 마지막 상태 복원. 더미·테스트 데이터를 출시 빌드에 남기지 않는다(데모 모드는 명시적으로 켜는 기능이고 배지로 표시하므로 별개).
- 설치·삭제가 깔끔해야 한다.
- 네트워크: 캐싱, 재시도·타임아웃, 오프라인 표시.

## 5. 매니페스트

```xml
<!-- 이 앱에서 쓰는 것만 선언한다. 최종 목록은 docs/sdk-capabilities.md -->
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />
<uses-permission android:name="android.car.permission.CAR_SPEED" />
<uses-permission android:name="android.car.permission.CAR_POWERTRAIN" />
<!-- 실제로 쓸 때만: FUSED_LOCATION, TTS_SERVICE, android.permission.INTERNET -->

<application ...>
    <!-- CCP(로터리) 미지원 선언 -->
    <meta-data android:name="com.hmg.rotary.ROTARY_SUPPORTED" android:value="false" />
    <activity ...>
        <!-- DO 선언: AAOS 표준 방식. Pleos 확인 대기 -->
        <meta-data android:name="distractionOptimized" android:value="true" />
    </activity>
</application>
```

- **선언 금지:** `android.intent.category.APP_MAPS`(내비 앱 전용, 카테고리 불일치 즉시 반려), `android:appCategory="audio"`(오디오 주력 앱 전용).
- 내비 앱이 아닌 앱이 턴 안내·음성 길안내를 하면 카테고리 위반 위험 → 진행률만 보여 준다.
- CCP Back 키: 최상위 화면에서 `moveTaskToBack(true)`. `finish()`/`finishAffinity()` 금지. 터치만으로 모든 기능을 쓸 수 있어야 한다.
- 대화상자 IME 처리: `setFitInsetsTypes(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())`.
- 필요한 컴포넌트만 `exported=true`.

## 6. 보안·개인정보

- HTTPS, TLS 1.2 이상(1.3 권장). 앱과 무관한 서버와 통신하지 않는다.
- **토큰·세션·비밀번호를 URL path/query에 넣지 않는다.** 공유 링크는 추측 불가능한 랜덤 공유 ID(읽기 전용, 만료·폐기 가능)로 만들고, 쓰기 인증은 헤더로 한다. 이 해석이 규정에 맞는지는 운영 측 확인 대기.
- 민감정보(SharedPreferences·DataStore·SQLite 포함)는 암호화 저장. 키는 Android Keystore, 하드코딩 금지. 이 앱: 아이 별명·계정 정보는 AES-GCM(#11).
- 암호: SHA-256, AES-GCM. MD5, SHA-1, ECB 금지.
- 릴리스 빌드: `debuggable=false`, WebView 원격 디버깅 off, 로그 마스킹, 운영 인증서 서명.
- 동의: 수집 항목·목적·보유 기간·거부권을 알린 뒤 명시적 동의(**동의 버튼 탭만 인정**). 링크 수신자에게 보내는 것은 제3자 제공 동의. 아동 정보는 법정대리인 사전 동의. 철회 시 즉시 중단·삭제.
- 차량 ID·사용자 ID는 가명화. 운전 행동 데이터를 마케팅에 쓰지 않는다. 불필요한 위치를 저장하지 않는다.
- 차는 여러 사람이 쓴다 → 로그아웃·계정 전환이 쉬워야 한다.
- 제출 서류: 개인정보처리방침 URL(필수), 위치정보 이용약관(위치 사용 시), 위치정보사업 신고 증빙 요구 가능.

## 7. 콘텐츠

- 비인증 화면·스크린샷은 전체 이용가.
- **아이가 차 밖으로 신체를 내밀도록 유도하는 콘텐츠는 엄격히 금지.** 창밖 안내는 "오른쪽 창밖을 봐요"처럼 보기만 유도한다. 창문 열기 제어와 결합하지 않는다.
- 과속·음주·신호위반 미화 금지 → 빠른 도착이나 속도를 보상하지 않는다.
- 허가 없는 IP 사용 금지, 권리 증빙 요청 가능 → 캐릭터·폰트·이미지·음원 출처를 `docs/assets-license.md`에 기록.
- UGC·AI 생성 콘텐츠(사진 → 캐릭터 변환)는 필터링·신고 기능 필요.
- IARC 연령등급 필요.

## 8. 앱 마켓 등록 (공모전 이후 참고)

| 항목 | 규격 |
| --- | --- |
| 앱 이름 | 영문 30자 이하, 중복·유사 금지 |
| 아이콘 | 512×512 PNG/JPEG, 1MB 이하 |
| 스크린샷 | 2~8장, 장당 8MB, 2560×1380 / 1700×1212 / 840×1212 px |
| APK | APK만, 600MB 이하, 요청마다 버전 증가 |
| 태그 | 최대 20개, 영문 공식 앱명 필수 포함 |
| 고객지원 | 웹·이메일·전화 중 1개 이상 |

- 카테고리 8종: Maps/Navigation, Mobility Services, Lifestyle, Entertainment, Business, Music/Audio, Kids, Social/Communication. Kids/Entertainment의 주행 중 허용 규칙은 문서에 없다(운영 측 확인 대기).
- 자동 업데이트가 없다 → 앱이 버전을 확인하고 `market://detail?package={패키지명}`으로 보낸다.
- 제출 시 SDK 추가 권한 항목에 My Project의 Project ID를 넣는다. 심사 전 외부 서버를 켜 둔다.
