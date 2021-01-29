package app.tek4tv.digitalsignage.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.util.*

class ImageResize(private val scope: CoroutineScope) {

    private val imageQueue: Queue<String> = LinkedList()
    private val addedItem = mutableMapOf<String, Boolean>()
    private var isReSizing = false


    fun startResize() {
        Log.d("resize", "Strt")
        isReSizing = true
        loopResize()
        isReSizing = false
        Log.d("resize", "End")
    }

    fun loopResize() {
        while (imageQueue.isNotEmpty()) {
            try {
                val p = imageQueue.poll()
                if (p != null) resizeImage(p)
            } catch (e: Exception) {

            }
        }
    }

    fun resizeImage(path: String) {
        try {
            Log.d("resize", path)
            val size = getImageSize(path) ?: return

            if (size.outWidth <= 1920) return

            var reqWidth: Double
            var reqHeight: Double

            if (size.outWidth > size.outHeight) {
                reqWidth = 1920.0
                reqHeight = size.outHeight * (1920 / size.outWidth.toDouble())
            } else {
                reqHeight = 1080.0
                reqWidth = size.outWidth * (1080 / size.outHeight.toDouble())
            }

            /*val reqWidth1 = 1920
            val reqHeight1 = size.outHeight * (1920 / size.outWidth.toDouble())*/

            val sampleSize = calculateInSampleSize(size, reqWidth.toInt(), reqHeight.toInt())

            var resizedBitmap = decodeSampledBitMap(
                path, sampleSize, size.outWidth, reqWidth.toInt())//if (sampleSize > 1) decodeSampledBitMap(path, sampleSize)
            //else resizeManually(path, reqWidth.toInt(), reqHeight.toInt())

            /*if (resizedBitmap != null
                && (resizedBitmap.width != reqWidth.toInt() || resizedBitmap.height != reqHeight.toInt()))
                    resizedBitmap =
                Bitmap.createScaledBitmap(resizedBitmap, reqWidth.toInt(), reqHeight.toInt(), true)*/

            if (resizedBitmap != null) saveBitmap(resizedBitmap, path)

            addedItem.remove(path)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    fun decodeSampledBitMap(path: String, sampleSize: Int, srcWidth: Int, reqWidth: Int): Bitmap {
        return BitmapFactory.Options().run {
            inScaled = true
            inSampleSize = sampleSize
            inDensity = srcWidth
            inTargetDensity = reqWidth * sampleSize
            BitmapFactory.decodeFile(path, this)
        }
    }

    fun resizeManually(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        try {
            val originalImage = BitmapFactory.decodeFile(path)
            if (originalImage != null) {
                return Bitmap.createScaledBitmap(originalImage, reqWidth, reqHeight, true)
            }

            return null
        } catch (e: Exception) {
            return null
        }
    }

    fun saveBitmap(bitmap: Bitmap, path: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val out = FileOutputStream(path)
                val compressFormat = if (path.endsWith("png")) Bitmap.CompressFormat.PNG
                else Bitmap.CompressFormat.JPEG
                bitmap.compress(compressFormat, 100, out)
                out.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addToQueue(path: String) {
        val added = addedItem[path]
        if (added != null) return

        addedItem[path] = true

        imageQueue.add(path)

        if (!isReSizing) startResize()
    }

    companion object {
        fun getImageSize(path: String): BitmapFactory.Options? {
            return try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                BitmapFactory.decodeFile(path, options)

                options
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}