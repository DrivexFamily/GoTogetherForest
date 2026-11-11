package app.familyjourney

import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.familyjourney.core.account.AccountRepository
import app.familyjourney.core.account.DemoStarSession
import app.familyjourney.core.account.InMemoryAccountRepository
import app.familyjourney.core.account.LocalAccountRepository
import app.familyjourney.feature.link.FamilyLinkController
import app.familyjourney.core.demo.DemoSpeed
import app.familyjourney.core.demo.DemoVehicleDataProvider
import app.familyjourney.core.trip.DataStoreTripStore
import app.familyjourney.core.trip.TripEvent
import app.familyjourney.core.trip.TripStore
import app.familyjourney.core.trip.TripTracker
import app.familyjourney.core.vehicle.LoggingVehicleControlProvider
import app.familyjourney.core.vehicle.MoodLightColors
import app.familyjourney.core.vehicle.NoOpVehicleControlProvider
import app.familyjourney.core.vehicle.SignalMode
import app.familyjourney.core.vehicle.SwitchingVehicleDataProvider
import app.familyjourney.core.vehicle.VehicleControlProvider
import app.familyjourney.core.vehicle.describe
import app.familyjourney.core.vehicle.pleos.PleosVehicleConfig
import app.familyjourney.core.vehicle.pleos.PleosVehicleControlProvider
import app.familyjourney.core.vehicle.pleos.PleosVehicleDataProvider
import app.familyjourney.core.voice.NoOpVoiceOutput
import app.familyjourney.core.voice.PleosTtsVoiceOutput
import app.familyjourney.core.voice.VoiceOutput
import app.familyjourney.core.atlas.AtlasRepository
import app.familyjourney.core.atlas.LandmarkCatalog
import app.familyjourney.core.atlas.PrefsAtlasStorage
import app.familyjourney.feature.atlas.AtlasStateHolder
import app.familyjourney.feature.forest.DataStoreRiderStore
import app.familyjourney.feature.forest.ForestRecorder
import app.familyjourney.feature.forest.ForestStateHolder
import app.familyjourney.feature.journey.JourneyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * 수동 DI 조립 담당. 앱 전체에서 하나만 쓰는 객체를 여기서 만든다(FamilyJourneyApp.onCreate에서 한 번).
 *
 * ```
 *  realVehicleData ─┐
 *  demoVehicleData ─┴▶ vehicleData(실차/데모 스위치) ─▶ trip(TripTracker) ─┬▶ journey(JourneyController): 음성·무드등
 *                                                                        ├▶ forestRecorder: 도착 → 아이 숲 적립(accounts)
 *                                                                        └▶ forest(ForestStateHolder): 숲 화면 상태
 *  vehicleData.mode(데모?) ─▶ demoStars(발표용 ★ 세션, 메모리) ─▶ forestRecorder·forest
 *  nav: 화면 이동 판단 값(AppNavigation이 쓴다)    actions: 출발·데모·홈으로의 순서(TripActions)
 * ```
 *
 * - **도착을 놓치지 않게** 모든 객체를 앱 시작 때 바로 만든다(화면을 열 때 만들지 않는다). 화면이 없어도
 *   (예: 홈을 보고 있어도) 도착 이벤트를 듣고 음성·무드등·숲 적립을 한다.
 * - **기능 플래그**(gradle.properties → BuildConfig)로 꺼진 기능의 객체는 만들지 않는다(null).
 *   진행 화면이 꺼지면 [journey]가 없고(음성·무드등 없음), 숲이 꺼지면 [accounts]·[forestRecorder]·[forest]가 없다.
 * - 데모와 실차는 [vehicleData] 스위치 하나로 바꿔 끼운다(decisions.md #10). 기능은 데모인지 모른다.
 * - **여행이 끊기지 않게:** 신호를 받을지는 화면 보임 + 여행 단계 + 데이터 모드로 정한다([SignalCollection]).
 *   실차 여행 중(주행 중·정차 중)이면 백그라운드에서도 받는다. 여행 상태는 [tripStore]에 저장해 프로세스가 죽어도 이어 간다.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 길 풍경 고르기(결정 #33-⑨): 골라 둔 테마, 없으면 여행마다 바꾸기 */
    val roadTheme = RoadThemeSetting(appContext)
    /** 처음 실행 안내(GPT R32)를 봤는지 */
    val onboarding = OnboardingSetting(appContext)

    /** 우리 차 고르기(결정 #34): 골라 둔 현대·기아 차 코드, 없으면 우리 가족 차 */
    val familyCar = CarSetting(appContext)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val features = FeatureFlags(
        journey = BuildConfig.FEATURE_JOURNEY,
        forest = BuildConfig.FEATURE_FOREST,
        devTools = BuildConfig.DEV_TOOLS,
        atlas = BuildConfig.FEATURE_ATLAS,
    )

    /** 실차 제공자. 신호 확인 화면이 진단 정보·재연결에 직접 쓴다. */
    val realVehicleData = PleosVehicleDataProvider(
        appContext,
        PleosVehicleConfig(devDiagnostics = BuildConfig.DEBUG),
    )

    // 데모는 16배속으로 시작(섞기 모드에서 실제로 약 10초 달리면 도착, 2026-11-08 마스터 지시). 발표자 패널에서 1×로 바꿀 수 있다
    val demoVehicleData = DemoVehicleDataProvider(initialSpeed = DemoSpeed.X16)

    /** 앱 전체가 쓰는 차량 데이터 창구(실차/데모 스위치) */
    val vehicleData = SwitchingVehicleDataProvider(
        real = realVehicleData,
        demo = demoVehicleData,
        scope = appScope,
    )

    /**
     * 차량 제어(지금은 도착 축하 무드등, decisions.md #17). 안전 규칙은 데모와 섞지 않은 실차 신호로 판단한다
     * (기어 P 또는 주차브레이크 + 주행 제한 꺼짐, #21-②, core:vehicle `isParkedForControl`).
     * 만들지 못하면(SDK 없음 등) 아무것도 하지 않는 구현으로 대신한다.
     */
    val vehicleControl: VehicleControlProvider = LoggingVehicleControlProvider(
        delegate = try {
            PleosVehicleControlProvider(
                appContext,
                realSignals = { realVehicleData.signals.value },
                devDiagnostics = BuildConfig.DEBUG,
            )
        } catch (t: Throwable) {
            NoOpVehicleControlProvider
        },
        // 모든 제어 요청·결과를 release에서도 한 줄씩(#17, QA-4). 색과 결과만, 개인정보·위치 없음
        log = { Log.i(PleosVehicleControlProvider.TAG, it) },
    )

    /** 음성 안내(decisions.md #16). TTS를 만들지 못하면 소리 없이 화면 문구만 남는다. */
    val voice: VoiceOutput = try {
        PleosTtsVoiceOutput(appContext, devDiagnostics = BuildConfig.DEBUG)
    } catch (t: Throwable) {
        NoOpVoiceOutput
    }

    /** 여행 도중 상태 저장(DataStore 파일 "trip"). 만들지 못하면 저장 없이 동작한다(앱이 꺼지면 여행이 끊김). */
    private val tripStore: TripStore? = try {
        DataStoreTripStore.create(appContext)
    } catch (t: Throwable) {
        devLog("여행 저장소를 만들지 못함: ${t.javaClass.simpleName}")
        null
    }

    /**
     * 여행 상태(core:trip). 두 기능이 함께 듣는다. 기능이 모두 꺼져 있어도 만든다(가볍고, 신호 확인 화면이 쓴다).
     * 시계는 부팅 뒤 시간(elapsedRealtime): 프로세스가 바뀌어도 이어져서, 저장한 여행의 출발 시각·나이를 그대로 잰다.
     */
    val trip = TripTracker(
        vehicleData.signals,
        appScope,
        clockMs = SystemClock::elapsedRealtime,
        store = tripStore,
        log = ::devLog,
    )

    /** 앱 화면이 보이나(MainActivity onStart/onStop을 센다). 신호 수집([SignalCollection])과 음성에 쓴다 */
    private val visibleActivities = VisibleActivities()
    private val appVisible = MutableStateFlow(false)

    /**
     * 진행 화면의 화면 밖 연결: 음성(Voice 이벤트 → [voice]), 도착 축하 무드등(ArrivalCelebration → [vehicleControl]).
     * 무드등의 안전 확인(실제 기어 P 또는 주차브레이크 + 실제 주행 제한 꺼짐)은 core:vehicle이 한 번 더 한다.
     */
    val journey: JourneyController? = if (features.journey) {
        JourneyController(
            events = trip.events,
            voice = voice,
            scope = appScope,
            requestMoodLight = {
                val result = vehicleControl.setMoodLightColor(MoodLightColors.FOREST_GREEN)
                devLog("도착 축하 무드등: ${result.describe()}")
            },
            log = ::devLog,
            // 백그라운드에서는 말하지 않는다(#27-②). 가려진 채 도착하면 감사 인사는 다시 보일 때 1번(#27-③, 도착 뒤 10분 안).
            // 바꾸려면 SPEAK_IN_BACKGROUND만 고친다.
            canSpeak = if (SPEAK_IN_BACKGROUND) MutableStateFlow(true) else appVisible,
            clockMs = SystemClock::elapsedRealtime,
        )
    } else {
        null
    }

    /**
     * 계정 저장소(decisions.md #7-2). 차 안 암호화 저장(LocalAccountRepository)을 쓰고,
     * 만들지 못하면 메모리 저장(앱을 끄면 사라짐)으로 대신한다. 숲이 꺼져 있으면 만들지 않는다.
     */
    private val localAccounts: AccountRepository? = if (features.forest) {
        try {
            LocalAccountRepository.create(appContext)
        } catch (t: Throwable) {
            devLog("차 안 저장소를 만들지 못해 메모리 저장을 씀: ${t.javaClass.simpleName}")
            InMemoryAccountRepository()
        }
    } else {
        null
    }

    /**
     * 가족 폰 연결(decisions.md #37, feature:link). **feature.link.enabled=false(기본)면 null**이고 아무것도 만들지 않는다.
     * 켜져 있으면 계정 창구를 덧씌운다(연결 전에는 그대로 LocalAccountRepository, 연결 뒤에는 동기화 저장소).
     */
    val familyLink: FamilyLinkController? = if (BuildConfig.FEATURE_LINK && localAccounts != null) {
        try {
            FamilyLinkController.create(
                appContext, BuildConfig.SYNC_SERVER_URL, localAccounts, trip.state, vehicleData.signals, appScope, ::devLog,
                appearance = { roadTheme.theme.value to familyCar.car.value },
            )
        } catch (t: Throwable) {
            devLog("가족 연결을 만들지 못함: ${t.javaClass.simpleName}")
            null
        }
    } else {
        null
    }

    val accounts: AccountRepository? = familyLink?.accounts ?: localAccounts

    /**
     * 발표용 ★ 세션(decisions.md #31-⑤). 데모 모드 동안 숲의 해금·고르기를 메모리에만 적고, 데모를 끄면 버린다
     * (실제 ★·해금은 그대로). 숲이 꺼져 있으면 만들지 않는다.
     */
    val demoStars: DemoStarSession? = if (accounts != null) {
        val demoActive = vehicleData.mode.map { it != SignalMode.REAL }
            .stateIn(appScope, SharingStarted.Eagerly, vehicleData.mode.value != SignalMode.REAL)
        DemoStarSession(demoActive, appScope)
    } else {
        null
    }

    /** 도착 → 아이 숲 적립(화면 밖). 앱 시작 때 바로 만들어 도착을 놓치지 않는다. 탄 아이는 여행과 함께 저장한다(QA-2). */
    val forestRecorder: ForestRecorder? = accounts?.let {
        val riders = try {
            DataStoreRiderStore.create(appContext)
        } catch (t: Throwable) {
            devLog("탄 아이 저장소를 만들지 못함: ${t.javaClass.simpleName}")
            null
        }
        ForestRecorder(trip, it, appScope, vehicleData.signals, riders, demoStars)
    }

    /** 숲 화면 3개(아이 고르기, 아이의 숲·도감·맵, 도착 결과)의 상태 */
    val forest: ForestStateHolder? =
        if (accounts != null && forestRecorder != null) ForestStateHolder(accounts, forestRecorder, trip.state, appScope, demoStars) else null

    /**
     * 지역별 랜드마크 도감(결정 #40). 끄면 null. 도착 판정은 [AtlasArrivals]가 여행 도착 사건마다 한 번 한다.
     * 화면의 주차 여부는 여행 상태의 parked(실제 주행 제한 꺼짐 + 기어 P)를 쓴다.
     */
    private val atlasRepository: AtlasRepository? =
        if (features.atlas) AtlasRepository(LandmarkCatalog.all, PrefsAtlasStorage(appContext)) else null
    /** 내비 목적지 확인(결정 #45). 데모는 확인할 때까지 데모 재생을 멈춘다 */
    val destinationConfirm = DestinationConfirmHolder(
        vehicleData.signals, appScope, demoVehicleData,
        answeredStore = object : DestinationConfirmHolder.AnsweredStore {
            private val prefs = appContext.getSharedPreferences("destination_confirm", android.content.Context.MODE_PRIVATE)
            override fun read(): String? = prefs.getString("answered", null)
            override fun write(hash: String?) {
                prefs.edit().apply { if (hash == null) remove("answered") else putString("answered", hash) }.apply()
            }
        },
    )

    /** 숲 배경 음악(결정 #47) */
    val forestMusic = ForestMusic(appContext)

    val atlas: AtlasStateHolder? = atlasRepository?.let { AtlasStateHolder(it, trip.state.map { s -> s.parked }.distinctUntilChanged(), appScope) }

    /** 출발·데모·홈으로 버튼이 여행에 하는 일(순서가 중요한 것) */
    val actions = TripActions(
        vehicleData = vehicleData,
        demo = demoVehicleData,
        trip = trip,
        dismissArrival = forest?.let { holder -> { holder.dismissArrival() } },
    )

    private val accountStatus: Flow<AccountStatus> =
        accounts?.account
            ?.map { a -> AppNavigation.accountStatusOf(loaded = true, hasGuardian = a != null, childCount = a?.children?.size ?: 0) }
            ?.onStart { emit(AccountStatus.LOADING) }
            ?: flowOf(AccountStatus.NOT_USED)

    /**
     * 화면 이동 판단 값(드물게 바뀌는 것만 모은다). 차속처럼 자주 바뀌는 신호는 여기서 걸러서
     * 화면 뿌리(AppRoot)가 신호마다 다시 그려지지 않게 한다. 화면이 구독하지 않으면 5초 뒤 멈춘다(백그라운드).
     */
    val nav: StateFlow<NavContext> = run {
        val arrivalId: Flow<String?> = forest?.arrival?.map { it?.tripId } ?: flowOf(null)
        AppNavigation.navContextFlow(
            features = features,
            tripState = trip.state,
            merged = vehicleData.signals,
            real = realVehicleData.signals,
            account = accountStatus,
            arrivalResultId = arrivalId,
            mode = vehicleData.mode,
        ).stateIn(appScope, SharingStarted.WhileSubscribed(STOP_MS), NavContext(features))
    }

    init {
        atlasRepository?.let { AtlasArrivals.start(appScope, it, trip.events, vehicleData) }
        // 도착 요약 한 줄(debug, GgfApp): 출발·끝 주행거리, 총 km, 판정 못 한 km, 포인트 km, 새싹, 데모 여부
        if (BuildConfig.DEBUG) {
            appScope.launch(start = CoroutineStart.UNDISPATCHED) {
                trip.events.filterIsInstance<TripEvent.Arrived>().collect { devLog(ArrivalLog.line(it.result)) }
            }
        }
        // 신호 수집 켜기/끄기. SDK 연결(Car 서비스 등)은 전처럼 메인 스레드에서 한다.
        appScope.launch(Dispatchers.Main.immediate) {
            val tripNeeds = trip.state.map { it.phase to it.celebrated }.distinctUntilChanged()
            combine(appVisible, tripNeeds, vehicleData.mode) { visible, (phase, celebrated), mode ->
                SignalCollection.needed(visible, phase, mode, celebrated)
            }.distinctUntilChanged().collect { needed ->
                if (needed) vehicleData.start() else vehicleData.stop()
                devLog(if (needed) "차량 신호 받기 시작" else "차량 신호 받기 멈춤")
            }
        }
    }

    /**
     * Activity가 보이게 됐거나(onStart, [started] = true) 가려졌을 때(onStop, false). 메인 스레드에서 부른다.
     * 여러 Activity를 센다(새 Activity의 onStart가 옛 Activity의 onStop보다 먼저 와도 "보임"이 유지되게).
     * 보일 때와 가려질 때 모두 여행 상태를 바로 저장한다(30초 간격을 기다리지 않는다).
     *  - 가려질 때: 그 뒤 OS가 앱을 얼리거나(에뮬레이터 실측: 가려지고 약 19초 뒤 `am_freeze`) 끝낼 수 있다.
     *  - 보일 때: 가려진 동안 받은 거리를 바로 남긴다(돌아온 직후 강제 종료돼도 그 거리가 "판정 못 함"이 되지 않게, Mac B4 3차).
     */
    fun onAppVisible(started: Boolean) {
        val visible = if (started) visibleActivities.onStart() else visibleActivities.onStop()
        appVisible.value = visible
        trip.saveNow()
    }

    /**
     * 버튼이 부르는 여행 동작을 앱 범위에서 실행한다(화면이 회전·재생성돼도 중간에 끊기지 않게).
     */
    fun launchAction(block: suspend TripActions.() -> Unit): Job = appScope.launch { actions.block() }

    /**
     * (개발용, 디버그 빌드만) 아이 추가. 점검 스크립트가 한글 별명을 넣을 때 MainActivity가 부른다
     * (adb input은 한글을 못 넣는다). 화면의 "만들기"와 같은 규칙을 거친다(주차 중·보호자 확인 뒤).
     */
    fun devAddChild(nickname: String) {
        if (features.devTools) forest?.addChild(nickname, guardianChecked = true)
    }

    private fun devLog(message: String) {
        // 아이 별명·계정 정보는 넣지 않는다(여기에는 고정 문장과 결과만 온다)
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    companion object {
        /** adb logcat -s GgfApp (디버그 빌드만): 음성 문장과 결과, 도착 축하 무드등 결과 */
        const val TAG = "GgfApp"
        private const val STOP_MS = 5_000L

        /** 백그라운드(다른 앱이 화면을 가림)에서도 음성 안내를 낼까. 결정 필요, 기본은 말하지 않음 */
        private const val SPEAK_IN_BACKGROUND = false
    }
}
