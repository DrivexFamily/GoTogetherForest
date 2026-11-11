package app.familyjourney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * qa 정적 점검(docs/qa-checklist.md 10장, 9장). 소스 파일을 읽어서 확인한다(제품 코드는 바꾸지 않는다).
 *
 * 1. **콘텐츠 규칙(CLAUDE.md 1-6):** 화면·음성 문자열에 창문을 열거나 몸을 내밀게 하는 말, 빠른 도착을 칭찬하는 말,
 *    광고·게임 요소가 없다. 아이가 보는 문자열에 차속(km/h)이 없다(발표자 패널·개발용 화면은 제외).
 * 2. **release 설정:** release는 debuggable이 아니고 개발 도구가 꺼져 있다. 앱 백업은 꺼져 있다(별명 키를 잃지 않게, #11).
 *
 * Gradle 단위 테스트의 작업 폴더는 모듈 폴더(app/)다.
 */
class QaContentAndReleaseRulesTest {
    private companion object { const val MAX_LINE = 2_000 }

    private val root = File("..").canonicalFile

    /** 제품 코드(main)의 Kotlin 파일. 개발용 화면(app/.../dev/)은 debug에서만 보이므로 [includeDev]일 때만 넣는다 */
    private fun mainSources(includeDev: Boolean): List<File> =
        listOf("app", "core", "feature").map { File(root, it) }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { "${File.separator}src${File.separator}main${File.separator}" in it.path }
            .filter { includeDev || "${File.separator}dev${File.separator}" !in it.path }

    /** 주석 줄을 뺀 문자열 상수들(파일:줄 → 글자) */
    private fun literals(files: List<File>): List<Pair<String, String>> {
        val str = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
        return files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val t = line.trim()
                // 아주 긴 한 줄(지도 경로 데이터 등, 사람이 읽는 문구가 아님)은 정규식이 스택을 넘치므로 뺀다(2026-11-08)
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || line.length > MAX_LINE) null else "${f.relativeTo(root)}:${i + 1}" to line
            }.flatMap { (where, line) -> str.findAll(line).map { where to it.groupValues[1] }.toList() }
        }
    }

    @Test
    fun `문자열을 실제로 읽고 있다 - 음성 문장과 아이 문구가 보인다`() {
        val all = literals(mainSources(includeDev = true)).map { it.second }
        for (expected in listOf("출발! 같이 가 볼까?", "부드럽게 달려 줘서 고마워요", "휴게소에서 쉬는 중", "목적지를 기다리고 있어요")) {
            assertTrue("검사 대상에 없음(검사가 비어 있음): $expected", all.any { expected in it })
        }
    }

    @Test
    fun `창문을 열거나 몸을 내밀게 하는 말이 없다`() {
        val bad = Regex("창문|창을 ?열|창 ?열어|문을 ?열|내밀|밖으로|손을 ?내|고개를 ?내|얼굴을 ?내")
        val hits = literals(mainSources(includeDev = true)).filter { bad.containsMatchIn(it.second) }
        assertTrue("금지 문구: $hits", hits.isEmpty())
    }

    @Test
    fun `빠른 도착을 칭찬하거나 재촉하는 말이 없다`() {
        val bad = Regex("빨리|서둘|일찍 ?도착|더 ?빠르|빠르게 ?도착|분 만에|시간 만에|기록 ?단축|최단")
        val hits = literals(mainSources(includeDev = true)).filter { bad.containsMatchIn(it.second) }
        assertTrue("금지 문구: $hits", hits.isEmpty())
    }

    @Test
    fun `광고와 게임 요소 문구가 없다`() {
        val bad = Regex("광고|레벨 ?업|1등|순위|랭킹|이겼|졌어|점수|콤보|보너스 ?스테이지")
        val hits = literals(mainSources(includeDev = true)).filter { bad.containsMatchIn(it.second) }
        assertTrue("금지 문구: $hits", hits.isEmpty())
    }

    @Test
    fun `아이가 보는 문자열에 차속이 없다 - 발표자 패널과 개발용 화면만 예외`() {
        val kidFacing = mainSources(includeDev = false).filter { !it.name.startsWith("PresenterPanel") }
        val hits = literals(kidFacing).filter { Regex("km/h|㎞/h|시속").containsMatchIn(it.second) }
        assertTrue("아이 화면 문자열에 차속: $hits", hits.isEmpty())
    }

    // ---- release 설정 ----

    private val buildFile = File("build.gradle.kts").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    /** `release { ... }` 블록 본문(중괄호 짝을 센다) */
    private fun releaseBlock(): String {
        val start = Regex("""\brelease\s*\{""").find(buildFile) ?: error("build.gradle.kts에 release 블록이 없다")
        var depth = 0
        for (i in start.range.last until buildFile.length) {
            when (buildFile[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return buildFile.substring(start.range.last + 1, i)
            }
        }
        error("release 블록이 닫히지 않았다")
    }

    @Test
    fun `release는 debuggable이 아니고 개발 도구가 꺼져 있다`() {
        val release = releaseBlock()
        assertFalse("release에 isDebuggable = true", Regex("""isDebuggable\s*=\s*true""").containsMatchIn(release))
        assertTrue("release의 DEV_TOOLS는 false", Regex("""buildConfigField\("boolean",\s*"DEV_TOOLS",\s*"false"\)""").containsMatchIn(release))
        assertFalse("매니페스트에 android:debuggable", manifest.contains("android:debuggable"))
        // release 전용 매니페스트·소스가 따로 있으면 위 확인이 빗나간다
        assertFalse("app/src/release가 있다(별도 검토 필요)", File("src/release").exists())
    }

    @Test
    fun `앱 백업은 꺼져 있다 - 별명 암호화 키를 새 기기로 옮길 수 없으므로`() {
        val m = Regex("""android:allowBackup="(true|false)"""").find(manifest)
        assertEquals("false", m?.groupValues?.get(1))
    }
}
