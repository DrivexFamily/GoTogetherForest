package app.familyjourney.core.account

import app.familyjourney.core.trip.PointReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCodecTest {

    @Test
    fun `저장 형식을 거쳐도 계정이 그대로 돌아온다`() {
        val original = GuardianAccount(
            id = "g",
            confirmedAtEpochMs = 123L,
            wallet = StarWallet(balance = 1_030, lifetime = 1_230, monthKey = "2026-11", earnedInMonth = 360),
            children = listOf(
                ChildProfile(
                    "c1", "콩이", totalSprouts = 54, createdAtEpochMs = 5L, treeKind = TreeKind.APPLE, map = ForestMap.BASIC,
                    planted = listOf(TreeRun(TreeKind.OAK, 3), TreeRun(TreeKind.APPLE, 2)),
                    spots = mapOf(
                        ForestMap.BASIC to ForestSpots(
                            trees = mapOf(0 to ForestCell(12, 9), 4 to ForestCell(15, 9)),
                            animals = mapOf(ForestAnimal.SQUIRREL to ForestCell(10, 12), ForestAnimal.FOX to ForestCell(8, 13)),
                            next = ForestCell(14, 10),
                        ),
                    ),
                ),
                ChildProfile("c2", "작은 별", totalSprouts = 0, createdAtEpochMs = 6L),
            ),
            trips = listOf(
                TripRecord("t1", 9L, "2026-11-01", 360, 54, 15, 360, 360, PointReason.AWARDED, false, 90, listOf("c1")),
                TripRecord("t2", 10L, "2026-11-01", 100, 15, 15, 0, 0, PointReason.NOT_JUDGED, true, null, emptyList()),
            ),
            lastRiderIds = listOf("c1"),
            boughtTrees = setOf(TreeKind.APPLE),
            boughtAnimals = setOf(ForestAnimal.BIRD),
        )
        val cipher = FakeCipher()
        val text = AccountCodec.encode(original, cipher)
        assertFalse(text.contains("콩이"))
        assertFalse(text.contains("작은 별"))
        assertEquals(original, AccountCodec.decode(text, cipher))
    }

    @Test
    fun `숲 칸이 없는 예전 저장본은 빈 칸으로 읽고, 이상한 칸 값은 건너뛴다`() {
        val cipher = FakeCipher()
        val base = GuardianAccount("g", 1L, children = listOf(ChildProfile("c1", "콩이", totalSprouts = 30)))
        val text = AccountCodec.encode(base, cipher)
        assertFalse(text.contains("spots"))
        assertEquals(emptyMap<ForestMap, ForestSpots>(), AccountCodec.decode(text, cipher).children[0].spots)

        val broken = text.replace("\"createdAt\"", "\"spots\":{\"M99\":{},\"M2\":{\"t\":[[0,3,9],[1,-1,2],[2,4]],\"a\":[[\"A99\",1,1],[\"A02\",5,18]]}},\"createdAt\"")
        val spots = AccountCodec.decode(broken, cipher).children[0].spots
        assertEquals(setOf(ForestMap.COAST), spots.keys)
        assertEquals(mapOf(0 to ForestCell(3, 9)), spots.getValue(ForestMap.COAST).trees)
        assertEquals(mapOf(ForestAnimal.RABBIT to ForestCell(5, 18)), spots.getValue(ForestMap.COAST).animals)
    }

    @Test
    fun `모르는 동물·코드와 이유 이름은 건너뛰고 읽는다`() {
        val text = """{"v":1,"id":"g","children":[{"id":"c","nick":"${FakeCipher().encrypt("콩이")}","sprouts":3,"animals":["BIRD","DRAGON"],
            |"tree":"T99","map":"M99","planted":[{"k":"T99","n":1},{"k":"T02"},"x"]}],"unlocks":["Z01","A04"],
            |"trips":[{"id":"t","reason":"SOMETHING_NEW","km":1}]}""".trimMargin()
        val a = AccountCodec.decode(text, FakeCipher())
        assertEquals(setOf(ForestAnimal.BIRD, ForestAnimal.DEER), a.boughtAnimals)
        assertEquals(TreeKind.DEFAULT, a.children.single().treeKind)
        assertEquals(ForestMap.DEFAULT, a.children.single().map)
        assertEquals(listOf(TreeRun(TreeKind.OAK, 1)), a.children.single().planted)
        assertEquals(PointReason.NOTHING, a.trips.single().pointReason)
        assertEquals(0, a.stars)
    }

    @Test
    fun `v1 저장본 데이터 이전 - 누적 ★ = 보유, 예전 동물은 가족 해금, 나무는 모두 참나무`() {
        val nick = FakeCipher().encrypt("콩이")
        val v1 = """{"v":1,"id":"g","confirmedAt":7,"wallet":{"balance":1230,"month":"2026-11","earned":360},
            |"children":[{"id":"c1","nick":"$nick","sprouts":126,"animals":["SQUIRREL","RABBIT","BIRD"],"createdAt":5},
            |{"id":"c2","nick":"$nick","sprouts":54,"animals":["SQUIRREL"],"createdAt":6}],
            |"lastRiders":["c1"],"trips":[]}""".trimMargin()
        val a = AccountCodec.decode(v1, FakeCipher())
        assertEquals(StarWallet(balance = 1_230, lifetime = 1_230, monthKey = "2026-11", earnedInMonth = 360), a.wallet)
        // 다람쥐·토끼는 처음부터 열려 있으므로 저장하지 않고, 새(파랑새)는 이미 왔으니 연 것으로 옮긴다
        assertEquals(setOf(ForestAnimal.BIRD), a.boughtAnimals)
        assertTrue(StarRules.view(a).isUnlocked(ForestAnimal.BIRD))
        val c1 = a.child("c1")!!
        assertEquals(12, c1.trees)
        assertEquals(List(12) { TreeKind.OAK }, c1.plantedKinds())
        assertEquals(TreeKind.OAK, c1.treeKind)
        assertEquals(ForestMap.BASIC, c1.map)
        // 다시 저장하면 v2로 쓰고, 읽어도 같다
        val again = AccountCodec.decode(AccountCodec.encode(a, FakeCipher()), FakeCipher())
        assertEquals(a.copy(children = a.children.map { it.copy(planted = it.plantedRuns()) }), again)
        assertTrue(AccountCodec.encode(a, FakeCipher()).startsWith("{\"v\":2"))
    }

    @Test
    fun `숲 보관함(st·sa)은 저장했다 읽어도 그대로, 비어 있으면 쓰지 않는다`() {
        val cipher = FakeCipher()
        val spots = ForestSpots(
            trees = mapOf(1 to ForestCell(12, 9)),
            animals = mapOf(ForestAnimal.SQUIRREL to ForestCell(10, 12)),
            next = ForestCell(14, 10),
            storedTrees = setOf(0, 3),
            storedAnimals = setOf(ForestAnimal.RABBIT),
        )
        val account = GuardianAccount("g", 1L, children = listOf(ChildProfile("c1", "콩이", totalSprouts = 50, spots = mapOf(ForestMap.BASIC to spots))))
        val text = AccountCodec.encode(account, cipher)
        assertEquals(spots, AccountCodec.decode(text, cipher).children[0].spots[ForestMap.BASIC])
        val plain = account.copy(children = listOf(account.children[0].copy(spots = mapOf(ForestMap.BASIC to spots.copy(storedTrees = emptySet(), storedAnimals = emptySet())))))
        val plainText = AccountCodec.encode(plain, cipher)
        assertFalse(plainText.contains("\"st\""))
        assertFalse(plainText.contains("\"sa\""))
    }

    @Test
    fun `꾸미기 소품(decor·d)은 저장했다 읽어도 그대로, 비어 있으면 쓰지 않고 모르는 코드는 건너뛴다`() {
        val cipher = FakeCipher()
        val spots = ForestSpots(decor = listOf(PlacedDecor("WOODEN-BENCH", ForestCell(12, 14)), PlacedDecor("WOOD-FENCE-HORIZONTAL", ForestCell(3, 5))))
        val c = ChildProfile("c1", "콩이", totalSprouts = 20, spots = mapOf(ForestMap.BASIC to spots), decor = mapOf("WOODEN-BENCH" to 1, "WOOD-FENCE-HORIZONTAL" to 4))
        val account = GuardianAccount("g", 1L, children = listOf(c))
        val text = AccountCodec.encode(account, cipher)
        val back = AccountCodec.decode(text, cipher).children[0]
        assertEquals(c.decor, back.decor)
        assertEquals(spots, back.spots[ForestMap.BASIC])

        val plain = AccountCodec.encode(GuardianAccount("g", 1L, children = listOf(ChildProfile("c1", "콩이"))), cipher)
        assertFalse(plain.contains("\"decor\""))
        assertFalse(plain.contains("\"d\""))

        val odd = text.replace("WOOD-FENCE-HORIZONTAL", "NO-SUCH-THING")
        val oddBack = AccountCodec.decode(odd, cipher).children[0]
        assertEquals(mapOf("WOODEN-BENCH" to 1), oddBack.decor)
        assertEquals(listOf(PlacedDecor("WOODEN-BENCH", ForestCell(12, 14))), oddBack.spots[ForestMap.BASIC]!!.decor)
    }
}
