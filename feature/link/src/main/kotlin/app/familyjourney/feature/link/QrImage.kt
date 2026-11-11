package app.familyjourney.feature.link

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** 연결 QR 그림(ZXing, Apache-2.0). 화면 밝기·테마와 상관없이 흰 바탕에 검은 점(카메라가 잘 읽게) */
internal object QrImage {
    fun of(text: String, sizePx: Int = 512): ImageBitmap {
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, sizePx, sizePx,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2),
        )
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) DARK else LIGHT }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private val DARK = android.graphics.Color.BLACK
    private val LIGHT = android.graphics.Color.WHITE

}
