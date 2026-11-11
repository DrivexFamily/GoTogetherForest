package app.familyjourney.feature.journey

import app.familyjourney.core.trip.KidTime
import app.familyjourney.core.trip.KidTimeFormatter
import app.familyjourney.core.trip.KidTimeUnit
import app.familyjourney.core.trip.ProgressDisplay
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.ui.WindowWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 진행 화면 상태 규칙(journeyUiOf). decisions.md #3·#4·#19·#21-①. */
class JourneyUiTest {

    private val fourRounds: KidTime = KidTimeFormatter.format(40f)!! // 끝말잇기 4판(#35)

    private fun state(
        phase: TripPhase,
        percent: Int = 40,
        kidTime: KidTime? = fourRounds,
        route: Boolean = true,
        restricted: Boolean = true,
        parked: Boolean = false,
        demo: Boolean = false,
    ) = TripState(
        tripId = "trip-1",
        phase = phase,
        display = ProgressDisplay(percent, kidTime),
        routeAvailable = route,
        restricted = restricted,
        parked = parked,
        demo = demo,
    )

    private fun ui(
        s: TripState,
        width: WindowWidth = WindowWidth.Medium,
        sprouts: Int? = null,
        canStartDemo: Boolean = true,
        canOpenForest: Boolean = true,
        canGoHome: Boolean = true,
        canMute: Boolean = true,
    ) = journeyUiOf(s, width, sprouts, canStartDemo = canStartDemo, canOpenForest = canOpenForest, canGoHome = canGoHome, canMute = canMute)

    /** 목적지 확인 [아니에요](결정 #45, 마스터 11-08): 정차 중이어도 휴게소가 아니라 "내비에 목적지를 설정해 주세요" */
    @Test
    fun rejectedDestinationShowsNoRouteInsteadOfRestStop() {
        val s = state(TripPhase.STOPPED, restricted = false, parked = true)
        assertTrue(ui(s).restStop)
        val u = journeyUiOf(s, WindowWidth.Medium, canStartDemo = true, canGoHome = true, destinationRejected = true)
        assertEquals(JourneyMode.NO_ROUTE, u.mode)
        assertEquals("내비에 목적지를 설정해 주세요", u.sentence)
        assertFalse(u.restStop)
    }

    // ── 주행 중 ──────────────────────────────────────────────

    /** 주행 중 반 화면은 넓은 문장, 정지 그림, 터치는 크게 작게만 */
    @Test
    fun drivingHalfWindowUsesWideSentenceStillArtAndOnlySizeToggle() {
        val u = ui(state(TripPhase.DRIVING))
        assertEquals(JourneyMode.PROGRESS, u.mode)
        assertEquals("끝말잇기 4판 하면 도착해요", u.sentence)
        assertEquals(KidTimeUnit.WORD_CHAIN, u.timeUnit)
        assertTrue(u.driving)
        assertTrue(u.showSizeToggle)
        assertFalse("주행 중 소리 버튼 없음", u.showMute)
        assertFalse(u.showStartDemo)
        assertFalse(u.showForestButton)
        assertFalse("주행 중 발표자 패널은 접힘", u.presenterExpandable)
        assertNull("주행 중 문구는 한 줄(보조 줄 없음)", u.detail)
    }

    /** 좁은 창은 짧은 문장, 전체 창은 넓은 문장 */
    @Test
    fun narrowWindowUsesShortSentenceAndExpandedUsesWide() {
        assertEquals("끝말잇기 4판 남았어요", ui(state(TripPhase.DRIVING), WindowWidth.Compact).sentence)
        assertEquals("끝말잇기 4판 하면 도착해요", ui(state(TripPhase.DRIVING), WindowWidth.Expanded).sentence)
    }

    /** 노래 단위면 노래 그림 */
    @Test
    fun songUnitShowsSongIcon() {
        val u = ui(state(TripPhase.DRIVING, kidTime = KidTimeFormatter.format(10f)))
        assertEquals(KidTimeUnit.SONG, u.timeUnit)
        assertEquals("노래 3곡 같이 부르면 도착해요", u.sentence) // #35 문구(화면에는 안 보이고 음성·설명용, #42)
    }

    /** 주행 중에는 새싹 수가 있어도 보이지 않는다 */
    @Test
    fun drivingNeverShowsSprouts() {
        val u = ui(state(TripPhase.DRIVING), sprouts = 12)
        assertNull(u.detail)
    }

    /** 여행 도중 경로를 잃으면 진행 바는 그대로, 주행 중에는 조작을 요구하지 않는 문장 */
    @Test
    fun routeLostWhileDrivingKeepsProgressAndAsksNothing() {
        val u = ui(state(TripPhase.DRIVING, percent = 60, route = false))
        assertEquals(JourneyMode.PROGRESS, u.mode)
        assertEquals(60, u.percent)
        assertEquals(JourneyTexts.WAITING_ROUTE, u.sentence)
        assertNull("지난 남은 시간 그림을 쓰지 않는다", u.timeUnit)
    }

    /** 여행 도중 경로를 잃고 정차하면 목적지 설정을 부탁한다 */
    @Test
    fun routeLostWhileStoppedAsksForDestination() {
        val u = ui(state(TripPhase.STOPPED, percent = 60, route = false, restricted = false, parked = false))
        assertEquals(JourneyTexts.NO_ROUTE_ASK, u.sentence)
        assertFalse(u.driving)
    }

    // ── 정차·주차 중 ────────────────────────────────────────

    /** 2026-11-10 Mac: 내비 안내를 끝내 경로가 없어지면 휴게소 문구 대신 목적지를 묻는다(0%면 경로 없음 화면) */
    @Test
    fun routeEndedWhileParkedAsksForDestination() {
        val mid = ui(state(TripPhase.STOPPED, percent = 45, restricted = false, parked = true, route = false))
        assertEquals(JourneyMode.PROGRESS, mid.mode)
        assertEquals(45, mid.percent)
        assertEquals(JourneyTexts.NO_ROUTE_ASK, mid.sentence)
        val zero = ui(state(TripPhase.STOPPED, percent = 0, restricted = false, parked = true, route = false))
        assertEquals(JourneyMode.NO_ROUTE, zero.mode)
    }

    /** 휴게소(기어 P)는 쉬는 중 문구와 지금까지 자란 새싹 수, 움직임 허용 */
    @Test
    fun restStopShowsRestSentenceSproutsAndAllowsMotion() {
        val u = ui(state(TripPhase.STOPPED, percent = 45, restricted = false, parked = true), sprouts = 27)
        assertEquals(JourneyMode.PROGRESS, u.mode)
        assertEquals(JourneyTexts.REST, u.sentence)
        assertEquals("지금까지 새싹 27개가 자랐어요", u.detail)
        assertNull(u.timeUnit)
        assertFalse(u.driving)
        assertTrue(u.showMute)
        assertTrue(u.presenterExpandable)
    }

    /** 새싹이 0개이거나 모르면 보조 줄을 숨긴다 */
    @Test
    fun zeroOrUnknownSproutsHideDetail() {
        val s = state(TripPhase.STOPPED, restricted = false, parked = true)
        assertNull(ui(s, sprouts = 0).detail)
        assertNull(ui(s, sprouts = null).detail)
    }

    /** 제한은 풀렸지만 기어 P가 아니면 움직임은 허용, 판정 결과(새싹)는 숨김 */
    @Test
    fun unrestrictedButNotParkedAllowsMotionButHidesSprouts() {
        val u = ui(state(TripPhase.STOPPED, restricted = false, parked = false), sprouts = 27)
        assertFalse(u.driving)
        assertEquals("끝말잇기 4판 하면 도착해요", u.sentence)
        assertNull(u.detail)
    }

    /** 출발 전 경로가 있으면 진행 바, 남은 시간을 모르면 출발 준비 */
    @Test
    fun beforeStartWithRouteShowsProgressOrReady() {
        val withTime = ui(state(TripPhase.BEFORE_START, percent = 0, restricted = false, parked = true))
        assertEquals(JourneyMode.PROGRESS, withTime.mode)
        assertEquals("끝말잇기 4판 하면 도착해요", withTime.sentence)
        assertFalse(withTime.driving)

        val noTime = ui(state(TripPhase.BEFORE_START, percent = 0, kidTime = null, restricted = false, parked = true))
        assertEquals(JourneyTexts.READY, noTime.sentence)
        assertEquals(JourneyTexts.READY_SHORT, ui(state(TripPhase.BEFORE_START, kidTime = null), WindowWidth.Compact).sentence)
    }

    // ── 경로 없음 ───────────────────────────────────────────

    /** 경로 없음 + 주차 중이면 목적지 설정 안내와 데모 버튼 */
    @Test
    fun noRouteWhileParkedAsksDestinationAndOffersDemo() {
        val u = ui(state(TripPhase.BEFORE_START, percent = 0, kidTime = null, route = false, restricted = false, parked = true))
        assertEquals(JourneyMode.NO_ROUTE, u.mode)
        assertEquals(JourneyTexts.NO_ROUTE_ASK, u.sentence)
        assertEquals(JourneyTexts.NO_ROUTE_HELP, u.subline)
        assertTrue(u.showStartDemo)
        assertFalse(u.showSizeToggle)
    }

    /**
     * 경로가 없어도 출발·도착 음성은 나오므로 정차 중에는 소리 끄기를 보여 준다(2026-11-02 Mac qa M1-5).
     * 주행 제한이 켜져 있거나 음성을 쓸 수 없으면 숨긴다.
     */
    @Test
    fun noRouteWhileParkedShowsMuteOnlyWhenUnrestrictedAndVoiceAvailable() {
        val parked = state(TripPhase.BEFORE_START, percent = 0, kidTime = null, route = false, restricted = false, parked = true)
        assertTrue(ui(parked).showMute)
        assertFalse(ui(parked, canMute = false).showMute)
        val driving = state(TripPhase.DRIVING, percent = 0, kidTime = null, route = false, restricted = true, parked = false)
        assertEquals(JourneyMode.NO_ROUTE, ui(driving).mode)
        assertFalse(ui(driving).showMute)
    }

    /** 경로 없음 좁은 창은 짧은 문장 */
    @Test
    fun noRouteNarrowWindowUsesShortSentence() {
        val u = ui(state(TripPhase.BEFORE_START, kidTime = null, route = false, restricted = false, parked = true), WindowWidth.Compact)
        assertEquals(JourneyTexts.NO_ROUTE_ASK_SHORT, u.sentence)
    }

    /** 데모 버튼은 기어 P까지 확인되고, 데모가 꺼져 있고, 콜백이 있을 때만 */
    @Test
    fun demoButtonOnlyWhenParkedDemoOffAndCallbackGiven() {
        val base = state(TripPhase.BEFORE_START, kidTime = null, route = false, restricted = false, parked = true)
        assertFalse("기어 P 아님", ui(base.copy(parked = false)).showStartDemo)
        assertFalse("이미 데모 중", ui(base.copy(demo = true)).showStartDemo)
        assertFalse("콜백 없음", ui(base, canStartDemo = false).showStartDemo)
        assertFalse("주행 제한(모름 포함)", ui(base.copy(restricted = true, parked = false)).showStartDemo)
    }

    /** 경로 없음 + 주행 중이면 중립 문장 한 줄, 도움말·버튼 없음 */
    @Test
    fun noRouteWhileDrivingShowsNeutralLineOnly() {
        val u = ui(state(TripPhase.BEFORE_START, kidTime = null, route = false, restricted = true))
        assertEquals(JourneyMode.NO_ROUTE, u.mode)
        assertEquals(JourneyTexts.WAITING_ROUTE, u.sentence)
        assertNull(u.subline)
        assertTrue(u.driving)
        assertFalse(u.showStartDemo)
    }

    /** 경로를 한 번도 못 본 여행(숲만 켠 경우 등)은 달리는 중에도 경로 없음 화면 */
    @Test
    fun tripThatNeverSawRouteShowsNoRouteEvenWhileDriving() {
        val u = ui(state(TripPhase.DRIVING, percent = 0, kidTime = null, route = false))
        assertEquals(JourneyMode.NO_ROUTE, u.mode)
        assertEquals(0, u.percent)
    }

    // ── 도착 ───────────────────────────────────────────────

    /** 도착했지만 주행 제한이면 100% 정지 그림만 */
    @Test
    fun arrivedWhileRestrictedShowsOnlyStillFullBar() {
        val s = state(TripPhase.ARRIVED, percent = 100, kidTime = KidTimeFormatter.arrived())
        val u = ui(s)
        assertEquals(JourneyMode.ARRIVED_WAITING, u.mode)
        assertEquals(100, u.percent)
        assertEquals("도착했어요!", u.sentence)
        assertTrue(u.driving)
        assertFalse(u.showForestButton)
        assertFalse(u.showHomeButton)
        assertFalse(u.showMute)
        assertEquals("도착!", ui(s, WindowWidth.Compact).sentence)
    }

    /** 도착 후 제한은 풀렸지만 기어 P 전이면 아직 축하 화면이 아니다 */
    @Test
    fun arrivedUnrestrictedButNotParkedIsNotCelebrationYet() {
        val u = ui(state(TripPhase.ARRIVED, percent = 100, kidTime = KidTimeFormatter.arrived(), restricted = false, parked = false))
        assertEquals(JourneyMode.ARRIVED_WAITING, u.mode)
        assertFalse(u.driving)
        assertFalse(u.showForestButton)
    }

    /** 도착 후 주차하면 축하와 숲 보러 가기 */
    @Test
    fun arrivedAndParkedCelebratesAndOffersForest() {
        val s = state(TripPhase.ARRIVED, percent = 100, kidTime = KidTimeFormatter.arrived(), restricted = false, parked = true)
        val u = ui(s)
        assertEquals(JourneyMode.ARRIVED_PARKED, u.mode)
        assertEquals(JourneyTexts.ARRIVED_TITLE, u.sentence)
        assertEquals(JourneyTexts.THANKS, u.subline)
        assertFalse(u.driving)
        assertTrue(u.showForestButton)
        assertFalse(u.showHomeButton)
        assertTrue(u.showMute)

        val journeyOnly = ui(s, canOpenForest = false)
        assertFalse(journeyOnly.showForestButton)
        assertTrue("숲이 꺼져 있으면 홈으로", journeyOnly.showHomeButton)
    }

    /** 도착 표시가 남은 시간 말에 없어도 도착 문장을 쓴다 */
    @Test
    fun arrivedUsesArrivalSentenceEvenWithoutArrivedKidTime() {
        val u = ui(state(TripPhase.ARRIVED, percent = 99, kidTime = fourRounds))
        assertEquals(100, u.percent)
        assertEquals("도착했어요!", u.sentence)
    }

    // ── 공통 ───────────────────────────────────────────────

    /** 데모 배지는 모든 모습에서 데모일 때 늘 보인다 */
    @Test
    fun demoBadgeAlwaysShownInEveryModeAndWidth() {
        val states = listOf(
            state(TripPhase.DRIVING, demo = true),
            state(TripPhase.STOPPED, restricted = false, parked = true, demo = true),
            state(TripPhase.BEFORE_START, kidTime = null, route = false, demo = true),
            state(TripPhase.ARRIVED, restricted = true, demo = true),
            state(TripPhase.ARRIVED, restricted = false, parked = true, demo = true),
        )
        for (s in states) {
            for (w in WindowWidth.entries) {
                assertTrue("${s.phase} $w", ui(s, w).showDemoBadge)
                assertFalse("${s.phase} $w", ui(s.copy(demo = false), w).showDemoBadge)
            }
        }
    }

    /** 소리 버튼은 음성이 있을 때만 */
    @Test
    fun muteButtonOnlyWhenVoiceAvailable() {
        val s = state(TripPhase.STOPPED, restricted = false, parked = true)
        assertTrue(ui(s).showMute)
        assertFalse(ui(s, canMute = false).showMute)
    }

    /** 진행률은 0~100으로 자른다 */
    @Test
    fun percentIsClamped() {
        assertEquals(100, ui(state(TripPhase.DRIVING, percent = 130)).percent)
    }

    @Test
    fun `풍경 길 테마 열쇠는 실차면 여행 ID, 데모면 없음(늘 첫 테마)`() {
        assertEquals("trip-1", ui(state(TripPhase.DRIVING)).roadKey)
        assertEquals("trip-1", ui(state(TripPhase.ARRIVED, percent = 100, parked = true, restricted = false)).roadKey)
        assertNull(ui(state(TripPhase.DRIVING, demo = true)).roadKey)
    }

    @Test
    fun `길 풍경 버튼은 주차 중(주행 제한 꺼짐)이고 고를 수 있을 때만`() {
        fun show(s: TripState, can: Boolean) = journeyUiOf(s, WindowWidth.Medium, canPickTheme = can).showThemeButton
        assertTrue(show(state(TripPhase.STOPPED, restricted = false, parked = true), true))
        assertTrue(show(state(TripPhase.BEFORE_START, route = false, restricted = false, parked = true), true))
        assertFalse("주행 중에는 없음", show(state(TripPhase.DRIVING), true))
        assertFalse("정차했지만 기어 P 전", show(state(TripPhase.DRIVING, restricted = false, parked = false), true))
        assertFalse("고를 수 없으면 없음", show(state(TripPhase.STOPPED, restricted = false, parked = true), false))
    }

    @Test
    fun `우리 차 버튼은 길 풍경과 같은 조건 + 고를 차 그림이 있을 때만(결정 #34)`() {
        fun show(s: TripState, can: Boolean) = journeyUiOf(s, WindowWidth.Medium, canPickCar = can).showCarButton
        assertTrue(show(state(TripPhase.STOPPED, restricted = false, parked = true), true))
        assertTrue(show(state(TripPhase.BEFORE_START, route = false, restricted = false, parked = true), true))
        assertFalse("주행 중에는 없음", show(state(TripPhase.DRIVING), true))
        assertFalse("정차했지만 기어 P 전", show(state(TripPhase.DRIVING, restricted = false, parked = false), true))
        assertFalse("고를 차 그림이 없으면 없음", show(state(TripPhase.STOPPED, restricted = false, parked = true), false))
    }

    @Test
    fun `진행 화면 홈 버튼은 주행 제한이 꺼져 있을 때만(Pleos에는 늘 보이는 뒤로 버튼이 없음)`() {
        fun top(s: TripState, home: Boolean = true, forest: Boolean = true) =
            journeyUiOf(s, WindowWidth.Medium, canGoHome = home, canOpenForest = forest).showTopHome
        assertTrue("휴게소", top(state(TripPhase.STOPPED, restricted = false, parked = true)))
        assertTrue("경로 없음·정차", top(state(TripPhase.BEFORE_START, route = false, restricted = false, parked = true)))
        assertTrue("도착·주차(숲 켜짐)", top(state(TripPhase.ARRIVED, restricted = false, parked = true)))
        assertFalse("도착·주차(숲 꺼짐)은 아래 홈으로가 있음", top(state(TripPhase.ARRIVED, restricted = false, parked = true), forest = false))
        assertFalse("주행 중", top(state(TripPhase.DRIVING)))
        assertFalse("홈으로 갈 수 없는 화면", top(state(TripPhase.STOPPED, restricted = false, parked = true), home = false))
    }
}
