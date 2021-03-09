package app.tek4tv.digitalsignage.core

import android.app.Activity
import android.util.Log
import app.tek4tv.digitalsignage.model.DirectMessage
import app.tek4tv.digitalsignage.model.PingHubRequest
import app.tek4tv.digitalsignage.model.ResponseHub
import app.tek4tv.digitalsignage.model.Video
import app.tek4tv.digitalsignage.utils.NetworkUtils
import app.tek4tv.digitalsignage.utils.Status
import app.tek4tv.digitalsignage.utils.Utils
import app.tek4tv.digitalsignage.utils.setSystemTime
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
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    private var pingHubJob: Job? = null
    private var openNewConnectionJob: Job? = null
    private val videoAdapter = moshi.adapter(Video::class.java)
    private val pingRequestAdapter = moshi.adapter(PingHubRequest::class.java)
    private val deviceImei = Utils.getDeviceId(mainActivity.applicationContext)
    private val responseAdapter = moshi.adapter(ResponseHub::class.java)

    var lastPing = ""
    var hubConnection: HubConnection? = null

    //current response Hub
    var responseHub = ResponseHub()
    var receivedConnectionId = ""


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
            parseHubResponse(message)
            mainActivity.runOnUiThread {
                Log.d("command", command ?: "")
                Log.d("message", message ?: "")

                if (command != null && message != null) {
                    when (command) {
                        Status.PONG -> {
                            lastPing = message
                            syncTimeWithServer(message)
                            Log.d("last ping", lastPing)
                        }

                        Status.UPDATE_STATUS -> {
                            if (responseHub.message != null) receivedConnectionId = responseHub.message!!
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
            hubConnection!!.invoke(command, Utils.getDeviceId(mainActivity.applicationContext),
                    mess)
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

        sendDirectMessage(connectionId, command,
                Utils.toJsonString(moshi, DirectMessage::class.java, directMessage))
    }

    private fun pingHub() {
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


                val request = PingHubRequest().apply {
                    imei = deviceImei
                    status = "START"
                    connectionId = (hubConnection!!.connectionId)
                    this.video = videoAdapter.toJson(video)
                    startTine = dateFormat.format(Date())
                }

                sendMessage(pingRequestAdapter.toJson(request), Utils.ping)
                //Log.d("request", pingRequestAdapter.toJson(request))
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun pingTimer() {
        pingHubJob?.cancel()
        pingHubJob = lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                pingHub()
                Log.d("pingtimer", "ping")
                delay(15000)

                if (lastPing != "") {
                    try {

                        val now = Calendar.getInstance()
                        val lastPingTime = Calendar.getInstance().apply {
                            time = dateFormat.parse(lastPing)
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
            responseHub = ResponseHub()
            if (message.startsWith("{")) {
                responseHub = responseAdapter.fromJson(message)!!
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncTimeWithServer(timeFromServer: String) {
        try {
            val time = dateFormat.parse(timeFromServer)
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