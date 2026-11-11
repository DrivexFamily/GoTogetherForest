package app.familyjourney.feature.atlas

/**
 * 랜드마크 ID → 앱용 그림(drawable). 그림 변환 도구가 [LandmarkArtData]를 만든다. 없으면 0(화면이 대체 그림).
 */
object LandmarkArt {
    fun of(id: String): Int = landmarkArtData[id] ?: 0
}
