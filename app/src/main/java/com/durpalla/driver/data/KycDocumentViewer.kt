package com.durpalla.driver.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.durpalla.driver.data.api.ApiClient
import java.io.File

/** Downloads an authenticated KYC document and opens it (image viewer / external PDF). */
object KycDocumentViewer {
    data class Preview(val bitmap: android.graphics.Bitmap?, val openedExternally: Boolean)

    suspend fun open(context: Context, api: ApiClient, viewPath: String): Result<Preview> = runCatching {
        val (bytes, mime) = api.getBytes(viewPath).getOrThrow()
        require(bytes.isNotEmpty()) { "empty document" }
        val isPdf = mime?.contains("pdf", true) == true || viewPath.endsWith(".pdf", true)
        if (!isPdf) {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) return@runCatching Preview(bmp, false)
        }
        val dir = File(context.cacheDir, "kyc").also { it.mkdirs() }
        val ext = if (isPdf) "pdf" else "jpg"
        val file = File(dir, "doc_${System.currentTimeMillis()}.$ext")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime ?: if (isPdf) "application/pdf" else "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, null))
        Preview(null, true)
    }
}
