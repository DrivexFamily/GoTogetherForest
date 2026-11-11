package app.familyjourney.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * GPT 일러스트(결정 #29) 점검.
 * - [GgfPicture]에 적은 크기가 실제 WebP 크기와 같다(비율이 틀리면 그림이 찌그러진다).
 * - 주행 포즈마다 차 안 가족 그림이 있고, 네 장의 비율이 같다(포즈가 바뀌어도 차가 흔들리지 않게).
 * - 움직이지 않을 때는 그림을 그대로 둔다.
 */
class PicturesTest {

    private val resDir = File("src/main/res/drawable-nodpi")

    /** WebP 머리에서 가로·세로를 읽는다(VP8X, VP8L, VP8). */
    private fun webpSize(f: File): Pair<Int, Int> {
        val b = f.readBytes()
        fun u8(i: Int) = b[i].toInt() and 0xff
        fun u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
        fun u24(i: Int) = u8(i) or (u8(i + 1) shl 8) or (u8(i + 2) shl 16)
        assertEquals("RIFF", String(b, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(b, 8, 4, Charsets.US_ASCII))
        return when (String(b, 12, 4, Charsets.US_ASCII)) {
            "VP8X" -> (u24(24) + 1) to (u24(27) + 1)
            "VP8L" -> {
                val bits = u8(21) or (u8(22) shl 8) or (u8(23) shl 16) or (u8(24) shl 24)
                ((bits and 0x3fff) + 1) to (((bits shr 14) and 0x3fff) + 1)
            }
            else -> (u16(26) and 0x3fff) to (u16(28) and 0x3fff)
        }
    }

    @Test
    fun `적어 둔 크기가 실제 WebP 크기와 같다`() {
        val files = resDir.listFiles { f -> f.name.startsWith("ggf_pic_") }!!.associateBy { it.nameWithoutExtension }
        for (p in GgfPicture.entries) {
            val name = nameOf(p)
            assertTrue("ggf_pic_로 시작해야 함: $name", name.startsWith("ggf_pic_"))
            val file = files[name]
            assertNotNull("그림 파일이 없음: $name", file)
            assertTrue("빈 파일: $name", file!!.length() > 0)
            assertEquals("크기가 다름: $name", p.widthPx to p.heightPx, webpSize(file))
        }
    }

    /** enum → 리소스 파일 이름. R.drawable에서 같은 id의 이름을 찾는다(새 그림을 넣어도 표를 고칠 필요 없음) */
    private fun nameOf(p: GgfPicture): String =
        R.drawable::class.java.fields.firstOrNull { it.getInt(null) == p.res }?.name ?: error("R.drawable에 없음: $p")

    /**
     * 숲 도감(결정 #31): 나무 10종·동물 10종의 그림이 ui-design의 목록(design/illustrations/derived/catalog.json)과 같고,
     * 목록에서 ok=true인 그림만 쓴다. 잠긴 실루엣은 원래 그림과 크기가 같다(같은 모양).
     */
    @Test
    fun `도감 그림은 catalog_json과 같고 ok인 그림만 쓴다`() {
        val catalog = File("../../design/illustrations/derived/catalog.json").readText()
        // 항목 하나 = 중괄호 한 쌍(중첩 없음). "id", "webp", "locked_webp", "ok"만 읽는다
        val entries = Regex("""\{[^{}]*"id"\s*:\s*"([TA]\d\d)"[^{}]*\}""").findAll(catalog).associate { m ->
            fun field(key: String) = Regex(""""$key"\s*:\s*("?)([^",}\s]*)\1""").find(m.value)?.groupValues?.get(2)
            m.groupValues[1] to Triple(field("webp"), field("locked_webp"), field("ok") == "true")
        }
        assertEquals(20, entries.size)
        for (t in TreeSpecies.entries) {
            val (webp, locked, ok) = entries.getValue(t.code)
            assertTrue("${t.code} ok=false인 그림을 쓰면 안 됨", ok)
            assertEquals(t.code, webp, nameOf(t.picture))
            assertEquals(t.code, locked, nameOf(t.lockedPicture!!))
            assertEquals(t.picture.widthPx to t.picture.heightPx, t.lockedPicture!!.widthPx to t.lockedPicture!!.heightPx)
        }
        for (a in Animal.entries) {
            val (webp, locked, ok) = entries.getValue(a.code)
            if (!ok) {
                assertNull("${a.code} ok=false인 그림을 쓰면 안 됨", a.picture)
                continue
            }
            assertEquals(a.code, webp, nameOf(a.picture!!))
            assertEquals(a.code, locked, nameOf(a.lockedPicture!!))
            assertEquals(a.picture!!.widthPx to a.picture!!.heightPx, a.lockedPicture!!.widthPx to a.lockedPicture!!.heightPx)
        }
    }

    @Test
    fun `주행 포즈마다 차 안 가족 그림이 있고 네 장의 비율이 같다`() {
        val cars = FamilyPose.entries.filter { it.driving }.map { carPictureFor(it) }
        cars.forEach { assertNotNull(it) }
        assertEquals(4, cars.toSet().size)
        cars.forEach { assertEquals(CAR_ASPECT, it!!.aspect, 0.0001f) }
        FamilyPose.entries.filter { !it.driving }.forEach { assertNull(carPictureFor(it)) }
    }

    @Test
    fun `움직이지 않을 때는 그림 그대로`() {
        // 주행 포즈는 서 있는 움직임이 없다
        FamilyPose.entries.filter { it.driving }.forEach { assertEquals(PictureMotion.Still, standingMotion(it, 0.3f)) }
        // 손 흔들기·만세는 phase 0에서 정지 장면
        assertEquals(0f, standingMotion(FamilyPose.ParkWave, 0f).rotation, 0.0001f)
        assertEquals(0f, standingMotion(FamilyPose.ParkCheer, 0f).translateY, 0.0001f)
        assertEquals(0f, carMotion(0f).translateY, 0.0001f)
        // 움직임은 작다(그림이 크게 흔들리지 않게)
        for (pose in FamilyPose.entries) for (i in 0..20) {
            val m = standingMotion(pose, i / 20f)
            assertTrue(kotlin.math.abs(m.rotation) <= 3f && kotlin.math.abs(m.translateY) <= 0.08f)
        }
    }
}
