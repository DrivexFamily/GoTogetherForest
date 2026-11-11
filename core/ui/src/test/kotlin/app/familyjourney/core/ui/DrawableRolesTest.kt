package app.familyjourney.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 생성된 VectorDrawable이 Compose 색 덮어쓰기 규칙과 맞는지 검사한다.
 * - path 이름은 모두 색 역할 표([ArtRoleTokens])에 있어야 한다.
 * - VectorDrawable이 못 쓰는 것(그라데이션·필터)은 없어야 한다.
 * - 움직이는 관절 그룹이 있어야 한다.
 */
class DrawableRolesTest {

    private val dir = File("src/main/res/drawable")
    private val ns = "http://schemas.android.com/apk/res/android"

    private fun files() = dir.listFiles { f -> f.name.startsWith("ggf_") && f.name.endsWith(".xml") }!!.sortedBy { it.name }

    private fun parse(f: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(f).documentElement

    private fun Element.all(tag: String): List<Element> {
        val nl = getElementsByTagName(tag)
        return (0 until nl.length).map { nl.item(it) as Element }
    }

    @Test
    fun `그림 파일이 GgfArt 목록과 같다`() {
        val names = files().map { it.nameWithoutExtension }.toSet()
        assertEquals(17, names.size)
        assertTrue("ggf_car_stage0" in names && "ggf_car_stage75" in names)
    }

    @Test
    fun `path 이름은 모두 알려진 색 역할이다`() {
        val known = ArtRoleTokens.light.keys
        for (f in files()) {
            val root = parse(f)
            for (p in root.all("path")) {
                val role = p.getAttributeNS(ns, "name")
                assertTrue("${f.name}: 모르는 역할 '$role'", role in known)
                assertTrue("${f.name}: '$role' 빈 pathData", p.getAttributeNS(ns, "pathData").isNotBlank())
                val hasFill = p.hasAttributeNS(ns, "fillColor")
                val hasStroke = p.hasAttributeNS(ns, "strokeColor")
                assertTrue("${f.name}: '$role'에 색이 없다", hasFill || hasStroke)
                if (!hasFill) assertTrue("${f.name}: 선만 있는 path는 선 역할이어야 한다('$role')", role in ArtRoleTokens.selfStroke)
                if (hasFill) {
                    val expected = "#%06X".format(ArtRoleTokens.light.getValue(role) and 0xFFFFFF)
                    assertEquals("${f.name}: '$role' 라이트 색", expected, p.getAttributeNS(ns, "fillColor").uppercase())
                }
            }
        }
    }

    @Test
    fun `VectorDrawable이 못 쓰는 요소가 없다`() {
        for (f in files()) {
            val text = f.readText()
            for (bad in listOf("<gradient", "<aapt:attr", "filter", "<pattern", "<mask")) {
                assertTrue("${f.name}: $bad 사용", bad !in text)
            }
        }
    }

    @Test
    fun `움직이는 관절 그룹이 있다`() {
        fun groups(name: String) = parse(File(dir, "$name.xml")).all("group").map { it.getAttributeNS(ns, "name") }.toSet()
        for (person in listOf("ggf_child", "ggf_guardian_a", "ggf_guardian_b")) {
            val g = groups(person)
            for (joint in listOf("bob", "armL", "armR", "legL", "legR")) assertTrue("$person: $joint 없음", joint in g)
        }
        for (stage in listOf(0, 25, 50, 75)) {
            val g = groups("ggf_car_stage$stage")
            assertTrue("car $stage: kid", "kid" in g)
            assertTrue("car $stage: kidArm", "kidArm" in g)
            assertTrue("car $stage: 창(clip)", "rearWindow" in g && "frontWindow" in g)
        }
        assertTrue("tail" in groups("ggf_animal_squirrel"))
        assertTrue("ears" in groups("ggf_animal_rabbit"))
        assertTrue("wing" in groups("ggf_animal_bird"))
    }

    @Test
    fun `GgfArt 크기가 drawable 뷰포트와 같다`() {
        val byName = GgfArt.entries.associateBy { it.res }
        assertEquals(17, byName.size)
        val expected = mapOf(
            "ggf_guardian_a" to GgfArt.GuardianA, "ggf_guardian_b" to GgfArt.GuardianB, "ggf_child" to GgfArt.Child,
            "ggf_car_stage0" to GgfArt.CarStage0, "ggf_car_stage25" to GgfArt.CarStage25,
            "ggf_car_stage50" to GgfArt.CarStage50, "ggf_car_stage75" to GgfArt.CarStage75,
            "ggf_plant_sprout" to GgfArt.Sprout, "ggf_plant_sapling" to GgfArt.Sapling, "ggf_plant_tree" to GgfArt.Tree,
            "ggf_animal_squirrel" to GgfArt.Squirrel, "ggf_animal_rabbit" to GgfArt.Rabbit, "ggf_animal_bird" to GgfArt.Bird,
            "ggf_house" to GgfArt.House, "ggf_flag" to GgfArt.Flag, "ggf_icon_cartoon" to GgfArt.IconCartoon, "ggf_icon_song" to GgfArt.IconSong,
        )
        for ((file, art) in expected) {
            val root = parse(File(dir, "$file.xml"))
            assertEquals("$file 가로", art.viewportWidth, root.getAttributeNS(ns, "viewportWidth").toFloat(), 0.01f)
            assertEquals("$file 세로", art.viewportHeight, root.getAttributeNS(ns, "viewportHeight").toFloat(), 0.01f)
        }
    }
}
