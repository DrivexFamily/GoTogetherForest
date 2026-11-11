package app.familyjourney.core.vehicle

/**
 * 차량 제어 창구. 읽기([VehicleDataProvider])와 나눠 둔다(CLAUDE.md 5-7).
 *
 * 지금 하는 제어는 하나: 도착 축하 무드등(decisions.md #17).
 *  - 주차 중(실제 기어 P 또는 주차브레이크 + 실제 주행 제한 꺼짐, #21-②)에만 실행한다. 아이 화면에서 직접 부르지 않는다.
 *  - capability가 알려 주는 영역(좌석 여러 개를 묶은 영역)에 쓴다.
 *  - 쓴 뒤 다시 읽어 확인한다("성공" 응답이어도 안 바뀌는 쓰기가 있다, 외부 실측).
 *  - 모든 요청과 결과를 로그로 남긴다(release 포함 logcat `GgfControl`, [LoggingVehicleControlProvider]).
 * 공조 미리 맞추기 등은 확장 계획(기획서)이다. 새 제어는 이 인터페이스에 함수를 더해 붙인다.
 *
 * 구현: 실제 = pleos.PleosVehicleControlProvider, SDK가 없을 때 = [NoOpVehicleControlProvider].
 */
interface VehicleControlProvider {
    /** 제어 SDK를 쓸 수 있는 구현인가. 실제 지원 여부는 요청할 때 capability로 확인한다. */
    val isAvailable: Boolean

    /**
     * 무드등 색을 바꾸고 다시 읽어 확인한다. [rgb] = 0xRRGGBB(알파 없음, [가정] docs/sdk-capabilities.md 4-1).
     * 예외를 던지지 않고 결과를 돌려준다.
     */
    suspend fun setMoodLightColor(rgb: Int): ControlResult
}

/** 제어 요청 결과. 화면과 로그에 그대로 보여 준다. */
sealed interface ControlResult {
    /** 쓰고 다시 읽은 값이 요청과 같다. */
    data class Confirmed(val readBackRgb: Int) : ControlResult

    /** 쓰기는 성공 응답이었지만 다시 읽은 값이 다르다(또는 읽지 못했다). 콘솔이 가까운 색으로 바꿨을 수 있다. */
    data class Mismatch(val requestedRgb: Int, val readBackRgb: Int?) : ControlResult

    /** 안전 규칙으로 거절(주차 중이 아님). 차에 아무것도 보내지 않았다. */
    data class Refused(val reason: String) : ControlResult

    /** 이 차에 기능이 없다(capability·지원 안 함 오류). */
    data class NotSupported(val detail: String) : ControlResult

    /**
     * 권한이 없어 SDK가 거절했다(또는 부르기 전에 앱이 확인해 보니 허용되지 않았다).
     * [step] = 어느 단계에서(capability/쓰기/다시 읽기), [permission] = 그 단계에 필요한 권한 전체 이름.
     */
    data class PermissionDenied(val step: String, val permission: String, val detail: String) : ControlResult

    /** 연결·시간 초과 등으로 실패. */
    data class Failed(val detail: String) : ControlResult
}

/** 무드등 색. ui-design이 최종 색을 정하면 이 한 곳만 바꾼다. */
object MoodLightColors {
    /** 숲 초록(#2B774A). 도착 축하(decisions.md #17) */
    const val FOREST_GREEN: Int = 0x2B774A
}

/**
 * 제어 안전 규칙: 실제 신호로 "주차 중"인가(decisions.md #17, #21-②).
 *  - 기어 P 또는 주차브레이크가 걸려 있고, **그리고**
 *  - 실제 주행 제한(CarUxRestrictions)이 꺼져 있어야 한다.
 * 둘 다 보는 이유: 기어만 보면 주행 제한이 켜진 채(차가 아직 움직이는 중으로 판단되는 순간) 제어가 나갈 수 있다.
 * 더 안전한 쪽(둘 다 만족)을 고른다.
 * 데모 값이 아니라 실제 값([VehicleSignals]의 실차 제공자 값)을 넣는다. 모르면(기어 UNKNOWN·브레이크 null,
 * 주행 제한 모름 = uxRestricted 기본값 true) 거절한다.
 */
fun isParkedForControl(real: VehicleSignals): Boolean =
    !real.uxRestricted && (real.gear == Gear.PARK || real.parkingBrakeOn == true)

/** 0xRRGGBB → "#RRGGBB" (알파는 버린다) */
fun rgbHex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

/** 쓴 값과 다시 읽은 값을 비교한다. 알파 바이트는 비교하지 않는다(SDK가 알파를 붙여 돌려줄 수 있어서). */
fun confirmColor(requestedRgb: Int, readBack: Int?): ControlResult =
    if (readBack != null && (readBack and 0xFFFFFF) == (requestedRgb and 0xFFFFFF)) {
        ControlResult.Confirmed(readBack and 0xFFFFFF)
    } else {
        ControlResult.Mismatch(requestedRgb and 0xFFFFFF, readBack?.let { it and 0xFFFFFF })
    }

/** 결과를 한 줄로(화면·로그용). */
fun ControlResult.describe(): String = when (this) {
    is ControlResult.Confirmed -> "확인됨: 다시 읽은 색 ${rgbHex(readBackRgb)}"
    is ControlResult.Mismatch ->
        "다시 읽은 색이 다름: 요청 ${rgbHex(requestedRgb)}, 읽음 ${readBackRgb?.let(::rgbHex) ?: "없음"}"
    is ControlResult.Refused -> "거절: $reason"
    is ControlResult.NotSupported -> "지원 안 함: $detail"
    is ControlResult.PermissionDenied ->
        "권한 없음: $step 단계에 $permission 필요 (매니페스트 선언 후 다시 설치, 권한 표에서 '허용' 확인) [$detail]"
    is ControlResult.Failed -> "실패: $detail"
}

/** 제어 SDK가 없을 때. 아무것도 하지 않는다. */
object NoOpVehicleControlProvider : VehicleControlProvider {
    override val isAvailable: Boolean = false
    override suspend fun setMoodLightColor(rgb: Int): ControlResult =
        ControlResult.NotSupported("제어 기능 없음(NoOp)")
}

/**
 * 모든 제어 요청과 결과를 남기는 감싸개(decisions.md #17, CLAUDE.md 5-7 "모든 제어 요청은 로그로", QA-4).
 * **release에서도** 한 줄씩 남긴다(앱은 logcat `GgfControl`에 `Log.i`). 색과 결과만 쓰고 개인정보·위치는 넣지 않는다.
 * 실제 제어는 [delegate]가 한다(안전 규칙도 그쪽).
 */
class LoggingVehicleControlProvider(
    private val delegate: VehicleControlProvider,
    private val log: (String) -> Unit,
) : VehicleControlProvider {
    override val isAvailable: Boolean get() = delegate.isAvailable

    override suspend fun setMoodLightColor(rgb: Int): ControlResult {
        val request = "무드등 색 ${rgbHex(rgb)}"
        log("제어 요청: $request")
        val result = try {
            delegate.setMoodLightColor(rgb)
        } catch (e: kotlinx.coroutines.CancellationException) {
            log("제어 취소: $request")
            throw e
        } catch (t: Throwable) {
            ControlResult.Failed("예외 ${t.javaClass.simpleName}")
        }
        log("제어 결과: $request → ${result.describe()}")
        return result
    }
}
