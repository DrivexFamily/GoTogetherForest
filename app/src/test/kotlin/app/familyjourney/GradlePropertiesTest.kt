package app.familyjourney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * 앱 이름 깨짐 재발 방지(2026-11-01).
 * Gradle은 gradle.properties를 ISO-8859-1로 읽는다. 한글을 그대로 쓰면 앱 이름이 "ê°ì´ê°ì²"처럼 깨진다.
 * 그래서 값은 ASCII만 쓰고, 한글은 유니코드 이스케이프(백슬래시 u + 16진수 4자리)로 적는다.
 * Gradle 단위 테스트의 작업 폴더는 모듈 폴더(app/)다.
 */
class GradlePropertiesTest {
    private val file = File("../gradle.properties")

    /** 주석·빈 줄을 뺀 "키=값" 줄 */
    private fun entries(): List<Pair<String, String>> =
        file.readLines(Charsets.ISO_8859_1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") && it.contains('=') }
            .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

    @Test
    fun `app 값은 모두 ASCII다`() {
        val appEntries = entries().filter { it.first.startsWith("app.") }
        assertTrue("app.* 값이 없음", appEntries.isNotEmpty())
        for ((key, value) in appEntries) {
            val bad = value.filter { it.code > 0x7F }
            assertTrue("$key 에 ASCII가 아닌 글자가 있음: \"$bad\" → 유니코드 이스케이프로 적으세요", bad.isEmpty())
        }
    }

    @Test
    fun `모든 설정 값이 ASCII다`() {
        for ((key, value) in entries()) {
            assertTrue("$key 에 ASCII가 아닌 글자가 있음", value.all { it.code <= 0x7F })
        }
    }

    @Test
    fun `Gradle과 같은 방식으로 읽으면 앱 이름이 같이가숲이다`() {
        // java.util.Properties.load(InputStream)도 ISO-8859-1 + 유니코드 이스케이프 규칙으로 읽는다(Gradle과 같다).
        val props = Properties().apply { file.inputStream().use { load(it) } }
        assertEquals("같이가숲", props.getProperty("app.displayName"))
        assertEquals("GoTogetherForest", props.getProperty("app.nameEn"))
        assertEquals("DriveXFamily", props.getProperty("app.teamName"))
    }
}
