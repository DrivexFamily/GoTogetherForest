package app.familyjourney.feature.link

import app.familyjourney.core.sync.CarPhase
import app.familyjourney.core.sync.CarStatus
import app.familyjourney.core.sync.CarStatusSender
import app.familyjourney.core.sync.LinkCredentials
import app.familyjourney.core.sync.LocationGrid
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.vehicle.VehicleSignals
import kotlin.math.roundToInt

/**
 * 우리 차 진행을 가족 폰에 보낸다(결정 #37 ⑧: 여행 중 5초마다). [tick]을 5초마다 부른다(FamilyLinkController).
 *
 * - 출발 전에는 보내지 않는다. 도착하면 "도착"을 보내고, 다음 여행(출발 전)이 되면 서버에서 지운다.
 * - 진행률은 아이 화면과 같은 값(TripState.display), 남은 시간은 내비 남은 시간(분).
 * - 대략 위치는 보호자가 켰을 때만, 차 안에서 약 5km 칸으로 반올림한 뒤 보낸다(결정 #8). 원래 좌표는 보내지 않는다.
 * - 목적지 이름·경로·차량 ID는 보내지 않는다.
 */
class CarStatusPublisher(
    private val sender: CarStatusSender,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val appearance: () -> Pair<String?, String?> = { null to null },
) {
    private var sentSomething = false

    suspend fun tick(creds: LinkCredentials, trip: TripState, signals: VehicleSignals, shareLocation: Boolean) {
        val phase = when (trip.phase) {
            TripPhase.BEFORE_START -> null
            TripPhase.DRIVING -> CarPhase.DRIVING
            TripPhase.STOPPED -> CarPhase.RESTING
            TripPhase.ARRIVED -> CarPhase.ARRIVED
        }
        if (phase == null) {
            if (sentSomething) { sender.clear(creds); sentSomething = false }
            return
        }
        val status = CarStatus(
            phase = phase,
            progressPercent = if (phase == CarPhase.ARRIVED) 100 else trip.display.percent,
            remainingMinutes = if (phase == CarPhase.ARRIVED) 0 else signals.remainingTimeMin?.roundToInt(),
            measuredAtEpochMs = nowEpochMs(),
            roadTheme = appearance().first,
            carCode = appearance().second,
        )
        val grid = if (shareLocation && phase != CarPhase.ARRIVED) signals.location?.let { LocationGrid.of(it.latitude, it.longitude) } else null
        sender.send(creds, status, grid)
        sentSomething = true
    }

    /** 위치 보내기를 끄거나 연결을 끊을 때: 서버의 진행·위치를 바로 지운다 */
    suspend fun clear(creds: LinkCredentials) {
        sender.clear(creds)
        sentSomething = false
    }
}
