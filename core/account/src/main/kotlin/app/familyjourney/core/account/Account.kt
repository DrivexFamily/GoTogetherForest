package app.familyjourney.core.account

import app.familyjourney.core.trip.ForestRewards
import app.familyjourney.core.trip.MileageReason
import app.familyjourney.core.trip.PointReason

/**
 * 보호자 계정(decisions.md #7-2). 차 한 대에 하나다. 별 포인트(★)와 해금은 가족 공용이고, 숲은 아이 것이다(#31).
 *
 * 지금(11/15)은 차 안에만 저장한다([LocalAccountRepository]). 보호자 실명·연락처·로그인 정보는 받지 않는다.
 * PT 단계에 서버 저장(Remote)을 붙이면 [id]로 계정을 잇는다.
 *
 * @param id 기기 안에서 만든 랜덤 ID(개인정보 아님)
 * @param confirmedAtEpochMs 보호자 확인을 한 시각(벽시계)
 * @param wallet 별 포인트 지갑(보유 ★·누적 ★·이번 달 쌓은 ★)
 * @param trips 여행 기록. 오래된 것부터, 최대 [MAX_TRIP_RECORDS]개
 * @param lastRiderIds 지난 여행에 탄 아이들. 다음 "오늘 누가 타나요?"의 기본 선택
 * @param boughtTrees ★로 연 나무 종류(처음부터 열린 종류는 넣지 않는다)
 * @param boughtAnimals ★로 연 동물(예전 규칙으로 이미 온 동물도 여기로 옮긴다, [AccountCodec] 데이터 이전)
 */
data class GuardianAccount(
    val id: String,
    val confirmedAtEpochMs: Long,
    val wallet: StarWallet = StarWallet(),
    val children: List<ChildProfile> = emptyList(),
    val trips: List<TripRecord> = emptyList(),
    val lastRiderIds: List<String> = emptyList(),
    val boughtTrees: Set<TreeKind> = emptySet(),
    val boughtAnimals: Set<ForestAnimal> = emptySet(),
    /** 어른(보호자) 안전 운전 마일리지(decisions.md #36). 아이 ★([wallet])와 따로, ★를 써도 줄지 않는다 */
    val mileage: MileageWallet = MileageWallet(),
    /** ★로 산 테마 맵(가족 공용, 2026-11-07). M1~M4는 누적 ★로 열리므로 넣지 않는다 */
    val boughtMaps: Set<ForestMap> = emptySet(),
) {
    /** 보유 ★(쓰면 줄어든다) */
    val stars: Int get() = wallet.balance

    /** 누적 ★(지금까지 모은 총합, 줄지 않는다. 숲 맵 기준) */
    val lifetimeStars: Int get() = wallet.lifetime

    fun child(id: String): ChildProfile? = children.firstOrNull { it.id == id }

    fun trip(tripId: String): TripRecord? = trips.lastOrNull { it.tripId == tripId }

    /** 이 맵이 열려 있나: M1~M4는 누적 ★, 테마 맵은 산 것. 데모의 발표용 세션은 보지 않는다([StarView]가 본다) */
    fun isMapOpen(map: ForestMap): Boolean = map.isOpenAt(lifetimeStars) || map in boughtMaps

    /** 열려 있나(처음부터 열림 또는 ★로 엶). 데모의 발표용 해금은 보지 않는다([StarView]가 본다) */
    fun isUnlocked(item: Unlockable): Boolean = item.openAtStart || when (item) {
        is TreeKind -> item in boughtTrees
        is ForestAnimal -> item in boughtAnimals
    }

    companion object {
        /** 기록은 최근 200개만 둔다(저장 파일이 끝없이 커지지 않게). 같은 여행 중복 확인도 이 안에서 한다 */
        const val MAX_TRIP_RECORDS: Int = 200

        /** 한 계정의 아이 프로필 최대 수 */
        const val MAX_CHILDREN: Int = 6
    }
}

/**
 * 별 포인트(★) 지갑(decisions.md #7-3, #31-①). 부드럽게 달린 1km = 1★, 여행당 1번, 월 상한(예시 3,000★), 데모 0★.
 *
 * - [balance] 보유 ★: 해금에 쓰면 줄어든다.
 * - [lifetime] 누적 ★: 지금까지 모은 총합. 쓰지 않고 줄지 않는다(숲 맵 기준).
 * - 월 상한을 계산하려고 "어느 달에 얼마 쌓았나"를 함께 둔다. 달이 바뀌면 [earnedIn]이 0을 돌려준다.
 *   쓴 ★는 월 상한과 무관하다(상한은 쌓는 쪽만 센다).
 *
 * @param monthKey [earnedInMonth]가 어느 달 값인가("2026-11"). 아직 없으면 null
 */
data class StarWallet(
    val balance: Int = 0,
    val lifetime: Int = 0,
    val monthKey: String? = null,
    val earnedInMonth: Int = 0,
) {
    /** [month]("2026-11")에 이미 쌓은 ★ */
    fun earnedIn(month: String): Int = if (monthKey == month) earnedInMonth else 0

    /** 지금까지 쓴 ★ */
    val spent: Int get() = (lifetime - balance).coerceAtLeast(0)

    companion object {
        /** 월 상한(예시). 값은 core:trip [ForestRewards]의 것을 그대로 쓴다. 한 달 1,000km(≈1,000★)는 넉넉히 들어간다 */
        const val MONTHLY_CAP: Int = ForestRewards.MONTHLY_POINT_CAP_EXAMPLE
    }
}

/**
 * 어른 안전 운전 마일리지(점) 지갑(decisions.md #36). 보호자 것이다.
 * 부드럽게 달린 비율 90% 이상인 여행만 1km = 1점, 여행당 1번, 월 상한 3,000점, 데모·판정 못 한 거리 0점.
 * 5,000점부터 5,000원 제휴 교환(예시, 교환 기능은 아직 없음 → [balance]는 지금 [lifetime]과 같다).
 *
 * @param monthKey [earnedInMonth]가 어느 달 값인가("2026-11")
 */
data class MileageWallet(
    val balance: Int = 0,
    val lifetime: Int = 0,
    val monthKey: String? = null,
    val earnedInMonth: Int = 0,
) {
    fun earnedIn(month: String): Int = if (monthKey == month) earnedInMonth else 0

    companion object {
        const val MONTHLY_CAP: Int = ForestRewards.MILEAGE_MONTHLY_CAP
    }
}

/** 심은 나무 한 묶음: 같은 종류 [count]그루가 이어서 심겼다(저장을 짧게 하려고 묶는다). */
data class TreeRun(val kind: TreeKind, val count: Int)

/**
 * 아이 프로필. 실명 대신 **별명만** 받고, 별명은 Keystore AES-GCM으로 암호화해 저장한다(decisions.md #11).
 *
 * 숲의 크기는 누적 새싹 하나로 나타낸다(나무 = 새싹 10개). 그래서 나무 수와 새싹 수가 서로 어긋날 일이 없다.
 * 나무마다의 종류는 [planted]에 심은 순서대로 묶어 둔다.
 *
 * @param totalSprouts 지금까지 받은 새싹 전부
 * @param treeKind 다음에 심을 나무 종류(주차 중 숲 화면에서 고름). 열린 종류만 고를 수 있다
 * @param map 이 아이가 고른 숲 맵
 * @param planted 심은 나무의 종류(오래된 것부터). 합이 나무 수보다 적으면 모자란 만큼은 **종류 정보가 없던 예전 나무**로
 *   보고 맨 앞에 참나무(T01)를 채운다(데이터 이전, [plantedRuns])
 * @param spots 맵마다 고정된 나무·동물 칸(decisions.md #32). 아이가 옮겼거나 숲 화면에 처음 보였을 때 저장한다.
 *   없는 맵은 기본 자리(core:ui `ForestPlacement`)로 그린다
 */
data class ChildProfile(
    val id: String,
    val nickname: String,
    val totalSprouts: Int = 0,
    val createdAtEpochMs: Long = 0L,
    val treeKind: TreeKind = TreeKind.DEFAULT,
    val map: ForestMap = ForestMap.DEFAULT,
    val planted: List<TreeRun> = emptyList(),
    val spots: Map<ForestMap, ForestSpots> = emptyMap(),
    /** 이 아이가 산 꾸미기 소품(코드 → 개수, decisions.md #38). 놓은 것과 보관함에 있는 것을 합친 수 */
    val decor: Map<String, Int> = emptyMap(),
) {
    /** 소품 [code]를 모든 맵에 놓은 수([except] 맵은 빼고) */
    fun decorPlaced(code: String, except: ForestMap? = null): Int =
        spots.entries.filter { it.key != except }.sumOf { (_, s) -> s.decor.count { it.code == code } }

    /** 나무 수(새싹 10개 = 1그루) */
    val trees: Int get() = ForestRewards.treesOf(totalSprouts).trees

    /** 다음 나무를 향한 새싹(0..9) */
    val sproutsTowardNextTree: Int get() = ForestRewards.treesOf(totalSprouts).sprouts

    /** 나무 수에 맞춘 묶음(앞은 예전 나무 T01로 채우고, 넘치면 뒤를 자른다) */
    fun plantedRuns(): List<TreeRun> = TreeRuns.fit(planted, trees)

    /** 나무마다의 종류(심은 순서). 길이 = [trees] */
    fun plantedKinds(): List<TreeKind> = TreeRuns.expand(plantedRuns())

    /** 로그나 오류 메시지에 별명이 새지 않게 가린다(CLAUDE.md 보안: 로그에 아이 별명 금지). */
    override fun toString(): String =
        "ChildProfile(id=$id, nickname=***, totalSprouts=$totalSprouts, treeKind=$treeKind, map=$map, " +
            "planted=$planted, spots=${spots.mapValues { it.value.size }}, decor=$decor, createdAtEpochMs=$createdAtEpochMs)"
}

/**
 * 숲 그리드의 칸 하나(decisions.md #32). core:ui 그리드의 열·행과 같다(왼쪽 위가 0, 0).
 * 나무는 아래 칸, 동물은 아래 왼쪽 칸(기준 칸)을 적는다.
 */
data class ForestCell(val col: Int, val row: Int)

/**
 * 한 맵 위에서 고정된 자리. 칸이 맞는지(평지·겹침)는 그리는 쪽(core:ui `ForestPlacement`)이 다시 확인하고,
 * 안 맞는 자리(그림이 바뀐 경우 등)는 기본 자리로 돌린다. 저장소는 값만 지킨다.
 *
 * @param trees 심은 순서 번호(0부터) → 칸
 * @param animals 동물 → 칸
 * @param next 자라는 자리(다음 나무가 설 칸). 다른 나무를 옮겨도 이 자리는 그대로다
 */
data class ForestSpots(
    val trees: Map<Int, ForestCell> = emptyMap(),
    val animals: Map<ForestAnimal, ForestCell> = emptyMap(),
    val next: ForestCell? = null,
    /** 보관함에 넣은 나무(심은 순서 번호)와 동물(2026-11-05 팀원 요청). 맵에 서지 않는다 */
    val storedTrees: Set<Int> = emptySet(),
    val storedAnimals: Set<ForestAnimal> = emptySet(),
    /** 이 맵에 놓은 꾸미기 소품(놓은 순서, decisions.md #38). 보관함 소품 수는 산 수 − 모든 맵에 놓은 수 */
    val decor: List<PlacedDecor> = emptyList(),
) {
    val size: Int get() = trees.size + animals.size + storedTrees.size + storedAnimals.size + decor.size

    companion object {
        /** 한 맵에 저장하는 칸 수 상한(저장 파일이 끝없이 커지지 않게). 30×20칸 그리드에 다 차도 이보다 적다 */
        const val MAX_ENTRIES: Int = 700
    }
}

/** 맵에 놓은 소품 하나: 소품 코드([DecorCatalog])와 기준 칸(아래 왼쪽 칸) */
data class PlacedDecor(val code: String, val cell: ForestCell)

/** 나무 종류 묶음 계산(순수 함수). */
object TreeRuns {
    /** [runs]를 [trees]그루에 맞춘다: 모자라면 앞에 참나무(예전 나무), 넘치면 뒤(최근)를 자른다. 빈 묶음은 뺀다 */
    fun fit(runs: List<TreeRun>, trees: Int): List<TreeRun> {
        val want = trees.coerceAtLeast(0)
        val out = ArrayList<TreeRun>()
        val have = runs.sumOf { it.count.coerceAtLeast(0) }
        if (have < want) out += TreeRun(TreeKind.DEFAULT, want - have)
        var left = want - (want - have).coerceAtLeast(0)
        for (r in runs) {
            if (left <= 0) break
            val n = minOf(r.count, left)
            if (n > 0) out += TreeRun(r.kind, n)
            left -= n
        }
        return merge(out)
    }

    /** 뒤에 [kind]를 [count]그루 더한다 */
    fun append(runs: List<TreeRun>, kind: TreeKind, count: Int): List<TreeRun> =
        if (count <= 0) runs else merge(runs + TreeRun(kind, count))

    fun expand(runs: List<TreeRun>): List<TreeKind> = runs.flatMap { r -> List(r.count.coerceAtLeast(0)) { r.kind } }

    /** 이웃한 같은 종류를 하나로 합친다 */
    fun merge(runs: List<TreeRun>): List<TreeRun> {
        val out = ArrayList<TreeRun>(runs.size)
        for (r in runs) {
            if (r.count <= 0) continue
            val last = out.lastOrNull()
            if (last != null && last.kind == r.kind) out[out.size - 1] = TreeRun(r.kind, last.count + r.count) else out += r
        }
        return out
    }
}

/**
 * 여행 기록 한 줄. 결과 화면을 다시 그릴 수 있을 만큼만 담는다(위치·경로는 담지 않는다).
 *
 * @param date 도착한 날("2026-11-01", 기기 시간대)
 * @param creditedKm 새싹 기준 km("부드럽게 달린 360km만큼 숲이 자랐어요")
 * @param sprouts 이번에 아이마다 받은 새싹(여러 아이면 모두 같은 수)
 * @param multiplierTenths 차종 배수 × 10(전기차 15, 하이브리드 13, 내연기관 10)
 * @param points 이번 여행에 실제로 쌓인 별 포인트(★)
 * @param pointsBeforeRules 상한·규칙 적용 전 값(인정 km × 1★)
 * @param demo 데모 여행(0★). 새싹은 쌓인다(decisions.md #22-⑥)
 * @param smoothPercent "부드럽게 달린 구간 N%". 판정 못 했으면 null
 * @param riderIds 이번에 새싹을 받은 아이들
 */
data class TripRecord(
    val tripId: String,
    val arrivedAtEpochMs: Long,
    val date: String,
    val creditedKm: Int,
    val sprouts: Int,
    val multiplierTenths: Int,
    val points: Int,
    val pointsBeforeRules: Int,
    val pointReason: PointReason,
    val demo: Boolean,
    val smoothPercent: Int?,
    val riderIds: List<String>,
    /** 이번 여행에 쌓인 어른 마일리지(점, decisions.md #36). 예전 기록은 0 */
    val mileage: Int = 0,
    val mileageBeforeRules: Int = 0,
    val mileageReason: MileageReason = MileageReason.NOTHING,
)

/** 별명 규칙. 화면과 저장소가 같은 규칙을 쓴다. */
object NicknameRules {
    /** 별명 최대 글자 수 */
    const val MAX_LENGTH: Int = 10

    /** 앞뒤 공백을 지우고 줄바꿈을 없앤 별명. 비었거나 너무 길면 null */
    fun normalize(input: String): String? {
        val s = input.replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty() || s.codePointCount(0, s.length) > MAX_LENGTH) return null
        return s
    }
}
