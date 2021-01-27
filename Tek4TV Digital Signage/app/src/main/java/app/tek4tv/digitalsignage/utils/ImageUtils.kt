package app.tek4tv.digitalsignage.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileOutputStream

class ImageUtils {
    companion object {
        fun resizeImage(path: String): Boolean {
            val originalImage = BitmapFactory.decodeFile(path)

            var result = false

            if (originalImage.width > 1920) {
                val newHeight = originalImage.height * (1920 / originalImage.width.toDouble())
                val resizedImage =
                    Bitmap.createScaledBitmap(originalImage, 1920, newHeight.toInt(), true)

                try {
                    val out = FileOutputStream(path)

                    val compressFormat = if (path.endsWith("png")) Bitmap.CompressFormat.PNG
                    else Bitmap.CompressFormat.JPEG
                    result = resizedImage.compress(compressFormat, 100, out)

                    out.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return false
                }
            }

            return result
        }
    }
}