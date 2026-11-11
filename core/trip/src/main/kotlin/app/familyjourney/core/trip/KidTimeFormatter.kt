package app.familyjourney.core.trip

import kotlin.math.ceil
import kotlin.math.floor

/** 남은 시간을 무엇으로 셌나. 화면은 그림(core:ui KidSentenceIcon)을 고를 때 쓴다. */
enum class KidTimeUnit {
    /** 끝말잇기(1판 = 10분, decisions.md #35. 예전 "만화"를 바꿈) */
    WORD_CHAIN,
    /** 노래(1곡 = 4분, 같이 부르기) */
    SONG,
    /** 곧 도착(남은 시간 0) */
    SOON,
    /** 도착함 */
    ARRIVED,
}

/**
 * 아이 말로 바꾼 남은 시간.
 *
 * @param count 끝말잇기 판 수 또는 노래 곡 수. SOON·ARRIVED는 0
 * @param sentence 넓은 창용 문장("끝말잇기 4판 하면 도착해요")
 * @param shortSentence 좁은 창용 짧은 문장("끝말잇기 4판 남았어요", decisions.md #21-①)
 */
data class KidTime(
    val unit: KidTimeUnit,
    val count: Double,
    val sentence: String,
    val shortSentence: String,
)

/**
 * 남은 시간 → 아이 말(decisions.md #2 "남은 시간 환산", #35 가족 놀이 단위).
 *
 * | 남은 시간 | 말 | 셈 |
 * | --- | --- | --- |
 * | 20분 이상 | "끝말잇기 N판 하면 도착해요" | 1판 = 10분, **가까운 판**으로 반올림(25분 → 3판, 최소 2판) |
 * | 0분 초과 20분 미만 | "노래 N곡 같이 부르면 도착해요" | 1곡 = 4분, **올림**(최소 1곡, 19분 → 5곡) |
 * | 0분 이하 | "곧 도착해요" | - |
 *
 * 2026-11-06 팀원 결정(#35): "만화 N편 보면"은 만화 보기를 부추기는 것 같아, 가족이 함께 하는 놀이(끝말잇기·노래)로 바꿨다.
 * 끝말잇기는 반올림해도 차이가 5분 이내, 노래는 올림해도 4분 이내라 아이에게 "벌써?"보다 "조금 더"가 되게 했다.
 * 빠른 도착을 재촉하는 말은 쓰지 않는다(decisions.md #2, CLAUDE.md 1-6).
 */
object KidTimeFormatter {
    /** 이 분 이상이면 끝말잇기로 센다 */
    const val WORD_CHAIN_FROM_MIN: Double = 20.0

    /** 끝말잇기 1판 */
    const val WORD_CHAIN_MIN: Double = 10.0
    const val SONG_MIN: Double = 4.0

    /** 모르면 null(화면은 문장을 숨기거나 다른 문구를 쓴다). */
    fun format(remainingMin: Float?): KidTime? {
        val m = remainingMin?.toDouble() ?: return null
        if (m.isNaN()) return null
        if (m <= 0.0) return soon()
        if (m >= WORD_CHAIN_FROM_MIN) {
            // 판(10분) 단위로 반올림. 20분 이상이므로 최소 2판.
            val rounds = floor(m / WORD_CHAIN_MIN + 0.5).toInt().coerceAtLeast(2)
            return KidTime(KidTimeUnit.WORD_CHAIN, rounds.toDouble(), "끝말잇기 ${rounds}판 하면 도착해요", "끝말잇기 ${rounds}판 남았어요")
        }
        val songs = ceil(m / SONG_MIN - 1e-9).toInt().coerceAtLeast(1)
        return KidTime(KidTimeUnit.SONG, songs.toDouble(), "노래 ${songs}곡 같이 부르면 도착해요", "노래 ${songs}곡 남았어요")
    }

    fun soon(): KidTime = KidTime(KidTimeUnit.SOON, 0.0, "곧 도착해요", "곧 도착해요")

    fun arrived(): KidTime = KidTime(KidTimeUnit.ARRIVED, 0.0, "도착했어요!", "도착!")
}
