package app.familyjourney.feature.atlas.ui

import androidx.annotation.DrawableRes
import app.familyjourney.core.atlas.Region
import app.familyjourney.feature.atlas.R

/** U03 생성 원본을 보존하고 축소한 앱 전용 그림. 지도·좌표 데이터가 아닌 지역 표지다. */
internal object AtlasArtwork {
    @DrawableRes
    fun region(region: Region): Int = when (region) {
        Region.SEOUL -> R.drawable.ggf_atlas_region_seoul
        Region.BUSAN -> R.drawable.ggf_atlas_region_busan
        Region.DAEGU -> R.drawable.ggf_atlas_region_daegu
        Region.INCHEON -> R.drawable.ggf_atlas_region_incheon
        Region.GWANGJU -> R.drawable.ggf_atlas_region_gwangju
        Region.DAEJEON -> R.drawable.ggf_atlas_region_daejeon
        Region.ULSAN -> R.drawable.ggf_atlas_region_ulsan
        Region.SEJONG -> R.drawable.ggf_atlas_region_sejong
        Region.GYEONGGI -> R.drawable.ggf_atlas_region_gyeonggi
        Region.GANGWON -> R.drawable.ggf_atlas_region_gangwon
        Region.CHUNGBUK -> R.drawable.ggf_atlas_region_chungbuk
        Region.CHUNGNAM -> R.drawable.ggf_atlas_region_chungnam
        Region.JEONBUK -> R.drawable.ggf_atlas_region_jeonbuk
        Region.JEONNAM -> R.drawable.ggf_atlas_region_jeonnam
        Region.GYEONGBUK -> R.drawable.ggf_atlas_region_gyeongbuk
        Region.GYEONGNAM -> R.drawable.ggf_atlas_region_gyeongnam
        Region.JEJU -> R.drawable.ggf_atlas_region_jeju
    }

    @DrawableRes
    fun pageBackground(dark: Boolean): Int =
        if (dark) R.drawable.ggf_atlas_page_evening else R.drawable.ggf_atlas_page_day

    @DrawableRes
    fun stageBackground(dark: Boolean): Int =
        if (dark) R.drawable.ggf_acquire_stage_evening else R.drawable.ggf_acquire_stage_day

    @get:DrawableRes val complete: Int get() = R.drawable.ggf_atlas_region_complete
    @get:DrawableRes val flag: Int get() = R.drawable.ggf_acquire_flag
    @get:DrawableRes val mystery: Int get() = R.drawable.ggf_atlas_mystery
    @get:DrawableRes val empty: Int get() = R.drawable.ggf_atlas_empty
    @get:DrawableRes val bookOpen: Int get() = R.drawable.ggf_atlas_book_open
}
