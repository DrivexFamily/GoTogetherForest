package app.familyjourney

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 골라 둔 우리 차(결정 #34). null이면 지금까지의 "우리 가족 차"(R3). 개인정보가 아니라 일반 설정 파일에 차 코드만 둔다.
 * 앱을 다시 켜도 남는다. 그림이 없어진 코드가 남아 있으면 앱 화면은 기본 가족 차로 그린다(FamilyCars.of).
 */
class CarSetting(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getString(KEY, null))
    val car: StateFlow<String?> = state.asStateFlow()

    fun set(code: String?) {
        state.value = code
        prefs.edit().apply { if (code == null) remove(KEY) else putString(KEY, code) }.apply()
    }

    private companion object {
        const val FILE = "ggf_settings"
        const val KEY = "family_car"
    }
}
