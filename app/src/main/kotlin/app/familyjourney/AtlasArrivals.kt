package app.familyjourney

import app.familyjourney.core.atlas.AtlasRepository
import app.familyjourney.core.atlas.GeoPoint
import app.familyjourney.core.atlas.LandmarkCatalog
import app.familyjourney.core.trip.ArrivalReason
import app.familyjourney.core.trip.TripEvent
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.SwitchingVehicleDataProvider
import app.familyjourney.core.vehicle.VehicleLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 여행 → 지역 도감(결정 #40, #42, Claude 담당).
 *
 * - **지나가며 얻기(#42, 마스터 지시 11-08):** 실제 여행 중 차 위치(NaviHelper 현재 위치)가 랜드마크 500m 안을 지나면
 *   "지나온 곳"에 넣는다([AtlasRepository.onLocation]). 진행 화면 위 빈 칸에 배지가 정지 그림으로 뜨고, **주차하면** "받기" 버튼으로
 *   얻는다(주행 중 조작 없음). 위치는 판정에만 쓰고 저장하지 않는다.
 * - 도착(결정 #43): 내비 목적지 좌표(없으면 현재 위치)도 같은 판정으로 넣고, 지나온 곳을 **모두 받는다**. 주차하면
 *   "랜드마크 N곳을 획득했어요!" 한 화면에 모두 보인다(2곳 이상). 휴게소 등 도착 전 정차에서는 "받기" 버튼.
 * - 데모 여행(서울역 → 부산역): 좌표가 없으므로 [LandmarkCatalog.DEMO_ROUTE_PASSES]의 진행률마다 길목의 곳을 지나간다(발표용 도감에만).
 *   도착하면 지금처럼 고른 목표(없으면 기본 목표)를 축하하고 도감 절반을 채운다(#41).
 */
object AtlasArrivals {

    fun start(scope: CoroutineScope, repo: AtlasRepository, events: Flow<TripEvent>, vehicle: SwitchingVehicleDataProvider) {
        // 목표 고르기를 없앴다(결정 #42): 예전에 골라 둔 목표가 남아 있으면 비운다(데모 도착은 기본 목표를 축하)
        if (repo.book.value.selectedId != null) repo.select(null)
        scope.launch {
            vehicle.mode.collect { repo.setDemo(it != SignalMode.REAL) }
        }
        // 데모가 출발 전 자리로 돌아오면(발표자 [처음부터]) 바로 앞 회차의 지나온 곳을 비운다. 다시 출발할 때까지 받기 제안이 남지 않게(2026-11-13 Mac R3)
        scope.launch {
            vehicle.signals.map { it.demoActive && (it.tripDistanceKm ?: 0f) <= 0f }
                .distinctUntilChanged()
                .collect { atStart -> if (atStart && repo.isDemo) repo.onDemoTripStart() }
        }
        // 실제 여행의 차 위치(데모·섞기 모드의 에뮬레이터 위치는 쓰지 않는다)
        scope.launch {
            combine(vehicle.mode, vehicle.signals.map { it.location }.distinctUntilChanged()) { mode, loc -> if (mode == SignalMode.REAL) loc else null }
                .distinctUntilChanged()
                .collect { loc -> loc?.let { repo.onLocation(it.toGeo()) } }
        }
        scope.launch {
            var demoTrip: String? = null
            var demoDone = 0
            events.collect { e ->
                when (e) {
                    is TripEvent.ProgressChanged -> if (repo.isDemo) {
                        if (e.tripId != demoTrip) {
                            // 새 데모 여행([처음부터] 포함): 앞 회차의 "지나온 곳 N곳을 받을까요?"가 남지 않게 비운다(2026-11-12 Mac)
                            repo.onDemoTripStart()
                            demoTrip = e.tripId
                            demoDone = 0
                        }
                        // 서울역 → 부산역 길목의 정해 둔 곳을 진행률에 맞춰 지난다(결정 #45)
                        val passes = LandmarkCatalog.DEMO_ROUTE_PASSES
                        val due = passes.count { e.percent >= it.first }
                        if (due > demoDone) {
                            repo.onDemoPass(passes.subList(demoDone, due).map { it.second })
                            demoDone = due
                        }
                    }
                    is TripEvent.Arrived -> {
                        val r = e.result
                        if (r.demo) {
                            // 데모: 지나온 곳을 먼저 받고, 기본 목표 축하 + 도감 절반(#41)
                            repo.claimPassed()
                            repo.onDemoArrival(r.tripId, LandmarkCatalog.DEMO_FALLBACK_ID)
                        } else {
                            val s = vehicle.signals.value
                            // 목적지 좌표는 내비가 도착을 알렸거나 98%까지 간 경우에만. 경로 취소·경로 없이 끝난 여행은 지금 위치로(2026-11-08 N12)
                            val atDestination = r.arrivalReason == ArrivalReason.ROUTE_ARRIVED || r.arrivalReason == ArrivalReason.PROGRESS_AND_PARK
                            val point = if (atDestination) s.destination ?: s.location else s.location
                            point?.toGeo()?.let(repo::onLocation)
                            // 도착하면 이번 여행에서 지나온 곳을 모두 받는다(결정 #43). 축하는 주차하면 한 화면에 모두
                            repo.claimPassed()
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun VehicleLocation.toGeo() = GeoPoint(latitude, longitude)
}
