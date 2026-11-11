package app.familyjourney.feature.journey

import androidx.compose.runtime.Immutable

/**
 * 내비 목적지 확인(결정 #45, 2026-11-08 마스터 지시): 내비에서 목적지를 정하면 같이가숲이 "목적지가 등록되었습니다.
 * 목적지: 부산역이 맞습니까?"로 묻는다. **주차 중에만** 확인창으로 보이고, 주행 중에는 진행 화면 위 빈 칸에 한 줄([drivingLine])만.
 *
 * @param key 이 목적지의 안정된 열쇠(같은 목적지를 두 번 묻지 않게, 움직임 playKey로)
 * @param origin 출발지 이름(내비 현재 위치 주소, 데모는 "서울역"). 모르면 null
 * @param destination 목적지 이름(내비 DestinationInfo.name, 데모는 "부산역")
 * @param demo 데모의 가상 목적지인가(확인창에 "데모" 표시)
 */
@Immutable
data class DestinationConfirmUi(
    val key: String,
    val origin: String?,
    val destination: String,
    val demo: Boolean,
) {
    val title: String get() = "목적지가 등록되었습니다"
    val question: String get() = "목적지: ${destination}${subjectParticle(destination)} 맞습니까?"
    val route: String? get() = origin?.let { "$it → $destination" }
    val yes: String get() = "맞아요"
    val no: String get() = "아니에요"

    /** 주행 중 위 빈 칸 한 줄(버튼 없음) */
    val drivingLine: String get() = "목적지: $destination"

    companion object {
        /** "아니에요"를 눌렀을 때 안내 */
        const val RESET_HINT: String = "내비에서 목적지를 다시 정해 주세요"
    }
}

/** 이/가: 마지막 글자에 받침이 있으면 "이" */
internal fun subjectParticle(word: String): String {
    val c = word.lastOrNull { it in '가'..'힣' } ?: return "이(가)"
    return if ((c - '가') % 28 != 0) "이" else "가"
}
