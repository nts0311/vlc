package app.tek4tv.digitalsignage

import android.app.Activity
import android.util.Log
import app.tek4tv.digitalsignage.model.PingHubRequest
import app.tek4tv.digitalsignage.model.Video
import app.tek4tv.digitalsignage.utils.NetworkUtils
import app.tek4tv.digitalsignage.utils.Status
import app.tek4tv.digitalsignage.utils.Utils
import app.tek4tv.digitalsignage.viewmodels.MainViewmodel
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class HubManager(
    private var lifecycleScope: CoroutineScope,
    private var mainActivity: Activity,
    private var viewModel: MainViewmodel,
    private val moshi: Moshi,
    var onMessageListener: (command: String?, message: String?) -> Unit
) {
    private val LOG_TAG = "HubConnectionTask"

    private var pingHubJob: Job? = null
    var lastPing = ""

    var hubConnection: HubConnection? = null

    private var openNewConnectionJob: Job? = null

    fun createNewHubConnection() {
        if (openNewConnectionJob != null && openNewConnectionJob!!.isActive)
            return

        hubConnection = HubConnectionBuilder.create(NetworkUtils.URL_HUB).build()

        connectToHub()
        onMessage()
    }

    private fun connectToHub() {
        openNewConnectionJob = lifecycleScope.launch {
            val connectionId = openConnection()

            if (connectionId != null) {
                Log.d("Connected", connectionId)
                pingTimer()
            }

            openNewConnectionJob = null
        }
    }

    private suspend fun openConnection(): String? {
        //open connection on another thread
        return withContext(Dispatchers.Default)
        {
            try {
                hubConnection!!.start().doOnError {
                    val logPath =
                        "${mainActivity.applicationContext.filesDir.path}${File.separator}log.txt"
                    val out = PrintWriter(BufferedWriter(FileWriter(logPath, true)))
                    val df = SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss")

                    out.println("------------------------")
                    out.println("From HubManager.openConnection()")

                    out.println("${df.format(Calendar.getInstance().time)}\n")

                    out.println("Thread name: ${Thread.currentThread().name}")
                    out.println("Message: ${it.message}")
                    out.println("Cause: ${it.cause}")
                    out.println("Stack trace: ")
                    it.printStackTrace(out)

                    out.println("------------------------")


                    out.close()
                }
                    .blockingAwait()
                hubConnection!!.connectionId
            } catch (e: Exception) {
                Log.e(LOG_TAG, "error connecting tto hub")
                null
            }
        }
    }

    private fun onMessage() {
        hubConnection?.on(
            "ReceiveMessage",
            { command: String?, message: String? ->
                mainActivity.runOnUiThread {
                    Log.d("command", command ?: "")
                    Log.d("message", message ?: "")

                    //Handle command from server in MainActivity
                    onMessageListener.invoke(command, message)

                    if (command != null && message != null && command == Status.PONG) {
                        lastPing = message
                        Log.d("last ping", lastPing)
                    }
                }
            },
            String::class.java,
            String::class.java
        )
    }

    fun sendMessage(mess: String, command: String) {
        try {
            hubConnection!!.invoke(
                command,
                Utils.getDeviceId(mainActivity.applicationContext),
                mess
            )
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message)
            e.printStackTrace()
        }
    }

    fun sendDirectMessage(connectionId: String, command: String, message: String) {
        try {
            hubConnection!!.invoke(Utils.DIRECT_MESSAGE, connectionId, command, message)
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message)
            e.printStackTrace()
        }
    }

    fun pingHub() {
        try {
            if (!mainActivity.isFinishing && hubConnection != null) {

                Log.d(LOG_TAG, "ping hub")
                val i = viewModel.playlistIndex
                Log.d("player:", i.toString())

                var mode = "-1"

                if (!viewModel.playlist.value.isNullOrEmpty()) {
                    val path = viewModel.playlist.value!![i].path!!
                    mode = if (path.isNotEmpty() && !File(path).exists()) "1"
                    else "0"
                }

                val video = Video("" + i, mode)

                val videoAdapter = moshi.adapter(Video::class.java)

                var request = PingHubRequest().apply {
                    imei = Utils.getDeviceId(mainActivity.applicationContext)
                    status = "START"
                    connectionId = (hubConnection!!.connectionId)
                    this.video = videoAdapter.toJson(video)
                    val dateformat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                    startTine = dateformat.format(Date())
                }
                val requestAdapter = moshi.adapter(PingHubRequest::class.java)
                sendMessage(requestAdapter.toJson(request), Utils.ping)
                Log.d("request", requestAdapter.toJson(request))
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()

        }
    }

    private fun pingTimer() {
        pingHubJob?.cancel()
        pingHubJob = lifecycleScope.launch {
            while (true) {
                pingHub()
                Log.d("pingtimer", "ping")
                delay(15000)

                val dateformat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                if (lastPing != "") {
                    try {

                        val now = Calendar.getInstance()
                        val lastPingTime = Calendar.getInstance().apply {
                            time = dateformat.parse(lastPing)
                        }

                        if (now.timeInMillis - lastPingTime.timeInMillis > 45000) {
                            Log.e(LOG_TAG, "Losed hub connection")

                            if (hubConnection != null)
                                hubConnection!!.stop()

                            createNewHubConnection()
                        }
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, e.message)
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}