> 검증 갱신: 실제Android/Compose core:ui컴파일과FrameSpec8개 Gradle검사 성공. 기존전체suite98/100(기존2실패), 앱/차량통합은대기. [최신검증기록](TEST-RESULTS.md). 아래SDK미설치표현은초기검사시점의기록입니다.

# P1 주차 중 일러스트 모션 연결

구현 브랜치: `chatgpt/parked-illustration-motion` · 코드 기준: `b42bf7edc119456e3f4ce1f70104f8dcc0bb24bf`(`b42bf7e`). 이 문서는 추가한 재사용 부품과 앱용 시트의 연결 계약이다. **기존 앱 화면·차량 서비스·효과음 서비스·설정 파일은 수정하지 않았다.** 화면 연결과 실제 차량 검수는 앱 담당 Claude PM이 수행한다.

## 제공하는 코드와 자산

패키지: `app.familyjourney.core.ui.motion`.

- `FrameSpec`: 행 우선 원본 셀 좌표, 명시적 활성 순서, 최종 정지 셀, 정확한 시트 크기 검증.
- `SpritePlayback`: 한 번의 요청에 1~3회, 총 3초 이내의 유한 재생.
- `ParkedSprite`: 한 번 디코딩한 `ImageBitmap`에서 Canvas source rect만 바꿔 그린다.
- `FamilyArrivalMotion`: 3×2/512px 가족 시트 wrapper. 활성 셀 순서를 명시해야 한다.
- `TreeGrowthMotion`: 4×1 성장 wrapper. 기본 셀 크기는 실제 시트 폭/4와 높이에서 정한다.
- `MotionAssets`와 `MotionAssetSprite`: 이 묶음의 실제 drawable 및 검수한 순서를 연결한다. 사용 중인 시트만 디코딩하며 13개 전체를 선행 디코딩하지 않는다.

13개 앱용 WebP와 출처/원본/파생본 SHA는 [ASSET-PROVENANCE.json](ASSET-PROVENANCE.json)에 있다. 아빠 JUMP는 실제 픽셀 머리 크기를 WAVE와 맞춘 v2로 교체했습니다. WAVE와 다른 가족은 v1을 유지했습니다. 13개 모두 원본·납품 사본·앱 WebP SHA를 최종 확인했으며 Android/실차 통합 검수는 남아 있습니다.

| 자산 묶음 | 시트/셀 | 활성 원본 셀 | 실제 재생 순서 → 정지 | 길이 |
| --- | --- | --- | --- | --- |
| RabbitHop / SquirrelHop / BluebirdFly | 1536×1024, 3×2, 512×512 | 0~3 | 0,1,2,3 → **0** | 800ms |
| RabbitBlink / SquirrelBlink / BluebirdBlink | 동일 | 0~1 | 0,1,0 → **0(뜬눈)** | 600ms |
| DadWave / MomWave / GirlWave | 동일 | 0~2 | 0,1,2 → **0** | 900ms |
| DadCheer / MomCheer / GirlCheer | 동일 | 0~3 | 0,1,2,3 → **3** | 900ms |
| OakGrowth | 1536×1024, 4×1, 384×1024 | 0~3 | 0,1,2,3 → **3** | 1200ms |

빈 셀은 재생하지 않는다. 정지 상태의 동물 IDLE이 감은 눈으로 남지 않도록 최종 프레임은 0이다. 나무는 마지막 성장 단계가 남는다. 원본 파일에 이미지 필터·색보정·프레임별 재정렬을 적용하지 않는다. 앱은 source rect 표시를 최대 원본 크기로 제한하고 작은 영역에는 비율 유지 축소만 한다. 원본보다 큰 컨테이너의 나머지 공간은 비워 둔다.

시트 bbox의 아래 끝은 발이 아니다. 점프의 분리된 그림자, 파랑새 날개 끝이 bbox 최하단일 수 있다. 프레임별 발을 y=460으로 다시 정렬하면 점프 높이가 사라진다. 기존 셀 배치와 움직임 높이는 보존한다. 부모·아이의 같은 512셀 안 실루엣 높이가 비슷할 수 있어 실제 화면의 가족 상대 표시 크기는 별도 검수한다.

프레임별 발/신발/뿌리의 기존 근사 좌표와 전체 실루엣 하단은 [ANCHOR-QA.md](ANCHOR-QA.md), 원래 ROI·측정 근거·미측정 상태는 [ANCHOR-QA.json](ANCHOR-QA.json)에 분리해 기록했다. 그림자 단독 좌표나 해부학적 발의 정확한 (x,y)는 원 명세에 없으므로 새로 만들어 채우지 않았다. 아빠 교체 후 측정 갱신 스크립트도 같은 폴더에 있다.

## 앱이 제공할 정책과 주차 게이트

`LocalMotionPolicy`의 기본값은 **`MotionPolicy.STILL`**이다. 값을 제공하지 않으면 움직이지 않는다. 시각 코드는 차 그림이나 화면 이름으로 주차 여부를 추측하지 않는다.

앱은 실제 `CarUxRestrictions` 제한 해제와 기어 **P**(또는 기존 계약의 검증한 주차브레이크 조건)를 결합한 주차 허용 값을 `MotionPolicy.parked`로 제공한다. 단순 차속 0, 신호 대기, 시연용 P만으로 실제 차량 주차를 대신하지 않는다. 시스템/앱 동작 줄이기를 `reduceMotion`에 반영한다. 실제 차량 정책이 데모보다 우선한다.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import app.familyjourney.core.ui.LocalMotionPolicy
import app.familyjourney.core.ui.LocalSoundPort
import app.familyjourney.core.ui.MotionPolicy
import app.familyjourney.core.ui.SoundPort
import app.familyjourney.core.ui.motion.MotionAssets
import app.familyjourney.core.ui.motion.MotionAssetSprite

@Composable
fun ParkedArrivalArtwork(
    arrivalEventId: String,
    motionPolicyFromApp: MotionPolicy,
    soundPortFromApp: SoundPort,
) {
    CompositionLocalProvider(
        LocalMotionPolicy provides motionPolicyFromApp,
        LocalSoundPort provides soundPortFromApp,
    ) {
        MotionAssetSprite(
            asset = MotionAssets.MomWave,
            playKey = arrivalEventId,
            modifier = Modifier.size(220.dp),
        )
    }
}

@Composable
fun NewlyPlantedOak(plantingEventId: String) {
    MotionAssetSprite(
        asset = MotionAssets.OakGrowth,
        playKey = plantingEventId,
        modifier = Modifier.size(width = 144.dp, height = 320.dp),
    )
}
```

두 번째 예제는 상위의 실제 정책을 상속한다. 상위 provider가 없으면 정지 나무다. 같은 방식으로 `MotionAssets.RabbitBlink`를 사용할 수 있다. 아빠 JUMP v2의 원본/정렬 사본 계보는 `DAD-JUMP-v2-ALIGNMENT.json`에 있다. `MotionAssets`는 drawable 정수만 보관하며 비트맵을 미리 모두 읽지 않는다. 직접 `ParkedSprite`를 부를 때는 호출자가 이미지 리소스를 한 번 기억해 전달하고 `drawable-nodpi`로 실제 픽셀 크기를 보존한다. 실제 크기가 `FrameSpec`과 다르면 임의 보정하지 않고 명확한 예외로 잘못된 바인딩을 드러낸다. 앱 연결 전 자산 규격을 확인하고, 누락/지원하지 않는 시트는 기존 정지 그림으로 대체한다.

## 정지·재생 종료·중간 정책 변경

최종 셀은 `FrameSpec.finalFrame`이며 정지 상태가 첫 셀과 같아야 한다고 가정하지 않는다. 시작 0과 종료 1을 구분하는 유한 시계로 처음에 최종 그림이 번쩍이거나 끝에서 첫 셀로 잘못 복귀하는 경우를 피했다.

- `policy.canAnimate=false`이면 현재 박자와 무관하게 **즉시 최종 정지 셀**이다. 주행 제한이 켜져도 계속 재생하는 전환 효과를 넣지 않는다.
- `reduceMotion=true` 및 시스템 `MotionDurationScale=0`에서도 최종 정지 상태다. 시스템 배율은 컴포지션과 매 프레임에서 확인한다. 중간 설정 변경 시 처리까지 실제 Compose 런타임 검수가 필요하다.
- `Lifecycle.STARTED` 아래에서는 프레임 시간을 누적하거나 그림 상태를 계속 갱신하지 않는다. 복귀하면 남은 시간만 진행하고 백그라운드 시간을 따라잡지 않는다. 컴포지션 제거 시 코루틴은 취소된다.
- 동작 줄이기의 정지 그림과 텍스트가 같은 정보를 제공해야 한다. 색·움직임·소리만으로 도착/획득을 설명하지 않는다.

## 안정된 사건 ID와 한 번의 연출

`playKey`에 도착/획득/나무 심기의 **안정적인 사건 ID**를 준다. 프레임 시간·현재 시각·매 재구성의 새 객체·무작위 값을 키로 만들지 않는다. 같은 키·시트·규격·정책의 일반 재구성은 새 재생을 시작하지 않는다.

**같은 사건을 영속적으로 한 번만 축하하는 책임은 앱의 사건 저장/도착 큐에 있다.** 검증한 도착 ID를 먼저 저장하고, 주차 후 그 단건 사건을 표시하며, 재방문·반복 콜백이 획득 수를 다시 늘리지 않게 한다. 이 부품은 획득/보상/큐 상태를 변경하지 않는다. 현재 부품은 정책 false→true, 시트/규격 변경, 컴포지션 재진입 때 새 짧은 재생을 할 수 있다. 따라서 앱의 표시 완료 기록 없이 이 부품만으로 프로세스 재시작까지 전역 1회가 보장된다고 간주하면 안 된다. 이미 확인한 사건의 모달을 다시 자동으로 열지 않는다. 의도한 사용자 탭 재생이라면 별도의 명시적 사건 키를 정한다.

## 소리는 선택이며 포트로만 요청

기본 소리는 없다. `MotionAssetSprite`는 자동으로 효과음을 요청하지 않는다. 부모 도착 화면이 이미 팡파르를 요청한다면 자식은 계속 무음으로 둔다. 시각 코드에서 실제 `MediaPlayer`/`SoundPool`/오디오 포커스 서비스를 새로 만들지 않았다.

하위 부품의 `soundCue`를 명시적으로 사용할 경우만 다음처럼 같은 검수 자산의 규격을 전달한다.

```kotlin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import app.familyjourney.core.ui.SoundCue
import app.familyjourney.core.ui.motion.ParkedSprite

@Composable
fun OneAcquisitionArtwork(eventId: String) {
    val asset = MotionAssets.OakGrowth
    val sheet = ImageBitmap.imageResource(asset.drawableRes)
    ParkedSprite(
        sheet = sheet,
        frames = asset.frames,
        playback = asset.playback,
        playKey = eventId,
        contentDescription = asset.description,
        soundCue = SoundCue.ACQUIRE,
    )
}
```

같은 컴포지션의 사건 키에 대해서는 정책 off→on으로 소리를 다시 요청하지 않는다. 생명주기 대기 직후에도 시스템 배율과 STARTED 상태를 재확인한다. 이 값들은 요청 조건이며 실제 재생 허가가 아니다. `LocalSoundPort` 기본값은 무음 no-op이다. 실제 앱 포트가 기본 음소거·주차·오디오 포커스·통화/내비 안내·사건 중복·주행 재개 시 소리 중단을 담당한다. 이 포트에는 stop 메서드가 없으므로 시각 부품이 실제 서비스의 재생을 직접 중단한다고 주장하지 않는다. 포커스 거절/음소거/재생 실패에도 정지 최종 그림과 도착 문구는 완료한다.

## 테스트 결과와 앱 담당의 남은 검수

확인한 실행 기록은 [TEST-RESULTS.md](TEST-RESULTS.md), [P1-test-status.json](P1-test-status.json), [P1-jvm-test.log](P1-jvm-test.log)다. **순수 JVM JUnit 테스트 8개 통과**는 확인됐지만 **Android SDK 위치가 없어 전체 Gradle Android 빌드는 차단**되어 있다. Compose/Kotlin UI 컴파일, 에뮬레이터/차량 런타임 성공으로 확대 보고하지 않는다.

연결 전 검수:

1. Android SDK 35/빌드 도구와 기존 프로젝트의 JDK/Gradle 환경에서 `:core:ui:testDebugUnitTest`와 앱 빌드.
2. 실제 P/주행 제한 신호 전환 시 즉시 정지 최종 셀, 시스템 배율 0 중간 변경, 앱 동작 줄이기.
3. 화면 백그라운드/복귀 중 프레임·소리 정책과 사건 중복 표시 방지.
4. 처음/끝 셀, 뜬눈 IDLE, 가족 상대 키·발 흔들림·점프 높이·나무 성장 단계, 좁은 창/가로·세로 표시.
5. 아빠 JUMP v2와 가족 상대 크기를 실제 화면에서 확인. 최종 출처 SHA/정렬 근거는 `ASSET-PROVENANCE.json`과 `DAD-JUMP-v2-ALIGNMENT.json`에 기록했다.

이 문서의 코드 조각은 연결 예제이며 기존 화면 파일에 적용한 완료 보고가 아니다.
