package app.familyjourney.core.vehicle.pleos

/**
 * NaviHelper 이벤트를 "경로 상태" 하나로 모은다. 안드로이드·SDK 클래스 없이 정수와 이름만 받는다(단위 테스트 대상).
 *
 * 진행률에 쓰는 값(docs/sdk-capabilities.md 3-2):
 *  - 남은 거리 R: DestinationInfo.remainDistance(조회 응답) 또는 DrivingInfo.destination.distance(주행 중 알림) 중 최근 값
 *  - 전체 거리 D0: RouteStartInfo.distance. 경로 시작을 놓쳤으면(앱이 안내 도중 켜짐) 처음 받은 R
 *  - 간 거리 = max(0, D0 − R), 진행률 = 간 거리 ÷ (간 거리 + R)   ← core:trip ProgressCalculator가 계산
 *
 * 이 클래스는 원시 단위(정수) 그대로 보관한다. km·분 변환은 [PleosVehicleConfig]의 단위 가정으로 한 곳에서 한다.
 *
 * 앱이 백그라운드에 다녀와도(내비 앱을 전체 화면으로 본 뒤 등) D0를 잃지 않도록 stop/start 사이에 상태를 지우지 않는다.
 * 그동안 목적지가 바뀌었는지는 목적지 좌표로 만든 열쇠([destinationKey])로 알아챈다. 열쇠는 메모리에만 두고 로그에 쓰지 않는다.
 */
internal class NaviRouteTracker {

    data class State(
        /** 경로 안내 중인가. 모르면 null */
        val routeActive: Boolean? = null,
        /** D0(원시 단위) */
        val routeTotal: Int? = null,
        /** R(원시 단위) */
        val remainDistance: Int? = null,
        val remainTime: Int? = null,
        val arrived: Boolean = false,
        val cancelled: Boolean = false,
        /** 목적지 좌표 열쇠(약 100m 격자). 목적지가 바뀌었는지 비교할 때만 쓴다 */
        val destinationKey: String? = null,
    )

    var state: State = State()
        private set

    /** onRouteStarted(RouteStartInfo): 새 경로. D0를 다시 잡는다. */
    fun onRouteStarted(distance: Int, duration: Int) {
        state = State(
            routeActive = true,
            routeTotal = distance.takeIf { it > 0 },
            remainDistance = distance.takeIf { it >= 0 },
            remainTime = duration.takeIf { it >= 0 },
        )
    }

    /**
     * onDestinationInfo(DestinationInfo): 조회 응답. 목적지가 이전과 다르면(앱이 꺼져 있는 동안 바뀐 경우) D0를 다시 잡는다.
     * [destinationKey]는 [destinationKeyOf]로 만든다.
     */
    fun onDestinationInfo(destinationKey: String?, distance: Int, duration: Int) {
        val previous = state.destinationKey
        if (destinationKey != null && previous != null && destinationKey != previous) {
            state = State(routeActive = state.routeActive.takeIf { it == true })
        }
        if (destinationKey != null) state = state.copy(destinationKey = destinationKey)
        onRemaining(distance, duration)
    }

    /** DestinationInfo.remainDistance/remainTime 또는 DrivingInfo.destination.distance/duration */
    fun onRemaining(distance: Int, duration: Int) {
        val s = state
        // 안내가 끝난 뒤(도착·취소·자유 주행)에 오는 남은 거리는 진행률에 쓰지 않는다.
        if (s.routeActive == false) return
        if (distance < 0) return
        // 남은 거리 0은 도착 신호(onDestinationArrived) 전에는 쓰지 않는다. 내비 "안내 종료" 때 0이 먼저 와서
        // 진행률이 100%로 굳고 0km에서 도착 처리되던 문제(2026-11-08 Mac 실제 내비 N12). 진짜 도착은 onArrived가 알린다
        if (distance == 0 && !s.arrived) return
        state = s.copy(
            routeTotal = s.routeTotal ?: distance.takeIf { it > 0 },
            remainDistance = distance,
            remainTime = duration.takeIf { it >= 0 } ?: s.remainTime,
        )
    }

    /**
     * onNavigationStatus(NavigationStatus) 이름: INACTIVE, INITIALIZING, FREE_DRIVE, ROUTE_DRIVE.
     * ROUTE_DRIVE만 안내 중으로 본다. INITIALIZING은 판단을 미룬다.
     */
    fun onNavigationStatus(statusName: String) {
        when (statusName) {
            "ROUTE_DRIVE" -> activate()
            "FREE_DRIVE", "INACTIVE" -> deactivate()
            else -> Unit
        }
    }

    /**
     * RouteStateInfo.naviMode 이름: NOT_WORKING, SAFE_GUIDING, ROUTE_GUIDING, SIMULATION_GUIDING.
     * ROUTE_GUIDING·SIMULATION_GUIDING(내비 모의 주행)을 안내 중으로 본다.
     * isRouting(경로 계산 중으로 추정)은 판단에 쓰지 않는다.
     */
    fun onNaviMode(modeName: String) {
        when (modeName) {
            "ROUTE_GUIDING", "SIMULATION_GUIDING" -> activate()
            "NOT_WORKING", "SAFE_GUIDING" -> deactivate()
            else -> Unit
        }
    }

    /** onDestinationChanged(): 목적지가 바뀌면 D0를 새로 잡는다(뒤로 가지 않는 규칙은 core:trip 몫). */
    fun onDestinationChanged() {
        state = state.copy(
            routeTotal = null, remainDistance = null, remainTime = null,
            arrived = false, cancelled = false, destinationKey = null,
        )
    }

    /** onDestinationArrived(DestinationArrivedInfo): 도착 판단 1순위 */
    fun onArrived() {
        state = state.copy(routeActive = false, arrived = true, cancelled = false, remainDistance = 0, remainTime = 0)
    }

    /** onRouteCancelled(): 도착 폴백 2(취소 후 기어 P)를 위해 표시만 한다 */
    fun onCancelled() {
        state = state.copy(routeActive = false, cancelled = true, remainDistance = null, remainTime = null)
    }

    /** 처음 상태로(테스트·명시적 초기화용). stop/start에서는 부르지 않는다. */
    fun reset() {
        state = State()
    }

    private fun activate() {
        val s = state
        if (s.routeActive == true) return
        // 도착·취소 뒤 다시 안내가 시작되면 새 여행이다.
        state = if (s.arrived || s.cancelled) State(routeActive = true) else s.copy(routeActive = true)
    }

    private fun deactivate() {
        val s = state
        state = s.copy(routeActive = false, remainDistance = if (s.arrived) 0 else null, remainTime = if (s.arrived) 0 else null)
    }
}

/**
 * 목적지 좌표 → 비교용 열쇠. 소수 셋째 자리(위도 기준 약 110m)로 반올림해 같은 목적지의 작은 흔들림은 무시한다.
 * 좌표가 둘 다 0이면(목적지 없음으로 추정) null.
 */
internal fun destinationKeyOf(latitude: Double, longitude: Double): String? {
    if (latitude == 0.0 && longitude == 0.0) return null
    if (latitude.isNaN() || longitude.isNaN()) return null
    return "${Math.round(latitude * 1000)}:${Math.round(longitude * 1000)}"
}
