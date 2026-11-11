package app.familyjourney.core.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 대비 계산. 글자는 4.5:1, 그림(길·깃발·차 윤곽)은 3:1 이상을 지킨다.
 * Compose에 기대지 않는 순수 계산이라 단위 테스트에서 토큰 짝을 바로 검사한다.
 */
internal object Contrast {
    const val TEXT_MIN = 4.5
    const val GRAPHIC_MIN = 3.0

    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
