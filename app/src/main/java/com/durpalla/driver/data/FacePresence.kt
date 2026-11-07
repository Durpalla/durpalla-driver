package com.durpalla.driver.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max

/** On-device face presence for profile selfies (camera-only). Rejects empty / no-face frames. */
object FacePresence {
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.2f)
                .build(),
        )
    }

    sealed class Result {
        data object Ok : Result()
        data object NoFace : Result()
        data object MultipleFaces : Result()
        data object FaceTooSmall : Result()
        data object EyesClosed : Result()
    }

    suspend fun checkUri(context: Context, uri: Uri): Result {
        // Same EXIF orientation as AvatarCrop so face checks match the upright selfie.
        val bmp = AvatarCrop.decodeOriented(context, uri) ?: return Result.NoFace
        return try {
            checkBitmap(bmp)
        } finally {
            bmp.recycle()
        }
    }

    suspend fun checkBitmap(bitmap: Bitmap): Result {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).await()
        if (faces.isEmpty()) return Result.NoFace
        if (faces.size > 1) return Result.MultipleFaces
        val face = faces[0]
        val box = face.boundingBox
        val area = box.width().toFloat() * box.height().toFloat()
        val frame = bitmap.width.toFloat() * bitmap.height.toFloat()
        if (frame <= 0f || area / frame < 0.08f) return Result.FaceTooSmall
        val left = face.leftEyeOpenProbability
        val right = face.rightEyeOpenProbability
        if (left != null && right != null && max(left, right) < 0.2f) return Result.EyesClosed
        return Result.Ok
    }
}
