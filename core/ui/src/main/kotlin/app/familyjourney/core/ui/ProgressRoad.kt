package app.familyjourney.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 아이용 진행 길. 집(출발)에서 목적지 집까지 길이 있고, 우리 차가 [percent] 자리에 서 있다.
 *
 * - 차는 1% 단위로 **즉시** 그 자리로 간다(미끄러지는 전환 효과 없음). 주행·주차 모두 같다.
 * - 지나온 길은 초록 자국, 남은 길은 점선. 10%마다 GPT 새싹 이정표(지난 것은 또렷, 남은 것은 옅게).
 * - 바탕은 GPT 숲길 풍경, 출발은 GPT 핀, 목적지에는 작은 숲 앞에서 토끼·다람쥐가 기다린다(GPT goal-grove, 결정 #29, #42).
 * - 25/50/75%에서 차 안 가족의 포즈가 바뀐다([drivePoseFor]).
 * - [animate]=false(주행 중): 완전한 정지 그림. true(주차 중): 같은 자리에서 차 안 아이만 짧게 움직인다.
 * - 자리가 넓으면 곧은 길, 세로로 길면 구불구불한 길([RoadShape.Auto]). 높이를 안 주면 가로:세로 3:1로 잡는다.
 * - 길이 그려진 풍경 그림([RoadScenes], 결정 #33)이 있으면 자리 모양에 맞는 그림을 깔고 그 그림의 길을 따라간다(Auto일 때).
 *   테마가 여럿이면 [themeKey](여행 ID)로 하나를 고른다: 같은 여행에서는 늘 같은 풍경, 다음 여행은 다른 풍경일 수 있다.
 *   사람이 길 풍경을 골라 두었으면([themeChoice]) 그 테마를 쓴다.
 * - 화면 읽기 설명은 길 전체에 하나다: 진행률 + 지금 차 안 가족 포즈([progressRoadDescription]). 차 그림 자체는 설명이 없다
 *   (같은 말을 두 번 읽지 않게). 점검 스크립트도 이 설명으로 포즈를 확인한다.
 */
@Composable
fun ProgressRoad(
    percent: Int,
    animate: Boolean,
    modifier: Modifier = Modifier,
    milestonesEvery: Int = 10,
    shape: RoadShape = RoadShape.Auto,
    look: FamilyLook = FamilyLook.Default,
    playKey: Any? = Unit,
    themeKey: Any? = null,
    themeChoice: String? = null,
    goalKey: Any? = themeKey,
) {
    val p = percent.coerceIn(0, 100)
    val colors = GgfTheme.colors
    // GPT 일러스트(결정 #29): 숲길 풍경 바탕, 새싹 이정표, 출발·목적지 핀(ILL-009 주행 배너에서 오림)
    val scenery = ImageBitmap.imageResource(GgfPicture.JourneyBg.res)
    val sprout = ImageBitmap.imageResource(GgfPicture.RoadSprout.res)
    val pinStart = ImageBitmap.imageResource(GgfPicture.PinStart.res)
    // 목적지에는 핀 대신 숲 동물이 기다린다(2026-11-08 마스터 지시, 결정 #42: "저 토끼가 있는 데까지 가면 도착이야")
    // 작은 숲 앞의 토끼·다람쥐 한 장(GPT goal-grove, 다크 모드는 저녁 그림)
    // 도착지 그림: 4안 중 이 여행의 것(여행 ID로 고정, 결정 #44). 다크는 같은 번호의 저녁 그림(있으면)
    val goalPicture = GoalPictures.pick(goalKey, colors.isDark)
    val goalGrove = ImageBitmap.imageResource(goalPicture.res)
    val milestones = remember(milestonesEvery) { milestonePercents(milestonesEvery) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = progressRoadDescription(p)
            progressBarRangeInfo = ProgressBarRangeInfo(p / 100f, 0f..1f)
        },
    ) {
        val wPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else with(density) { 600.dp.toPx() }
        val hPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else wPx / 3f
        // 길이 그려진 풍경 그림(결정 #33): 자리 모양에 맞는 그림이 있으면 그 그림의 길을 따라간다. 없으면 앱이 길을 그린다
        val scenes = remember(colors.isDark, themeKey, themeChoice) {
            RoadScenes.usable(colors.isDark, theme = RoadScenes.themeFor(themeKey, choice = themeChoice))
        }
        // 고른 차(결정 #34)마다 그림 비율·바퀴 높이가 달라 길 위 자리를 다시 잰다
        val car = LocalFamilyCar.current
        val layout = remember(wPx, hPx, shape, scenes, car) { roadLayout(wPx, hPx, shape, scenes.map { it.spec }, car.aspect, car.groundRatio) }
        val sceneArt = layout.scene?.let { spec -> scenes.firstOrNull { it.spec === spec }?.picture(colors.isDark) }
        val scenePicture = sceneArt?.let { ImageBitmap.imageResource(it) }
        val (carLeft, carTop) = layout.carTopLeft(p)
        val facingRight = layout.geometry.pointAt(p / 100f).facingRight
        Box(Modifier.fillMaxWidth().height(with(density) { hPx.toDp() }).clipToBounds()) {
            Canvas(Modifier.fillMaxWidth().height(with(density) { hPx.toDp() })) {
                val spec = layout.scene
                if (spec != null && scenePicture != null) {
                    // 풍경 길 그림: 길 선을 딴 것과 같은 계산으로 비율 지켜 채운다(길이 잘리면 그림 전체가 보이게 맞춤)
                    val c = spec.place(size.width, size.height)
                    drawImage(
                        scenePicture,
                        dstOffset = IntOffset(c[0].roundToInt(), c[1].roundToInt()),
                        dstSize = IntSize((spec.pictureWidth * c[2]).roundToInt(), (spec.pictureHeight * c[2]).roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                } else if (!colors.isDark) {
                    // 바탕: GPT 숲길 풍경(흐린 숲). 비율을 지키며 가득 채우고 남는 쪽은 자른다.
                    // 다크 모드에서는 그리지 않는다: 밤 주행 중 밝은 큰 면이 눈부시고, 그림 색을 필터로 어둡게 바꾸지 않기 위해
                    val bg = IslandMap.cover(size, scenery)
                    drawImage(
                        scenery,
                        dstOffset = IntOffset(bg.left.roundToInt(), bg.top.roundToInt()),
                        dstSize = IntSize(bg.width.roundToInt(), bg.height.roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                }
                drawRoad(layout, p / 100f, colors, painted = spec != null && scenePicture != null)
                // 10%마다 새싹 이정표: 지난 것은 또렷하게, 남은 것은 옅게(색은 바꾸지 않고 투명도만).
                // 길 아래 길가에 심는다: 차는 길 위에 서므로 이정표를 가리지 않는다(2026-11-03 Mac 검증)
                val sproutH = layout.flagHeight * 0.56f
                val sproutW = sproutH * GgfPicture.RoadSprout.aspect
                for (m in milestones) {
                    val pt = layout.geometry.pointAt(m / 100f)
                    val (sx, top) = if (layout.scene != null) {
                        // 그림 길: 길 옆(아래쪽, 굽이에서는 바깥쪽)으로 비켜 심는다. 굽이에서 길 위에 겹치지 않게
                        val c = sproutSpot(layout, m / 100f, sproutH)
                        (c.first - sproutW / 2f) to (c.second - sproutH / 2f).coerceIn(1f, size.height - sproutH - 1f)
                    } else {
                        (pt.x - sproutW / 2f) to min(pt.y + layout.roadWidth / 2f + 2f, size.height - sproutH - 1f)
                    }
                    drawImage(
                        sprout,
                        dstOffset = IntOffset(sx.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(sproutW.roundToInt(), sproutH.roundToInt()),
                        alpha = if (p >= m) 1f else 0.42f,
                        filterQuality = FilterQuality.High,
                    )
                }
            }
            FamilyCharacter(
                pose = drivePoseFor(p),
                animate = animate,
                look = look,
                playKey = playKey,
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(carLeft.roundToInt(), carTop.roundToInt()) }
                    .size(with(density) { layout.carWidth.toDp() }, with(density) { layout.carHeight.toDp() })
                    .graphicsLayer { scaleX = if (facingRight) 1f else -1f },
            )
            // 출발·목적지 핀은 차 위 층에 그린다(0%·100% 근처에서 차가 핀을 가리지 않게, 2026-11-05 Mac 검증).
            // 핀이 차와 겹치면 차 지붕 위로 올려 가족 얼굴을 가리지 않게 하고("여기"를 가리키는 모양), 자리 밖으로는 나가지 않게 한다.
            val pins = remember(layout, p, goalPicture) { pinRects(layout, p, goalPicture) }
            Canvas(Modifier.fillMaxWidth().height(with(density) { hPx.toDp() })) {
                // 작은 숲에서 기다리는 동물(정지 그림, 주행·주차 모두 움직이지 않음)
                drawPicture(goalGrove, pins.goalAt, pins.goalSize, PictureMotion.Still)
                drawPicture(pinStart, pins.startAt, pins.startSize, PictureMotion.Still)
            }
        }
    }
}

/**
 * 길 그림의 화면 읽기 설명: "목적지까지 40퍼센트 왔어요. 아이가 닫힌 창으로 바깥을 보고 있어요".
 * 포즈 문장은 [FamilyPose.description]을 그대로 쓴다([drivePoseFor]와 같은 경계, 25/50/75%).
 */
fun progressRoadDescription(percent: Int): String {
    val p = percent.coerceIn(0, 100)
    return "목적지까지 ${p}퍼센트 왔어요. ${drivePoseFor(p).description}"
}

/**
 * 진행 길 끝 도착지 그림 4안(결정 #44, 2026-11-08 마스터 "4개 다 랜덤으로"): 숲 입구 아치·숲속 집·소풍 쉼터·꽃 환영 정원.
 * 여행 ID([pick]의 key)로 하나를 골라 그 여행 동안 고정한다(재구성·진행률·주차/주행·다크 전환으로 다시 뽑지 않음). 다음 여행은 새로.
 * 다크 모드는 같은 번호의 저녁 그림, 아직 없으면 낮 그림.
 */
internal object GoalPictures {
    val day: List<GgfPicture> = listOf(GgfPicture.GoalGate, GgfPicture.GoalHome, GgfPicture.GoalPicnic, GgfPicture.GoalGarden)

    /** 같은 번호의 저녁 그림(GPT goal-variants 11-08) */
    private val evening: List<GgfPicture?> = listOf(
        GgfPicture.GoalGateEvening, GgfPicture.GoalHomeEvening, GgfPicture.GoalPicnicEvening, GgfPicture.GoalGardenEvening,
    )

    fun indexFor(key: Any?): Int = if (key == null) 0 else Math.floorMod(key.hashCode(), day.size)

    fun pick(key: Any?, dark: Boolean): GgfPicture {
        val i = indexFor(key)
        return if (dark) evening[i] ?: day[i] else day[i]
    }
}

/** 목적지 그림의 키: 길 끝 집 자리 높이에 대한 비율 */
private const val GOAL_GROVE_HEIGHT = 1.55f

/** 출발 핀·목적지(기다리는 동물) 자리의 왼쪽 위와 크기(픽셀). 그리기와 단위 테스트가 같은 계산을 쓴다 */
internal data class PinRects(val startAt: Offset, val startSize: Size, val goalAt: Offset, val goalSize: Size)

/**
 * 진행률 [percent]%일 때 핀 자리. 목적지 핀은 집 앞, 출발 핀은 길 첫머리 위가 제자리이고,
 * 차와 겹치거나 자리 밖으로 나가면 [pinSpot]이 옮긴다.
 */
internal fun pinRects(layout: RoadLayout, percent: Int, goalPicture: GgfPicture = GgfPicture.GoalGrove): PinRects {
    val (carLeft, carTop) = layout.carTopLeft(percent)
    val area = Size(layout.width, layout.height)
    val goalH = layout.houseHeight * GOAL_GROVE_HEIGHT
    val goal = Size(goalH * goalPicture.aspect, goalH)
    val goalAt = pinSpot(
        Offset(layout.houseLeft + (layout.houseWidth - goal.width) / 2f, layout.houseTop + layout.houseHeight - goalH),
        goal, carLeft, carTop, layout, area,
    )
    val begin = layout.geometry.pointAt(0f)
    val startH = layout.houseHeight * 0.9f * 0.62f // 출발 핀은 예전 크기 그대로(목적지 숲만 키움)
    val start = Size(startH * GgfPicture.PinStart.aspect, startH)
    val startAt = pinSpot(
        Offset(layout.roadStartX - start.width / 2f, begin.y - layout.roadWidth / 2f - startH),
        start, carLeft, carTop, layout, area,
    )
    return PinRects(startAt, start, goalAt, goal)
}

/**
 * 핀을 그릴 왼쪽 위 자리(2026-11-05 Mac 검증: 핀이 차 위 층으로 올라오며 0%·100%에서 가족 얼굴을 가림).
 * 차 그림(왼쪽 위 [carLeft],[carTop], 크기는 [layout])과 겹치면 핀 아래 끝이 차 지붕에 살짝 닿게 올린다("여기"를 가리키는 모양).
 * 위로 올릴 자리가 없으면(차가 자리 위 끝 근처) 차 옆으로 비킨다. 마지막으로 자리([area]) 밖으로 나가지 않게 안쪽으로 붙인다
 * (넓은 창의 경로 없는 화면에서 출발 핀 윗부분이 잘리던 문제).
 */
internal fun pinSpot(at: Offset, pin: Size, carLeft: Float, carTop: Float, layout: RoadLayout, area: Size): Offset {
    val maxX = max(0f, area.width - pin.width)
    val maxY = max(0f, area.height - pin.height)
    var x = at.x.coerceIn(0f, maxX)
    var y = at.y.coerceIn(0f, maxY)
    if (overlapsCar(x, y, pin, carLeft, carTop, layout)) {
        val lifted = carTop - pin.height + layout.carHeight * PIN_ROOF_TOUCH - 1f // 1px: 소수 반올림으로 경계에 걸리지 않게
        if (lifted >= 0f) {
            y = lifted
        } else {
            // 위로 못 올리면 차 옆으로: 핀 가운데가 차 가운데보다 왼쪽이면 왼쪽, 아니면 오른쪽(자리가 없으면 반대쪽)
            val left = carLeft - pin.width - 1f
            val right = carLeft + layout.carWidth + 1f
            val goLeft = x + pin.width / 2f < carLeft + layout.carWidth / 2f
            x = when {
                goLeft && left >= 0f -> left
                !goLeft && right <= maxX -> right
                left >= 0f -> left
                else -> right
            }
        }
        x = x.coerceIn(0f, maxX)
        y = y.coerceIn(0f, maxY)
    }
    return Offset(x, y)
}

/** 핀 아래 끝이 차 지붕에 닿는 깊이(차 높이의 비율). 차 그림 윗부분은 여백이라 조금 겹쳐야 붙어 보인다 */
internal const val PIN_ROOF_TOUCH = 0.08f

internal fun overlapsCar(x: Float, y: Float, pin: Size, carLeft: Float, carTop: Float, layout: RoadLayout): Boolean =
    x < carLeft + layout.carWidth && x + pin.width > carLeft && y < carTop + layout.carHeight * (1f - PIN_ROOF_TOUCH) && y + pin.height > carTop + layout.carHeight * PIN_ROOF_TOUCH

/**
 * 그림 길에서 새싹 이정표의 가운데 자리: 길 위 점에서 길에 수직으로 (길 폭 절반 + 새싹 절반)만큼 비킨 곳.
 * 비키는 쪽은 아래(화면 아래가 +y), 길이 거의 위아래로 가는 굽이에서는 자리 가운데에서 먼 쪽(굽이 바깥).
 */
internal fun sproutSpot(layout: RoadLayout, fraction: Float, sproutH: Float): Pair<Float, Float> {
    val g = layout.geometry
    val a = g.pointAt((fraction - 0.004f).coerceIn(0f, 1f))
    val b = g.pointAt((fraction + 0.004f).coerceIn(0f, 1f))
    val p = g.pointAt(fraction)
    var tx = b.x - a.x
    var ty = b.y - a.y
    val len = kotlin.math.hypot(tx, ty).takeIf { it > 0f } ?: 1f
    tx /= len
    ty /= len
    var nx = -ty
    var ny = tx
    if (kotlin.math.abs(ny) >= 0.35f) {
        if (ny < 0f) { nx = -nx; ny = -ny }
    } else if ((p.x - layout.width / 2f) * nx < 0f) {
        nx = -nx; ny = -ny
    }
    val d = layout.roadWidth / 2f + sproutH * 0.55f
    return (p.x + nx * d) to (p.y + ny * d)
}

private fun pathOf(points: FloatArray, start: Pair<Float, Float>? = null, end: Pair<Float, Float>? = null): Path = Path().apply {
    var first = true
    fun add(x: Float, y: Float) {
        if (first) moveTo(x, y) else lineTo(x, y)
        first = false
    }
    start?.let { add(it.first, it.second) }
    var i = 0
    while (i + 1 < points.size) {
        add(points[i], points[i + 1])
        i += 2
    }
    end?.let { add(it.first, it.second) }
}

/** [painted]: 길이 그림에 이미 그려져 있으면 길 바닥은 그리지 않고 남은 길 점선과 지나온 길 자국만 얹는다 */
private fun DrawScope.drawRoad(layout: RoadLayout, fraction: Float, colors: GgfColors, painted: Boolean = false) {
    val g = layout.geometry
    val step = max(3f, layout.roadWidth * 0.4f)
    val begin = g.pointAt(0f)
    val finish = g.pointAt(1f)
    val start = layout.roadStartX to begin.y
    val end = layout.roadEndX to finish.y
    val full = pathOf(g.sample(0f, 1f, step), start, end)
    val rw = layout.roadWidth
    if (!painted) {
        drawPath(full, colors.roadEdge, style = Stroke(width = rw + max(3f, rw * 0.22f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(full, colors.road, style = Stroke(width = rw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // 남은 길: 점선
    val rest = pathOf(g.sample(fraction, 1f, step), end = end)
    drawPath(
        rest,
        colors.roadLine,
        style = Stroke(
            width = max(2f, rw * 0.14f),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(rw * 0.55f, rw * 0.75f)),
        ),
    )
    // 지나온 길: 초록 자국
    val passed = pathOf(g.sample(0f, fraction, step), start = start)
    drawPath(passed, colors.trail, style = Stroke(width = rw * 0.42f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // 출발점 표시(작은 동그라미)
    drawCircle(colors.trail, radius = rw * 0.42f, center = Offset(start.first, start.second))
}
