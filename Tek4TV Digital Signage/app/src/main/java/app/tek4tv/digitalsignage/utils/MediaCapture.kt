package app.tek4tv.digitalsignage.utils


import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import android.view.TextureView
import android.widget.FrameLayout
import androidx.core.view.children
import app.tek4tv.digitalsignage.network.PlaylistService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*


class MediaCapture(
    private val appContext: Context, private val scope: CoroutineScope
) {

    private lateinit var mediaRecorder: MediaRecorder
    private val recordedAudioPath = "${appContext.filesDir.path}/recorded.aac"

    fun getBestSampleRate(): Int {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRateString = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return sampleRateString?.toInt() ?: 44100
    }

    fun startCaptureAudio() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            setAudioEncodingBitRate(32000)
            setAudioSamplingRate(getBestSampleRate())
            setOutputFile(recordedAudioPath)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }

        mediaRecorder.start()
    }

    fun stopAudioCapture() {
        mediaRecorder.stop()
        scope.launch {
            updateRecordedAudio()
        }
    }

    private suspend fun updateRecordedAudio() {
        withContext(Dispatchers.IO) {
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

                val dateformat =
                    SimpleDateFormat("ddMMyyyyHHmmss")//SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                val fileName = dateformat.format(Date())

                val buffIn = BufferedInputStream(FileInputStream(recordedAudioPath))
                ftpClient.enterLocalPassiveMode()
                ftpClient.storeFile("$fileName.aac", buffIn)
                buffIn.close()
                ftpClient.logout()
                ftpClient.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun captureScreen(service: PlaylistService, isPortrait: Boolean, mVideoLayout: VLCVideoLayout) {
        scope.launch(Dispatchers.Default) {
            try {
                val tv: TextureView =
                    (mVideoLayout.getChildAt(0) as FrameLayout).children.filter { it is TextureView }
                        .first() as TextureView


                val base64Img = withContext(Dispatchers.Default) {
                    val bitmap = if (!isPortrait) ImageResize.getResizedBitmap(tv.bitmap, 640, 360)
                    else ImageResize.getResizedBitmap(tv.bitmap, 360, 640)
                    val bs = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs)
                    Base64.encodeToString(bs.toByteArray(), Base64.DEFAULT)
                }


                val body = mapOf(
                    "Data" to base64Img,
                    "Imei" to Utils.getDeviceId(appContext),
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
}