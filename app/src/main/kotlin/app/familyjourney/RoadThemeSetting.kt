package app.familyjourney

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 골라 둔 길 풍경 테마(결정 #33-⑨). null이면 "여행마다 바꾸기"(기본). 개인정보가 아니라 일반 설정 파일에 테마 이름만 둔다.
 * 앱을 다시 켜도 남는다. 없는 테마 이름이 남아 있으면(그림이 바뀐 경우) 앱 화면은 여행마다 바꾸기로 동작한다(RoadScenes.themeFor).
 */
class RoadThemeSetting(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getString(KEY, null))
    val theme: StateFlow<String?> = state.asStateFlow()

    fun set(theme: String?) {
        state.value = theme
        prefs.edit().apply { if (theme == null) remove(KEY) else putString(KEY, theme) }.apply()
    }

    private companion object {
        const val FILE = "ggf_settings"
        const val KEY = "road_theme"
    }
}
