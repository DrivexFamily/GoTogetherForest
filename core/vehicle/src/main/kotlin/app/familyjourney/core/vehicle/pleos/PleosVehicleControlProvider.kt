package app.familyjourney.core.vehicle.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.familyjourney.core.vehicle.ControlResult
import app.familyjourney.core.vehicle.VehicleControlProvider
import app.familyjourney.core.vehicle.VehiclePermissions
import app.familyjourney.core.vehicle.VehicleSignals
import app.familyjourney.core.vehicle.confirmColor
import app.familyjourney.core.vehicle.isParkedForControl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pleos Vehicle SDK 2.3.2 Light로 무드등 색을 바꾸는 제어 구현(decisions.md #17).
 *
 * 순서(요청 한 번마다):
 *  1. 안전 규칙: 실제 신호가 주차 중(기어 P 또는 주차브레이크, 그리고 주행 제한 꺼짐, decisions.md #21-②)이 아니면
 *     거절([isParkedForControl]). 차에 아무것도 보내지 않는다.
 *  2. 권한 확인: 단계별로 SDK가 실행 때 검사하는 권한([MoodStep])이 앱에 허용됐는지 먼저 본다.
 *     없으면 어떤 권한이 필요한지 담아 [ControlResult.PermissionDenied]로 돌려준다(SDK를 부르지 않는다).
 *  3. Vehicle(ctx).initialize()
 *  4. checkAmbientLightBrightnessColorCapabilities → (CapabilityConfig, List<SeatArea>)
 *     무드등은 좌석 하나로 부르면 실패하고 **여러 좌석을 묶은 영역**으로만 응답한다(외부 실측: 좌석 5개).
 *     그래서 capability가 준 영역 중 좌석이 가장 많은 것을 쓴다.
 *  5. 쓰기 직전에 주차 상태를 한 번 더 확인한다(IPC 사이에 기어가 바뀌었을 수 있다).
 *  6. setAmbientLightBrightnessColor(area, rgb) → 잠깐 기다린 뒤 getAmbientLightBrightnessColor(area)로 다시 읽어 비교
 *  7. release()
 *
 * 권한(2026-11-01 javap -v로 다시 확인, 에뮬레이터 실패 "setAmbientLightBrightnessColor need Permission"의 원인):
 *  - capability·다시 읽기 = `pleos.car.permission.CAR_LIGHTS`
 *  - **쓰기 = `pleos.car.permission.CONTROL_CAR_LIGHTS`** (`LightImpl`의 `@RequiresCustomPermission`).
 *    인터페이스 `Light`에 붙은 `@RequiresPermission(CAR_LIGHTS)`는 컴파일용 표시라 실행 때 검사와 다르다.
 *  - SDK의 권한 검사기(난독화된 `vehicle.utils.f`, aar proguard 주석 "PermissionUtil")가 호출 스택에서 메서드 이름을 찾아
 *    그 어노테이션의 권한을 `checkSelfPermission`으로 확인하고, 하나라도 없으면
 *    `SecurityException("<메서드 이름> need Permission")`을 onFailed로 넘긴다.
 * 로그: 요청·결과 한 줄은 앱이 감싸는 `LoggingVehicleControlProvider`가 release에서도 남긴다. 여기서는 capability 같은
 * 자세한 단계만 디버그 빌드에서 남긴다([devDiagnostics]).
 */
class PleosVehicleControlProvider(
    context: Context,
    /** 실차 신호(데모와 섞지 않은 값). 안전 규칙에 쓴다. */
    private val realSignals: () -> VehicleSignals,
    private val devDiagnostics: Boolean = false,
    private val stepTimeoutMs: Long = 5_000L,
    /** 쓴 직후 차량 서비스가 값을 반영할 시간. 너무 빨리 읽으면 이전 값이 올 수 있다. */
    private val readBackDelayMs: Long = 500L,
) : VehicleControlProvider {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    override val isAvailable: Boolean = true

    override suspend fun setMoodLightColor(rgb: Int): ControlResult = mutex.withLock {
        withContext(Dispatchers.Default) { runMoodLight(rgb and 0xFFFFFF) }
    }

    private suspend fun runMoodLight(rgb: Int): ControlResult {
        if (!isParkedForControl(realSignals())) return NOT_PARKED

        firstMissingMoodPermission(::isGranted)?.let { step ->
            return ControlResult.PermissionDenied(step.label, step.permission, "앱에 허용되지 않음(부르기 전 확인)")
        }

        val vehicle = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (t: Throwable) {
            return ControlResult.Failed("Vehicle 연결: ${t.javaClass.simpleName}")
        }
        try {
            val light = vehicle.getLight()

            val (config, areas) = call<Pair<CapabilityConfig?, List<SeatArea>>>(MoodStep.CAPABILITY) { ok, fail ->
                light.checkAmbientLightBrightnessColorCapabilities({ c, a -> ok(c to a) }, fail)
            }
            val access = config?.accessType?.name
            log("capability: accessType=$access, 영역=${areas.joinToString { seatNames(it) }}")
            if (!canWrite(access)) return ControlResult.NotSupported("쓰기 불가(accessType=$access)")
            val area = pickCombinedArea(areas) { it.values.size }
                ?: return ControlResult.NotSupported("영역 없음")

            if (!isParkedForControl(realSignals())) return NOT_PARKED

            call<Int?>(MoodStep.WRITE) { ok, fail ->
                light.setAmbientLightBrightnessColor(area, rgb, { _, value -> ok(value) }, fail)
            }
            if (!canRead(access)) return ControlResult.Mismatch(rgb, null)

            delay(readBackDelayMs)
            val readBack = call<Int?>(MoodStep.READ_BACK) { ok, fail ->
                light.getAmbientLightBrightnessColor(area, { value -> ok(value) }, fail)
            }
            return confirmColor(rgb, readBack)
        } catch (e: StepFailure) {
            return when {
                isPermissionError(e.detail, e.security) ->
                    ControlResult.PermissionDenied(e.step.label, e.step.permission, e.detail)
                classifyVehicleError(e.detail) == VehicleErrorKind.NOT_SUPPORTED ->
                    ControlResult.NotSupported("${e.step.label}: ${e.detail}")
                else -> ControlResult.Failed("${e.step.label}: ${e.detail}")
            }
        } finally {
            try {
                vehicle.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun isGranted(permission: String): Boolean =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * SDK 콜백 하나를 기다린다. 성공·실패 중 먼저 온 것 하나만 받는다(CompletableDeferred.complete는 한 번만 성공).
     * 시간 안에 답이 없으면 실패로 본다.
     */
    private suspend fun <T> call(step: MoodStep, block: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit): T {
        val answer = CompletableDeferred<Result<T>>()
        try {
            block({ answer.complete(Result.success(it)) }, { answer.complete(Result.failure(it)) })
        } catch (t: Throwable) {
            answer.complete(Result.failure(t))
        }
        val result = withTimeoutOrNull(stepTimeoutMs) { answer.await() }
            ?: throw StepFailure(step, "시간 초과(${stepTimeoutMs}ms)", security = false)
        return result.getOrElse {
            throw StepFailure(step, it.message ?: it.javaClass.simpleName, security = it is SecurityException)
        }
    }

    private class StepFailure(val step: MoodStep, val detail: String, val security: Boolean) :
        Exception("${step.label}: $detail")

    private fun seatNames(area: SeatArea): String = area.values.joinToString("+") { it.name }

    private fun log(message: String) {
        if (devDiagnostics) Log.d(TAG, message)
    }

    companion object {
        /** adb logcat -s GgfControl (요청·결과는 release 포함, 자세한 단계는 디버그 빌드만) */
        const val TAG = "GgfControl"
        private val NOT_PARKED = ControlResult.Refused("주차 중이 아님(기어 P 또는 주차브레이크 + 주행 제한 꺼짐 필요)")
    }
}

/**
 * 무드등 요청의 단계와, 각 단계에서 SDK가 **실행 때** 검사하는 권한.
 * 근거: Vehicle SDK 2.3.2 `LightImpl`의 `@RequiresCustomPermission`(javap -v, 2026-11-01).
 */
internal enum class MoodStep(val label: String, val permission: String) {
    CAPABILITY("capability", VehiclePermissions.CAR_LIGHTS),
    WRITE("쓰기", VehiclePermissions.CONTROL_CAR_LIGHTS),
    READ_BACK("다시 읽기", VehiclePermissions.CAR_LIGHTS),
}

/** 단계 순서대로 보아 처음으로 허용되지 않은 단계. 모두 허용이면 null. */
internal fun firstMissingMoodPermission(isGranted: (String) -> Boolean): MoodStep? =
    MoodStep.entries.firstOrNull { !isGranted(it.permission) }

/**
 * SDK 오류가 권한 거절인가.
 *  - 앱 쪽 검사: `SecurityException("<메서드> need Permission")`(Vehicle SDK 권한 검사기)
 *  - 어노테이션을 못 찾음: `SecurityException("Permission metadata not found for …")`(난독화 등)
 *  - 서비스 쪽 검사: 오류 이름 `ERROR_SECURITY_PERMISSION`
 */
internal fun isPermissionError(message: String?, isSecurityException: Boolean): Boolean {
    if (isSecurityException) return true
    val m = message ?: return false
    return "need Permission" in m ||
        "Permission metadata not found" in m ||
        classifyVehicleError(m) == VehicleErrorKind.NO_PERMISSION
}

/** capability 영역 중 좌석이 가장 많은 "묶은 영역"을 고른다. 비어 있으면 null. */
internal fun <T> pickCombinedArea(areas: List<T>, seatCount: (T) -> Int): T? = areas.maxByOrNull(seatCount)

/** CapabilityConfig.accessType 이름으로 쓰기 가능 여부. */
internal fun canWrite(accessTypeName: String?): Boolean = accessTypeName == "WRITE" || accessTypeName == "READ_WRITE"

/** CapabilityConfig.accessType 이름으로 읽기 가능 여부. */
internal fun canRead(accessTypeName: String?): Boolean = accessTypeName == "READ" || accessTypeName == "READ_WRITE"
