package app.familyjourney

import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.vehicle.SignalMode

/**
 * 차량 신호를 받을지 정하는 규칙(순수 함수, JVM 테스트 대상). AppContainer가 화면 보임·여행 단계·데이터 모드가 바뀔 때마다
 * 이 값으로 `vehicleData.start()/stop()`을 부른다.
 *
 * | 화면 | 여행 단계 | 데이터 모드 | 신호 |
 * | --- | --- | --- | --- |
 * | 보임 | 무엇이든 | 무엇이든 | 받는다 |
 * | 가려짐(백그라운드) | 주행 중·정차 중(출발 뒤 도착 전) | 실차 | **받는다**: 여행 기록(거리·안전 판정·도착)이 끊기지 않게 |
 * | 가려짐 | 도착했고 아직 축하 전(도착 뒤 주차를 못 봄) | 실차 | **받는다**: 주차를 확인하면 무드등을 바로 켠다(decisions.md #27-③) |
 * | 가려짐 | 출발 전·도착(축하 뒤) | 무엇이든 | 멈춘다(Pleos 규칙: 백그라운드에서 불필요한 센서 수집 중단) |
 * | 가려짐 | 무엇이든 | 데모 | 멈춘다: 데모는 재생이라 멈췄다가 다시 보면 이어서 튼다(전과 같음) |
 *
 * 화면 갱신·애니메이션은 이것과 상관없이 Compose 수명 규칙으로 멈춘다(`collectAsStateWithLifecycle`, 프레임 시계).
 * 음성은 화면이 보일 때만 낸다(JourneyController canSpeak). 가려진 채 도착하면 감사 인사는 다시 보일 때 1번(도착 뒤 10분 안).
 *
 * @param celebrated 도착 축하를 했나(TripState.celebrated). 도착 단계에서만 본다
 */
object SignalCollection {
    fun needed(appVisible: Boolean, phase: TripPhase, mode: SignalMode, celebrated: Boolean): Boolean = when {
        appVisible -> true
        mode != SignalMode.REAL -> false
        phase == TripPhase.DRIVING || phase == TripPhase.STOPPED -> true
        else -> phase == TripPhase.ARRIVED && !celebrated
    }
}

/**
 * 앱 화면이 보이나: 시작된(onStart~onStop) Activity 수를 센다. Activity가 새로 만들어질 때는 새 쪽의 onStart가
 * 옛 쪽의 onStop보다 **먼저** 온다(Android 순서). 참/거짓 하나로 두면 마지막 onStop이 "가려짐"으로 덮어써서,
 * 화면이 보이는데도 신호가 멈출 수 있다. 메인 스레드(수명 콜백)에서만 부른다.
 */
class VisibleActivities {
    private var started = 0

    val visible: Boolean get() = started > 0

    fun onStart(): Boolean {
        started++
        return visible
    }

    fun onStop(): Boolean {
        started = (started - 1).coerceAtLeast(0)
        return visible
    }
}
