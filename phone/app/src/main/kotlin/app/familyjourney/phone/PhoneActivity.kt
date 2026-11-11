package app.familyjourney.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.phone.ui.FakeFamilySource
import app.familyjourney.phone.ui.FamilySource
import app.familyjourney.phone.ui.PhoneApp
import app.familyjourney.phone.ui.SyncFamilySource
import kotlinx.coroutines.launch

/**
 * 가족 폰 앱 시작 화면(결정 #37). 서버 주소(gradle.properties `sync.serverUrl`)가 있으면 진짜 공유 서버,
 * 없으면 시험용 가족(FakeFamilySource)으로 돈다. 화면 코드는 같다.
 */
class PhoneActivity : ComponentActivity() {
    private val source: FamilySource by lazy {
        val url = BuildConfig.SYNC_SERVER_URL
        if (url.isBlank()) FakeFamilySource() else SyncFamilySource(applicationContext, url)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        (source as? SyncFamilySource)?.let { s -> lifecycleScope.launch { s.restore() } }
        setContent {
            GgfTheme {
                PhoneApp(source, Modifier.fillMaxSize().safeDrawingPadding())
            }
        }
    }
}
