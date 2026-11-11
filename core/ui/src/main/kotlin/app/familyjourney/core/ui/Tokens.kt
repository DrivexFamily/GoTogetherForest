package app.familyjourney.core.ui

/**
 * 같이가숲 색 토큰의 원래 값(ARGB).
 *
 * Compose 없이 읽을 수 있게 숫자로 둔다. 그래서 단위 테스트가 글자·그림 대비(4.5:1, 3:1)를
 * 바로 검사할 수 있다. 화면에서는 [GgfColors]로 바꿔 쓴다.
 *
 * 색은 `design/overview/index.html`의 팔레트(숲 초록, 잎, 하늘, 해, 산딸기)를 이어받았다.
 */
data class GgfColorTokens(
    /** 앱 바탕 */
    val paper: Long,
    /** 카드·패널 바탕 */
    val card: Long,
    /** 본문 글자 */
    val ink: Long,
    /** 보조 글자 */
    val ink2: Long,
    /** 구분선 */
    val line: Long,
    /** 대표색. 큰 % 숫자, 주 버튼 */
    val forest: Long,
    /** forest 위의 글자 */
    val onForest: Long,
    val leaf: Long,
    val leafSoft: Long,
    /** 하늘색 면(그림용) */
    val sky: Long,
    /** 하늘색 글자(바탕 대비 4.5:1 이상) */
    val skyText: Long,
    val skySoft: Long,
    val sun: Long,
    val sunSoft: Long,
    /** 산딸기색 면(그림용) */
    val berry: Long,
    /** 산딸기색 글자(경고·오류) */
    val berryText: Long,
    val berrySoft: Long,
    /** "데모 주행" 배지 바탕 */
    val demo: Long,
    /** 데모 배지 글자 */
    val onDemo: Long,
    /** 숲 장면 하늘 */
    val sceneSky: Long,
    /** 숲 장면 뒤 언덕 */
    val hillBack: Long,
    /** 숲 장면 앞 언덕 */
    val hillFront: Long,
    /** 진행 길 */
    val road: Long,
    /** 진행 길 가장자리 */
    val roadEdge: Long,
    /** 아직 안 간 길의 점선 */
    val roadLine: Long,
    /** 이미 온 길(초록 자국) */
    val trail: Long,
    /** 아직 안 지난 깃발 */
    val flagIdle: Long,
) {
    companion object {
        val Light = GgfColorTokens(
            paper = 0xFFF5FAF3,
            card = 0xFFFFFFFF,
            ink = 0xFF1D2A22,
            ink2 = 0xFF4B5C51,
            line = 0xFFD3E2D4,
            forest = 0xFF2B774A,
            onForest = 0xFFFFFFFF,
            leaf = 0xFF6BB85A,
            leafSoft = 0xFFE3F3DC,
            sky = 0xFF3F92D2,
            skyText = 0xFF2A6FA8,
            skySoft = 0xFFDDEEFB,
            sun = 0xFFF0AE34,
            sunSoft = 0xFFFFF0CC,
            berry = 0xFFD25A49,
            berryText = 0xFFB0402F,
            berrySoft = 0xFFFBE2DD,
            demo = 0xFF7D5FD0,
            onDemo = 0xFFFFFFFF,
            sceneSky = 0xFFDCEFFC,
            hillBack = 0xFFC3E5B4,
            hillFront = 0xFF9DD08A,
            road = 0xFFD8D4C8,
            roadEdge = 0xFFB9B4A6,
            roadLine = 0xFFFFFFFF,
            trail = 0xFF2B774A,
            flagIdle = 0xFFFFFFFF,
        )

        val Dark = GgfColorTokens(
            paper = 0xFF0F1713,
            card = 0xFF17221C,
            ink = 0xFFE3EEE6,
            ink2 = 0xFFA8BBAE,
            line = 0xFF2A3B31,
            forest = 0xFF6FCB8E,
            onForest = 0xFF0F1713,
            leaf = 0xFF5DAE50,
            leafSoft = 0xFF1C3222,
            sky = 0xFF6BB3EA,
            skyText = 0xFF6BB3EA,
            skySoft = 0xFF172B3A,
            sun = 0xFFF2BE57,
            sunSoft = 0xFF3A2F15,
            berry = 0xFFF08676,
            berryText = 0xFFF08676,
            berrySoft = 0xFF3C201C,
            demo = 0xFFA98FF2,
            onDemo = 0xFF0F1713,
            sceneSky = 0xFF13283A,
            hillBack = 0xFF1E3B29,
            hillFront = 0xFF26502F,
            road = 0xFF343B38,
            roadEdge = 0xFF56605B,
            roadLine = 0xFFC9CFCB,
            trail = 0xFF6FCB8E,
            flagIdle = 0xFF8A958F,
        )
    }
}

/**
 * 도착 축하 무드등 색(decisions #17 "숲 초록색").
 *
 * 화면의 대표색 forest(#2B774A)와 같은 색상(약 145°)이지만, 무드등은 빛이라서 어두운 색이면
 * 흐리게 보인다. 그래서 같은 초록을 LED에 맞게 밝게 올린 값이다.
 * (2026-11-02) 무드등은 core:vehicle의 MoodLightColors.FOREST_GREEN(#2B774A) 하나만 쓰기로 했다(decisions #23-⑤).
 * 이 값은 실차 LED가 흐릴 때 바꿀 후보로 남겨 둔다.
 */
const val FOREST_GREEN_ARGB: Long = 0xFF2EB868

/** 무드등에 보낼 RGB(알파 없음) = #2EB868 */
const val FOREST_GREEN_RGB: Int = 0x2EB868
