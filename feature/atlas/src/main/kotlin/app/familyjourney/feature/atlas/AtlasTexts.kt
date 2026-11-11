package app.familyjourney.feature.atlas

/** 도감 화면 글자(한곳에 모음). 그림에는 글자를 넣지 않는다. */
object AtlasTexts {
    const val TITLE = "우리 가족 여행 도감"
    const val BACK = "뒤로"
    const val CLOSE = "닫기"
    const val OWNED = "얻었어요"
    const val NOT_YET = "아직 못 갔어요"
    const val SET_TARGET = "이곳을 목표로"
    const val CLEAR_TARGET = "목표 풀기"
    const val TARGET_BADGE = "목표"
    const val NOT_REACHABLE = "지나가며 얻기는 준비 중이에요"
    const val PARKED_ONLY = "목표 고르기는 주차 중에만 돼요"
    const val DEMO = "데모 도감 · 데모를 끄면 사라져요"
    // 결정 #42: 목적지 도착이 아니라 근처(500m)를 지나가면 얻는다(주차하면 "받기")
    const val HOW = "차로 이곳 근처를 지나가면 얻어요"
    const val SEE_ATLAS = "도감 보기"
    const val CELEBRATION_CLOSE = "좋아요!"
    /** 묶음 축하 제목(결정 #43): "랜드마크 5곳을 획득했어요!" */
    fun groupTitle(n: Int) = "랜드마크 ${n}곳을 획득했어요!"
    fun count(owned: Int, total: Int) = "$owned / ${total}곳"
    fun target(name: String) = "목표: $name"
    fun regionCount(region: String, owned: Int, total: Int) = "$region 도감 $owned / ${total}곳"
}
