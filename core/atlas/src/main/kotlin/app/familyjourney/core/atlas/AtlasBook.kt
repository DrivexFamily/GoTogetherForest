package app.familyjourney.core.atlas

/**
 * 우리 가족 랜드마크 도감의 저장 상태(결정 #40).
 *
 * - [owned]: 얻은 랜드마크 ID → 처음 얻은 때. 다시 가도 수는 늘지 않는다.
 * - [pendingCelebrations]: 얻었지만 아직 축하를 보이지 않은 것(주행 중 도착 등). 주차하면 앞에서부터 하나씩 보인다.
 * - [selectedId]: 아이가 도감에서 "이곳을 목표로" 고른 장소(없으면 null). 도착 판정에서 먼저 고른다.
 * - [passed]: 지나온 랜드마크(500m 안을 지남, 결정 #42). 아직 받지 않았다. 주차 중 "받기"를 누르면 [owned]로 옮기고 축하 대기열에 넣는다.
 * 선택·소유·축하 대기·지나옴을 한 값으로 섞지 않는다(U03 인계 4장).
 */
data class AtlasBook(
    val owned: Map<String, Acquired> = emptyMap(),
    val pendingCelebrations: List<String> = emptyList(),
    val selectedId: String? = null,
    val passed: List<String> = emptyList(),
) {
    fun owns(id: String): Boolean = id in owned
    fun ownedIn(region: Region, catalog: List<Landmark>): Int = catalog.count { it.region == region && owns(it.id) }
}

/** 처음 얻은 기록. [tripId]는 그 여행(같은 여행에서 두 번 주지 않기 위해). */
data class Acquired(val atMs: Long, val tripId: String)

/** 도착 한 번의 결과. */
sealed interface ArrivalOutcome {
    /** 새로 얻음 → 저장하고 축하 대기열에 넣었다 */
    data class NewLandmark(val landmark: Landmark) : ArrivalOutcome

    /** 이미 가진 곳에 다시 옴(수는 그대로, "또 왔어요"만) */
    data class Revisit(val landmark: Landmark) : ArrivalOutcome

    /** 도감의 장소가 아님(반경 밖, 좌표 없음) */
    data object NotLandmark : ArrivalOutcome
}

/**
 * 도착 처리 규칙(순수 함수). 저장이 먼저, 화면은 나중(U03 인계 5장): 이 함수가 돌려준 새 [AtlasBook]을 저장한 뒤에만
 * 축하를 보인다. 같은 여행([tripId])의 도착이 두 번 와도 한 번만 처리한다.
 */
object AtlasRules {
    fun onArrival(
        book: AtlasBook,
        catalog: List<Landmark>,
        tripId: String,
        destination: GeoPoint?,
        arrivedAt: GeoPoint?,
        nowMs: Long,
    ): Pair<AtlasBook, ArrivalOutcome> {
        if (book.owned.values.any { it.tripId == tripId }) {
            // 같은 여행에서 이미 얻음: 아무것도 바꾸지 않는다
            return book to ArrivalOutcome.NotLandmark
        }
        val lm = LandmarkMatcher.match(catalog, destination, arrivedAt, book.selectedId) ?: return book to ArrivalOutcome.NotLandmark
        if (book.owns(lm.id)) return book to ArrivalOutcome.Revisit(lm)
        val next = book.copy(
            owned = book.owned + (lm.id to Acquired(nowMs, tripId)),
            pendingCelebrations = book.pendingCelebrations + lm.id,
            selectedId = if (book.selectedId == lm.id) null else book.selectedId,
        )
        return next to ArrivalOutcome.NewLandmark(lm)
    }

    /** 축하를 다 보였다(닫음). 대기열에서만 뺀다. 소유는 그대로 */
    fun celebrated(book: AtlasBook, id: String): AtlasBook = book.copy(pendingCelebrations = book.pendingCelebrations - id)

    /** 도감에서 목표로 고르기/풀기(주차 중 화면에서만 부른다) */
    fun select(book: AtlasBook, id: String?): AtlasBook = book.copy(selectedId = id)

    /** 지나가며 얻는 거리(미터, 결정 #42 마스터 지시 "반경 500m 이내를 지나가면") */
    const val PASS_RADIUS_M: Int = 500

    /** 큰 산·섬(도착 반경이 이 이상)은 가운데 점에서 500m 안을 지나기 어려워 그 반경을 쓴다 */
    private const val WIDE_PLACE_RADIUS_M: Int = 5_000

    /** 이 랜드마크를 "지나갔다"로 보는 거리(미터) */
    fun passRadiusM(lm: Landmark): Int = if (lm.radiusM >= WIDE_PLACE_RADIUS_M) lm.radiusM else PASS_RADIUS_M

    /**
     * 차가 [at]에 있을 때(결정 #42): [passRadiusM] 안의 랜드마크 중 아직 얻지도 지나오지도 않은 곳을 [AtlasBook.passed] 뒤에 붙인다
     * (가까운 것부터). 위치는 판정에만 쓰고 저장하지 않는다. 바뀐 것이 없으면 [book] 그대로.
     */
    fun passBy(book: AtlasBook, catalog: List<Landmark>, at: GeoPoint): AtlasBook {
        val near = catalog.mapNotNull { lm ->
            val p = lm.point ?: return@mapNotNull null
            if (book.owns(lm.id) || lm.id in book.passed) return@mapNotNull null
            val d = LandmarkMatcher.distanceM(at, p)
            if (d <= passRadiusM(lm)) lm.id to d else null
        }
        if (near.isEmpty()) return book
        return book.copy(passed = book.passed + near.sortedBy { it.second }.map { it.first })
    }

    /** 데모: 정해 둔 [ids]를 지나온 것으로(좌표 없이). 이미 얻었거나 지나온 곳은 뺀다 */
    fun passIds(book: AtlasBook, ids: List<String>): AtlasBook {
        val add = ids.filter { !book.owns(it) && it !in book.passed }
        return if (add.isEmpty()) book else book.copy(passed = book.passed + add)
    }

    /** 지나가며 받은 곳의 여행 ID */
    const val PASS_TRIP_ID: String = "pass-by"

    /** "받기"(주차 중만, 결정 #42): 지나온 곳을 모두 얻고 축하 대기열에 넣는다. 목표로 고른 곳이면 목표를 푼다 */
    fun claimPassed(book: AtlasBook, nowMs: Long): AtlasBook {
        if (book.passed.isEmpty()) return book
        val add = book.passed.filter { !book.owns(it) }
        return book.copy(
            owned = book.owned + add.associateWith { Acquired(nowMs, PASS_TRIP_ID) },
            pendingCelebrations = book.pendingCelebrations + add,
            selectedId = book.selectedId?.takeIf { it !in add },
            passed = emptyList(),
        )
    }

    /** 데모 보여 주기로 한꺼번에 얻은 곳의 여행 ID(실제 여행과 섞이지 않게) */
    const val SHOWCASE_TRIP_ID: String = "demo-showcase"

    /**
     * 데모 보여 주기(2026-11-08 마스터 지시 "도착하면 50% 정도 랜드마크가 풀리게"): ID 순으로 하나 건너 하나
     * (500곳이면 250곳, 시·도마다 고르게)를 얻은 것으로 넣는다. 축하 대기열에는 넣지 않고(축하는 이번 도착 한 곳만),
     * 목표로 고른 곳은 빼서 다음 데모 도착에서도 축하를 볼 수 있게 한다. **발표용 도감에만** 쓴다([AtlasRepository.onDemoArrival]).
     */
    fun showcaseHalf(book: AtlasBook, catalog: List<Landmark>, nowMs: Long): AtlasBook {
        val add = catalog.sortedBy { it.id }
            .filterIndexed { i, _ -> i % 2 == 0 }
            .filter { !book.owns(it.id) && it.id != book.selectedId }
        if (add.isEmpty()) return book
        return book.copy(owned = book.owned + add.associate { it.id to Acquired(nowMs, SHOWCASE_TRIP_ID) })
    }
}
