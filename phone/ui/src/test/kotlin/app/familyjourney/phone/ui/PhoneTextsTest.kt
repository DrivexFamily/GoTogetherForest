package app.familyjourney.phone.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneTextsTest {
    // 남은 시간 문장: 분·시간, 정각, 모름, 거의 다 옴
    @Test
    fun remainingSentence() {
        assertEquals("약 1시간 20분 남았어요", PhoneTexts.remaining(80))
        assertEquals("약 2시간 남았어요", PhoneTexts.remaining(120))
        assertEquals("약 45분 남았어요", PhoneTexts.remaining(45))
        assertEquals("거의 다 왔어요", PhoneTexts.remaining(0))
        assertEquals("목적지로 가고 있어요", PhoneTexts.remaining(null))
    }

    // "N초 전 갱신"
    @Test
    fun updatedAgo() {
        assertEquals("방금 갱신", PhoneTexts.updatedAgo(2))
        assertEquals("12초 전 갱신", PhoneTexts.updatedAgo(12))
        assertEquals("3분 전 갱신", PhoneTexts.updatedAgo(190))
    }
}
