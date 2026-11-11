package app.familyjourney.core.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 실제 DataStore(임시 파일) + 가짜 암호화기로 저장·읽기를 확인한다.
 * (suspend 람다가 든 테스트는 이름을 영문으로 둔다. core:vehicle MergeSignalsTest 참고)
 */
class LocalAccountRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var file: File
    private val store: DataStore<Preferences> by lazy {
        file = File(tmp.root, "account.preferences_pb")
        PreferenceDataStoreFactory.create(scope = scope) { file }
    }
    private var ids = 0

    private fun repo(cipher: TextCipher = FakeCipher(), now: Long = NOV_1) =
        LocalAccountRepository(store, cipher, nowEpochMs = { now }, zone = { ZONE }, newId = { "id${++ids}" })

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun nicknameIsEncryptedOnDiskAndReadBackThroughCipher() = runBlocking {
        val writeCipher = FakeCipher()
        val r = repo(writeCipher)
        r.confirmGuardian()
        val added = r.addChild("콩이") as AddChildResult.Added
        assertEquals("콩이", added.child.nickname)
        assertTrue(writeCipher.encrypted >= 1)

        val bytes = file.readBytes()
        assertFalse("파일에 별명 평문이 있으면 안 된다", String(bytes, Charsets.UTF_8).contains("콩이"))
        assertFalse(bytes.containsSlice("콩이".toByteArray(Charsets.UTF_8)))
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("fake:"))

        // 같은 파일을 다른 저장소 객체(다른 암호화기)로 읽어도 별명이 돌아온다
        val readCipher = FakeCipher()
        val again = repo(readCipher).current()!!
        assertEquals("콩이", again.children.single().nickname)
        assertTrue(readCipher.decrypted >= 1)
    }

    @Test
    fun undecryptableNicknameKeepsForestWithPlaceholderName() = runBlocking {
        val r = repo()
        r.confirmGuardian()
        val id = (r.addChild("별이") as AddChildResult.Added).child.id
        r.applyTripReward(tripResult(), setOf(id))

        val broken = FakeCipher().apply { failDecrypt = true }
        val c = repo(broken).current()!!.children.single()
        assertEquals("아이 1", c.nickname)
        assertEquals(54, c.totalSprouts)
    }

    @Test
    fun addChildNeedsGuardianAndValidNickname() = runBlocking {
        val r = repo()
        assertEquals(AddChildResult.NoGuardian, r.addChild("콩이"))
        assertNull(r.current())
        r.confirmGuardian()
        assertEquals(AddChildResult.InvalidNickname, r.addChild("  "))
        assertEquals(AddChildResult.InvalidNickname, r.addChild("가나다라마바사아자차카"))
        repeat(GuardianAccount.MAX_CHILDREN) { assertTrue(r.addChild("아이$it") is AddChildResult.Added) }
        assertEquals(AddChildResult.TooManyChildren, r.addChild("하나 더"))
        assertEquals(GuardianAccount.MAX_CHILDREN, r.current()!!.children.size)
    }

    @Test
    fun confirmGuardianIsIdempotent() = runBlocking {
        val r = repo()
        val a1 = r.confirmGuardian()
        val a2 = r.confirmGuardian()
        assertEquals(a1.id, a2.id)
        assertEquals(NOV_1, a2.confirmedAtEpochMs)
    }

    @Test
    fun tripRewardIsSavedOnceAndFlowUpdates() = runBlocking {
        val r = repo()
        r.confirmGuardian()
        val a = (r.addChild("콩이") as AddChildResult.Added).child.id
        val b = (r.addChild("별이") as AddChildResult.Added).child.id

        val first = r.applyTripReward(tripResult("trip-x"), setOf(a, b))!!
        assertFalse(first.duplicate)
        val second = r.applyTripReward(tripResult("trip-x"), setOf(a, b))!!
        assertTrue(second.duplicate)

        val saved = r.account.first()!!
        assertEquals(listOf(54, 54), saved.children.map { it.totalSprouts })
        assertEquals(360, saved.stars)
        assertEquals(360, saved.lifetimeStars)
        assertEquals(1, saved.trips.size)
        assertEquals(listOf(a, b), saved.lastRiderIds)
        assertEquals("2026-11", saved.wallet.monthKey)
    }

    @Test
    fun noAccountMeansNoReward() = runBlocking {
        val r = repo()
        assertNull(r.applyTripReward(tripResult(), setOf("x")))
        assertNull(r.current())
    }

    @Test
    fun resetForestClearsForestAndUnlocksAndRefundsStars() = runBlocking {
        val r = repo()
        r.confirmGuardian()
        val a = (r.addChild("콩이") as AddChildResult.Added).child.id
        r.applyTripReward(tripResult(safeKm = 400, eventKm = 0), setOf(a)) // 60 새싹 = 나무 6, 400★
        assertTrue(r.unlock(TreeKind.APPLE) is UnlockResult.Unlocked)
        assertEquals(ChoiceResult.Chosen, r.chooseTree(a, TreeKind.APPLE))
        assertEquals(200, r.current()!!.stars)

        r.resetForest()
        val after = r.current()!!
        assertEquals(0, after.child(a)!!.totalSprouts)
        assertTrue(after.child(a)!!.planted.isEmpty())
        assertEquals(TreeKind.DEFAULT, after.child(a)!!.treeKind)
        assertTrue(after.boughtTrees.isEmpty())
        assertEquals("콩이", after.child(a)!!.nickname)
        assertEquals(400, after.stars) // 쓴 ★를 돌려받음
        assertEquals(400, after.lifetimeStars)
        assertEquals(1, after.trips.size)

        r.resetAll()
        assertNull(r.current())
    }

    @Test
    fun unlockSpendsStarsAndIsSavedOnDisk() = runBlocking {
        val r = repo()
        assertEquals(UnlockResult.NoGuardian, r.unlock(TreeKind.APPLE))
        r.confirmGuardian()
        val a = (r.addChild("콩이") as AddChildResult.Added).child.id
        assertEquals(UnlockResult.NotEnough(TreeKind.APPLE, 200), r.unlock(TreeKind.APPLE))
        r.applyTripReward(tripResult(safeKm = 360, eventKm = 40), setOf(a)) // 360★
        assertEquals(UnlockResult.Unlocked(TreeKind.APPLE, 160), r.unlock(TreeKind.APPLE))
        assertEquals(UnlockResult.AlreadyUnlocked(TreeKind.APPLE), r.unlock(TreeKind.APPLE))
        assertEquals(UnlockResult.NotEnough(ForestAnimal.BIRD, 40), r.unlock(ForestAnimal.BIRD))
        assertEquals(ChoiceResult.MapLocked(ForestMap.COAST, 1_640), r.chooseMap(a, ForestMap.COAST))
        assertEquals(ChoiceResult.Chosen, r.chooseTree(a, TreeKind.APPLE))

        // 다른 저장소 객체로 다시 읽어도 그대로
        val again = repo().current()!!
        assertEquals(160, again.stars)
        assertEquals(360, again.lifetimeStars)
        assertEquals(setOf(TreeKind.APPLE), again.boughtTrees)
        assertEquals(TreeKind.APPLE, again.child(a)!!.treeKind)
    }

    @Test
    fun syncIsPushedAfterSave() = runBlocking {
        val pushed = mutableListOf<GuardianAccount?>()
        val sync = object : AccountSync {
            override suspend fun push(account: GuardianAccount?) { pushed += account }
        }
        val r = LocalAccountRepository(store, FakeCipher(), sync, nowEpochMs = { NOV_1 }, zone = { ZONE }, newId = { "id${++ids}" })
        r.confirmGuardian()
        r.confirmGuardian() // 바뀐 것이 없으면 보내지 않는다
        r.addChild("콩이")
        assertEquals(2, pushed.size)
        assertEquals("콩이", pushed.last()!!.children.single().nickname)
    }

    private fun ByteArray.containsSlice(needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
