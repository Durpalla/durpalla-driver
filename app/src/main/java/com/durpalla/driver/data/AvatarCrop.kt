package com.durpalla.driver.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.min

/** Center-square crop → 100×100 JPEG ≤ 100 KB (customer Flutter avatar_crop parity). */
object AvatarCrop {
    const val SIZE = 100
    private const val MAX_BYTES = 100 * 1024

    fun fromUri(context: Context, uri: Uri): ByteArray? {
        val src = decodeOriented(context, uri) ?: return null
        return try {
            encode(src)
        } finally {
            src.recycle()
        }
    }

    /** Decode JPEG applying EXIF orientation so front-camera photos are upright on all devices. */
    fun decodeOriented(context: Context, uri: Uri): Bitmap? {
        val orientation = readOrientation(context, uri)
        val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        return applyOrientation(raw, orientation)
    }

    fun encode(src: Bitmap): ByteArray {
        val side = min(src.width, src.height).coerceAtLeast(1)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val square = Bitmap.createBitmap(src, x, y, side, side)
        val out = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        if (square !== src && square !== out) square.recycle()
        var quality = 85
        var bytes: ByteArray
        do {
            val buf = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, quality, buf)
            bytes = buf.toByteArray()
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 40)
        if (out !== src) out.recycle()
        return bytes
    }

    private fun readOrientation(context: Context, uri: Uri): Int {
        return runCatching {
            when (uri.scheme) {
                "file" -> ExifInterface(uri.path ?: return ExifInterface.ORIENTATION_UNDEFINED)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                else -> context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                    ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_UNDEFINED
            }
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
    }

    private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return src
        }
        val oriented = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (oriented !== src) src.recycle()
        return oriented
    }
}
