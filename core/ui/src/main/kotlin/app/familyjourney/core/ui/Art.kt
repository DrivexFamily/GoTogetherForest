package app.familyjourney.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.VectorConfig
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource

/**
 * 직접 그린 그림 목록. 원본 SVG는 `design/characters/svg/`, 생성기는 `design/characters/tools/`.
 *
 * 그림 안의 path 이름은 "색 역할"이다(예: skin, hairKid, carBody). [rememberArtPainter]가 이 이름으로
 * 라이트/다크 색과 꾸미기 색을 덮어쓴다. 그룹 이름(armL, legR, kidArm, wing 등)은 움직이는 관절이다.
 */
enum class GgfArt(@DrawableRes val res: Int, val label: String, val viewportWidth: Float, val viewportHeight: Float) {
    GuardianA(R.drawable.ggf_guardian_a, "보호자 A", 130f, 190f),
    GuardianB(R.drawable.ggf_guardian_b, "보호자 B", 130f, 190f),
    Child(R.drawable.ggf_child, "아이", 120f, 150f),
    CarStage0(R.drawable.ggf_car_stage0, "가족 차, 출발", 240f, 206f),
    CarStage25(R.drawable.ggf_car_stage25, "가족 차, 창밖 보기", 240f, 206f),
    CarStage50(R.drawable.ggf_car_stage50, "가족 차, 손 흔들기", 240f, 206f),
    CarStage75(R.drawable.ggf_car_stage75, "가족 차, 거의 다 왔어", 240f, 206f),
    Sprout(R.drawable.ggf_plant_sprout, "새싹", 60f, 64f),
    Sapling(R.drawable.ggf_plant_sapling, "어린 나무", 80f, 110f),
    Tree(R.drawable.ggf_plant_tree, "나무", 140f, 170f),
    Squirrel(R.drawable.ggf_animal_squirrel, "다람쥐", 100f, 100f),
    Rabbit(R.drawable.ggf_animal_rabbit, "토끼", 90f, 110f),
    Bird(R.drawable.ggf_animal_bird, "새", 80f, 70f),
    House(R.drawable.ggf_house, "집", 130f, 120f),
    Flag(R.drawable.ggf_flag, "깃발", 44f, 70f),
    IconCartoon(R.drawable.ggf_icon_cartoon, "만화", 48f, 48f),
    IconSong(R.drawable.ggf_icon_song, "노래", 48f, 48f),
    ;

    /** 가로 ÷ 세로 */
    val aspect: Float get() = viewportWidth / viewportHeight
}

/**
 * 꾸미기 색. null이면 테마 기본색을 쓴다. 지금은 색만 바꾸고, 머리 모양 바꾸기는 레이어를 늘려 PT 단계에서 한다.
 */
@Immutable
data class FamilyLook(
    val hairA: Color? = null,
    val hairB: Color? = null,
    val hairKid: Color? = null,
    val shirtA: Color? = null,
    val shirtB: Color? = null,
    val shirtKid: Color? = null,
    val carBody: Color? = null,
) {
    internal fun overrides(): Map<String, Color> = buildMap {
        hairA?.let { put("hairA", it) }
        hairB?.let { put("hairB", it) }
        hairKid?.let { put("hairKid", it) }
        shirtA?.let { put("shirtA", it) }
        shirtB?.let { put("shirtB", it) }
        shirtKid?.let { put("shirtKid", it) }
        carBody?.let { put("carBody", it) }
    }

    companion object {
        val Default = FamilyLook()
    }
}

/** 그룹(관절) 하나를 그림에 적힌 값에서 얼마나 더 움직일지. 회전은 도, 이동은 그림 좌표. */
@Immutable
data class GroupTransform(
    val rotation: Float = 0f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scale: Float = 1f,
)

private val NoTransforms: () -> Map<String, GroupTransform> = { emptyMap() }

/**
 * 그림을 테마 색으로 그리는 Painter.
 *
 * @param roleOverrides 역할 색을 직접 바꿀 때(예: 지난 깃발/남은 깃발)
 * @param transforms 관절 움직임. 람다 안에서 애니메이션 상태를 읽으면 그림만 다시 그려진다.
 *   주행 중에는 넘기지 않는다(빈 값 = 그림 그대로의 정지 장면).
 */
@Composable
fun rememberArtPainter(
    art: GgfArt,
    look: FamilyLook = FamilyLook.Default,
    roleOverrides: Map<String, Color> = emptyMap(),
    transforms: () -> Map<String, GroupTransform> = NoTransforms,
): VectorPainter {
    val vector = ImageVector.vectorResource(art.res)
    val colors = GgfTheme.colors
    val roleConfigs = remember(colors, look, roleOverrides) { roleConfigs(colors, look.overrides() + roleOverrides) }
    return rememberVectorPainter(
        defaultWidth = vector.defaultWidth,
        defaultHeight = vector.defaultHeight,
        viewportWidth = vector.viewportWidth,
        viewportHeight = vector.viewportHeight,
        name = vector.name,
        tintColor = Color.Unspecified,
        tintBlendMode = BlendMode.SrcIn,
        autoMirror = false,
    ) { _, _ ->
        val moves = transforms()
        val configs = if (moves.isEmpty()) roleConfigs else roleConfigs + moves.mapValues { GroupConfig(it.value) }
        RenderVectorGroup(group = vector.root, configs = configs)
    }
}

/** 그림 한 장. 장식 그림이면 contentDescription을 null로 둔다. */
@Composable
fun ArtImage(
    art: GgfArt,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    look: FamilyLook = FamilyLook.Default,
    roleOverrides: Map<String, Color> = emptyMap(),
) {
    Image(
        painter = rememberArtPainter(art, look, roleOverrides),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

private fun roleConfigs(colors: GgfColors, overrides: Map<String, Color>): Map<String, VectorConfig> {
    val line = colors.artColor("line")
    return ArtRoleTokens.light.keys.associateWith { role ->
        val own = overrides[role] ?: colors.artColor(role)
        val stroke = if (role in ArtRoleTokens.selfStroke) own else line
        RoleConfig(fill = own, stroke = stroke)
    }
}

/** path 색 덮어쓰기. 원래 채움/선이 없는 path에는 새로 칠하지 않는다. */
private class RoleConfig(private val fill: Color, private val stroke: Color) : VectorConfig {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T = when (property) {
        VectorProperty.Fill -> if (defaultValue != null) SolidColor(fill) as T else defaultValue
        VectorProperty.Stroke -> if (defaultValue != null) SolidColor(stroke) as T else defaultValue
        else -> defaultValue
    }
}

/** 그룹 움직임 더하기. */
private class GroupConfig(private val t: GroupTransform) : VectorConfig {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T = when (property) {
        VectorProperty.Rotation -> ((defaultValue as Float) + t.rotation) as T
        VectorProperty.TranslateX -> ((defaultValue as Float) + t.translateX) as T
        VectorProperty.TranslateY -> ((defaultValue as Float) + t.translateY) as T
        VectorProperty.ScaleX, VectorProperty.ScaleY -> ((defaultValue as Float) * t.scale) as T
        else -> defaultValue
    }
}
