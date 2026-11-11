package app.familyjourney.screens

import app.familyjourney.core.account.ChildGrowth
import app.familyjourney.core.account.ChildProfile
import app.familyjourney.core.account.ForestAnimal
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.StarRules
import app.familyjourney.core.account.StarWallet
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.account.TreeRun
import app.familyjourney.core.account.TripRewardOutcome
import app.familyjourney.core.demo.DemoDrive
import app.familyjourney.core.demo.DemoPhase
import app.familyjourney.core.demo.DemoPlayback
import app.familyjourney.core.demo.DemoSpeed
import app.familyjourney.core.trip.ForestReward
import app.familyjourney.core.trip.KidTimeFormatter
import app.familyjourney.core.trip.PointAward
import app.familyjourney.core.trip.PointReason
import app.familyjourney.core.trip.ProgressDisplay
import app.familyjourney.core.trip.TripPhase
import app.familyjourney.core.trip.TripState
import app.familyjourney.core.vehicle.Powertrain
import app.familyjourney.feature.forest.ArrivalResultUi
import app.familyjourney.feature.forest.ForestPage
import app.familyjourney.feature.forest.ForestUi
import app.familyjourney.feature.forest.ForestUiMapper
import app.familyjourney.feature.forest.RecordedTrip
import app.familyjourney.feature.forest.TripEfficiency

/*
 * 캡처에 넣을 상태. 앱이 쓰는 실제 모델(TripState, GuardianAccount → ForestUiMapper)로 만든다.
 * 숫자는 기획서 4장 시나리오("할머니 댁 가는 날")에 맞췄다. 별명 "하늘이"는 가상의 별명이다(블라인드 원칙).
 */
internal object ShotSamples {
    private const val KID_ID = "kid-1"
    private const val KID = "하늘이"

    // ---- 진행 화면(feature:journey) ----

    /** 오전 10:00 고속도로: 주행 중 40%, 남은 40분 → "끝말잇기 4판 하면 도착해요" */
    val driving40 = TripState(
        tripId = "screens-trip",
        phase = TripPhase.DRIVING,
        display = ProgressDisplay(percent = 40, kidTime = KidTimeFormatter.format(40f)),
        routeAvailable = true,
        restricted = true,
        parked = false,
    )

    /** 오전 11:00 휴게소: 주행 제한 꺼짐 + 기어 P. "휴게소에서 쉬는 중" + 새싹 수 */
    val restStop = driving40.copy(
        phase = TripPhase.STOPPED,
        display = ProgressDisplay(percent = 50, kidTime = KidTimeFormatter.format(120f)),
        restricted = false,
        parked = true,
    )
    const val REST_SPROUTS = 24

    /** 오후 1:00 도착 + 주차: "도착했어요!" "운전해 줘서 고마워요" [숲 보러 가기] */
    val arrivedParked = driving40.copy(
        phase = TripPhase.ARRIVED,
        display = ProgressDisplay(percent = 100, kidTime = KidTimeFormatter.arrived()),
        restricted = false,
        parked = true,
    )

    /** 데모 주행(배지 + 접힌 발표자 패널) */
    val demoDriving = driving40.copy(demo = true)
    val demoPlayback = DemoPlayback(
        phase = DemoPhase.DRIVING,
        percent = 40,
        positionMs = 70_000,
        lengthMs = 176_000,
        speed = DemoSpeed.X1,
        paused = false,
        running = true,
        drive = DemoDrive.REAL_MOVING,
        waitingForReal = false,
        hint = null,
    )

    // ---- 숲(feature:forest) ----

    /** 하늘이의 나무 11그루(심은 순서): 예전 참나무 4·벚나무 2 + 이번 여행 참나무 5 */
    private val planted11 = listOf(TreeRun(TreeKind.OAK, 4), TreeRun(TreeKind.CHERRY, 2), TreeRun(TreeKind.OAK, 5))
    private val kinds11 = planted11.flatMap { r -> List(r.count) { r.kind } }

    /** 도착 결과 화면 때의 계정: 이번 여행 +360★ → 보유·모은 560★, 아직 연 것 없음 */
    private val atArrival = GuardianAccount(
        id = "screens-guardian",
        confirmedAtEpochMs = 0L,
        wallet = StarWallet(balance = 560, lifetime = 560, monthKey = "2026-11", earnedInMonth = 360),
        children = listOf(ChildProfile(KID_ID, KID, totalSprouts = 118, planted = planted11)),
        lastRiderIds = listOf(KID_ID),
    )

    /**
     * 숲 확인(오후 1:05, 주차 중): 사과나무를 ★ 200개로 연 뒤. 나무 11그루 + 새싹 8개, 동물은 처음부터 있는 다람쥐·토끼.
     * 보유 360★ · 모은 560★ → 나무 도감 일부 잠김, 숲 맵 M2~M4 잠김.
     */
    private val basic = atArrival.copy(
        wallet = atArrival.wallet.copy(balance = 360),
        children = listOf(ChildProfile(KID_ID, KID, totalSprouts = 118, treeKind = TreeKind.APPLE, planted = planted11)),
        boughtTrees = setOf(TreeKind.APPLE),
    )

    fun forest(page: ForestPage = ForestPage.FOREST): ForestUi =
        ForestUiMapper.forest(loaded = true, account = basic, wantedChildId = KID_ID, riders = setOf(KID_ID), parked = true, page = page)

    /** 이번 여행: 400km 중 부드럽게 360km(90%), 전기차 ×1.5 → 새싹 54(나무 5 + 새싹 4), +360★(decisions.md #5 예) */
    fun arrival(): ArrivalResultUi {
        val recorded = RecordedTrip(
            outcome = TripRewardOutcome(
                tripId = "screens-trip",
                reward = ForestReward(
                    creditedKm = 360,
                    multiplierTenths = 15,
                    sprouts = 54,
                    points = PointAward(360, PointReason.AWARDED, 360),
                    smoothPercent = 90,
                    demo = false,
                ),
                riders = listOf(ChildGrowth(KID_ID, KID, sproutsBefore = 64, sproutsAfter = 118, plantedAfter = kinds11, plantedKind = TreeKind.OAK)),
                starBalance = 560,
                lifetimeStars = 560,
            ),
            powertrain = Powertrain.EV,
            efficiency = TripEfficiency(usedWh = 60_000.0, km = 400.0),
        )
        return ForestUiMapper.arrival(recorded, parked = true, view = StarRules.view(atArrival, null))
    }

    /**
     * 큰 맵(M2~M4) 배치 확인용: 나무 25그루 + 새싹 3개, 열 수 있는 동물 10종 모두.
     * 모은 ★가 맵 기준(최대 10,000★) 이상인 오래 쓴 가족을 가정한 값이다(이야기 숫자가 아니라 배치 확인용).
     */
    fun bigMap(map: ForestMap): ForestUi {
        val kinds = listOf(TreeKind.OAK, TreeKind.CHERRY, TreeKind.APPLE, TreeKind.MAPLE, TreeKind.PINE, TreeKind.GINKGO, TreeKind.TANGERINE)
        val runs = listOf(5, 4, 4, 3, 3, 3, 3).mapIndexed { i, n -> TreeRun(kinds[i], n) }
        val account = GuardianAccount(
            id = "screens-guardian",
            confirmedAtEpochMs = 0L,
            wallet = StarWallet(balance = 2_400, lifetime = 13_000),
            children = listOf(ChildProfile(KID_ID, KID, totalSprouts = 253, treeKind = TreeKind.TANGERINE, map = map, planted = runs)),
            lastRiderIds = listOf(KID_ID),
            boughtTrees = kinds.filterNot { it.openAtStart }.toSet(),
            boughtAnimals = ForestAnimal.entries.filterNot { it.openAtStart }.toSet(),
        )
        return ForestUiMapper.forest(loaded = true, account = account, wantedChildId = KID_ID, riders = setOf(KID_ID), parked = true)
    }
}
