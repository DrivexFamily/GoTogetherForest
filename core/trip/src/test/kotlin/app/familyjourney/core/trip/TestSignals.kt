package app.familyjourney.core.trip

import app.familyjourney.core.vehicle.Gear
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.core.vehicle.VehicleSignals

/** 실차 모드처럼 주행 제한을 "아는" 신호(uxRestrictionBits가 있음)를 만든다. */
internal fun sig(
    speed: Float? = 0f,
    gear: Gear = Gear.PARK,
    restricted: Boolean = gear != Gear.PARK,
    routeKm: Float? = 100f,
    remainingKm: Float? = 100f,
    remainingMin: Float? = 70f,
    routeActive: Boolean? = true,
    arrived: Boolean = false,
    cancelled: Boolean = false,
    odometer: Float? = 1_000f,
    nanos: Long? = null,
    parkingBrake: Boolean? = null,
    powertrain: Powertrain = Powertrain.EV,
    demo: Boolean = false,
    tripKm: Float? = null,
    restrictionKnown: Boolean = true,
): VehicleSignals = VehicleSignals(
    speedKmh = speed,
    speedSampleNanos = nanos,
    gear = gear,
    parkingBrakeOn = parkingBrake,
    uxRestricted = if (restrictionKnown) restricted else true,
    uxRestrictionBits = if (!restrictionKnown) null else if (restricted) 80 else 0,
    odometerKm = odometer,
    tripDistanceKm = tripKm,
    powertrain = powertrain,
    remainingDistanceKm = remainingKm,
    remainingTimeMin = remainingMin,
    routeDistanceKm = routeKm,
    routeActive = routeActive,
    routeCancelled = cancelled,
    arrived = arrived,
    demoActive = demo,
)

internal const val SEC: Long = 1_000L
