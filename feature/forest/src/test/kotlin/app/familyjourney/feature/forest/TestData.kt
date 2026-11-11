package app.familyjourney.feature.forest

import app.familyjourney.core.account.ChildProfile
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.InMemoryAccountRepository
import app.familyjourney.core.trip.ArrivalReason
import app.familyjourney.core.trip.DistanceSource
import app.familyjourney.core.trip.SafeDriveSummary
import app.familyjourney.core.trip.TripResult
import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.VehicleSignals
import java.time.LocalDateTime
import java.time.ZoneId

internal val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
internal val NOV_1: Long = LocalDateTime.of(2026, 11, 1, 12, 0).atZone(ZONE).toInstant().toEpochMilli()

/** 400km 중 360km 안전(decisions.md #5의 예) */
internal fun tripResult(
    tripId: String = "t1",
    safeKm: Int = 360,
    eventKm: Int = 40,
    totalKm: Double = 400.0,
    powertrain: Powertrain = Powertrain.EV,
    demo: Boolean = false,
) = TripResult(
    tripId = tripId,
    totalKm = totalKm,
    distanceSource = DistanceSource.ODOMETER,
    safety = SafeDriveSummary(countedKm = safeKm + eventKm, safeKm = safeKm, eventKm = eventKm, eventCount = eventKm),
    powertrain = powertrain,
    demo = demo,
    arrivalReason = ArrivalReason.ROUTE_ARRIVED,
    startedAtMs = 0L,
    arrivedAtMs = 1L,
)

/** 가상의 별명(블라인드 원칙) */
internal fun child(id: String, nickname: String = "별명$id", sprouts: Int = 0) =
    ChildProfile(id = id, nickname = nickname, totalSprouts = sprouts)

internal fun account(vararg children: ChildProfile, lastRiders: List<String> = emptyList()) =
    GuardianAccount(id = "g", confirmedAtEpochMs = 0L, children = children.toList(), lastRiderIds = lastRiders)

internal fun repo(account: GuardianAccount?) =
    InMemoryAccountRepository(account, nowEpochMs = { NOV_1 }, zone = { ZONE })

/** 실차처럼 주행 제한을 "아는" 신호 */
internal fun signals(
    speed: Float? = 0f,
    gear: Gear = Gear.PARK,
    routeKm: Float? = 100f,
    remainingKm: Float? = 100f,
    odometer: Float? = 1_000f,
    arrived: Boolean = false,
    energyWh: Float? = null,
    powertrain: Powertrain = Powertrain.EV,
): VehicleSignals {
    val restricted = gear != Gear.PARK
    return VehicleSignals(
        speedKmh = speed,
        gear = gear,
        parkingBrakeOn = false,
        uxRestricted = restricted,
        uxRestrictionBits = if (restricted) 80 else 0,
        batteryEnergyWh = energyWh,
        odometerKm = odometer,
        powertrain = powertrain,
        remainingDistanceKm = remainingKm,
        remainingTimeMin = remainingKm?.let { it * 0.7f },
        routeDistanceKm = routeKm,
        routeActive = true,
        arrived = arrived,
    )
}
