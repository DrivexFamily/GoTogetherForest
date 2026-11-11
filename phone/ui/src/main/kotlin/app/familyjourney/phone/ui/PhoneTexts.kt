package app.familyjourney.phone.ui

/** 폰 앱 문구(한곳). 아이·가족이 읽는 말은 짧게, 빠른 도착을 재촉하지 않는다(CLAUDE.md 1-6). */
object PhoneTexts {
    /** 모든 화면 맨 위(#20) */
    const val DRIVER_WARNING = "운전 중인 분은 사용하지 마세요"

    // 연결
    const val CONNECT_TITLE = "가족 폰 연결"
    const val CONNECT_HELP = "차 화면(주차 중)에서 \"가족 폰 연결\"을 누르면 QR이 보여요. 이 폰으로 찍어 주세요."
    const val SCAN_QR = "QR 찍기"
    const val SCAN_PROMPT = "차 화면의 QR을 네모 안에 맞춰 주세요"
    const val CODE_LABEL = "또는 차 화면의 코드(예: K7QM-4RXP-2WDN)"
    const val NAME_LABEL = "이 폰 이름(예: 엄마 폰)"
    const val DEFAULT_NAME = "가족 폰"
    const val CONNECT = "연결 요청"
    const val QR_INVALID = "같이가숲 QR이나 코드가 아니에요"
    const val QR_EXPIRED = "QR이 끝났어요. 차 화면에서 새 QR을 띄워 주세요"
    const val QR_WRONG = "QR이 맞지 않아요. 차 화면의 QR을 다시 찍어 주세요"
    const val OFFLINE = "인터넷 연결을 확인해 주세요. 꾸민 것은 폰에 먼저 반영되고 연결되면 올라가요"
    const val UNLINKED = "이 폰의 연결이 끊겼어요. 차 화면에서 다시 연결해 주세요"
    const val FORBIDDEN = "이 폰은 숲 꾸미기 권한이 없어요(차 화면에서 보호자가 바꿀 수 있어요)"
    const val SERVER_PROBLEM = "잠시 뒤에 다시 해 볼게요"
    const val WAITING = "차 화면에서 [연결]을 눌러 주세요"
    const val WAITING_HELP = "보호자가 차 화면에서 이 폰이 할 수 있는 일을 고르고 연결을 눌러야 해요."

    // 탭
    const val TAB_CAR = "우리 차"
    const val TAB_FOREST = "아이 숲"
    const val TAB_SHOP = "상점"
    const val TAB_LINK = "연결"

    // 우리 차
    const val NO_TRIP = "지금은 여행 중이 아니에요"
    const val NO_TRIP_HELP = "아이 숲을 보거나 꾸며 볼까요?"
    const val GO_FOREST = "아이 숲 보러 가기"
    const val ARRIVED = "도착했어요!"
    const val STOPPED = "잠깐 쉬는 중이에요"
    const val NO_LOCATION = "대략 위치는 보호자가 켜고 이 폰에 허락했을 때만 보여요"

    fun updatedAgo(seconds: Long): String = when {
        seconds < 5 -> "방금 갱신"
        seconds < 60 -> "${seconds}초 전 갱신"
        seconds < 3600 -> "${seconds / 60}분 전 갱신"
        else -> "${seconds / 3600}시간 전 갱신"
    }

    fun locationLine(area: String): String = "대략 위치: $area (약 5km)"

    /** "약 1시간 20분 남았어요"(차 화면의 남은 시간과 같은 뜻, 빠른 도착을 재촉하지 않음) */
    fun remaining(minutes: Int?): String = when {
        minutes == null -> "목적지로 가고 있어요"
        minutes < 1 -> "거의 다 왔어요"
        minutes < 60 -> "약 ${minutes}분 남았어요"
        minutes % 60 == 0 -> "약 ${minutes / 60}시간 남았어요"
        else -> "약 ${minutes / 60}시간 ${minutes % 60}분 남았어요"
    }

    fun syncedAgo(seconds: Long): String = "숲 " + updatedAgo(seconds)
    const val NO_TRIP_PERMISSION = "이 폰은 우리 차 진행 보기 권한이 없어요"

    // 숲
    fun forestTitle(nickname: String): String = "${nickname}의 숲"
    fun treesLine(trees: Int, sprouts: Int): String = "나무 ${trees}그루 · 새싹 ${sprouts}개"
    const val FOREST_HINT = "나무·동물·소품을 끌어서 옮겨요. 차 화면에도 같은 숲이 보여요"
    fun starsLine(stars: Int): String = "★ ${thousands(stars)}"

    // 상점(카드·확인은 feature:forest DecorShop이 그림)
    const val SHOP_TITLE = "꾸미기 상점"
    fun bought(label: String): String = "$label 을(를) 보관함에 넣었어요. 아이 숲에서 꺼내 놓아 보세요"
    fun notEnough(missing: Int): String = "★ ${thousands(missing)}개 더 모으면 돼요"
    const val TOO_MANY = "더 살 수 없어요"
    const val CANCEL = "그만두기"

    // 연결 관리
    const val DEVICES_TITLE = "이 폰 연결"
    const val CAN_EDIT_FOREST = "아이 숲 보기·꾸미기"
    const val CAN_SEE_TRIP = "우리 차 진행 보기"
    const val CAN_SEE_LOCATION = "대략 위치 보기"
    const val UNLINK = "이 폰 연결 끊기"
    const val UNLINK_CONFIRM = "이 폰의 연결을 끊을까요? 다시 쓰려면 차 화면에서 다시 연결해요"
    const val UNLINK_YES = "끊기"
    const val MANAGE_ON_CAR = "다른 폰의 권한·끊기는 차 화면(주차 중)에서 보호자가 해요"
    const val MILEAGE_TITLE = "어른 안전 운전 마일리지"
    fun mileageLine(points: Int): String = "${thousands(points)}점"

    fun thousands(n: Int): String = "%,d".format(n)
}
