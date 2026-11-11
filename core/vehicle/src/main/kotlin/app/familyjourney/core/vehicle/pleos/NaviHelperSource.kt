package app.familyjourney.core.vehicle.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import app.familyjourney.core.vehicle.RoutePlaces
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import app.familyjourney.core.vehicle.SdkSource
import app.familyjourney.core.vehicle.Signal
import app.familyjourney.core.vehicle.SourceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pleos NaviHelper 3.0.5: 남은 거리·시간, 경로 시작·취소, 도착 이벤트, 현재 위치, 도로 제한 속도.
 *
 * - 현재 위치: getCurrentLocationInfo()(NAVI_ROUTE) → onCurrentLocationInfo(CurrentLocationInfo). 연결 때 1회 + 재조회 간격마다.
 *   위도·경도·heading만 신호로 넘기고 **주소는 넘기지 않는다**(디버그 로그에서도 NaviLogRedactor가 가린다).
 *   FusedLocation은 에뮬레이터에서 값이 오지 않아(외부 실측) 위치는 이쪽으로 받는다(decisions.md #8).
 * - 도로 제한 속도: onCurrentRoadSpeedLimit(Int) 알림. 안내 중이 아니면 0(외부 실측) → 신호는 모름(null).
 *
 * - 내비가 탑재된 차에서만 동작한다. 연결 실패를 정상 상황으로 다룬다(폴백: 데모·"내비에 목적지를 설정해 주세요").
 *   SDK는 연결을 최대 30회 재시도하고(약 60초 제한) 그래도 안 되면 onFailed를 부른다(javap: manager.a 상수 r=30, t=60000).
 * - 모든 API는 onConnected 뒤에만 부른다. 콜백은 SDK의 바인더 스레드에서 바로 온다(callback.b가 Stub.onResponse에서
 *   스레드 전환 없이 리스너를 부름, javap 확인. 실제 스레드 이름은 로그로 확인).
 * - 턴 안내(getTBTInfo)는 부르지 않는다(내비 앱이 아니므로 카테고리 위반 위험).
 * - 정리 순서: removeListener → release. stop() 뒤에 온 콜백은 세대 번호로 버린다.
 * - 경로 상태([NaviRouteTracker])는 stop/start 사이에 유지한다(D0를 잃지 않게).
 */
internal class NaviHelperSource(
    private val context: Context,
    private val store: SignalStore,
    private val config: PleosVehicleConfig,
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private var helper: NaviHelper? = null
    private var listener: NaviHelperEventListener? = null
    private var pollJob: Job? = null
    private var generation = 0
    private val tracker = NaviRouteTracker()

    fun start() {
        val gen: Int
        val h: NaviHelper
        synchronized(lock) {
            if (helper != null) return
            gen = ++generation
            // tracker는 지우지 않는다: 백그라운드에 다녀와도 경로 전체 거리(D0)를 유지한다.
            store.status(SdkSource.NAVI_HELPER, SourceState.CONNECTING)
            h = try {
                NaviHelper(context)
            } catch (t: Throwable) {
                store.fail(SdkSource.NAVI_HELPER, SourceState.FAILED, t.javaClass.simpleName)
                return
            }
            helper = h
        }
        try {
            h.initialize(
                { onConnected(gen, h) },
                { t -> onConnectFailed(gen, t) },
            )
        } catch (t: Throwable) {
            onConnectFailed(gen, t)
        }
    }

    fun stop() {
        synchronized(lock) {
            generation++
            pollJob?.cancel()
            pollJob = null
            val h = helper ?: return
            try {
                listener?.let { h.removeListener(it) }
            } catch (_: Throwable) {
            }
            try {
                h.release()
            } catch (_: Throwable) {
            }
            listener = null
            helper = null
        }
        store.fail(SdkSource.NAVI_HELPER, SourceState.STOPPED)
    }

    private fun onConnected(gen: Int, h: NaviHelper) {
        synchronized(lock) {
            if (gen != generation) return
            val l = Listener(gen)
            listener = l
            try {
                h.addListener(l)
                h.getRouteStateInfo()
                h.getDestinationInfo()
                h.getCurrentLocationInfo()
            } catch (t: Throwable) {
                store.fail(SdkSource.NAVI_HELPER, SourceState.FAILED, t.javaClass.simpleName)
                return
            }
            pollJob = scope.launch {
                while (isActive) {
                    delay(config.naviPollIntervalMs)
                    poll(gen)
                }
            }
        }
        store.status(SdkSource.NAVI_HELPER, SourceState.CONNECTED)
        store.naviLog("onConnected", "")
    }

    private fun poll(gen: Int) {
        val h = synchronized(lock) { if (gen == generation) helper else null } ?: return
        try {
            h.getDestinationInfo()
            h.getCurrentLocationInfo()
        } catch (t: Throwable) {
            store.naviLog("poll", t.javaClass.simpleName)
        }
    }

    private fun onConnectFailed(gen: Int, t: Throwable) {
        if (!isCurrent(gen)) return
        // 내비 서비스(ai.pleos.playground.service.navi)가 없거나 권한이 없을 때. 앱은 계속 동작한다.
        store.fail(SdkSource.NAVI_HELPER, SourceState.FAILED, t.javaClass.simpleName)
        store.naviLog("onFailed", t.javaClass.simpleName)
    }

    private fun isCurrent(gen: Int): Boolean = synchronized(lock) { gen == generation }

    /** 트래커 상태 → 신호(km·분). 단위 변환은 설정의 가정값으로 한 곳에서만 한다. */
    private fun publish() {
        val s = synchronized(lock) { tracker.state }
        val km = { raw: Int? -> raw?.let { (it * config.naviMetersPerDistanceUnit / 1000.0).toFloat() } }
        val min = { raw: Int? -> raw?.let { (it * config.naviSecondsPerTimeUnit / 60.0).toFloat() } }
        store.update(NAVI_SIGNALS) {
            it.copy(
                remainingDistanceKm = km(s.remainDistance),
                remainingTimeMin = min(s.remainTime),
                routeDistanceKm = km(s.routeTotal),
                routeActive = s.routeActive,
                routeCancelled = s.cancelled,
                arrived = s.arrived,
            )
        }
    }

    private inner class Listener(private val gen: Int) : NaviHelperEventListener {
        /** 위치·제한 속도는 자주 오므로 화면 로그를 채우지 않게 처음 한 번(제한 속도는 값이 바뀔 때)만 남긴다. */
        @Volatile private var locationLogged = false
        @Volatile private var lastSpeedLimit: Int? = null
        /** 지금 위치 주소(메모리만, 출발지 이름용, 결정 #45) */
        @Volatile private var lastAddress: String? = null
        /** 이름을 붙인 목적지 열쇠와 그때의 출발지 */
        @Volatile private var placesKey: String? = null
        @Volatile private var placesOrigin: String? = null

        private inline fun handle(name: String, payload: Any?, block: () -> Unit = {}) {
            if (!isCurrent(gen)) return
            if (store.keepsNaviLog) store.naviLog(name, payload?.toString() ?: "")
            try {
                synchronized(lock) { block() }
                publish()
            } catch (t: Throwable) {
                store.naviLog("$name:error", t.javaClass.simpleName)
            }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            if (!isCurrent(gen)) return
            if (store.keepsNaviLog && !locationLogged) {
                locationLogged = true
                // toString()에 주소·좌표·routeId가 있지만 naviLog가 가린다(NaviLogRedactor).
                store.naviLog("onCurrentLocationInfo", currentLocationInfo.toString())
            }
            try {
                val location = locationOf(
                    currentLocationInfo.latitude,
                    currentLocationInfo.longitude,
                    currentLocationInfo.heading,
                )
                // 주소(address)는 메모리에만 잠깐 둔다: 목적지가 처음 잡힐 때 "출발지" 이름으로만 쓴다(결정 #45). 로그·저장 안 함
                lastAddress = runCatching { currentLocationInfo.address }.getOrNull()?.takeIf { it.isNotBlank() }
                store.update(Signal.LOCATION) { it.copy(location = location) }
            } catch (t: Throwable) {
                store.naviLog("onCurrentLocationInfo:error", t.javaClass.simpleName)
            }
        }

        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            if (!isCurrent(gen)) return
            if (store.keepsNaviLog && lastSpeedLimit != speedLimit) {
                store.naviLog("onCurrentRoadSpeedLimit", speedLimit.toString())
            }
            lastSpeedLimit = speedLimit
            store.naviRaw { it.copy(roadSpeedLimit = speedLimit) }
            store.update(Signal.ROAD_SPEED_LIMIT) { it.copy(roadSpeedLimitKmh = roadSpeedLimitOf(speedLimit)) }
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = handle("onRouteStarted", routeStartInfo) {
            tracker.onRouteStarted(routeStartInfo.distance, routeStartInfo.duration)
            store.naviRaw { it.copy(routeStartDistance = routeStartInfo.distance, routeStartDuration = routeStartInfo.duration) }
        }

        override fun onRoutingCompleted() = handle("onRoutingCompleted", null)

        override fun onReRouteEnded() = handle("onReRouteEnded", null)

        override fun onRouteCancelled() = handle("onRouteCancelled", null) {
            tracker.onCancelled()
            placesKey = null
            store.update(Signal.LOCATION) { it.copy(routePlaces = null) }
        }

        override fun onDestinationChanged() = handle("onDestinationChanged", null) { tracker.onDestinationChanged() }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
            handle("onDestinationArrived", destinationArrivedInfo) {
                tracker.onArrived()
                store.naviRaw {
                    it.copy(arrivedDistance = destinationArrivedInfo.distance, arrivedDuration = destinationArrivedInfo.duration)
                }
            }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = handle("onRouteStateInfo", routeStateInfo) {
            tracker.onNaviMode(routeStateInfo.naviMode.name)
            store.naviRaw { it.copy(naviMode = routeStateInfo.naviMode.name, isRouting = routeStateInfo.isRouting) }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) = handle("onDestinationInfo", destinationInfo) {
            tracker.onDestinationInfo(
                destinationKeyOf(destinationInfo.latitude, destinationInfo.longitude),
                destinationInfo.remainDistance,
                destinationInfo.remainTime,
            )
            store.naviRaw {
                it.copy(destinationRemainDistance = destinationInfo.remainDistance, destinationRemainTime = destinationInfo.remainTime)
            }
            // 지역 도감 도착 판정용 목적지 좌표(결정 #40). 로그에는 남기지 않는다(NaviLogRedactor)
            val dest = locationOf(destinationInfo.latitude, destinationInfo.longitude, Double.NaN)
            // 목적지 이름(결정 #45): 새 목적지가 잡히면 그때의 현재 위치 주소를 출발지로 붙인다(화면 확인용, 메모리만)
            val key = destinationKeyOf(destinationInfo.latitude, destinationInfo.longitude)
            val name = destinationInfo.name.takeIf { it.isNotBlank() }
            if (key != placesKey) { placesKey = key; placesOrigin = lastAddress }
            val places = name?.let { RoutePlaces(placesOrigin, it) }
            store.update(Signal.LOCATION) { it.copy(destination = dest, routePlaces = places) }
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) = handle("onDrivingInfo", drivingInfo) {
            val dest = drivingInfo.destination
            tracker.onRemaining(dest.distance, dest.duration)
            store.naviRaw { it.copy(drivingDestinationDistance = dest.distance, drivingDestinationDuration = dest.duration) }
        }

        override fun onNavigationStatus(status: NavigationStatus) = handle("onNavigationStatus", status) {
            tracker.onNavigationStatus(status.name)
            store.naviRaw { it.copy(navigationStatus = status.name) }
        }

        override fun onTripInfo(tripInfo: TripInfo) = handle("onTripInfo", tripInfo)

        override fun onError(errorCode: NaviErrorCode) = handle("onError", errorCode) {
            store.naviRaw { it.copy(lastError = errorCode.name) }
        }
    }

    private companion object {
        val NAVI_SIGNALS = setOf(
            Signal.REMAINING_DISTANCE,
            Signal.REMAINING_TIME,
            Signal.ROUTE_DISTANCE,
            Signal.ROUTE_STATE,
            Signal.ARRIVAL,
        )
    }
}
