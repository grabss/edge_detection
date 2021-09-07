import android.graphics.*

// BitmapクラスのExtension
fun Bitmap.setContrast(contrast: Float = 1.0F): Bitmap? {
    val bitmap = copy(Bitmap.Config.ARGB_8888, true)
    val paint = Paint()
    val matrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, 0f,
            0f, contrast, 0f, 0f, 0f,
            0f, 0f, contrast, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val filter = ColorMatrixColorFilter(matrix)
    paint.colorFilter = filter

    Canvas(bitmap).drawBitmap(this, 0f, 0f, paint)
    return bitmap
}