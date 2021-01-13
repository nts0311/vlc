package app.tek4tv.digitalsignage.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.tek4tv.digitalsignage.CrashHandler
import app.tek4tv.digitalsignage.HubConnectionTask
import app.tek4tv.digitalsignage.R
import app.tek4tv.digitalsignage.media.PlayerManager
import app.tek4tv.digitalsignage.model.*
import app.tek4tv.digitalsignage.utils.*
import app.tek4tv.digitalsignage.viewmodels.MainViewmodel
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.videolan.libvlc.*
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity()
{
    private val UPDATE_PERMISSION_REQUEST_CODE = 1

    private var mVideoLayout: VLCVideoLayout? = null

    private val viewModel by viewModels<MainViewmodel>()

    private var hubConnection: HubConnection? = null

    @Inject
    lateinit var moshi: Moshi

    private var pingHubJob: Job? = null

    private lateinit var audioManager: AudioManager

    private var lastPing = ""

    private lateinit var playerManager: PlayerManager


    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        //Rotate screen
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        if (needGrantPermission())
            requestUpdatePermission()

        mVideoLayout = findViewById(R.id.video_layout)
        playerManager = PlayerManager(applicationContext, lifecycleScope, viewModel, mVideoLayout)

        NetworkUtils.instance.startNetworkListener(this)

        NetworkUtils.instance.mNetworkLive.observe(this)
        { isConnected ->
            if (isConnected)
                initHubConnect()
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        initHubConnect()
        registerObservers()
    }

    override fun onStop()
    {
        super.onStop()
        playerManager.onActivityStop()
    }

    override fun onDestroy()
    {
        super.onDestroy()
        playerManager.onActivityDestroy()
    }

    override fun onStart()
    {
        super.onStart()
        playerManager.attachVisualPlayerView()
        startPlayingMedia()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    )
    {
        when (requestCode)
        {
            UPDATE_PERMISSION_REQUEST_CODE ->
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                )
                {

                } else
                {

                }
                return
            }
        }
    }

    private fun needGrantPermission(): Boolean
    {
        val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.REQUEST_INSTALL_PACKAGES,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.READ_PHONE_STATE
        )

        var needGrantPermission = false

        permissions.forEach {
            if (checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED)
            {
                needGrantPermission = true
                return@forEach
            }
        }

        return needGrantPermission
    }

    private fun requestUpdatePermission()
    {
        val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.REQUEST_INSTALL_PACKAGES,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.READ_PHONE_STATE
        )
        requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)
    }


    private fun registerObservers()
    {
        viewModel.playlist.observe(this)
        {
            playerManager.checkScheduledMedia()

            viewModel.downloadMedias(applicationContext)


            if (viewModel.playlist.value != null && viewModel.playlistIndex >= viewModel.playlist.value!!.size)
                viewModel.playlistIndex = 0
            playerManager.playMediaByIndex(viewModel.playlistIndex)
        }
    }

    private fun startPlayingMedia()
    {
        viewModel.getPlaylist(applicationContext, false)
        viewModel.checkPlaylist(applicationContext)
    }

    private fun initHubConnect()
    {
        NetworkUtils.instance.mNetworkLive.observe(this)
        { isConnected ->
            if (isConnected)
            {

                if (!viewModel.isPlaying)
                    startPlayingMedia()

                if (hubConnection == null)
                {

                    connectToHub()

                    Log.d("Connected", "Connected")
                }
            } else
            {
                //hubConnection = null
            }

        }
    }

    private fun connectToHub()
    {
        hubConnection = HubConnectionBuilder.create(NetworkUtils.URL_HUB).build()

        HubConnectionTask { connectionId ->
            if (connectionId != null)
            {
                Log.d("Connected", connectionId)
                pingTimer()
            }
        }.execute(hubConnection)
        onMessage()
    }

    private fun onMessage()
    {
        hubConnection?.on(
                "ReceiveMessage",
                { command: String?, message: String? ->
                    runOnUiThread {
                        Log.d("command", command ?: "")
                        Log.d("message", message ?: "")
                        handleFromCommandServer(command, message)
                    }
                },
                String::class.java,
                String::class.java
        )
    }

    // ham gui du lieu
    private fun sendMessage(mess: String, command: String)
    {
        try
        {
            hubConnection!!.invoke(command, Utils.getDeviceId(applicationContext), mess)
        } catch (e: Exception)
        {
            e.printStackTrace()
            //connectToHub()
        }
    }

    private fun playURLVideo(index: Int)
    {
        viewModel.playlistIndex = index
        playerManager.playMediaByIndex(viewModel.playlistIndex)
    }

    private fun handleFromCommandServer(commamd: String?, message: String?)
    {
        try
        {
            if (commamd == null || commamd.isEmpty())
            {
                return
            }
            if (message == null || message.isEmpty())
            {
                return
            }
            //count_ping_hub = 0
            var reponseHub = ReponseHub()
            if (message.startsWith("{"))
            {
                val jsonAdapter = moshi.adapter(ReponseHub::class.java)
                reponseHub = jsonAdapter.fromJson(message)!!
            }

            if (commamd == Status.PONG)
            {
                lastPing = message
                Log.d("last ping", lastPing)
            }

            val isImei = Utils.getDeviceId(applicationContext) == reponseHub.imei
            if (isImei)
            {
                when (commamd)
                {
                    Status.GET_LIST ->
                    {
                    }
                    Status.UPDATE_LIST ->
                    {
                        viewModel.playlistIndex = 0
                        viewModel.getPlaylist(applicationContext, true)
                    }
                    Status.NEXT ->
                    {
                    }
                    Status.PREVIEW ->
                    {
                    }
                    Status.JUMP ->
                    {
                        Log.d(commamd, message)
                        val id = reponseHub.message!!.trim().toInt()
                        val volume = reponseHub.volume
                        playURLVideo(id)
                    }
                    Status.LIVE ->
                    {
                        /*volume = reponseHub.getVolume()
                        playURLVideo(reponseHub.getMessage().trim(), false)*/
                    }
                    Status.UPDATE_STATUS -> pingHub(true)
                    Status.GET_LOCATION ->
                    {
                    }
                    Status.SET_VOLUME ->
                    {
                    }
                    Status.STOP ->
                    {
                    }
                    Status.PAUSE ->
                    {
                    }
                    Status.START ->
                    {
                    }
                    Status.RESTART ->
                    {
                    }
                    Status.RELOAD ->
                    {

                    }
                    Status.SWITCH_MODE_FM ->
                    {
                    }
                    Status.SET_MUTE_DEVICE ->
                    {
                        if (reponseHub.message != null)
                            setMute(reponseHub.message!!)
                    }
                    Status.SET_VOLUME_DEVICE ->
                    {
                        if (reponseHub.message != null)
                            setVolume(reponseHub.message!!)
                    }
                    Status.GET_VOLUME_DEVICE ->
                    {
                    }
                    Status.GET_SOURCE_AUDIO ->
                    {
                    }
                    /*Status.GET_PA -> {
                        deviceAdrress = Define.Power_Amplifier_R
                        writeToDevice(buildReadMessageNew(Define.FUNC_WRITE_READ_DEVICE_INFO, "6"))
                    }*/
                    Status.GET_FM_FQ ->
                    {
                    }
                    Status.GET_AM_FQ ->
                    {
                    }
                    Status.GET_TEMPERATURE ->
                    {
                    }

                    Status.UPDATE_VERSION ->
                    {
                        if (reponseHub.message != null)
                        {
                            Log.d("update", reponseHub.message!!)
                            downloadUpdateApk(reponseHub.message!!, this)
                        }
                    }

                    Status.UPDATE_MUSIC ->
                    {
                        if (reponseHub.message != null)
                        {
                            Log.d("Update Music", reponseHub.message!!)
                            viewModel.getAudioListFromNetwork(
                                    applicationContext,
                                    reponseHub.message!!
                            )

                            playerManager.audioList = viewModel.getAudioList(applicationContext)
                        }
                    }


                }
            }
        } catch (e: java.lang.Exception)
        {
            e.printStackTrace()
        }
    }

    private fun pingHub(isUpdate: Boolean)
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
            if (!this@MainActivity.isFinishing && hubConnection != null)
            {
                // send ping_hub || update_status
                //  val date: String = simpleDateFormat.format(Date())
                Log.d("test", "ping hub")
                var request: PingHubRequest? = null

                val i = viewModel.playlistIndex
                Log.d("player:", java.lang.String.valueOf(i))
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

                request = PingHubRequest().apply {
                    imei = Utils.getDeviceId(applicationContext)
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
        pingHubJob = lifecycleScope.launchWhenResumed {
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
                            Log.e("reconnecet", "Losed hub connection")

                            if (hubConnection != null)
                                hubConnection!!.stop()

                            connectToHub()
                        }
                    } catch (e: Exception)
                    {
                        Log.e("reconnecet", e.message)
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun setMute(mute: String)
    {
        val direction = if (mute == "1") AudioManager.ADJUST_MUTE
        else AudioManager.ADJUST_UNMUTE

        audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
        )
    }

    private fun setVolume(message: String)
    {
        var volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        var maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        try
        {
            volume = ((maxVolume.toFloat() / 100) * message.toInt()).toInt()
        } catch (e: NumberFormatException)
        {

        }

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)
    }
}