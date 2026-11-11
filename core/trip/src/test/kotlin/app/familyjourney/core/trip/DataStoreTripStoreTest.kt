package app.familyjourney.core.trip

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.familyjourney.core.vehicle.Gear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 실제 DataStore(임시 파일)로 저장본 글자를 쓰고 읽고 지운다. 글자는 코덱으로 다시 저장본이 된다.
 * (suspend 람다가 든 테스트는 이름을 영문으로 둔다. core:vehicle MergeSignalsTest 참고)
 */
class DataStoreTripStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    private fun store(file: File): DataStoreTripStore {
        val scope = CoroutineScope(Dispatchers.IO + Job()).also { scopes += it }
        return DataStoreTripStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun writesReadsAndClearsSnapshotText() = runBlocking {
        val file = File(tmp.root, "trip.preferences_pb")
        val s = store(file)
        assertNull(s.read())

        val r = TripStateMachine(newTripId = { "trip-1" })
        r.onSignals(sig(speed = 60f, gear = Gear.DRIVE, odometer = 1_234.5f), 1_000L)
        r.onSignals(sig(speed = 60f, gear = Gear.DRIVE, odometer = 1_235.25f), 2_000L)
        val text = TripSnapshotCodec.encode(r.snapshot(2_000L, 1_793_000_000_000L)!!)
        s.write(text)
        assertEquals(text, s.read())
        val back = TripSnapshotCodec.decode(s.read()!!)!!
        assertEquals("trip-1", back.tripId)
        assertEquals(1_234.5f, back.meter.startOdometerKm)
        assertEquals(0.75, back.meter.distanceKm, 1e-9)

        s.write(null)
        assertNull(s.read())
    }
}
