package app.familyjourney.core.trip

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
 * 여행 저장본([TripSnapshot])을 둘 곳. 글자 한 덩어리만 읽고 쓴다(형식은 core:trip 안의 코덱이 안다).
 * 앱은 [DataStoreTripStore], 테스트는 메모리 구현을 쓴다. 없으면(null) 저장하지 않는다.
 */
interface TripStore {
    /** 저장된 글자. 없으면 null */
    suspend fun read(): String?

    /** [text]로 바꾼다. null이면 지운다 */
    suspend fun write(text: String?)
}

/**
 * Preferences DataStore의 키 하나에 여행 저장본 JSON을 둔다(파일 "trip"). 앱에서 한 번만 만든다(AppContainer).
 * 암호화하지 않는다: 아이 별명·위치·계정 정보가 없다(여행 ID·시각·누적 주행거리·진행률·판정 개수뿐).
 */
class DataStoreTripStore(private val dataStore: DataStore<Preferences>) : TripStore {

    override suspend fun read(): String? = dataStore.data.first()[KEY]

    override suspend fun write(text: String?) {
        dataStore.edit { prefs -> if (text == null) prefs.remove(KEY) else prefs[KEY] = text }
    }

    companion object {
        private val KEY = stringPreferencesKey("trip_v${TripSnapshotCodec.VERSION}")
        const val FILE_NAME: String = "trip"

        fun create(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())): DataStoreTripStore {
            val app = context.applicationContext
            return DataStoreTripStore(PreferenceDataStoreFactory.create(scope = scope) { app.preferencesDataStoreFile(FILE_NAME) })
        }
    }
}
