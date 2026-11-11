package app.familyjourney.phone.ui

import android.view.ViewGroup.LayoutParams
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.familyjourney.core.ui.GgfTheme
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import java.io.File
import java.lang.reflect.Proxy

/*
 * 화면 캡처 도구(테스트 전용). 앱 코드는 바꾸지 않고, 화면 함수에 상태를 넣어 그린 그림을 PNG로 쓴다.
 *
 * - 밀도는 에뮬레이터와 같은 160dpi(1dp = 1px)다(docs/emulator-capabilities.md 11장). 그래서 PNG 크기 = 창 크기(dp).
 * - 주차 중 화면은 짧은 움직임(최대 약 2.7초)이 끝난 뒤의 모습을 찍는다([settled]). 주행 중 화면은 원래 정지 그림이다.
 */

/**
 * 폰 앱을 찍을 창 크기(dp, 160dpi라 1dp = 1px).
 * - [PHONE] 흔한 안드로이드 폰 세로(412×915dp)
 * - [TABLET] 10인치 태블릿 가로(1280×800dp). 600dp보다 넓어 탭이 왼쪽 세로 줄로 간다
 */
enum class ShotWindow(val key: String, val widthDp: Int, val heightDp: Int) {
    PHONE("phone", 412, 915),
    TABLET("tablet", 1280, 800),
}

/** 찍은 그림을 쓰는 폴더. Gradle(screens/build.gradle.kts)이 저장소 루트 기준 design/screens/phone을 넘긴다. */
internal val outDir: File by lazy {
    File(System.getProperty("screens.outDir") ?: error("screens.outDir가 없습니다. ./gradlew -Pphone=true :phone:ui:testDebugUnitTest로 실행하세요"))
        .also { it.mkdirs() }
}

/**
 * 받은 프레임 중 **마지막** 것을 `<이름>.png`로 쓴다(한 장이면 그 한 장). Paparazzi의 기록·비교 기능은 쓰지 않는다.
 *
 * Android 모듈의 테스트 코드는 android.jar 기준으로 컴파일되어 `java.awt`·`javax.imageio`를 직접 쓸 수 없다.
 * 그래서 프레임 받는 쪽은 [Proxy]로 만들고, PNG 쓰기는 실행 중인 JDK의 ImageIO를 이름으로 찾아 부른다.
 */
internal class PngFolderHandler(private val dir: () -> File) : SnapshotHandler {
    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
        val name = requireNotNull(snapshot.name) { "그림 이름이 필요합니다" }
        var last: Any? = null
        val type = SnapshotHandler.FrameHandler::class.java
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "handle" -> last = args!![0] // java.awt.image.BufferedImage
                "close" -> last?.let { writePng(it, File(dir(), "$name.png")) }
                "toString" -> return@newProxyInstance "PngFrameHandler($name)"
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "equals" -> return@newProxyInstance proxy === args?.get(0)
            }
            null
        } as SnapshotHandler.FrameHandler
    }

    override fun close() = Unit

    private fun writePng(image: Any, file: File) {
        val imageIo = Class.forName("javax.imageio.ImageIO")
        val rendered = Class.forName("java.awt.image.RenderedImage")
        val ok = imageIo.getMethod("write", rendered, String::class.java, File::class.java).invoke(null, image, "PNG", file) as Boolean
        check(ok) { "PNG를 쓰지 못했습니다: $file" }
        val w = image.javaClass.getMethod("getWidth").invoke(image)
        val h = image.javaClass.getMethod("getHeight").invoke(image)
        println("찍음: ${file.name} (${w}x$h)")
    }
}

private fun deviceOf(window: ShotWindow, dark: Boolean) = DeviceConfig(
    screenWidth = window.widthDp,
    screenHeight = window.heightDp,
    xdpi = 160,
    ydpi = 160,
    density = Density.MEDIUM,
    orientation = if (window.widthDp > window.heightDp) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
    nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT,
    locale = "ko-rKR",
)

/** 테스트 클래스마다 하나. 창 크기·다크는 [shoot]가 그때그때 바꾼다. */
internal fun newPaparazzi(): Paparazzi = Paparazzi(
    deviceConfig = deviceOf(ShotWindow.PHONE, dark = false),
    theme = "android:Theme.Material.Light.NoActionBar",
    // 기본값이면 긴 변이 1000px을 넘을 때 줄여 저장한다. 실제 크기 그대로 찍는다
    useDeviceResolution = true,
    snapshotHandler = PngFolderHandler { outDir },
)

/** 주차 중 움직임이 끝날 때까지 기다리는 시간. 가장 긴 것(ParkWave 900ms × 3)보다 넉넉하게 */
private const val SETTLE_MS = 3_200L

/** 16fps면 한 프레임이 62.5ms라 core:ui 움직임 시계(프레임 사이 최대 64ms까지만 셈)가 빠짐없이 흐른다 */
private const val SETTLE_FPS = 16

/**
 * [content]를 [window] 크기로 그려 `<name>.png`로 쓴다. 앱처럼 [GgfTheme]으로 감싼다(다크는 시스템 설정으로).
 *
 * @param settled true면 주차 중 짧은 움직임이 끝난 마지막 장면을 찍는다(새 나무가 다 자란 모습 등)
 */
internal fun Paparazzi.shoot(
    name: String,
    window: ShotWindow,
    dark: Boolean = false,
    settled: Boolean = false,
    content: @Composable () -> Unit,
) {
    unsafeUpdateConfig(deviceConfig = deviceOf(window, dark))
    val view = ComposeView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setContent { GgfTheme { content() } }
    }
    if (settled) {
        gif(view, name = name, start = 0L, end = SETTLE_MS, fps = SETTLE_FPS)
    } else {
        snapshot(view, name = name)
    }
}

/** 파일 이름: `<화면>-<창>[-dark]` */
internal fun shotName(screen: String, window: ShotWindow, dark: Boolean = false): String =
    buildString {
        append(screen).append('-').append(window.key)
        if (dark) append("-dark")
    }
