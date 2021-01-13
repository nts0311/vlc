package app.tek4tv.digitalsignage.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import java.io.File

object MediaDownloadUtils {
    fun downloadAudio(appContext: Context, paths: List<String>) {
        val storagePath = appContext.filesDir.path
        if (!isFolderExisted(storagePath, AUDIO_FOLDER_NAME))
            createFolder(storagePath, AUDIO_FOLDER_NAME)
        val listener = object : OnDownloadListener {
            override fun onDownloadComplete() {
                Log.d("downloadcomplete", "music")
            }

            override fun onError(error: Error?) {
                Log.d("downloaderror", "music")
            }
        }

        downloadMedias("$storagePath${File.separator}$AUDIO_FOLDER_NAME", paths, listener)
    }

    private fun downloadMedias(
        storagePath: String,
        paths: List<String>,
        downloadListener: OnDownloadListener
    ) {
        if (paths.isNotEmpty()) {
            paths.forEach { url ->
                if (url.isNotEmpty() && url.startsWith("http")) {
                    var downloadFileName = File(Uri.parse(url).path).name

                    val slash = downloadFileName.lastIndexOf("\\")

                    if (slash != -1) {
                        downloadFileName = downloadFileName.substring(slash + 1)
                    }

                    if (!isFileExisted(storagePath, downloadFileName)) {
                        PRDownloader.download(url, storagePath, downloadFileName).build()
                            .start(downloadListener)
                    }
                }
            }
        }
    }
}