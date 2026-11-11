package app.familyjourney.feature.forest

import androidx.compose.runtime.Composable
import app.familyjourney.core.account.ChildGrowth
import app.familyjourney.core.account.ChildProfile
import app.familyjourney.core.account.DemoStars
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.StarRules
import app.familyjourney.core.account.StarWallet
import app.familyjourney.core.account.TreeKind
import app.familyjourney.core.account.TreeRun
import app.familyjourney.core.account.TripRewardOutcome
import app.familyjourney.core.trip.ForestReward
import app.familyjourney.core.trip.PointAward
import app.familyjourney.core.trip.PointReason
import app.familyjourney.core.ui.GgfTheme
import app.familyjourney.core.ui.GgfWindowPreviews
import app.familyjourney.core.ui.GgfWindowPreviewsDark
import app.familyjourney.core.vehicle.Powertrain

/*
 * 미리보기(Android Studio). 크기 묶음은 core:ui의 것을 쓴다:
 *  - GgfWindowPreviews: 전체 1200×720, 반 화면 600×720, 2:1 넓은 쪽 800×720, 좁은 쪽 400×720, 세로 720×1200, 세로 반 720×600, 낮은 가로 960×440
 *  - GgfWindowPreviewsDark: 다크 전체·반 화면·좁은 쪽·세로
 * 별명은 가상의 별명이다(블라인드 원칙).
 */

internal object ForestSamples {
    private val kong = ChildProfile(
        "c1", "콩이", totalSprouts = 126, treeKind = TreeKind.APPLE,
        planted = listOf(TreeRun(TreeKind.OAK, 7), TreeRun(TreeKind.APPLE, 5)),
    )
    private val byul = ChildProfile("c2", "별이", totalSprouts = 54)

    /** 보유 1,030★ · 모은 1,230★, 사과나무를 연 가족 */
    val account = GuardianAccount(
        id = "guardian",
        confirmedAtEpochMs = 0L,
        wallet = StarWallet(balance = 1_030, lifetime = 1_230, monthKey = "2026-11", earnedInMonth = 360),
        children = listOf(kong, byul),
        lastRiderIds = listOf("c1"),
        boughtTrees = setOf(TreeKind.APPLE),
    )

    /** decisions.md #5의 예: 400km 중 360km 안전, 전기차 → 새싹 54(나무 5 + 4), 360★ */
    fun recorded(demo: Boolean) = RecordedTrip(
        outcome = TripRewardOutcome(
            tripId = "sample",
            reward = ForestReward(
                creditedKm = 360,
                multiplierTenths = 15,
                sprouts = 54,
                points = if (demo) PointAward(0, PointReason.DEMO, 360) else PointAward(360, PointReason.AWARDED, 360),
                smoothPercent = 90,
                demo = demo,
            ),
            riders = listOf(
                ChildGrowth("c1", "콩이", 72, 126, List(7) { TreeKind.OAK } + List(5) { TreeKind.APPLE }, TreeKind.APPLE),
                ChildGrowth("c2", "별이", 0, 54, List(5) { TreeKind.OAK }, TreeKind.OAK),
            ),
            starBalance = if (demo) 670 else 1_030,
            lifetimeStars = if (demo) 870 else 1_230,
        ),
        powertrain = Powertrain.EV,
        efficiency = TripEfficiency(usedWh = 60_000.0, km = 400.0),
    )

    fun arrival(parked: Boolean = true, demo: Boolean = false) =
        ForestUiMapper.arrival(recorded(demo), parked, StarRules.view(account, if (demo) DemoStars(granted = 1_000) else null))

    fun forest(parked: Boolean = true, page: ForestPage = ForestPage.FOREST, demo: Boolean = false) =
        ForestUiMapper.forest(true, account, null, setOf("c1"), parked, if (demo) DemoStars(granted = 1_000) else null, page)
    fun picker(step: PickerStep, parked: Boolean = true): ChildPickerUi = when (step) {
        PickerStep.PICK -> ForestUiMapper.picker(true, account, setOf("c1"), parked)
        PickerStep.ADD_FIRST_CHILD -> ForestUiMapper.picker(true, account.copy(children = emptyList()), emptySet(), parked)
        else -> ForestUiMapper.picker(true, null, emptySet(), parked)
    }
}

@GgfWindowPreviews
@GgfWindowPreviewsDark
@Composable
private fun ArrivalResultPreview() = GgfTheme {
    ArrivalResultScreen(ForestSamples.arrival(), onOpenForest = {}, onDone = {})
}

@GgfWindowPreviews
@Composable
private fun ArrivalResultDemoPreview() = GgfTheme {
    ArrivalResultScreen(ForestSamples.arrival(demo = true), onOpenForest = {}, onDone = {})
}

@GgfWindowPreviews
@GgfWindowPreviewsDark
@Composable
private fun ArrivalResultDrivingPreview() = GgfTheme {
    ArrivalResultScreen(ForestSamples.arrival(parked = false), onOpenForest = {}, onDone = {})
}

@GgfWindowPreviews
@GgfWindowPreviewsDark
@Composable
private fun ForestScreenPreview() = GgfTheme {
    ForestScreen(ForestSamples.forest(), ForestActions(onBack = {}))
}

@GgfWindowPreviews
@GgfWindowPreviewsDark
@Composable
private fun TreeBookPreview() = GgfTheme {
    ForestScreen(ForestSamples.forest(page = ForestPage.TREES), ForestActions())
}

@GgfWindowPreviews
@Composable
private fun AnimalBookDemoPreview() = GgfTheme {
    ForestScreen(ForestSamples.forest(page = ForestPage.ANIMALS, demo = true), ForestActions(onPresenterStars = {}))
}

@GgfWindowPreviews
@Composable
private fun MapPagePreview() = GgfTheme {
    ForestScreen(ForestSamples.forest(page = ForestPage.MAPS), ForestActions())
}

@GgfWindowPreviews
@Composable
private fun ForestScreenDrivingPreview() = GgfTheme {
    ForestScreen(ForestSamples.forest(parked = false), ForestActions())
}

@GgfWindowPreviews
@GgfWindowPreviewsDark
@Composable
private fun ChildPickerPickPreview() = GgfTheme {
    ChildPickerScreen(ForestSamples.picker(PickerStep.PICK), onConfirmGuardian = {}, onAddChild = { _, _ -> }, onToggleChild = {}, onDone = {})
}

@GgfWindowPreviews
@GgfWindowPreviewsDark
@Composable
private fun ChildPickerFirstRunPreview() = GgfTheme {
    ChildPickerScreen(ForestSamples.picker(PickerStep.CONFIRM_GUARDIAN), onConfirmGuardian = {}, onAddChild = { _, _ -> }, onToggleChild = {}, onDone = {})
}

@GgfWindowPreviews
@Composable
private fun ChildPickerFirstChildPreview() = GgfTheme {
    ChildPickerScreen(ForestSamples.picker(PickerStep.ADD_FIRST_CHILD), onConfirmGuardian = {}, onAddChild = { _, _ -> }, onToggleChild = {}, onDone = {})
}
