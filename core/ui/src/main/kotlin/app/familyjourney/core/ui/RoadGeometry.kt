package app.familyjourney.core.ui

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 길 모양. Auto는 넓은 자리면 곧은 길, 세로로 긴 자리면 구불구불한 길(위에서 아래로 내려옴). */
enum class RoadShape { Auto, Straight, Winding }

/** 길 위의 한 점과, 그 자리에서 차가 오른쪽을 보는지(왼쪽으로 가는 줄에서는 차를 뒤집는다). */
data class RoadPoint(val x: Float, val y: Float, val facingRight: Boolean)

internal sealed interface RoadSegment {
    val length: Float
    fun at(d: Float): RoadPoint
}

internal data class LineSegment(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : RoadSegment {
    override val length: Float = hypot(x1 - x0, y1 - y0)
    override fun at(d: Float): RoadPoint {
        val t = if (length == 0f) 0f else (d / length).coerceIn(0f, 1f)
        return RoadPoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, facingRight = x1 >= x0)
    }
}

/** 그림에서 딴 길의 한 토막(결정 #33). 차가 보는 쪽은 토막 둘레의 흐름으로 미리 정해 둔다(급한 굽이에서 차가 깜빡이며 뒤집히지 않게). */
internal data class PolySegment(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val facingRight: Boolean) : RoadSegment {
    override val length: Float = hypot(x1 - x0, y1 - y0)
    override fun at(d: Float): RoadPoint {
        val t = if (length == 0f) 0f else (d / length).coerceIn(0f, 1f)
        return RoadPoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, facingRight)
    }
}

/** 반원 돌기. 화면 좌표(아래가 +y)에서 각도는 도 단위. */
internal data class ArcSegment(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val startDeg: Float,
    val sweepDeg: Float,
    /** 오른쪽 끝에서 도는 반원이면 true(오른쪽으로 오다가 왼쪽으로 간다) */
    val turnAtRight: Boolean,
) : RoadSegment {
    override val length: Float = (kotlin.math.abs(sweepDeg) * PI / 180.0 * r).toFloat()
    override fun at(d: Float): RoadPoint {
        val t = if (length == 0f) 0f else (d / length).coerceIn(0f, 1f)
        val a = (startDeg + sweepDeg * t) * PI / 180.0
        val half = t < 0.5f
        return RoadPoint((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat(), facingRight = if (turnAtRight) half else !half)
    }
}

/**
 * 진행 길의 모양(순수 계산). 진행률 0..1을 길 위의 점으로 바꾼다.
 * 1%씩 움직일 때 같은 거리만큼 가도록 길이 기준으로 계산한다.
 */
class RoadGeometry internal constructor(internal val segments: List<RoadSegment>) {
    val length: Float = segments.sumOf { it.length.toDouble() }.toFloat()

    fun pointAt(fraction: Float): RoadPoint {
        var d = fraction.coerceIn(0f, 1f) * length
        for (seg in segments) {
            if (d <= seg.length) return seg.at(d)
            d -= seg.length
        }
        val last = segments.last()
        return last.at(last.length)
    }

    /** from..to 구간을 step 간격으로 찍은 점들 [x0, y0, x1, y1, ...] (양 끝 포함) */
    fun sample(from: Float, to: Float, step: Float): FloatArray {
        val a = from.coerceIn(0f, 1f) * length
        val b = to.coerceIn(0f, 1f) * length
        if (b <= a || length == 0f) {
            val p = pointAt(from)
            return floatArrayOf(p.x, p.y)
        }
        val n = max(1, ceil((b - a) / max(step, 0.5f)).toInt())
        val out = FloatArray((n + 1) * 2)
        for (i in 0..n) {
            val p = pointAt((a + (b - a) * i / n) / length)
            out[i * 2] = p.x
            out[i * 2 + 1] = p.y
        }
        return out
    }

    companion object {
        fun straight(x0: Float, x1: Float, y: Float): RoadGeometry = RoadGeometry(listOf(LineSegment(x0, y, max(x1, x0 + 1f), y)))

        /**
         * 점 목록 [x0, y0, x1, y1, ...]을 잇는 길(그림에 그린 꼬부랑 길, 결정 #33). 출발 → 도착 순서. 점 간격은 고르지 않아도 된다.
         * 차가 보는 쪽: 토막 앞뒤로 길을 따라 [FACING_REACH_RATIO](전체 길이 비율, 최소 24px)만큼 사이에서 옆으로 충분히 움직였으면 그 쪽,
         * 거의 위아래로만 가면(굽이의 가장 끝) 앞 토막을 따른다.
         */
        fun polyline(points: FloatArray): RoadGeometry {
            val n = points.size / 2
            require(n >= 2) { "길 점이 2개 이상 있어야 한다" }
            fun x(i: Int) = points[i * 2]
            fun y(i: Int) = points[i * 2 + 1]
            val cum = FloatArray(n)
            for (i in 1 until n) cum[i] = cum[i - 1] + hypot(x(i) - x(i - 1), y(i) - y(i - 1))
            val reach = max(24f, cum[n - 1] * FACING_REACH_RATIO)
            var facing = x(n - 1) >= x(0)
            val segs = ArrayList<RoadSegment>(n - 1)
            var a = 0
            var b = 1
            for (i in 0 until n - 1) {
                while (a < i && cum[a + 1] <= cum[i] - reach) a++
                if (b < i + 1) b = i + 1
                while (b < n - 1 && cum[b] < cum[i + 1] + reach) b++
                val dx = x(b) - x(a)
                val dy = y(b) - y(a)
                if (kotlin.math.abs(dx) >= 0.35f * kotlin.math.abs(dy) && dx != 0f) facing = dx > 0f
                segs += PolySegment(x(i), y(i), x(i + 1), y(i + 1), facing)
            }
            return RoadGeometry(segs)
        }

        /**
         * 구불구불한 길: 첫 줄은 왼쪽→오른쪽, 다음 줄은 오른쪽→왼쪽… 줄 사이는 반원으로 돈다.
         * @param lastRowInset 마지막 줄 끝을 이만큼 줄여 목적지 집 자리를 만든다
         */
        fun winding(left: Float, right: Float, firstY: Float, rowGap: Float, rows: Int, lastRowInset: Float = 0f): RoadGeometry {
            require(rows >= 1)
            val segs = mutableListOf<RoadSegment>()
            val r = rowGap / 2f
            for (i in 0 until rows) {
                val y = firstY + i * rowGap
                val goingRight = i % 2 == 0
                val xs = if (goingRight) left else right
                var xe = if (goingRight) right else left
                if (i == rows - 1) xe = if (goingRight) max(xs + 1f, xe - lastRowInset) else min(xs - 1f, xe + lastRowInset)
                segs += LineSegment(xs, y, xe, y)
                if (i < rows - 1) {
                    segs += if (goingRight) {
                        ArcSegment(cx = right, cy = y + r, r = r, startDeg = -90f, sweepDeg = 180f, turnAtRight = true)
                    } else {
                        ArcSegment(cx = left, cy = y + r, r = r, startDeg = 270f, sweepDeg = -180f, turnAtRight = false)
                    }
                }
            }
            return RoadGeometry(segs)
        }
    }
}

/** 그림 길에서 차가 보는 쪽을 정할 때 앞뒤로 보는 길이(전체 길 길이에 대한 비율) */
internal const val FACING_REACH_RATIO = 0.015f

/** 깃발 이정표 자리(%). every=10이면 10, 20, … 90. 100%는 목적지 집이라 깃발이 없다. */
fun milestonePercents(every: Int): List<Int> = if (every <= 0 || every >= 100) emptyList() else (every until 100 step every).toList()

/** 차 그림의 바퀴가 땅에 닿는 높이(그림 세로 206 중 197) */
internal const val CAR_GROUND_RATIO = 197f / 206f

/** 집 그림의 바닥 높이(세로 120 중 116) */
internal const val HOUSE_GROUND_RATIO = 116f / 120f

/** 진행 길을 그리는 데 필요한 크기와 자리(픽셀). 순수 계산이라 단위 테스트한다. */
internal data class RoadLayout(
    val width: Float,
    val height: Float,
    val geometry: RoadGeometry,
    val winding: Boolean,
    val roadWidth: Float,
    val carWidth: Float,
    val carHeight: Float,
    val flagHeight: Float,
    val houseWidth: Float,
    val houseHeight: Float,
    val houseLeft: Float,
    val houseTop: Float,
    /** 길 띠가 시작하는 점(차가 0%에 서 있을 자리보다 조금 앞) */
    val roadStartX: Float,
    /** 길 띠가 끝나는 점(집 앞) */
    val roadEndX: Float,
    /** 길이 그려진 풍경 그림을 쓰면 그 그림(결정 #33). null이면 앱이 길을 그린다 */
    val scene: RoadSceneSpec? = null,
    /** 차 그림에서 바퀴가 땅에 닿는 높이 비율(고른 차마다 다르다, 결정 #34) */
    val carGround: Float = CAR_GROUND_RATIO,
) {
    /**
     * 진행률 p%일 때 차 그림의 왼쪽 위 자리. 구불구불한 길의 반원 끝에서도 차가 자리 밖으로
     * 나가지 않게 안쪽으로 붙인다.
     */
    fun carTopLeft(percent: Int): Pair<Float, Float> {
        val pt = geometry.pointAt(percent.coerceIn(0, 100) / 100f)
        val x = (pt.x - carWidth / 2f).coerceIn(0f, max(0f, width - carWidth))
        val y = (pt.y + roadWidth * 0.15f - carHeight * carGround).coerceIn(0f, max(0f, height - carHeight))
        return x to y
    }
}

/**
 * 세로가 가로의 이 배 이상이면 Auto가 구불구불한 길을 고른다.
 * 반 화면(길 자리 약 550×430)부터 두 줄 S자 길이 되어 남는 높이를 쓰고 차도 커진다.
 */
internal const val WINDING_ASPECT = 0.75f

internal fun resolveRoadShape(width: Float, height: Float, shape: RoadShape): RoadShape = when (shape) {
    RoadShape.Auto -> if (height >= width * WINDING_ASPECT) RoadShape.Winding else RoadShape.Straight
    else -> shape
}

/**
 * @param carAspect 차 그림의 가로 ÷ 세로, [carGround] 바퀴가 땅에 닿는 높이 비율. 고른 차([FamilyCar])마다 다르다(결정 #34)
 */
internal fun roadLayout(
    width: Float,
    height: Float,
    shape: RoadShape,
    scenes: List<RoadSceneSpec> = emptyList(),
    carAspect: Float = CAR_ASPECT,
    carGround: Float = CAR_GROUND_RATIO,
): RoadLayout {
    val w = max(width, 40f)
    val h = max(height, 20f)
    val picked = if (shape == RoadShape.Auto) pickScene(w, h, scenes) else null
    val layout = when {
        picked != null -> sceneLayout(w, h, picked, carAspect)
        resolveRoadShape(w, h, shape) == RoadShape.Winding -> windingLayout(w, h, carAspect)
        else -> straightLayout(w, h, carAspect)
    }
    return layout.copy(carGround = carGround)
}

/**
 * 길이 그려진 풍경 그림 한 장(결정 #33): 그림 크기(px), 길 가운데 선(그림 px, 출발 → 도착), 길 폭(그림 px).
 * 점 목록은 `design/illustrations/tools/trace_road.py`가 그림에서 딴다.
 */
class RoadSceneSpec(val pictureWidth: Int, val pictureHeight: Int, val points: FloatArray, val roadWidth: Float) {
    val aspect: Float get() = pictureWidth.toFloat() / pictureHeight

    /** 길이 차지하는 범위(그림 px): [minX, minY, maxX, maxY] */
    private val bounds: FloatArray by lazy {
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        var i = 0
        while (i + 1 < points.size) {
            x0 = min(x0, points[i]); x1 = max(x1, points[i]); y0 = min(y0, points[i + 1]); y1 = max(y1, points[i + 1]); i += 2
        }
        floatArrayOf(x0, y0, x1, y1)
    }

    /**
     * 자리(w×h)를 비율 지켜 가득 채울 때 그림의 왼쪽 위와 배율: [left, top, scale].
     * 남는 쪽은 길이 차지하는 범위의 가운데를 기준으로 자른다(양옆·위아래 여백이 고르게, 가운데 선에서 길 폭 절반 × [ROAD_EDGE_KEEP]까지 보이게).
     * 길이 보이는 범위보다 크면 가운데 그대로(그 그림은 [pickScene]이 고르지 않는다). 검수 도구 build_road_scenes.py와 같은 계산.
     */
    fun cover(w: Float, h: Float): FloatArray {
        val k = max(w / pictureWidth, h / pictureHeight)
        val pad = roadWidth / 2f * ROAD_EDGE_KEEP + 2f // 검사 여유보다 2px 더(경계에 딱 붙어 반올림으로 갈리지 않게)
        // 잘라야 하면 길이 차지하는 범위의 가운데를 보이는 자리 가운데에 둔다(양옆 여백이 고르게, 2026-11-05 Mac 제안)
        fun axis(lo: Float, hi: Float, view: Float, full: Float): Float {
            val c = if (hi - lo <= view) (lo + hi) / 2f - view / 2f else (full - view) / 2f
            return min(max(c, 0f), max(0f, full - view))
        }
        val cx = axis(bounds[0] - pad, bounds[2] + pad, w / k, pictureWidth.toFloat())
        val cy = axis(bounds[1] - pad, bounds[3] + pad, h / k, pictureHeight.toFloat())
        return floatArrayOf(-cx * k, -cy * k, k)
    }

    /**
     * 실제로 그릴 자리: 가득 채워도 길이 다 보이면 [cover], 아니면 그림 전체가 보이게 비율 지켜 맞추고 가운데에 둔다
     * (아주 좁고 긴 창에서 굽이가 잘리는 대신, 위아래나 양옆에 바탕이 조금 남는다). [left, top, scale]
     */
    fun place(w: Float, h: Float): FloatArray {
        if (roadFits(w, h, this)) return cover(w, h)
        val k = min(w / pictureWidth, h / pictureHeight)
        return floatArrayOf((w - pictureWidth * k) / 2f, (h - pictureHeight * k) / 2f, k)
    }
}

/** 길 가운데 선이 자리 가장자리에서 (길 폭 절반 × 이 값)만큼은 안쪽이어야 한다. 굽이 바깥 가장자리가 몇 px 잘리는 것은 괜찮다 */
internal const val ROAD_EDGE_KEEP = 0.66f

/** 자리 비율과 그림 비율이 이 배 넘게 다르면 그림을 쓰지 않는다(너무 많이 잘림) */
internal const val SCENE_MAX_ASPECT_GAP = 1.6f

/**
 * 자리 비율에 가장 가까운 그림을 고른다. 채웠을 때 길(양옆 길 폭 절반 포함)이 자리 밖으로 잘리는 그림은 고르지 않는다.
 * 맞는 그림이 없으면 null(앱이 길을 그린다).
 */
internal fun pickScene(w: Float, h: Float, scenes: List<RoadSceneSpec>): RoadSceneSpec? {
    val near = scenes.filter { s -> max(s.aspect / (w / h), (w / h) / s.aspect) <= SCENE_MAX_ASPECT_GAP }
    fun gap(s: RoadSceneSpec) = kotlin.math.abs(kotlin.math.ln((s.aspect / (w / h)).toDouble()))
    // 가득 채워도 길이 다 보이는 그림이 먼저. 없으면 비율이 가장 가까운 그림을 통째로 보이게(RoadSceneSpec.place)
    return near.filter { roadFits(w, h, it) }.minByOrNull(::gap) ?: near.minByOrNull(::gap)
}

internal fun roadFits(w: Float, h: Float, s: RoadSceneSpec): Boolean {
    val (left, top, k) = s.cover(w, h).let { Triple(it[0], it[1], it[2]) }
    val margin = s.roadWidth * k / 2f * ROAD_EDGE_KEEP
    var i = 0
    while (i + 1 < s.points.size) {
        val x = left + s.points[i] * k
        val y = top + s.points[i + 1] * k
        if (x < margin || x > w - margin || y < margin || y > h - margin) return false
        i += 2
    }
    return true
}

/** 그림 길 위에 차·핀을 놓는 크기와 자리. 차는 길 폭의 약 4배(그림이 보이는 폭의 16~26%), 자리 높이의 40%를 넘지 않는다 */
internal fun sceneLayout(w: Float, h: Float, spec: RoadSceneSpec, carAspect: Float = CAR_ASPECT): RoadLayout {
    val (left, top, k) = spec.place(w, h).let { Triple(it[0], it[1], it[2]) }
    val shownW = min(w, spec.pictureWidth * k)
    val pts = FloatArray(spec.points.size) { i -> if (i % 2 == 0) left + spec.points[i] * k else top + spec.points[i] * k }
    val geometry = RoadGeometry.polyline(pts)
    val roadW = max(spec.roadWidth * k, 6f)
    var carW = (roadW * 4f).coerceIn(shownW * 0.16f, shownW * 0.26f)
    var carH = carW / carAspect
    if (carH > h * 0.4f) {
        carH = h * 0.4f
        carW = carH * carAspect
    }
    val houseH = carH * 0.9f
    val houseW = houseH * GgfArt.House.aspect
    val begin = geometry.pointAt(0f)
    val end = geometry.pointAt(1f)
    return RoadLayout(
        width = w,
        height = h,
        geometry = geometry,
        winding = spec.aspect < 1.2f,
        roadWidth = roadW,
        carWidth = carW,
        carHeight = carH,
        flagHeight = carH * 0.62f,
        houseWidth = houseW,
        houseHeight = houseH,
        houseLeft = end.x - houseW / 2f,
        houseTop = end.y + roadW * 0.25f - houseH * HOUSE_GROUND_RATIO,
        roadStartX = begin.x,
        roadEndX = end.x,
        scene = spec,
    )
}

private fun straightLayout(w: Float, h: Float, carAspect: Float): RoadLayout {
    // 가로 2.4 : 세로 1 까지 쓴다(반 화면에서도 차가 작아지지 않게). 남는 높이는 위아래로 나눈다.
    val used = min(h, w / 2.4f)
    val top = (h - used) / 2f
    var carH = used * 0.56f
    var carW = carH * carAspect
    // GPT 차 그림은 옆으로 길어서(가로:세로 1.69) 폭으로 묶는다. 이정표를 덜 가리게 22%까지(2026-11-03 Mac 검증)
    if (carW > w * 0.22f) {
        carW = w * 0.22f
        carH = carW / carAspect
    }
    val roadW = max(used * 0.12f, 6f)
    val houseH = carH * 0.9f
    val houseW = houseH * GgfArt.House.aspect
    val roadY = top + used * 0.80f
    val x0 = carW * 0.5f + 2f
    val x1 = max(x0 + 1f, w - houseW - carW * 0.30f)
    val houseLeft = w - houseW
    return RoadLayout(
        width = w,
        height = h,
        geometry = RoadGeometry.straight(x0, x1, roadY),
        winding = false,
        roadWidth = roadW,
        carWidth = carW,
        carHeight = carH,
        flagHeight = min(used * 0.30f, carH * 0.62f),
        houseWidth = houseW,
        houseHeight = houseH,
        houseLeft = houseLeft,
        houseTop = roadY + roadW * 0.25f - houseH * HOUSE_GROUND_RATIO,
        roadStartX = roadW / 2f + 1f,
        roadEndX = houseLeft + houseW * 0.45f,
    )
}

private fun windingLayout(w: Float, h: Float, carAspect: Float): RoadLayout {
    val rows = ceil(h / (w * 0.55f)).toInt().coerceIn(2, 6)
    val band = h / rows
    val r = band / 2f
    var carH = band * 0.55f
    var carW = carH * carAspect
    if (carW > w * 0.26f) {
        carW = w * 0.26f
        carH = carW / carAspect
    }
    val roadW = max(band * 0.085f, 6f)
    val flagH = min(band * 0.3f, carH * 0.62f)
    // 반원 바깥 끝의 깃발 천이 잘리지 않을 만큼 띄운다
    val edge = max(roadW / 2f, flagH * GgfArt.Flag.aspect * 0.6f) + 2f
    val left = r + edge
    val right = max(left + 1f, w - r - edge)
    val firstY = band * 0.74f
    val houseH = carH * 0.9f
    val houseW = houseH * GgfArt.House.aspect
    val lastGoesRight = (rows - 1) % 2 == 0
    val inset = if (lastGoesRight) {
        max(0f, right - (w - houseW - carW * 0.30f))
    } else {
        max(0f, (houseW + carW * 0.30f) - left)
    }
    val geometry = RoadGeometry.winding(left, right, firstY, band, rows, inset)
    val end = geometry.pointAt(1f)
    val houseLeft = if (lastGoesRight) end.x + carW * 0.30f else end.x - carW * 0.30f - houseW
    return RoadLayout(
        width = w,
        height = h,
        geometry = geometry,
        winding = true,
        roadWidth = roadW,
        carWidth = carW,
        carHeight = carH,
        flagHeight = flagH,
        houseWidth = houseW,
        houseHeight = houseH,
        houseLeft = houseLeft,
        houseTop = end.y + roadW * 0.25f - houseH * HOUSE_GROUND_RATIO,
        roadStartX = max(roadW / 2f + 1f, left - carW * 0.45f),
        roadEndX = houseLeft + houseW * 0.5f,
    )
}
