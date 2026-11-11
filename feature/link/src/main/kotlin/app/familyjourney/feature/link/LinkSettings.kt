package app.familyjourney.feature.link

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 차의 연결 설정. 지금은 "대략 위치 보내기"(기본 꺼짐, 보호자가 주차 중에 켬, 결정 #8) 하나 */
class LinkSettings(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    val shareLocation: StateFlow<Boolean> = store.data.map { it[SHARE_LOCATION] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun setShareLocation(on: Boolean) { store.edit { it[SHARE_LOCATION] = on } }

    companion object {
        private val SHARE_LOCATION = booleanPreferencesKey("share_location")

        fun create(context: Context, scope: CoroutineScope): LinkSettings =
            LinkSettings(PreferenceDataStoreFactory.create(scope = scope) { context.applicationContext.preferencesDataStoreFile("family_link_settings") }, scope)
    }
}
