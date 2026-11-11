package app.familyjourney.core.ui

import androidx.annotation.DrawableRes

/**
 * 진행 화면의 풍경 길 그림(결정 #33, GPT R20). 그림마다 낮 그림, 다크 모드용 저녁 그림, 길 가운데 선이 있다.
 * 자리 모양(넓은 화면·반 화면·세로)에 가장 가까운 그림을 [roadLayout]이 고른다.
 *
 * 그림을 받으면: `design/illustrations/tools/trace_road.py`로 길 선(점 목록)과 길 폭을 따서 [all]에 한 줄씩 더한다.
 * 목록이 비어 있거나(그림이 아직 없음) 다크 모드인데 저녁 그림이 없으면 앱이 지금처럼 길을 직접 그린다.
 */
/** [day]·[evening]은 그림 리소스(drawable-nodpi). 60장을 GgfPicture에 하나씩 적지 않고 만든 목록(RoadSceneData)에서 바로 가리킨다 */
class RoadScene(val theme: String, val spec: RoadSceneSpec, @DrawableRes val day: Int, @DrawableRes val evening: Int?) {
    @DrawableRes
    fun picture(dark: Boolean): Int? = if (dark) evening else day
}

object RoadScenes {
    /**
     * 받은 그림(아직 없음). 테마마다 넓은 화면·반 화면·세로 그림을 더한다(없는 모양은 그 화면에서 앱이 길을 그림).
     * 테마 순서가 곧 우선순위: 첫 테마는 데모(발표)와 열쇠가 없을 때 쓴다.
     */
    val all: List<RoadScene> = roadSceneData

    /** 테마 이름(처음 나온 순서) */
    fun themes(scenes: List<RoadScene> = all): List<String> = scenes.map { it.theme }.distinct()

    /**
     * 쓸 테마: 사람이 고른 테마([choice], 길 풍경 고르기)가 있으면 그것. 없으면 열쇠(여행 ID)로 하나를 고른다 —
     * 같은 열쇠는 늘 같은 테마(String.hashCode는 실행마다 같다), 열쇠가 없으면 첫 테마.
     */
    fun themeFor(key: Any?, scenes: List<RoadScene> = all, choice: String? = null): String? {
        val t = themes(scenes)
        if (t.isEmpty()) return null
        if (choice != null && choice in t) return choice
        return if (key == null) t.first() else t[Math.floorMod(key.hashCode(), t.size)]
    }

    /** 테마 고르기 화면의 작은 그림: 그 테마의 넓은 화면 낮 그림(없으면 아무 그림) */
    @DrawableRes
    fun preview(theme: String, scenes: List<RoadScene> = all): Int? =
        scenes.filter { it.theme == theme }.let { s -> (s.firstOrNull { it.spec.aspect > 1.2f } ?: s.firstOrNull())?.day }

    /** 지금 테마·다크 모드에서 쓸 수 있는 그림만(다크 모드는 저녁 그림이 있는 것만) */
    fun usable(dark: Boolean, scenes: List<RoadScene> = all, theme: String? = null): List<RoadScene> =
        scenes.filter { (theme == null || it.theme == theme) && it.picture(dark) != null }
}
