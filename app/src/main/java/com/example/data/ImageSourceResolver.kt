package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import java.io.File

object ImageSourceResolver {
    fun getImageDimensions(context: Context, uriString: String): Pair<Int, Int>? {
        if (uriString.isBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            var orientation = ExifInterface.ORIENTATION_NORMAL
            if (uri.scheme == "file") {
                val file = File(uri.path ?: return null)
                if (!file.exists()) return null
                BitmapFactory.decodeFile(file.absolutePath, options)
                orientation = ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    orientation = ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )
                }
            }
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270
            ) Pair(h, w) else Pair(w, h)
        } catch (e: Exception) {
            null
        }
    }
}
