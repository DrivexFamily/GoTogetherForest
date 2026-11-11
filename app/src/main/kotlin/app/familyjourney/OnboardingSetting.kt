package app.familyjourney

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 처음 실행 안내(GPT R32 그림 3장)를 이미 봤는지. 끝까지 보거나 "건너뛰기"를 누르면 저장하고 다시 보이지 않는다.
 * 기기 설정 파일([RoadThemeSetting]과 같은 파일)에 참/거짓 하나만 둔다(개인정보 없음).
 */
class OnboardingSetting(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getBoolean(KEY, false))
    val seen: StateFlow<Boolean> = state.asStateFlow()

    fun markSeen() {
        state.value = true
        prefs.edit().putBoolean(KEY, true).apply()
    }

    /** 개발 메뉴 "처음부터"용: 다시 보이게 */
    fun reset() {
        state.value = false
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val FILE = "ggf_settings"
        const val KEY = "onboarding_seen"
    }
}
