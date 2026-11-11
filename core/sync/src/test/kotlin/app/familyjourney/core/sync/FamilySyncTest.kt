package app.familyjourney.core.sync

import app.familyjourney.core.account.DecorBuyResult
import app.familyjourney.core.account.DecorCatalog
import app.familyjourney.core.account.ForestMap
import app.familyjourney.core.account.ForestSpots
import app.familyjourney.core.account.GuardianAccount
import app.familyjourney.core.account.StarWallet
import app.familyjourney.core.account.ChildProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 서버의 판 번호 맞추기만 흉내 내는 가짜(server/src/family.ts putState와 같은 규칙) */
private class FakeServer {
    var version = 0
    var blob: SealedBlob? = null
    var offline = false

    fun client(): SyncClient = object : SyncClient by Unsupported {
        override suspend fun getState(auth: String, since: Int): SyncResult<RemoteState?> =
            if (offline) SyncResult.Offline else if (version <= since && blob != null) SyncResult.Ok(null) else SyncResult.Ok(RemoteState(version, blob))
        override suspend fun putState(auth: String, baseVersion: Int, blob: SealedBlob): SyncResult<Int> = when {
            offline -> SyncResult.Offline
            baseVersion != version -> SyncResult.Conflict(RemoteState(version, this@FakeServer.blob))
            else -> { version += 1; this@FakeServer.blob = blob; SyncResult.Ok(version) }
        }
    }
}

private object Unsupported : SyncClient {
    override suspend fun createFamily(deviceKeyHash: String, nameBlob: SealedBlob) = TODO()
    override suspend fun openPairing(auth: String, codeHash: String, shortCodeHash: String?, wrappedKey: SealedBlob?) = TODO()
    override suspend fun claimShort(shortCode: String, deviceKeyHash: String, nameBlob: SealedBlob) = TODO()
    override suspend fun setName(auth: String, nameBlob: SealedBlob) = TODO()
    override suspend fun claim(familyId: String, code: String, deviceKeyHash: String, nameBlob: SealedBlob) = TODO()
    override suspend fun me(auth: String) = TODO()
    override suspend fun pendingDevices(auth: String) = TODO()
    override suspend fun devices(auth: String) = TODO()
    override suspend fun approve(auth: String, deviceId: String, roles: Set<DeviceRole>) = TODO()
    override suspend fun removeDevice(auth: String, deviceId: String) = TODO()
    override suspend fun wipeFamily(auth: String) = TODO()
    override suspend fun getState(auth: String, since: Int): SyncResult<RemoteState?> = TODO()
    override suspend fun putState(auth: String, baseVersion: Int, blob: SealedBlob): SyncResult<Int> = TODO()
    override suspend fun putStatus(auth: String, blob: SealedBlob, locationBlob: SealedBlob?, expiresAtEpochMs: Long?) = TODO()
    override suspend fun getStatus(auth: String) = TODO()
    override suspend fun clearStatus(auth: String) = TODO()
}

class FamilySyncTest {
    private val fam = Base64Url.random(16)
    private val key = FamilyCipher.newFamilyKey()
    private fun creds(car: Boolean) = LinkCredentials(fam, Base64Url.random(8), Base64Url.random(32), key, car)
    private val start = GuardianAccount(
        id = "g", confirmedAtEpochMs = 0, wallet = StarWallet(balance = 100, lifetime = 100),
        children = listOf(ChildProfile(id = "c1", nickname = "하늘이")),
    )

    private suspend fun seededCar(server: FakeServer): FamilySync {
        val car = FamilySync(server.client(), creds(true), InMemorySnapshotStore())
        car.mutate(AccountOp.ConfirmGuardian("g", 0)) // 계정 없음 → 만들기
        return car
    }

    /** 차가 처음 올리면 판 1, 폰이 받아 본다 */
    @Test fun carSeedsVersionOneAndPhoneReadsIt() = runTest {
        val server = FakeServer()
        val store = InMemorySnapshotStore(StoredSnapshot(0, app.familyjourney.core.account.AccountSnapshotCodec.encode(start), dirty = false))
        val car = FamilySync(server.client(), creds(true), store)
        car.refresh()
        assertEquals(1, server.version)
        val phone = FamilySync(server.client(), creds(false), InMemorySnapshotStore())
        assertEquals("하늘이", phone.refresh().account?.child("c1")?.nickname)
    }

    /** 두 기기가 같은 판을 보고 ★를 쓰면 나중 것은 다시 적용되고 모자라면 거절 */
    @Test fun concurrentSpendIsReappliedAndRefusedWhenShort() = runTest {
        val server = FakeServer()
        val car = FamilySync(server.client(), creds(true), InMemorySnapshotStore(StoredSnapshot(0, app.familyjourney.core.account.AccountSnapshotCodec.encode(start), false)))
        car.refresh()
        val phone = FamilySync(server.client(), creds(false), InMemorySnapshotStore())
        phone.refresh()
        val item = DecorCatalog.all.first { it.price in 51..100 } // 둘 다 사면 100★를 넘는 것
        assertTrue(car.mutate(AccountOp.BuyDecor("c1", item)) is DecorBuyResult.Bought)
        val r = phone.mutate(AccountOp.BuyDecor("c1", item)) // 폰은 옛 판(★100)을 보고 있었다 → 409 → 다시 적용 → 모자람
        assertTrue(r is DecorBuyResult.NotEnough)
        assertEquals(100 - item.price, phone.state.value.account?.stars)
        assertEquals(0, phone.state.value.pending)
    }

    /** 같은 맵은 마지막 저장이 남고 다른 맵은 서로 지우지 않는다 */
    @Test fun sameMapLastSaveWins() = runTest {
        val server = FakeServer()
        val car = FamilySync(server.client(), creds(true), InMemorySnapshotStore(StoredSnapshot(0, app.familyjourney.core.account.AccountSnapshotCodec.encode(start), false)))
        car.refresh()
        val phone = FamilySync(server.client(), creds(false), InMemorySnapshotStore())
        phone.refresh()
        val a = ForestSpots(next = app.familyjourney.core.account.ForestCell(1, 1))
        val b = ForestSpots(next = app.familyjourney.core.account.ForestCell(2, 2))
        car.mutate(AccountOp.SaveSpots("c1", ForestMap.DEFAULT, a))
        phone.mutate(AccountOp.SaveSpots("c1", ForestMap.DEFAULT, b))
        car.refresh()
        assertEquals(b.next, car.state.value.account?.child("c1")?.spots?.get(ForestMap.DEFAULT)?.next)
    }

    /** 인터넷이 없으면 기기 안에 바로 적용하고 다시 연결되면 올린다 */
    @Test fun offlineChangesAreAppliedLocallyAndPushedLater() = runTest {
        val server = FakeServer()
        val car = seededCar(server)
        server.offline = true
        car.mutate(AccountOp.ChooseTree("none", app.familyjourney.core.account.TreeKind.DEFAULT)) // 아이 없음 → 안 바뀜
        car.mutate(AccountOp.AddChild("바다", "c2", 1))
        assertEquals(SyncProblem.OFFLINE, car.state.value.problem)
        assertEquals(1, car.state.value.pending)
        server.offline = false
        car.refresh()
        assertNull(car.state.value.problem)
        assertEquals(2, server.version)
    }
}
