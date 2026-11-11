package app.familyjourney

import android.app.Application

class FamilyJourneyApp : Application() {
    /** 수동 DI: 앱 전체에서 하나만 쓰는 객체를 여기서 조립한다. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
