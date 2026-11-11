package app.familyjourney.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FamilyCipherTest {
    private val key = FamilyCipher.newFamilyKey()
    private val fam = Base64Url.random(16)

    /** 잠근 것은 같은 열쇠·가족·종류로만 풀린다 */
    @Test fun opensOnlyWithSameKeyFamilyAndKind() {
        val c = FamilyCipher(key, fam)
        val blob = c.seal("하늘이", BlobKind.STATE)
        assertEquals("하늘이", c.open(blob, BlobKind.STATE))
        assertNull(c.open(blob, BlobKind.STATUS)) // 종류 바꿔치기
        assertNull(FamilyCipher(key, Base64Url.random(16)).open(blob, BlobKind.STATE)) // 다른 가족
        assertNull(FamilyCipher(FamilyCipher.newFamilyKey(), fam).open(blob, BlobKind.STATE)) // 다른 열쇠
    }

    /** 서버가 받는 모양(iv 16글자) */
    @Test fun ivMatchesServerShape() {
        val blob = FamilyCipher(key, fam).seal("x", BlobKind.NAME)
        assertEquals(16, blob.iv.length)
    }

    /** QR 내용은 정해진 모양만 읽는다 */
    @Test fun pairingQrParsesOnlyItsFormat() {
        val qr = PairingQr.newFor(fam, key)
        assertEquals(qr, PairingQr.parse(qr.format()))
        assertNull(PairingQr.parse("https://example.com/${qr.code}"))
        assertNull(PairingQr.parse(qr.format() + "x"))
        assertNotNull(PairingQr.parse("  " + qr.format() + "\n"))
        assert(!qr.toString().contains(qr.familyKey))
    }

    /** 대략 위치는 0점05도 칸 */
    @Test fun locationSnapsToFiveKmGrid() {
        val g = LocationGrid.of(37.56661, 126.97839)
        assertEquals(37.55, g.lat, 1e-9)
        assertEquals(127.0, g.lon, 1e-9)
    }

    /** 짧은 코드로 감싼 가족 열쇠는 같은 코드로만 풀린다 */
    @Test fun shortCodeWrapsFamilyKey() {
        val code = ShortCode.generate()
        val wrapped = ShortCode.wrapFamilyKey(code, key)
        assertEquals(key, ShortCode.unwrapFamilyKey(ShortCode.normalize(ShortCode.display(code).lowercase())!!, wrapped))
        assertNull(ShortCode.unwrapFamilyKey(ShortCode.generate(), wrapped))
        assertNull(ShortCode.normalize("ABCD-0000-1111"))
    }
}
