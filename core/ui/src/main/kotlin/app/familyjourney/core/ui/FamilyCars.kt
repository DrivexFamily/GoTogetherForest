package app.familyjourney.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** 차 브랜드(결정 #34). 이름 글자는 이 파일에만 둔다 */
enum class CarBrand(val label: String) {
    Hyundai("현대"),
    Kia("기아"),
}

/**
 * 고를 수 있는 현대·기아 차(결정 #34, GPT R21). 목록 순서가 고르기 화면의 순서다.
 *
 * 차 이름 글자는 이 목록 한 곳에만 둔다. 운영국이 차 이름 사용을 허락하지 않으면 [modelName]을
 * [kind] 같은 일반 이름으로 바꾸면 된다(그림 안에는 이름·로고·엠블럼이 없다).
 *
 * @param code GPT 그림 파일 이름(`GGF-CAR-<코드>-…`)과 저장 값에 쓰는 코드. 바꾸지 않는다
 * @param kind 차 모양을 일반 말로(고르기 카드의 작은 글)
 */
enum class CarModel(val code: String, val brand: CarBrand, val modelName: String, val kind: String) {
    Palisade("H-PALISADE", CarBrand.Hyundai, "팰리세이드", "큰 SUV"),
    SantaFe("H-SANTAFE", CarBrand.Hyundai, "싼타페", "SUV"),
    Ioniq5("H-IONIQ5", CarBrand.Hyundai, "아이오닉 5", "전기 SUV"),
    Ioniq9("H-IONIQ9", CarBrand.Hyundai, "아이오닉 9", "큰 전기 SUV"),
    Ioniq6("H-IONIQ6", CarBrand.Hyundai, "아이오닉 6", "전기 세단"),
    Tucson("H-TUCSON", CarBrand.Hyundai, "투싼", "SUV"),
    Casper("H-CASPER", CarBrand.Hyundai, "캐스퍼", "작은 SUV"),
    Staria("H-STARIA", CarBrand.Hyundai, "스타리아", "미니밴"),
    Avante("H-AVANTE", CarBrand.Hyundai, "아반떼", "세단"),
    Carnival("K-CARNIVAL", CarBrand.Kia, "카니발", "미니밴"),
    Sorento("K-SORENTO", CarBrand.Kia, "쏘렌토", "SUV"),
    Ev9("K-EV9", CarBrand.Kia, "EV9", "큰 전기 SUV"),
    Ev3("K-EV3", CarBrand.Kia, "EV3", "작은 전기 SUV"),
    Ev6("K-EV6", CarBrand.Kia, "EV6", "전기 크로스오버"),
    Sportage("K-SPORTAGE", CarBrand.Kia, "스포티지", "SUV"),
    Ray("K-RAY", CarBrand.Kia, "레이", "경차"),
    Niro("K-NIRO", CarBrand.Kia, "니로", "작은 SUV"),
    K5("K-K5", CarBrand.Kia, "K5", "세단"),
    ;

    /** "현대 아이오닉 5" */
    val displayName: String get() = "${brand.label} $modelName"

    companion object {
        fun of(code: String?): CarModel? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 가족 차 한 대의 그림(결정 #34): 빈 차 1장 + 차 안 가족 주행 포즈(출발·창밖 보기·손 흔들기·거의 다 왔어).
 * 포즈 그림이 아직 없으면 출발 그림을 쓴다(GPT R21은 빈 차·출발을 먼저, 나머지 포즈는 R21-C).
 * 같은 차의 그림은 모두 같은 상자로 잘라 크기가 같다([widthPx]×[heightPx]).
 *
 * @param model null이면 기본 가족 차(R3 크림색 SUV)
 * @param groundRatio 바퀴가 땅에 닿는 높이(그림 위 0 → 아래 1). 길 위에 차를 세울 때 쓴다
 */
@Immutable
class FamilyCar(
    val model: CarModel?,
    val widthPx: Int,
    val heightPx: Int,
    val groundRatio: Float,
    @DrawableRes val empty: Int,
    @DrawableRes val start: Int,
    @DrawableRes val lookOut: Int? = null,
    @DrawableRes val wave: Int? = null,
    @DrawableRes val almost: Int? = null,
) {
    /** 가로 ÷ 세로 */
    val aspect: Float get() = widthPx.toFloat() / heightPx

    /** 고르기 카드·읽기 설명에 쓰는 이름 */
    val label: String get() = model?.displayName ?: DEFAULT_LABEL

    /** 저장 값(기본 차는 null) */
    val code: String? get() = model?.code

    /** 주행 포즈 → 그림. 주차 포즈는 null(서 있는 가족을 그린다) */
    @DrawableRes
    fun picture(pose: FamilyPose): Int? = when (pose) {
        FamilyPose.DriveStart -> start
        FamilyPose.DriveLookOut -> lookOut ?: start
        FamilyPose.DriveWave -> wave ?: start
        FamilyPose.DriveAlmost -> almost ?: start
        else -> null
    }

    companion object {
        const val DEFAULT_LABEL: String = "우리 가족 차"

        /** 지금까지의 가족 차(GPT R3·R4). 네 포즈가 같은 상자(960×568)로 잘려 있다 */
        val Default: FamilyCar = FamilyCar(
            model = null,
            widthPx = GgfPicture.CarStart.widthPx,
            heightPx = GgfPicture.CarStart.heightPx,
            groundRatio = CAR_GROUND_RATIO,
            empty = GgfPicture.CarEmpty.res,
            start = GgfPicture.CarStart.res,
            lookOut = GgfPicture.CarLookOut.res,
            wave = GgfPicture.CarWave.res,
            almost = GgfPicture.CarAlmost.res,
        )
    }
}

/** 고를 수 있는 차 목록 */
object FamilyCars {
    /**
     * 그림이 있는 현대·기아 차([familyCarData], `design/illustrations/tools/build_family_cars.py`가 만든다).
     * 그림이 없는 차는 고를 수 없다. 비어 있으면 차 고르기 버튼이 보이지 않는다.
     */
    val available: List<FamilyCar> = familyCarData.sortedBy { it.model?.ordinal ?: -1 }

    /** 저장된 코드 → 차. 없거나 그림이 없어진 코드면 기본 가족 차 */
    fun of(code: String?, cars: List<FamilyCar> = available): FamilyCar =
        cars.firstOrNull { it.code != null && it.code == code } ?: FamilyCar.Default
}

/**
 * 지금 가족이 탄 차. 앱 맨 위(AppRoot)가 저장된 선택으로 정하고, 진행 길·차 안 가족 그림이 읽는다.
 * 미리보기·캡처에서는 기본 가족 차.
 */
val LocalFamilyCar = staticCompositionLocalOf { FamilyCar.Default }
