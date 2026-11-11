package app.familyjourney

import app.familyjourney.core.ui.ScreenArt
import app.familyjourney.core.ui.ScreenPicture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 처음 실행 안내(GPT R32): 주차 중에만, 한 번만, 그림이 있는 장만 */
class OnboardingGuideTest {
    private fun pic(art: ScreenArt) = ScreenPicture(art, 1, 720, 720)

    @Test
    fun `그림이 있는 장만 순서대로`() {
        assertEquals(0, OnboardingGuide.pages(emptyList()).size)
        val pages = OnboardingGuide.pages(listOf(pic(ScreenArt.GuideOnboard3), pic(ScreenArt.GuideOnboard1)))
        assertEquals(listOf(ScreenArt.GuideOnboard1, ScreenArt.GuideOnboard3), pages.map { it.picture.art })
    }

    @Test
    fun `주차 중 홈에서만, 본 뒤에는 다시 안 보임`() {
        assertTrue(OnboardingGuide.shouldShow(seen = false, pageCount = 3, parked = true, screen = AppScreen.HOME))
        assertTrue(OnboardingGuide.shouldShow(seen = false, pageCount = 3, parked = true, screen = AppScreen.ONBOARDING))
        assertFalse(OnboardingGuide.shouldShow(seen = true, pageCount = 3, parked = true, screen = AppScreen.HOME))
        assertFalse(OnboardingGuide.shouldShow(seen = false, pageCount = 3, parked = false, screen = AppScreen.HOME))
        assertFalse(OnboardingGuide.shouldShow(seen = false, pageCount = 0, parked = true, screen = AppScreen.HOME))
        assertFalse(OnboardingGuide.shouldShow(seen = false, pageCount = 3, parked = true, screen = AppScreen.JOURNEY))
    }
}
