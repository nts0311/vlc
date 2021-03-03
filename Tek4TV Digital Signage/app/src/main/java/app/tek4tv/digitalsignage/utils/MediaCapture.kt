package app.tek4tv.digitalsignage.utils


//import android.view.PixelCopy
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.util.Base64
import android.util.Log
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.graphics.createBitmap
import androidx.core.view.children
import app.tek4tv.digitalsignage.network.PlaylistService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


class MediaCapture(
    private val appContext: Context, private val scope: CoroutineScope
) {

    private lateinit var mediaRecorder: MediaRecorder
    private val recordedAudioPath = "${appContext.filesDir.path}/recorded"
    var isRecordingAudio = false

    fun getBestSampleRate(): Int {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRateString = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return sampleRateString?.toInt() ?: 44100
    }

    fun startCaptureAudio() {
        isRecordingAudio = true

        if (!File(recordedAudioPath).exists()) createFolder(recordedAudioPath)

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            setAudioEncodingBitRate(32000)
            setAudioSamplingRate(getBestSampleRate())
            val dateformat = SimpleDateFormat("ddMMyyyyHHmmss")
            val fileName = "${dateformat.format(Date())}.aac"
            setOutputFile("$recordedAudioPath/$fileName")
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }

        mediaRecorder.start()
    }

    fun stopAudioCapture() {
        isRecordingAudio = false
        mediaRecorder.stop()
    }

    fun updateRecordedAudio(filePath: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (!File(filePath).exists()) return@withContext

                try {
                    val remoteFolder = Utils.getDeviceId(appContext).replace(":", "-")

                    val ftpClient = FTPClient()
                    ftpClient.connect("14.225.16.145")
                    ftpClient.login("sondev", "123456")
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE)


                    val isRemoteFolderExisted = ftpClient.changeWorkingDirectory(remoteFolder)

                    if (!isRemoteFolderExisted) {
                        ftpClient.makeDirectory("/$remoteFolder")
                        ftpClient.changeWorkingDirectory(remoteFolder)
                    }

                    val buffIn = BufferedInputStream(FileInputStream(filePath))
                    ftpClient.enterLocalPassiveMode()
                    val res = ftpClient.storeFile(File(filePath).name, buffIn)
                    Log.d("upfile", res.toString())
                    buffIn.close()
                    ftpClient.logout()
                    ftpClient.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        //val filePath = "$recordedAudioPath/$fileName
    }

    fun captureScreen(service: PlaylistService, isPortrait: Boolean, mVideoLayout: VLCVideoLayout) {
        scope.launch(Dispatchers.Default) {
            try {
                val tv: TextureView = (mVideoLayout.getChildAt(0) as FrameLayout).children.filter { it is TextureView }.first() as TextureView

                val base64Img = withContext(Dispatchers.Default) {
                    val bitmap = if (!isPortrait) ImageResize.getResizedBitmap(tv.bitmap, 640, 360)
                    else ImageResize.getResizedBitmap(tv.bitmap, 360, 640)
                    val bs = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs)
                    Base64.encodeToString(bs.toByteArray(), Base64.DEFAULT)
                }


                val body = mapOf("Data" to base64Img, "Imei" to Utils.getDeviceId(appContext),
                                 "Extension" to ".jpg")

                withContext(Dispatchers.IO) {
                    service.postScreenshot(body)
                }

            } catch (e: Exception) {
                Log.e("capture", e.message ?: "")
                e.printStackTrace()
            }
        }
    }

    fun captureSurfaceView(
        service: PlaylistService, isPortrait: Boolean, mVideoLayout: VLCVideoLayout
    ) {
        /*if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        scope.launch(Dispatchers.Default) {

            try {
                val surfaceView = (mVideoLayout.getChildAt(0) as FrameLayout).children.filter { it is SurfaceView }.first() as SurfaceView

                val bitmap = if (!isPortrait) Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_4444)
                else Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_4444)

                PixelCopy.request(surfaceView, bitmap, {

                    val out = FileOutputStream("${appContext.filesDir.path}/img.jpg")

                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)

                    out.close()

                    sendPictureToSever(service, bitmap)
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {

            }
        }*/
    }

    private fun sendPictureToSever(service: PlaylistService, bitmap: Bitmap) {
        scope.launch {
            val base64Img = withContext(Dispatchers.Default) {
                val bs = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs)
                Base64.encodeToString(bs.toByteArray(), Base64.DEFAULT)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "to base64", Toast.LENGTH_LONG).show()
            }

            withContext(Dispatchers.IO) {
                val body = mapOf("Data" to base64Img, "Imei" to Utils.getDeviceId(appContext),
                    "Extension" to ".jpg")
                service.postScreenshot(body)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "send", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun captureImageMega(
        playlistService: PlaylistService,
        mediaProjection: MediaProjection,
        dpi: Int,
        isPortrait: Boolean
    ) {
        //try {
        val (sw, sh) = if (isPortrait) listOf(360, 640) else listOf(640, 360)

        val flags: Int =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
        val imageReader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 1)
        val virtualDisplay =
            mediaProjection.createVirtualDisplay("test", sw, sh, dpi, flags, imageReader.surface,
                null, null)

        Toast.makeText(appContext, "created virtual display ", Toast.LENGTH_LONG).show()

        val callback: (ImageReader) -> Unit = {

            scope.launch(Dispatchers.Default) {
                //try {
                val bitmap = copyImageFromVirtualDisplay(imageReader, sw, sh)
                virtualDisplay.release()

                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "image not null", Toast.LENGTH_LONG).show()
                    }

                    sendPictureToSever(playlistService, bitmap)
                    val fos = FileOutputStream("${appContext.filesDir.path}/image.jpg")
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                    fos.close()
                }

                        mediaProjection.stop()
                /* } catch (e: Exception) {
                     Log.e("mediacapture", e.toString())
                 }*/
                }
            }

            imageReader.setOnImageAvailableListener(callback, null)
        /* } catch (e: Exception) {
             Log.e("mediacapture", e.toString())
         }*/
    }

    private fun copyImageFromVirtualDisplay(imageReader: ImageReader, sw: Int, sh: Int): Bitmap? {
        //return try {
        val image = imageReader.acquireLatestImage()
        val pixelStride = image.planes[0].pixelStride
        val rowStride = image.planes[0].rowStride
        val rowPadding = rowStride - pixelStride * sw
        val bitmap = createBitmap(sw + rowPadding / pixelStride, sh)
        bitmap.copyPixelsFromBuffer(image.planes[0].buffer.rewind())
        image.close()
        return bitmap
        /*} catch (e: Exception) {
            Log.e("mediacapture", e.toString())
            null
        }*/
    }
}