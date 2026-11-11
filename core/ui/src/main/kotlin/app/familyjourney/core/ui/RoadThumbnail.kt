package app.familyjourney.core.ui

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 큰 그림(풍경 길 1672×941 등)을 작게 줄여 읽는다(테마 고르기의 작은 그림). 원본 크기로 10장을 읽으면 메모리가 60MB를 넘으므로,
 * [widthPx]보다 크지 않은 가장 작은 2의 거듭제곱 배율로 줄여 읽는다(색·비율은 그대로). 읽는 동안은 null.
 */
@Composable
fun rememberThumbnail(@DrawableRes res: Int, widthPx: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, res, widthPx) {
        value = withContext(Dispatchers.IO) {
            val r = context.resources
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(r, res, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
            BitmapFactory.decodeResource(r, res, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
}
