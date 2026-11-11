package app.familyjourney.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KidTimeFormatterTest {

    private fun f(min: Float) = KidTimeFormatter.format(min)!!

    @Test
    fun `20분은 끝말잇기 2판, 19분은 노래 5곡(경계, 결정 #35)`() {
        val a = f(20f)
        assertEquals(KidTimeUnit.WORD_CHAIN, a.unit)
        assertEquals("끝말잇기 2판 하면 도착해요", a.sentence)
        assertEquals("끝말잇기 2판 남았어요", a.shortSentence)
        val b = f(19f)
        assertEquals(KidTimeUnit.SONG, b.unit)
        assertEquals("노래 5곡 같이 부르면 도착해요", b.sentence)
        assertEquals("노래 5곡 남았어요", b.shortSentence)
        assertEquals(KidTimeUnit.SONG, f(19.99f).unit)
    }

    @Test
    fun `끝말잇기는 가까운 판(10분)으로`() {
        assertEquals("끝말잇기 2판 하면 도착해요", f(24.9f).sentence)
        assertEquals("끝말잇기 3판 하면 도착해요", f(25f).sentence)
        assertEquals(3.0, f(25f).count, 0.0)
        assertEquals("끝말잇기 4판 하면 도착해요", f(40f).sentence)
        assertEquals("끝말잇기 5판 남았어요", f(45f).shortSentence)
        // 데모 처음: 400km ÷ 85km/h ≈ 282분 → 28판
        assertEquals("끝말잇기 28판 하면 도착해요", f(282.35f).sentence)
    }

    @Test
    fun `노래는 올림, 최소 1곡`() {
        assertEquals(1.0, f(0.2f).count, 0.0)
        assertEquals(1.0, f(4f).count, 0.0)
        assertEquals(2.0, f(4.1f).count, 0.0)
        assertEquals(4.0, f(16f).count, 0.0)
        assertEquals(5.0, f(16.5f).count, 0.0)
    }

    @Test
    fun `0분 이하는 곧 도착, 모르면 null`() {
        assertEquals(KidTimeUnit.SOON, f(0f).unit)
        assertEquals("곧 도착해요", f(-3f).sentence)
        assertNull(KidTimeFormatter.format(null))
        assertNull(KidTimeFormatter.format(Float.NaN))
        assertEquals("도착했어요!", KidTimeFormatter.arrived().sentence)
        assertEquals("도착!", KidTimeFormatter.arrived().shortSentence)
    }
}
