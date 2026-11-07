package com.durpalla.driver.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Center-square crop → 100×100 JPEG ≤ 100 KB (customer Flutter avatar_crop parity). */
object AvatarCrop {
    const val SIZE = 100
    private const val MAX_BYTES = 100 * 1024

    fun fromUri(context: Context, uri: Uri): ByteArray? {
        val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        return encode(src)
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
}
