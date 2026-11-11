package app.familyjourney.core.account

import app.familyjourney.core.trip.MileageReason
import app.familyjourney.core.trip.PointReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 계정 ↔ 저장용 JSON 한 덩어리. 별명만 [TextCipher]로 암호화한다(나머지는 숫자·랜덤 ID·날짜·도감 코드뿐).
 *
 * 형식 v2(2026-11-03, decisions.md #31, 예):
 * ```
 * {"v":2,"id":"…","confirmedAt":…,
 *  "wallet":{"balance":160,"lifetime":360,"month":"2026-11","earned":360},
 *  "unlocks":["T03"],
 *  "children":[{"id":"…","nick":"g1:…(암호문)","sprouts":54,"tree":"T03","map":"M1",
 *               "planted":[{"k":"T01","n":3},{"k":"T03","n":2}],
 *               "spots":{"M1":{"t":[[0,12,9],[1,15,9]],"a":[["A01",10,12]],"n":[14,10],"st":[3],"sa":["A02"],
 *                          "d":[["WOODEN-BENCH",12,14]]}},"decor":{"WOODEN-BENCH":2},"createdAt":…}],
 *  "lastRiders":["…"],
 *  "trips":[{"id":"…","at":…,"date":"2026-11-01","km":360,"sprouts":54,"mult":15,"points":360,
 *            "before":360,"reason":"AWARDED","demo":false,"smooth":90,"riders":["…"]}]}
 * ```
 *
 * **v1 → v2 데이터 이전(읽을 때):**
 * - 지갑: 누적 ★가 없으면 보유 ★와 같게 본다(v1에는 ★를 쓰는 기능이 없었다).
 * - 아이마다 있던 동물(나무 3/10/25그루 규칙으로 온 것)은 가족 해금으로 옮긴다(한 번 온 동물은 떠나지 않는다).
 * - 나무 종류가 없는 나무는 참나무(T01), 고른 종류·맵이 없으면 참나무·기본 섬([ChildProfile.plantedRuns]).
 * 다음에 저장할 때 v2로 쓴다. 저장 키("account_v1")는 바꾸지 않는다(바꾸면 예전 저장본을 못 읽는다).
 *
 * `mileage`(어른 마일리지 지갑, decisions.md #36, 2026-11-06)와 여행의 `miles`·`milesBefore`·`milesReason`도 v2에 더한 칸이다.
 * 없으면(예전 파일) 0점으로 읽는다. 예전 여행의 ★를 마일리지로 옮기지 않는다(규칙이 달라 다시 셀 수 없다).
 *
 * `spots`(숲 칸, decisions.md #32, 2026-11-04)는 v2에 더한 칸이다. 없으면 빈 값(모두 기본 자리)으로 읽는다.
 * 나무는 [번호, 열, 행], 동물은 [코드, 열, 행], 자라는 자리 n은 [열, 행]. 이상한 값(음수·모르는 코드)은 건너뛴다.
 *
 * 꾸미기 소품(decisions.md #38, 2026-11-06): 아이마다 가진 수 `decor`({코드: 개수}), 맵마다 놓은 자리 `d`([코드, 열, 행]).
 * 둘 다 비어 있으면 쓰지 않는다. 모르는 코드는 건너뛴다.
 *
 * 읽기는 너그럽다: 모르는 코드·이유 이름은 건너뛰거나 기본값으로 읽는다. 별명을 풀 수 없으면
 * (Keystore 키를 잃은 경우 등) 숲은 살리고 별명만 "아이 N"으로 바꾼다.
 *
 * 라이브러리: kotlinx-serialization-json(Apache-2.0)의 JsonElement API만 쓴다(컴파일러 플러그인 없음).
 */
internal object AccountCodec {
    const val VERSION: Int = 2

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(account: GuardianAccount, cipher: TextCipher): String = buildJsonObject {
        put("v", VERSION)
        put("id", account.id)
        put("confirmedAt", account.confirmedAtEpochMs)
        put("wallet", buildJsonObject {
            put("balance", account.wallet.balance)
            put("lifetime", account.wallet.lifetime)
            put("month", account.wallet.monthKey)
            put("earned", account.wallet.earnedInMonth)
        })
        put("mileage", buildJsonObject {
            put("balance", account.mileage.balance)
            put("lifetime", account.mileage.lifetime)
            put("month", account.mileage.monthKey)
            put("earned", account.mileage.earnedInMonth)
        })
        val unlocks = account.boughtTrees.sortedBy { it.ordinal }.map { it.code } + account.boughtAnimals.sortedBy { it.ordinal }.map { it.code }
        put("unlocks", strings(unlocks))
        // 산 테마 맵(2026-11-07): 비어 있으면 쓰지 않는다(예전 파일과 같은 모양)
        if (account.boughtMaps.isNotEmpty()) put("maps", strings(account.boughtMaps.sortedBy { it.ordinal }.map { it.code }))
        put("children", buildJsonArray {
            for (c in account.children) {
                add(buildJsonObject {
                    put("id", c.id)
                    put("nick", cipher.encrypt(c.nickname))
                    put("sprouts", c.totalSprouts)
                    put("tree", c.treeKind.code)
                    put("map", c.map.code)
                    put("planted", buildJsonArray {
                        for (r in c.plantedRuns()) add(buildJsonObject { put("k", r.kind.code); put("n", r.count) })
                    })
                    if (c.spots.isNotEmpty()) put("spots", encodeSpots(c.spots))
                    // 꾸미기 소품(decisions.md #38): 비어 있으면 쓰지 않는다(예전 파일과 같은 모양)
                    val decor = c.decor.filterValues { it > 0 }
                    if (decor.isNotEmpty()) put("decor", buildJsonObject { for ((code, n) in decor.entries.sortedBy { it.key }) put(code, n) })
                    put("createdAt", c.createdAtEpochMs)
                })
            }
        })
        put("lastRiders", strings(account.lastRiderIds))
        put("trips", buildJsonArray {
            for (t in account.trips) {
                add(buildJsonObject {
                    put("id", t.tripId)
                    put("at", t.arrivedAtEpochMs)
                    put("date", t.date)
                    put("km", t.creditedKm)
                    put("sprouts", t.sprouts)
                    put("mult", t.multiplierTenths)
                    put("points", t.points)
                    put("before", t.pointsBeforeRules)
                    put("reason", t.pointReason.name)
                    put("demo", t.demo)
                    put("smooth", t.smoothPercent)
                    put("riders", strings(t.riderIds))
                    put("miles", t.mileage)
                    put("milesBefore", t.mileageBeforeRules)
                    put("milesReason", t.mileageReason.name)
                })
            }
        })
    }.toString()

    fun decode(text: String, cipher: TextCipher): GuardianAccount {
        val o = json.parseToJsonElement(text).jsonObject
        val w = o.obj("wallet")
        // v1: 아이마다 온 동물(예전 규칙) → 가족 해금으로 옮긴다
        val legacyAnimals = LinkedHashSet<ForestAnimal>()
        val children = o.arr("children").mapIndexed { i, e ->
            val c = e.jsonObject
            c.arr("animals").mapNotNullTo(legacyAnimals) { a -> enumOrNull<ForestAnimal>(a.jsonPrimitive.contentOrNull) }
            ChildProfile(
                id = c.str("id") ?: "child-$i",
                nickname = c.str("nick")?.let { runCatching { cipher.decrypt(it) }.getOrNull() } ?: "아이 ${i + 1}",
                totalSprouts = c.int("sprouts")?.coerceAtLeast(0) ?: 0,
                createdAtEpochMs = c.long("createdAt") ?: 0L,
                treeKind = TreeKind.ofCode(c.str("tree")) ?: TreeKind.DEFAULT,
                map = ForestMap.ofCode(c.str("map")) ?: ForestMap.DEFAULT,
                planted = c.arr("planted").mapNotNull { r ->
                    val ro = r as? JsonObject ?: return@mapNotNull null
                    val kind = TreeKind.ofCode(ro.str("k")) ?: TreeKind.DEFAULT
                    ro.int("n")?.takeIf { it > 0 }?.let { TreeRun(kind, it) }
                }.let(TreeRuns::merge),
                spots = (c["spots"] as? JsonObject)?.let(::decodeSpots) ?: emptyMap(),
                decor = (c["decor"] as? JsonObject)?.let(::decodeDecor) ?: emptyMap(),
            )
        }
        val unlocks = o.arr("unlocks").mapNotNull { ForestCatalog.ofCode(it.jsonPrimitive.contentOrNull) }
        val trips = o.arr("trips").mapNotNull { e ->
            val t = e.jsonObject
            val id = t.str("id") ?: return@mapNotNull null
            TripRecord(
                tripId = id,
                arrivedAtEpochMs = t.long("at") ?: 0L,
                date = t.str("date") ?: "",
                creditedKm = t.int("km") ?: 0,
                sprouts = t.int("sprouts") ?: 0,
                multiplierTenths = t.int("mult") ?: 10,
                points = t.int("points") ?: 0,
                pointsBeforeRules = t.int("before") ?: 0,
                pointReason = enumOrNull<PointReason>(t.str("reason")) ?: PointReason.NOTHING,
                demo = t["demo"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                smoothPercent = t.int("smooth"),
                riderIds = t.arr("riders").mapNotNull { it.jsonPrimitive.contentOrNull },
                mileage = t.int("miles")?.coerceAtLeast(0) ?: 0,
                mileageBeforeRules = t.int("milesBefore")?.coerceAtLeast(0) ?: 0,
                mileageReason = enumOrNull<MileageReason>(t.str("milesReason")) ?: MileageReason.NOTHING,
            )
        }
        val balance = (w?.int("balance") ?: 0).coerceAtLeast(0)
        val m = o.obj("mileage")
        val milesBalance = (m?.int("balance") ?: 0).coerceAtLeast(0)
        return GuardianAccount(
            id = o.str("id") ?: error("계정 ID 없음"),
            confirmedAtEpochMs = o.long("confirmedAt") ?: 0L,
            wallet = StarWallet(
                balance = balance,
                // v1에는 누적이 없다: 쓴 적이 없으므로 보유와 같다. 누적은 보유보다 작을 수 없다
                lifetime = (w?.int("lifetime") ?: balance).coerceAtLeast(balance),
                monthKey = w?.str("month"),
                earnedInMonth = w?.int("earned") ?: 0,
            ),
            children = children,
            trips = trips,
            lastRiderIds = o.arr("lastRiders").mapNotNull { it.jsonPrimitive.contentOrNull },
            boughtTrees = unlocks.filterIsInstance<TreeKind>().filterNotTo(LinkedHashSet()) { it.openAtStart },
            mileage = MileageWallet(
                balance = milesBalance,
                lifetime = (m?.int("lifetime") ?: milesBalance).coerceAtLeast(milesBalance),
                monthKey = m?.str("month"),
                earnedInMonth = (m?.int("earned") ?: 0).coerceAtLeast(0),
            ),
            boughtAnimals = (unlocks.filterIsInstance<ForestAnimal>() + legacyAnimals).filterNotTo(LinkedHashSet()) { it.openAtStart },
            boughtMaps = o.arr("maps").mapNotNull { ForestMap.ofCode(it.jsonPrimitive.contentOrNull) }.filterTo(LinkedHashSet()) { it.bought },
        )
    }

    private fun encodeSpots(spots: Map<ForestMap, ForestSpots>): JsonObject = buildJsonObject {
        for ((map, s) in spots.entries.sortedBy { it.key.ordinal }) {
            put(map.code, buildJsonObject {
                put("t", buildJsonArray {
                    for ((i, cell) in s.trees.entries.sortedBy { it.key }) add(buildJsonArray { add(JsonPrimitive(i)); add(JsonPrimitive(cell.col)); add(JsonPrimitive(cell.row)) })
                })
                put("a", buildJsonArray {
                    for ((a, cell) in s.animals.entries.sortedBy { it.key.ordinal }) add(buildJsonArray { add(JsonPrimitive(a.code)); add(JsonPrimitive(cell.col)); add(JsonPrimitive(cell.row)) })
                })
                s.next?.let { n -> put("n", buildJsonArray { add(JsonPrimitive(n.col)); add(JsonPrimitive(n.row)) }) }
                // 보관함(2026-11-05): 비어 있으면 쓰지 않는다(예전 파일과 같은 모양)
                if (s.storedTrees.isNotEmpty()) put("st", buildJsonArray { s.storedTrees.sorted().forEach { add(JsonPrimitive(it)) } })
                if (s.storedAnimals.isNotEmpty()) put("sa", buildJsonArray { s.storedAnimals.sortedBy { it.ordinal }.forEach { add(JsonPrimitive(it.code)) } })
                // 놓은 꾸미기 소품(놓은 순서 그대로): 비어 있으면 쓰지 않는다
                if (s.decor.isNotEmpty()) put("d", buildJsonArray {
                    for (p in s.decor) add(buildJsonArray { add(JsonPrimitive(p.code)); add(JsonPrimitive(p.cell.col)); add(JsonPrimitive(p.cell.row)) })
                })
            })
        }
    }

    private fun decodeSpots(o: JsonObject): Map<ForestMap, ForestSpots> {
        val out = LinkedHashMap<ForestMap, ForestSpots>()
        for ((code, v) in o) {
            val map = ForestMap.ofCode(code) ?: continue
            val m = v as? JsonObject ?: continue
            val trees = LinkedHashMap<Int, ForestCell>()
            for (e in m.arr("t")) {
                val t = (e as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: continue
                if (t.size == 3 && t.all { it >= 0 }) trees[t[0]] = ForestCell(t[1], t[2])
            }
            val animals = LinkedHashMap<ForestAnimal, ForestCell>()
            for (e in m.arr("a")) {
                val arr = e as? JsonArray ?: continue
                if (arr.size != 3) continue
                val animal = ForestAnimal.ofCode((arr[0] as? JsonPrimitive)?.contentOrNull) ?: continue
                val col = (arr[1] as? JsonPrimitive)?.intOrNull ?: continue
                val row = (arr[2] as? JsonPrimitive)?.intOrNull ?: continue
                if (col >= 0 && row >= 0) animals[animal] = ForestCell(col, row)
            }
            val next = (m["n"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                ?.takeIf { it.size == 2 && it.all { v -> v >= 0 } }?.let { ForestCell(it[0], it[1]) }
            val storedTrees = m.arr("st").mapNotNull { (it as? JsonPrimitive)?.intOrNull }.filterTo(LinkedHashSet()) { it >= 0 }
            val storedAnimals = m.arr("sa").mapNotNullTo(LinkedHashSet()) { ForestAnimal.ofCode((it as? JsonPrimitive)?.contentOrNull) }
            val decor = m.arr("d").mapNotNull { e ->
                val arr = e as? JsonArray ?: return@mapNotNull null
                if (arr.size != 3) return@mapNotNull null
                val code = (arr[0] as? JsonPrimitive)?.contentOrNull?.takeIf { DecorCatalog.ofCode(it) != null } ?: return@mapNotNull null
                val col = (arr[1] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val row = (arr[2] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                if (col >= 0 && row >= 0) PlacedDecor(code, ForestCell(col, row)) else null
            }
            out[map] = ForestSpots(trees, animals, next, storedTrees, storedAnimals, decor)
        }
        return out
    }

    /** 아이가 가진 소품: 아는 코드만, 1..[DecorCatalog.MAX_OWNED]개 */
    private fun decodeDecor(o: JsonObject): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for ((code, v) in o) {
            if (DecorCatalog.ofCode(code) == null) continue
            val n = (v as? JsonPrimitive)?.intOrNull ?: continue
            if (n > 0) out[code] = n.coerceAtMost(DecorCatalog.MAX_OWNED)
        }
        return out
    }

    private fun strings(values: List<String>): JsonArray = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

    private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
    private fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull
    private fun JsonObject.long(key: String): Long? = prim(key)?.longOrNull
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray) ?: emptyList()

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }
}
