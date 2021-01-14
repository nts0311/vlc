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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HubManager(
        private var lifecycleScope: CoroutineScope,
        private var mainActivity: Activity,
        private var viewModel: MainViewmodel,
        private val moshi: Moshi,
        var onMessageListener: (command: String?, message: String?) -> Unit
)
{
    private val LOG_TAG = "HubConnectionTask"

    private var pingHubJob: Job? = null
    var lastPing = ""

    var hubConnection: HubConnection? = null

    private var openNewConnectionJob: Job? = null

    fun createNewHubConnection()
    {
        if (openNewConnectionJob != null && openNewConnectionJob!!.isActive)
            return

        hubConnection = HubConnectionBuilder.create(NetworkUtils.URL_HUB).build()
        connectToHub()
        onMessage()
    }

    private fun connectToHub()
    {
        openNewConnectionJob = lifecycleScope.launch {
            val connectionId = openConnection()

            if (connectionId != null)
            {
                Log.d("Connected", connectionId)
                pingTimer()
            }

            openNewConnectionJob = null
        }
    }

    private suspend fun openConnection(): String?
    {
        //open connection on another thread
        return withContext(Dispatchers.Default)
        {
            try
            {
                hubConnection!!.start().blockingAwait()
                hubConnection!!.connectionId
            } catch (e: Exception)
            {
                Log.e(LOG_TAG, "error connecting tto hub")
                null
            }
        }
    }

    private fun onMessage()
    {
        hubConnection?.on(
                "ReceiveMessage",
                { command: String?, message: String? ->
                    mainActivity.runOnUiThread {
                        Log.d("command", command ?: "")
                        Log.d("message", message ?: "")
                        onMessageListener.invoke(command, message)
                        //handleFromCommandServer(command, message)

                        if (command != null && message != null && command == Status.PONG)
                        {
                            lastPing = message
                            Log.d("last ping", lastPing)
                        }
                    }
                },
                String::class.java,
                String::class.java
        )
    }

    private fun sendMessage(mess: String, command: String)
    {
        try
        {
            hubConnection!!.invoke(command, Utils.getDeviceId(mainActivity.applicationContext), mess)
        } catch (e: Exception)
        {
            Log.e(LOG_TAG, e.message)
            e.printStackTrace()
        }
    }

    fun pingHub(isUpdate: Boolean)
    {
        try
        {
            /*if (!isUpdate) {
                if (count_ping_hub > 1) {
                    if (hubConnection == null) {
                        connectHub()
                    } else {
                        hubConnection!!.start()
                    }
                }
            }*/
            if (!mainActivity.isFinishing && hubConnection != null)
            {
                // send ping_hub || update_status
                //  val date: String = simpleDateFormat.format(Date())
                Log.d(LOG_TAG, "ping hub")
                val i = viewModel.playlistIndex
                Log.d("player:", i.toString())
                //if (i >= 0 && i < mainViewModel.lstLiveData.getValue().size()) {

                var mode = "-1"

                if (!viewModel.playlist.value.isNullOrEmpty())
                {
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


                //count_ping_hub = count_ping_hub + 1
                val requestAdater = moshi.adapter(PingHubRequest::class.java)
                sendMessage(requestAdater.toJson(request), Utils.ping)
                Log.d("request", requestAdater.toJson(request))
            }
        } catch (e: java.lang.Exception)
        {
            e.printStackTrace()

        }
    }

    private fun pingTimer()
    {
        pingHubJob?.cancel()
        pingHubJob = lifecycleScope.launch {
            while (true)
            {
                pingHub(true)
                Log.d("pingtimer", "ping")
                delay(15000)

                val dateformat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                if (lastPing != "")
                {
                    try
                    {

                        val now = Calendar.getInstance()
                        val lastPingTime = Calendar.getInstance().apply {
                            time = dateformat.parse(lastPing)
                        }

                        if (now.timeInMillis - lastPingTime.timeInMillis > 45000)
                        {
                            Log.e(LOG_TAG, "Losed hub connection")

                            if (hubConnection != null)
                                hubConnection!!.stop()

                            createNewHubConnection()
                        }
                    } catch (e: Exception)
                    {
                        Log.e(LOG_TAG, e.message)
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}