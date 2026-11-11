package com.fieldreport.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Draws annotations straight onto the original photo's pixels, never onto a screenshot of the preview. */
object PhotoRender {
    private fun rotation(file: File): Int = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90; ExifInterface.ORIENTATION_ROTATE_180 -> 180; ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0
        }
    }.getOrDefault(0)

    /** Width and height as the photo is displayed, i.e. after the camera's rotation tag is applied. */
    fun orientedSize(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        val w = max(1, o.outWidth); val h = max(1, o.outHeight)
        return if (rotation(file) % 180 == 0) w to h else h to w
    }

    private fun decodeUpright(file: File, sample: Int): Bitmap {
        val o = BitmapFactory.Options().apply { inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888; inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(file.path, o) ?: error("원본 사진을 열 수 없습니다")
        val deg = rotation(file)
        if (deg != 0) { val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg.toFloat()) }, true); if (r !== bmp) bmp.recycle(); bmp = r }
        if (!bmp.isMutable) { val c = bmp.copy(Bitmap.Config.ARGB_8888, true); bmp.recycle(); bmp = c }
        return bmp
    }

    /**
     * Renders [shapes] on [source] at the source's own resolution and writes a new JPEG into [outDir].
     * [previewWidth] is the on-screen width the shapes were drawn at, so line weights match the preview.
     */
    fun render(source: File, shapes: List<Annotation>, previewWidth: Float, outDir: File): File {
        // Full resolution first; only a photo too large for this phone's memory is halved.
        val bitmap = try { decodeUpright(source, 1) } catch (e: OutOfMemoryError) { System.gc(); decodeUpright(source, 2) }
        try {
            val canvas = Canvas(bitmap)
            val scale = bitmap.width / previewWidth.coerceAtLeast(1f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
            shapes.forEachIndexed { i, s ->
                val x1 = s.x1.toFloat() * bitmap.width; val y1 = s.y1.toFloat() * bitmap.height
                val x2 = s.x2.toFloat() * bitmap.width; val y2 = s.y2.toFloat() * bitmap.height
                val left = min(x1, x2); val top = min(y1, y2)
                val color = Color.parseColor(s.color)
                paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * scale
                val rect = RectF(left, top, max(x1, x2), max(y1, y2))
                when (s.kind) {
                    KIND_CIRCLE -> canvas.drawOval(rect, paint)
                    KIND_RECTANGLE -> canvas.drawRect(rect, paint)
                    KIND_ARROW -> {
                        canvas.drawLine(x1, y1, x2, y2, paint)
                        val angle = atan2(y2 - y1, x2 - x1); val head = 14f * scale
                        val path = Path().apply {
                            moveTo(x2, y2)
                            lineTo(x2 - head * cos(angle - .45f), y2 - head * sin(angle - .45f))
                            lineTo(x2 - head * cos(angle + .45f), y2 - head * sin(angle + .45f)); close()
                        }
                        paint.style = Paint.Style.FILL; canvas.drawPath(path, paint)
                    }
                }
                val cx = (left + 9f * scale).coerceIn(13f * scale, max(13f * scale, bitmap.width - 13f * scale))
                val cy = (top - 13f * scale).coerceIn(13f * scale, max(13f * scale, bitmap.height - 13f * scale))
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE; canvas.drawCircle(cx, cy, 11f * scale, paint)
                paint.style = Paint.Style.STROKE; paint.color = color; paint.strokeWidth = 2f * scale; canvas.drawCircle(cx, cy, 11f * scale, paint)
                paint.style = Paint.Style.FILL; paint.textSize = 12f * scale; paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
                canvas.drawText((i + 1).toString(), cx, cy + 4f * scale, paint)
            }
            outDir.mkdirs()
            val output = File(outDir, "edited-${UUID.randomUUID()}.jpg")
            output.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) { "사진을 저장하지 못했습니다" } }
            return output
        } finally { bitmap.recycle() }
    }
}
