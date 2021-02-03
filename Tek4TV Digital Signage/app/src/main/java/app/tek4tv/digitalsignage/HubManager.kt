package app.tek4tv.digitalsignage

import android.app.Activity
import android.util.Log
import app.tek4tv.digitalsignage.media.setSystemTime
import app.tek4tv.digitalsignage.model.DirectMessage
import app.tek4tv.digitalsignage.model.PingHubRequest
import app.tek4tv.digitalsignage.model.ReponseHub
import app.tek4tv.digitalsignage.model.Video
import app.tek4tv.digitalsignage.utils.NetworkUtils
import app.tek4tv.digitalsignage.utils.Status
import app.tek4tv.digitalsignage.utils.Utils
import app.tek4tv.digitalsignage.viewmodels.MainViewModel
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class HubManager(
    private var lifecycleScope: CoroutineScope,
    private var mainActivity: Activity,
    private var viewModel: MainViewModel,
    private val moshi: Moshi,
    var onMessageListener: (command: String?, message: String?) -> Unit
) {
    private val LOG_TAG = "HubConnectionTask"

    private var pingHubJob: Job? = null
    var lastPing = ""

    var hubConnection: HubConnection? = null

    private var openNewConnectionJob: Job? = null

    //current response Hub
    var responseHub = ReponseHub()

    var receivedConnectionId = ""

    val dateformat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")

    fun createNewHubConnection() {
        if (openNewConnectionJob != null && openNewConnectionJob!!.isActive) return

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
        return withContext(Dispatchers.Default) {
            try {
                hubConnection!!.start().blockingAwait()
                hubConnection!!.connectionId
            } catch (e: Exception) {
                Log.e(LOG_TAG, "error connecting tto hub")
                e.printStackTrace()
                null
            }
        }
    }

    private fun onMessage() {
        hubConnection?.on("ReceiveMessage", { command: String?, message: String? ->
            mainActivity.runOnUiThread {
                Log.d("command", command ?: "")
                Log.d("message", message ?: "")

                parseHubResponse(message)

                if (command != null && message != null) {
                    when (command) {
                        Status.PONG -> {
                            lastPing = message
                            syncTimeWithServer(message)
                            Log.d("last ping", lastPing)
                        }

                        Status.UPDATE_STATUS -> {
                            if (responseHub.message != null) receivedConnectionId =
                                responseHub.message!!
                        }
                    }
                }

                //Handle command from server in MainActivity
                onMessageListener.invoke(command, message)
            }
        }, String::class.java, String::class.java)
    }

    fun sendMessage(mess: String, command: String) {
        try {
            hubConnection!!.invoke(
                command, Utils.getDeviceId(mainActivity.applicationContext), mess)
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message)
            e.printStackTrace()
        }
    }

    private fun sendDirectMessage(connectionId: String, command: String, message: String) {
        try {
            hubConnection!!.invoke(Utils.DIRECT_MESSAGE, connectionId, command, message)
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message)
            e.printStackTrace()
        }
    }

    fun sendHubDirectMessage(connectionId: String, command: String, message: String) {
        val directMessage = DirectMessage(Utils.getDeviceId(mainActivity), message)

        sendDirectMessage(
            connectionId,
            command,
            Utils.toJsonString(moshi, DirectMessage::class.java, directMessage))
    }

    fun pingHub() {
        try {
            if (!mainActivity.isFinishing && hubConnection != null) {

                Log.d(LOG_TAG, "ping hub")
                val i = viewModel.playlistIndex
                Log.d("player:", i.toString())

                var mode = "-1"

                val curMediaItem = viewModel.currentMediaItem

                if (curMediaItem != null) {
                    val path = curMediaItem.path
                    mode = if (path.isNotEmpty() && !File(path).exists()) "1"
                    else "0"
                }

                val video = Video("" + i, mode).apply {
                    if (curMediaItem != null) mediaName = curMediaItem.name ?: ""
                    audioName = viewModel.currentAudioName
                }

                val videoAdapter = moshi.adapter(Video::class.java)

                var request = PingHubRequest().apply {
                    imei = Utils.getDeviceId(mainActivity.applicationContext)
                    status = "START"
                    connectionId = (hubConnection!!.connectionId)
                    this.video = videoAdapter.toJson(video)
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

                if (lastPing != "") {
                    try {

                        val now = Calendar.getInstance()
                        val lastPingTime = Calendar.getInstance().apply {
                            time = dateformat.parse(lastPing)
                        }

                        if (now.timeInMillis - lastPingTime.timeInMillis > 45000) {
                            Log.e(LOG_TAG, "Losed hub connection")

                            if (hubConnection != null) hubConnection!!.stop()

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

    private fun parseHubResponse(message: String?) {
        if (message.isNullOrEmpty()) return

        try {
            responseHub = ReponseHub()
            if (message.startsWith("{")) {
                val jsonAdapter = moshi.adapter(ReponseHub::class.java)
                responseHub = jsonAdapter.fromJson(message)!!
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncTimeWithServer(timeFromServer: String) {
        try {
            val time = dateformat.parse(timeFromServer)
            val now = Date()

            if (abs(time.time - now.time) >= 3000) {
                setSystemTime(timeFromServer)
                Log.d("timesync", "sync with server time: $timeFromServer")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}