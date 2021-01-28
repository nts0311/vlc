package app.tek4tv.digitalsignage.utils

import android.util.Log
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

class DownloadManager(private val scope: CoroutineScope) {
    private val CHUNK_SIZE = 5

    private val addedItem = mutableMapOf<String, Boolean>()
    private val downloadQueue: Queue<DownloadItem> = LinkedList()
    private var isDownloading = false
    private var downloadJob: Job? = null

    init {

    }

    fun startDownload() {
        isDownloading = true
        scope.launch {
            downloadJob?.join()
            downloadJob = launch {
                loopDownload()
            }
        }
    }

    private suspend fun loopDownload() {
        while (true) {
            downloadChunk()

            Log.d("downloadx", "finish chunked")

            if (downloadQueue.isEmpty()) break
        }
        isDownloading = false
        Log.d("downloadx", "finish all")
    }

    private suspend fun downloadChunk() = suspendCancellableCoroutine<Unit?> { cont ->
        Log.d("downloadx", "chunked")
        val itemsToDownload = mutableListOf<DownloadItem>()

        val itemCount = Counter(0)

        while (downloadQueue.isNotEmpty() && itemCount.value < CHUNK_SIZE) {
            itemCount.value++
            val head = downloadQueue.poll()
            if (head != null) itemsToDownload.add(head)
        }

        itemsToDownload.forEach {
            PRDownloader.download(it.itDownloadUrl, it.itStoragePath, it.itFileName).build()
                .start(object : OnDownloadListener {
                    override fun onDownloadComplete() {

                        itemCount.value--

                        if (itemCount.value <= 0) {
                            cont.resume(null)
                        }

                        addedItem[it.itDownloadUrl] = false

                        it.downloadListener.onDownloadComplete()
                    }

                    override fun onError(error: Error?) {
                        itemCount.value--

                        addedItem[it.itDownloadUrl] = false

                        if (itemCount.value <= 0) {
                            cont.resume(null)
                        }

                        it.downloadListener.onError(error)
                    }
                })
        }
    }

    fun addToQueue(downloadItem: DownloadItem) {
        val added = addedItem[downloadItem.itDownloadUrl]
        if (added != null && added) return
        addedItem[downloadItem.itDownloadUrl] = true

        downloadQueue.add(downloadItem)

        if (!isDownloading) startDownload()
    }
}

class DownloadItem {
    var itDownloadUrl = ""
    var itStoragePath = ""
    var itFileName = ""
    var downloadListener = object : OnDownloadListener {
        override fun onDownloadComplete() {

        }

        override fun onError(error: Error?) {

        }
    }
}

class Counter(var value: Int = 0)

