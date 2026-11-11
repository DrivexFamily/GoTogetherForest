package app.familyjourney.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 화면에 넣을 GPT 그림(R22 도착지·휴게소·서 있는 가족, R32 안내, R36 별 보상, R39 버튼 아이콘, R42 도착 축하 장식).
 *
 * 그림이 아직 없을 수 있다. 앱에 들어온 그림만 [screenPictureData]에 있고(`design/illustrations/tools/build_screen_pictures.py --apply`가 만든다),
 * 화면은 [ScreenPictures.get]이 null이면 지금 화면(예전 그림·글자만)을 그대로 보인다. 그림이 오면 스크립트를 돌리기만 하면 된다.
 *
 * @param gptName GPT 파일 이름의 가운데(`GGF-<gptName>-v<번호>.png`). 스크립트가 이 값으로 파일을 찾는다. 바꾸지 않는다
 * @param label 읽기 설명(장식 그림은 화면에서 null로 쓴다)
 */
enum class ScreenArt(val gptName: String, val label: String) {
    // R22 서 있는 가족(아빠·엄마·딸이 한 장에). 도착·홈의 서 있는 가족 대신
    StandWave("FAMILY-STAND-WAVE-R22", "가족이 손을 흔들어요"),
    StandCheer("FAMILY-STAND-CHEER-R22", "가족이 기뻐서 팔을 들어요"),
    StandWalk("FAMILY-STAND-WALK-R22", "가족이 손잡고 걸어요"),

    // R22 도착지 4곳(1536×1024, 아래 가운데 1/3은 빈 마당). 저녁 그림은 다크 모드
    ArrivalGrandma("ARRIVAL-GRANDMA-R22", "할머니 댁"),
    ArrivalGrandmaEvening("ARRIVAL-GRANDMA-EVENING-R22", "저녁 할머니 댁"),
    ArrivalSeaside("ARRIVAL-SEASIDE-R22", "바닷가 펜션"),
    ArrivalSeasideEvening("ARRIVAL-SEASIDE-EVENING-R22", "저녁 바닷가 펜션"),
    ArrivalCamping("ARRIVAL-CAMPING-R22", "숲속 캠핑장"),
    ArrivalCampingEvening("ARRIVAL-CAMPING-EVENING-R22", "저녁 숲속 캠핑장"),
    ArrivalHanok("ARRIVAL-HANOK-R22", "한옥 고향집"),
    ArrivalHanokEvening("ARRIVAL-HANOK-EVENING-R22", "저녁 한옥 고향집"),

    // R22 휴게소(1536×1024)
    RestArea("REST-AREA-R22", "휴게소"),
    RestAreaEvening("REST-AREA-EVENING-R22", "저녁 휴게소"),

    // R32 안내 그림(투명 1024×1024)
    GuideNoRoute("GUIDE-NO-ROUTE-R32", "지도를 펼쳐 보는 아이"),
    GuideParkToDecorate("GUIDE-PARK-TO-DECORATE-R32", "주차된 차 옆 새싹 화분"),
    GuideOnboard1("GUIDE-ONBOARD-1-R32", "가족 차가 꼬부랑 길을 가요"),
    GuideOnboard2("GUIDE-ONBOARD-2-R32", "안전하게 달린 길에 새싹이 돋아요"),
    GuideOnboard3("GUIDE-ONBOARD-3-R32", "숲에 나무와 동물이 모여요"),
    GuideGuardian("GUIDE-GUARDIAN-R32", "엄마 아빠가 아이 옆에서 웃어요"),

    // R39 버튼 아이콘(글자 앞 작은 그림). GPT가 붙일 영문 이름이 정해지지 않아 스크립트가 비슷한 이름도 받는다
    IconForest("ICON-FOREST-R39", "숲"),
    IconTreeBook("ICON-TREE-BOOK-R39", "나무 도감"),
    IconAnimalBook("ICON-ANIMAL-BOOK-R39", "동물 도감"),
    IconMap("ICON-MAP-R39", "숲 맵"),
    IconCar("ICON-CAR-R39", "우리 차"),
    IconRoad("ICON-ROAD-R39", "길 풍경"),
    IconSoundOn("ICON-SOUND-ON-R39", "소리 켜기"),
    IconSoundOff("ICON-SOUND-OFF-R39", "소리 끄기"),
    IconBigger("ICON-BIGGER-R39", "크게"),
    IconShare("ICON-SHARE-R39", "공유"),
    IconHome("ICON-HOME-R39", "홈"),
    IconClose("ICON-CLOSE-R39", "닫기"),

    // R36 별 보상(주차 중)
    RewardStarPouch("REWARD-STAR-POUCH-R36", "별 주머니"),
    RewardChest("REWARD-CHEST-R36", "별 보물 상자"),
    RewardGift("REWARD-GIFT-R36", "선물 상자"),
    RewardUnlock("REWARD-UNLOCK-R36", "열리는 자물쇠"),
    RewardConfetti("REWARD-CONFETTI-R36", "축하 꽃가루"),
    RewardSquirrelStar("REWARD-SQUIRREL-STAR-R36", "별을 든 다람쥐"),

    // R42 도착 축하 장식(주차 중 도착 화면 위에 얹음, 가운데 비움)
    CelebrateTop("CELEBRATE-TOP-R42", "꽃가루 띠"),
    CelebrateBalloons("CELEBRATE-BALLOONS-R42", "풍선 다발"),
    CelebrateBottom("CELEBRATE-BOTTOM-R42", "꽃과 새싹 띠"),
}

/** 앱에 들어온 화면 그림 한 장. [widthPx]·[heightPx]는 WebP 실제 크기(늘리지 않게 할 때 쓴다) */
@Immutable
class ScreenPicture(val art: ScreenArt, @DrawableRes val res: Int, val widthPx: Int, val heightPx: Int) {
    val aspect: Float get() = widthPx.toFloat() / heightPx
}

/** 도착지 한 곳: 낮 그림 + (있으면) 저녁 그림 */
@Immutable
class ArrivalPlace(val day: ScreenPicture, val evening: ScreenPicture?) {
    /** 다크 모드면 저녁 그림만(없으면 null → 이 도착지는 다크 모드에서 쓰지 않음, 결정 #33-④와 같은 원칙) */
    fun picture(dark: Boolean): ScreenPicture? = if (dark) evening else day
}

object ScreenPictures {
    private val byArt: Map<ScreenArt, ScreenPicture> = screenPictureData.associateBy { it.art }

    /** 그림이 있으면 그 그림, 아직 없으면 null(화면은 예전 모습) */
    fun get(art: ScreenArt, pictures: List<ScreenPicture> = screenPictureData): ScreenPicture? =
        if (pictures === screenPictureData) byArt[art] else pictures.firstOrNull { it.art == art }

    /** 도착지 4곳의 순서(첫 곳 = 데모). 낮 그림이 있는 곳만 */
    private val ARRIVALS = listOf(
        ScreenArt.ArrivalGrandma to ScreenArt.ArrivalGrandmaEvening,
        ScreenArt.ArrivalSeaside to ScreenArt.ArrivalSeasideEvening,
        ScreenArt.ArrivalCamping to ScreenArt.ArrivalCampingEvening,
        ScreenArt.ArrivalHanok to ScreenArt.ArrivalHanokEvening,
    )

    fun arrivalPlaces(pictures: List<ScreenPicture> = screenPictureData): List<ArrivalPlace> =
        ARRIVALS.mapNotNull { (day, evening) -> get(day, pictures)?.let { ArrivalPlace(it, get(evening, pictures)) } }

    /**
     * 이번 여행의 도착지 그림. 여행마다 하나(열쇠 = 여행 ID, 같은 여행 안에서는 그대로), 열쇠가 null(데모)이면 첫 곳.
     * 다크 모드는 저녁 그림이 있는 곳 중에서 고른다. 쓸 그림이 없으면 null(지금 도착 화면 그대로).
     */
    fun arrivalFor(key: Any?, dark: Boolean, pictures: List<ScreenPicture> = screenPictureData): ScreenPicture? {
        val usable = arrivalPlaces(pictures).mapNotNull { it.picture(dark) }
        if (usable.isEmpty()) return null
        return if (key == null) usable.first() else usable[Math.floorMod(key.hashCode(), usable.size)]
    }

    /** 휴게소 그림. 다크 모드는 저녁 그림만. 없으면 null(지금 진행 화면 그대로) */
    fun restArea(dark: Boolean, pictures: List<ScreenPicture> = screenPictureData): ScreenPicture? =
        get(if (dark) ScreenArt.RestAreaEvening else ScreenArt.RestArea, pictures)

    /** 주차 포즈 → 서 있는 가족 한 장 그림(R22). 없으면 null(엄마·아이·아빠를 따로 그리는 지금 방식) */
    fun standingFamily(pose: FamilyPose, pictures: List<ScreenPicture> = screenPictureData): ScreenPicture? = when (pose) {
        FamilyPose.ParkWave -> get(ScreenArt.StandWave, pictures)
        FamilyPose.ParkCheer -> get(ScreenArt.StandCheer, pictures)
        FamilyPose.ParkRun -> get(ScreenArt.StandWalk, pictures)
        else -> null
    }
}

/**
 * 화면 그림 한 장. 비율을 지키고 원본보다 크게 늘리지 않는다(결정 #29-⑦). 장식이면 [contentDescription]=null.
 * 바탕 그림처럼 자리를 채워야 하면 [ScreenBackdrop]을 쓴다.
 */
@Composable
fun ScreenPictureImage(picture: ScreenPicture, contentDescription: String?, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Image(
        bitmap = ImageBitmap.imageResource(picture.res),
        contentDescription = contentDescription,
        modifier = modifier.widthIn(max = with(density) { picture.widthPx.toDp() }).aspectRatio(picture.aspect),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
    )
}

/** 자리를 가득 채우는 바탕 그림(도착지·휴게소). 비율을 지키며 채우고 남는 쪽을 자른다(풍경 길과 같은 방식) */
@Composable
fun ScreenBackdrop(picture: ScreenPicture, modifier: Modifier = Modifier) {
    Image(
        bitmap = ImageBitmap.imageResource(picture.res),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
    )
}

/**
 * 주차 중 서 있는 가족. 손 흔들기·기뻐 뛰기는 GPT P1 시트([ParkedFamilySprites]),
 * 그 밖에는 R22 한 장 그림이 있고 가족 모두를 보일 때는 그 그림을, 아니면 지금의 [FamilyCharacter].
 * 움직임은 [FamilyCharacter]와 같다([animate]일 때만, 정해진 횟수, 기준점은 발끝).
 */
@Composable
fun ParkedFamily(
    pose: FamilyPose,
    animate: Boolean,
    modifier: Modifier = Modifier,
    members: List<FamilyMember> = FamilyMember.entries,
    playKey: Any? = Unit,
    contentDescription: String? = pose.description,
) {
    // 손 흔들기·기뻐 뛰기는 GPT P1 움직임 시트(사람마다 한 장)로 그린다
    if (hasFamilySprites(pose) && members.isNotEmpty()) {
        ParkedFamilySprites(pose, animate, members, playKey, contentDescription, modifier)
        return
    }
    val group = if (members.size == FamilyMember.entries.size) ScreenPictures.standingFamily(pose) else null
    if (group == null) {
        FamilyCharacter(pose = pose, animate = animate, modifier = modifier, members = members, playKey = playKey, contentDescription = contentDescription)
        return
    }
    val phase = rememberParkedPhase(animate, motionSpecFor(pose), playKey)
    val bitmap = ImageBitmap.imageResource(group.res)
    val density = LocalDensity.current
    Canvas(
        modifier
            .widthIn(max = with(density) { group.widthPx.toDp() })
            .aspectRatio(group.aspect)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                role = Role.Image
            },
    ) {
        val p = if (animate) phase.value else null
        drawPicture(bitmap, Offset.Zero, size, if (p == null) PictureMotion.Still else standingMotion(pose, p))
    }
}

/**
 * 별 보상 그림(GPT R36) 한 장. 그림이 없으면 [fallback](예: 지금의 ★ 배지)을 그린다.
 * [animate](주차 중)일 때만 처음에 한 번 살짝 커졌다 돌아온다(0.5초, 반복 없음). 주행 중·움직임 끄기 설정이면 정지.
 */
@Composable
fun RewardPicture(
    art: ScreenArt,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    playKey: Any? = Unit,
    fallback: (@Composable () -> Unit)? = null,
) {
    val picture = ScreenPictures.get(art)
    if (picture == null) {
        fallback?.invoke()
        return
    }
    val progress = rememberParkedProgress(animate, REWARD_POP_MS, playKey)
    val bitmap = ImageBitmap.imageResource(picture.res)
    Canvas(
        modifier.aspectRatio(picture.aspect).semantics { role = Role.Image },
    ) {
        val t = progress.value
        // 0 → 0.4: 0.85배에서 1.08배로, 0.4 → 1: 1배로 돌아옴
        val scale = when {
            t == null -> 1f
            t < 0.4f -> 0.85f + (1.08f - 0.85f) * (t / 0.4f)
            else -> 1.08f - 0.08f * ((t - 0.4f) / 0.6f)
        }
        val w = size.width * scale
        val h = size.height * scale
        drawPicture(bitmap, Offset((size.width - w) / 2f, size.height - h), androidx.compose.ui.geometry.Size(w, h), PictureMotion.Still)
    }
}

private const val REWARD_POP_MS = 500

/**
 * 버튼 글자 앞 작은 그림(GPT R39, 글자를 못 읽는 아이도 알아보게). 그림이 없으면 글자만(지금 그대로).
 * 그림은 장식이라 읽기 설명이 없다(글자가 이미 있다). 크기는 글자 크기를 따라 24~40dp.
 */
@Composable
fun IconLabel(
    text: String,
    icon: ScreenArt?,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    maxLines: Int = 1,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    val picture = icon?.let { ScreenPictures.get(it) }
    val label: @Composable () -> Unit = {
        androidx.compose.material3.Text(text, style = style, color = color, maxLines = maxLines, textAlign = textAlign)
    }
    if (picture == null) {
        label()
        return
    }
    val density = LocalDensity.current
    val side = with(density) { (style.fontSize * 1.4f).toDp() }.coerceIn(24.dp, 40.dp)
    androidx.compose.foundation.layout.Row(
        modifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Image(
            bitmap = ImageBitmap.imageResource(picture.res),
            contentDescription = null,
            modifier = Modifier.size(side),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
        )
        label()
    }
}
