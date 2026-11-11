package app.familyjourney.feature.forest

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * 지금 여행에 탄 아이를 둘 곳(QA-2). 앱이 꺼졌다 켜져 여행을 이어 가도 "오늘 누가 타나요?"에서 고른 아이에게 새싹이 가게 한다.
 * 글자 한 덩어리만 읽고 쓴다(형식은 [RidersCodec]). 앱은 [DataStoreRiderStore], 테스트는 메모리 구현. 없으면(null) 저장하지 않는다.
 * 담는 것: 여행 ID와 아이의 랜덤 ID뿐(별명·계정 정보 없음).
 */
interface RiderStore {
    suspend fun read(): String?

    /** null이면 지운다 */
    suspend fun write(text: String?)
}

/** 여행 하나에 탄 아이(랜덤 ID). */
internal data class SavedRiders(val tripId: String, val riderIds: Set<String>)

/**
 * [SavedRiders] ↔ 글자. 형식 v1: `v1` 줄, 여행 ID 줄, 아이 ID를 쉼표로 이은 줄(없으면 빈 줄).
 * ID는 앱이 만든 랜덤 값이라 줄바꿈·쉼표가 없다. 형식이 다르면 null(쓰지 않음).
 */
internal object RidersCodec {
    private const val VERSION = "v1"

    fun encode(s: SavedRiders): String = listOf(VERSION, s.tripId, s.riderIds.joinToString(",")).joinToString("\n")

    fun decode(text: String?): SavedRiders? {
        val lines = text?.split('\n') ?: return null
        if (lines.size != 3 || lines[0] != VERSION || lines[1].isBlank()) return null
        val ids = lines[2].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return SavedRiders(lines[1], ids.toCollection(LinkedHashSet()))
    }
}

/** Preferences DataStore의 키 하나(파일 "riders"). 앱에서 한 번만 만든다(AppContainer). */
class DataStoreRiderStore(private val dataStore: DataStore<Preferences>) : RiderStore {
    override suspend fun read(): String? = dataStore.data.first()[KEY]

    override suspend fun write(text: String?) {
        dataStore.edit { prefs -> if (text == null) prefs.remove(KEY) else prefs[KEY] = text }
    }

    companion object {
        private val KEY = stringPreferencesKey("riders_v1")
        const val FILE_NAME: String = "riders"

        fun create(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())): DataStoreRiderStore {
            val app = context.applicationContext
            return DataStoreRiderStore(PreferenceDataStoreFactory.create(scope = scope) { app.preferencesDataStoreFile(FILE_NAME) })
        }
    }
}
